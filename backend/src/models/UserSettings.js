import mongoose from "mongoose";

const userSettingsSchema = new mongoose.Schema(
  {
    user: { type: mongoose.Schema.Types.ObjectId, ref: "User", required: true, unique: true },
    notifications: {
      dailyReminder: { type: Boolean, default: true },
      lessonReminder: { type: Boolean, default: true },
      speakingReminder: { type: Boolean, default: true },
      weeklySummary: { type: Boolean, default: true },
    },
    explanationLanguage: { type: String, default: "bn" },
    vocabulary: {
      dailyGoal: { type: Number, default: 5, min: 1, max: 5 },
      preferredLevel: {
        type: String,
        enum: ["ALL", "A1", "A2", "B1", "B2", "C1", "C2"],
        default: "A1",
      },
      preferredCategoryIds: {
        type: [{ type: mongoose.Schema.Types.ObjectId, ref: "VocabularyCategory" }],
        default: [],
      },
      autoPronounce: { type: Boolean, default: true },
      reminderEnabled: { type: Boolean, default: false },
      reminderTime: { type: String, default: "20:00" },
      timezone: { type: String, default: "Asia/Dhaka" },
    },
    reading: {
      preferredLevel: {
        type: String,
        enum: ["ALL", "A1", "A2", "B1", "B2", "C1", "C2"],
        default: "ALL",
      },
      accent: {
        type: String,
        enum: ["en-BD", "en-GB", "en-US"],
        default: "en-US",
      },
      voiceName: { type: String, default: "" },
      defaultSpeed: { type: Number, default: 1.0, min: 0.75, max: 1.5 },
      showBengali: { type: Boolean, default: true },
      dailyGoalMinutes: { type: Number, default: 15, min: 5, max: 120 },
      reminderEnabled: { type: Boolean, default: false },
      reminderTime: { type: String, default: "20:00" },
    },
  },
  { timestamps: true, collection: "userSettings" }
);

export default mongoose.models.UserSettings || mongoose.model("UserSettings", userSettingsSchema);
