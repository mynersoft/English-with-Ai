import { ok } from "@/lib/response";
import { buildPublicOrigin } from "@/lib/sslcommerz";
import { withAuth } from "@/middleware/auth";
import { initCourseSslCommerzPayment } from "@/modules/payments/sslcommerz.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const POST = withAuth(async (req, { user }) => {
  const body = await req.json().catch(() => ({}));
  const origin = buildPublicOrigin(req);
  const data = await initCourseSslCommerzPayment({
    userDoc: user,
    courseId: body?.courseId,
    cusPhone: body?.cusPhone || "",
    origin,
  });
  return ok(data, 201);
}, { limit: 20 });
