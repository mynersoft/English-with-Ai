import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { getOrAssignDailyWords, getUserVocabSettings } from "@/modules/vocabulary/vocabulary.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/vocabulary/daily
export const GET = withAuth(async (request, { decoded, user }) => {
  const settings = await getUserVocabSettings(user);
  const data = await getOrAssignDailyWords({
    userId: decoded.uid,
    settings,
    now: new Date(),
  });
  return ok(data);
}, { limit: 60 });
