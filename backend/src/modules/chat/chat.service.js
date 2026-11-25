import mongoose from "mongoose";
import { AppError } from "@/lib/errors";
import { slugify } from "@/lib/vocabSrs";
import { correctEnglishChatMessage } from "@/lib/chatCorrection";
import User from "@/models/User";
import Pattern from "@/models/Pattern";
import ChatRoom from "@/models/ChatRoom";
import ChatMessage from "@/models/ChatMessage";
import ChatPresence from "@/models/ChatPresence";

const ONLINE_WINDOW_MS = 120_000;

const DEFAULT_ROOMS = [
  {
    name: "Daily English Practice",
    slug: "daily-english-practice",
    description: "Chat with fellow Bengali learners in everyday English with live AI grammar tips.",
    avatarEmoji: "🦁",
    maxMembers: 50,
    isDefault: true,
  },
  {
    name: "A1-A2 Beginners Club",
    slug: "beginners-club-a1-a2",
    description: "Friendly room for beginners practicing simple daily sentences.",
    avatarEmoji: "🌱",
    maxMembers: 50,
    isDefault: true,
  },
  {
    name: "Speaking & Study Buddies",
    slug: "speaking-study-buddies",
    description: "Find a study partner for vocabulary, IELTS, and fluency chat.",
    avatarEmoji: "🎯",
    maxMembers: 50,
    isDefault: true,
  },
];

const FALLBACK_STARTERS = [
  "I feel like...",
  "I want to...",
  "I'd like to...",
  "I'm planning to...",
  "Could you help me...",
  "How was your day?",
];

export function assertChatObjectId(id, label = "ID") {
  if (!id || !mongoose.Types.ObjectId.isValid(String(id))) {
    throw new AppError(400, "INVALID_ID", `Invalid ${label}.`);
  }
  return new mongoose.Types.ObjectId(String(id));
}

function formatStarterFromPattern(title) {
  const clean = String(title || "")
    .replace(/\+\s*(Verb|Noun|Adj|Adjective|Gerund|V-ing|Object).*$/i, "")
    .trim();
  if (!clean || clean.length < 3) return null;
  return clean.endsWith("...") || clean.endsWith("?") ? clean : `${clean}...`;
}

function userBadgeLabel(userDoc) {
  if (!userDoc) return "";
  if (userDoc.role === "admin") return "Admin";
  if (userDoc.learningLevel) {
    return userDoc.learningLevel.charAt(0).toUpperCase() + userDoc.learningLevel.slice(1);
  }
  return "";
}

function serializeMessage(m, currentUserIdStr) {
  const ai = m.aiCorrection || {};
  return {
    id: String(m._id),
    roomId: String(m.roomId),
    senderId: String(m.senderId),
    senderName: m.senderName || "Learner",
    senderBadge: m.senderBadge || "",
    isMine: String(m.senderId) === currentUserIdStr,
    text: m.text,
    replyTo: m.replyTo?.messageId
      ? {
          messageId: String(m.replyTo.messageId),
          senderName: m.replyTo.senderName || "Learner",
          textPreview: m.replyTo.textPreview || "",
        }
      : null,
    aiCorrection: {
      status: ai.status || "none",
      hasMistake: Boolean(ai.hasMistake),
      correctedText: ai.correctedText || "",
      tokens: Array.isArray(ai.tokens)
        ? ai.tokens.map((t) => ({ text: t.text, changed: Boolean(t.changed) }))
        : [],
      explanationBn: ai.explanationBn || "",
    },
    createdAt: m.createdAt ? new Date(m.createdAt).toISOString() : new Date().toISOString(),
  };
}

export async function ensureStarterRooms(userDoc) {
  const count = await ChatRoom.countDocuments({ roomType: "group" });
  if (count > 0) return;
  for (const r of DEFAULT_ROOMS) {
    await ChatRoom.findOneAndUpdate(
      { slug: r.slug },
      {
        $setOnInsert: {
          ...r,
          roomType: "group",
          createdBy: userDoc?._id || null,
          memberIds: userDoc?._id ? [userDoc._id] : [],
        },
      },
      { upsert: true }
    );
  }
}

async function getOnlineCountMap(roomIds) {
  if (!roomIds || roomIds.length === 0) return new Map();
  const cutoff = new Date(Date.now() - ONLINE_WINDOW_MS);
  const agg = await ChatPresence.aggregate([
    { $match: { roomId: { $in: roomIds }, lastSeenAt: { $gte: cutoff } } },
    { $group: { _id: "$roomId", count: { $sum: 1 } } },
  ]);
  return new Map(agg.map((a) => [String(a._id), a.count]));
}

