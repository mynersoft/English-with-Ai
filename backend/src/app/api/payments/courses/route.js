import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { listStudentCoursePurchases } from "@/modules/payments/sslcommerz.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = withAuth(async (_req, { user }) => {
  const data = await listStudentCoursePurchases({ userDoc: user });
  return ok(data);
}, { limit: 60 });
