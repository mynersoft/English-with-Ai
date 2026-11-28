import { decodeUnicodeEscapes, parseAiJsonObject } from "./speakingJson.js";

/**
 * Computes word-level diff tokens between an original English chat message and the
 * AI-corrected version using Longest Common Subsequence so changed/inserted words
 * can be highlighted in orange inside the UI bubble (e.g. "I [feel] like [going]").
 */
export function computeCorrectionTokens(originalText, correctedText) {
  const origWords = String(originalText || "")
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  const corrWords = String(correctedText || "")
    .trim()
    .split(/\s+/)
    .filter(Boolean);

  if (corrWords.length === 0) return [];
  if (origWords.length === 0) {
    return corrWords.map((w) => ({ text: w, changed: true }));
  }

  const norm = (w) => w.toLowerCase().replace(/^[^a-z0-9']+|[^a-z0-9']+$/g, "");
  const m = origWords.length;
  const n = corrWords.length;
  const dp = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));

  for (let i = m - 1; i >= 0; i--) {
    for (let j = n - 1; j >= 0; j--) {
      if (norm(origWords[i]) === norm(corrWords[j]) && origWords[i].toLowerCase() === corrWords[j].toLowerCase()) {
        dp[i][j] = 1 + dp[i + 1][j + 1];
      } else {
        dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
      }
    }
  }

  const tokens = [];
  let i = 0;
  let j = 0;
  while (i < m && j < n) {
    if (origWords[i].toLowerCase() === corrWords[j].toLowerCase()) {
      tokens.push({ text: corrWords[j], changed: false });
      i++;
      j++;
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      i++;
    } else {
      tokens.push({ text: corrWords[j], changed: true });
      j++;
    }
  }
  while (j < n) {
    tokens.push({ text: corrWords[j], changed: true });
    j++;
  }

  return tokens;
}

function parseCorrectionJson(raw) {
  return parseAiJsonObject(raw);
}

/**
 * Corrects a student's English chat message using server AI (if configured & enabled).
 * Returns status: "unavailable" when AI_API_KEY is not set (never fakes AI output).
 */
export async function correctEnglishChatMessage(text, { userId = "system", isPremium = false } = {}) {
  const original = String(text || "").trim();
  if (!original) {
    return {
      status: "none",
      hasMistake: false,
      correctedText: "",
      tokens: [],
      explanationBn: "",
    };
  }

  if (!process.env.AI_API_KEY) {
    return {
      status: "unavailable",
      hasMistake: false,
      correctedText: "",
      tokens: [],
      explanationBn: "AI grammar correction is not configured on the server.",
    };
  }

  try {
    const { buildChatGrammarSystemPrompt } = await import("@/lib/aiConfigUtils");
    const { getEffectiveAiConfig } = await import("@/modules/ai/aiConfig.service");
    const config = await getEffectiveAiConfig();

    if (
      config?.isEnabled === false ||
      config?.conversation?.chatGrammarEnabled === false ||
      config?.conversation?.autoCorrectGrammar === false
    ) {
      return {
        status: "none",
        hasMistake: false,
        correctedText: "",
        tokens: [],
        explanationBn: "",
      };
    }

    const system = buildChatGrammarSystemPrompt(config);
    const { generateText } = await import("@/lib/aiProvider");
    const raw = await generateText({
      system,
      messages: [{ role: "user", content: original }],
      json: true,
      maxTokens: Math.max(512, Number(config?.maxOutputTokens || 512)),
      temperature: Math.min(0.4, Number(config?.temperature ?? 0.2)),
      feature: "chat_grammar",
      userId,
      isPremium,
    });

    const parsed = parseCorrectionJson(raw);
    if (!parsed) {
      return {
        status: "correct",
        hasMistake: false,
        correctedText: "",
        tokens: [],
        explanationBn: "",
      };
    }

    const corrected = typeof parsed.correctedText === "string"
      ? decodeUnicodeEscapes(parsed.correctedText)
      : "";
    const hasMistake =
      parsed.hasMistake === true &&
      corrected.length > 0 &&
      corrected.toLowerCase() !== original.toLowerCase();

    if (!hasMistake) {
      return {
        status: "correct",
        hasMistake: false,
        correctedText: "",
        tokens: [],
        explanationBn: "",
      };
    }

    return {
      status: "corrected",
      hasMistake: true,
      correctedText: corrected,
      tokens: computeCorrectionTokens(original, corrected),
      explanationBn:
        typeof parsed.explanationBn === "string"
          ? decodeUnicodeEscapes(parsed.explanationBn)
          : "",
    };
  } catch (err) {
    console.warn("Chat AI grammar correction skipped due to provider error:", err?.message);
    return {
      status: "unavailable",
      hasMistake: false,
      correctedText: "",
      tokens: [],
      explanationBn: err?.code === "AI_DAILY_LIMIT_REACHED"
        ? err.message
        : "AI grammar check temporarily unavailable.",
    };
  }
}

