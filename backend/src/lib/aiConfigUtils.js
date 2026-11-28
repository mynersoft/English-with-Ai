export const AI_PROVIDERS = ["gemini", "groq", "anthropic"];

export const PROVIDER_CATALOG = {
  gemini: {
    label: "Google Gemini",
    defaultModel: "gemini-2.0-flash",
    models: [
      { id: "gemini-2.0-flash", label: "Gemini 2.0 Flash", inputPerMillionUsd: 0.1, outputPerMillionUsd: 0.4 },
      { id: "gemini-2.5-flash", label: "Gemini 2.5 Flash", inputPerMillionUsd: 0.15, outputPerMillionUsd: 0.6 },
      { id: "gemini-2.5-pro", label: "Gemini 2.5 Pro", inputPerMillionUsd: 1.25, outputPerMillionUsd: 10.0 },
      { id: "gemini-1.5-flash", label: "Gemini 1.5 Flash", inputPerMillionUsd: 0.075, outputPerMillionUsd: 0.3 },
    ],
  },
  groq: {
    label: "Groq Cloud",
    defaultModel: "llama-3.3-70b-versatile",
    models: [
      { id: "llama-3.3-70b-versatile", label: "Llama 3.3 70B Versatile", inputPerMillionUsd: 0.59, outputPerMillionUsd: 0.79 },
      { id: "llama-3.1-8b-instant", label: "Llama 3.1 8B Instant", inputPerMillionUsd: 0.05, outputPerMillionUsd: 0.08 },
      { id: "mixtral-8x7b-32768", label: "Mixtral 8x7B", inputPerMillionUsd: 0.24, outputPerMillionUsd: 0.24 },
    ],
  },
  anthropic: {
    label: "Anthropic Claude",
    defaultModel: "claude-haiku-5-5",
    models: [
      { id: "claude-haiku-5-5", label: "Claude Haiku", inputPerMillionUsd: 0.8, outputPerMillionUsd: 4.0 },
      { id: "claude-sonnet-4-5", label: "Claude Sonnet 4.5", inputPerMillionUsd: 3.0, outputPerMillionUsd: 15.0 },
    ],
  },
};

export const SPEAKING_TOPICS = [
  "Daily Life",
  "Self Introduction",
  "Family",
  "Education",
  "Shopping",
  "Travel",
  "Food",
  "Job Interviews",
  "Workplace",
  "Social Communication",
  "Business Communication",
];

export const AI_FEATURE_LABELS = {
  speaking_turn: "AI Speaking Partner",
  speaking_eval: "Speaking Evaluation",
  chat_grammar: "Chat Grammar Check",
  vocab_sentence: "Vocab Sentence Check",
  chat_ocr: "Handwriting OCR",
};

