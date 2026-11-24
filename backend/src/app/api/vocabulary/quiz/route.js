import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import {
  generateQuizSession,
  getUserVocabSettings,
  submitQuizAttempt,
} from "@/modules/vocabulary/vocabulary.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/vocabulary/quiz?type=mixed&count=5&level=ALL&categoryId=...
export const GET = withAuth(async (request, { decoded, user }) => {
  const { searchParams } = new URL(request.url);
  const quizType = (searchParams.get("type") || "mixed").trim();
  const count = Number(searchParams.get("count")) || 5;
  const level = (searchParams.get("level") || "ALL").trim();
  const categoryId = (searchParams.get("categoryId") || "").trim();
  const settings = await getUserVocabSettings(user);

  const quiz = await generateQuizSession({
    userId: decoded.uid,
    quizType,
    count,
    level,
    categoryId,
    settings,
    now: new Date(),
  });
  return ok(quiz);
}, { limit: 30 });

// POST /api/vocabulary/quiz -> evaluates answers server-side and saves attempt + updates SRS progress
export const POST = withAuth(async (request, { decoded, user }) => {
  const body = await readJson(request);
  const settings = await getUserVocabSettings(user);
  const result = await submitQuizAttempt({
    userId: decoded.uid,
    body,
    settings,
    now: new Date(),
  });
  return ok(result, 201);
}, { limit: 20 });
