import { apiHandler } from "@/lib/apiHandler";
import { connectDB } from "@/lib/db";
import { ok } from "@/lib/response";
import { verifyRequestToken } from "@/middleware/auth";
import { assertProvider, toPublicUser, upsertUserFromToken } from "@/modules/auth/auth.service";
import { readJson, validateRegister } from "@/validators/auth.validator";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// POST /api/v1/auth/email/register  body: { name }  (Bearer token from Firebase createUser)
export const POST = apiHandler(async (request) => {
  const [decoded, body] = await Promise.all([
    verifyRequestToken(request),
    readJson(request),
    connectDB(),
  ]);
  assertProvider(decoded, "password");
  const { name } = validateRegister(body);
  const { user, isNewUser } = await upsertUserFromToken(decoded, { name });
  return ok({ user: toPublicUser(user), isNewUser }, isNewUser ? 201 : 200);
}, { limit: 10 });
