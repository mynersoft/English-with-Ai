import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { readJson } from "@/validators/auth.validator";
import { adminUpdateStudentSubscription } from "@/modules/admin/adminStudents.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const PATCH = withAdmin(
  async (req, { params }) => {
    const resolved = await params;
    const body = await readJson(req);
    const data = await adminUpdateStudentSubscription({
      studentId: resolved?.id,
      body,
    });
    return ok(data);
  },
  { limit: 40 }
);
