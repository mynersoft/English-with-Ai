import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { correctExistingMessage } from "@/modules/chat/chat.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 30;

// POST /api/chat/messages/[id]/correct -> on-demand AI grammar check/fix for a chat message
export const POST = withAuth(
  async (_request, { params, user }) => {
    const { id } = await params;
    const data = await correctExistingMessage(user, id);
    return ok(data);
  },
  { limit: 25 }
);
