import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { getReviewLists } from "@/modules/vocabulary/vocabulary.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/vocabulary/review/due?tab=due|difficult|learning|mastered&page=1&limit=30
export const GET = withAuth(async (request, { decoded }) => {
  const { searchParams } = new URL(request.url);
  const tab = (searchParams.get("tab") || "due").trim().toLowerCase();
  const page = Number(searchParams.get("page")) || 1;
  const limit = Number(searchParams.get("limit")) || 30;
  const data = await getReviewLists({
    userId: decoded.uid,
    tab,
    page,
    limit,
    now: new Date(),
  });
  return ok(data);
}, { limit: 60 });
