import mongoose from "mongoose";

const chatRoomSchema = new mongoose.Schema(
  {
    name: { type: String, required: true, trim: true, maxlength: 60 },
    slug: { type: String, required: true, unique: true, index: true },
    description: { type: String, trim: true, maxlength: 200, default: "" },
    avatarEmoji: { type: String, trim: true, maxlength: 12, default: "🦁" },
    roomType: {
      type: String,
      enum: ["group", "direct"],
      default: "group",
      index: true,
    },
    createdBy: { type: mongoose.Schema.Types.ObjectId, ref: "User", default: null },
    memberIds: {
      type: [{ type: mongoose.Schema.Types.ObjectId, ref: "User" }],
      default: [],
      index: true,
    },
    directPairKey: { type: String, default: null, index: true, sparse: true },
    maxMembers: { type: Number, default: 50, min: 2, max: 200 },
    isDefault: { type: Boolean, default: false },
    lastMessageText: { type: String, default: "" },
    lastMessageAt: { type: Date, default: null, index: true },
  },
  { timestamps: true }
);

export default mongoose.models.ChatRoom || mongoose.model("ChatRoom", chatRoomSchema);
