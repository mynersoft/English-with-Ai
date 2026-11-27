import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import { registerPushToken, unregisterPushToken } from "@/modules/chat/chat.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// POST /api/chat/push-token -> register device FCM token for current user
export const POST = withAuth(
  async (request, { user }) => {
    const body = await readJson(request);
    const data = await registerPushToken(user, body?.token);
    return ok(data);
  },
  { limit: 30 }
);

// DELETE /api/chat/push-token -> remove device FCM token on sign-out
export const DELETE = withAuth(
  async (request, { user }) => {
    const { searchParams } = new URL(request.url);
    let token = searchParams.get("token") || "";
    if (!token) {
      try {
        const body = await readJson(request);
        token = body?.token || "";
      } catch {
        // Optional body on DELETE
      }
    }
    const data = await unregisterPushToken(user, token);
    return ok(data);
  },
  { limit: 30 }
);
