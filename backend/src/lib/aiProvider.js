import { AppError } from "@/lib/errors";
import {
  calculateEstimatedCostUsd,
  extractTokenUsage,
} from "@/lib/aiConfigUtils";
import {
  enforceAiQuota,
  getEffectiveAiConfig,
  recordAiUsageLog,
} from "@/modules/ai/aiConfig.service";

const DEFAULT_MODELS = {
  gemini: "gemini-2.0-flash",
  groq: "llama-3.3-70b-versatile",
  anthropic: "claude-haiku-5-5",
};

const DEFAULT_TIMEOUT_MS = 25_000;

async function postJson(url, headers, body, timeoutMs = DEFAULT_TIMEOUT_MS) {
  const controller = new AbortController();
  const safeTimeout = Math.max(5000, Math.min(60_000, Number(timeoutMs || DEFAULT_TIMEOUT_MS)));
  const timer = setTimeout(() => controller.abort(), safeTimeout);
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    if (res.status === 429) {
      throw new AppError(429, "AI_BUSY", "The AI teacher is busy right now. Please try again in a minute.");
    }
    if (!res.ok) {
      console.error("AI provider error:", res.status, (await res.text()).slice(0, 300));
      throw new AppError(502, "AI_UNAVAILABLE", "The AI teacher is not available right now.");
    }
    return await res.json();
  } catch (err) {
    if (err instanceof AppError) throw err;
    if (err?.name === "AbortError") throw new AppError(504, "AI_TIMEOUT", "The AI teacher took too long to answer.");
    console.error("AI request failed:", err);
    throw new AppError(502, "AI_UNAVAILABLE", "The AI teacher is not available right now.");
  } finally {
    clearTimeout(timer);
  }
}

function buildFallbackInputString(system, messages = []) {
  return [String(system || ""), ...messages.map((m) => String(m?.content || ""))].join("\n");
}

/**
 * @param {string} system   instructions
 * @param {{role:"user"|"assistant", content:string}[]} messages  conversation, last one is the user's
 * @param {{json?:boolean, maxTokens?:number, temperature?:number, feature?:string, userId?:string, isPremium?:boolean, bypassQuota?:boolean}} opts
 * @returns {Promise<string>} model text
 */
