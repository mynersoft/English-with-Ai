import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { adminListStudents } from "@/modules/admin/adminStudents.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAdmin(
  async (req) => {
    const { searchParams } = new URL(req.url);
    const data = await adminListStudents({
      page: Number(searchParams.get("page")) || 1,
      limit: Number(searchParams.get("limit")) || 20,
      search: searchParams.get("search") || "",
      accountStatus: searchParams.get("status") || "all",
      subscriptionStatus: searchParams.get("subscriptionStatus") || "all",
    });
    return ok(data);
  },
  { limit: 60 }
);
