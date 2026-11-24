import { connectDB } from "@/lib/db";
import { isAdmin } from "@/lib/adminGuard";
import { apiHandler } from "@/lib/apiHandler";
import { AppError } from "@/lib/errors";
import { ok } from "@/lib/response";
import Vocabulary from "@/models/Vocabulary";
import { requireObjectId } from "@/lib/vocabulary/validators";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// POST /api/vocabulary/admin/words/:id/audio
// Synthesizes pronunciation MP3 via Google Cloud TTS + Cloudinary if configured.
export const POST = apiHandler(async (req, { params }) => {
  if (!(await isAdmin(req))) {
    throw new AppError(401, "UNAUTHORIZED", "Admin access required.");
  }
  const { id } = await params;
  const wordId = requireObjectId(id, "vocabulary id");
  await connectDB();

  const vocab = await Vocabulary.findById(wordId);
  if (!vocab) throw new AppError(404, "WORD_NOT_FOUND", "Vocabulary word not found.");

  if (!process.env.GOOGLE_TTS_CREDENTIALS || !process.env.CLOUDINARY_CLOUD_NAME) {
    throw new AppError(
      500,
      "AUDIO_PROVIDER_NOT_CONFIGURED",
      "TTS audio generation requires GOOGLE_TTS_CREDENTIALS and CLOUDINARY_* environment variables. You can paste an audioUrl directly instead."
    );
  }

  const { synthesize, uploadAudio } = await import("@/lib/audio");
  const buffer = await synthesize(vocab.word, "en");
  const publicId = `vocabulary/${String(vocab._id)}/word`;
  const result = await uploadAudio(buffer, publicId);

  vocab.audioUrl = result.secure_url;
  vocab.audioPublicId = result.public_id;
  await vocab.save();

  return ok({
    id: String(vocab._id),
    audioUrl: vocab.audioUrl,
  });
}, { limit: 30 });
