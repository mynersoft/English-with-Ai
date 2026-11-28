import { NextResponse } from "next/server";
import { apiHandler } from "@/lib/apiHandler";
import { connectDB } from "@/lib/db";
import { escapeHtml } from "@/lib/sslcommerz";
import { processSslCommerzCallback } from "@/modules/payments/sslcommerz.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

async function extractCallbackParams(req) {
  const { searchParams } = new URL(req.url);
  const params = {
    outcome: searchParams.get("outcome") || "",
    tran_id: searchParams.get("tran_id") || "",
    val_id: searchParams.get("val_id") || "",
    error: searchParams.get("error") || "",
  };

  if (req.method === "POST") {
    const contentType = String(req.headers.get("content-type") || "").toLowerCase();
    if (contentType.includes("application/x-www-form-urlencoded") || contentType.includes("multipart/form-data")) {
      const form = await req.formData().catch(() => null);
      if (form) {
        if (form.get("tran_id")) params.tran_id = String(form.get("tran_id"));
        if (form.get("val_id")) params.val_id = String(form.get("val_id"));
        if (form.get("error")) params.error = String(form.get("error"));
        if (!params.outcome && form.get("status")) {
          const st = String(form.get("status")).toUpperCase();
          if (st === "VALID" || st === "VALIDATED") params.outcome = "success";
          else if (st === "CANCELLED") params.outcome = "cancel";
          else params.outcome = "fail";
        }
      }
    } else {
      const body = await req.json().catch(() => null);
      if (body && typeof body === "object") {
        if (body.tran_id) params.tran_id = String(body.tran_id);
        if (body.val_id) params.val_id = String(body.val_id);
        if (body.error) params.error = String(body.error);
      }
    }
  }
  return params;
}

function renderStatusHtml(result) {
  const isPaid = result?.status === "paid";
  const isCancelled = result?.status === "cancelled";
  const heading = isPaid
    ? "Payment Verified"
    : isCancelled
      ? "Payment Cancelled"
      : "Payment Incomplete";
  const badgeColor = isPaid ? "#0E7A4D" : isCancelled ? "#B45309" : "#DC2626";
  const safeMsg = escapeHtml(result?.message || "Returning to course...");
  const safeTran = escapeHtml(result?.purchase?.tranId || "");
  const safeTitle = escapeHtml(result?.purchase?.courseTitle || "Premium Course");

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>${heading}</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; margin: 0; background: #0B120F; color: #F8FAFC; display: flex; min-height: 100vh; align-items: center; justify-content: center; padding: 24px; }
    .card { width: 100%; max-width: 420px; background: #131E19; border: 1px solid #24352E; border-radius: 20px; padding: 24px; text-align: center; }
    .badge { display: inline-block; padding: 6px 14px; border-radius: 999px; background: ${badgeColor}; color: #fff; font-weight: 700; font-size: 13px; margin-bottom: 12px; }
    h1 { font-size: 22px; margin: 6px 0 10px; }
    p { font-size: 14px; color: #CBD5E1; line-height: 1.5; margin: 0 0 12px; }
    .meta { font-size: 12px; color: #94A3B8; word-break: break-all; }
  </style>
</head>
<body>
  <div class="card">
    <div class="badge">${heading}</div>
    <h1>${safeTitle}</h1>
    <p>${safeMsg}</p>
    ${safeTran ? `<div class="meta">Transaction: ${safeTran}</div>` : ""}
  </div>
</body>
</html>`;
}

async function handleCallback(req) {
  await connectDB();
  const params = await extractCallbackParams(req);
  const result = await processSslCommerzCallback({
    outcome: params.outcome,
    tranId: params.tran_id,
    valId: params.val_id,
    errorMessage: params.error,
  });

  return new NextResponse(renderStatusHtml(result), {
    status: 200,
    headers: { "Content-Type": "text/html; charset=utf-8" },
  });
}

export const POST = apiHandler(handleCallback, { limit: 60 });
export const GET = apiHandler(handleCallback, { limit: 60 });
