import mongoose from "mongoose";
import { AppError } from "@/lib/errors";
import { adminMessaging } from "@/lib/firebaseAdmin";
import { extractInvalidFcmTokens } from "@/lib/chatNotifications";
import {
  buildCallInvitePushPayload,
  buildClientJoinTransportConfig,
  generateSecureRoomCode,
  normalizeRoomCode,
  resolveCallProviderConfig,
} from "@/lib/callProvider";
import CallRoom from "@/models/CallRoom";
import CallSignal from "@/models/CallSignal";
import User from "@/models/User";

const RINGING_TIMEOUT_MS = 65_000;
const PARTICIPANT_HEARTBEAT_TTL_MS = 35_000;
const EMPTY_ROOM_GRACE_MS = 90_000;
const VALID_SIGNAL_TYPES = new Set(["offer", "answer", "ice", "renegotiate", "bye"]);
const VALID_LEVELS = new Set(["all", "beginner", "intermediate", "advanced"]);

export function assertCallObjectId(id, label = "ID") {
  if (!id || !mongoose.Types.ObjectId.isValid(String(id))) {
    throw new AppError(400, "INVALID_ID", `Invalid ${label}.`);
  }
  return new mongoose.Types.ObjectId(String(id));
}

function userBadgeLabel(userDoc) {
  if (!userDoc) return "Student";
  if (userDoc.role === "admin") return "Admin";
  if (userDoc.learningLevel) {
    return userDoc.learningLevel.charAt(0).toUpperCase() + userDoc.learningLevel.slice(1);
  }
  return "Student";
}

function isRoomMemberOrInvited(room, userIdStr) {
  if (!room || !userIdStr) return false;
  if (String(room.hostUserId) === userIdStr) return true;
  if (room.targetUserId && String(room.targetUserId) === userIdStr) return true;
  if ((room.invitedUserIds || []).some((id) => String(id) === userIdStr)) return true;
  if ((room.participants || []).some((p) => String(p.userId) === userIdStr)) return true;
  return false;
}

function serializeCallRoom(room, currentUserIdStr) {
  const uid = String(currentUserIdStr || "");
  const isHost = String(room.hostUserId) === uid;
  const participants = (room.participants || []).map((p) => ({
    userId: String(p.userId),
    name: p.name || "Learner",
    badge: p.badge || "Student",
    audioMuted: Boolean(p.audioMuted),
    videoEnabled: p.videoEnabled !== false,
    isSelf: String(p.userId) === uid,
  }));

  let displayTitle = room.title;
  if (room.mode === "direct") {
    displayTitle = isHost
      ? room.targetName || room.title || "Video Call"
      : room.hostName || room.title || "Video Call";
  }

  const canSeeRoomCode =
    room.mode === "group_private" && isRoomMemberOrInvited(room, uid);

  return {
    id: String(room._id),
    mode: room.mode,
    visibility: room.visibility,
    title: displayTitle,
    topic: room.topic || "",
    learningLevel: room.learningLevel || "all",
    hostUserId: String(room.hostUserId),
    hostName: room.hostName || "Learner",
    targetUserId: room.targetUserId ? String(room.targetUserId) : null,
    targetName: room.targetName || "",
    roomCode: canSeeRoomCode ? room.roomCode || "" : "",
    status: room.status,
    provider: room.provider || "free_p2p",
    maxParticipants: room.maxParticipants || 6,
    participantCount: participants.length,
    participants,
    isHost,
    isJoined: participants.some((p) => p.userId === uid),
    startedAt: room.startedAt ? new Date(room.startedAt).toISOString() : null,
    createdAt: room.createdAt ? new Date(room.createdAt).toISOString() : new Date().toISOString(),
    updatedAt: room.updatedAt ? new Date(room.updatedAt).toISOString() : new Date().toISOString(),
  };
}

