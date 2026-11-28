import { AppError } from "@/lib/errors";
import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import { validateSpeakingTurn } from "@/validators/speaking.validator";
import { speakingTurn } from "@/modules/ai/speaking.service";
import { getEffectiveAiConfig } from "@/modules/ai/aiConfig.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 30;

// POST /api/v1/ai/speaking/turn
// Body: { message, level?, topic?, history?: [{ role: "user"|"assistant", content }] }
// Returns: { reply, correction: { hasMistake, original, corrected, explanationBn } }
export const POST = withAuth(
  async (request, { user }) => {
    const input = validateSpeakingTurn(await readJson(request));
    const config = await getEffectiveAiConfig();
    const maxChars = Math.max(100, Number(config?.limits?.maxMessageChars || 500));
    if (input.message.length > maxChars) {
      throw new AppError(
        400,
        "VALIDATION_ERROR",
        `Message is too long (max ${maxChars} characters).`
      );
    }
    const isPremium =
      user?.subscriptionStatus === "active" || user?.subscriptionStatus === "trial";
    const result = await speakingTurn({
      ...input,
      userId: user?.firebaseUid || "system",
      isPremium,
    });
    return ok(result);
  },
  { limit: 20 }
);
