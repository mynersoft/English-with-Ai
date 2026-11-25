import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import {
  getUserReadingSettings,
  updateUserReadingSettings,
} from "@/modules/reading/reading.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAuth(async (_req, { user }) => {
  const settings = await getUserReadingSettings(user);
  return ok({ settings });
}, { limit: 60 });

export const PUT = withAuth(async (req, { user }) => {
  const body = await readJson(req);
  const settings = await updateUserReadingSettings(user, body);
  return ok({ settings });
}, { limit: 30 });
