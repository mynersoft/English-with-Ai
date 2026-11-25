import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { searchStudents } from "@/modules/chat/chat.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/chat/students?search=...
export const GET = withAuth(async (request, { user }) => {
  const { searchParams } = new URL(request.url);
  const search = searchParams.get("search") || "";
  const students = await searchStudents(user, search);
  return ok({ students });
});
