import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import { toggleRoomMembership } from "@/modules/chat/chat.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// POST /api/chat/rooms/[id]/join -> body: { action?: "join" | "leave" }
export const POST = withAuth(async (request, { params, user }) => {
  const { id } = await params;
  const body = await readJson(request).catch(() => ({}));
  const action = body?.action === "leave" ? "leave" : "join";
  const result = await toggleRoomMembership(user, id, action);
  return ok(result);
});
