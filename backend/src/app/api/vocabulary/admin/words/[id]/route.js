import { connectDB } from "@/lib/db";
import { isAdmin } from "@/lib/adminGuard";
import { apiHandler } from "@/lib/apiHandler";
import { AppError } from "@/lib/errors";
import { ok } from "@/lib/response";
import { readJson } from "@/validators/auth.validator";
import Vocabulary from "@/models/Vocabulary";
import VocabularyCategory from "@/models/VocabularyCategory";
import UserVocabularyProgress from "@/models/UserVocabularyProgress";
import {
  escapeRegex,
  requireObjectId,
  validateVocabularyPayload,
} from "@/lib/vocabulary/validators";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// PUT /api/vocabulary/admin/words/:id -> edit word
export const PUT = apiHandler(async (req, { params }) => {
  if (!(await isAdmin(req))) {
    throw new AppError(401, "UNAUTHORIZED", "Admin access required.");
  }
  const { id } = await params;
  const wordId = requireObjectId(id, "vocabulary id");
  await connectDB();

  const body = await readJson(req);
  const patch = validateVocabularyPayload(body, { partial: true });

  if (patch.categoryId) {
    const cat = await VocabularyCategory.findById(patch.categoryId).select("_id").lean();
    if (!cat) throw new AppError(400, "CATEGORY_NOT_FOUND", "Category not found.");
  }

  if (patch.word || patch.slug) {
    const dupQuery = { _id: { $ne: wordId }, $or: [] };
    if (patch.slug) dupQuery.$or.push({ slug: patch.slug });
    if (patch.word) dupQuery.$or.push({ word: new RegExp(`^${escapeRegex(patch.word)}$`, "i") });
    if (dupQuery.$or.length > 0) {
      const dup = await Vocabulary.findOne(dupQuery).select("_id word").lean();
      if (dup) throw new AppError(409, "DUPLICATE_WORD", `The word "${dup.word}" already exists.`);
    }
  }

  const updated = await Vocabulary.findByIdAndUpdate(
    wordId,
    { $set: patch },
    { new: true, runValidators: true }
  ).lean();
  if (!updated) throw new AppError(404, "WORD_NOT_FOUND", "Vocabulary word not found.");

  return ok({ word: { ...updated, id: String(updated._id) } });
}, { limit: 60 });

// PATCH /api/vocabulary/admin/words/:id -> quick toggle publish/audio/image
export const PATCH = apiHandler(async (req, { params }) => {
  if (!(await isAdmin(req))) {
    throw new AppError(401, "UNAUTHORIZED", "Admin access required.");
  }
  const { id } = await params;
  const wordId = requireObjectId(id, "vocabulary id");
  await connectDB();

  const body = await readJson(req);
  const patch = validateVocabularyPayload(body, { partial: true });
  const updated = await Vocabulary.findByIdAndUpdate(
    wordId,
    { $set: patch },
    { new: true, runValidators: true }
  ).lean();
  if (!updated) throw new AppError(404, "WORD_NOT_FOUND", "Vocabulary word not found.");

  return ok({ word: { ...updated, id: String(updated._id) } });
}, { limit: 60 });

// DELETE /api/vocabulary/admin/words/:id
export const DELETE = apiHandler(async (req, { params }) => {
  if (!(await isAdmin(req))) {
    throw new AppError(401, "UNAUTHORIZED", "Admin access required.");
  }
  const { id } = await params;
  const wordId = requireObjectId(id, "vocabulary id");
  await connectDB();

  const existing = await Vocabulary.findById(wordId).select("_id audioPublicId").lean();
  if (!existing) throw new AppError(404, "WORD_NOT_FOUND", "Vocabulary word not found.");

  const tasks = [
    Vocabulary.deleteOne({ _id: wordId }),
    UserVocabularyProgress.deleteMany({ vocabularyId: wordId }),
  ];
  if (existing.audioPublicId) {
    const { deleteAudio } = await import("@/lib/audio");
    tasks.push(deleteAudio(existing.audioPublicId));
  }
  await Promise.all(tasks);

  return ok({ deleted: true, id: String(wordId) });
}, { limit: 60 });
