import UserVocabularyProgress from "@/models/UserVocabularyProgress";
import VocabularyDailyActivity from "@/models/VocabularyDailyActivity";
import VocabularyQuizAttempt from "@/models/VocabularyQuizAttempt";
import VocabularySpeakingSession from "@/models/VocabularySpeakingSession";
import VocabularyCategory from "@/models/VocabularyCategory";
import Vocabulary from "@/models/Vocabulary";
import {
  getLocalDateString,
  getOrCreateVocabSettings,
  getPreviousDateString,
  MAX_DAILY_NEW_WORDS,
} from "@/lib/vocabulary/dailyWords";

function hasActivity(dayDoc) {
  if (!dayDoc) return false;
  return (
    (dayDoc.learnedWordIds?.length || 0) > 0 ||
    dayDoc.reviewedWords > 0 ||
    dayDoc.quizCount > 0 ||
    dayDoc.speakingMinutes > 0
  );
}

export async function calculateLearningStreak(userId, timezone = "Asia/Dhaka", now = new Date()) {
  const today = getLocalDateString(timezone, now);
  const yesterday = getPreviousDateString(today, 1);

  const days = await VocabularyDailyActivity.find({ userId })
    .sort({ date: -1 })
    .limit(120)
    .select("date learnedWordIds reviewedWords quizCount speakingMinutes")
    .lean();

  const activeSet = new Set(days.filter(hasActivity).map((d) => d.date));
  let cursor = activeSet.has(today) ? today : activeSet.has(yesterday) ? yesterday : null;
  if (!cursor) return 0;

  let streak = 0;
  while (activeSet.has(cursor)) {
    streak += 1;
    cursor = getPreviousDateString(cursor, 1);
  }
  return streak;
}

export async function getVocabularyDashboardData(user) {
  const userId = user._id;
  const now = new Date();
  const settings = await getOrCreateVocabSettings(userId);
  const today = getLocalDateString(settings.timezone, now);
  const dailyGoal = Math.min(MAX_DAILY_NEW_WORDS, Math.max(1, settings.dailyGoal || 5));

  const [
    todayActivity,
    statusCountsAgg,
    dueCount,
    favoriteCount,
    quizStatsAgg,
    speakingCount,
    streak,
    categories,
    publishedByCategoryAgg,
    learnedByCategoryAgg,
    recentActivities,
  ] = await Promise.all([
    VocabularyDailyActivity.findOne({ userId, date: today }).lean(),
    UserVocabularyProgress.aggregate([
      { $match: { userId } },
      { $group: { _id: "$status", count: { $sum: 1 } } },
    ]),
    UserVocabularyProgress.countDocuments({
      userId,
      nextReviewAt: { $lte: now },
    }),
    UserVocabularyProgress.countDocuments({ userId, isFavorite: true }),
    VocabularyQuizAttempt.aggregate([
      { $match: { userId } },
      {
        $group: {
          _id: null,
          totalQuestions: { $sum: "$totalQuestions" },
          correctAnswers: { $sum: "$correctAnswers" },
          attempts: { $sum: 1 },
        },
      },
    ]),
    VocabularySpeakingSession.countDocuments({ userId }),
    calculateLearningStreak(userId, settings.timezone, now),
    VocabularyCategory.find({ isActive: true }).sort({ sortOrder: 1, name: 1 }).lean(),
    Vocabulary.aggregate([
      { $match: { isPublished: true } },
      { $group: { _id: "$categoryId", count: { $sum: 1 } } },
    ]),
    UserVocabularyProgress.aggregate([
      { $match: { userId } },
      {
        $lookup: {
          from: "vocabularies",
          localField: "vocabularyId",
          foreignField: "_id",
          as: "vocab",
        },
      },
      { $unwind: "$vocab" },
      { $group: { _id: "$vocab.categoryId", count: { $sum: 1 } } },
    ]),
    VocabularyDailyActivity.find({ userId })
      .sort({ date: -1 })
      .limit(7)
      .lean(),
  ]);

  const statusMap = new Map(statusCountsAgg.map((r) => [r._id, r.count]));
  const learningCount = statusMap.get("learning") || 0;
  const reviewingCount = statusMap.get("reviewing") || 0;
  const masteredCount = statusMap.get("mastered") || 0;
  const totalLearned = learningCount + reviewingCount + masteredCount;

  const newWordsCompleted = todayActivity?.learnedWordIds?.length || 0;
  const newWordsAssigned = todayActivity?.newWordIds?.length || 0;
  const quizRow = quizStatsAgg[0] || { totalQuestions: 0, correctAnswers: 0, attempts: 0 };
  const quizAccuracy =
    quizRow.totalQuestions > 0
      ? Math.round((quizRow.correctAnswers / quizRow.totalQuestions) * 100)
      : 0;

  const publishedCatMap = new Map(publishedByCategoryAgg.map((r) => [String(r._id), r.count]));
  const learnedCatMap = new Map(learnedByCategoryAgg.map((r) => [String(r._id), r.count]));

  const formattedCategories = categories.map((c) => ({
    id: String(c._id),
    name: c.name,
    slug: c.slug,
    description: c.description || "",
    icon: c.icon || "book",
    image: c.image || null,
    parentId: c.parentId ? String(c.parentId) : null,
    wordCount: publishedCatMap.get(String(c._id)) || 0,
    learnedCount: learnedCatMap.get(String(c._id)) || 0,
  }));

  return {
    profile: {
      id: String(user._id),
      name: user.name,
      email: user.email,
      photoUrl: user.photoUrl || null,
      role: user.role,
    },
    settings: {
      dailyGoal,
      preferredLevel: settings.preferredLevel || "A1",
      preferredCategories: (settings.preferredCategories || []).map(String),
      showBengaliMeaning: settings.showBengaliMeaning ?? true,
      autoPronunciation: settings.autoPronunciation ?? true,
      learningReminder: settings.learningReminder ?? true,
      timezone: settings.timezone || "Asia/Dhaka",
    },
    stats: {
      todayDate: today,
      dailyGoal,
      newWordsAssigned,
      newWordsCompleted,
      wordsDueForReview: dueCount,
      totalWordsLearned: totalLearned,
      masteredWords: masteredCount,
      learningWords: learningCount,
      reviewingWords: reviewingCount,
      favoriteWords: favoriteCount,
      currentStreak: streak,
      quizAccuracy,
      quizAttempts: quizRow.attempts,
      speakingSessions: speakingCount,
      dailyProgressPercent: Math.min(100, Math.round((newWordsCompleted / dailyGoal) * 100)),
      isGoalCompleted: newWordsCompleted >= dailyGoal,
    },
    categories: formattedCategories,
    recentActivity: recentActivities.map((d) => ({
      date: d.date,
      newWordsCompleted: d.learnedWordIds?.length || 0,
      reviewedWords: d.reviewedWords || 0,
      quizCount: d.quizCount || 0,
      correctAnswers: d.correctAnswers || 0,
      wrongAnswers: d.wrongAnswers || 0,
      speakingMinutes: d.speakingMinutes || 0,
      isGoalCompleted: Boolean(d.isGoalCompleted),
    })),
  };
}

