export const MAX_FCM_TOKENS_PER_USER = 5;

const INVALID_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
  "messaging/invalid-argument",
]);

export function sanitizeFcmToken(tokenRaw) {
  const token = String(tokenRaw || "").trim();
  if (!token || token.length < 16 || token.length > 4096) return "";
  return token;
}

export function mergeUserFcmTokens(existingTokens = [], newTokenRaw) {
  const token = sanitizeFcmToken(newTokenRaw);
  const cleaned = Array.isArray(existingTokens)
    ? existingTokens.map((t) => sanitizeFcmToken(t)).filter(Boolean)
    : [];
  if (!token) return cleaned.slice(-MAX_FCM_TOKENS_PER_USER);

  const withoutDuplicate = cleaned.filter((t) => t !== token);
  withoutDuplicate.push(token);
  return withoutDuplicate.slice(-MAX_FCM_TOKENS_PER_USER);
}

export function removeUserFcmToken(existingTokens = [], tokenRaw) {
  const token = sanitizeFcmToken(tokenRaw);
  const cleaned = Array.isArray(existingTokens)
    ? existingTokens.map((t) => sanitizeFcmToken(t)).filter(Boolean)
    : [];
  if (!token) return cleaned;
  return cleaned.filter((t) => t !== token);
}

export function truncateMessagePreview(text, maxLen = 140) {
  const normalized = String(text || "").replace(/\s+/g, " ").trim();
  if (!normalized) return "New message";
  if (normalized.length <= maxLen) return normalized;
  return `${normalized.slice(0, Math.max(1, maxLen - 1)).trimEnd()}…`;
}

export function resolveNotificationRoomTitle({ room, senderName }) {
  if (room?.roomType === "direct") {
    return String(senderName || room?.name || "Student").trim() || "Student";
  }
  return String(room?.name || "Study Room").trim() || "Study Room";
}

export function buildChatPushDataPayload({ message, room, senderName }) {
  const safeSenderName = String(senderName || message?.senderName || "Learner").trim() || "Learner";
  const roomType = room?.roomType === "direct" ? "direct" : "group";
  const roomName = resolveNotificationRoomTitle({ room, senderName: safeSenderName });
  const roomEmoji = String(room?.avatarEmoji || (roomType === "direct" ? "💬" : "🦁")).trim();

  return {
    type: "chat_message",
    messageId: String(message?.id || message?._id || ""),
    roomId: String(room?.id || room?._id || message?.roomId || ""),
    roomName,
    roomType,
    roomEmoji,
    senderId: String(message?.senderId || ""),
    senderName: safeSenderName,
    senderBadge: String(message?.senderBadge || ""),
    textPreview: truncateMessagePreview(message?.text || "", 160),
    createdAt: String(message?.createdAt || new Date().toISOString()),
  };
}

export function extractInvalidFcmTokens(tokens = [], batchResponse) {
  if (!Array.isArray(tokens) || !batchResponse || !Array.isArray(batchResponse.responses)) {
    return [];
  }
  const invalid = [];
  batchResponse.responses.forEach((res, idx) => {
    if (!res || res.success) return;
    const code = res.error?.code || "";
    if (INVALID_TOKEN_CODES.has(code) && tokens[idx]) {
      invalid.push(tokens[idx]);
    }
  });
  return Array.from(new Set(invalid));
}
