/**
 * Pure vocabulary learning logic: SM-2 inspired spaced repetition,
 * local-timezone day formatting, streak computation, quiz scoring, and CSV parsing.
 */

export const MIN_DAILY_NEW_WORDS = 5;
export const DEFAULT_DAILY_NEW_WORDS = 5;
export const MAX_DAILY_NEW_WORDS = 500;
export const DEFAULT_TIMEZONE = "Asia/Dhaka";
export const CEFR_LEVELS = ["A1", "A2", "B1", "B2", "C1", "C2"];
export const SRS_STEP_DAYS = [0, 1, 3, 7, 14, 30];

export function slugify(text) {
  return String(text || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

export function normalizeDailyGoal(goal) {
  const num = Number(goal);
  if (!Number.isFinite(num)) return DEFAULT_DAILY_NEW_WORDS;
  return Math.max(MIN_DAILY_NEW_WORDS, Math.min(MAX_DAILY_NEW_WORDS, Math.round(num)));
}

export function isValidTimezone(tz) {
  if (!tz || typeof tz !== "string") return false;
  try {
    Intl.DateTimeFormat("en-US", { timeZone: tz.trim() }).format(new Date());
    return true;
  } catch {
    return false;
  }
}

export function normalizeTimezone(tz) {
  return isValidTimezone(tz) ? tz.trim() : DEFAULT_TIMEZONE;
}

/** Returns local YYYY-MM-DD for `date` in `timeZone` (defaults to Asia/Dhaka). */
export function getLocalDateString(date = new Date(), timeZone = DEFAULT_TIMEZONE) {
  const safeTz = normalizeTimezone(timeZone);
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: safeTz,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(date);
  const get = (type) => parts.find((p) => p.type === type)?.value;
  return `${get("year")}-${get("month")}-${get("day")}`;
}

/** Shifts a YYYY-MM-DD date string by `deltaDays` cleanly without local DST drift. */
export function shiftLocalDate(yyyyMmDd, deltaDays) {
  const [y, m, d] = String(yyyyMmDd).split("-").map(Number);
  const utc = Date.UTC(y, m - 1, d + deltaDays);
  const dt = new Date(utc);
  const yy = dt.getUTCFullYear();
  const mm = String(dt.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(dt.getUTCDate()).padStart(2, "0");
  return `${yy}-${mm}-${dd}`;
}

/**
 * SM-2 inspired spaced repetition schedule.
 * Intervals progression: Day 0 -> 1 -> 3 -> 7 -> 14 -> 30.
 * - quality 0..2: incorrect -> reset repetition = 0, short interval (1 day), status = "learning"
 * - quality 3: hard -> modest growth
 * - quality 4: correct -> standard progression [1, 3, 7, 14, 30]
 * - quality 5: easy -> standard progression with higher ease factor
 * - status = "mastered" when repetition >= 5 AND intervalDays >= 30.
 */
export function calculateSrsSchedule(current = {}, quality, now = new Date()) {
  const q = Math.max(0, Math.min(5, Math.round(Number(quality))));
  const prevRep = Math.max(0, Number(current.repetition || 0));
  const prevEf = Math.max(1.3, Number(current.easeFactor || 2.5));
  const prevInterval = Math.max(0, Number(current.intervalDays || 0));

  let repetition;
  let intervalDays;
  let easeFactor = prevEf + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02));
  easeFactor = Math.round(Math.max(1.3, easeFactor) * 100) / 100;

  if (q <= 2) {
    repetition = 0;
    intervalDays = 1;
    easeFactor = Math.round(Math.max(1.3, prevEf - 0.2) * 100) / 100;
  } else {
    repetition = prevRep + 1;
    if (repetition === 1) {
      intervalDays = 1;
    } else if (repetition === 2) {
      intervalDays = q === 3 ? 2 : 3;
    } else if (repetition === 3) {
      intervalDays = q === 3 ? 5 : 7;
    } else if (repetition === 4) {
      intervalDays = q === 3 ? 10 : 14;
    } else if (repetition === 5) {
      intervalDays = q === 3 ? 21 : 30;
    } else {
      const factor = q === 3 ? Math.max(1.2, easeFactor * 0.85) : easeFactor;
      intervalDays = Math.max(30, Math.round(prevInterval * factor));
    }
  }

  let status;
  if (repetition >= 5 && intervalDays >= 30) {
    status = "mastered";
  } else if (repetition >= 2) {
    status = "review";
  } else {
    status = "learning";
  }

  const nextReviewAt = new Date(now.getTime() + intervalDays * 86_400_000);
  return {
    repetition,
    intervalDays,
    easeFactor,
    lastQuality: q,
    lastReviewedAt: now,
    nextReviewAt,
    status,
    isCorrect: q >= 3,
  };
}

/**
 * Consecutive local days with `isGoalCompleted === true` or `reviewedWords > 0`.
 * If today has not been completed yet, counts backwards from yesterday so an
 * ongoing streak stays alive throughout the day until midnight.
 */
export function calculateStreak(activities, todayDateStr) {
  const activeDates = new Set();
  for (const act of activities || []) {
    if (act && act.date && (act.isGoalCompleted || Number(act.reviewedWords || 0) > 0)) {
      activeDates.add(act.date);
    }
  }
  if (activeDates.size === 0) return 0;

  let cursor = activeDates.has(todayDateStr) ? todayDateStr : shiftLocalDate(todayDateStr, -1);
  if (!activeDates.has(cursor)) return 0;

  let streak = 0;
  while (activeDates.has(cursor)) {
    streak += 1;
    cursor = shiftLocalDate(cursor, -1);
  }
  return streak;
}

/**
 * Evaluates a list of quiz answer items `{ vocabularyId, selectedAnswer, expectedAnswer }`
 * or `{ vocabularyId, isCorrect }` and computes score 0..100 without trusting client score.
 */
export function scoreQuizAnswers(items = []) {
  if (!Array.isArray(items) || items.length === 0) {
    return { totalQuestions: 0, correctAnswers: 0, wrongAnswers: 0, score: 0, results: [] };
  }
  const results = items.map((item) => {
    const expected = item.expectedAnswer != null ? String(item.expectedAnswer).trim().toLowerCase() : null;
    const selected = item.selectedAnswer != null ? String(item.selectedAnswer).trim().toLowerCase() : null;
    const isCorrect =
      expected !== null && selected !== null
        ? selected === expected
        : Boolean(item.isCorrect);
    return {
      vocabularyId: String(item.vocabularyId || ""),
      isCorrect,
    };
  });
  const totalQuestions = results.length;
  const correctAnswers = results.filter((r) => r.isCorrect).length;
  const wrongAnswers = totalQuestions - correctAnswers;
  const score = Math.round((correctAnswers / totalQuestions) * 100);
  return { totalQuestions, correctAnswers, wrongAnswers, score, results };
}

const CSV_HEADER_ALIASES = {
  word: "word",
  english: "word",
  englishword: "word",
  meaning: "meaning",
  bangla: "meaning",
  bengali: "meaning",
  bengalimeaning: "meaning",
  banglameaning: "meaning",
  pronunciation: "pronunciation",
  ipa: "pronunciation",
  partofspeech: "partOfSpeech",
  pos: "partOfSpeech",
  level: "level",
  cefr: "level",
  cefrlevel: "level",
  category: "category",
  categoryname: "category",
  categoryid: "categoryId",
  example1en: "example1En",
  example1bn: "example1Bn",
  example2en: "example2En",
  example2bn: "example2Bn",
  synonyms: "synonyms",
  antonyms: "antonyms",
  tags: "tags",
  difficulty: "difficulty",
  ispublished: "isPublished",
  published: "isPublished",
  audiourl: "audioUrl",
  imageurl: "imageUrl",
  sortorder: "sortOrder",
};

function canonicalCsvHeader(rawHeader) {
  const trimmed = String(rawHeader || "").replace(/^\uFEFF/, "").trim();
  const key = trimmed.toLowerCase().replace(/[^a-z0-9]/g, "");
  return CSV_HEADER_ALIASES[key] || trimmed;
}

/**
 * Parses CSV text cleanly (handles UTF-8 BOM, quoted commas, and escaped double quotes) into row objects.
 * Expected headers (case-insensitive):
 * word,meaning,pronunciation,partOfSpeech,level,category,example1En,example1Bn,example2En,example2Bn,synonyms,antonyms,tags,difficulty,isPublished
 */
export function parseVocabularyCsv(csvText) {
  const rows = [];
  let row = [];
  let cell = "";
  let inQuotes = false;
  const text = String(csvText || "")
    .replace(/^\uFEFF/, "")
    .replace(/\r\n/g, "\n")
    .replace(/\r/g, "\n");

  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (inQuotes) {
      if (ch === '"' && text[i + 1] === '"') {
        cell += '"';
        i++;
      } else if (ch === '"') {
        inQuotes = false;
      } else {
        cell += ch;
      }
    } else if (ch === '"') {
      inQuotes = true;
    } else if (ch === ",") {
      row.push(cell.trim());
      cell = "";
    } else if (ch === "\n") {
      row.push(cell.trim());
      if (row.some((c) => c.length > 0)) rows.push(row);
      row = [];
      cell = "";
    } else {
      cell += ch;
    }
  }
  row.push(cell.trim());
  if (row.some((c) => c.length > 0)) rows.push(row);

  if (rows.length < 2) {
    return { headers: rows[0] || [], records: [] };
  }

  const headers = rows[0].map(canonicalCsvHeader);
  const records = rows.slice(1).map((cols, idx) => {
    const obj = { _rowNumber: idx + 2 };
    headers.forEach((h, colIdx) => {
      if (h) obj[h] = cols[colIdx] ?? "";
    });
    return obj;
  });

  return { headers, records };
}

