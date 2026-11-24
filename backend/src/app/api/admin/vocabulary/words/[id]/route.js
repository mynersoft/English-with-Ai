import { AppError } from "@/lib/errors";
import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { CEFR_LEVELS, slugify } from "@/lib/vocabSrs";
import { readJson } from "@/validators/auth.validator";
import Vocabulary from "@/models/Vocabulary";
import UserVocabularyProgress from "@/models/UserVocabularyProgress";
import { assertObjectId } from "@/modules/vocabulary/vocabulary.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// PUT /api/admin/vocabulary/words/:id
export const PUT = withAdmin(async (request, { params }) => {
  const { id } = await params;
  const vocabId = assertObjectId(id, "wordId");
  const body = await readJson(request);

  const wordDoc = await Vocabulary.findById(vocabId);
  if (!wordDoc) throw new AppError(404, "WORD_NOT_FOUND", "Vocabulary word not found.");

  if (body.word !== undefined) {
    const clean = String(body.word).trim();
    if (!clean) throw new AppError(400, "VALIDATION_ERROR", "word cannot be empty.");
    const nextSlug = slugify(body.slug || clean);
    const conflict = await Vocabulary.findOne({ slug: nextSlug, _id: { $ne: vocabId } }).select("_id").lean();
    if (conflict) throw new AppError(409, "DUPLICATE_WORD", `Word "${clean}" already exists.`);
    wordDoc.word = clean;
    wordDoc.slug = nextSlug;
  }
  if (body.meaning !== undefined) {
    const cleanMeaning = String(body.meaning).trim();
    if (!cleanMeaning) throw new AppError(400, "VALIDATION_ERROR", "meaning cannot be empty.");
    wordDoc.meaning = cleanMeaning;
  }
  if (body.pronunciation !== undefined) wordDoc.pronunciation = String(body.pronunciation || "").trim();
  if (body.audioUrl !== undefined) wordDoc.audioUrl = body.audioUrl ? String(body.audioUrl).trim() : null;
  if (body.imageUrl !== undefined) wordDoc.imageUrl = body.imageUrl ? String(body.imageUrl).trim() : null;
  if (body.partOfSpeech !== undefined) wordDoc.partOfSpeech = String(body.partOfSpeech || "noun").trim().toLowerCase();
  if (body.level !== undefined) {
    const lvl = String(body.level || "").trim().toUpperCase();
    if (!CEFR_LEVELS.includes(lvl)) throw new AppError(400, "VALIDATION_ERROR", "Invalid CEFR level.");
    wordDoc.level = lvl;
  }
  if (body.categoryId !== undefined && String(body.categoryId).trim()) {
    wordDoc.categoryId = assertObjectId(body.categoryId, "categoryId");
  }
  if (Array.isArray(body.examples)) {
    wordDoc.examples = body.examples
      .map((e) => ({ en: String(e?.en || "").trim(), bn: String(e?.bn || "").trim() }))
      .filter((e) => e.en && e.bn);
  }
  if (Array.isArray(body.synonyms)) {
    wordDoc.synonyms = body.synonyms.map((s) => String(s).trim()).filter(Boolean);
  }
  if (Array.isArray(body.antonyms)) {
    wordDoc.antonyms = body.antonyms.map((s) => String(s).trim()).filter(Boolean);
  }
  if (Array.isArray(body.tags)) {
    wordDoc.tags = body.tags.map((s) => String(s).trim()).filter(Boolean);
  }
  if (body.difficulty !== undefined) {
    const d = Number(body.difficulty);
    if (Number.isFinite(d)) wordDoc.difficulty = Math.max(1, Math.min(5, Math.round(d)));
  }
  if (body.isPublished !== undefined) wordDoc.isPublished = Boolean(body.isPublished);
  if (body.sortOrder !== undefined && Number.isFinite(Number(body.sortOrder))) {
    wordDoc.sortOrder = Number(body.sortOrder);
  }

  await wordDoc.save();
  return ok({ id: String(wordDoc._id), isPublished: wordDoc.isPublished });
}, { limit: 40 });

// DELETE /api/admin/vocabulary/words/:id
export const DELETE = withAdmin(async (request, { params }) => {
  const { id } = await params;
  const vocabId = assertObjectId(id, "wordId");
  const deleted = await Vocabulary.findByIdAndDelete(vocabId).lean();
  if (!deleted) throw new AppError(404, "WORD_NOT_FOUND", "Vocabulary word not found.");
  await UserVocabularyProgress.deleteMany({ vocabularyId: vocabId });
  return ok({ deleted: true });
}, { limit: 40 });