export const DEFAULT_AI_CONFIG = {
  isEnabled: true,
  provider: "gemini",
  model: "gemini-2.0-flash",
  temperature: 0.6,
  maxOutputTokens: 500,
  requestTimeoutMs: 25_000,
  instructions: {
    speakingBasePrompt:
      'You are "English AI", a friendly English speaking partner for Bangla-speaking learners.\nTopic: {{topic}}. Student level: {{level}}.\n{{levelRule}}\n\nEach turn, do two jobs:\n1. Reply naturally to the student in 1-{{replyMaxSentences}} short sentences and end with ONE easy question to keep the conversation going.\n2. Check the student\'s LAST message for grammar, tense, article, preposition or word-order mistakes.\n   The message comes from speech-to-text, so IGNORE capitalization and punctuation. Do not mark a sentence wrong for style only.',
    beginnerRule:
      "Use very simple words and short sentences (max 12 words). Speak slowly and clearly.",
    intermediateRule:
      "Use everyday English with natural sentences. Introduce one useful new word or phrase sometimes.",
    advancedRule:
      "Use natural, rich English with idioms and longer sentences. Challenge the student a little.",
    chatGrammarPrompt:
      "You are a helpful English grammar corrector for Bengali-speaking students chatting in an English practice room.\nCheck the student's chat message for spelling mistakes, wrong verb forms, missing/wrong prepositions, tense errors, or unnatural grammar.\nKeep the student's intended meaning and casual chat tone. Do NOT change messages just for formality or missing trailing punctuation.",
    vocabSentencePrompt:
      'You are a friendly English teacher for Bengali-speaking beginners.\nEvaluate the student\'s English sentence using the target word "{{word}}" (Bengali meaning: "{{meaning}}").',
    speakingEvalPrompt:
      'You are "English AI", an encouraging spoken-English coach for Bengali learners.\nEvaluate the student\'s spoken transcript. Target words to practice: {{words}}.\nIgnore missing punctuation from speech-to-text.',
  },
  conversation: {
    maxHistoryTurns: 10,
    replyMaxSentences: 3,
    defaultTopic: "Daily Life",
    defaultLevel: "beginner",
    autoCorrectGrammar: true,
    includeBanglaExplanation: true,
    speakingEnabled: true,
    chatGrammarEnabled: true,
    vocabEvalEnabled: true,
  },
  limits: {
    enforceLimits: true,
    freeDailyTurns: 20,
    premiumDailyTurns: 200,
    dailyCostCapUsd: 25.0,
    rpmPerUser: 20,
    maxMessageChars: 500,
  },
  pricing: {
    inputPerMillionUsd: 0.1,
    outputPerMillionUsd: 0.4,
  },
};

export function resolveEnvDefaultProviderAndModel(env = process.env) {
  const rawProvider = String(env?.AI_PROVIDER || "gemini").trim().toLowerCase();
  const provider = AI_PROVIDERS.includes(rawProvider) ? rawProvider : "gemini";
  const catalog = PROVIDER_CATALOG[provider] || PROVIDER_CATALOG.gemini;
  const rawModel = String(env?.AI_MODEL || "").trim();
  const model = rawModel || catalog.defaultModel;
  const modelEntry = catalog.models.find((m) => m.id === model);
  return {
    provider,
    model,
    inputPerMillionUsd: modelEntry ? modelEntry.inputPerMillionUsd : 0.1,
    outputPerMillionUsd: modelEntry ? modelEntry.outputPerMillionUsd : 0.4,
    apiKeyConfigured: Boolean(String(env?.AI_API_KEY || "").trim()),
  };
}

export function buildDefaultAiConfig(env = process.env) {
  const envDefaults = resolveEnvDefaultProviderAndModel(env);
  return {
    ...DEFAULT_AI_CONFIG,
    provider: envDefaults.provider,
    model: envDefaults.model,
    instructions: { ...DEFAULT_AI_CONFIG.instructions },
    conversation: { ...DEFAULT_AI_CONFIG.conversation },
    limits: { ...DEFAULT_AI_CONFIG.limits },
    pricing: {
      inputPerMillionUsd: envDefaults.inputPerMillionUsd,
      outputPerMillionUsd: envDefaults.outputPerMillionUsd,
    },
  };
}

function clampNumber(value, min, max, fallback) {
  const n = Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(min, Math.min(max, n));
}

function clampInt(value, min, max, fallback) {
  const n = Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(min, Math.min(max, Math.round(n)));
}

function cleanText(value, fallback, maxLen = 4000) {
  if (typeof value !== "string") return fallback;
  const trimmed = value.trim();
  return trimmed ? trimmed.slice(0, maxLen) : fallback;
}

export function lookupModelPricing(provider, model) {
  const catalog = PROVIDER_CATALOG[provider];
  if (!catalog) return { inputPerMillionUsd: 0.1, outputPerMillionUsd: 0.4 };
  const found = catalog.models.find((m) => m.id === model);
  if (found) {
    return {
      inputPerMillionUsd: found.inputPerMillionUsd,
      outputPerMillionUsd: found.outputPerMillionUsd,
    };
  }
  const fallback = catalog.models[0];
  return {
    inputPerMillionUsd: fallback?.inputPerMillionUsd ?? 0.1,
    outputPerMillionUsd: fallback?.outputPerMillionUsd ?? 0.4,
  };
}