export function normalizeVocabularyPayload(input = {}) {
  const word = String(input.word || "").trim();
  const meaning = String(input.meaning || "").trim();
  const levelRaw = String(input.level || "A1").trim().toUpperCase();
  const level = CEFR_LEVELS.includes(levelRaw) ? levelRaw : null;

  const splitList = (val) => {
    if (Array.isArray(val)) return val.map((v) => String(v).trim()).filter(Boolean);
    return String(val || "")
      .split(/[;|,]/)
      .map((s) => s.trim())
      .filter(Boolean);
  };

  let examples = [];
  if (Array.isArray(input.examples)) {
    examples = input.examples
      .map((e) => ({ en: String(e?.en || "").trim(), bn: String(e?.bn || "").trim() }))
      .filter((e) => e.en && e.bn);
  } else {
    if (input.example1En && input.example1Bn) {
      examples.push({ en: String(input.example1En).trim(), bn: String(input.example1Bn).trim() });
    }
    if (input.example2En && input.example2Bn) {
      examples.push({ en: String(input.example2En).trim(), bn: String(input.example2Bn).trim() });
    }
  }

  const diffNum = Number(input.difficulty || 1);
  const difficulty = Number.isFinite(diffNum) ? Math.max(1, Math.min(5, Math.round(diffNum))) : 1;

  return {
    word,
    slug: slugify(input.slug || word),
    meaning,
    pronunciation: String(input.pronunciation || "").trim(),
    audioUrl: input.audioUrl ? String(input.audioUrl).trim() : null,
    imageUrl: input.imageUrl ? String(input.imageUrl).trim() : null,
    partOfSpeech: String(input.partOfSpeech || "noun").trim().toLowerCase() || "noun",
    level,
    categoryId: input.categoryId ? String(input.categoryId).trim() : "",
    category: input.category ? String(input.category).trim() : "",
    examples,
    synonyms: splitList(input.synonyms),
    antonyms: splitList(input.antonyms),
    tags: splitList(input.tags),
    difficulty,
    isPublished:
      input.isPublished === undefined
        ? true
        : String(input.isPublished).toLowerCase() !== "false" && input.isPublished !== false,
    sortOrder: Number.isFinite(Number(input.sortOrder)) ? Number(input.sortOrder) : 0,
  };
}

