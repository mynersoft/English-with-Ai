import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { getAdminDashboardOverview } from "@/modules/admin/adminDashboard.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAdmin(
  async () => {
    const dashboard = await getAdminDashboardOverview();
    return ok(dashboard);
  },
  { limit: 60 }
);
