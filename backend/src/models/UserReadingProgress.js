import mongoose from "mongoose";

const userReadingProgressSchema = new mongoose.Schema(
  {
    userId: { type: String, required: true }, // Firebase UID derived from verified token
    passageId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "ReadingPassage",
      required: true,
    },
    status: {
      type: String,
      enum: ["not_started", "in_progress", "completed"],
      default: "in_progress",
    },
    lastSentenceIndex: { type: Number, default: 0, min: 0 },
    lastSentenceId: { type: String, default: "" },
    readSentenceIds: { type: [String], default: [] },
    progressPercent: { type: Number, default: 0, min: 0, max: 100 },
    totalSeconds: { type: Number, default: 0, min: 0 },
    lastClientSequence: { type: Number, default: 0, min: 0 },
    lastReadAt: { type: Date, default: Date.now },
    completedAt: { type: Date, default: null },
  },
  { timestamps: true }
);

userReadingProgressSchema.index({ userId: 1, passageId: 1 }, { unique: true });
userReadingProgressSchema.index({ userId: 1, lastReadAt: -1 });
userReadingProgressSchema.index({ userId: 1, status: 1 });

export default mongoose.models.UserReadingProgress ||
  mongoose.model("UserReadingProgress", userReadingProgressSchema);
