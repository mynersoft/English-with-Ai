import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import { recordStudentActivity } from "@/modules/student/student.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// POST /api/v1/student/activity -> records pattern practice for student weekly activity
export const POST = withAuth(
  async (request, { user }) => {
    const body = await readJson(request);
    const data = await recordStudentActivity({
      userDoc: user,
      type: body?.type || "pattern",
      patternId: body?.patternId || null,
    });
    return ok(data);
  },
  { limit: 60 }
);
