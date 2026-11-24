import { NextResponse } from "next/server";
import { connectDB } from "@/lib/db";
import { isAdmin } from "@/lib/adminGuard";
import { apiHandler } from "@/lib/apiHandler";
import { AppError } from "@/lib/errors";
import { ok } from "@/lib/response";
import { readJson } from "@/validators/auth.validator";
import VocabularyCategory from "@/models/VocabularyCategory";
import Vocabulary from "@/models/Vocabulary";
import {
  cleanText,
  isValidObjectId,
  slugify,
  STANDARD_CATEGORIES,
} from "@/lib/vocabulary/validators";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/vocabulary/admin/categories
export const GET = apiHandler(async (req) => {
  if (!(await isAdmin(req))) {
    throw new AppError(401, "UNAUTHORIZED", "Admin access required.");
  }
  await connectDB();

  const [categories, countsAgg] = await Promise.all([
    VocabularyCategory.find({}).sort({ sortOrder: 1, name: 1 }).lean(),
    Vocabulary.aggregate([{ $group: { _id: "$categoryId", count: { $sum: 1 } } }]),
  ]);

  const countMap = new Map(countsAgg.map((c) => [String(c._id), c.count]));
  return ok({
    categories: categories.map((c) => ({
      ...c,
      id: String(c._id),
      parentId: c.parentId ? String(c.parentId) : null,
      wordCount: countMap.get(String(c._id)) || 0,
    })),
  });
}, { limit: 60 });

// POST /api/vocabulary/admin/categories
export const POST = apiHandler(async (req) => {
  if (!(await isAdmin(req))) {
    throw new AppError(401, "UNAUTHORIZED", "Admin access required.");
  }
  await connectDB();

  const body = await readJson(req);

  if (body?.action === "seed_defaults") {
    let createdCount = 0;
    for (const item of STANDARD_CATEGORIES) {
      const res = await VocabularyCategory.updateOne(
        { slug: item.slug },
        { $setOnInsert: { ...item, parentId: null, isActive: true } },
        { upsert: true }
      );
      if (res.upsertedCount > 0) createdCount += 1;
    }
    const all = await VocabularyCategory.find({}).sort({ sortOrder: 1, name: 1 }).lean();
    return ok({
      createdCount,
      categories: all.map((c) => ({ ...c, id: String(c._id), parentId: c.parentId ? String(c.parentId) : null })),
    });
  }

  const name = cleanText(body?.name, 80);
  if (!name) throw new AppError(400, "VALIDATION_ERROR", "Category name is required.");
  const slug = cleanText(body?.slug, 100) || slugify(name);

  const dup = await VocabularyCategory.findOne({ slug }).select("_id").lean();
  if (dup) throw new AppError(409, "DUPLICATE_CATEGORY", `Category "${slug}" already exists.`);

  let parentId = null;
  if (body?.parentId) {
    if (!isValidObjectId(body.parentId)) {
      throw new AppError(400, "VALIDATION_ERROR", "Invalid parentId.");
    }
    const parent = await VocabularyCategory.findById(body.parentId).select("_id").lean();
    if (!parent) throw new AppError(404, "PARENT_NOT_FOUND", "Parent category not found.");
    parentId = parent._id;
  }

  const created = await VocabularyCategory.create({
    name,
    slug,
    description: cleanText(body?.description || "", 300),
    icon: cleanText(body?.icon || "book", 60) || "book",
    image: cleanText(body?.image || "", 500) || null,
    parentId,
    sortOrder: Number(body?.sortOrder) || 0,
    isActive: body?.isActive !== undefined ? Boolean(body.isActive) : true,
  });

  return NextResponse.json(
    {
      success: true,
      data: {
        category: {
          ...created.toObject(),
          id: String(created._id),
          parentId: created.parentId ? String(created.parentId) : null,
        },
      },
      error: null,
    },
    { status: 201 }
  );
}, { limit: 60 });
