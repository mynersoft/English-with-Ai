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
import UserPatternProgress from "@/models/UserPatternProgress";
import ChatMessage from "@/models/ChatMessage";
import ChatRoom from "@/models/ChatRoom";
import ReadingPassage from "@/models/ReadingPassage";
import UserReadingProgress from "@/models/UserReadingProgress";
import ReadingAttempt from "@/models/ReadingAttempt";
import ReadingDailyActivity from "@/models/ReadingDailyActivity";
import { normalizeReadingGoalMinutes } from "@/lib/readingUtils";

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
  const settingsDoc = await UserSettings.findOne({ user: userDoc._id }).select("vocabulary reading").lean();
  const timezone = normalizeTimezone(settingsDoc?.vocabulary?.timezone || DEFAULT_TIMEZONE);
  const todayStr = getLocalDateString(now, timezone);
  const sixtyDaysAgoStr = shiftLocalDate(todayStr, -60);
  const sixtyDaysAgoDate = new Date(now.getTime() - 61 * 24 * 60 * 60 * 1000);

  const [
    dailyActivities,
    vocabStatsAgg,
    totalPublishedWords,
    totalQuizAttempts,
    vocabSpeakingSessions,
    totalPatternsCount,
    completedPatterns,
    chatMessagesTotal,
    recentChatMessages,
    activeChatRoomsCount,
    totalReadingPassages,
    readingProgressDocs,
    readingAttemptsDocs,
    readingDailyDocs,
  ] = await Promise.all([
    VocabularyDailyActivity.find({
      userId,
      date: { $gte: sixtyDaysAgoStr, $lte: todayStr },
    })
      .select("date newWords reviewedWords quizCount speakingMinutes patternsPracticed practicedPatternIds aiSpeakingTurns isGoalCompleted")
      .lean(),
    UserVocabularyProgress.aggregate([
      { $match: { userId } },
      {
        $group: {
          _id: null,
          learned: {
            $sum: { $cond: [{ $ne: [{ $ifNull: ["$learnedAt", null] }, null] }, 1, 0] },
          },
          mastered: { $sum: { $cond: [{ $eq: ["$status", "mastered"] }, 1, 0] } },
        },
      },
    ]),
    Vocabulary.countDocuments({ isPublished: true }),
    VocabularyQuizAttempt.countDocuments({ userId }),
    VocabularySpeakingSession.find({ userId, createdAt: { $gte: sixtyDaysAgoDate } })
      .select("createdAt grammarScore fluencyScore")
      .lean(),
    Pattern.countDocuments(),
    UserPatternProgress.find({ userId, isCompleted: true })
      .select("patternId completedAt updatedAt")
      .lean(),
    ChatMessage.countDocuments({ senderId: userDoc._id }),
    ChatMessage.find({ senderId: userDoc._id, createdAt: { $gte: sixtyDaysAgoDate } })
      .select("createdAt")
      .lean(),
    ChatRoom.countDocuments({
      $or: [{ roomType: "group" }, { roomType: "direct", memberIds: userDoc._id }],
    }),
    ReadingPassage.countDocuments({ isPublished: true }),
    UserReadingProgress.find({ userId })
      .select("status totalSeconds lastReadAt completedAt passageId")
      .lean(),
    ReadingAttempt.find({ userId })
      .sort({ createdAt: -1 })
      .limit(50)
      .select("mode scorePercent correctCount totalCount passageId createdAt")
      .lean(),
    ReadingDailyActivity.find({
      userId,
      date: { $gte: sixtyDaysAgoStr, $lte: todayStr },
    })
      .select("date readingSeconds completedPassageIds")
      .lean(),
  ]);

  const vocabStats = vocabStatsAgg[0] || {};
  const vocabLearnedCount = Number(vocabStats.learned || 0);
  const vocabMasteredCount = Number(vocabStats.mastered || 0);

  // Index daily activities by YYYY-MM-DD
  const activityByDate = new Map(dailyActivities.map((a) => [a.date, a]));
  const readingByDate = new Map(readingDailyDocs.map((r) => [r.date, r]));

  // Aggregate unique patterns practiced/learned & total speaking/practice across recent history
  const uniquePatternIds = new Set();
  const completedPatternDates = new Set();
  for (const cp of completedPatterns) {
    if (cp.patternId) uniquePatternIds.add(String(cp.patternId));
    const ts = cp.completedAt || cp.updatedAt;
    if (ts) completedPatternDates.add(toLocalDateKey(ts, timezone));
  }
  let patternSessionsCount = 0;
  let speakingTurnsTotal = 0;
  for (const a of dailyActivities) {
    patternSessionsCount += Number(a.patternsPracticed || 0);
    speakingTurnsTotal += Number(a.aiSpeakingTurns || 0);
    if (Array.isArray(a.practicedPatternIds)) {
      for (const pid of a.practicedPatternIds) uniquePatternIds.add(String(pid));
    }
  }
  const patternsPracticedCount = Math.max(
    completedPatterns.length,
    uniquePatternIds.size,
    patternSessionsCount
  );
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

  // Compute Reading Report metrics from MongoDB
  let readingCompletedCount = 0;
  let readingInProgressCount = 0;
  let readingTotalSeconds = 0;
  for (const p of readingProgressDocs) {
    if (p.status === "completed") readingCompletedCount += 1;
    else if (p.status === "in_progress") readingInProgressCount += 1;
    readingTotalSeconds += Number(p.totalSeconds || 0);
  }
  const readingTotalMinutes = Math.round(readingTotalSeconds / 60);
  const readingDailyGoalMinutes = normalizeReadingGoalMinutes(
    settingsDoc?.reading?.dailyGoalMinutes ?? userDoc.dailyGoalMinutes ?? 15
  );
  const todayReadingDoc = readingByDate.get(todayStr);
  const readingTodayMinutes = Math.round((todayReadingDoc?.readingSeconds || 0) / 60);

  const compAttempts = readingAttemptsDocs.filter((a) => a.mode === "comprehension");
  const readAloudAttempts = readingAttemptsDocs.filter((a) => a.mode === "read_aloud");
  const averageQuizScore = compAttempts.length
    ? Math.round(compAttempts.reduce((sum, a) => sum + Number(a.scorePercent || 0), 0) / compAttempts.length)
    : 0;
  const averageReadAloudScore = readAloudAttempts.length
    ? Math.round(readAloudAttempts.reduce((sum, a) => sum + Number(a.scorePercent || 0), 0) / readAloudAttempts.length)
    : 0;

  const readingDates = new Set();
  for (const rd of readingDailyDocs) {
    if ((rd.readingSeconds || 0) > 0 || (rd.completedPassageIds || []).length > 0) {
      readingDates.add(rd.date);
    }
  }
  for (const att of readingAttemptsDocs) {
    if (att.createdAt) {
      const k = toLocalDateKey(att.createdAt, timezone);
      readingDates.add(k);
    }
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
  for (const d of completedPatternDates) activeDates.add(d);
  for (const d of readingDates) activeDates.add(d);

  const { currentStreak, longestStreak } = computeStreaksFromActiveDates(activeDates, todayStr);
  const { weekRangeLabel, days } = buildWeekWindow(todayStr);

  const weeklyActivity = days.map((d) => {
    const a = activityByDate.get(d.date);
    return {
      date: d.date,
      dayNumber: d.dayNumber,
      dayShort: d.dayShort,
      isToday: d.isToday,
      patternsActive: Boolean((a && (a.patternsPracticed || 0) > 0) || completedPatternDates.has(d.date)),
      speakingActive: Boolean((a && ((a.aiSpeakingTurns || 0) > 0 || (a.speakingMinutes || 0) > 0)) || vocabSpeakingDates.has(d.date)),
      vocabularyActive: Boolean(a && ((a.newWords || 0) > 0 || (a.reviewedWords || 0) > 0)),
      readingActive: readingDates.has(d.date),
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
  const readingPercent = totalReadingPassages > 0
    ? Math.min(100, Math.round((readingCompletedCount / totalReadingPassages) * 100))
    : 0;
  // Target benchmark of 20 speaking sessions & 30 chat messages for percentage display
  const speakingPercent = Math.min(100, Math.round((totalSpeakingSessions / 20) * 100));
  const chatPercent = Math.min(100, Math.round((chatMessagesTotal / 30) * 100));
  const overallPercent = Math.round((vocabPercent + patternsPercent + readingPercent + speakingPercent + chatPercent) / 5);

  const levelLabel = formatLevelLabel(userDoc.learningLevel);

  const top3Attempts = readingAttemptsDocs.slice(0, 3);
  const topPassageIds = Array.from(
    new Set(top3Attempts.map((a) => a.passageId).filter(Boolean).map((id) => String(id)))
  );
  const topPassages = topPassageIds.length
    ? await ReadingPassage.find({ _id: { $in: topPassageIds } }).select("title level").lean()
    : [];
  const passageMetaMap = new Map(topPassages.map((p) => [String(p._id), p]));

  const recentReadingAttempts = top3Attempts.map((att) => {
    const pid = att.passageId ? String(att.passageId) : "";
    const meta = pid ? passageMetaMap.get(pid) : null;
    return {
      id: String(att._id),
      passageId: pid,
      passageTitle: meta?.title || "Reading Passage",
      level: meta?.level || "A1",
      mode: att.mode,
      scorePercent: Number(att.scorePercent || 0),
      createdAt: att.createdAt ? new Date(att.createdAt).toISOString() : "",
    };
  });

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
      readingPassagesCompleted: readingCompletedCount,
      readingMinutes: readingTotalMinutes,
    },
    weekRangeLabel,
    weeklyActivity,
    streakCalendar: {
      ...calendar,
      longestStreak,
      totalActiveDays: activeDates.size,
    },
    readingReport: {
      completedPassages: readingCompletedCount,
      inProgressPassages: readingInProgressCount,
      totalPassages: totalReadingPassages,
      completionPercent: readingPercent,
      totalMinutes: readingTotalMinutes,
      todayMinutes: readingTodayMinutes,
      dailyGoalMinutes: readingDailyGoalMinutes,
      quizAttemptsCount: compAttempts.length,
      averageQuizScore,
      readAloudCount: readAloudAttempts.length,
      averageReadAloudScore,
      recentAttempts: recentReadingAttempts,
    },
    courseReport: {
      overallPercent,
      modules: [
        {
          key: "reading",
          title: "Reading Practice",
          percent: readingPercent,
          summary: `${readingCompletedCount} of ${totalReadingPassages} passages completed · ${readingTotalMinutes} min read · ${compAttempts.length} quizzes`,
          route: "reading",
        },
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
