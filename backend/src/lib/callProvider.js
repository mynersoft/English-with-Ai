import crypto from "node:crypto";

export const SUPPORTED_CALL_PROVIDERS = ["free_p2p", "livekit", "agora", "daily"];

export const DEFAULT_FREE_ICE_SERVERS = [
  {
    urls: [
      "stun:stun.l.google.com:19302",
      "stun:stun1.l.google.com:19302",
    ],
  },
  {
    urls: ["stun:stun.cloudflare.com:3478"],
  },
];

const ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export function generateSecureRoomCode(length = 6) {
  const bytes = crypto.randomBytes(length);
  let code = "";
  for (let i = 0; i < length; i += 1) {
    code += ROOM_CODE_ALPHABET[bytes[i] % ROOM_CODE_ALPHABET.length];
  }
  return code;
}

export function normalizeRoomCode(raw) {
  return String(raw || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "")
    .slice(0, 12);
}

function base64UrlEncode(input) {
  const buf = Buffer.isBuffer(input) ? input : Buffer.from(String(input), "utf8");
  return buf
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

/**
 * Mints a standard LiveKit JWT access token using Node's built-in crypto so
 * upgrading CALL_PROVIDER=livekit later needs zero extra npm dependencies.
 */
export function createLiveKitAccessToken({
  apiKey,
  apiSecret,
  roomName,
  identity,
  name,
  ttlSeconds = 3600,
}) {
  if (!apiKey || !apiSecret || !roomName || !identity) return "";
  const nowSec = Math.floor(Date.now() / 1000);
  const header = base64UrlEncode(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = base64UrlEncode(
    JSON.stringify({
      iss: apiKey,
      sub: String(identity),
      name: String(name || "Learner"),
      nbf: nowSec - 5,
      exp: nowSec + ttlSeconds,
      video: {
        roomJoin: true,
        room: String(roomName),
        canPublish: true,
        canSubscribe: true,
        canPublishData: true,
      },
    })
  );
  const signature = base64UrlEncode(
    crypto.createHmac("sha256", apiSecret).update(`${header}.${payload}`).digest()
  );
  return `${header}.${payload}.${signature}`;
}

export function resolveIceServers(env = process.env, { includeTurnCredentials = true } = {}) {
  const servers = DEFAULT_FREE_ICE_SERVERS.map((entry) => ({
    urls: [...entry.urls],
  }));

  const customJson = String(env.CALL_ICE_SERVERS_JSON || "").trim();
  if (customJson) {
    try {
      const parsed = JSON.parse(customJson);
      if (Array.isArray(parsed)) {
        const sanitized = parsed
          .filter((item) => item && (typeof item.urls === "string" || Array.isArray(item.urls)))
          .map((item) => {
            const urls = Array.isArray(item.urls)
              ? item.urls.map((u) => String(u).trim()).filter(Boolean)
              : [String(item.urls).trim()].filter(Boolean);
            const out = { urls };
            if (includeTurnCredentials && item.username) {
              out.username = String(item.username);
            }
            if (includeTurnCredentials && item.credential) {
              out.credential = String(item.credential);
            }
            return out;
          })
          .filter((item) => item.urls.length > 0);
        if (sanitized.length > 0) {
          return sanitized;
        }
      }
    } catch {
      // Ignore malformed CALL_ICE_SERVERS_JSON and keep safe defaults.
    }
  }

  const turnUrl = String(env.TURN_SERVER_URL || "").trim();
  if (turnUrl) {
    const turnEntry = { urls: [turnUrl] };
    if (includeTurnCredentials) {
      const turnUser = String(env.TURN_USERNAME || "").trim();
      const turnCred = String(env.TURN_CREDENTIAL || "").trim();
      if (turnUser) turnEntry.username = turnUser;
      if (turnCred) turnEntry.credential = turnCred;
    }
    servers.push(turnEntry);
  }

  return servers;
}

export function resolveCallProviderConfig(env = process.env) {
  const requested = String(env.CALL_PROVIDER || "free_p2p").trim().toLowerCase() || "free_p2p";
  const turnConfigured = Boolean(String(env.TURN_SERVER_URL || "").trim() || String(env.CALL_ICE_SERVERS_JSON || "").trim());

  const livekitReady = Boolean(
    String(env.LIVEKIT_URL || "").trim() &&
      String(env.LIVEKIT_API_KEY || "").trim() &&
      String(env.LIVEKIT_API_SECRET || "").trim()
  );
  const agoraReady = Boolean(String(env.AGORA_APP_ID || "").trim());
  const dailyReady = Boolean(String(env.DAILY_API_KEY || "").trim());

  let activeProvider = "free_p2p";
  let fallbackReason = null;

  if (requested === "livekit") {
    if (livekitReady) activeProvider = "livekit";
    else fallbackReason = "LIVEKIT credentials not configured; using free_p2p";
  } else if (requested === "agora") {
    if (agoraReady) activeProvider = "agora";
    else fallbackReason = "AGORA_APP_ID not configured; using free_p2p";
  } else if (requested === "daily") {
    if (dailyReady) activeProvider = "daily";
    else fallbackReason = "DAILY_API_KEY not configured; using free_p2p";
  }

  const isPaidSfu = activeProvider !== "free_p2p";
  const maxGroupParticipants = isPaidSfu
    ? Math.max(4, Math.min(50, Number(env.CALL_SFU_MAX_PARTICIPANTS || 25) || 25))
    : Math.max(2, Math.min(8, Number(env.CALL_P2P_MAX_PARTICIPANTS || 6) || 6));

  const providerLabel = {
    free_p2p: turnConfigured ? "Free WebRTC P2P + TURN" : "Free WebRTC P2P ($0)",
    livekit: "LiveKit Cloud SFU",
    agora: "Agora RTC",
    daily: "Daily Video SFU",
  }[activeProvider];

  return {
    provider: activeProvider,
    requestedProvider: requested,
    providerLabel,
    costTier: isPaidSfu || turnConfigured ? "paid" : "free",
    transportMode: isPaidSfu ? "sfu" : "p2p_mesh",
    signalingMode: isPaidSfu ? "provider_sdk" : "http_poll",
    maxGroupParticipants,
    hasTurnRelay: turnConfigured,
    upgradeReady: true,
    fallbackReason,
  };
}

export function buildClientJoinTransportConfig({ room, user, env = process.env }) {
  const summary = resolveCallProviderConfig(env);
  const iceServers = resolveIceServers(env, { includeTurnCredentials: true });
  const roomIdStr = String(room?._id || room?.id || "");
  const userIdStr = String(user?._id || user?.id || "");

  let sfu = null;
  if (summary.provider === "livekit") {
    const serverUrl = String(env.LIVEKIT_URL || "").trim();
    const roomName = `engai_${roomIdStr}`;
    const token = createLiveKitAccessToken({
      apiKey: String(env.LIVEKIT_API_KEY || "").trim(),
      apiSecret: String(env.LIVEKIT_API_SECRET || "").trim(),
      roomName,
      identity: userIdStr,
      name: user?.name || "Learner",
    });
    sfu = {
      provider: "livekit",
      serverUrl,
      roomName,
      token,
      appId: null,
    };
  } else if (summary.provider === "agora") {
    sfu = {
      provider: "agora",
      serverUrl: null,
      roomName: `engai_${roomIdStr}`,
      token: null,
      appId: String(env.AGORA_APP_ID || "").trim(),
    };
  } else if (summary.provider === "daily") {
    const domain = String(env.DAILY_DOMAIN || "").trim().replace(/^https?:\/\//, "").replace(/\/$/, "");
    sfu = {
      provider: "daily",
      serverUrl: domain ? `https://${domain}/engai_${roomIdStr}` : null,
      roomName: `engai_${roomIdStr}`,
      token: null,
      appId: null,
    };
  }

  return {
    provider: summary.provider,
    providerLabel: summary.providerLabel,
    costTier: summary.costTier,
    transportMode: summary.transportMode,
    signalingMode: summary.signalingMode,
    maxGroupParticipants: summary.maxGroupParticipants,
    hasTurnRelay: summary.hasTurnRelay,
    iceServers,
    sfu,
  };
}

export function isOneOnOneCallMode(mode) {
  return mode === "direct" || mode === "random";
}

export function collectBusyUserIdsFromRooms(rooms = []) {
  const busy = new Set();
  for (const room of rooms || []) {
    if (!room) continue;
    const status = String(room.status || "");
    if (status !== "ringing" && status !== "active") continue;
    const participants = Array.isArray(room.participants) ? room.participants : [];
    for (const p of participants) {
      if (p?.userId) busy.add(String(p.userId));
    }
    if (status === "ringing" || participants.length > 0) {
      if (room.hostUserId) busy.add(String(room.hostUserId));
    }
    if (room.targetUserId && (status === "ringing" || isOneOnOneCallMode(room.mode))) {
      busy.add(String(room.targetUserId));
    }
  }
  return busy;
}

export function pickRandomFreeCandidate(candidates = [], onlineUserIds = new Set(), randomFn = Math.random) {
  if (!Array.isArray(candidates) || candidates.length === 0) return null;
  const onlinePool = candidates.filter((c) => onlineUserIds.has(String(c._id || c.id)));
  const pool = onlinePool.length > 0 ? onlinePool : candidates;
  const idx = Math.min(pool.length - 1, Math.max(0, Math.floor(randomFn() * pool.length)));
  return pool[idx];
}

export function buildCallInvitePushPayload({ room, callerName }) {
  const safeCaller = String(callerName || room?.hostName || "Student").trim() || "Student";
  return {
    type: "call_invite",
    callRoomId: String(room?._id || room?.id || ""),
    callMode: String(room?.mode || "direct"),
    callerId: String(room?.hostUserId || ""),
    callerName: safeCaller,
    title: String(room?.title || `${safeCaller} is calling`),
    createdAt: String(room?.createdAt ? new Date(room.createdAt).toISOString() : new Date().toISOString()),
  };
}
