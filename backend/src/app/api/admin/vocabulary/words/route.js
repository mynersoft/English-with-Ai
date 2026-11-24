import mongoose from "mongoose";
import { AppError } from "@/lib/errors";
import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { CEFR_LEVELS, normalizeVocabularyPayload, slugify } from "@/lib/vocabSrs";
import { readJson } from "@/validators/auth.validator";
import Vocabulary from "@/models/Vocabulary";
import VocabularyCategory from "@/models/VocabularyCategory";
import { assertObjectId } from "@/modules/vocabulary/vocabulary.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/admin/vocabulary/words?page=1&limit=20&search=&level=ALL&categoryId=&published=all
export const GET = withAdmin(async (request) => {
  const { searchParams } = new URL(request.url);
  const page = Math.max(1, Number(searchParams.get("page")) || 1);
  const limit = Math.max(1, Math.min(50, Number(searchParams.get("limit")) || 20));
  const search = (searchParams.get("search") || "").trim();
  const level = (searchParams.get("level") || "").trim().toUpperCase();
  const categoryId = (searchParams.get("categoryId") || "").trim();
  const published = (searchParams.get("published") || "all").trim().toLowerCase();

  const query = {};
  if (level && level !== "ALL" && CEFR_LEVELS.includes(level)) {
    query.level = level;
  }
  if (categoryId && mongoose.Types.ObjectId.isValid(categoryId)) {
    query.categoryId = new mongoose.Types.ObjectId(categoryId);
  }
  if (published === "true" || published === "published") query.isPublished = true;
  if (published === "false" || published === "unpublished") query.isPublished = false;
  if (search) {
    const escaped = search.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const rx = new RegExp(escaped, "i");
    query.$or = [{ word: rx }, { meaning: rx }, { tags: rx }];
  }

  const skip = (page - 1) * limit;
  const [words, total] = await Promise.all([
    Vocabulary.find(query)
      .sort({ sortOrder: 1, createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .populate("categoryId", "name")
      .lean(),
    Vocabulary.countDocuments(query),
  ]);

  return ok({
    items: words.map((w) => ({
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
      isPublished: w.isPublished !== false,
      sortOrder: w.sortOrder || 0,
    })),
    page,
    limit,
    total,
    totalPages: Math.max(1, Math.ceil(total / limit)),
  });
}, { limit: 60 });

// POST /api/admin/vocabulary/words
export const POST = withAdmin(async (request) => {
  const body = await readJson(request);
  const payload = normalizeVocabularyPayload(body);

  if (!payload.word || !payload.meaning) {
    throw new AppError(400, "VALIDATION_ERROR", "word and meaning are required.");
  }
  if (!payload.level) {
    throw new AppError(400, "VALIDATION_ERROR", "level must be one of A1, A2, B1, B2, C1, C2.");
  }

  let catObjId;
  if (payload.categoryId) {
    catObjId = assertObjectId(payload.categoryId, "categoryId");
  } else {
    const fallbackName = payload.category || "Daily Life";
    const escapedName = fallbackName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const catSlug = slugify(fallbackName) || "daily-life";
    let cat = await VocabularyCategory.findOne({
      $or: [{ slug: catSlug }, { name: new RegExp(`^${escapedName}$`, "i") }],
    })
      .select("_id")
      .lean();
    if (!cat) {
      cat = await VocabularyCategory.create({
        name: fallbackName,
        slug: catSlug,
        isActive: true,
      });
    }
    catObjId = cat._id;
  }

  const existing = await Vocabulary.findOne({ slug: payload.slug }).select("_id").lean();
  if (existing) {
    throw new AppError(409, "DUPLICATE_WORD", `Word "${payload.word}" already exists.`);
  }

  const created = await Vocabulary.create({
    word: payload.word,
    slug: payload.slug,
    meaning: payload.meaning,
    pronunciation: payload.pronunciation,
    audioUrl: payload.audioUrl,
    imageUrl: payload.imageUrl,
    partOfSpeech: payload.partOfSpeech,
    level: payload.level,
    categoryId: catObjId,
    examples: payload.examples,
    synonyms: payload.synonyms,
    antonyms: payload.antonyms,
    tags: payload.tags,
    difficulty: payload.difficulty,
    isPublished: payload.isPublished,
    sortOrder: payload.sortOrder,
  });

  return ok({ id: String(created._id), word: created.word }, 201);
}, { limit: 40 });
