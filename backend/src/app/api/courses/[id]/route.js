import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { studentGetCourseDetail } from "@/modules/courses/course.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAuth(async (req, { params, user }) => {
  const { id } = await params;
  const { searchParams } = new URL(req.url);
  const includeContent = searchParams.get("includeContent") === "true";
  const updatedAfter = searchParams.get("updatedAfter") || null;

  const data = await studentGetCourseDetail({
    courseId: id,
    userDoc: user,
    includeContent,
    updatedAfter,
  });
  return ok(data);
}, { limit: 60 });
