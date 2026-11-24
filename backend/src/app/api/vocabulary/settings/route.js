import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import {
  getUserVocabSettings,
  updateUserVocabSettings,
} from "@/modules/vocabulary/vocabulary.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/vocabulary/settings
export const GET = withAuth(async (request, { user }) => {
  const settings = await getUserVocabSettings(user);
  return ok({ settings });
}, { limit: 60 });

// PUT /api/vocabulary/settings
export const PUT = withAuth(async (request, { user }) => {
  const body = await readJson(request);
  const settings = await updateUserVocabSettings(user, body);
  return ok({ settings });
}, { limit: 30 });