export function normalizeAiConfigPatch(currentConfig, patch = {}, env = process.env) {
  const base = currentConfig ? { ...buildDefaultAiConfig(env), ...currentConfig } : buildDefaultAiConfig(env);
  const nextProviderRaw = patch.provider !== undefined ? String(patch.provider || "").trim().toLowerCase() : base.provider;
  if (!AI_PROVIDERS.includes(nextProviderRaw)) {
    throw new Error(`Unsupported AI provider "${nextProviderRaw}". Allowed: ${AI_PROVIDERS.join(", ")}.`);
  }
  const providerChanged = nextProviderRaw !== base.provider;
  const catalog = PROVIDER_CATALOG[nextProviderRaw];

  let nextModel = base.model;
  if (patch.model !== undefined) {
    const trimmedModel = String(patch.model || "").trim();
    if (!trimmedModel) {
      throw new Error("Model ID cannot be empty.");
    }
    if (trimmedModel.length > 120) {
      throw new Error("Model ID must be 120 characters or fewer.");
    }
    nextModel = trimmedModel;
  } else if (providerChanged) {
    nextModel = catalog.defaultModel;
  }

  const instrPatch = patch.instructions && typeof patch.instructions === "object" ? patch.instructions : {};
  const convPatch = patch.conversation && typeof patch.conversation === "object" ? patch.conversation : {};
  const limitsPatch = patch.limits && typeof patch.limits === "object" ? patch.limits : {};
  const pricingPatch = patch.pricing && typeof patch.pricing === "object" ? patch.pricing : {};

  const autoPricing = lookupModelPricing(nextProviderRaw, nextModel);
  const modelChanged = nextModel !== base.model || providerChanged;

  const nextLevel = convPatch.defaultLevel !== undefined
    ? String(convPatch.defaultLevel || "").trim().toLowerCase()
    : base.conversation.defaultLevel;
  const safeLevel = ["beginner", "intermediate", "advanced"].includes(nextLevel)
    ? nextLevel
    : "beginner";

  const nextTopic = convPatch.defaultTopic !== undefined
    ? String(convPatch.defaultTopic || "").trim()
    : base.conversation.defaultTopic;
  const safeTopic = SPEAKING_TOPICS.includes(nextTopic) ? nextTopic : "Daily Life";

  return {
    isEnabled: patch.isEnabled !== undefined ? Boolean(patch.isEnabled) : Boolean(base.isEnabled),
    provider: nextProviderRaw,
    model: nextModel,
    temperature: Number(
      clampNumber(
        patch.temperature !== undefined ? patch.temperature : base.temperature,
        0.0,
        1.5,
        0.6
      ).toFixed(2)
    ),
    maxOutputTokens: clampInt(
      patch.maxOutputTokens !== undefined ? patch.maxOutputTokens : base.maxOutputTokens,
      64,
      4096,
      500
    ),
    requestTimeoutMs: clampInt(
      patch.requestTimeoutMs !== undefined ? patch.requestTimeoutMs : base.requestTimeoutMs,
      5000,
      60000,
      25000
    ),
    instructions: {
      speakingBasePrompt: cleanText(
        instrPatch.speakingBasePrompt,
        base.instructions.speakingBasePrompt,
        3500
      ),
      beginnerRule: cleanText(instrPatch.beginnerRule, base.instructions.beginnerRule, 800),
      intermediateRule: cleanText(instrPatch.intermediateRule, base.instructions.intermediateRule, 800),
      advancedRule: cleanText(instrPatch.advancedRule, base.instructions.advancedRule, 800),
      chatGrammarPrompt: cleanText(instrPatch.chatGrammarPrompt, base.instructions.chatGrammarPrompt, 2500),
      vocabSentencePrompt: cleanText(instrPatch.vocabSentencePrompt, base.instructions.vocabSentencePrompt, 1500),
      speakingEvalPrompt: cleanText(instrPatch.speakingEvalPrompt, base.instructions.speakingEvalPrompt, 1500),
    },
    conversation: {
      maxHistoryTurns: clampInt(
        convPatch.maxHistoryTurns !== undefined ? convPatch.maxHistoryTurns : base.conversation.maxHistoryTurns,
        1,
        30,
        10
      ),
      replyMaxSentences: clampInt(
        convPatch.replyMaxSentences !== undefined ? convPatch.replyMaxSentences : base.conversation.replyMaxSentences,
        1,
        6,
        3
      ),
      defaultTopic: safeTopic,
      defaultLevel: safeLevel,
      autoCorrectGrammar:
        convPatch.autoCorrectGrammar !== undefined
          ? Boolean(convPatch.autoCorrectGrammar)
          : Boolean(base.conversation.autoCorrectGrammar),
      includeBanglaExplanation:
        convPatch.includeBanglaExplanation !== undefined
          ? Boolean(convPatch.includeBanglaExplanation)
          : Boolean(base.conversation.includeBanglaExplanation),
      speakingEnabled:
        convPatch.speakingEnabled !== undefined
          ? Boolean(convPatch.speakingEnabled)
          : Boolean(base.conversation.speakingEnabled),
      chatGrammarEnabled:
        convPatch.chatGrammarEnabled !== undefined
          ? Boolean(convPatch.chatGrammarEnabled)
          : Boolean(base.conversation.chatGrammarEnabled),
      vocabEvalEnabled:
        convPatch.vocabEvalEnabled !== undefined
          ? Boolean(convPatch.vocabEvalEnabled)
          : Boolean(base.conversation.vocabEvalEnabled),
    },
    limits: {
      enforceLimits:
        limitsPatch.enforceLimits !== undefined
          ? Boolean(limitsPatch.enforceLimits)
          : Boolean(base.limits.enforceLimits),
      freeDailyTurns: clampInt(
        limitsPatch.freeDailyTurns !== undefined ? limitsPatch.freeDailyTurns : base.limits.freeDailyTurns,
        0,
        50000,
        20
      ),
      premiumDailyTurns: clampInt(
        limitsPatch.premiumDailyTurns !== undefined ? limitsPatch.premiumDailyTurns : base.limits.premiumDailyTurns,
        0,
        50000,
        200
      ),
      dailyCostCapUsd: Number(
        clampNumber(
          limitsPatch.dailyCostCapUsd !== undefined ? limitsPatch.dailyCostCapUsd : base.limits.dailyCostCapUsd,
          0,
          10000,
          25
        ).toFixed(2)
      ),
      rpmPerUser: clampInt(
        limitsPatch.rpmPerUser !== undefined ? limitsPatch.rpmPerUser : base.limits.rpmPerUser,
        1,
        300,
        20
      ),
      maxMessageChars: clampInt(
        limitsPatch.maxMessageChars !== undefined ? limitsPatch.maxMessageChars : base.limits.maxMessageChars,
        100,
        4000,
        500
      ),
    },
    pricing: {
      inputPerMillionUsd: Number(
        clampNumber(
          pricingPatch.inputPerMillionUsd !== undefined
            ? pricingPatch.inputPerMillionUsd
            : modelChanged
              ? autoPricing.inputPerMillionUsd
              : base.pricing.inputPerMillionUsd,
          0,
          500,
          autoPricing.inputPerMillionUsd
        ).toFixed(4)
      ),
      outputPerMillionUsd: Number(
        clampNumber(
          pricingPatch.outputPerMillionUsd !== undefined
            ? pricingPatch.outputPerMillionUsd
            : modelChanged
              ? autoPricing.outputPerMillionUsd
              : base.pricing.outputPerMillionUsd,
          0,
          500,
          autoPricing.outputPerMillionUsd
        ).toFixed(4)
      ),
    },
  };
}

