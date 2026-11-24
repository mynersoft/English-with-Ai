import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import VocabularyCategory from "@/models/VocabularyCategory";
import Vocabulary from "@/models/Vocabulary";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/vocabulary/categories -> returns parent categories and nested subcategories with word counts
export const GET = withAuth(async () => {
  const [cats, wordCounts] = await Promise.all([
    VocabularyCategory.find({ isActive: true })
      .sort({ sortOrder: 1, name: 1 })
      .lean(),
    Vocabulary.aggregate([
      { $match: { isPublished: true } },
      { $group: { _id: "$categoryId", count: { $sum: 1 } } },
    ]),
  ]);

  const countMap = new Map(wordCounts.map((c) => [String(c._id), c.count]));
  const subByParent = new Map();
  const parents = [];

  for (const c of cats) {
    const item = {
      id: String(c._id),
      name: c.name,
      slug: c.slug,
      description: c.description || "",
      icon: c.icon || "menu_book",
      image: c.image || null,
      parentId: c.parentId ? String(c.parentId) : null,
      sortOrder: c.sortOrder || 0,
      wordCount: countMap.get(String(c._id)) || 0,
      subcategories: [],
    };
    if (item.parentId) {
      const list = subByParent.get(item.parentId) || [];
      list.push(item);
      subByParent.set(item.parentId, list);
    } else {
      parents.push(item);
    }
  }

  for (const p of parents) {
    const subs = subByParent.get(p.id) || [];
    p.subcategories = subs;
    p.totalWordCount = p.wordCount + subs.reduce((acc, s) => acc + s.wordCount, 0);
  }

  return ok({ categories: parents });
}, { limit: 60 });
