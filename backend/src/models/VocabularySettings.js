import mongoose from "mongoose";
import { CEFR_LEVELS } from "@/models/Vocabulary";

const vocabularySettingsSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      unique: true,
    },
    dailyGoal: { type: Number, default: 5, min: 1, max: 5 },
    preferredLevel: { type: String, enum: CEFR_LEVELS, default: "A1" },
    preferredCategories: [{ type: mongoose.Schema.Types.ObjectId, ref: "VocabularyCategory" }],
    showBengaliMeaning: { type: Boolean, default: true },
    autoPronunciation: { type: Boolean, default: true },
    learningReminder: { type: Boolean, default: true },
    timezone: { type: String, default: "Asia/Dhaka", trim: true, maxlength: 60 },
  },
  { timestamps: true }
);

export default mongoose.models.VocabularySettings ||
  mongoose.model("VocabularySettings", vocabularySettingsSchema);