export function buildSpeakingSystemPrompt(config, { level, topic }) {
  const effective = config || DEFAULT_AI_CONFIG;
  const instr = effective.instructions || DEFAULT_AI_CONFIG.instructions;
  const conv = effective.conversation || DEFAULT_AI_CONFIG.conversation;
  const resolvedLevel = ["beginner", "intermediate", "advanced"].includes(level)
    ? level
    : conv.defaultLevel || "beginner";
  const resolvedTopic = topic || conv.defaultTopic || "Daily Life";
  const levelRuleMap = {
    beginner: instr.beginnerRule || DEFAULT_AI_CONFIG.instructions.beginnerRule,
    intermediate: instr.intermediateRule || DEFAULT_AI_CONFIG.instructions.intermediateRule,
    advanced: instr.advancedRule || DEFAULT_AI_CONFIG.instructions.advancedRule,
  };
  const levelRule = levelRuleMap[resolvedLevel];
  const replyMaxSentences = conv.replyMaxSentences || 3;

  let promptBody = String(instr.speakingBasePrompt || DEFAULT_AI_CONFIG.instructions.speakingBasePrompt)
    .replace(/\{\{topic\}\}/g, resolvedTopic)
    .replace(/\{\{level\}\}/g, resolvedLevel)
    .replace(/\{\{levelRule\}\}/g, levelRule)
    .replace(/\{\{replyMaxSentences\}\}/g, String(replyMaxSentences));

  if (!promptBody.includes(resolvedTopic)) {
    promptBody += `\nTopic: ${resolvedTopic}. Student level: ${resolvedLevel}.\n${levelRule}`;
  }

  const explanationInstruction = conv.includeBanglaExplanation !== false
    ? '"short, simple explanation in Bangla (empty string if no mistake)"'
    : '"short, simple explanation in English (empty string if no mistake)"';

  return `${promptBody}

Return ONLY valid JSON in exactly this shape:
{
  "reply": "your spoken reply in English",
  "correction": {
    "hasMistake": true or false,
    "original": "the student's sentence",
    "corrected": "the corrected sentence (empty string if no mistake)",
    "explanationBn": ${explanationInstruction}
  }
}
If the student's message is correct, set hasMistake to false and keep the other correction fields empty.
Write plain UTF-8 characters (never \\uXXXX escapes), keep explanationBn under 15 words, and never add text outside the JSON.`;
}

