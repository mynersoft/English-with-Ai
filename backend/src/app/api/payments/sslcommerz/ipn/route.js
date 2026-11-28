import { ok } from "@/lib/response";
import { apiHandler } from "@/lib/apiHandler";
import { connectDB } from "@/lib/db";
import { processSslCommerzIpn } from "@/modules/payments/sslcommerz.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const POST = apiHandler(async (req) => {
  await connectDB();
  const params = {};
  const contentType = String(req.headers.get("content-type") || "").toLowerCase();

  if (contentType.includes("application/x-www-form-urlencoded") || contentType.includes("multipart/form-data")) {
    const form = await req.formData().catch(() => null);
    if (form) {
      for (const [k, v] of form.entries()) {
        params[k] = typeof v === "string" ? v : "";
      }
    }
  } else {
    const json = await req.json().catch(() => null);
    if (json && typeof json === "object") {
      Object.assign(params, json);
    }
  }

  const data = await processSslCommerzIpn({ params });
  return ok(data);
}, { limit: 60 });
