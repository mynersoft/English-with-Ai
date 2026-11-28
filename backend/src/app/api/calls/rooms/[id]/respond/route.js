import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import { respondToCall } from "@/modules/calls/call.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// POST /api/calls/rooms/[id]/respond -> accept, decline, cancel, or end a call
export const POST = withAuth(
  async (request, { params, user }) => {
    const { id } = await params;
    const body = await readJson(request);
    const data = await respondToCall(user, id, body);
    return ok(data);
  },
  { limit: 40 }
);
