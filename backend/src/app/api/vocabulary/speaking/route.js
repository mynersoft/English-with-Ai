import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import VocabularySpeakingSession from "@/models/VocabularySpeakingSession";
import {
  evaluateSpeakingPractice,
  getUserVocabSettings,
} from "@/modules/vocabulary/vocabulary.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 30;

// GET /api/vocabulary/speaking -> recent speaking practice sessions + whether AI is configured
export const GET = withAuth(async (request, { decoded }) => {
  const { searchParams } = new URL(request.url);
  const limit = Math.max(1, Math.min(30, Number(searchParams.get("limit")) || 15));
  const sessions = await VocabularySpeakingSession.find({ userId: decoded.uid })
    .sort({ createdAt: -1 })
    .limit(limit)
    .lean();

  return ok({
    aiConfigured: Boolean(process.env.AI_API_KEY),
    sessions: sessions.map((s) => ({
      id: String(s._id),
      transcript: s.transcript,
      duration: s.duration || 0,
      grammarScore: s.grammarScore || 0,
      vocabularyScore: s.vocabularyScore || 0,
      fluencyScore: s.fluencyScore || 0,
      feedback: s.feedback || "",
      createdAt: s.createdAt,
    })),
  });
}, { limit: 40 });

// POST /api/vocabulary/speaking -> body: { transcript, duration, vocabularyIds }
export const POST = withAuth(async (request, { decoded, user }) => {
  const body = await readJson(request);
  const settings = await getUserVocabSettings(user);
  const session = await evaluateSpeakingPractice({
    userId: decoded.uid,
    transcript: body?.transcript,
    duration: body?.duration,
    vocabularyIds: Array.isArray(body?.vocabularyIds) ? body.vocabularyIds : [],
    settings,
    now: new Date(),
  });
  return ok({ session }, 201);
}, { limit: 15 });
