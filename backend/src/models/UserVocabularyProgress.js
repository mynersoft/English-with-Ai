import mongoose from "mongoose";

const userVocabularyProgressSchema = new mongoose.Schema(
  {
    userId: { type: String, required: true }, // Firebase UID derived from verified token
    vocabularyId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Vocabulary",
      required: true,
    },
    status: {
      type: String,
      enum: ["new", "learning", "review", "mastered"],
      default: "new",
    },
    isFavorite: { type: Boolean, default: false },
    correctCount: { type: Number, default: 0, min: 0 },
    wrongCount: { type: Number, default: 0, min: 0 },
    reviewCount: { type: Number, default: 0, min: 0 },
    intervalDays: { type: Number, default: 0, min: 0 },
    easeFactor: { type: Number, default: 2.5, min: 1.3 },
    repetition: { type: Number, default: 0, min: 0 },
    lastQuality: { type: Number, default: null, min: 0, max: 5 },
    lastReviewedAt: { type: Date, default: null },
    nextReviewAt: { type: Date, default: null },
    learnedAt: { type: Date, default: null },
  },
  { timestamps: true }
);

userVocabularyProgressSchema.index({ userId: 1, vocabularyId: 1 }, { unique: true });
userVocabularyProgressSchema.index({ userId: 1, nextReviewAt: 1 });
userVocabularyProgressSchema.index({ userId: 1, status: 1 });
userVocabularyProgressSchema.index({ userId: 1, isFavorite: 1 });

export default mongoose.models.UserVocabularyProgress ||
  mongoose.model("UserVocabularyProgress", userVocabularyProgressSchema);
