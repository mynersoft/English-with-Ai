import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { adminGetStudentDetail } from "@/modules/admin/adminStudents.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAdmin(
  async (_req, { params }) => {
    const resolved = await params;
    const data = await adminGetStudentDetail(resolved?.id);
    return ok(data);
  },
  { limit: 60 }
);
