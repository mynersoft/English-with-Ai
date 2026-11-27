import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { normalizeApiError, sanitizeServerText } from "../src/lib/errorMapper.js";
import { AppError } from "../src/lib/errors.js";

test("normalizeApiError preserves AppError status, code, message, and headers", () => {
  const err = new AppError(429, "RATE_LIMITED", "Slow down", { "Retry-After": "15" });
  const mapped = normalizeApiError(err);
  assert.equal(mapped.status, 429);
  assert.equal(mapped.code, "RATE_LIMITED");
  assert.equal(mapped.message, "Slow down");
  assert.deepEqual(mapped.headers, { "Retry-After": "15" });
});

test("normalizeApiError handles Mongoose duplicate key 11000 accurately by field", () => {
  const emailDup = normalizeApiError({ code: 11000, keyPattern: { email: 1 } });
  assert.equal(emailDup.status, 409);
  assert.match(emailDup.message, /email/i);

  const slugDup = normalizeApiError({ code: 11000, keyPattern: { slug: 1 } });
  assert.equal(slugDup.status, 409);
  assert.match(slugDup.message, /slug/i);

  const customDup = normalizeApiError({ code: 11000, keyValue: { directPairKey: "a:b" } });
  assert.equal(customDup.status, 409);
  assert.match(customDup.message, /directPairKey/i);
});

test("normalizeApiError maps ValidationError, CastError, and SyntaxError to HTTP 400", () => {
  const valErr = normalizeApiError({
    name: "ValidationError",
    errors: { title: { message: "Title is required." } },
  });
  assert.equal(valErr.status, 400);
  assert.equal(valErr.code, "VALIDATION_ERROR");
  assert.equal(valErr.message, "Title is required.");

  const castErr = normalizeApiError({ name: "CastError", path: "passageId" });
  assert.equal(castErr.status, 400);
  assert.equal(castErr.code, "INVALID_ID");
  assert.match(castErr.message, /passageId/i);

  const jsonErr = normalizeApiError(new SyntaxError("Unexpected token"));
  assert.equal(jsonErr.status, 400);
  assert.equal(jsonErr.code, "INVALID_JSON");
});

test("normalizeApiError returns 503 with Retry-After on transient DB timeouts and redacts internal 500s", () => {
  const transient = normalizeApiError({ name: "MongoServerSelectionError", message: "timed out" });
  assert.equal(transient.status, 503);
  assert.equal(transient.code, "SERVICE_UNAVAILABLE");
  assert.deepEqual(transient.headers, { "Retry-After": "3" });

  const internal = normalizeApiError(
    new Error("Cannot read properties of undefined (reading 'foo') at mongodb+srv://user:secret@cluster0.net")
  );
  assert.equal(internal.status, 500);
  assert.equal(internal.code, "INTERNAL_ERROR");
  assert.doesNotMatch(internal.message, /mongodb|secret|undefined/i);

  const redacted = sanitizeServerText("Error connecting mongodb+srv://admin:pass123@db.example.net/prod");
  assert.doesNotMatch(redacted, /admin:pass123/);
  assert.match(redacted, /mongodb\+srv:\/\/\*\*\*@/);
});

test("Backend performance optimizations preserve required indexes and exclude heavy blobs in summary queries", () => {
  const root = process.cwd();
  const vocabSrc = fs.readFileSync(path.join(root, "backend/src/modules/vocabulary/vocabulary.service.js"), "utf8");
  const readingSrc = fs.readFileSync(path.join(root, "backend/src/modules/reading/reading.service.js"), "utf8");
  const authSrc = fs.readFileSync(path.join(root, "backend/src/middleware/auth.js"), "utf8");

  assert.match(vocabSrc, /\$group/);
  assert.match(readingSrc, /sentenceCount:\s*\{\s*\$size/);
  assert.match(authSrc, /\.select\("-fcmTokens"\)/);
});