export async function generateText({
  system,
  messages,
  json = false,
  maxTokens,
  temperature,
  feature = "speaking_turn",
  userId = "system",
  isPremium = false,
  bypassQuota = false,
}) {
  const config = bypassQuota
    ? await getEffectiveAiConfig()
    : await enforceAiQuota({ userId, feature, isPremium });

  const provider = (config?.provider || process.env.AI_PROVIDER || "gemini").toLowerCase();
  const apiKey = process.env.AI_API_KEY;
  if (!apiKey) throw new AppError(500, "AI_NOT_CONFIGURED", "AI is not configured on the server.");
  const model = config?.model || process.env.AI_MODEL || DEFAULT_MODELS[provider];
  if (!model) throw new AppError(500, "AI_NOT_CONFIGURED", `Unknown AI_PROVIDER "${provider}".`);

  const effectiveTemp =
    temperature !== undefined ? Number(temperature) : Number(config?.temperature ?? 0.6);
  const effectiveMaxTokens =
    maxTokens !== undefined ? Number(maxTokens) : Number(config?.maxOutputTokens ?? 500);
  const timeoutMs = Number(config?.requestTimeoutMs || DEFAULT_TIMEOUT_MS);

  const startedAt = Date.now();
  let rawData = null;
  let outputText = "";

  try {
    if (provider === "gemini") {
      rawData = await postJson(
        `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,
        { "x-goog-api-key": apiKey },
        {
          systemInstruction: { parts: [{ text: system }] },
          contents: messages.map((m) => ({
            role: m.role === "assistant" ? "model" : "user",
            parts: [{ text: m.content }],
          })),
          generationConfig: {
            temperature: effectiveTemp,
            maxOutputTokens: effectiveMaxTokens,
            ...(json ? { responseMimeType: "application/json" } : {}),
          },
        },
        timeoutMs
      );
      outputText = rawData?.candidates?.[0]?.content?.parts?.map((p) => p.text).join("") || "";
    } else if (provider === "groq") {
      rawData = await postJson(
        "https://api.groq.com/openai/v1/chat/completions",
        { Authorization: `Bearer ${apiKey}` },
        {
          model,
          temperature: effectiveTemp,
          max_tokens: effectiveMaxTokens,
          messages: [{ role: "system", content: system }, ...messages],
          ...(json ? { response_format: { type: "json_object" } } : {}),
        },
        timeoutMs
      );
      outputText = rawData?.choices?.[0]?.message?.content || "";
    } else if (provider === "anthropic") {
      rawData = await postJson(
        "https://api.anthropic.com/v1/messages",
        { "x-api-key": apiKey, "anthropic-version": "2023-06-01" },
        { model, max_tokens: effectiveMaxTokens, temperature: effectiveTemp, system, messages },
        timeoutMs
      );
      outputText = rawData?.content?.map((b) => b.text || "").join("") || "";
    } else {
      throw new AppError(500, "AI_NOT_CONFIGURED", `Unknown AI_PROVIDER "${provider}".`);
    }

    const usage = extractTokenUsage(
      provider,
      rawData,
      buildFallbackInputString(system, messages),
      outputText
    );
    const estimatedCostUsd = calculateEstimatedCostUsd({
      provider,
      model,
      promptTokens: usage.promptTokens,
      completionTokens: usage.completionTokens,
      pricingOverride: config?.pricing,
    });

    await recordAiUsageLog({
      userId,
      feature,
      provider,
      model,
      promptTokens: usage.promptTokens,
      completionTokens: usage.completionTokens,
      totalTokens: usage.totalTokens,
      estimatedCostUsd,
      latencyMs: Date.now() - startedAt,
      status: "success",
    });

    return outputText;
  } catch (err) {
    await recordAiUsageLog({
      userId,
      feature,
      provider,
      model,
      latencyMs: Date.now() - startedAt,
      status: "error",
      errorCode: err?.code || "AI_ERROR",
    });
    throw err;
  }
}

export async function generateTextFromImage({
  system,
  prompt = "Describe or transcribe the image.",
  imageBase64,
  mimeType = "image/jpeg",
  json = false,
  maxTokens = 500,
  temperature = 0.1,
  feature = "chat_ocr",
  userId = "system",
  isPremium = false,
}) {
  const config = await enforceAiQuota({ userId, feature, isPremium });
  const provider = (config?.provider || process.env.AI_PROVIDER || "gemini").toLowerCase();
  const apiKey = process.env.AI_API_KEY;
  if (!apiKey) throw new AppError(500, "AI_NOT_CONFIGURED", "AI is not configured on the server.");
  const model = config?.model || process.env.AI_MODEL || DEFAULT_MODELS[provider];
  const timeoutMs = Number(config?.requestTimeoutMs || DEFAULT_TIMEOUT_MS);

  const startedAt = Date.now();
  let rawData = null;
  let outputText = "";

  try {
    if (provider === "gemini") {
      rawData = await postJson(
        `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,
        { "x-goog-api-key": apiKey },
        {
          systemInstruction: { parts: [{ text: system }] },
          contents: [
            {
              role: "user",
              parts: [
                { text: prompt },
                { inlineData: { mimeType, data: imageBase64 } },
              ],
            },
          ],
          generationConfig: {
            temperature,
            maxOutputTokens: maxTokens,
            ...(json ? { responseMimeType: "application/json" } : {}),
          },
        },
        timeoutMs
      );
      outputText = rawData?.candidates?.[0]?.content?.parts?.map((p) => p.text).join("") || "";
    } else if (provider === "anthropic") {
      rawData = await postJson(
        "https://api.anthropic.com/v1/messages",
        { "x-api-key": apiKey, "anthropic-version": "2023-06-01" },
        {
          model,
          max_tokens: maxTokens,
          temperature,
          system,
          messages: [
            {
              role: "user",
              content: [
                { type: "image", source: { type: "base64", media_type: mimeType, data: imageBase64 } },
                { type: "text", text: prompt },
              ],
            },
          ],
        },
        timeoutMs
      );
      outputText = rawData?.content?.map((b) => b.text || "").join("") || "";
    } else {
      throw new AppError(
        400,
        "VISION_NOT_SUPPORTED",
        `Handwriting photo OCR requires Gemini or Anthropic provider (current: ${provider}).`
      );
    }

    const usage = extractTokenUsage(provider, rawData, `${system}\n${prompt}`, outputText);
    const estimatedCostUsd = calculateEstimatedCostUsd({
      provider,
      model,
      promptTokens: usage.promptTokens,
      completionTokens: usage.completionTokens,
      pricingOverride: config?.pricing,
    });

    await recordAiUsageLog({
      userId,
      feature,
      provider,
      model,
      promptTokens: usage.promptTokens,
      completionTokens: usage.completionTokens,
      totalTokens: usage.totalTokens,
      estimatedCostUsd,
      latencyMs: Date.now() - startedAt,
      status: "success",
    });

    return outputText;
  } catch (err) {
    await recordAiUsageLog({
      userId,
      feature,
      provider,
      model,
      latencyMs: Date.now() - startedAt,
      status: "error",
      errorCode: err?.code || "AI_OCR_ERROR",
    });
    throw err;
  }
}
