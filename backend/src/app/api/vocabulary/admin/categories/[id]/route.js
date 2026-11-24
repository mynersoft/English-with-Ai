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
  requireObjectId,
  slugify,
} from "@/lib/vocabulary/validators";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// PUT /api/vocabulary/admin/categories/:id
export const PUT = apiHandler(async (req, { params }) => {
  if (!(await isAdmin(req))) {
    throw new AppError(401, "UNAUTHORIZED", "Admin access required.");
  }
  const { id } = await params;
  const catId = requireObjectId(id, "category id");
  await connectDB();

  const body = await readJson(req);
  const update = {};
  if (body?.name !== undefined) {
    const name = cleanText(body.name, 80);
    if (!name) throw new AppError(400, "VALIDATION_ERROR", "Category name cannot be empty.");
    update.name = name;
    if (body?.slug === undefined) update.slug = slugify(name);
  }
  if (body?.slug !== undefined) update.slug = slugify(body.slug);
  if (body?.description !== undefined) update.description = cleanText(body.description, 300);
  if (body?.icon !== undefined) update.icon = cleanText(body.icon, 60) || "book";
  if (body?.image !== undefined) update.image = cleanText(body.image || "", 500) || null;
  if (body?.sortOrder !== undefined) update.sortOrder = Number(body.sortOrder) || 0;
  if (body?.isActive !== undefined) update.isActive = Boolean(body.isActive);
  if (body?.parentId !== undefined) {
    if (!body.parentId) {
      update.parentId = null;
    } else if (!isValidObjectId(body.parentId) || String(body.parentId) === String(catId)) {
      throw new AppError(400, "VALIDATION_ERROR", "Invalid parentId.");
    } else {
      update.parentId = body.parentId;
    }
  }

  const updated = await VocabularyCategory.findByIdAndUpdate(
    catId,
    { $set: update },
    { new: true, runValidators: true }
  ).lean();
  if (!updated) throw new AppError(404, "CATEGORY_NOT_FOUND", "Category not found.");

  return ok({
    category: {
      ...updated,
      id: String(updated._id),
      parentId: updated.parentId ? String(updated.parentId) : null,
    },
  });
}, { limit: 60 });

// DELETE /api/vocabulary/admin/categories/:id
export const DELETE = apiHandler(async (req, { params }) => {
  if (!(await isAdmin(req))) {
    throw new AppError(401, "UNAUTHORIZED", "Admin access required.");
  }
  const { id } = await params;
  const catId = requireObjectId(id, "category id");
  await connectDB();

  const [wordCount, childCount] = await Promise.all([
    Vocabulary.countDocuments({ categoryId: catId }),
    VocabularyCategory.countDocuments({ parentId: catId }),
  ]);

  if (wordCount > 0 || childCount > 0) {
    throw new AppError(
      400,
      "CATEGORY_IN_USE",
      "Reassign or delete words and subcategories inside this category before deleting it."
    );
  }

  const deleted = await VocabularyCategory.findByIdAndDelete(catId).lean();
  if (!deleted) throw new AppError(404, "CATEGORY_NOT_FOUND", "Category not found.");

  return ok({ deleted: true, id: String(catId) });
}, { limit: 60 });
