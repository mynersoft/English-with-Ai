import mongoose from "mongoose";
import { AppError } from "@/lib/errors";
import { CEFR_LEVELS, PARTS_OF_SPEECH } from "@/models/Vocabulary";

export function isValidObjectId(value) {
  return typeof value === "string" && mongoose.Types.ObjectId.isValid(value);
}

export function requireObjectId(value, label = "id") {
  if (!isValidObjectId(value)) {
    throw new AppError(400, "INVALID_ID", `Invalid ${label}.`);
  }
  return new mongoose.Types.ObjectId(value);
}

export function cleanText(value, maxLen = 200) {
  if (typeof value !== "string") return "";
  return value.trim().replace(/\s+/g, " ").slice(0, maxLen);
}

export function escapeRegex(value) {
  return String(value || "").replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

export function slugify(text, fallback = "item") {
  const base = String(text || "")
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9\s-]/g, "")
    .replace(/\s+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
  return base || fallback;
}

export function cleanStringList(input, { maxItems = 12, maxLen = 80 } = {}) {
  const arr = Array.isArray(input)
    ? input
    : typeof input === "string"
      ? input.split(",")
      : [];
  const seen = new Set();
  const out = [];
  for (const raw of arr) {
    const item = cleanText(String(raw || ""), maxLen);
    if (!item) continue;
    const key = item.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(item);
    if (out.length >= maxItems) break;
  }
  return out;
}

export function parsePagination(searchParams, { defaultLimit = 20, maxLimit = 50 } = {}) {
  const pageRaw = Number(searchParams?.get("page") || 1);
  const limitRaw = Number(searchParams?.get("limit") || defaultLimit);
  const page = Number.isFinite(pageRaw) && pageRaw >= 1 ? Math.floor(pageRaw) : 1;
  const limit = Number.isFinite(limitRaw)
    ? Math.min(maxLimit, Math.max(1, Math.floor(limitRaw)))
    : defaultLimit;
  return { page, limit, skip: (page - 1) * limit };
}

export function validateVocabularyPayload(body, { partial = false } = {}) {
  const out = {};

  if (!partial || body?.word !== undefined) {
    const word = cleanText(body?.word, 120);
    if (!word) throw new AppError(400, "VALIDATION_ERROR", "Word is required.");
    out.word = word;
    out.slug = cleanText(body?.slug, 140) || slugify(word);
  }

  if (!partial || body?.meaning !== undefined) {
    const meaning = cleanText(body?.meaning, 240);
    if (!meaning) throw new AppError(400, "VALIDATION_ERROR", "Bengali meaning is required.");
    out.meaning = meaning;
  }

  if (body?.pronunciation !== undefined || !partial) {
    out.pronunciation = cleanText(body?.pronunciation || "", 160);
  }

  if (body?.audioUrl !== undefined) {
    const url = cleanText(body?.audioUrl || "", 500);
    out.audioUrl = url || null;
  }

  if (body?.imageUrl !== undefined) {
    const url = cleanText(body?.imageUrl || "", 500);
    out.imageUrl = url || null;
  }

  if (!partial || body?.partOfSpeech !== undefined) {
    const pos = cleanText(body?.partOfSpeech || "verb", 30).toLowerCase();
    if (!PARTS_OF_SPEECH.includes(pos)) {
      throw new AppError(400, "VALIDATION_ERROR", `Invalid partOfSpeech: ${pos}`);
    }
    out.partOfSpeech = pos;
  }

  if (!partial || body?.level !== undefined) {
    const level = cleanText(body?.level, 10).toUpperCase();
    if (!CEFR_LEVELS.includes(level)) {
      throw new AppError(400, "VALIDATION_ERROR", `Level must be one of: ${CEFR_LEVELS.join(", ")}`);
    }
    out.level = level;
  }

  if (!partial || body?.categoryId !== undefined) {
    out.categoryId = requireObjectId(body?.categoryId, "categoryId");
  }

  if (!partial || body?.examples !== undefined) {
    const rawExamples = Array.isArray(body?.examples) ? body.examples : [];
    const examples = rawExamples
      .map((ex) => ({
        en: cleanText(ex?.en, 400),
        bn: cleanText(ex?.bn, 400),
      }))
      .filter((ex) => ex.en && ex.bn)
      .slice(0, 10);
    if (!partial && examples.length < 2) {
      throw new AppError(400, "VALIDATION_ERROR", "At least 2 bilingual examples (en + bn) are required.");
    }
    if (partial && body?.examples !== undefined && examples.length < 2) {
      throw new AppError(400, "VALIDATION_ERROR", "At least 2 bilingual examples (en + bn) are required.");
    }
    out.examples = examples;
  }

  if (body?.synonyms !== undefined || !partial) {
    out.synonyms = cleanStringList(body?.synonyms, { maxItems: 12, maxLen: 80 });
  }
  if (body?.antonyms !== undefined || !partial) {
    out.antonyms = cleanStringList(body?.antonyms, { maxItems: 12, maxLen: 80 });
  }
  if (body?.tags !== undefined || !partial) {
    out.tags = cleanStringList(body?.tags, { maxItems: 12, maxLen: 50 });
  }

  if (body?.difficulty !== undefined || !partial) {
    const d = Number(body?.difficulty ?? 1);
    out.difficulty = Number.isFinite(d) ? Math.min(5, Math.max(1, Math.round(d))) : 1;
  }

  if (body?.isPublished !== undefined) {
    out.isPublished = Boolean(body.isPublished);
  } else if (!partial) {
    out.isPublished = false;
  }

  if (body?.sortOrder !== undefined || !partial) {
    const s = Number(body?.sortOrder ?? 0);
    out.sortOrder = Number.isFinite(s) ? Math.round(s) : 0;
  }

  return out;
}

