import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import { getRoomMessages, sendChatMessage } from "@/modules/chat/chat.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 30;

// GET /api/chat/rooms/[id]/messages?after=...&limit=50
export const GET = withAuth(async (request, { params, user }) => {
  const { id } = await params;
  const { searchParams } = new URL(request.url);
  const afterIso = searchParams.get("after") || undefined;
  const limit = Number(searchParams.get("limit") || 50);
  const data = await getRoomMessages(user, id, { afterIso, limit });
  return ok(data);
});

// POST /api/chat/rooms/[id]/messages -> body: { text, replyToMessageId?, autoCorrect? }
export const POST = withAuth(
  async (request, { params, user }) => {
    const { id } = await params;
    const body = await readJson(request);
    const data = await sendChatMessage(user, id, body);
    return ok(data, 201);
  },
  { limit: 30 }
);
