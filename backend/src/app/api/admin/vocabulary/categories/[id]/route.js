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

// PUT /api/admin/vocabulary/categories/:id
export const PUT = withAdmin(async (request, { params }) => {
  const { id } = await params;
  const catId = assertObjectId(id, "categoryId");
  const body = await readJson(request);

  const cat = await VocabularyCategory.findById(catId);
  if (!cat) throw new AppError(404, "CATEGORY_NOT_FOUND", "Category not found.");

  if (body.name !== undefined) {
    const clean = String(body.name).trim();
    if (!clean) throw new AppError(400, "VALIDATION_ERROR", "name cannot be empty.");
    const nextSlug = slugify(body.slug || clean);
    const conflict = await VocabularyCategory.findOne({ slug: nextSlug, _id: { $ne: catId } }).select("_id").lean();
    if (conflict) throw new AppError(409, "DUPLICATE_CATEGORY", `Category "${clean}" already exists.`);
    cat.name = clean;
    cat.slug = nextSlug;
  }
  if (body.description !== undefined) cat.description = String(body.description || "").trim();
  if (body.icon !== undefined) cat.icon = String(body.icon || "menu_book").trim();
  if (body.image !== undefined) cat.image = body.image ? String(body.image).trim() : null;
  if (body.parentId !== undefined) {
    if (!body.parentId) {
      cat.parentId = null;
    } else {
      const pid = assertObjectId(body.parentId, "parentId");
      if (String(pid) === String(catId)) {
        throw new AppError(400, "INVALID_PARENT", "A category cannot be its own parent.");
      }
      cat.parentId = pid;
    }
  }
  if (body.sortOrder !== undefined && Number.isFinite(Number(body.sortOrder))) {
    cat.sortOrder = Number(body.sortOrder);
  }
  if (body.isActive !== undefined) cat.isActive = Boolean(body.isActive);

  await cat.save();
  return ok({ id: String(cat._id), isActive: cat.isActive });
}, { limit: 40 });

// DELETE /api/admin/vocabulary/categories/:id
export const DELETE = withAdmin(async (request, { params }) => {
  const { id } = await params;
  const catId = assertObjectId(id, "categoryId");
  const [wordCount, childCount] = await Promise.all([
    Vocabulary.countDocuments({ categoryId: catId }),
    VocabularyCategory.countDocuments({ parentId: catId }),
  ]);
  if (wordCount > 0 || childCount > 0) {
    throw new AppError(
      409,
      "CATEGORY_IN_USE",
      "Cannot delete category with existing words or subcategories. Deactivate it instead."
    );
  }
  const deleted = await VocabularyCategory.findByIdAndDelete(catId).lean();
  if (!deleted) throw new AppError(404, "CATEGORY_NOT_FOUND", "Category not found.");
  return ok({ deleted: true });
}, { limit: 40 });