export async function cleanupStaleCalls() {
  const now = Date.now();
  const ringCutoff = new Date(now - RINGING_TIMEOUT_MS);
  const participantCutoff = new Date(now - PARTICIPANT_HEARTBEAT_TTL_MS);
  const emptyCutoff = new Date(now - EMPTY_ROOM_GRACE_MS);

  await Promise.all([
    CallRoom.updateMany(
      { mode: "direct", status: "ringing", createdAt: { $lt: ringCutoff } },
      {
        $set: {
          status: "missed",
          endedReason: "no_answer",
          endedAt: new Date(now),
          participants: [],
        },
      }
    ),
    CallRoom.updateMany(
      {
        status: "active",
        "participants.lastHeartbeatAt": { $lt: participantCutoff },
      },
      {
        $pull: {
          participants: { lastHeartbeatAt: { $lt: participantCutoff } },
        },
      }
    ),
  ]);

  await CallRoom.updateMany(
    {
      status: "active",
      participants: { $size: 0 },
      updatedAt: { $lt: emptyCutoff },
    },
    {
      $set: {
        status: "ended",
        endedReason: "idle_timeout",
        endedAt: new Date(now),
      },
    }
  );
}

async function dispatchIncomingCallPush({ room, targetUserId, callerName }) {
  try {
    const target = await User.findOne({
      _id: targetUserId,
      status: "active",
      "fcmTokens.0": { $exists: true },
    })
      .select("fcmTokens")
      .lean();
    const tokens = Array.isArray(target?.fcmTokens)
      ? Array.from(new Set(target.fcmTokens.filter(Boolean)))
      : [];
    if (!tokens.length) return;

    const messaging = adminMessaging();
    const dataPayload = buildCallInvitePushPayload({ room, callerName });
    const batchRes = await messaging.sendEachForMulticast({
      tokens: tokens.slice(0, 50),
      data: dataPayload,
      android: {
        priority: "high",
      },
    });
    const invalidTokens = extractInvalidFcmTokens(tokens.slice(0, 50), batchRes);
    if (invalidTokens.length) {
      await User.updateMany(
        { fcmTokens: { $in: invalidTokens } },
        { $pullAll: { fcmTokens: invalidTokens } }
      );
    }
  } catch (err) {
    console.warn("FCM call invite push skipped:", err?.message || err);
  }
}

export async function getCallProviderConfigForUser() {
  return {
    config: resolveCallProviderConfig(process.env),
  };
}

export async function listCallLobby(userDoc) {
  await cleanupStaleCalls();
  const uid = String(userDoc._id);
  const providerConfig = resolveCallProviderConfig(process.env);

  const [publicRooms, privateRooms, directCalls] = await Promise.all([
    CallRoom.find({
      mode: "group_public",
      status: "active",
    })
      .sort({ updatedAt: -1 })
      .limit(30)
      .lean(),
    CallRoom.find({
      mode: "group_private",
      status: "active",
      $or: [
        { hostUserId: userDoc._id },
        { invitedUserIds: userDoc._id },
        { "participants.userId": userDoc._id },
      ],
    })
      .sort({ updatedAt: -1 })
      .limit(20)
      .lean(),
    CallRoom.find({
      mode: "direct",
      $or: [{ hostUserId: userDoc._id }, { targetUserId: userDoc._id }],
    })
      .sort({ updatedAt: -1 })
      .limit(15)
      .lean(),
  ]);

  const incomingInvites = directCalls
    .filter((r) => r.status === "ringing" && String(r.targetUserId) === uid)
    .map((r) => serializeCallRoom(r, uid));

  return {
    config: providerConfig,
    publicRooms: publicRooms.map((r) => serializeCallRoom(r, uid)),
    privateRooms: privateRooms.map((r) => serializeCallRoom(r, uid)),
    directCalls: directCalls.map((r) => serializeCallRoom(r, uid)),
    incomingInvites,
  };
}

export async function getIncomingInvites(userDoc) {
  await cleanupStaleCalls();
  const uid = String(userDoc._id);
  const invites = await CallRoom.find({
    mode: "direct",
    targetUserId: userDoc._id,
    status: "ringing",
  })
    .sort({ createdAt: -1 })
    .limit(5)
    .lean();

  return {
    serverTime: new Date().toISOString(),
    invites: invites.map((r) => serializeCallRoom(r, uid)),
  };
}

async function generateUniqueRoomCode() {
  for (let attempt = 0; attempt < 6; attempt += 1) {
    const candidate = generateSecureRoomCode(6);
    const exists = await CallRoom.exists({
      roomCode: candidate,
      status: { $in: ["active", "ringing"] },
    });
    if (!exists) return candidate;
  }
  return generateSecureRoomCode(8);
}

