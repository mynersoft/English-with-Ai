import mongoose from "mongoose";
import { AppError } from "@/lib/errors";
import { DEFAULT_TIMEZONE, getLocalDateString, normalizeDailyGoal, normalizeTimezone, shiftLocalDate } from "@/lib/vocabSrs";
import { buildMonthCalendar, buildWeekWindow, computeStreaksFromActiveDates, toLocalDateKey } from "@/lib/studentStats";
import UserSettings from "@/models/UserSettings";
import VocabularyDailyActivity from "@/models/VocabularyDailyActivity";
import UserVocabularyProgress from "@/models/UserVocabularyProgress";
import Vocabulary from "@/models/Vocabulary";
import VocabularyQuizAttempt from "@/models/VocabularyQuizAttempt";
import VocabularySpeakingSession from "@/models/VocabularySpeakingSession";
import Pattern from "@/models/Pattern";
import ChatMessage from "@/models/ChatMessage";
import ChatRoom from "@/models/ChatRoom";

function formatLevelLabel(level) {
  const raw = String(level || "beginner").trim().toLowerCase();
  if (raw === "intermediate") return "Intermediate";
  if (raw === "advanced") return "Advanced";
  return "Beginner";
}

export async function recordStudentActivity({ userDoc, type, patternId = null, now = new Date() }) {
  const settingsDoc = await UserSettings.findOne({ user: userDoc._id }).select("vocabulary").lean();
  const timezone = normalizeTimezone(settingsDoc?.vocabulary?.timezone || DEFAULT_TIMEZONE);
  const dailyGoal = normalizeDailyGoal(settingsDoc?.vocabulary?.dailyGoal ?? 5);
  const todayStr = getLocalDateString(now, timezone);
  const userId = userDoc.firebaseUid;

  if (type === "speaking_turn") {
    await VocabularyDailyActivity.findOneAndUpdate(
      { userId, date: todayStr },
      {
        $setOnInsert: { userId, date: todayStr, dailyGoal },
        $inc: { aiSpeakingTurns: 1 },
      },
      { upsert: true }
    );
    return { recorded: true, date: todayStr };
  }

  if (type === "pattern") {
    const cleanId = patternId ? String(patternId).trim() : "";
    const update = {
      $setOnInsert: { userId, date: todayStr, dailyGoal },
      $inc: { patternsPracticed: 1 },
    };
    if (cleanId && mongoose.Types.ObjectId.isValid(cleanId)) {
      update.$addToSet = { practicedPatternIds: cleanId };
    }
    await VocabularyDailyActivity.findOneAndUpdate(
      { userId, date: todayStr },
      update,
      { upsert: true }
    );
    return { recorded: true, date: todayStr };
  }

  throw new AppError(400, "INVALID_ACTIVITY_TYPE", "Unsupported activity type.");
}

