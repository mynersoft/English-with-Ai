import mongoose from "mongoose";

const correctionTokenSchema = new mongoose.Schema(
  {
    text: { type: String, required: true },
    changed: { type: Boolean, default: false },
  },
  { _id: false }
);

const chatMessageSchema = new mongoose.Schema(
  {
    roomId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "ChatRoom",
      required: true,
      index: true,
    },
    senderId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    senderName: { type: String, required: true, trim: true, maxlength: 80 },
    senderBadge: { type: String, default: "", maxlength: 30 },
    text: { type: String, required: true, trim: true, maxlength: 1000 },
    replyTo: {
      messageId: { type: mongoose.Schema.Types.ObjectId, ref: "ChatMessage", default: null },
      senderName: { type: String, default: "" },
      textPreview: { type: String, default: "" },
    },
    aiCorrection: {
      status: {
        type: String,
        enum: ["none", "corrected", "correct", "unavailable"],
        default: "none",
      },
      hasMistake: { type: Boolean, default: false },
      correctedText: { type: String, default: "" },
      tokens: { type: [correctionTokenSchema], default: [] },
      explanationBn: { type: String, default: "" },
    },
  },
  { timestamps: true }
);

chatMessageSchema.index({ roomId: 1, createdAt: -1 });
chatMessageSchema.index({ senderId: 1, createdAt: -1 });
chatMessageSchema.index({ createdAt: -1, roomId: 1 });

export default mongoose.models.ChatMessage || mongoose.model("ChatMessage", chatMessageSchema);
