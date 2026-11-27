import test from "node:test";
import assert from "node:assert/strict";
import {
  MAX_FCM_TOKENS_PER_USER,
  buildChatPushDataPayload,
  extractInvalidFcmTokens,
  mergeUserFcmTokens,
  removeUserFcmToken,
  resolveNotificationRoomTitle,
  truncateMessagePreview,
} from "../src/lib/chatNotifications.js";

test("mergeUserFcmTokens deduplicates and caps token count cleanly", () => {
  const existing = [
    "token_1111111111111111",
    "token_2222222222222222",
    "token_3333333333333333",
    "token_4444444444444444",
    "token_5555555555555555",
  ];
  const updated = mergeUserFcmTokens(existing, "token_6666666666666666");
  assert.equal(updated.length, MAX_FCM_TOKENS_PER_USER);
  assert.equal(updated[0], "token_2222222222222222");
  assert.equal(updated.at(-1), "token_6666666666666666");

  // Re-adding an existing token moves it to the end without duplicating
  const refreshed = mergeUserFcmTokens(updated, "token_2222222222222222");
  assert.equal(refreshed.length, MAX_FCM_TOKENS_PER_USER);
  assert.equal(refreshed.at(-1), "token_2222222222222222");
});

test("removeUserFcmToken removes matching token and ignores invalid inputs", () => {
  const tokens = ["token_aaaaaaaaaaaaaaaa", "token_bbbbbbbbbbbbbbbb"];
  assert.deepEqual(removeUserFcmToken(tokens, "token_aaaaaaaaaaaaaaaa"), [
    "token_bbbbbbbbbbbbbbbb",
  ]);
  assert.deepEqual(removeUserFcmToken(tokens, ""), tokens);
});

test("resolveNotificationRoomTitle uses sender name for direct rooms and room name for group rooms", () => {
  assert.equal(
    resolveNotificationRoomTitle({
      room: { roomType: "direct", name: "Placeholder" },
      senderName: "Nadia Rahman",
    }),
    "Nadia Rahman"
  );
  assert.equal(
    resolveNotificationRoomTitle({
      room: { roomType: "group", name: "Daily English Practice" },
      senderName: "Nadia Rahman",
    }),
    "Daily English Practice"
  );
});

test("buildChatPushDataPayload produces all-string FCM data map with truncated preview", () => {
  const longText = "Hello   everyone! ".repeat(20);
  const payload = buildChatPushDataPayload({
    room: {
      _id: "room_123",
      roomType: "group",
      name: "Daily English Practice",
      avatarEmoji: "🦁",
    },
    senderName: "Arif",
    message: {
      id: "msg_456",
      roomId: "room_123",
      senderId: "user_789",
      senderName: "Arif",
      senderBadge: "Beginner",
      text: longText,
      createdAt: "2026-11-27T10:00:00.000Z",
    },
  });

  assert.equal(payload.type, "chat_message");
  assert.equal(payload.roomId, "room_123");
  assert.equal(payload.messageId, "msg_456");
  assert.equal(payload.roomName, "Daily English Practice");
  assert.equal(payload.roomType, "group");
  assert.equal(payload.senderName, "Arif");
  assert.ok(payload.textPreview.length <= 160);
  assert.ok(payload.textPreview.endsWith("…"));
  for (const val of Object.values(payload)) {
    assert.equal(typeof val, "string");
  }
});

test("extractInvalidFcmTokens collects unregistered and invalid tokens only", () => {
  const sentTokens = [
    "token_valid_11111111111",
    "token_unreg_22222222222",
    "token_temp_err_33333333",
    "token_invalid_444444444",
  ];
  const batchResponse = {
    responses: [
      { success: true },
      { success: false, error: { code: "messaging/registration-token-not-registered" } },
      { success: false, error: { code: "messaging/unavailable" } },
      { success: false, error: { code: "messaging/invalid-registration-token" } },
    ],
  };

  assert.deepEqual(extractInvalidFcmTokens(sentTokens, batchResponse), [
    "token_unreg_22222222222",
    "token_invalid_444444444",
  ]);
  assert.equal(truncateMessagePreview(""), "New message");
});