export function buildChatGrammarSystemPrompt(config) {
  const effective = config || DEFAULT_AI_CONFIG;
  const instr = effective.instructions || DEFAULT_AI_CONFIG.instructions;
  const conv = effective.conversation || DEFAULT_AI_CONFIG.conversation;
  const basePrompt = String(instr.chatGrammarPrompt || DEFAULT_AI_CONFIG.instructions.chatGrammarPrompt).trim();
  const explanationLine = conv.includeBanglaExplanation !== false
    ? '"explanationBn": "1 short sentence in simple Bangla explaining the fix (empty string if no mistake)"'
    : '"explanationBn": "1 short sentence in simple English explaining the fix (empty string if no mistake)"';

  return `${basePrompt}
Examples:
- "I foel like to go" -> "I feel like going"
- "Where are you live?" -> "Where do you live?"
- "Yes" -> no mistake.

Return ONLY valid JSON:
{
  "hasMistake": true or false,
  "correctedText": "natural corrected English sentence (empty string if no mistake)",
  ${explanationLine}
}`;
}

export function extractTokenUsage(provider, rawResponse, fallbackInputText = "", fallbackOutputText = "") {
  let promptTokens = 0;
  let completionTokens = 0;

  if (rawResponse && typeof rawResponse === "object") {
    if (provider === "gemini" && rawResponse.usageMetadata) {
      promptTokens = Number(rawResponse.usageMetadata.promptTokenCount || 0);
      completionTokens = Number(rawResponse.usageMetadata.candidatesTokenCount || 0);
    } else if (provider === "groq" && rawResponse.usage) {
      promptTokens = Number(rawResponse.usage.prompt_tokens || 0);
      completionTokens = Number(rawResponse.usage.completion_tokens || 0);
    } else if (provider === "anthropic" && rawResponse.usage) {
      promptTokens = Number(rawResponse.usage.input_tokens || 0);
      completionTokens = Number(rawResponse.usage.output_tokens || 0);
    }
  }

  if (!Number.isFinite(promptTokens) || promptTokens <= 0) {
    promptTokens = Math.max(1, Math.ceil(String(fallbackInputText || "").length / 4));
  }
  if (!Number.isFinite(completionTokens) || completionTokens < 0) {
    completionTokens = Math.ceil(String(fallbackOutputText || "").length / 4);
  }
  if (completionTokens === 0 && String(fallbackOutputText || "").length > 0) {
    completionTokens = Math.max(1, Math.ceil(String(fallbackOutputText || "").length / 4));
  }

  return {
    promptTokens: Math.round(promptTokens),
    completionTokens: Math.round(completionTokens),
    totalTokens: Math.round(promptTokens + completionTokens),
  };
}

