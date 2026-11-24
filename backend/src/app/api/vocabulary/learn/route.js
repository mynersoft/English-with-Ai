import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import { getUserVocabSettings, recordWordLearned } from "@/modules/vocabulary/vocabulary.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// POST /api/vocabulary/learn -> body: { vocabularyId, knowsWord: boolean }
export const POST = withAuth(async (request, { decoded, user }) => {
  const body = await readJson(request);
  const settings = await getUserVocabSettings(user);
  const result = await recordWordLearned({
    userId: decoded.uid,
    vocabularyId: body?.vocabularyId,
    knowsWord: body?.knowsWord !== false,
    settings,
    now: new Date(),
  });
  return ok(result);
}, { limit: 60 });
