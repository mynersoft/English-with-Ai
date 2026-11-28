import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import { joinCallRoom } from "@/modules/calls/call.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// POST /api/calls/rooms/[id]/join -> join public, invited private, or direct call room
export const POST = withAuth(
  async (request, { params, user }) => {
    const { id } = await params;
    const body = await readJson(request).catch(() => ({}));
    const data = await joinCallRoom(user, id, body);
    return ok(data);
  },
  { limit: 40 }
);