const ALLOWED_OCR_MIMES = new Set(["image/jpeg", "image/jpg", "image/png", "image/webp"]);
const MAX_BASE64_LENGTH = 7_000_000; // ~5 MB binary

/**
 * Extracts only the handwritten text from an uploaded photo without storing the image.
 * Preserves the student's actual wording so chat AI correction can review it afterwards.
 */
export async function extractHandwritingTextFromImage({
  imageBase64,
  mimeType = "image/jpeg",
  userId = "system",
  isPremium = false,
}) {
  const cleanB64 = String(imageBase64 || "").replace(/\s+/g, "");
  const normalizedMime = String(mimeType || "image/jpeg").toLowerCase().trim();

  if (!cleanB64) {
    return { aiConfigured: true, text: "", errorCode: "IMAGE_REQUIRED", errorMessage: "Photo data is required." };
  }
  if (cleanB64.length > MAX_BASE64_LENGTH) {
    return { aiConfigured: true, text: "", errorCode: "IMAGE_TOO_LARGE", errorMessage: "Photo is too large. Please capture a smaller photo." };
  }
  if (!ALLOWED_OCR_MIMES.has(normalizedMime)) {
    return { aiConfigured: true, text: "", errorCode: "INVALID_MIME", errorMessage: "Unsupported image format. Use JPEG, PNG, or WebP." };
  }

  if (!process.env.AI_API_KEY) {
    return {
      aiConfigured: false,
      text: "",
      errorCode: "AI_NOT_CONFIGURED",
      errorMessage: "Handwriting recognition is not configured on the server (missing AI_API_KEY).",
    };
  }

  const system = `You are a precise OCR transcription assistant for a student English learning chat app.
Read the handwritten (or printed) text in the photo and transcribe ONLY the text written by the student.
Rules:
1. Keep the student's exact words and grammar as written (do NOT silently fix spelling or grammar mistakes, because the chat app will check grammar separately).
2. Join broken line wraps into natural sentences when appropriate.
3. Ignore ruled notebook lines, page numbers, doodles, or borders.
4. Return ONLY valid JSON:
{
  "text": "transcribed text (empty string if no readable text found)"
}`;

  const { generateTextFromImage } = await import("@/lib/aiProvider");
  const raw = await generateTextFromImage({
    system,
    prompt: "Transcribe only the handwritten text from this photo as plain text JSON.",
    imageBase64: cleanB64,
    mimeType: normalizedMime === "image/jpg" ? "image/jpeg" : normalizedMime,
    json: true,
    maxTokens: 500,
    temperature: 0.1,
    feature: "chat_ocr",
    userId,
    isPremium,
  });

  const parsed = parseCorrectionJson(raw);
  const extracted =
    parsed && typeof parsed.text === "string"
      ? parsed.text.trim()
      : String(raw || "").replace(/```json|```/gi, "").trim();

  return {
    aiConfigured: true,
    text: extracted.slice(0, 1000),
    errorCode: null,
    errorMessage: null,
  };
}
