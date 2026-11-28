import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  buildCallInvitePushPayload,
  buildClientJoinTransportConfig,
  createLiveKitAccessToken,
  generateSecureRoomCode,
  normalizeRoomCode,
  resolveCallProviderConfig,
  resolveIceServers,
} from "../src/lib/callProvider.js";

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

test("resolveCallProviderConfig defaults to zero-cost free_p2p mode with free STUN servers", () => {
  const config = resolveCallProviderConfig({});
  assert.equal(config.provider, "free_p2p");
  assert.equal(config.costTier, "free");
  assert.equal(config.transportMode, "p2p_mesh");
  assert.equal(config.signalingMode, "http_poll");
  assert.equal(config.maxGroupParticipants, 6);
  assert.equal(config.hasTurnRelay, false);
  assert.equal(config.upgradeReady, true);

  const ice = resolveIceServers({});
  assert.ok(ice.length >= 2);
  assert.ok(ice[0].urls.some((u) => u.startsWith("stun:")));
});

test("resolveCallProviderConfig falls back safely to free_p2p when paid SFU keys are missing", () => {
  const config = resolveCallProviderConfig({ CALL_PROVIDER: "livekit" });
  assert.equal(config.provider, "free_p2p");
  assert.equal(config.costTier, "free");
  assert.ok(config.fallbackReason.includes("LIVEKIT"));
});

test("resolveCallProviderConfig & buildClientJoinTransportConfig switch seamlessly to LiveKit when configured", () => {
  const env = {
    CALL_PROVIDER: "livekit",
    LIVEKIT_URL: "wss://example.livekit.cloud",
    LIVEKIT_API_KEY: "LK_TEST_KEY",
    LIVEKIT_API_SECRET: "super_secret_livekit_key_123456",
    CALL_SFU_MAX_PARTICIPANTS: "30",
  };
  const config = resolveCallProviderConfig(env);
  assert.equal(config.provider, "livekit");
  assert.equal(config.costTier, "paid");
  assert.equal(config.transportMode, "sfu");
  assert.equal(config.maxGroupParticipants, 30);

  const transport = buildClientJoinTransportConfig({
    room: { _id: "room123" },
    user: { _id: "user456", name: "Mahir" },
    env,
  });
  assert.equal(transport.provider, "livekit");
  assert.equal(transport.sfu.serverUrl, "wss://example.livekit.cloud");
  assert.equal(transport.sfu.roomName, "engai_room123");
  assert.equal(transport.sfu.token.split(".").length, 3);
  assert.ok(!transport.sfu.token.includes(env.LIVEKIT_API_SECRET));
});

test("createLiveKitAccessToken encodes valid JWT header and payload", () => {
  const jwt = createLiveKitAccessToken({
    apiKey: "apiKey1",
    apiSecret: "secret1",
    roomName: "engai_r1",
    identity: "u1",
    name: "Ayesha",
  });
  const [h, p, s] = jwt.split(".");
  assert.ok(h && p && s);
  const decodedPayload = JSON.parse(Buffer.from(p, "base64url").toString("utf8"));
  assert.equal(decodedPayload.iss, "apiKey1");
  assert.equal(decodedPayload.sub, "u1");
  assert.equal(decodedPayload.video.room, "engai_r1");
  assert.equal(decodedPayload.video.roomJoin, true);
});

test("generateSecureRoomCode and normalizeRoomCode enforce unambiguous uppercase codes", () => {
  const code = generateSecureRoomCode(6);
  assert.equal(code.length, 6);
  assert.match(code, /^[A-Z0-9]{6}$/);
  assert.equal(normalizeRoomCode(" eng-4k9 "), "ENG4K9");
});

test("buildCallInvitePushPayload builds safe call_invite payload", () => {
  const payload = buildCallInvitePushPayload({
    room: {
      _id: "c1",
      mode: "direct",
      hostUserId: "u1",
      hostName: "Mahir",
      title: "1-on-1 Practice",
    },
    callerName: "Mahir",
  });
  assert.equal(payload.type, "call_invite");
  assert.equal(payload.callRoomId, "c1");
  assert.equal(payload.callerName, "Mahir");
});

test("All video call API routes wrap handlers with withAuth and never trust client userId", () => {
  const callRoutes = collectRouteFiles(path.join(backendRoot, "src/app/api/calls"));
  assert.ok(callRoutes.length >= 9, "Expected all 9 call API route files");
  for (const file of callRoutes) {
    const src = fs.readFileSync(file, "utf8");
    assert.ok(
      src.includes("withAuth("),
      `Route ${path.relative(backendRoot, file)} must wrap handlers with withAuth`
    );
    assert.ok(
      !src.includes('searchParams.get("userId")') && !src.includes("body.userId"),
      `Route ${path.relative(backendRoot, file)} must not trust client-supplied userId`
    );
  }
});
