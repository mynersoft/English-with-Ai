import mongoose from "mongoose";

const chatPresenceSchema = new mongoose.Schema(
  {
    roomId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "ChatRoom",
      required: true,
    },
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
    },
    lastSeenAt: {
      type: Date,
      default: Date.now,
      expires: 180,
    },
  },
  { timestamps: true }
);

chatPresenceSchema.index({ roomId: 1, userId: 1 }, { unique: true });

export default mongoose.models.ChatPresence || mongoose.model("ChatPresence", chatPresenceSchema);
