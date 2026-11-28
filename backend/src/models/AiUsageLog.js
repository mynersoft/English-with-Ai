import mongoose from "mongoose";

const aiUsageLogSchema = new mongoose.Schema(
  {
    userId: { type: String, default: "system", index: true },
    feature: {
      type: String,
      enum: ["speaking_turn", "speaking_eval", "chat_grammar", "vocab_sentence", "chat_ocr"],
      required: true,
      index: true,
    },
    provider: { type: String, default: "gemini" },
    model: { type: String, default: "gemini-2.0-flash" },
    promptTokens: { type: Number, default: 0 },
    completionTokens: { type: Number, default: 0 },
    totalTokens: { type: Number, default: 0 },
    estimatedCostUsd: { type: Number, default: 0 },
    latencyMs: { type: Number, default: 0 },
    status: {
      type: String,
      enum: ["success", "error", "blocked"],
      default: "success",
      index: true,
    },
    errorCode: { type: String, default: "" },
  },
  { timestamps: { createdAt: true, updatedAt: false } }
);

aiUsageLogSchema.index({ createdAt: -1 });
aiUsageLogSchema.index({ userId: 1, createdAt: -1 });
aiUsageLogSchema.index({ feature: 1, createdAt: -1 });

export default mongoose.models.AiUsageLog || mongoose.model("AiUsageLog", aiUsageLogSchema);
