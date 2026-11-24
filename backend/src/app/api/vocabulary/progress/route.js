import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { calculateStreak, getLocalDateString, shiftLocalDate } from "@/lib/vocabSrs";
import UserVocabularyProgress from "@/models/UserVocabularyProgress";
import VocabularyDailyActivity from "@/models/VocabularyDailyActivity";
import VocabularyQuizAttempt from "@/models/VocabularyQuizAttempt";
import VocabularySpeakingSession from "@/models/VocabularySpeakingSession";
import { getUserVocabSettings } from "@/modules/vocabulary/vocabulary.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/vocabulary/progress -> analytics for Progress/Statistics charts screen
export const GET = withAuth(async (request, { decoded, user }) => {
  const settings = await getUserVocabSettings(user);
  const now = new Date();
  const todayStr = getLocalDateString(now, settings.timezone);
  const weekStartStr = shiftLocalDate(todayStr, -6);
  const sixtyDaysAgoStr = shiftLocalDate(todayStr, -60);

  const [
    dailyRows,
    statusAgg,
    quizAgg,
    speakingCount,
    recentSpeaking,
  ] = await Promise.all([
    VocabularyDailyActivity.find({
      userId: decoded.uid,
      date: { $gte: sixtyDaysAgoStr, $lte: todayStr },
    })
      .sort({ date: 1 })
      .lean(),
    UserVocabularyProgress.aggregate([
      { $match: { userId: decoded.uid } },
      { $group: { _id: "$status", count: { $sum: 1 } } },
    ]),
    VocabularyQuizAttempt.aggregate([
      { $match: { userId: decoded.uid } },
      {
        $group: {
          _id: null,
          totalQuizzes: { $sum: 1 },
          avgScore: { $avg: "$score" },
          totalCorrect: { $sum: "$correctAnswers" },
          totalQuestions: { $sum: "$totalQuestions" },
        },
      },
    ]),
    VocabularySpeakingSession.countDocuments({ userId: decoded.uid }),
    VocabularySpeakingSession.aggregate([
      { $match: { userId: decoded.uid } },
      {
        $group: {
          _id: null,
          avgGrammar: { $avg: "$grammarScore" },
          avgVocab: { $avg: "$vocabularyScore" },
          avgFluency: { $avg: "$fluencyScore" },
        },
      },
    ]),
  ]);

  const byDate = new Map(dailyRows.map((d) => [d.date, d]));
  const weeklyActivity = [];
  for (let i = 0; i < 7; i++) {
    const dStr = shiftLocalDate(weekStartStr, i);
    const row = byDate.get(dStr);
    weeklyActivity.push({
      date: dStr,
      newWords: row?.newWords || 0,
      reviewedWords: row?.reviewedWords || 0,
      quizCount: row?.quizCount || 0,
      speakingMinutes: row?.speakingMinutes || 0,
      isGoalCompleted: Boolean(row?.isGoalCompleted),
    });
  }

  const statusCounts = { new: 0, learning: 0, review: 0, mastered: 0 };
  for (const s of statusAgg) {
    if (s._id in statusCounts) statusCounts[s._id] = s.count;
  }

  const qSummary = quizAgg[0] || {};
  const totalQ = qSummary.totalQuestions || 0;
  const quizAccuracy = totalQ > 0 ? Math.round(((qSummary.totalCorrect || 0) / totalQ) * 100) : 0;

  const spSummary = recentSpeaking[0] || {};
  const streak = calculateStreak(dailyRows, todayStr);

  return ok({
    timezone: settings.timezone,
    streak,
    weeklyActivity,
    statusBreakdown: statusCounts,
    quizStats: {
      totalQuizzes: qSummary.totalQuizzes || 0,
      averageScore: Math.round(qSummary.avgScore || 0),
      accuracyPercent: quizAccuracy,
    },
    speakingStats: {
      totalSessions: speakingCount,
      avgGrammarScore: Math.round(spSummary.avgGrammar || 0),
      avgVocabularyScore: Math.round(spSummary.avgVocab || 0),
      avgFluencyScore: Math.round(spSummary.avgFluency || 0),
    },
  });
}, { limit: 60 });
