import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { readJson } from "@/validators/auth.validator";
import {
  adminDeleteModule,
  adminUpdateModule,
} from "@/modules/courses/course.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const PATCH = withAdmin(async (req, { params }) => {
  const { id } = await params;
  const body = await readJson(req);
  const data = await adminUpdateModule({ moduleId: id, body });
  return ok(data);
}, { limit: 40 });

export const DELETE = withAdmin(async (_req, { params }) => {
  const { id } = await params;
  const data = await adminDeleteModule(id);
  return ok(data);
}, { limit: 30 });
