import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { getSslCommerzPurchaseStatus } from "@/modules/payments/sslcommerz.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAuth(async (req, { user }) => {
  const { searchParams } = new URL(req.url);
  const tranId = searchParams.get("tranId") || searchParams.get("tran_id") || "";
  const data = await getSslCommerzPurchaseStatus({
    userDoc: user,
    tranId,
  });
  return ok(data);
}, { limit: 60 });