export async function buildStudentDashboardData({ userDoc, now = new Date() }) {
  const userId = userDoc.firebaseUid;
  const settingsDoc = await UserSettings.findOne({ user: userDoc._id }).select("vocabulary").lean();
  const timezone = normalizeTimezone(settingsDoc?.vocabulary?.timezone || DEFAULT_TIMEZONE);
  const todayStr = getLocalDateString(now, timezone);
  const sixtyDaysAgoStr = shiftLocalDate(todayStr, -60);
  const sixtyDaysAgoDate = new Date(now.getTime() - 61 * 24 * 60 * 60 * 1000);

  const [
    dailyActivities,
    vocabLearnedCount,
    vocabMasteredCount,
    totalPublishedWords,
    totalQuizAttempts,
    vocabSpeakingSessions,
    totalPatternsCount,
    chatMessagesTotal,
    recentChatMessages,
    activeChatRoomsCount,
  ] = await Promise.all([
    VocabularyDailyActivity.find({
      userId,
      date: { $gte: sixtyDaysAgoStr, $lte: todayStr },
    })
      .select("date newWords reviewedWords quizCount speakingMinutes patternsPracticed practicedPatternIds aiSpeakingTurns isGoalCompleted")
      .lean(),
    UserVocabularyProgress.countDocuments({ userId, learnedAt: { $ne: null } }),
    UserVocabularyProgress.countDocuments({ userId, status: "mastered" }),
    Vocabulary.countDocuments({ isPublished: true }),
    VocabularyQuizAttempt.countDocuments({ userId }),
    VocabularySpeakingSession.find({ userId, createdAt: { $gte: sixtyDaysAgoDate } })
      .select("createdAt grammarScore fluencyScore")
      .lean(),
    Pattern.countDocuments(),
    ChatMessage.countDocuments({ senderId: userDoc._id }),
    ChatMessage.find({ senderId: userDoc._id, createdAt: { $gte: sixtyDaysAgoDate } })
      .select("createdAt")
      .lean(),
    ChatRoom.countDocuments({
      $or: [{ roomType: "group" }, { roomType: "direct", memberIds: userDoc._id }],
    }),
  ]);

  // Index daily activities by YYYY-MM-DD
  const activityByDate = new Map(dailyActivities.map((a) => [a.date, a]));

  // Aggregate unique patterns practiced & total speaking/practice across recent history
  const uniquePatternIds = new Set();
  let patternSessionsCount = 0;
  let speakingTurnsTotal = 0;
  for (const a of dailyActivities) {
    patternSessionsCount += Number(a.patternsPracticed || 0);
    speakingTurnsTotal += Number(a.aiSpeakingTurns || 0);
    if (Array.isArray(a.practicedPatternIds)) {
      for (const pid of a.practicedPatternIds) uniquePatternIds.add(String(pid));
    }
  }
  const patternsPracticedCount = Math.max(uniquePatternIds.size, patternSessionsCount);
  const totalSpeakingSessions = vocabSpeakingSessions.length + speakingTurnsTotal;

  // Map speaking sessions & chat messages into local YYYY-MM-DD sets
  const vocabSpeakingDates = new Set();
  for (const s of vocabSpeakingSessions) {
    if (s.createdAt) vocabSpeakingDates.add(toLocalDateKey(s.createdAt, timezone));
  }
  const chatDates = new Set();
  for (const m of recentChatMessages) {
    if (m.createdAt) chatDates.add(toLocalDateKey(m.createdAt, timezone));
  }

  // Build set of ALL active dates (any module active on that local day)
  const activeDates = new Set();
  for (const [dateKey, a] of activityByDate.entries()) {
    const hasVocab = (a.newWords || 0) > 0 || (a.reviewedWords || 0) > 0 || a.isGoalCompleted;
    const hasQuizOrPractice = (a.quizCount || 0) > 0;
    const hasPattern = (a.patternsPracticed || 0) > 0;
    const hasSpeak = (a.aiSpeakingTurns || 0) > 0 || (a.speakingMinutes || 0) > 0;
    if (hasVocab || hasQuizOrPractice || hasPattern || hasSpeak) {
      activeDates.add(dateKey);
    }
  }
  for (const d of vocabSpeakingDates) activeDates.add(d);
  for (const d of chatDates) activeDates.add(d);

  const { currentStreak, longestStreak } = computeStreaksFromActiveDates(activeDates, todayStr);
  const { weekRangeLabel, days } = buildWeekWindow(todayStr);

  const weeklyActivity = days.map((d) => {
    const a = activityByDate.get(d.date);
    return {
      date: d.date,
      dayNumber: d.dayNumber,
      dayShort: d.dayShort,
      isToday: d.isToday,
      patternsActive: Boolean(a && (a.patternsPracticed || 0) > 0),
      speakingActive: Boolean((a && ((a.aiSpeakingTurns || 0) > 0 || (a.speakingMinutes || 0) > 0)) || vocabSpeakingDates.has(d.date)),
      vocabularyActive: Boolean(a && ((a.newWords || 0) > 0 || (a.reviewedWords || 0) > 0)),
      practiceActive: Boolean((a && (a.quizCount || 0) > 0) || chatDates.has(d.date)),
    };
  });

  const calendar = buildMonthCalendar(todayStr, activeDates);

  // Compute percentage completion for Course Report cards
  const vocabPercent = totalPublishedWords > 0
    ? Math.min(100, Math.round((vocabLearnedCount / totalPublishedWords) * 100))
    : 0;
  const patternsPercent = totalPatternsCount > 0
    ? Math.min(100, Math.round((uniquePatternIds.size / totalPatternsCount) * 100))
    : 0;
  // Target benchmark of 20 speaking sessions & 30 chat messages for percentage display
  const speakingPercent = Math.min(100, Math.round((totalSpeakingSessions / 20) * 100));
  const chatPercent = Math.min(100, Math.round((chatMessagesTotal / 30) * 100));
  const overallPercent = Math.round((vocabPercent + patternsPercent + speakingPercent + chatPercent) / 4);

  const levelLabel = formatLevelLabel(userDoc.learningLevel);

  return {
    user: {
      id: String(userDoc._id),
      name: userDoc.name || "Learner",
      email: userDoc.email || "",
      photoUrl: userDoc.photoUrl || null,
      role: userDoc.role || "student",
      planLabel: userDoc.role === "admin" ? "Admin" : "Free",
      learningLevel: levelLabel,
      timezone,
    },
    stats: {
      streakDays: currentStreak,
      vocabularyLearned: vocabLearnedCount,
      patternsPracticed: patternsPracticedCount,
      speakingSessions: totalSpeakingSessions,
      chatMessagesSent: chatMessagesTotal,
      quizzesCompleted: totalQuizAttempts,
      activeChatRooms: activeChatRoomsCount,
    },
    weekRangeLabel,
    weeklyActivity,
    streakCalendar: {
      ...calendar,
      longestStreak,
      totalActiveDays: activeDates.size,
    },
    courseReport: {
      overallPercent,
      modules: [
        {
          key: "vocabulary",
          title: "Vocabulary",
          percent: vocabPercent,
          summary: `${vocabLearnedCount} of ${totalPublishedWords} words learned · ${vocabMasteredCount} mastered`,
          route: "vocabulary",
        },
        {
          key: "patterns",
          title: `Patterns · ${levelLabel}`,
          percent: patternsPercent,
          summary: `${uniquePatternIds.size} of ${totalPatternsCount} sentence patterns practiced`,
          route: "patterns",
        },
        {
          key: "speaking",
          title: "AI Speaking",
          percent: speakingPercent,
          summary: `${totalSpeakingSessions} speaking turns & sessions completed`,
          route: "speaking",
        },
        {
          key: "chat",
          title: "Student Chat",
          percent: chatPercent,
          summary: `${chatMessagesTotal} English messages sent across ${activeChatRoomsCount} rooms`,
          route: "chat",
        },
      ],
    },
  };
}
