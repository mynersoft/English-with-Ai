import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { buildDashboardData, getUserVocabSettings } from "@/modules/vocabulary/vocabulary.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/vocabulary/dashboard
export const GET = withAuth(async (request, { decoded, user }) => {
  const settings = await getUserVocabSettings(user);
  const data = await buildDashboardData({
    userId: decoded.uid,
    userDoc: user,
    settings,
    now: new Date(),
  });
  return ok(data);
}, { limit: 60 });
