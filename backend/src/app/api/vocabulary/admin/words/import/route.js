import { connectDB } from "@/lib/db";
import { isAdmin } from "@/lib/adminGuard";
import { apiHandler } from "@/lib/apiHandler";
import { AppError } from "@/lib/errors";
import { ok } from "@/lib/response";
import { readJson } from "@/validators/auth.validator";
import Vocabulary, { CEFR_LEVELS, PARTS_OF_SPEECH } from "@/models/Vocabulary";
import VocabularyCategory from "@/models/VocabularyCategory";
import { cleanStringList, cleanText, slugify } from "@/lib/vocabulary/validators";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function parseCsvLine(line) {
  const cells = [];
  let cur = "";
  let inQuotes = false;
  for (let i = 0; i < line.length; i += 1) {
    const ch = line[i];
    if (ch === '"') {
      if (inQuotes && line[i + 1] === '"') {
        cur += '"';
        i += 1;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (ch === "," && !inQuotes) {
      cells.push(cur.trim());
      cur = "";
    } else {
      cur += ch;
    }
  }
  cells.push(cur.trim());
  return cells;
}

function parseCsvText(csv) {
  const lines = String(csv || "")
    .replace(/\r\n/g, "\n")
    .replace(/\r/g, "\n")
    .split("\n")
    .map((l) => l.trim())
    .filter(Boolean);

  if (lines.length < 2) {
    throw new AppError(400, "VALIDATION_ERROR", "CSV must contain a header row and at least 1 data row.");
  }
  const headers = parseCsvLine(lines[0]).map((h) => h.trim());
  return lines.slice(1).map((line, index) => {
    const values = parseCsvLine(line);
    const row = { _rowNumber: index + 2 };
    headers.forEach((h, colIdx) => {
      row[h] = values[colIdx] ?? "";
    });
    return row;
  });
}

// POST /api/vocabulary/admin/words/import
// Body: { csv: string, publishByDefault?: boolean }
export const POST = apiHandler(async (req) => {
  if (!(await isAdmin(req))) {
    throw new AppError(401, "UNAUTHORIZED", "Admin access required.");
  }
  await connectDB();

  const body = await readJson(req);
  const rows = Array.isArray(body?.rows) ? body.rows : parseCsvText(body?.csv);
  const publishDefault = Boolean(body?.publishByDefault);

  if (rows.length > 500) {
    throw new AppError(400, "VALIDATION_ERROR", "Maximum 500 words per CSV import batch.");
  }

  const [categories, existingSlugs] = await Promise.all([
    VocabularyCategory.find({}).select("_id name slug").lean(),
    Vocabulary.find({}).distinct("slug"),
  ]);

  const catByKey = new Map();
  for (const c of categories) {
    catByKey.set(c.slug.toLowerCase(), c._id);
    catByKey.set(c.name.toLowerCase(), c._id);
    catByKey.set(String(c._id), c._id);
  }
  const existingSet = new Set(existingSlugs.map((s) => String(s).toLowerCase()));

  const toInsert = [];
  const errors = [];
  const skippedDuplicates = [];

  for (let i = 0; i < rows.length; i += 1) {
    const r = rows[i];
    const rowNum = r._rowNumber || i + 2;
    const word = cleanText(r.word, 120);
    const meaning = cleanText(r.meaning, 240);
    const level = cleanText(r.level || "A1", 10).toUpperCase();
    const pos = cleanText(r.partOfSpeech || "verb", 30).toLowerCase();
    const catLookup = cleanText(r.categorySlug || r.category || r.categoryId || "", 120).toLowerCase();
    const categoryId = catByKey.get(catLookup);

    if (!word || !meaning) {
      errors.push({ row: rowNum, word, error: "word and meaning are required" });
      continue;
    }
    if (!CEFR_LEVELS.includes(level)) {
      errors.push({ row: rowNum, word, error: `Invalid level "${level}"` });
      continue;
    }
    if (!PARTS_OF_SPEECH.includes(pos)) {
      errors.push({ row: rowNum, word, error: `Invalid partOfSpeech "${pos}"` });
      continue;
    }
    if (!categoryId) {
      errors.push({ row: rowNum, word, error: `Unknown category "${r.categorySlug || r.category || ""}"` });
      continue;
    }

    const examples = [
      { en: cleanText(r.example1En, 400), bn: cleanText(r.example1Bn, 400) },
      { en: cleanText(r.example2En, 400), bn: cleanText(r.example2Bn, 400) },
    ].filter((e) => e.en && e.bn);

    if (examples.length < 2) {
      errors.push({ row: rowNum, word, error: "At least 2 bilingual examples (example1En/Bn & example2En/Bn) required" });
      continue;
    }

    const slug = slugify(word);
    if (existingSet.has(slug)) {
      skippedDuplicates.push({ row: rowNum, word, slug });
      continue;
    }
    existingSet.add(slug);

    const isPubRaw = String(r.isPublished || "").trim().toLowerCase();
    const isPublished = isPubRaw ? isPubRaw === "true" || isPubRaw === "1" : publishDefault;

    toInsert.push({
      word,
      slug,
      meaning,
      pronunciation: cleanText(r.pronunciation || "", 160),
      partOfSpeech: pos,
      level,
      categoryId,
      examples,
      synonyms: cleanStringList(String(r.synonyms || "").split("|")),
      antonyms: cleanStringList(String(r.antonyms || "").split("|")),
      tags: cleanStringList(String(r.tags || "").split("|")),
      difficulty: Math.min(5, Math.max(1, Number(r.difficulty) || 1)),
      audioUrl: cleanText(r.audioUrl || "", 500) || null,
      imageUrl: cleanText(r.imageUrl || "", 500) || null,
      isPublished,
      sortOrder: Number(r.sortOrder) || 0,
    });
  }

  let insertedCount = 0;
  if (toInsert.length > 0) {
    const inserted = await Vocabulary.insertMany(toInsert, { ordered: false });
    insertedCount = inserted.length;
  }

  return ok(
    {
      totalRows: rows.length,
      insertedCount,
      skippedDuplicates,
      errors,
    },
    201
  );
}, { limit: 20 });
