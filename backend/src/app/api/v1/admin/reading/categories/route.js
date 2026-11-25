import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { readJson } from "@/validators/auth.validator";
import {
  adminListReadingCategories,
  adminSaveReadingCategory,
} from "@/modules/reading/reading.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAdmin(async () => {
  const categories = await adminListReadingCategories();
  return ok({ categories });
}, { limit: 60 });

export const POST = withAdmin(async (req) => {
  const body = await readJson(req);
  const category = await adminSaveReadingCategory({ id: null, body });
  return ok({ category }, 201);
}, { limit: 30 });
