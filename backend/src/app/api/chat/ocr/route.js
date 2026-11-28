import { AppError } from "@/lib/errors";
import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import { extractHandwritingTextFromImage } from "@/lib/chatCorrection";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 30;

// POST /api/chat/ocr -> body: { imageBase64, mimeType? }
// Extracts handwritten text from the photo without storing the image.
export const POST = withAuth(
  async (request, { user }) => {
    const body = await readJson(request);
    const isPremium =
      user?.subscriptionStatus === "active" || user?.subscriptionStatus === "trial";
    const result = await extractHandwritingTextFromImage({
      imageBase64: body?.imageBase64,
      mimeType: body?.mimeType || "image/jpeg",
      userId: user?.firebaseUid || "system",
      isPremium,
    });

    if (result.errorCode === "AI_NOT_CONFIGURED") {
      throw new AppError(503, "AI_NOT_CONFIGURED", result.errorMessage);
    }
    if (result.errorCode) {
      throw new AppError(400, result.errorCode, result.errorMessage);
    }
    if (!result.text) {
      throw new AppError(
        422,
        "NO_TEXT_FOUND",
        "No readable handwriting was detected in the photo. Please try again with clearer lighting."
      );
    }

    return ok({
      text: result.text,
      aiConfigured: true,
    });
  },
  { limit: 15 }
);
