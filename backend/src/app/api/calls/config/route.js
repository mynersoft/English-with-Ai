import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { getCallProviderConfigForUser } from "@/modules/calls/call.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/calls/config -> active call provider summary (free_p2p by default, drop-in paid SFU/TURN later)
export const GET = withAuth(async () => {
  const data = await getCallProviderConfigForUser();
  return ok(data);
});
