import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { readJson } from "@/validators/auth.validator";
import { adminCreateCourse, adminListCourses } from "@/modules/courses/course.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAdmin(async (req) => {
  const { searchParams } = new URL(req.url);
  const data = await adminListCourses({
    page: Number(searchParams.get("page")) || 1,
    limit: Number(searchParams.get("limit")) || 20,
    level: searchParams.get("level") || "ALL",
    category: searchParams.get("category") || "",
    published: searchParams.get("published") || "all",
    search: searchParams.get("search") || "",
  });
  return ok(data);
}, { limit: 60 });

export const POST = withAdmin(async (req, { adminUid }) => {
  const body = await readJson(req);
  const data = await adminCreateCourse({ adminUid, body });
  return ok(data, 201);
}, { limit: 40 });
