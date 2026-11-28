import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { pollCallRoomState } from "@/modules/calls/call.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/calls/rooms/[id]?after=...&audioMuted=0|1&videoEnabled=0|1 -> heartbeat + room state + pending WebRTC signals
export const GET = withAuth(
  async (request, { params, user }) => {
    const { id } = await params;
    const { searchParams } = new URL(request.url);
    const afterIso = searchParams.get("after") || undefined;
    const mutedParam = searchParams.get("audioMuted");
    const videoParam = searchParams.get("videoEnabled");

    const data = await pollCallRoomState(user, id, {
      afterIso,
      audioMuted: mutedParam === null ? undefined : mutedParam === "1" || mutedParam === "true",
      videoEnabled: videoParam === null ? undefined : videoParam !== "0" && videoParam !== "false",
    });
    return ok(data);
  },
  { limit: 240 }
);
