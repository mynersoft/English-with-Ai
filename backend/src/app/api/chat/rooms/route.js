import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import {
  createGroupRoom,
  getOrCreateDirectRoom,
  listRoomsAndStarters,
} from "@/modules/chat/chat.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/chat/rooms -> list group rooms, user's direct chats, and starter chips
export const GET = withAuth(async (_request, { user }) => {
  const data = await listRoomsAndStarters(user);
  return ok(data);
});

// POST /api/chat/rooms -> create group room or start 1-on-1 direct student chat
export const POST = withAuth(
  async (request, { user }) => {
    const body = await readJson(request);
    if (body?.roomType === "direct" || body?.peerUserId) {
      const room = await getOrCreateDirectRoom(user, body.peerUserId);
      return ok({ room }, 201);
    }
    const room = await createGroupRoom(user, body);
    return ok({ room }, 201);
  },
  { limit: 20 }
);