export async function createCallRoom(userDoc, body = {}) {
  await cleanupStaleCalls();
  const uid = String(userDoc._id);
  const providerConfig = resolveCallProviderConfig(process.env);
  const mode = String(body?.mode || "").trim();

  if (!["direct", "group_public", "group_private"].includes(mode)) {
    throw new AppError(
      400,
      "INVALID_CALL_MODE",
      "Call mode must be direct, group_public, or group_private."
    );
  }

  const audioMuted = Boolean(body?.audioMuted);
  const videoEnabled = body?.videoEnabled !== false;
  const selfParticipant = {
    userId: userDoc._id,
    name: userDoc.name || "Learner",
    badge: userBadgeLabel(userDoc),
    audioMuted,
    videoEnabled,
    joinedAt: new Date(),
    lastHeartbeatAt: new Date(),
  };

  if (mode === "direct") {
    const targetId = assertCallObjectId(body?.targetUserId, "targetUserId");
    if (String(targetId) === uid) {
      throw new AppError(400, "INVALID_PEER", "Choose another student to call.");
    }
    const targetUser = await User.findOne({ _id: targetId, status: "active" })
      .select("_id name learningLevel role")
      .lean();
    if (!targetUser) {
      throw new AppError(404, "STUDENT_NOT_FOUND", "Student account not found.");
    }

    // Reuse existing active or ringing call between these two students if one exists
    const existing = await CallRoom.findOne({
      mode: "direct",
      status: { $in: ["ringing", "active"] },
      $or: [
        { hostUserId: userDoc._id, targetUserId: targetUser._id },
        { hostUserId: targetUser._id, targetUserId: userDoc._id },
      ],
    });

    if (existing) {
      const alreadyJoined = (existing.participants || []).some((p) => String(p.userId) === uid);
      if (!alreadyJoined) {
        existing.participants.push(selfParticipant);
      }
      if (String(existing.targetUserId) === uid && existing.status === "ringing") {
        existing.status = "active";
        existing.startedAt = new Date();
      }
      await existing.save();
      const plain = existing.toObject();
      return {
        room: serializeCallRoom(plain, uid),
        transport: buildClientJoinTransportConfig({ room: plain, user: userDoc, env: process.env }),
      };
    }

    const created = await CallRoom.create({
      mode: "direct",
      visibility: "private",
      title: `${userDoc.name || "Learner"} & ${targetUser.name || "Learner"}`,
      topic: String(body?.topic || "1-on-1 English Video Call").trim().slice(0, 160),
      learningLevel: "all",
      hostUserId: userDoc._id,
      hostName: userDoc.name || "Learner",
      targetUserId: targetUser._id,
      targetName: targetUser.name || "Learner",
      invitedUserIds: [userDoc._id, targetUser._id],
      participants: [selfParticipant],
      maxParticipants: 2,
      status: "ringing",
      provider: providerConfig.provider,
      startedAt: new Date(),
    });

    const plain = created.toObject();
    await dispatchIncomingCallPush({
      room: plain,
      targetUserId: targetUser._id,
      callerName: userDoc.name || "Learner",
    });

    return {
      room: serializeCallRoom(plain, uid),
      transport: buildClientJoinTransportConfig({ room: plain, user: userDoc, env: process.env }),
    };
  }

  // Group Public or Group Private
  const title = String(body?.title || "").trim();
  if (title.length < 2 || title.length > 80) {
    throw new AppError(400, "INVALID_ROOM_TITLE", "Room title must be 2 to 80 characters.");
  }
  const topic = String(body?.topic || "").trim().slice(0, 160);
  const rawLevel = String(body?.learningLevel || "all").trim().toLowerCase();
  const learningLevel = VALID_LEVELS.has(rawLevel) ? rawLevel : "all";
  const requestedMax = Number(body?.maxParticipants || providerConfig.maxGroupParticipants);
  const maxParticipants = Math.max(
    2,
    Math.min(providerConfig.maxGroupParticipants, Number.isFinite(requestedMax) ? requestedMax : providerConfig.maxGroupParticipants)
  );

  const isPrivate = mode === "group_private";
  const roomCode = isPrivate ? await generateUniqueRoomCode() : null;

  const created = await CallRoom.create({
    mode,
    visibility: isPrivate ? "private" : "public",
    title,
    topic,
    learningLevel,
    hostUserId: userDoc._id,
    hostName: userDoc.name || "Learner",
    roomCode,
    invitedUserIds: isPrivate ? [userDoc._id] : [],
    participants: [selfParticipant],
    maxParticipants,
    status: "active",
    provider: providerConfig.provider,
    startedAt: new Date(),
  });

  const plain = created.toObject();
  return {
    room: serializeCallRoom(plain, uid),
    transport: buildClientJoinTransportConfig({ room: plain, user: userDoc, env: process.env }),
  };
}

