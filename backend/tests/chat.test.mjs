import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  computeCorrectionTokens,
  correctEnglishChatMessage,
  extractHandwritingTextFromImage,
} from "../src/lib/chatCorrection.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const backendRoot = path.resolve(__dirname, "..");

function collectRouteFiles(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...collectRouteFiles(full));
    } else if (entry.isFile() && entry.name === "route.js") {
      files.push(full);
    }
  }
  return files;
}

test("computeCorrectionTokens highlights changed words accurately", () => {
  const tokens = computeCorrectionTokens("I foel like to go", "I feel like going");
  assert.deepEqual(tokens, [
    { text: "I", changed: false },
    { text: "feel", changed: true },
    { text: "like", changed: false },
    { text: "going", changed: true },
  ]);
});

test("correctEnglishChatMessage returns unavailable cleanly when AI_API_KEY is unset", async () => {
  const prevKey = process.env.AI_API_KEY;
  delete process.env.AI_API_KEY;
  try {
    const res = await correctEnglishChatMessage("I foel like to go");
    assert.equal(res.status, "unavailable");
    assert.equal(res.hasMistake, false);
  } finally {
    if (prevKey !== undefined) process.env.AI_API_KEY = prevKey;
  }
});

test("extractHandwritingTextFromImage validates inputs and handles missing AI_API_KEY", async () => {
  const emptyRes = await extractHandwritingTextFromImage({ imageBase64: "" });
  assert.equal(emptyRes.errorCode, "IMAGE_REQUIRED");

  const badMime = await extractHandwritingTextFromImage({ imageBase64: "AAAA", mimeType: "application/pdf" });
  assert.equal(badMime.errorCode, "INVALID_MIME");

  const prevKey = process.env.AI_API_KEY;
  delete process.env.AI_API_KEY;
  try {
    const noKey = await extractHandwritingTextFromImage({ imageBase64: "AAAA", mimeType: "image/jpeg" });
    assert.equal(noKey.aiConfigured, false);
    assert.equal(noKey.errorCode, "AI_NOT_CONFIGURED");
  } finally {
    if (prevKey !== undefined) process.env.AI_API_KEY = prevKey;
  }
});

test("All student chat API routes enforce withAuth and never trust client userId", () => {
  const chatRoutes = collectRouteFiles(path.join(backendRoot, "src/app/api/chat"));
  assert.ok(chatRoutes.length >= 6, "Expected all student chat route files including OCR");
  for (const file of chatRoutes) {
    const src = fs.readFileSync(file, "utf8");
    assert.ok(src.includes("withAuth("), `Route ${path.relative(backendRoot, file)} must wrap handlers with withAuth`);
    assert.ok(
      !src.includes("searchParams.get(\"userId\")") && !src.includes("body.userId"),
      `Route ${path.relative(backendRoot, file)} must not trust client-supplied userId`
    );
  }
});
