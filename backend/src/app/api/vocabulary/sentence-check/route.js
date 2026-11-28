import { AppError } from "@/lib/errors";
import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import Vocabulary from "@/models/Vocabulary";
import { assertObjectId, evaluateSentenceWithAi } from "@/modules/vocabulary/vocabulary.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 30;

// POST /api/vocabulary/sentence-check -> body: { vocabularyId, sentence }
export const POST = withAuth(async (request, { user }) => {
  const body = await readJson(request);
  const sentence = String(body?.sentence || "").trim();
  if (!sentence) {
    throw new AppError(400, "SENTENCE_REQUIRED", "Please write an English sentence first.");
  }
  if (sentence.length > 500) {
    throw new AppError(400, "SENTENCE_TOO_LONG", "Sentence must be 500 characters or fewer.");
  }
  const vocabId = assertObjectId(body?.vocabularyId, "vocabularyId");
  const wordObj = await Vocabulary.findOne({ _id: vocabId, isPublished: true }).lean();
  if (!wordObj) {
    throw new AppError(404, "WORD_NOT_FOUND", "Vocabulary word not found.");
  }

  const isPremium =
    user?.subscriptionStatus === "active" || user?.subscriptionStatus === "trial";
  const evaluation = await evaluateSentenceWithAi({
    wordObj,
    sentence,
    userId: user?.firebaseUid || "system",
    isPremium,
  });
  return ok(evaluation);
}, { limit: 20 });