export async function listRoomsAndStarters(userDoc) {
  await ensureStarterRooms(userDoc);
  const uid = String(userDoc._id);

  const [groupRooms, directRooms, patternDocs] = await Promise.all([
    ChatRoom.find({ roomType: "group" })
      .sort({ isDefault: -1, lastMessageAt: -1, createdAt: 1 })
      .limit(40)
      .lean(),
    ChatRoom.find({ roomType: "direct", memberIds: userDoc._id })
      .sort({ lastMessageAt: -1, updatedAt: -1 })
      .limit(30)
      .lean(),
    Pattern.find().sort({ order: 1 }).select("title").limit(8).lean(),
  ]);

  const allRooms = [...groupRooms, ...directRooms];
  const roomIds = allRooms.map((r) => r._id);
  const onlineMap = await getOnlineCountMap(roomIds);

  // Resolve peer names for direct chats
  const peerIds = [];
  for (const dr of directRooms) {
    for (const mid of dr.memberIds || []) {
      if (String(mid) !== uid) peerIds.push(mid);
    }
  }
  const peers = peerIds.length
    ? await User.find({ _id: { $in: peerIds } }).select("_id name learningLevel role").lean()
    : [];
  const peerMap = new Map(peers.map((p) => [String(p._id), p]));

  const serializeRoom = (r) => {
    const memberIds = (r.memberIds || []).map((id) => String(id));
    let title = r.name;
    let subtitle = r.description;
    if (r.roomType === "direct") {
      const peerId = memberIds.find((id) => id !== uid) || uid;
      const peer = peerMap.get(peerId);
      if (peer) {
        title = peer.name;
        subtitle = userBadgeLabel(peer) || "Student";
      }
    }
    return {
      id: String(r._id),
      name: title,
      description: subtitle || "",
      avatarEmoji: r.avatarEmoji || (r.roomType === "direct" ? "💬" : "🦁"),
      roomType: r.roomType,
      memberCount: memberIds.length,
      maxMembers: r.maxMembers || 50,
      onlineCount: onlineMap.get(String(r._id)) || 0,
      isMember: memberIds.includes(uid),
      lastMessageText: r.lastMessageText || "",
      lastMessageAt: r.lastMessageAt ? new Date(r.lastMessageAt).toISOString() : null,
    };
  };

  const patternStarters = patternDocs
    .map((p) => formatStarterFromPattern(p.title))
    .filter(Boolean);
  const starters = Array.from(new Set([...FALLBACK_STARTERS.slice(0, 4), ...patternStarters])).slice(
    0,
    8
  );

  return {
    aiConfigured: Boolean(process.env.AI_API_KEY),
    groupRooms: groupRooms.map(serializeRoom),
    directRooms: directRooms.map(serializeRoom),
    starterChips: starters,
  };
}

export async function createGroupRoom(userDoc, body = {}) {
  const name = String(body?.name || "").trim();
  if (name.length < 2 || name.length > 60) {
    throw new AppError(400, "INVALID_ROOM_NAME", "Room name must be 2 to 60 characters.");
  }
  const description = String(body?.description || "").trim().slice(0, 200);
  const avatarEmoji = String(body?.avatarEmoji || "🦁").trim().slice(0, 12) || "🦁";
  const maxMembers = Math.max(2, Math.min(200, Number(body?.maxMembers || 50) || 50));
  const baseSlug = slugify(name) || "study-room";
  const uniqueSlug = `${baseSlug}-${Date.now().toString(36)}`;

  const room = await ChatRoom.create({
    name,
    slug: uniqueSlug,
    description,
    avatarEmoji,
    roomType: "group",
    createdBy: userDoc._id,
    memberIds: [userDoc._id],
    maxMembers,
    isDefault: false,
  });

  return {
    id: String(room._id),
    name: room.name,
    description: room.description,
    avatarEmoji: room.avatarEmoji,
    roomType: "group",
    memberCount: 1,
    maxMembers: room.maxMembers,
    onlineCount: 1,
    isMember: true,
    lastMessageText: "",
    lastMessageAt: null,
  };
}

