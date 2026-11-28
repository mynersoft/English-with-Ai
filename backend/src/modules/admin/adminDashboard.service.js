import mongoose from "mongoose";
import { connectDB } from "@/lib/db";
import { adminAuth, adminMessaging } from "@/lib/firebaseAdmin";
import { DEFAULT_TIMEZONE, getLocalDateString } from "@/lib/vocabSrs";
import {
  buildLastNDays,
  buildSubscriptionBreakdown,
  evaluateSystemHealth,
  resolveSafeAiDescriptor,
  safePercent,
} from "@/lib/adminDashboardUtils";
import User from "@/models/User";
import Vocabulary from "@/models/Vocabulary";
import Pattern from "@/models/Pattern";
import ReadingPassage from "@/models/ReadingPassage";
import UserVocabularyProgress from "@/models/UserVocabularyProgress";
import UserPatternProgress from "@/models/UserPatternProgress";
import UserReadingProgress from "@/models/UserReadingProgress";
import VocabularyDailyActivity from "@/models/VocabularyDailyActivity";
import ReadingDailyActivity from "@/models/ReadingDailyActivity";
import VocabularySpeakingSession from "@/models/VocabularySpeakingSession";
import VocabularyQuizAttempt from "@/models/VocabularyQuizAttempt";
import ReadingAttempt from "@/models/ReadingAttempt";
import ChatMessage from "@/models/ChatMessage";
import { getEffectiveAiConfig } from "@/modules/ai/aiConfig.service";

function getStartOfUtcDay(date) {
  return new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
}

function toCountMap(rows = []) {
  const map = {};
  for (const r of rows) {
    if (r && r._id != null) {
      map[String(r._id)] = Number(r.count || 0);
    }
  }
  return map;
}

