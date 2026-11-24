import mongoose from "mongoose";
import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { CEFR_LEVELS } from "@/lib/vocabSrs";
import Vocabulary from "@/models/Vocabulary";
import UserVocabularyProgress from "@/models/UserVocabularyProgress";
import { expandCategoryIds } from "@/modules/vocabulary/vocabulary.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/vocabulary/words?page=1&limit=20&level=A1&categoryId=...&search=...
export const GET = withAuth(async (request, { decoded }) => {
  const { searchParams } = new URL(request.url);
  const page = Math.max(1, Number(searchParams.get("page")) || 1);
  const limit = Math.max(1, Math.min(50, Number(searchParams.get("limit")) || 20));
  const level = (searchParams.get("level") || "").trim().toUpperCase();
  const categoryId = (searchParams.get("categoryId") || "").trim();
  const search = (searchParams.get("search") || "").trim();

  const query = { isPublished: true };
  if (level && level !== "ALL" && CEFR_LEVELS.includes(level)) {
    query.level = level;
  }
  if (categoryId && mongoose.Types.ObjectId.isValid(categoryId)) {
    const expanded = await expandCategoryIds([categoryId]);
    query.categoryId = { $in: expanded };
  }
  if (search) {
    const escaped = search.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const rx = new RegExp(escaped, "i");
    query.$or = [{ word: rx }, { meaning: rx }, { tags: rx }];
  }

  const skip = (page - 1) * limit;
  const [words, total] = await Promise.all([
    Vocabulary.find(query)
      .sort({ sortOrder: 1, word: 1 })
      .skip(skip)
      .limit(limit)
      .populate("categoryId", "name")
      .lean(),
    Vocabulary.countDocuments(query),
  ]);

  const wordIds = words.map((w) => w._id);
  const progressRows = wordIds.length
    ? await UserVocabularyProgress.find({
        userId: decoded.uid,
        vocabularyId: { $in: wordIds },
      }).lean()
    : [];
  const progMap = new Map(progressRows.map((p) => [String(p.vocabularyId), p]));

  const items = words.map((w) => {
    const p = progMap.get(String(w._id));
    return {
      id: String(w._id),
      word: w.word,
      slug: w.slug,
      meaning: w.meaning,
      pronunciation: w.pronunciation || "",
      audioUrl: w.audioUrl || null,
      imageUrl: w.imageUrl || null,
      partOfSpeech: w.partOfSpeech || "noun",
      level: w.level || "A1",
      categoryId: w.categoryId ? String(w.categoryId._id || w.categoryId) : "",
      categoryName: w.categoryId?.name || "",
      examples: (w.examples || []).map((e) => ({ en: e.en, bn: e.bn })),
      synonyms: w.synonyms || [],
      antonyms: w.antonyms || [],
      tags: w.tags || [],
      difficulty: w.difficulty || 1,
      progress: p
        ? {
            status: p.status,
            isFavorite: Boolean(p.isFavorite),
            correctCount: p.correctCount || 0,
            wrongCount: p.wrongCount || 0,
            reviewCount: p.reviewCount || 0,
            intervalDays: p.intervalDays || 0,
            repetition: p.repetition || 0,
            nextReviewAt: p.nextReviewAt || null,
            learnedAt: p.learnedAt || null,
          }
        : null,
    };
  });

  return ok({
    items,
    page,
    limit,
    total,
    totalPages: Math.max(1, Math.ceil(total / limit)),
  });
}, { limit: 60 });
