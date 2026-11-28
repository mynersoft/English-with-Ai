import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import {
  getLessonProgress,
  upsertLessonProgress,
} from "@/modules/courses/course.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAuth(async (_req, { params, user }) => {
  const { id } = await params;
  const data = await getLessonProgress({ lessonId: id, userDoc: user });
  return ok(data);
}, { limit: 60 });

export const POST = withAuth(async (req, { params, user }) => {
  const { id } = await params;
  const body = await readJson(req);
  const data = await upsertLessonProgress({
    lessonId: id,
    userDoc: user,
    body,
  });
  return ok(data);
}, { limit: 90 });
