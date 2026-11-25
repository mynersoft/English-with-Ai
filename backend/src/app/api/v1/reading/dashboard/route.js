import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { getReadingDashboard } from "@/modules/reading/reading.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAuth(async (_req, { user }) => {
  const data = await getReadingDashboard({ userDoc: user });
  return ok(data);
}, { limit: 60 });
