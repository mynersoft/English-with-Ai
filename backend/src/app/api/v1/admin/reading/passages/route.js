import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { readJson } from "@/validators/auth.validator";
import {
  adminListPassages,
  adminSavePassage,
} from "@/modules/reading/reading.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAdmin(async (req) => {
  const { searchParams } = new URL(req.url);
  const data = await adminListPassages({
    page: Number(searchParams.get("page")) || 1,
    limit: Number(searchParams.get("limit")) || 20,
    level: searchParams.get("level") || "ALL",
    categoryId: searchParams.get("categoryId") || "",
    published: searchParams.get("published") || "all",
    search: searchParams.get("search") || "",
  });
  return ok(data);
}, { limit: 60 });

export const POST = withAdmin(async (req, { adminUid }) => {
  const body = await readJson(req);
  const data = await adminSavePassage({ id: null, adminUid, body });
  return ok(data, 201);
}, { limit: 40 });
