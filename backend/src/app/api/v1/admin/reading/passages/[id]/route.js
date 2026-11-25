import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { readJson } from "@/validators/auth.validator";
import {
  adminDeletePassage,
  adminSavePassage,
  getReadingPassageDetail,
} from "@/modules/reading/reading.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAdmin(async (_req, { params, adminUid }) => {
  const { id } = await params;
  const data = await getReadingPassageDetail({
    userId: adminUid || "admin",
    passageId: id,
    includeUnpublished: true,
  });
  return ok(data);
}, { limit: 60 });

export const PUT = withAdmin(async (req, { params, adminUid }) => {
  const { id } = await params;
  const body = await readJson(req);
  const data = await adminSavePassage({ id, adminUid, body });
  return ok(data);
}, { limit: 40 });

export const DELETE = withAdmin(async (_req, { params }) => {
  const { id } = await params;
  const data = await adminDeletePassage(id);
  return ok(data);
}, { limit: 30 });
