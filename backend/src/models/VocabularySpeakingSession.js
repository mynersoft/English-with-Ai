import mongoose from "mongoose";

const vocabularySpeakingSessionSchema = new mongoose.Schema(
  {
    userId: { type: String, required: true, index: true },
    vocabularyIds: {
      type: [{ type: mongoose.Schema.Types.ObjectId, ref: "Vocabulary" }],
      default: [],
    },
    transcript: { type: String, required: true, trim: true, maxlength: 4000 },
    duration: { type: Number, default: 0, min: 0 },
    grammarScore: { type: Number, default: 0, min: 0, max: 100 },
    vocabularyScore: { type: Number, default: 0, min: 0, max: 100 },
    fluencyScore: { type: Number, default: 0, min: 0, max: 100 },
    feedback: { type: String, default: "", maxlength: 4000 },
  },
  { timestamps: true }
);

vocabularySpeakingSessionSchema.index({ userId: 1, createdAt: -1 });

export default mongoose.models.VocabularySpeakingSession ||
  mongoose.model("VocabularySpeakingSession", vocabularySpeakingSessionSchema);
