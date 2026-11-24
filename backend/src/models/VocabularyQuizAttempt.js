import mongoose from "mongoose";

const vocabularyQuizAttemptSchema = new mongoose.Schema(
  {
    userId: { type: String, required: true, index: true },
    quizType: {
      type: String,
      enum: ["en_to_bn", "bn_to_en", "fill_blank", "matching", "listening", "mixed"],
      default: "mixed",
    },
    vocabularyIds: {
      type: [{ type: mongoose.Schema.Types.ObjectId, ref: "Vocabulary" }],
      default: [],
    },
    totalQuestions: { type: Number, required: true, min: 1 },
    correctAnswers: { type: Number, required: true, min: 0 },
    wrongAnswers: { type: Number, required: true, min: 0 },
    score: { type: Number, required: true, min: 0, max: 100 },
    timeTaken: { type: Number, default: 0, min: 0 },
    completedAt: { type: Date, default: Date.now },
  },
  { timestamps: true }
);

vocabularyQuizAttemptSchema.index({ userId: 1, completedAt: -1 });

export default mongoose.models.VocabularyQuizAttempt ||
  mongoose.model("VocabularyQuizAttempt", vocabularyQuizAttemptSchema);