function stableHashInt(input = "") {
  let hash = 2166136261;
  const s = String(input || "");
  for (let i = 0; i < s.length; i += 1) {
    hash ^= s.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

function formatWordOfTheDayItem(wordDoc, dateStr) {
  const firstEx = Array.isArray(wordDoc?.examples) ? wordDoc.examples[0] : null;
  return {
    id: String(wordDoc._id || wordDoc.id || ""),
    word: String(wordDoc.word || "").trim(),
    meaning: String(wordDoc.meaning || "").trim(),
    pronunciation: String(wordDoc.pronunciation || "").trim(),
    partOfSpeech: String(wordDoc.partOfSpeech || "noun").trim(),
    level: String(wordDoc.level || "A1").trim(),
    categoryName: String(wordDoc.categoryName || wordDoc.categoryId?.name || "").trim(),
    exampleEn: String(firstEx?.en || wordDoc.exampleEn || "").trim(),
    exampleBn: String(firstEx?.bn || wordDoc.exampleBn || "").trim(),
    date: dateStr,
  };
}

export function selectWordOfTheDayFromPool({
  words = [],
  learnedIds = new Set(),
  masteredIds = new Set(),
  dateStr = "2026-01-01",
  userId = "",
  upcomingCount = 7,
}) {
  const valid = words.filter(
    (w) => String(w?.word || "").trim() && String(w?.meaning || "").trim()
  );
  if (valid.length === 0) {
    return { date: dateStr, wordOfTheDay: null, upcoming: [] };
  }

  const unlearned = valid.filter((w) => {
    const id = String(w._id || w.id || "");
    return !learnedIds.has(id) && !masteredIds.has(id);
  });
  const nonMastered = valid.filter((w) => !masteredIds.has(String(w._id || w.id || "")));

  const primaryPool =
    unlearned.length > 0 ? unlearned : nonMastered.length > 0 ? nonMastered : valid;

  const dayNumber = Math.floor(Date.parse(`${dateStr}T00:00:00Z`) / 86400000) || 0;
  const userOffset = stableHashInt(String(userId || "")) % primaryPool.length;
  const startIdx = ((dayNumber % primaryPool.length) + primaryPool.length + userOffset) % primaryPool.length;

  const ordered = [];
  const seenWords = new Set();
  for (let i = 0; i < primaryPool.length && ordered.length < 1 + upcomingCount; i += 1) {
    const item = primaryPool[(startIdx + i) % primaryPool.length];
    const key = String(item.word || "").trim().toLowerCase();
    if (!seenWords.has(key)) {
      seenWords.add(key);
      ordered.push(item);
    }
  }
  if (ordered.length < 1 + upcomingCount) {
    for (const item of valid) {
      if (ordered.length >= 1 + upcomingCount) break;
      const key = String(item.word || "").trim().toLowerCase();
      if (!seenWords.has(key)) {
        seenWords.add(key);
        ordered.push(item);
      }
    }
  }

  const [todayDoc, ...restDocs] = ordered;
  return {
    date: dateStr,
    wordOfTheDay: todayDoc ? formatWordOfTheDayItem(todayDoc, dateStr) : null,
    upcoming: restDocs.slice(0, upcomingCount).map((doc, idx) =>
      formatWordOfTheDayItem(doc, shiftLocalDate(dateStr, idx + 1))
    ),
  };
}

