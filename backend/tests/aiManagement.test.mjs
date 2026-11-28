import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import {
  AI_PROVIDERS,
  DEFAULT_AI_CONFIG,
  buildChatGrammarSystemPrompt,
  buildDefaultAiConfig,
  buildSpeakingSystemPrompt,
  calculateEstimatedCostUsd,
  evaluateQuotaDecision,
  extractTokenUsage,
  lookupModelPricing,
  normalizeAiConfigPatch,
} from "../src/lib/aiConfigUtils.js";

test("buildDefaultAiConfig uses env defaults and never exposes API keys", () => {
  const cfg = buildDefaultAiConfig({
    AI_PROVIDER: "groq",
    AI_MODEL: "llama-3.3-70b-versatile",
    AI_API_KEY: "super-secret-key",
  });
  assert.equal(cfg.provider, "groq");
  assert.equal(cfg.model, "llama-3.3-70b-versatile");
  assert.equal(cfg.isEnabled, true);
  assert.equal("AI_API_KEY" in cfg, false);
  assert.equal("apiKey" in cfg, false);
});

test("normalizeAiConfigPatch validates provider, clamps numeric settings, and updates pricing on model switch", () => {
  const base = buildDefaultAiConfig({});
  const updated = normalizeAiConfigPatch(base, {
    provider: "anthropic",
    model: "claude-sonnet-4-5",
    temperature: 2.5,
    maxOutputTokens: 99999,
    requestTimeoutMs: 100,
    conversation: {
      maxHistoryTurns: 100,
      replyMaxSentences: 0,
      defaultTopic: "Job Interviews",
      defaultLevel: "advanced",
      autoCorrectGrammar: false,
    },
    limits: {
      freeDailyTurns: 30,
      premiumDailyTurns: 400,
      dailyCostCapUsd: 12.5,
      rpmPerUser: 15,
      maxMessageChars: 900,
    },
  });

  assert.equal(updated.provider, "anthropic");
  assert.equal(updated.model, "claude-sonnet-4-5");
  assert.equal(updated.temperature, 1.5);
  assert.equal(updated.maxOutputTokens, 4096);
  assert.equal(updated.requestTimeoutMs, 5000);
  assert.equal(updated.conversation.maxHistoryTurns, 30);
  assert.equal(updated.conversation.replyMaxSentences, 1);
  assert.equal(updated.conversation.defaultTopic, "Job Interviews");
  assert.equal(updated.conversation.defaultLevel, "advanced");
  assert.equal(updated.conversation.autoCorrectGrammar, false);
  assert.equal(updated.limits.freeDailyTurns, 30);
  assert.equal(updated.limits.premiumDailyTurns, 400);
  assert.equal(updated.limits.dailyCostCapUsd, 12.5);
  assert.equal(updated.pricing.inputPerMillionUsd, 3.0);
  assert.equal(updated.pricing.outputPerMillionUsd, 15.0);

  assert.throws(
    () => normalizeAiConfigPatch(base, { provider: "invalid-provider" }),
    /Unsupported AI provider/i
  );
  assert.throws(
    () => normalizeAiConfigPatch(base, { model: "   " }),
    /Model ID cannot be empty/i
  );
});

test("buildSpeakingSystemPrompt and buildChatGrammarSystemPrompt inject custom instructions & toggles", () => {
  const cfg = normalizeAiConfigPatch(DEFAULT_AI_CONFIG, {
    instructions: {
      speakingBasePrompt: "Custom coach on {{topic}} ({{level}}): {{levelRule}} max {{replyMaxSentences}}.",
      intermediateRule: "Use practical office expressions.",
      chatGrammarPrompt: "Be super gentle with learners.",
    },
    conversation: {
      replyMaxSentences: 2,
      includeBanglaExplanation: false,
    },
  });

  const speakingPrompt = buildSpeakingSystemPrompt(cfg, { level: "intermediate", topic: "Workplace" });
  assert.equal(speakingPrompt.includes("Custom coach on Workplace (intermediate): Use practical office expressions. max 2."), true);
  assert.equal(speakingPrompt.includes("short, simple explanation in English"), true);
  assert.equal(speakingPrompt.includes('"correction"'), true);

  const chatPrompt = buildChatGrammarSystemPrompt(cfg);
  assert.equal(chatPrompt.includes("Be super gentle with learners."), true);
  assert.equal(chatPrompt.includes("1 short sentence in simple English"), true);
});

