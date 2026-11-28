import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { adminDuplicateLesson } from "@/modules/courses/course.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const POST = withAdmin(async (_req, { params, adminUid }) => {
  const { id } = await params;
  const data = await adminDuplicateLesson({ lessonId: id, adminUid });
  return ok(data, 201);
}, { limit: 30 });
