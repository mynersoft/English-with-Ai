import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import { joinPrivateRoomByCode } from "@/modules/calls/call.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// POST /api/calls/rooms/join-by-code -> join private group video call using shareable code
export const POST = withAuth(
  async (request, { user }) => {
    const body = await readJson(request);
    const data = await joinPrivateRoomByCode(user, body);
    return ok(data);
  },
  { limit: 30 }
);
