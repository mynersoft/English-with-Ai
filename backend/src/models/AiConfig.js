import mongoose from "mongoose";
import { DEFAULT_AI_CONFIG } from "@/lib/aiConfigUtils";

const aiConfigSchema = new mongoose.Schema(
  {
    key: { type: String, default: "default", unique: true, index: true },
    isEnabled: { type: Boolean, default: DEFAULT_AI_CONFIG.isEnabled },
    provider: {
      type: String,
      enum: ["gemini", "groq", "anthropic"],
      default: DEFAULT_AI_CONFIG.provider,
    },
    model: { type: String, default: DEFAULT_AI_CONFIG.model, trim: true },
    temperature: { type: Number, default: DEFAULT_AI_CONFIG.temperature },
    maxOutputTokens: { type: Number, default: DEFAULT_AI_CONFIG.maxOutputTokens },
    requestTimeoutMs: { type: Number, default: DEFAULT_AI_CONFIG.requestTimeoutMs },
    instructions: {
      speakingBasePrompt: {
        type: String,
        default: DEFAULT_AI_CONFIG.instructions.speakingBasePrompt,
      },
      beginnerRule: {
        type: String,
        default: DEFAULT_AI_CONFIG.instructions.beginnerRule,
      },
      intermediateRule: {
        type: String,
        default: DEFAULT_AI_CONFIG.instructions.intermediateRule,
      },
      advancedRule: {
        type: String,
        default: DEFAULT_AI_CONFIG.instructions.advancedRule,
      },
      chatGrammarPrompt: {
        type: String,
        default: DEFAULT_AI_CONFIG.instructions.chatGrammarPrompt,
      },
      vocabSentencePrompt: {
        type: String,
        default: DEFAULT_AI_CONFIG.instructions.vocabSentencePrompt,
      },
      speakingEvalPrompt: {
        type: String,
        default: DEFAULT_AI_CONFIG.instructions.speakingEvalPrompt,
      },
    },
    conversation: {
      maxHistoryTurns: { type: Number, default: DEFAULT_AI_CONFIG.conversation.maxHistoryTurns },
      replyMaxSentences: { type: Number, default: DEFAULT_AI_CONFIG.conversation.replyMaxSentences },
      defaultTopic: { type: String, default: DEFAULT_AI_CONFIG.conversation.defaultTopic },
      defaultLevel: {
        type: String,
        enum: ["beginner", "intermediate", "advanced"],
        default: DEFAULT_AI_CONFIG.conversation.defaultLevel,
      },
      autoCorrectGrammar: {
        type: Boolean,
        default: DEFAULT_AI_CONFIG.conversation.autoCorrectGrammar,
      },
      includeBanglaExplanation: {
        type: Boolean,
        default: DEFAULT_AI_CONFIG.conversation.includeBanglaExplanation,
      },
      speakingEnabled: { type: Boolean, default: DEFAULT_AI_CONFIG.conversation.speakingEnabled },
      chatGrammarEnabled: {
        type: Boolean,
        default: DEFAULT_AI_CONFIG.conversation.chatGrammarEnabled,
      },
      vocabEvalEnabled: { type: Boolean, default: DEFAULT_AI_CONFIG.conversation.vocabEvalEnabled },
    },
    limits: {
      enforceLimits: { type: Boolean, default: DEFAULT_AI_CONFIG.limits.enforceLimits },
      freeDailyTurns: { type: Number, default: DEFAULT_AI_CONFIG.limits.freeDailyTurns },
      premiumDailyTurns: { type: Number, default: DEFAULT_AI_CONFIG.limits.premiumDailyTurns },
      dailyCostCapUsd: { type: Number, default: DEFAULT_AI_CONFIG.limits.dailyCostCapUsd },
      rpmPerUser: { type: Number, default: DEFAULT_AI_CONFIG.limits.rpmPerUser },
      maxMessageChars: { type: Number, default: DEFAULT_AI_CONFIG.limits.maxMessageChars },
    },
    pricing: {
      inputPerMillionUsd: { type: Number, default: DEFAULT_AI_CONFIG.pricing.inputPerMillionUsd },
      outputPerMillionUsd: { type: Number, default: DEFAULT_AI_CONFIG.pricing.outputPerMillionUsd },
    },
    updatedBy: { type: String, default: "" },
  },
  { timestamps: true }
);

export default mongoose.models.AiConfig || mongoose.model("AiConfig", aiConfigSchema);
