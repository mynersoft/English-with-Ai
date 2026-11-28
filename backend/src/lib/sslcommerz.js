import crypto from "crypto";

export const DEFAULT_PREMIUM_COURSE_PRICE_BDT = 499;

export function getSslCommerzConfig(env = process.env) {
  const storeId = String(env.SSLCOMMERZ_STORE_ID || "").trim();
  const storePassword = String(env.SSLCOMMERZ_STORE_PASSWORD || "").trim();
  const rawSandbox = String(env.SSLCOMMERZ_SANDBOX || "").trim().toLowerCase();
  const isSandbox = rawSandbox !== "false" && rawSandbox !== "0";
  const rawCurrency = String(env.SSLCOMMERZ_CURRENCY || "BDT").trim().toUpperCase();
  const currency = ["BDT", "USD", "EUR", "GBP"].includes(rawCurrency) ? rawCurrency : "BDT";
  const parsedDefault = Number(env.SSLCOMMERZ_DEFAULT_PRICE || DEFAULT_PREMIUM_COURSE_PRICE_BDT);
  const defaultPrice = Number.isFinite(parsedDefault) && parsedDefault >= 10 ? parsedDefault : DEFAULT_PREMIUM_COURSE_PRICE_BDT;
  const baseUrl = isSandbox ? "https://sandbox.sslcommerz.com" : "https://securepay.sslcommerz.com";

  return {
    configured: Boolean(storeId && storePassword),
    storeId,
    storePassword,
    isSandbox,
    currency,
    defaultPrice,
    baseUrl,
  };
}

export function resolveCourseCheckoutAmount(course, defaultPrice = DEFAULT_PREMIUM_COURSE_PRICE_BDT) {
  const raw = Number(course?.price || 0);
  if (Number.isFinite(raw) && raw >= 10) {
    return Number(raw.toFixed(2));
  }
  const fallback = Number.isFinite(Number(defaultPrice)) && Number(defaultPrice) >= 10
    ? Number(defaultPrice)
    : DEFAULT_PREMIUM_COURSE_PRICE_BDT;
  return Number(fallback.toFixed(2));
}

export function generateSslCommerzTranId(courseId = "") {
  const shortCourse = String(courseId || "")
    .replace(/[^a-zA-Z0-9]/g, "")
    .slice(-6)
    .toUpperCase() || "COURSE";
  const ts = Date.now().toString(36).toUpperCase();
  const rand = crypto.randomBytes(4).toString("hex").toUpperCase();
  return `EAI_${shortCourse}_${ts}_${rand}`.slice(0, 30);
}

export function buildPublicOrigin(request, env = process.env) {
  const forwardedProto = request?.headers?.get("x-forwarded-proto")?.split(",")[0]?.trim();
  const forwardedHost = request?.headers?.get("x-forwarded-host")?.split(",")[0]?.trim();
  if (forwardedHost) {
    const proto = forwardedProto === "http" ? "http" : "https";
    return `${proto}://${forwardedHost}`.replace(/\/+$/, "");
  }
  if (request?.url) {
    try {
      const parsed = new URL(request.url);
      if (parsed.protocol === "http:" || parsed.protocol === "https:") {
        return parsed.origin.replace(/\/+$/, "");
      }
    } catch {
      // fall through
    }
  }
  const configured = String(env.BACKEND_BASE_URL || "").trim().replace(/\/+$/, "");
  return configured;
}

export function verifySslCommerzValidationPayload(validationData, purchase) {
  if (!validationData || typeof validationData !== "object") {
    return { valid: false, reason: "Empty validation response from SSLCOMMERZ." };
  }
  const status = String(validationData.status || "").trim().toUpperCase();
  if (status !== "VALID" && status !== "VALIDATED") {
    return { valid: false, reason: `SSLCOMMERZ validation returned status "${status || "UNKNOWN"}".` };
  }

  const tranId = String(validationData.tran_id || "").trim();
  if (!tranId || tranId !== String(purchase?.tranId || "").trim()) {
    return { valid: false, reason: "Transaction ID mismatch during SSLCOMMERZ validation." };
  }

  const valId = String(validationData.val_id || "").trim();
  if (!valId) {
    return { valid: false, reason: "Missing val_id in SSLCOMMERZ validation response." };
  }

  const expectedAmount = Number(purchase?.amount || 0);
  const expectedCurrency = String(purchase?.currency || "BDT").trim().toUpperCase();
  const paidAmount = Number(validationData.amount);
  const currencyAmount = Number(validationData.currency_amount);
  const currencyType = String(validationData.currency_type || validationData.currency || expectedCurrency)
    .trim()
    .toUpperCase();

  const amountMatches =
    (Number.isFinite(paidAmount) && Math.abs(paidAmount - expectedAmount) <= 0.5) ||
    (currencyType === expectedCurrency &&
      Number.isFinite(currencyAmount) &&
      Math.abs(currencyAmount - expectedAmount) <= 0.5);

  if (!amountMatches) {
    return {
      valid: false,
      reason: `Paid amount mismatch (expected ${expectedAmount} ${expectedCurrency}, received ${validationData.amount}).`,
    };
  }

  return {
    valid: true,
    reason: "",
    valId,
    bankTranId: String(validationData.bank_tran_id || "").trim(),
    cardType: String(validationData.card_type || "").trim(),
    cardIssuer: String(validationData.card_issuer || "").trim(),
    storeAmount: Number(validationData.store_amount || 0) || 0,
  };
}

export function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

export async function initiateSslCommerzSession({ config, payload }) {
  const form = new URLSearchParams();
  for (const [k, v] of Object.entries(payload || {})) {
    if (v !== undefined && v !== null) {
      form.set(k, String(v));
    }
  }

  const res = await fetch(`${config.baseUrl}/gwprocess/v4/api.php`, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      Accept: "application/json",
    },
    body: form.toString(),
    signal: AbortSignal.timeout(15_000),
  });

  const data = await res.json().catch(() => null);
  if (!res.ok || !data || String(data.status || "").toUpperCase() !== "SUCCESS" || !data.GatewayPageURL) {
    const reason = data?.failedreason || data?.message || `SSLCOMMERZ session init failed (${res.status})`;
    const err = new Error(reason);
    err.gatewayResponse = data;
    throw err;
  }

  return {
    gatewayUrl: String(data.GatewayPageURL).trim(),
    sessionKey: String(data.sessionkey || "").trim(),
  };
}

export async function validateSslCommerzOrder({ config, valId }) {
  const url = new URL(`${config.baseUrl}/validator/api/validationserverAPI.php`);
  url.searchParams.set("val_id", String(valId || "").trim());
  url.searchParams.set("store_id", config.storeId);
  url.searchParams.set("store_passwd", config.storePassword);
  url.searchParams.set("v", "1");
  url.searchParams.set("format", "json");

  const res = await fetch(url.toString(), {
    method: "GET",
    headers: { Accept: "application/json" },
    signal: AbortSignal.timeout(15_000),
  });

  const data = await res.json().catch(() => null);
  if (!res.ok || !data) {
    throw new Error(`SSLCOMMERZ validation API error (${res.status})`);
  }
  return data;
}
