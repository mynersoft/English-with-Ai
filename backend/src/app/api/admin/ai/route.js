import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { readJson } from "@/validators/auth.validator";
import {
  getAdminAiManagementOverview,
  resetAiConfig,
  updateAiConfig,
} from "@/modules/ai/aiConfig.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAdmin(
  async (req) => {
    const { searchParams } = new URL(req.url);
    const windowDays = Number(searchParams.get("window") || 7);
    const overview = await getAdminAiManagementOverview({ windowDays });
    return ok(overview);
  },
  { limit: 60 }
);

export const PATCH = withAdmin(
  async (req, { adminUid }) => {
    const body = await readJson(req);
    const windowDays = Number(body?.windowDays || 7);
    await updateAiConfig({ adminUid, body });
    const overview = await getAdminAiManagementOverview({ windowDays });
    return ok(overview);
  },
  { limit: 40 }
);

export const POST = withAdmin(
  async (req, { adminUid }) => {
    const body = await readJson(req);
    const action = String(body?.action || "reset").trim().toLowerCase();
    const windowDays = Number(body?.windowDays || 7);
    if (action === "reset") {
      await resetAiConfig({ adminUid, section: body?.section || "all" });
    }
    const overview = await getAdminAiManagementOverview({ windowDays });
    return ok(overview);
  },
  { limit: 30 }
);
