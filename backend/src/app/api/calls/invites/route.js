import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { getIncomingInvites } from "@/modules/calls/call.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/calls/invites -> active incoming 1-on-1 call invites for authenticated user
export const GET = withAuth(
  async (_request, { user }) => {
    const data = await getIncomingInvites(user);
    return ok(data);
  },
  { limit: 120 }
);