export async function getVocabularyProgressAnalytics(user) {
  const userId = user._id;
  const now = new Date();
  const settings = await getOrCreateVocabSettings(userId);
  const today = getLocalDateString(settings.timezone, now);

  const last7Dates = Array.from({ length: 7 }, (_, i) => getPreviousDateString(today, 6 - i));

  const [
    progressCounts,
    dueCount,
    difficultCount,
    streak,
    weeklyDocs,
    quizAttempts,
    speakingSessions,
    levelAgg,
  ] = await Promise.all([
    UserVocabularyProgress.aggregate([
      { $match: { userId } },
      { $group: { _id: "$status", count: { $sum: 1 } } },
    ]),
    UserVocabularyProgress.countDocuments({ userId, nextReviewAt: { $lte: now } }),
    UserVocabularyProgress.countDocuments({ userId, wrongCount: { $gt: 0 } }),
    calculateLearningStreak(userId, settings.timezone, now),
    VocabularyDailyActivity.find({ userId, date: { $in: last7Dates } }).lean(),
    VocabularyQuizAttempt.find({ userId }).sort({ completedAt: -1 }).limit(15).lean(),
    VocabularySpeakingSession.find({ userId }).sort({ createdAt: -1 }).limit(10).lean(),
    UserVocabularyProgress.aggregate([
      { $match: { userId } },
      {
        $lookup: {
          from: "vocabularies",
          localField: "vocabularyId",
          foreignField: "_id",
          as: "vocab",
        },
      },
      { $unwind: "$vocab" },
      { $group: { _id: "$vocab.level", count: { $sum: 1 } } },
    ]),
  ]);

  const statusMap = new Map(progressCounts.map((r) => [r._id, r.count]));
  const learning = statusMap.get("learning") || 0;
  const reviewing = statusMap.get("reviewing") || 0;
  const mastered = statusMap.get("mastered") || 0;
  const totalLearned = learning + reviewing + mastered;

  const weeklyByDate = new Map(weeklyDocs.map((d) => [d.date, d]));
  const weeklyActivity = last7Dates.map((date) => {
    const doc = weeklyByDate.get(date);
    return {
      date,
      newWordsCompleted: doc?.learnedWordIds?.length || 0,
      reviewedWords: doc?.reviewedWords || 0,
      quizCount: doc?.quizCount || 0,
      speakingMinutes: doc?.speakingMinutes || 0,
    };
  });

  const totalQuizQ = quizAttempts.reduce((sum, q) => sum + (q.totalQuestions || 0), 0);
  const totalQuizCorrect = quizAttempts.reduce((sum, q) => sum + (q.correctAnswers || 0), 0);
  const quizAccuracy = totalQuizQ > 0 ? Math.round((totalQuizCorrect / totalQuizQ) * 100) : 0;

  return {
    summary: {
      totalLearned,
      mastered,
      currentlyLearning: learning + reviewing,
      dueForReview: dueCount,
      difficultWords: difficultCount,
      quizAccuracy,
      streak,
      speakingSessionsCount: speakingSessions.length,
    },
    weeklyActivity,
    levelBreakdown: ["A1", "A2", "B1", "B2", "C1", "C2"].map((lvl) => ({
      level: lvl,
      count: levelAgg.find((r) => r._id === lvl)?.count || 0,
    })),
    recentQuizzes: quizAttempts.map((q) => ({
      id: String(q._id),
      quizType: q.quizType,
      totalQuestions: q.totalQuestions,
      correctAnswers: q.correctAnswers,
      wrongAnswers: q.wrongAnswers,
      score: q.score,
      timeTaken: q.timeTaken,
      completedAt: q.completedAt,
    })),
    recentSpeaking: speakingSessions.map((s) => ({
      id: String(s._id),
      targetWords: s.targetWords,
      usedWords: s.usedWords,
      duration: s.duration,
      grammarScore: s.grammarScore,
      vocabularyScore: s.vocabularyScore,
      fluencyScore: s.fluencyScore,
      aiEvaluated: Boolean(s.aiEvaluated),
      createdAt: s.createdAt,
    })),
  };
}