async function joinRoomDocument(userDoc, room, { audioMuted = false, videoEnabled = true } = {}) {
  const uid = String(userDoc._id);
  const now = new Date();
  const activeParticipants = (room.participants || []).filter(
    (p) => Date.now() - new Date(p.lastHeartbeatAt || now).getTime() <= PARTICIPANT_HEARTBEAT_TTL_MS
  );
  room.participants = activeParticipants;

  const existingIdx = room.participants.findIndex((p) => String(p.userId) === uid);
  if (existingIdx < 0 && room.participants.length >= (room.maxParticipants || 6)) {
    throw new AppError(409, "ROOM_FULL", "This call room is currently full.");
  }

  if (existingIdx >= 0) {
    room.participants[existingIdx].audioMuted = Boolean(audioMuted);
    room.participants[existingIdx].videoEnabled = videoEnabled !== false;
    room.participants[existingIdx].lastHeartbeatAt = now;
  } else {
    room.participants.push({
      userId: userDoc._id,
      name: userDoc.name || "Learner",
      badge: userBadgeLabel(userDoc),
      audioMuted: Boolean(audioMuted),
      videoEnabled: videoEnabled !== false,
      joinedAt: now,
      lastHeartbeatAt: now,
    });
  }

  if (room.mode === "group_private" && !(room.invitedUserIds || []).some((id) => String(id) === uid)) {
    room.invitedUserIds.push(userDoc._id);
  }
  if (room.mode === "direct" && room.status === "ringing" && String(room.targetUserId) === uid) {
    room.status = "active";
    room.startedAt = now;
  }

  await room.save();
  const plain = room.toObject();
  return {
    room: serializeCallRoom(plain, uid),
    transport: buildClientJoinTransportConfig({ room: plain, user: userDoc, env: process.env }),
  };
}

export async function joinCallRoom(userDoc, roomIdRaw, body = {}) {
  await cleanupStaleCalls();
  const roomId = assertCallObjectId(roomIdRaw, "roomId");
  const uid = String(userDoc._id);

  const room = await CallRoom.findById(roomId);
  if (!room) throw new AppError(404, "CALL_NOT_FOUND", "Call room not found.");
  if (room.status === "ended" || room.status === "declined" || room.status === "missed") {
    throw new AppError(409, "CALL_ENDED", "This call has already ended.");
  }

  if (room.mode === "direct") {
    const allowed =
      String(room.hostUserId) === uid ||
      (room.targetUserId && String(room.targetUserId) === uid);
    if (!allowed) {
      throw new AppError(403, "FORBIDDEN", "You are not a participant in this 1-on-1 call.");
    }
  } else if (room.mode === "group_private") {
    const providedCode = normalizeRoomCode(body?.roomCode);
    const matchesCode = Boolean(providedCode && room.roomCode && providedCode === room.roomCode);
    const alreadyAuthorized = isRoomMemberOrInvited(room, uid);
    if (!alreadyAuthorized && !matchesCode) {
      throw new AppError(403, "INVALID_ROOM_CODE", "Valid private room code required to join.");
    }
  }

  return joinRoomDocument(userDoc, room, {
    audioMuted: Boolean(body?.audioMuted),
    videoEnabled: body?.videoEnabled !== false,
  });
}

export async function joinPrivateRoomByCode(userDoc, body = {}) {
  await cleanupStaleCalls();
  const roomCode = normalizeRoomCode(body?.roomCode);
  if (roomCode.length < 4) {
    throw new AppError(400, "INVALID_ROOM_CODE", "Enter a valid private room code.");
  }
  const room = await CallRoom.findOne({
    mode: "group_private",
    roomCode,
    status: "active",
  });
  if (!room) {
    throw new AppError(404, "ROOM_NOT_FOUND", "No active private call room found with that code.");
  }

  return joinRoomDocument(userDoc, room, {
    audioMuted: Boolean(body?.audioMuted),
    videoEnabled: body?.videoEnabled !== false,
  });
}

