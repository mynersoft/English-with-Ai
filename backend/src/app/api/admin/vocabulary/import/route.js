import { AppError } from "@/lib/errors";
import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import {
  normalizeVocabularyPayload,
  parseVocabularyCsv,
  slugify,
} from "@/lib/vocabSrs";
import { readJson } from "@/validators/auth.validator";
import VocabularyCategory from "@/models/VocabularyCategory";
import Vocabulary from "@/models/Vocabulary";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// POST /api/admin/vocabulary/import -> body: { csv: "..." }
export const POST = withAdmin(async (request) => {
  const body = await readJson(request);
  const csvText = String(body?.csv || "").trim();
  if (!csvText) {
    throw new AppError(400, "CSV_REQUIRED", "CSV content is required.");
  }

  const { records } = parseVocabularyCsv(csvText);
  if (records.length === 0) {
    throw new AppError(400, "EMPTY_CSV", "No data rows found in CSV.");
  }
  if (records.length > 500) {
    throw new AppError(400, "CSV_TOO_LARGE", "Maximum 500 rows per import.");
  }

  const [existingCategories, existingWords] = await Promise.all([
    VocabularyCategory.find().select("_id name slug").lean(),
    Vocabulary.find().select("slug").lean(),
  ]);

  const catByKey = new Map();
  for (const c of existingCategories) {
    catByKey.set(c.slug.toLowerCase(), c._id);
    catByKey.set(c.name.trim().toLowerCase(), c._id);
  }
  const existingSlugs = new Set(existingWords.map((w) => w.slug.toLowerCase()));
  const batchSlugs = new Set();

  const toInsert = [];
  const errors = [];
  let duplicates = 0;

  for (const rec of records) {
    const rowNum = rec._rowNumber;
    const norm = normalizeVocabularyPayload(rec);
    if (!norm.word || !norm.meaning) {
      errors.push(`Row ${rowNum}: word and meaning are required.`);
      continue;
    }
    if (!norm.level) {
      errors.push(`Row ${rowNum} (${norm.word}): invalid CEFR level.`);
      continue;
    }
    if (existingSlugs.has(norm.slug) || batchSlugs.has(norm.slug)) {
      duplicates += 1;
      continue;
    }

    const catKey = (norm.category || "daily-life").trim().toLowerCase();
    let catId = catByKey.get(catKey) || catByKey.get(slugify(catKey));
    if (!catId && norm.category) {
      // Auto-create missing category only when explicitly named in CSV
      const createdCat = await VocabularyCategory.create({
        name: norm.category,
        slug: slugify(norm.category),
      });
      catId = createdCat._id;
      catByKey.set(createdCat.slug, catId);
      catByKey.set(createdCat.name.toLowerCase(), catId);
    }
    if (!catId) {
      errors.push(`Row ${rowNum} (${norm.word}): category not found.`);
      continue;
    }

    batchSlugs.add(norm.slug);
    toInsert.push({
      word: norm.word,
      slug: norm.slug,
      meaning: norm.meaning,
      pronunciation: norm.pronunciation,
      audioUrl: norm.audioUrl,
      imageUrl: norm.imageUrl,
      partOfSpeech: norm.partOfSpeech,
      level: norm.level,
      categoryId: catId,
      examples: norm.examples,
      synonyms: norm.synonyms,
      antonyms: norm.antonyms,
      tags: norm.tags,
      difficulty: norm.difficulty,
      isPublished: norm.isPublished,
      sortOrder: norm.sortOrder,
    });
  }

  let inserted = 0;
  if (toInsert.length > 0) {
    const res = await Vocabulary.insertMany(toInsert, { ordered: false });
    inserted = res.length;
  }

  return ok({
    totalRows: records.length,
    inserted,
    duplicates,
    errors,
  });
}, { limit: 10 });
