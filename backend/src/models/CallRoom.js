import mongoose from "mongoose";

const callParticipantSchema = new mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: "User", required: true },
    name: { type: String, trim: true, maxlength: 80, default: "Learner" },
    badge: { type: String, trim: true, maxlength: 40, default: "Student" },
    audioMuted: { type: Boolean, default: false },
    videoEnabled: { type: Boolean, default: true },
    joinedAt: { type: Date, default: Date.now },
    lastHeartbeatAt: { type: Date, default: Date.now },
  },
  { _id: false }
);

const callRoomSchema = new mongoose.Schema(
  {
    mode: {
      type: String,
      enum: ["direct", "random", "group_public", "group_private"],
      required: true,
      index: true,
    },
    title: { type: String, required: true, trim: true, maxlength: 80 },
    topic: { type: String, trim: true, maxlength: 160, default: "" },
    learningLevel: {
      type: String,
      enum: ["all", "beginner", "intermediate", "advanced"],
      default: "all",
    },
    visibility: {
      type: String,
      enum: ["public", "private"],
      required: true,
      index: true,
    },
    hostUserId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    hostName: { type: String, trim: true, maxlength: 80, default: "Learner" },
    targetUserId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      default: null,
      index: true,
    },
    targetName: { type: String, trim: true, maxlength: 80, default: "" },
    roomCode: {
      type: String,
      trim: true,
      uppercase: true,
      default: null,
      index: true,
      sparse: true,
    },
    invitedUserIds: {
      type: [{ type: mongoose.Schema.Types.ObjectId, ref: "User" }],
      default: [],
      index: true,
    },
    participants: {
      type: [callParticipantSchema],
      default: [],
    },
    maxParticipants: { type: Number, default: 6, min: 2, max: 50 },
    status: {
      type: String,
      enum: ["ringing", "active", "ended", "declined", "missed"],
      default: "active",
      index: true,
    },
    provider: {
      type: String,
      enum: ["free_p2p", "livekit", "agora", "daily"],
      default: "free_p2p",
    },
    endedReason: { type: String, trim: true, maxlength: 80, default: "" },
    startedAt: { type: Date, default: Date.now },
    endedAt: { type: Date, default: null },
  },
  { timestamps: true }
);

callRoomSchema.index({ mode: 1, status: 1, updatedAt: -1 });
callRoomSchema.index({ targetUserId: 1, status: 1, createdAt: -1 });
callRoomSchema.index({ hostUserId: 1, status: 1, createdAt: -1 });
callRoomSchema.index({ invitedUserIds: 1, status: 1, updatedAt: -1 });

export default mongoose.models.CallRoom || mongoose.model("CallRoom", callRoomSchema);