export async function getAdminDashboardOverview(now = new Date()) {
  let mongoConnected = false;
  let mongoPingMs = null;

  try {
    await connectDB();
    mongoConnected = mongoose.connection?.readyState === 1;
    if (mongoConnected && mongoose.connection?.db) {
      const pingStart = Date.now();
      await mongoose.connection.db.admin().ping();
      mongoPingMs = Date.now() - pingStart;
    }
  } catch {
    mongoConnected = false;
  }

  let firebaseAdminReady = false;
  let fcmReady = false;
  try {
    adminAuth();
    firebaseAdminReady = true;
  } catch {
    firebaseAdminReady = false;
  }
  try {
    if (firebaseAdminReady) {
      adminMessaging();
      fcmReady = true;
    }
  } catch {
    fcmReady = false;
  }

  const envAiDescriptor = resolveSafeAiDescriptor(process.env);
  const liveAiConfig = mongoConnected ? await getEffectiveAiConfig() : null;
  const aiDescriptor = {
    provider: liveAiConfig?.provider || envAiDescriptor.provider,
    model: liveAiConfig?.model || envAiDescriptor.model,
    configured: envAiDescriptor.configured && (liveAiConfig?.isEnabled !== false),
  };
  const mem = process.memoryUsage();
  const memoryUsedMb = mem.heapUsed / (1024 * 1024);
  const memoryRssMb = mem.rss / (1024 * 1024);

  if (!mongoConnected) {
    return {
      generatedAt: now.toISOString(),
      students: {
        totalRegistered: 0,
        activeAccounts: 0,
        activeLast7Days: 0,
        dailyActiveUsers: 0,
        dauPercentOfActive: 0,
        newToday: 0,
        newLast7Days: 0,
        suspended: 0,
        deactivated: 0,
        dailyActiveTrend: [],
      },
      courseCompletion: {
        overallPercent: 0,
        publishedWords: 0,
        publishedPatterns: 0,
        publishedPassages: 0,
        modules: [],
      },
      aiUsage: {
        configured: aiDescriptor.configured,
        provider: aiDescriptor.provider,
        model: aiDescriptor.model,
        totalInteractions: 0,
        todayInteractions: 0,
        speakingTurnsTotal: 0,
        speakingTurnsToday: 0,
        speakingEvaluationsTotal: 0,
        speakingEvaluationsToday: 0,
        avgSpeakingScore: 0,
        chatGrammarChecksTotal: 0,
        chatGrammarChecksToday: 0,
        chatMistakesFixedTotal: 0,
      },
      subscriptions: buildSubscriptionBreakdown({}),
      systemHealth: evaluateSystemHealth({
        mongoConnected: false,
        mongoPingMs: null,
        firebaseAdminReady,
        fcmReady,
        aiConfigured: aiDescriptor.configured,
        aiProvider: aiDescriptor.provider,
        aiModel: aiDescriptor.model,
        uptimeSeconds: process.uptime(),
        memoryUsedMb,
        memoryRssMb,
        fcmEnabledStudents: 0,
      }),
    };
  }

  const todayStr = getLocalDateString(now, DEFAULT_TIMEZONE);
  const last7Days = buildLastNDays(todayStr, 7);
  const sevenDaysAgoStr = last7Days[0].date;

  const startOfToday = new Date(now.getTime() - 24 * 60 * 60 * 1000);
  const startOfUtcToday = getStartOfUtcDay(now);
  const effectiveTodayStart = startOfUtcToday < startOfToday ? startOfUtcToday : startOfToday;
  const sevenDaysAgoDate = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
  const sevenDaysAheadDate = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);

  const [
    userOverviewAgg,
    statusBreakdownRows,
    planBreakdownRows,
    expiringSoonCount,
    studentLoginTodayUids,
    vocabDaily7dRows,
    readingDaily7dRows,
    speaking7dRows,
    patternProgress7dRows,
    chatDaily7dRows,
    publishedWords,
    totalPatterns,
    publishedPassages,
    vocabProgressAgg,
    patternProgressAgg,
    readingProgressAgg,
    readingAttemptAgg,
    vocabQuizCount,
    aiSpeakingTurnsAgg,
    speakingSessionAgg,
    chatAiAgg,
  ] = await Promise.all([
    User.aggregate([
      { $match: { role: "student" } },
      {
        $group: {
          _id: null,
          totalRegistered: { $sum: 1 },
          activeAccounts: { $sum: { $cond: [{ $eq: ["$status", "active"] }, 1, 0] } },
          suspended: { $sum: { $cond: [{ $eq: ["$status", "suspended"] }, 1, 0] } },
          deactivated: { $sum: { $cond: [{ $eq: ["$status", "deactivated"] }, 1, 0] } },
          activeLogin7d: {
            $sum: { $cond: [{ $gte: ["$lastLoginAt", sevenDaysAgoDate] }, 1, 0] },
          },
          newLast7Days: {
            $sum: { $cond: [{ $gte: ["$createdAt", sevenDaysAgoDate] }, 1, 0] },
          },
          newToday: {
            $sum: { $cond: [{ $gte: ["$createdAt", effectiveTodayStart] }, 1, 0] },
          },
          fcmEnabledStudents: {
            $sum: {
              $cond: [{ $gt: [{ $size: { $ifNull: ["$fcmTokens", []] } }, 0] }, 1, 0],
            },
          },
        },
      },
    ]),
    User.aggregate([
      { $match: { role: "student" } },
      { $group: { _id: "$status", count: { $sum: 1 } } },
    ]),
    User.aggregate([
      { $match: { role: "student" } },
      {
        $group: {
          _id: "$subscriptionStatus",
          count: { $sum: 1 },
        },
      },
    ]),
    User.countDocuments({
      role: "student",
      subscriptionStatus: { $in: ["active", "trial"] },
      subscriptionExpiresAt: { $gte: now, $lte: sevenDaysAheadDate },
    }),
    User.distinct("firebaseUid", {
      role: "student",
      lastLoginAt: { $gte: effectiveTodayStart },
    }),
    VocabularyDailyActivity.aggregate([
      { $match: { date: { $gte: sevenDaysAgoStr, $lte: todayStr } } },
      { $group: { _id: "$date", users: { $addToSet: "$userId" } } },
    ]),
    ReadingDailyActivity.aggregate([
      { $match: { date: { $gte: sevenDaysAgoStr, $lte: todayStr } } },
      { $group: { _id: "$date", users: { $addToSet: "$userId" } } },
    ]),
    VocabularySpeakingSession.aggregate([
      { $match: { createdAt: { $gte: sevenDaysAgoDate } } },
      {
        $group: {
          _id: {
            $dateToString: {
              format: "%Y-%m-%d",
              date: "$createdAt",
              timezone: DEFAULT_TIMEZONE,
            },
          },
          users: { $addToSet: "$userId" },
        },
      },
    ]),
    UserPatternProgress.aggregate([
      { $match: { updatedAt: { $gte: sevenDaysAgoDate } } },
      {
        $group: {
          _id: {
            $dateToString: {
              format: "%Y-%m-%d",
              date: "$updatedAt",
              timezone: DEFAULT_TIMEZONE,
            },
          },
          users: { $addToSet: "$userId" },
        },
      },
    ]),
    ChatMessage.aggregate([
      { $match: { createdAt: { $gte: sevenDaysAgoDate } } },
      {
        $group: {
          _id: {
            $dateToString: {
              format: "%Y-%m-%d",
              date: "$createdAt",
              timezone: DEFAULT_TIMEZONE,
            },
          },
          senderIds: { $addToSet: "$senderId" },
        },
      },
    ]),
    Vocabulary.countDocuments({ isPublished: true }),
    Pattern.countDocuments(),
    ReadingPassage.countDocuments({ isPublished: true }),
    UserVocabularyProgress.aggregate([
      {
        $group: {
          _id: "$userId",
          learned: {
            $sum: { $cond: [{ $ne: [{ $ifNull: ["$learnedAt", null] }, null] }, 1, 0] },
          },
          mastered: { $sum: { $cond: [{ $eq: ["$status", "mastered"] }, 1, 0] } },
        },
      },
      {
        $group: {
          _id: null,
          learnersCount: { $sum: 1 },
          totalLearned: { $sum: "$learned" },
          totalMastered: { $sum: "$mastered" },
        },
      },
    ]),
    UserPatternProgress.aggregate([
      { $match: { isCompleted: true } },
      {
        $group: {
          _id: "$userId",
          completedCount: { $sum: 1 },
        },
      },
      {
        $group: {
          _id: null,
          learnersCount: { $sum: 1 },
          totalCompleted: { $sum: "$completedCount" },
        },
      },
    ]),
    UserReadingProgress.aggregate([
      {
        $group: {
          _id: "$userId",
          completedCount: { $sum: { $cond: [{ $eq: ["$status", "completed"] }, 1, 0] } },
          totalSeconds: { $sum: { $ifNull: ["$totalSeconds", 0] } },
        },
      },
      {
        $group: {
          _id: null,
          readersCount: { $sum: 1 },
          totalCompleted: { $sum: "$completedCount" },
          totalSeconds: { $sum: "$totalSeconds" },
        },
      },
    ]),
    ReadingAttempt.aggregate([
      { $match: { mode: "comprehension" } },
      {
        $group: {
          _id: null,
          quizCount: { $sum: 1 },
          avgScore: { $avg: "$scorePercent" },
        },
      },
    ]),
    VocabularyQuizAttempt.countDocuments(),
    VocabularyDailyActivity.aggregate([
      {
        $group: {
          _id: null,
          totalTurns: { $sum: { $ifNull: ["$aiSpeakingTurns", 0] } },
          todayTurns: {
            $sum: {
              $cond: [{ $eq: ["$date", todayStr] }, { $ifNull: ["$aiSpeakingTurns", 0] }, 0],
            },
          },
        },
      },
    ]),
    VocabularySpeakingSession.aggregate([
      {
        $group: {
          _id: null,
          totalSessions: { $sum: 1 },
          todaySessions: {
            $sum: { $cond: [{ $gte: ["$createdAt", effectiveTodayStart] }, 1, 0] },
          },
          avgGrammar: { $avg: "$grammarScore" },
          avgFluency: { $avg: "$fluencyScore" },
        },
      },
    ]),
    ChatMessage.aggregate([
      {
        $match: {
          "aiCorrection.status": { $in: ["corrected", "correct"] },
        },
      },
      {
        $group: {
          _id: null,
          totalChecked: { $sum: 1 },
          todayChecked: {
            $sum: { $cond: [{ $gte: ["$createdAt", effectiveTodayStart] }, 1, 0] },
          },
          mistakesFixed: {
            $sum: { $cond: [{ $eq: ["$aiCorrection.status", "corrected"] }, 1, 0] },
          },
        },
      },
    ]),
  ]);

  const [subPlanRows, studentUsersForActivity] = await Promise.all([
    User.aggregate([
      { $match: { role: "student" } },
      { $group: { _id: "$subscriptionPlan", count: { $sum: 1 } } },
    ]),
    User.find({ role: "student" }).select("_id firebaseUid lastLoginAt").lean(),
  ]);

  // Build exact student ID lookups so DAU/WAU never double-counts or counts admins.
  const studentUidSet = new Set();
  const studentObjectIdToUid = new Map();
  const active7dUidSet = new Set();

  for (const s of studentUsersForActivity) {
    const uid = String(s.firebaseUid || "");
    if (!uid) continue;
    studentUidSet.add(uid);
    studentObjectIdToUid.set(String(s._id), uid);
    if (s.lastLoginAt && new Date(s.lastLoginAt) >= sevenDaysAgoDate) {
      active7dUidSet.add(uid);
    }
  }

  const dayUsersMap = new Map(last7Days.map((d) => [d.date, new Set()]));
  const addDayUsers = (dateKey, uids = []) => {
    const bucket = dayUsersMap.get(dateKey);
    if (!bucket) return;
    for (const rawUid of uids) {
      const uid = String(rawUid || "");
      if (studentUidSet.has(uid)) {
        bucket.add(uid);
        active7dUidSet.add(uid);
      }
    }
  };

  for (const row of vocabDaily7dRows) addDayUsers(row._id, row.users);
  for (const row of readingDaily7dRows) addDayUsers(row._id, row.users);
  for (const row of speaking7dRows) addDayUsers(row._id, row.users);
  for (const row of patternProgress7dRows) addDayUsers(row._id, row.users);
  for (const row of chatDaily7dRows) {
    const mappedUids = (row.senderIds || [])
      .map((id) => studentObjectIdToUid.get(String(id)))
      .filter(Boolean);
    addDayUsers(row._id, mappedUids);
  }
  addDayUsers(todayStr, studentLoginTodayUids);

  const dailyActiveTrend = last7Days.map((d) => ({
    date: d.date,
    dayShort: d.dayShort,
    isToday: d.isToday,
    activeUsers: dayUsersMap.get(d.date)?.size || 0,
  }));

  const userSummary = userOverviewAgg[0] || {};
  const totalRegistered = Number(userSummary.totalRegistered || 0);
  const activeAccounts = Number(userSummary.activeAccounts || 0);
  const dailyActiveUsers = dayUsersMap.get(todayStr)?.size || 0;
  const activeLast7Days = active7dUidSet.size;

  // Course completion across active student base
  const vocabAgg = vocabProgressAgg[0] || {};
  const patternAgg = patternProgressAgg[0] || {};
  const readingAgg = readingProgressAgg[0] || {};
  const readingQuizAgg = readingAttemptAgg[0] || {};

  const vocabLearnersCount = Number(vocabAgg.learnersCount || 0);
  const vocabTotalLearned = Number(vocabAgg.totalLearned || 0);
  const vocabTotalMastered = Number(vocabAgg.totalMastered || 0);
  const vocabTargetDenominator = Math.max(1, activeAccounts || totalRegistered) * Math.max(0, publishedWords);
  const vocabCompletionPercent = safePercent(vocabTotalLearned, vocabTargetDenominator);

  const patternLearnersCount = Number(patternAgg.learnersCount || 0);
  const patternsTotalCompleted = Number(patternAgg.totalCompleted || 0);
  const patternTargetDenominator = Math.max(1, activeAccounts || totalRegistered) * Math.max(0, totalPatterns);
  const patternCompletionPercent = safePercent(patternsTotalCompleted, patternTargetDenominator);

  const readingReadersCount = Number(readingAgg.readersCount || 0);
  const readingTotalCompleted = Number(readingAgg.totalCompleted || 0);
  const readingTotalMinutes = Math.round(Number(readingAgg.totalSeconds || 0) / 60);
  const readingTargetDenominator = Math.max(1, activeAccounts || totalRegistered) * Math.max(0, publishedPassages);
  const readingCompletionPercent = safePercent(readingTotalCompleted, readingTargetDenominator);

  const activeModulePercents = [];
  if (publishedWords > 0) activeModulePercents.push(vocabCompletionPercent);
  if (totalPatterns > 0) activeModulePercents.push(patternCompletionPercent);
  if (publishedPassages > 0) activeModulePercents.push(readingCompletionPercent);

  const overallCoursePercent = activeModulePercents.length
    ? Math.round(activeModulePercents.reduce((a, b) => a + b, 0) / activeModulePercents.length)
    : 0;

  const avgReadingQuizScore = Math.round(Number(readingQuizAgg.avgScore || 0));
  const totalReadingQuizzes = Number(readingQuizAgg.quizCount || 0);

  // AI usage summary
  const turnsDoc = aiSpeakingTurnsAgg[0] || {};
  const evalsDoc = speakingSessionAgg[0] || {};
  const chatDoc = chatAiAgg[0] || {};

  const speakingTurnsTotal = Number(turnsDoc.totalTurns || 0);
  const speakingTurnsToday = Number(turnsDoc.todayTurns || 0);
  const speakingEvaluationsTotal = Number(evalsDoc.totalSessions || 0);
  const speakingEvaluationsToday = Number(evalsDoc.todaySessions || 0);
  const avgSpeakingScore = Math.round(
    ((Number(evalsDoc.avgGrammar || 0) + Number(evalsDoc.avgFluency || 0)) / 2) || 0
  );
  const chatGrammarChecksTotal = Number(chatDoc.totalChecked || 0);
  const chatGrammarChecksToday = Number(chatDoc.todayChecked || 0);
  const chatMistakesFixedTotal = Number(chatDoc.mistakesFixed || 0);

  const totalAiInteractions =
    speakingTurnsTotal + speakingEvaluationsTotal + chatGrammarChecksTotal;
  const todayAiInteractions =
    speakingTurnsToday + speakingEvaluationsToday + chatGrammarChecksToday;

  const statusCountsMap = toCountMap(statusBreakdownRows);
  const subStatusMap = toCountMap(planBreakdownRows);
  const subPlanMap = toCountMap(subPlanRows);

  const subscriptions = buildSubscriptionBreakdown({
    statusCounts: subStatusMap,
    planCounts: subPlanMap,
    totalStudents: totalRegistered,
    expiringSoonCount,
  });

  const systemHealth = evaluateSystemHealth({
    mongoConnected,
    mongoPingMs,
    firebaseAdminReady,
    fcmReady,
    aiConfigured: aiDescriptor.configured,
    aiProvider: aiDescriptor.provider,
    aiModel: aiDescriptor.model,
    uptimeSeconds: process.uptime(),
    memoryUsedMb,
    memoryRssMb,
    fcmEnabledStudents: Number(userSummary.fcmEnabledStudents || 0),
  });

  return {
    generatedAt: now.toISOString(),
    students: {
      totalRegistered,
      activeAccounts: Number(statusCountsMap.active || activeAccounts),
      activeLast7Days,
      dailyActiveUsers,
      dauPercentOfActive: safePercent(dailyActiveUsers, activeAccounts || totalRegistered),
      newToday: Number(userSummary.newToday || 0),
      newLast7Days: Number(userSummary.newLast7Days || 0),
      suspended: Number(statusCountsMap.suspended || userSummary.suspended || 0),
      deactivated: Number(statusCountsMap.deactivated || userSummary.deactivated || 0),
      dailyActiveTrend,
    },
    courseCompletion: {
      overallPercent: overallCoursePercent,
      publishedWords,
      publishedPatterns: totalPatterns,
      publishedPassages,
      vocabQuizAttempts: vocabQuizCount,
      readingQuizAttempts: totalReadingQuizzes,
      avgReadingQuizScore,
      modules: [
        {
          key: "vocabulary",
          title: "Vocabulary Mastery",
          percent: vocabCompletionPercent,
          completions: vocabTotalLearned,
          mastered: vocabTotalMastered,
          activeLearners: vocabLearnersCount,
          catalogCount: publishedWords,
          summary: `${vocabTotalLearned} words learned · ${vocabTotalMastered} mastered · ${vocabLearnersCount} learners`,
        },
        {
          key: "patterns",
          title: "Sentence Patterns",
          percent: patternCompletionPercent,
          completions: patternsTotalCompleted,
          mastered: patternsTotalCompleted,
          activeLearners: patternLearnersCount,
          catalogCount: totalPatterns,
          summary: `${patternsTotalCompleted} pattern completions across ${patternLearnersCount} learners`,
        },
        {
          key: "reading",
          title: "Reading Practice",
          percent: readingCompletionPercent,
          completions: readingTotalCompleted,
          mastered: readingTotalCompleted,
          activeLearners: readingReadersCount,
          catalogCount: publishedPassages,
          summary: `${readingTotalCompleted} passages finished · ${readingTotalMinutes} min read · ${avgReadingQuizScore}% quiz avg`,
        },
      ],
    },
    aiUsage: {
      configured: aiDescriptor.configured,
      provider: aiDescriptor.provider,
      model: aiDescriptor.model,
      totalInteractions: totalAiInteractions,
      todayInteractions: todayAiInteractions,
      speakingTurnsTotal,
      speakingTurnsToday,
      speakingEvaluationsTotal,
      speakingEvaluationsToday,
      avgSpeakingScore,
      chatGrammarChecksTotal,
      chatGrammarChecksToday,
      chatMistakesFixedTotal,
    },
    subscriptions,
    systemHealth,
  };
}
