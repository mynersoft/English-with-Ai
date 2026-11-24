import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import VocabularyDailyActivity from "@/models/VocabularyDailyActivity";
import VocabularyQuizAttempt from "@/models/VocabularyQuizAttempt";
import VocabularySpeakingSession from "@/models/VocabularySpeakingSession";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/vocabulary/history?page=1&limit=20
export const GET = withAuth(async (request, { decoded }) => {
  const { searchParams } = new URL(request.url);
  const page = Math.max(1, Number(searchParams.get("page")) || 1);
  const limit = Math.max(1, Math.min(30, Number(searchParams.get("limit")) || 20));
  const skip = (page - 1) * limit;

  const [quizzes, speaking, daily] = await Promise.all([
    VocabularyQuizAttempt.find({ userId: decoded.uid })
      .sort({ completedAt: -1 })
      .skip(skip)
      .limit(limit)
      .lean(),
    VocabularySpeakingSession.find({ userId: decoded.uid })
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .lean(),
    VocabularyDailyActivity.find({ userId: decoded.uid })
      .sort({ date: -1 })
      .skip(skip)
      .limit(limit)
      .lean(),
  ]);

  return ok({
    page,
    limit,
    quizzes: quizzes.map((q) => ({
      id: String(q._id),
      quizType: q.quizType,
      totalQuestions: q.totalQuestions,
      correctAnswers: q.correctAnswers,
      wrongAnswers: q.wrongAnswers,
      score: q.score,
      timeTaken: q.timeTaken,
      completedAt: q.completedAt,
    })),
    speakingSessions: speaking.map((s) => ({
      id: String(s._id),
      transcript: s.transcript,
      duration: s.duration,
      grammarScore: s.grammarScore,
      vocabularyScore: s.vocabularyScore,
      fluencyScore: s.fluencyScore,
      feedback: s.feedback,
      createdAt: s.createdAt,
    })),
    dailyActivities: daily.map((d) => ({
      date: d.date,
      dailyGoal: d.dailyGoal,
      newWords: d.newWords,
      reviewedWords: d.reviewedWords,
      quizCount: d.quizCount,
      isGoalCompleted: Boolean(d.isGoalCompleted),
    })),
  });
}, { limit: 60 });
