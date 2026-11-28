import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import { leaveCallRoom } from "@/modules/calls/call.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// POST /api/calls/rooms/[id]/leave -> leave active call and broadcast bye signal to peers
export const POST = withAuth(
  async (request, { params, user }) => {
    const { id } = await params;
    const body = await readJson(request).catch(() => ({}));
    const data = await leaveCallRoom(user, id, body);
    return ok(data);
  },
  { limit: 40 }
);
