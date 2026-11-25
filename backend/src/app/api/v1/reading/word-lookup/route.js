import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { lookupReadingWord } from "@/modules/reading/reading.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAuth(async (req, { user }) => {
  const { searchParams } = new URL(req.url);
  const word = searchParams.get("word") || "";
  const passageId = searchParams.get("passageId") || null;
  const data = await lookupReadingWord({
    userId: user.firebaseUid,
    rawWord: word,
    passageId,
  });
  return ok(data);
}, { limit: 90 });