export function validateTimezone(tz) {
  const candidate = cleanText(tz, 60) || "Asia/Dhaka";
  try {
    Intl.DateTimeFormat("en-US", { timeZone: candidate });
    return candidate;
  } catch {
    return "Asia/Dhaka";
  }
}

export const STANDARD_CATEGORIES = [
  { name: "Daily Life", slug: "daily-life", icon: "sun", sortOrder: 1, description: "Essential everyday words and routines" },
  { name: "Communication", slug: "communication", icon: "message", sortOrder: 2, description: "Conversations, expressing thoughts, and polite phrases" },
  { name: "Family", slug: "family", icon: "home", sortOrder: 3, description: "Home, relatives, and family activities" },
  { name: "Work & Office", slug: "work-office", icon: "briefcase", sortOrder: 4, description: "Workplace tasks, meetings, and teamwork" },
  { name: "Business", slug: "business", icon: "chart", sortOrder: 5, description: "Commerce, finance, and negotiations" },
  { name: "Shopping", slug: "shopping", icon: "bag", sortOrder: 6, description: "Buying, prices, stores, and discounts" },
  { name: "Travel", slug: "travel", icon: "plane", sortOrder: 7, description: "Directions, transport, hotels, and trips" },
  { name: "Food & Cooking", slug: "food-cooking", icon: "utensils", sortOrder: 8, description: "Meals, ingredients, dining, and recipes" },
  { name: "Health", slug: "health", icon: "heart", sortOrder: 9, description: "Wellness, medical visits, and fitness" },
  { name: "Education", slug: "education", icon: "book", sortOrder: 10, description: "Study, school, research, and exams" },
  { name: "Technology", slug: "technology", icon: "cpu", sortOrder: 11, description: "Devices, internet, software, and digital life" },
  { name: "Emotions", slug: "emotions", icon: "smile", sortOrder: 12, description: "Feelings, attitudes, and reactions" },
  { name: "Relationships", slug: "relationships", icon: "users", sortOrder: 13, description: "Friendships, trust, and social bonds" },
];
