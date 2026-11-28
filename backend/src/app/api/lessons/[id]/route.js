import { ok } from "@/lib/response";
import { AppError } from "@/lib/errors";
import { apiHandler } from "@/lib/apiHandler";
import { connectDB } from "@/lib/db";
import { resolveAdminCaller } from "@/lib/adminGuard";
import { withAuth } from "@/middleware/auth";
import { adminGetLesson, studentGetLessonDetail } from "@/modules/courses/course.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const handleStudentGet = withAuth(async (_req, { params, user }) => {
  const { id } = await params;
  const data = await studentGetLessonDetail({ lessonId: id, userDoc: user });
  return ok(data);
}, { limit: 60 });

const handleAdminPreviewGet = apiHandler(async (req, { params }) => {
  const [caller] = await Promise.all([resolveAdminCaller(req), connectDB()]);
  if (!caller.authenticated) {
    throw new AppError(401, "UNAUTHORIZED", "Missing or invalid authentication token.");
  }
  if (!caller.isAdmin) {
    throw new AppError(403, "FORBIDDEN", "Admin access is required to preview unpublished lessons.");
  }
  const { id } = await params;
  const data = await adminGetLesson(id);
  return ok(data);
}, { limit: 60 });

export async function GET(req, context) {
  const { searchParams } = new URL(req.url);
  if (searchParams.get("preview") === "true") {
    return handleAdminPreviewGet(req, context);
  }
  return handleStudentGet(req, context);
}
