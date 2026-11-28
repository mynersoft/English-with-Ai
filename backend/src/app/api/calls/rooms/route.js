import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import { createCallRoom, listCallLobby } from "@/modules/calls/call.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/calls/rooms -> public rooms, user's private rooms, direct calls, and incoming invites
export const GET = withAuth(async (_request, { user }) => {
  const data = await listCallLobby(user);
  return ok(data);
});

// POST /api/calls/rooms -> create 1-on-1, public group, or private group video call
export const POST = withAuth(
  async (request, { user }) => {
    const body = await readJson(request);
    const data = await createCallRoom(user, body);
    return ok(data, 201);
  },
  { limit: 30 }
);
