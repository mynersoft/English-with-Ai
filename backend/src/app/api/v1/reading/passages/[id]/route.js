import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { getReadingPassageDetail } from "@/modules/reading/reading.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAuth(async (_req, { params, user }) => {
  const { id } = await params;
  const data = await getReadingPassageDetail({
    userId: user.firebaseUid,
    passageId: id,
    includeUnpublished: false,
  });
  return ok(data);
}, { limit: 60 });
