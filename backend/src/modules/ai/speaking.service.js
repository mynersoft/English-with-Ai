import { generateText } from "@/lib/aiProvider";
import { buildSpeakingSystemPrompt } from "@/lib/aiConfigUtils";
import { normalizeSpeakingModelOutput } from "@/lib/speakingJson";
import { getEffectiveAiConfig } from "@/modules/ai/aiConfig.service";

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

  const configuredMaxTokens = Number(config?.maxOutputTokens || 0);
  const effectiveMaxTokens = Math.max(1024, Number.isFinite(configuredMaxTokens) ? configuredMaxTokens : 0);

  const raw = await generateText({
    system: buildSpeakingSystemPrompt(config, { level: resolvedLevel, topic: resolvedTopic }),
    messages: [...trimmedHistory, { role: "user", content: message }],
    json: true,
    maxTokens: effectiveMaxTokens,
    temperature: config?.temperature ?? 0.6,
    feature: "speaking_turn",
    userId,
    isPremium,
  });

  const allowCorrection = config?.conversation?.autoCorrectGrammar !== false;
  return normalizeSpeakingModelOutput({
    raw,
    originalMessage: message,
    allowCorrection,
  });
}