export async function respondToCall(userDoc, roomIdRaw, body = {}) {
  const roomId = assertCallObjectId(roomIdRaw, "roomId");
  const uid = String(userDoc._id);
  const action = String(body?.action || "").trim().toLowerCase();
  if (!["accept", "decline", "cancel", "end"].includes(action)) {
    throw new AppError(400, "INVALID_ACTION", "Action must be accept, decline, cancel, or end.");
  }

  const room = await CallRoom.findById(roomId);
  if (!room) throw new AppError(404, "CALL_NOT_FOUND", "Call room not found.");

  const isHost = String(room.hostUserId) === uid;
  const isTarget = room.targetUserId && String(room.targetUserId) === uid;

  if (room.mode === "direct") {
    if (!isHost && !isTarget) {
      throw new AppError(403, "FORBIDDEN", "You cannot modify this call.");
    }
    if (action === "accept") {
      if (!isTarget && !isHost) {
        throw new AppError(403, "FORBIDDEN", "Only the callee can accept this call.");
      }
      if (room.status === "ended" || room.status === "declined" || room.status === "missed") {
        throw new AppError(409, "CALL_ENDED", "This call is no longer active.");
      }
      return joinRoomDocument(userDoc, room, {
        audioMuted: Boolean(body?.audioMuted),
        videoEnabled: body?.videoEnabled !== false,
      });
    }

    if (action === "decline") {
      if (!isTarget) {
        throw new AppError(403, "FORBIDDEN", "Only the callee can decline an incoming call.");
      }
      room.status = "declined";
      room.endedReason = "declined";
      room.endedAt = new Date();
      room.participants = [];
      await room.save();
      return { room: serializeCallRoom(room.toObject(), uid) };
    }

    // cancel or end
    room.status = "ended";
    room.endedReason = action === "cancel" ? "cancelled" : "ended";
    room.endedAt = new Date();
    room.participants = [];
    await room.save();
    return { room: serializeCallRoom(room.toObject(), uid) };
  }

  // Group rooms: host can end the room
  if (action === "end") {
    if (!isHost) {
      throw new AppError(403, "FORBIDDEN", "Only the room host can end the group call.");
    }
    room.status = "ended";
    room.endedReason = "host_ended";
    room.endedAt = new Date();
    room.participants = [];
    await room.save();
    return { room: serializeCallRoom(room.toObject(), uid) };
  }

  throw new AppError(400, "INVALID_ACTION", "Unsupported action for a group room.");
}

export async function leaveCallRoom(userDoc, roomIdRaw, body = {}) {
  const roomId = assertCallObjectId(roomIdRaw, "roomId");
  const uid = String(userDoc._id);
  const room = await CallRoom.findById(roomId);
  if (!room) return { left: true };

  if (!isRoomMemberOrInvited(room, uid) && room.mode !== "group_public") {
    throw new AppError(403, "FORBIDDEN", "You do not have access to this call room.");
  }

  const remaining = (room.participants || []).filter((p) => String(p.userId) !== uid);
  if (remaining.length > 0) {
    await CallSignal.insertMany(
      remaining.map((p) => ({
        roomId: room._id,
        fromUserId: userDoc._id,
        toUserId: p.userId,
        type: "bye",
        payload: JSON.stringify({ userId: uid }),
      }))
    );
  }

  room.participants = remaining;
  const endRoomRequested = Boolean(body?.endRoom) && String(room.hostUserId) === uid;
  if (room.mode === "direct" || endRoomRequested || remaining.length === 0) {
    room.status = "ended";
    room.endedReason = endRoomRequested ? "host_ended" : "completed";
    room.endedAt = new Date();
  }
  await room.save();

  return {
    left: true,
    room: serializeCallRoom(room.toObject(), uid),
  };
}

