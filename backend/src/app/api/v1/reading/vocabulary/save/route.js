import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import { saveReadingWordToVocabulary } from "@/modules/reading/reading.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const POST = withAuth(async (req, { user }) => {
  const body = await readJson(req);
  const data = await saveReadingWordToVocabulary({
    userId: user.firebaseUid,
    body,
  });
  return ok(data);
}, { limit: 60 });
