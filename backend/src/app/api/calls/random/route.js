import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import { startRandomCall } from "@/modules/calls/call.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// POST /api/calls/random -> connect with an available free student not in another call
export const POST = withAuth(
  async (request, { user }) => {
    const body = await readJson(request).catch(() => ({}));
    const data = await startRandomCall(user, body);
    return ok(data, 201);
  },
  { limit: 40 }
);
