import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { listReadingPassages } from "@/modules/reading/reading.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAuth(async (req, { user }) => {
  const { searchParams } = new URL(req.url);
  const page = Number(searchParams.get("page")) || 1;
  const limit = Number(searchParams.get("limit")) || 15;
  const level = searchParams.get("level") || "ALL";
  const categoryId = searchParams.get("categoryId") || "";
  const search = searchParams.get("search") || "";

  const data = await listReadingPassages({
    userId: user.firebaseUid,
    page,
    limit,
    level,
    categoryId,
    search,
  });
  return ok(data);
}, { limit: 60 });