test("extractTokenUsage and calculateEstimatedCostUsd handle Gemini, Groq, Anthropic, and fallback", () => {
  const geminiUsage = extractTokenUsage("gemini", {
    usageMetadata: { promptTokenCount: 1200, candidatesTokenCount: 300 },
  });
  assert.deepEqual(geminiUsage, { promptTokens: 1200, completionTokens: 300, totalTokens: 1500 });

  const groqUsage = extractTokenUsage("groq", {
    usage: { prompt_tokens: 500, completion_tokens: 150 },
  });
  assert.deepEqual(groqUsage, { promptTokens: 500, completionTokens: 150, totalTokens: 650 });

  const anthropicUsage = extractTokenUsage("anthropic", {
    usage: { input_tokens: 1000, output_tokens: 250 },
  });
  assert.deepEqual(anthropicUsage, { promptTokens: 1000, completionTokens: 250, totalTokens: 1250 });

  const fallback = extractTokenUsage("gemini", null, "12345678", "1234");
  assert.deepEqual(fallback, { promptTokens: 2, completionTokens: 1, totalTokens: 3 });

  const cost = calculateEstimatedCostUsd({
    provider: "gemini",
    model: "gemini-2.0-flash",
    promptTokens: 1_000_000,
    completionTokens: 1_000_000,
  });
  assert.equal(cost, 0.5); // 0.10 + 0.40
  assert.equal(AI_PROVIDERS.includes("gemini"), true);
  assert.equal(lookupModelPricing("groq", "llama-3.1-8b-instant").inputPerMillionUsd, 0.05);
});

test("evaluateQuotaDecision enforces global pause, feature toggles, cost cap, and free vs premium limits", () => {
  const paused = evaluateQuotaDecision({
    config: { ...DEFAULT_AI_CONFIG, isEnabled: false },
    feature: "speaking_turn",
  });
  assert.equal(paused.allowed, false);
  assert.equal(paused.statusCode, 503);
  assert.equal(paused.code, "AI_DISABLED");

  const featureOff = evaluateQuotaDecision({
    config: {
      ...DEFAULT_AI_CONFIG,
      conversation: { ...DEFAULT_AI_CONFIG.conversation, chatGrammarEnabled: false },
    },
    feature: "chat_grammar",
  });
  assert.equal(featureOff.allowed, false);
  assert.equal(featureOff.code, "AI_FEATURE_DISABLED");

  const budgetHit = evaluateQuotaDecision({
    config: DEFAULT_AI_CONFIG,
    feature: "speaking_turn",
    todayTotalCostUsd: 25.01,
  });
  assert.equal(budgetHit.allowed, false);
  assert.equal(budgetHit.statusCode, 429);
  assert.equal(budgetHit.code, "AI_DAILY_BUDGET_REACHED");

  const freeHit = evaluateQuotaDecision({
    config: DEFAULT_AI_CONFIG,
    feature: "speaking_turn",
    userIsPremium: false,
    userTodayCalls: 20,
    todayTotalCostUsd: 1.0,
  });
  assert.equal(freeHit.allowed, false);
  assert.equal(freeHit.code, "AI_DAILY_LIMIT_REACHED");

  const premiumOk = evaluateQuotaDecision({
    config: DEFAULT_AI_CONFIG,
    feature: "speaking_turn",
    userIsPremium: true,
    userTodayCalls: 20,
    todayTotalCostUsd: 1.0,
  });
  assert.equal(premiumOk.allowed, true);
});

test("Admin AI route uses withAdmin guard and never reads client-sent secret keys", () => {
  const routePath = path.resolve("backend/src/app/api/admin/ai/route.js");
  const content = fs.readFileSync(routePath, "utf8");
  assert.equal(content.includes("withAdmin("), true);
  assert.equal(content.includes("AI_API_KEY"), false);
});
