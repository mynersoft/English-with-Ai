import mongoose from "mongoose";

const readingAttemptAnswerSchema = new mongoose.Schema(
  {
    questionId: { type: String, required: true },
    userAnswer: { type: String, default: "" },
    isCorrect: { type: Boolean, default: false },
    correctAnswer: { type: String, default: "" },
    explanationBn: { type: String, default: "" },
  },
  { _id: false }
);

const readingAttemptSchema = new mongoose.Schema(
  {
    userId: { type: String, required: true },
    passageId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "ReadingPassage",
      required: true,
    },
    mode: {
      type: String,
      enum: ["comprehension", "read_aloud"],
      required: true,
    },
    scorePercent: { type: Number, default: 0, min: 0, max: 100 },
    correctCount: { type: Number, default: 0, min: 0 },
    totalCount: { type: Number, default: 0, min: 0 },
    answers: { type: [readingAttemptAnswerSchema], default: [] },
    textMatchingSummary: {
      matchedWords: { type: Number, default: 0, min: 0 },
      missingWords: { type: Number, default: 0, min: 0 },
      extraWords: { type: Number, default: 0, min: 0 },
      mismatchedWords: { type: Number, default: 0, min: 0 },
    },
  },
  { timestamps: true }
);

readingAttemptSchema.index({ userId: 1, passageId: 1, createdAt: -1 });
readingAttemptSchema.index({ userId: 1, createdAt: -1 });

export default mongoose.models.ReadingAttempt ||
  mongoose.model("ReadingAttempt", readingAttemptSchema);