export async function pollCallRoomState(
  userDoc,
  roomIdRaw,
  { afterIso, audioMuted, videoEnabled } = {}
) {
  await cleanupStaleCalls();
  const roomId = assertCallObjectId(roomIdRaw, "roomId");
  const uid = String(userDoc._id);

  const room = await CallRoom.findById(roomId);
  if (!room) throw new AppError(404, "CALL_NOT_FOUND", "Call room not found.");

  const hasAccess =
    room.mode === "group_public" ||
    isRoomMemberOrInvited(room, uid);
  if (!hasAccess) {
    throw new AppError(403, "FORBIDDEN", "You do not have access to this call room.");
  }

  // Update caller/participant heartbeat if currently listed
  const now = new Date();
  let modified = false;
  const idx = (room.participants || []).findIndex((p) => String(p.userId) === uid);
  if (idx >= 0 && (room.status === "active" || room.status === "ringing")) {
    room.participants[idx].lastHeartbeatAt = now;
    if (typeof audioMuted === "boolean") {
      room.participants[idx].audioMuted = audioMuted;
    }
    if (typeof videoEnabled === "boolean") {
      room.participants[idx].videoEnabled = videoEnabled;
    }
    modified = true;
  }

  // Prune stale participants inside this room immediately
  const pruned = (room.participants || []).filter(
    (p) => Date.now() - new Date(p.lastHeartbeatAt || now).getTime() <= PARTICIPANT_HEARTBEAT_TTL_MS
  );
  if (pruned.length !== (room.participants || []).length) {
    room.participants = pruned;
    modified = true;
  }

  if (modified) {
    await room.save();
  }

  const signalQuery = {
    roomId: room._id,
    toUserId: userDoc._id,
  };
  if (afterIso) {
    const dt = new Date(afterIso);
    if (!Number.isNaN(dt.getTime())) {
      signalQuery.createdAt = { $gt: dt };
    }
  }

  const signals = await CallSignal.find(signalQuery)
    .sort({ createdAt: 1 })
    .limit(100)
    .lean();

  return {
    serverTime: now.toISOString(),
    room: serializeCallRoom(room.toObject(), uid),
    signals: signals.map((s) => ({
      id: String(s._id),
      roomId: String(s.roomId),
      fromUserId: String(s.fromUserId),
      toUserId: String(s.toUserId),
      type: s.type,
      payload: s.payload,
      createdAt: s.createdAt ? new Date(s.createdAt).toISOString() : now.toISOString(),
    })),
  };
}

export async function sendCallSignal(userDoc, roomIdRaw, body = {}) {
  const roomId = assertCallObjectId(roomIdRaw, "roomId");
  const uid = String(userDoc._id);
  const toUserId = assertCallObjectId(body?.toUserId, "toUserId");
  const type = String(body?.type || "").trim().toLowerCase();
  const payload = typeof body?.payload === "string"
    ? body.payload
    : JSON.stringify(body?.payload ?? "");

  if (!VALID_SIGNAL_TYPES.has(type)) {
    throw new AppError(400, "INVALID_SIGNAL_TYPE", "Unsupported signaling message type.");
  }
  if (!payload || payload.length > 24_000) {
    throw new AppError(400, "INVALID_SIGNAL_PAYLOAD", "Signaling payload is missing or too large.");
  }
  if (String(toUserId) === uid) {
    throw new AppError(400, "INVALID_SIGNAL_TARGET", "Cannot send signaling messages to yourself.");
  }

  const room = await CallRoom.findById(roomId).lean();
  if (!room) throw new AppError(404, "CALL_NOT_FOUND", "Call room not found.");
  if (room.status === "ended" || room.status === "declined" || room.status === "missed") {
    throw new AppError(409, "CALL_ENDED", "Cannot signal an ended call.");
  }

  const senderInRoom = isRoomMemberOrInvited(room, uid);
  const targetInRoom = isRoomMemberOrInvited(room, String(toUserId));
  if (!senderInRoom || !targetInRoom) {
    throw new AppError(403, "FORBIDDEN", "Both peers must belong to the call room.");
  }

  const created = await CallSignal.create({
    roomId: room._id,
    fromUserId: userDoc._id,
    toUserId,
    type,
    payload,
  });

  return {
    signalId: String(created._id),
    createdAt: created.createdAt ? new Date(created.createdAt).toISOString() : new Date().toISOString(),
  };
}
