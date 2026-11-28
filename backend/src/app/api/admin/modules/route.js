import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { readJson } from "@/validators/auth.validator";
import {
  adminCreateModule,
  adminListModules,
  adminReorderModules,
} from "@/modules/courses/course.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAdmin(async (req) => {
  const { searchParams } = new URL(req.url);
  const data = await adminListModules(searchParams.get("courseId") || "");
  return ok(data);
}, { limit: 60 });

export const POST = withAdmin(async (req) => {
  const body = await readJson(req);
  const data = await adminCreateModule({ courseId: body.courseId, body });
  return ok(data, 201);
}, { limit: 40 });

export const PATCH = withAdmin(async (req) => {
  const body = await readJson(req);
  const data = await adminReorderModules({
    courseId: body.courseId,
    moduleIds: body.moduleIds,
  });
  return ok(data);
}, { limit: 40 });