export async function getOrCreateDirectRoom(userDoc, peerUserIdRaw) {
  const peerId = assertChatObjectId(peerUserIdRaw, "peerUserId");
  if (String(peerId) === String(userDoc._id)) {
    throw new AppError(400, "INVALID_PEER", "Choose another student to start a chat.");
  }
  const peer = await User.findOne({ _id: peerId, status: "active" })
    .select("_id name learningLevel role")
    .lean();
  if (!peer) {
    throw new AppError(404, "STUDENT_NOT_FOUND", "Student account not found.");
  }

  const pairIds = [String(userDoc._id), String(peer._id)].sort();
  const directPairKey = `${pairIds[0]}:${pairIds[1]}`;

  let room = await ChatRoom.findOne({ roomType: "direct", directPairKey }).lean();
  if (!room) {
    room = await ChatRoom.create({
      name: peer.name,
      slug: `dm-${pairIds[0]}-${pairIds[1]}`,
      description: userBadgeLabel(peer) || "Student",
      avatarEmoji: "💬",
      roomType: "direct",
      createdBy: userDoc._id,
      memberIds: [userDoc._id, peer._id],
      directPairKey,
      maxMembers: 2,
      isDefault: false,
    });
    room = room.toObject();
  }

  const onlineMap = await getOnlineCountMap([room._id]);
  return {
    id: String(room._id),
    name: peer.name,
    description: userBadgeLabel(peer) || "Student",
    avatarEmoji: "💬",
    roomType: "direct",
    memberCount: 2,
    maxMembers: 2,
    onlineCount: onlineMap.get(String(room._id)) || 1,
    isMember: true,
    lastMessageText: room.lastMessageText || "",
    lastMessageAt: room.lastMessageAt ? new Date(room.lastMessageAt).toISOString() : null,
  };
}

