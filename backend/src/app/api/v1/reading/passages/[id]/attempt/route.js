import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import { submitReadingAttempt } from "@/modules/reading/reading.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const POST = withAuth(async (req, { params, user }) => {
  const { id } = await params;
  const body = await readJson(req);
  const data = await submitReadingAttempt({
    userDoc: user,
    passageId: id,
    body,
  });
  return ok(data);
}, { limit: 40 });
