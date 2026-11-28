import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { studentListCourses } from "@/modules/courses/course.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAuth(async (req, { user }) => {
  const { searchParams } = new URL(req.url);
  const data = await studentListCourses({
    userDoc: user,
    page: Number(searchParams.get("page")) || 1,
    limit: Number(searchParams.get("limit")) || 20,
    level: searchParams.get("level") || "ALL",
    category: searchParams.get("category") || "",
    search: searchParams.get("search") || "",
    updatedAfter: searchParams.get("updatedAfter") || null,
  });
  return ok(data);
}, { limit: 60 });
