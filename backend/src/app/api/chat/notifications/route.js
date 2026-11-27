import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { getRecentIncomingMessages } from "@/modules/chat/chat.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/chat/notifications?after=...&limit=25 -> incoming chat messages for current user's rooms
export const GET = withAuth(
  async (request, { user }) => {
    const { searchParams } = new URL(request.url);
    const afterIso = searchParams.get("after") || undefined;
    const limit = Number(searchParams.get("limit") || 25);
    const data = await getRecentIncomingMessages(user, { afterIso, limit });
    return ok(data);
  },
  { limit: 120 }
);
