import mongoose from "mongoose";

const readingDailyActivitySchema = new mongoose.Schema(
  {
    userId: { type: String, required: true },
    date: { type: String, required: true }, // Local YYYY-MM-DD
    readingSeconds: { type: Number, default: 0, min: 0 },
    completedPassageIds: {
      type: [{ type: mongoose.Schema.Types.ObjectId, ref: "ReadingPassage" }],
      default: [],
    },
    dailyGoalMinutes: { type: Number, default: 15, min: 5, max: 120 },
  },
  { timestamps: true }
);

readingDailyActivitySchema.index({ userId: 1, date: 1 }, { unique: true });

export default mongoose.models.ReadingDailyActivity ||
  mongoose.model("ReadingDailyActivity", readingDailyActivitySchema);