export async function searchStudents(userDoc, search = "") {
  const q = String(search || "").trim();
  const filter = {
    _id: { $ne: userDoc._id },
    status: "active",
  };
  if (q) {
    const regex = new RegExp(q.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
    filter.name = regex;
  }
  const users = await User.find(filter)
    .sort({ lastLoginAt: -1, createdAt: -1 })
    .select("_id name learningLevel role lastLoginAt")
    .limit(30)
    .lean();

  return users.map((u) => ({
    id: String(u._id),
    name: u.name,
    badge: userBadgeLabel(u) || "Student",
    learningLevel: u.learningLevel || "beginner",
  }));
}

export async function toggleRoomMembership(userDoc, roomIdRaw, action = "join") {
  const roomId = assertChatObjectId(roomIdRaw, "roomId");
  const room = await ChatRoom.findById(roomId).lean();
  if (!room) throw new AppError(404, "ROOM_NOT_FOUND", "Chat room not found.");
  if (room.roomType === "direct") {
    const isMember = (room.memberIds || []).some((id) => String(id) === String(userDoc._id));
    if (!isMember) throw new AppError(403, "FORBIDDEN", "Not a participant in this direct chat.");
    return { joined: true, memberCount: room.memberIds.length };
  }

  if (action === "leave") {
    const updated = await ChatRoom.findByIdAndUpdate(
      roomId,
      { $pull: { memberIds: userDoc._id } },
      { new: true }
    ).lean();
    return { joined: false, memberCount: updated?.memberIds?.length || 0 };
  }

  const already = (room.memberIds || []).some((id) => String(id) === String(userDoc._id));
  if (!already && (room.memberIds?.length || 0) >= (room.maxMembers || 50)) {
    throw new AppError(400, "ROOM_FULL", "This chat room is already full.");
  }

  const updated = await ChatRoom.findByIdAndUpdate(
    roomId,
    { $addToSet: { memberIds: userDoc._id } },
    { new: true }
  ).lean();
  return { joined: true, memberCount: updated?.memberIds?.length || 0 };
}

async function loadRoomAndAssertAccess(userDoc, roomIdRaw) {
  const roomId = assertChatObjectId(roomIdRaw, "roomId");
  const room = await ChatRoom.findById(roomId).lean();
  if (!room) throw new AppError(404, "ROOM_NOT_FOUND", "Chat room not found.");
  if (
    room.roomType === "direct" &&
    !(room.memberIds || []).some((id) => String(id) === String(userDoc._id))
  ) {
    throw new AppError(403, "FORBIDDEN", "You do not have access to this direct chat.");
  }
  return room;
}

export async function getRoomMessages(userDoc, roomIdRaw, { afterIso, limit = 50 } = {}) {
  const room = await loadRoomAndAssertAccess(userDoc, roomIdRaw);
  const uid = String(userDoc._id);
  const safeLimit = Math.max(1, Math.min(100, Number(limit) || 50));

  // Upsert live presence so online count reflects active learners
  await ChatPresence.findOneAndUpdate(
    { roomId: room._id, userId: userDoc._id },
    { $set: { lastSeenAt: new Date() } },
    { upsert: true }
  );

  const msgFilter = { roomId: room._id };
  if (afterIso) {
    const dt = new Date(afterIso);
    if (!Number.isNaN(dt.getTime())) {
      msgFilter.createdAt = { $gt: dt };
    }
  }

  const [messagesRaw, onlineMap, memberUsers] = await Promise.all([
    afterIso
      ? ChatMessage.find(msgFilter).sort({ createdAt: 1 }).limit(safeLimit).lean()
      : ChatMessage.find(msgFilter).sort({ createdAt: -1 }).limit(safeLimit).lean(),
    getOnlineCountMap([room._id]),
    User.find({ _id: { $in: (room.memberIds || []).slice(0, 50) } })
      .select("_id name learningLevel role")
      .lean(),
  ]);

  const ordered = afterIso ? messagesRaw : messagesRaw.reverse();

  let displayName = room.name;
  let description = room.description || "";
  if (room.roomType === "direct") {
    const peer = memberUsers.find((u) => String(u._id) !== uid);
    if (peer) {
      displayName = peer.name;
      description = userBadgeLabel(peer) || "Student";
    }
  }

  return {
    aiConfigured: Boolean(process.env.AI_API_KEY),
    room: {
      id: String(room._id),
      name: displayName,
      description,
      avatarEmoji: room.avatarEmoji || (room.roomType === "direct" ? "💬" : "🦁"),
      roomType: room.roomType,
      memberCount: (room.memberIds || []).length,
      maxMembers: room.maxMembers || 50,
      onlineCount: Math.max(1, onlineMap.get(String(room._id)) || 1),
      isMember: (room.memberIds || []).some((id) => String(id) === uid),
    },
    members: memberUsers.map((u) => ({
      id: String(u._id),
      name: u.name,
      badge: userBadgeLabel(u) || "Student",
    })),
    messages: ordered.map((m) => serializeMessage(m, uid)),
  };
}

export async function sendChatMessage(userDoc, roomIdRaw, body = {}) {
  const room = await loadRoomAndAssertAccess(userDoc, roomIdRaw);
  const text = String(body?.text || "").trim();
  if (!text) {
    throw new AppError(400, "MESSAGE_REQUIRED", "Write a message before sending.");
  }
  if (text.length > 1000) {
    throw new AppError(400, "MESSAGE_TOO_LONG", "Message must be 1000 characters or fewer.");
  }

  const uid = String(userDoc._id);
  const isMember = (room.memberIds || []).some((id) => String(id) === uid);
  if (!isMember && room.roomType === "group") {
    if ((room.memberIds?.length || 0) >= (room.maxMembers || 50)) {
      throw new AppError(400, "ROOM_FULL", "This room has reached its member capacity.");
    }
    await ChatRoom.updateOne({ _id: room._id }, { $addToSet: { memberIds: userDoc._id } });
  }

  let replyTo = { messageId: null, senderName: "", textPreview: "" };
  if (body?.replyToMessageId) {
    const replyId = assertChatObjectId(body.replyToMessageId, "replyToMessageId");
    const targetMsg = await ChatMessage.findOne({ _id: replyId, roomId: room._id })
      .select("_id senderName text")
      .lean();
    if (targetMsg) {
      replyTo = {
        messageId: targetMsg._id,
        senderName: targetMsg.senderName || "Learner",
        textPreview: String(targetMsg.text || "").slice(0, 140),
      };
    }
  }

  const autoCorrect = body?.autoCorrect !== false;
  const aiCorrection = autoCorrect
    ? await correctEnglishChatMessage(text)
    : { status: "none", hasMistake: false, correctedText: "", tokens: [], explanationBn: "" };

  const created = await ChatMessage.create({
    roomId: room._id,
    senderId: userDoc._id,
    senderName: userDoc.name || "Learner",
    senderBadge: userBadgeLabel(userDoc),
    text,
    replyTo,
    aiCorrection,
  });

  await Promise.all([
    ChatRoom.updateOne(
      { _id: room._id },
      {
        $set: {
          lastMessageText: `${userDoc.name || "Learner"}: ${text.slice(0, 80)}`,
          lastMessageAt: created.createdAt,
        },
      }
    ),
    ChatPresence.findOneAndUpdate(
      { roomId: room._id, userId: userDoc._id },
      { $set: { lastSeenAt: new Date() } },
      { upsert: true }
    ),
  ]);

  return {
    message: serializeMessage(created.toObject(), uid),
  };
}

export async function correctExistingMessage(userDoc, messageIdRaw) {
  const msgId = assertChatObjectId(messageIdRaw, "messageId");
  const msg = await ChatMessage.findById(msgId);
  if (!msg) throw new AppError(404, "MESSAGE_NOT_FOUND", "Message not found.");

  await loadRoomAndAssertAccess(userDoc, msg.roomId);

  const aiCorrection = await correctEnglishChatMessage(msg.text);
  msg.aiCorrection = aiCorrection;
  await msg.save();

  return {
    message: serializeMessage(msg.toObject(), String(userDoc._id)),
  };
}
