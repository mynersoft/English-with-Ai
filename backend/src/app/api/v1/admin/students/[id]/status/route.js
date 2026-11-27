import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { readJson } from "@/validators/auth.validator";
import { adminUpdateStudentAccountStatus } from "@/modules/admin/adminStudents.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const PATCH = withAdmin(
  async (req, { params, adminUid }) => {
    const resolved = await params;
    const body = await readJson(req);
    const data = await adminUpdateStudentAccountStatus({
      studentId: resolved?.id,
      status: body?.status,
      adminUid,
    });
    return ok(data);
  },
  { limit: 40 }
);
