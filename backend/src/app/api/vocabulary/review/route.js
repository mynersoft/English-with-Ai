import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import { getUserVocabSettings, recordWordReview } from "@/modules/vocabulary/vocabulary.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// POST /api/vocabulary/review -> body: { vocabularyId, quality: 0..5 }
export const POST = withAuth(async (request, { decoded, user }) => {
  const body = await readJson(request);
  const settings = await getUserVocabSettings(user);
  const result = await recordWordReview({
    userId: decoded.uid,
    vocabularyId: body?.vocabularyId,
    quality: body?.quality,
    settings,
    now: new Date(),
  });
  return ok(result);
}, { limit: 60 });
