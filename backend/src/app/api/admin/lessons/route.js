import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { readJson } from "@/validators/auth.validator";
import {
  adminCreateLesson,
  adminListLessons,
  adminReorderLessons,
} from "@/modules/courses/course.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAdmin(async (req) => {
  const { searchParams } = new URL(req.url);
  const data = await adminListLessons({
    courseId: searchParams.get("courseId") || "",
    moduleId: searchParams.get("moduleId") || "",
  });
  return ok(data);
}, { limit: 60 });

export const POST = withAdmin(async (req, { adminUid }) => {
  const body = await readJson(req);
  const data = await adminCreateLesson({ adminUid, body });
  return ok(data, 201);
}, { limit: 40 });

export const PATCH = withAdmin(async (req) => {
  const body = await readJson(req);
  const data = await adminReorderLessons({
    moduleId: body.moduleId,
    lessonIds: body.lessonIds,
  });
  return ok(data);
}, { limit: 40 });
