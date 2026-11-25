import mongoose from "mongoose";

const userPatternProgressSchema = new mongoose.Schema(
  {
    userId: { type: String, required: true }, // Firebase UID derived from verified token
    patternId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Pattern",
      required: true,
    },
    isCompleted: { type: Boolean, default: false },
    completedAt: { type: Date, default: null },
  },
  { timestamps: true }
);

userPatternProgressSchema.index({ userId: 1, patternId: 1 }, { unique: true });
userPatternProgressSchema.index({ userId: 1, isCompleted: 1 });

export default mongoose.models.UserPatternProgress ||
  mongoose.model("UserPatternProgress", userPatternProgressSchema);
