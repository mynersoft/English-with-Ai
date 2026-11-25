import mongoose from "mongoose";

const vocabularyDailyActivitySchema = new mongoose.Schema(
  {
    userId: { type: String, required: true }, // Firebase UID
    date: { type: String, required: true }, // Local YYYY-MM-DD in user's timezone
    dailyGoal: { type: Number, default: 5, min: 5, max: 500 },
    newWords: { type: Number, default: 0, min: 0 },
    newWordIds: {
      type: [{ type: mongoose.Schema.Types.ObjectId, ref: "Vocabulary" }],
      default: [],
    },
    reviewedWords: { type: Number, default: 0, min: 0 },
    quizCount: { type: Number, default: 0, min: 0 },
    correctAnswers: { type: Number, default: 0, min: 0 },
    wrongAnswers: { type: Number, default: 0, min: 0 },
    speakingMinutes: { type: Number, default: 0, min: 0 },
    isGoalCompleted: { type: Boolean, default: false },
  },
  { timestamps: true }
);

vocabularyDailyActivitySchema.index({ userId: 1, date: 1 }, { unique: true });

export default mongoose.models.VocabularyDailyActivity ||
  mongoose.model("VocabularyDailyActivity", vocabularyDailyActivitySchema);
