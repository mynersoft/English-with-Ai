import mongoose from "mongoose";

const callSignalSchema = new mongoose.Schema(
  {
    roomId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "CallRoom",
      required: true,
      index: true,
    },
    fromUserId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
    },
    toUserId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    type: {
      type: String,
      enum: ["offer", "answer", "ice", "renegotiate", "bye"],
      required: true,
    },
    payload: {
      type: String,
      required: true,
      maxlength: 24000,
    },
    createdAt: {
      type: Date,
      default: Date.now,
      expires: 300, // Auto-expire ephemeral WebRTC signaling docs after 5 minutes
    },
  },
  { versionKey: false }
);

callSignalSchema.index({ roomId: 1, toUserId: 1, createdAt: 1 });

export default mongoose.models.CallSignal || mongoose.model("CallSignal", callSignalSchema);