export function calculateEstimatedCostUsd({
  provider,
  model,
  promptTokens = 0,
  completionTokens = 0,
  pricingOverride = null,
}) {
  const pricing =
    pricingOverride &&
    Number.isFinite(Number(pricingOverride.inputPerMillionUsd)) &&
    Number.isFinite(Number(pricingOverride.outputPerMillionUsd))
      ? pricingOverride
      : lookupModelPricing(provider, model);

  const inputCost = (Math.max(0, Number(promptTokens || 0)) / 1_000_000) * Number(pricing.inputPerMillionUsd || 0);
  const outputCost = (Math.max(0, Number(completionTokens || 0)) / 1_000_000) * Number(pricing.outputPerMillionUsd || 0);
  return Number((inputCost + outputCost).toFixed(6));
}

export function evaluateQuotaDecision({
  config,
  feature,
  userIsPremium = false,
  userTodayCalls = 0,
  todayTotalCostUsd = 0,
}) {
  const effective = config || DEFAULT_AI_CONFIG;
  if (effective.isEnabled === false) {
    return {
      allowed: false,
      statusCode: 503,
      code: "AI_DISABLED",
      message: "AI features are temporarily paused by the administrator.",
    };
  }

  const conv = effective.conversation || {};
  if ((feature === "speaking_turn" || feature === "speaking_eval") && conv.speakingEnabled === false) {
    return {
      allowed: false,
      statusCode: 503,
      code: "AI_FEATURE_DISABLED",
      message: "AI speaking practice is currently paused by the administrator.",
    };
  }
  if ((feature === "chat_grammar" || feature === "chat_ocr") && conv.chatGrammarEnabled === false) {
    return {
      allowed: false,
      statusCode: 503,
      code: "AI_FEATURE_DISABLED",
      message: "AI chat grammar features are currently paused by the administrator.",
    };
  }
  if (feature === "vocab_sentence" && conv.vocabEvalEnabled === false) {
    return {
      allowed: false,
      statusCode: 503,
      code: "AI_FEATURE_DISABLED",
      message: "AI vocabulary sentence evaluation is currently paused by the administrator.",
    };
  }

  const limits = effective.limits || DEFAULT_AI_CONFIG.limits;
  if (limits.enforceLimits === false) {
    return { allowed: true, statusCode: 200, code: null, message: null };
  }

  const costCap = Number(limits.dailyCostCapUsd || 0);
  if (costCap > 0 && Number(todayTotalCostUsd || 0) >= costCap) {
    return {
      allowed: false,
      statusCode: 429,
      code: "AI_DAILY_BUDGET_REACHED",
      message: "Daily AI budget cap has been reached. Please try again tomorrow.",
    };
  }

  const userDailyLimit = userIsPremium
    ? Number(limits.premiumDailyTurns ?? 200)
    : Number(limits.freeDailyTurns ?? 20);

  if (userDailyLimit > 0 && Number(userTodayCalls || 0) >= userDailyLimit) {
    return {
      allowed: false,
      statusCode: 429,
      code: "AI_DAILY_LIMIT_REACHED",
      message: userIsPremium
        ? `You have reached today's AI limit (${userDailyLimit} requests). Try again tomorrow.`
        : `Daily free AI limit (${userDailyLimit} requests) reached. Upgrade or try again tomorrow.`,
    };
  }

  return { allowed: true, statusCode: 200, code: null, message: null };
}
