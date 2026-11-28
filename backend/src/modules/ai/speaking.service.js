import { generateText } from "@/lib/aiProvider";
import { buildSpeakingSystemPrompt } from "@/lib/aiConfigUtils";
import { getEffectiveAiConfig } from "@/modules/ai/aiConfig.service";

function parseModelJson(raw) {
  const cleaned = String(raw || "").replace(/```json|```/gi, "").trim();
  const start = cleaned.indexOf("{");
  const end = cleaned.lastIndexOf("}");
  if (start === -1 || end === -1) return null;
  try {
    return JSON.parse(cleaned.slice(start, end + 1));
  } catch {
    return null;
  }
}

/** One conversation turn: AI reply + grammar correction of the student's message. */
export async function speakingTurn({
  message,
  level,
  topic,
  history = [],
  userId = "system",
  isPremium = false,
}) {
  const config = await getEffectiveAiConfig();
  const maxTurns = Math.max(1, Number(config?.conversation?.maxHistoryTurns || 10));
  const trimmedHistory = Array.isArray(history) ? history.slice(-maxTurns) : [];
  const resolvedLevel = level || config?.conversation?.defaultLevel || "beginner";
  const resolvedTopic = topic || config?.conversation?.defaultTopic || "Daily Life";

  const raw = await generateText({
    system: buildSpeakingSystemPrompt(config, { level: resolvedLevel, topic: resolvedTopic }),
    messages: [...trimmedHistory, { role: "user", content: message }],
    json: true,
    maxTokens: config?.maxOutputTokens ?? 500,
    temperature: config?.temperature ?? 0.6,
    feature: "speaking_turn",
    userId,
    isPremium,
  });

  const parsed = parseModelJson(raw);
  if (!parsed || typeof parsed.reply !== "string") {
    return {
      reply: String(raw || "").trim() || "Sorry, I did not understand. Can you say that again?",
      correction: { hasMistake: false, original: message, corrected: "", explanationBn: "" },
    };
  }

  const allowCorrection = config?.conversation?.autoCorrectGrammar !== false;
  const c = parsed.correction || {};
  const corrected = typeof c.corrected === "string" ? c.corrected.trim() : "";
  const hasMistake = allowCorrection && c.hasMistake === true && corrected !== "" && corrected !== message;
  return {
    reply: parsed.reply.trim(),
    correction: {
      hasMistake,
      original: message,
      corrected: hasMistake ? corrected : "",
      explanationBn: hasMistake && typeof c.explanationBn === "string" ? c.explanationBn.trim() : "",
    },
  };
}
