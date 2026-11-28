import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import { sendCallSignal } from "@/modules/calls/call.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// POST /api/calls/rooms/[id]/signal -> send WebRTC SDP offer/answer/ICE candidate to another participant
export const POST = withAuth(
  async (request, { params, user }) => {
    const { id } = await params;
    const body = await readJson(request);
    const data = await sendCallSignal(user, id, body);
    return ok(data, 201);
  },
  { limit: 300 }
);
