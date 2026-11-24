import { NextResponse } from "next/server";
import { connectDB } from "@/lib/db";
import { isAdmin } from "@/lib/adminGuard";
import { apiHandler } from "@/lib/apiHandler";
import { AppError } from "@/lib/errors";
import { ok } from "@/lib/response";
import { readJson } from "@/validators/auth.validator";
import Vocabulary, { CEFR_LEVELS } from "@/models/Vocabulary";
import VocabularyCategory from "@/models/VocabularyCategory";
import {
  cleanText,
  escapeRegex,
  isValidObjectId,
  parsePagination,
  validateVocabularyPayload,
} from "@/lib/vocabulary/validators";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/vocabulary/admin/words?q=...&level=...&categoryId=...&published=all|true|false&page=1&limit=20
export const GET = apiHandler(async (req) => {
  if (!(await isAdmin(req))) {
    throw new AppError(401, "UNAUTHORIZED", "Admin access required.");
  }
  await connectDB();

  const { searchParams } = new URL(req.url);
  const { page, limit, skip } = parsePagination(searchParams, { defaultLimit: 20, maxLimit: 100 });
  const q = cleanText(searchParams.get("q") || "", 80);
  const level = cleanText(searchParams.get("level") || "", 10).toUpperCase();
  const categoryId = cleanText(searchParams.get("categoryId") || "", 40);
  const published = searchParams.get("published");

  const filter = {};
  if (CEFR_LEVELS.includes(level)) filter.level = level;
  if (categoryId && isValidObjectId(categoryId)) filter.categoryId = categoryId;
  if (published === "true") filter.isPublished = true;
  if (published === "false") filter.isPublished = false;
  if (q) {
    const rx = new RegExp(escapeRegex(q), "i");
    filter.$or = [{ word: rx }, { meaning: rx }, { tags: rx }];
  }

  const [words, total] = await Promise.all([
    Vocabulary.find(filter)
      .sort({ sortOrder: 1, createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .populate("categoryId", "name slug")
      .lean(),
    Vocabulary.countDocuments(filter),
  ]);

  return ok({
    words: words.map((w) => ({
      ...w,
      id: String(w._id),
      category:
        w.categoryId && typeof w.categoryId === "object"
          ? { id: String(w.categoryId._id), name: w.categoryId.name, slug: w.categoryId.slug }
          : null,
    })),
    pagination: {
      page,
      limit,
      total,
      totalPages: Math.max(1, Math.ceil(total / limit)),
    },
  });
}, { limit: 120 });

// POST /api/vocabulary/admin/words -> create word with duplicate detection
export const POST = apiHandler(async (req) => {
  if (!(await isAdmin(req))) {
    throw new AppError(401, "UNAUTHORIZED", "Admin access required.");
  }
  await connectDB();

  const body = await readJson(req);
  const data = validateVocabularyPayload(body, { partial: false });

  const [category, duplicate] = await Promise.all([
    VocabularyCategory.findById(data.categoryId).select("_id").lean(),
    Vocabulary.findOne({
      $or: [
        { slug: data.slug },
        { word: new RegExp(`^${escapeRegex(data.word)}$`, "i") },
      ],
    })
      .select("_id word")
      .lean(),
  ]);

  if (!category) {
    throw new AppError(400, "CATEGORY_NOT_FOUND", "Selected vocabulary category does not exist.");
  }
  if (duplicate) {
    throw new AppError(409, "DUPLICATE_WORD", `The word "${duplicate.word}" already exists.`);
  }

  const created = await Vocabulary.create(data);
  return NextResponse.json(
    { success: true, data: { word: { ...created.toObject(), id: String(created._id) } }, error: null },
    { status: 201 }
  );
}, { limit: 60 });
