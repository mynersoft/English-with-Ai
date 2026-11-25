import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { readJson } from "@/validators/auth.validator";
import {
  adminDeleteReadingCategory,
  adminSaveReadingCategory,
} from "@/modules/reading/reading.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const PUT = withAdmin(async (req, { params }) => {
  const { id } = await params;
  const body = await readJson(req);
  const category = await adminSaveReadingCategory({ id, body });
  return ok({ category });
}, { limit: 30 });

export const DELETE = withAdmin(async (_req, { params }) => {
  const { id } = await params;
  const data = await adminDeleteReadingCategory(id);
  return ok(data);
}, { limit: 30 });
