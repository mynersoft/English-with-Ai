import { AppError } from "@/lib/errors";
import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import Vocabulary from "@/models/Vocabulary";
import UserVocabularyProgress from "@/models/UserVocabularyProgress";
import { assertObjectId } from "@/modules/vocabulary/vocabulary.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/vocabulary/words/:id
export const GET = withAuth(async (request, { decoded, params }) => {
  const { id } = await params;
  const vocabId = assertObjectId(id, "wordId");

  const [word, progress] = await Promise.all([
    Vocabulary.findOne({ _id: vocabId, isPublished: true })
      .populate("categoryId", "name")
      .lean(),
    UserVocabularyProgress.findOne({ userId: decoded.uid, vocabularyId: vocabId }).lean(),
  ]);

  if (!word) throw new AppError(404, "WORD_NOT_FOUND", "Vocabulary word not found.");

  return ok({
    word: {
      id: String(word._id),
      word: word.word,
      slug: word.slug,
      meaning: word.meaning,
      pronunciation: word.pronunciation || "",
      audioUrl: word.audioUrl || null,
      imageUrl: word.imageUrl || null,
      partOfSpeech: word.partOfSpeech || "noun",
      level: word.level || "A1",
      categoryId: word.categoryId ? String(word.categoryId._id || word.categoryId) : "",
      categoryName: word.categoryId?.name || "",
      examples: (word.examples || []).map((e) => ({ en: e.en, bn: e.bn })),
      synonyms: word.synonyms || [],
      antonyms: word.antonyms || [],
      tags: word.tags || [],
      difficulty: word.difficulty || 1,
      progress: progress
        ? {
            status: progress.status,
            isFavorite: Boolean(progress.isFavorite),
            correctCount: progress.correctCount || 0,
            wrongCount: progress.wrongCount || 0,
            reviewCount: progress.reviewCount || 0,
            intervalDays: progress.intervalDays || 0,
            easeFactor: progress.easeFactor || 2.5,
            repetition: progress.repetition || 0,
            lastQuality: progress.lastQuality ?? null,
            lastReviewedAt: progress.lastReviewedAt || null,
            nextReviewAt: progress.nextReviewAt || null,
            learnedAt: progress.learnedAt || null,
          }
        : null,
    },
  });
}, { limit: 60 });
