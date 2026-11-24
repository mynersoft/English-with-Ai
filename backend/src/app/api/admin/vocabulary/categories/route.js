import { AppError } from "@/lib/errors";
import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { slugify } from "@/lib/vocabSrs";
import { readJson } from "@/validators/auth.validator";
import VocabularyCategory from "@/models/VocabularyCategory";
import Vocabulary from "@/models/Vocabulary";
import { assertObjectId } from "@/modules/vocabulary/vocabulary.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/admin/vocabulary/categories
export const GET = withAdmin(async () => {
  const [cats, counts] = await Promise.all([
    VocabularyCategory.find().sort({ sortOrder: 1, name: 1 }).lean(),
    Vocabulary.aggregate([{ $group: { _id: "$categoryId", count: { $sum: 1 } } }]),
  ]);
  const countMap = new Map(counts.map((c) => [String(c._id), c.count]));
  return ok({
    categories: cats.map((c) => ({
      id: String(c._id),
      name: c.name,
      slug: c.slug,
      description: c.description || "",
      icon: c.icon || "menu_book",
      image: c.image || null,
      parentId: c.parentId ? String(c.parentId) : null,
      sortOrder: c.sortOrder || 0,
      isActive: c.isActive !== false,
      wordCount: countMap.get(String(c._id)) || 0,
    })),
  });
}, { limit: 60 });

// POST /api/admin/vocabulary/categories
export const POST = withAdmin(async (request) => {
  const body = await readJson(request);
  const name = String(body?.name || "").trim();
  if (!name) throw new AppError(400, "VALIDATION_ERROR", "Category name is required.");
  const slug = slugify(body?.slug || name);
  const existing = await VocabularyCategory.findOne({ slug }).select("_id").lean();
  if (existing) throw new AppError(409, "DUPLICATE_CATEGORY", `Category "${name}" already exists.`);

  const parentId = body?.parentId ? assertObjectId(body.parentId, "parentId") : null;
  const created = await VocabularyCategory.create({
    name,
    slug,
    description: String(body?.description || "").trim(),
    icon: String(body?.icon || "menu_book").trim() || "menu_book",
    image: body?.image ? String(body.image).trim() : null,
    parentId,
    sortOrder: Number.isFinite(Number(body?.sortOrder)) ? Number(body.sortOrder) : 0,
    isActive: body?.isActive !== false,
  });

  return ok({ id: String(created._id), name: created.name, slug: created.slug }, 201);
}, { limit: 40 });
