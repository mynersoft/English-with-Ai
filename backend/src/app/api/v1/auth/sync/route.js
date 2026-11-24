import { apiHandler } from "@/lib/apiHandler";
import { connectDB } from "@/lib/db";
import { ok } from "@/lib/response";
import { verifyRequestToken } from "@/middleware/auth";
import { toPublicUser, upsertUserFromToken } from "@/modules/auth/auth.service";
import { readJson } from "@/validators/auth.validator";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// POST /api/v1/auth/sync  (Provider-agnostic sync for any valid Firebase ID token)
export const POST = apiHandler(async (request) => {
  const decoded = await verifyRequestToken(request);
  const body = await readJson(request);
  const name = typeof body?.name === "string" ? body.name.trim().slice(0, 60) : undefined;
  await connectDB();
  const { user, isNewUser } = await upsertUserFromToken(decoded, { name });
  return ok({ user: toPublicUser(user), isNewUser }, isNewUser ? 201 : 200);
}, { limit: 30 });
