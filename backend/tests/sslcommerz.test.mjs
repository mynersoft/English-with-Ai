import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import {
  DEFAULT_PREMIUM_COURSE_PRICE_BDT,
  escapeHtml,
  generateSslCommerzTranId,
  getSslCommerzConfig,
  resolveCourseCheckoutAmount,
  verifySslCommerzValidationPayload,
} from "../src/lib/sslcommerz.js";

test("getSslCommerzConfig reads env cleanly and defaults to sandbox BDT", () => {
  const unconfigured = getSslCommerzConfig({});
  assert.equal(unconfigured.configured, false);
  assert.equal(unconfigured.isSandbox, true);
  assert.equal(unconfigured.currency, "BDT");
  assert.equal(unconfigured.baseUrl, "https://sandbox.sslcommerz.com");

  const live = getSslCommerzConfig({
    SSLCOMMERZ_STORE_ID: "store_123",
    SSLCOMMERZ_STORE_PASSWORD: "secret_pw",
    SSLCOMMERZ_SANDBOX: "false",
    SSLCOMMERZ_CURRENCY: "BDT",
  });
  assert.equal(live.configured, true);
  assert.equal(live.isSandbox, false);
  assert.equal(live.baseUrl, "https://securepay.sslcommerz.com");
});

test("resolveCourseCheckoutAmount enforces server-side pricing floor", () => {
  assert.equal(resolveCourseCheckoutAmount({ price: 990 }), 990);
  assert.equal(resolveCourseCheckoutAmount({ price: 0 }), DEFAULT_PREMIUM_COURSE_PRICE_BDT);
  assert.equal(resolveCourseCheckoutAmount({ price: -50 }, 750), 750);
});

test("generateSslCommerzTranId produces compact unique IDs", () => {
  const t1 = generateSslCommerzTranId("65f0a1b2c3d4e5f6a7b8c9d0");
  const t2 = generateSslCommerzTranId("65f0a1b2c3d4e5f6a7b8c9d0");
  assert.notEqual(t1, t2);
  assert.ok(t1.startsWith("EAI_"));
  assert.ok(t1.length <= 30);
});

test("verifySslCommerzValidationPayload accepts valid matching transaction and blocks tampering", () => {
  const purchase = {
    tranId: "EAI_C9D0_1234",
    amount: 499,
    currency: "BDT",
  };

  const ok = verifySslCommerzValidationPayload(
    {
      status: "VALID",
      tran_id: "EAI_C9D0_1234",
      val_id: "VAL_999",
      amount: "499.00",
      currency_type: "BDT",
      bank_tran_id: "BANK_1",
      card_type: "BKASH-BKash",
      card_issuer: "bKash",
      store_amount: "486.50",
    },
    purchase
  );
  assert.equal(ok.valid, true);
  assert.equal(ok.valId, "VAL_999");

  const wrongAmount = verifySslCommerzValidationPayload(
    { status: "VALID", tran_id: "EAI_C9D0_1234", val_id: "VAL_999", amount: "10.00" },
    purchase
  );
  assert.equal(wrongAmount.valid, false);

  const wrongTran = verifySslCommerzValidationPayload(
    { status: "VALID", tran_id: "OTHER_TRAN", val_id: "VAL_999", amount: "499.00" },
    purchase
  );
  assert.equal(wrongTran.valid, false);

  const invalidStatus = verifySslCommerzValidationPayload(
    { status: "FAILED", tran_id: "EAI_C9D0_1234", val_id: "VAL_999", amount: "499.00" },
    purchase
  );
  assert.equal(invalidStatus.valid, false);
});

test("escapeHtml prevents XSS injection in callback HTML", () => {
  const escaped = escapeHtml('<script>alert("xss")</script>');
  assert.equal(escaped.includes("<script>"), false);
  assert.ok(escaped.includes("&lt;script&gt;"));
});

test("SSLCOMMERZ payment routes enforce auth and server-side validation", () => {
  const initSrc = fs.readFileSync(path.resolve("backend/src/app/api/payments/sslcommerz/init/route.js"), "utf8");
  const statusSrc = fs.readFileSync(path.resolve("backend/src/app/api/payments/sslcommerz/status/route.js"), "utf8");
  const coursesSrc = fs.readFileSync(path.resolve("backend/src/app/api/payments/courses/route.js"), "utf8");
  const serviceSrc = fs.readFileSync(path.resolve("backend/src/modules/payments/sslcommerz.service.js"), "utf8");

  assert.match(initSrc, /withAuth\(/);
  assert.match(statusSrc, /withAuth\(/);
  assert.match(coursesSrc, /withAuth\(/);
  assert.match(serviceSrc, /validateSslCommerzOrder/);
  assert.equal(serviceSrc.includes("body.amount"), false);
});
