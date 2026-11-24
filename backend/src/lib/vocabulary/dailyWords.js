import Vocabulary from "@/models/Vocabulary";
import VocabularyCategory from "@/models/VocabularyCategory";
import UserVocabularyProgress from "@/models/UserVocabularyProgress";
import VocabularyDailyActivity from "@/models/VocabularyDailyActivity";
import VocabularySettings from "@/models/VocabularySettings";
import { validateTimezone } from "@/lib/vocabulary/validators";

export const MAX_DAILY_NEW_WORDS = 5;

export function getLocalDateString(timezone = "Asia/Dhaka", now = new Date()) {
  const safeTz = validateTimezone(timezone);
  // en-CA formats reliably as YYYY-MM-DD
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: safeTz,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now);
}

export function getPreviousDateString(dateStr, daysBack = 1) {
  const [y, m, d] = dateStr.split("-").map(Number);
  const utc = new Date(Date.UTC(y, m - 1, d - daysBack));
  return utc.toISOString().slice(0, 10);
}

export async function getOrCreateVocabSettings(userId) {
  const existing = await VocabularySettings.findOne({ userId }).lean();
  if (existing) return existing;
  return await VocabularySettings.findOneAndUpdate(
    { userId },
    {
      $setOnInsert: {
        userId,
        dailyGoal: MAX_DAILY_NEW_WORDS,
        preferredLevel: "A1",
        preferredCategories: [],
        showBengaliMeaning: true,
        autoPronunciation: true,
        learningReminder: true,
        timezone: "Asia/Dhaka",
      },
    },
    { upsert: true, new: true, setDefaultsOnInsert: true }
  ).lean();
}

async function resolveCategoryIdsFilter(categoryId) {
  if (!categoryId) return null;
  const subCategories = await VocabularyCategory.find({ parentId: categoryId, isActive: true })
    .select("_id")
    .lean();
  return [categoryId, ...subCategories.map((c) => c._id)];
}

/**
 * Returns today's assigned new words for the user (capped strictly at min(5, dailyGoal)).
 * Concurrency-safe: even simultaneous parallel requests cannot assign > 5 words or duplicates.
 */
export async function getOrAssignDailyWords({ userId, level, categoryId, now = new Date() }) {
  const settings = await getOrCreateVocabSettings(userId);
  const timezone = validateTimezone(settings.timezone);
  const today = getLocalDateString(timezone, now);
  const goal = Math.min(MAX_DAILY_NEW_WORDS, Math.max(1, settings.dailyGoal || MAX_DAILY_NEW_WORDS));
  const targetLevel = level || settings.preferredLevel || "A1";

  let activity = await VocabularyDailyActivity.findOneAndUpdate(
    { userId, date: today },
    {
      $setOnInsert: {
        userId,
        date: today,
        timezone,
        dailyGoal: goal,
        newWords: 0,
        newWordIds: [],
        learnedWordIds: [],
      },
    },
    { upsert: true, new: true, setDefaultsOnInsert: true }
  ).lean();

  // Assign additional words only up to today's cap (never roll over missed days).
  let attempts = 0;
  while ((activity.newWordIds?.length || 0) < goal && attempts < 4) {
    attempts += 1;
    const currentIds = activity.newWordIds || [];
    const currentLen = currentIds.length;
    const needed = goal - currentLen;
    if (needed <= 0) break;

    const existingProgressIds = await UserVocabularyProgress.find({ userId })
      .distinct("vocabularyId");

    const excludeSet = [...existingProgressIds, ...currentIds];
    const catFilterIds = categoryId
      ? await resolveCategoryIdsFilter(categoryId)
      : settings.preferredCategories?.length
        ? settings.preferredCategories
        : null;

    // 1. Strict match: published + target level + optional category
    const baseQuery = {
      isPublished: true,
      _id: { $nin: excludeSet },
      level: targetLevel,
    };
    if (catFilterIds) {
      baseQuery.categoryId = { $in: catFilterIds };
    }

    let candidates = await Vocabulary.find(baseQuery)
      .sort({ sortOrder: 1, _id: 1 })
      .limit(needed)
      .select("_id")
      .lean();

    // 2. If user didn't filter by explicit category and preferredCategories ran out, expand within same level
    if (candidates.length < needed && !categoryId && catFilterIds) {
      const picked = candidates.map((c) => c._id);
      const more = await Vocabulary.find({
        isPublished: true,
        _id: { $nin: [...excludeSet, ...picked] },
        level: targetLevel,
      })
        .sort({ sortOrder: 1, _id: 1 })
        .limit(needed - candidates.length)
        .select("_id")
        .lean();
      candidates = [...candidates, ...more];
    }

    if (candidates.length === 0) break;

    const newCandidateIds = candidates.map((c) => c._id);
    // Atomic CAS guard on array length guarantees simultaneous requests cannot exceed dailyGoal.
    const updated = await VocabularyDailyActivity.findOneAndUpdate(
      {
        _id: activity._id,
        newWordIds: { $size: currentLen },
      },
      {
        $push: { newWordIds: { $each: newCandidateIds } },
        $set: {
          newWords: Math.min(goal, currentLen + newCandidateIds.length),
          timezone,
          dailyGoal: goal,
        },
      },
      { new: true }
    ).lean();

    if (updated) {
      activity = updated;
      break;
    }

    // Another concurrent request won the CAS; reload authoritative state.
    activity = await VocabularyDailyActivity.findOne({ _id: activity._id }).lean();
  }

  const assignedIds = (activity.newWordIds || []).slice(0, goal);
  const words = assignedIds.length
    ? await Vocabulary.find({ _id: { $in: assignedIds }, isPublished: true })
        .populate("categoryId", "name slug icon")
        .lean()
    : [];

  const wordById = new Map(words.map((w) => [String(w._id), w]));
  const progressList = assignedIds.length
    ? await UserVocabularyProgress.find({ userId, vocabularyId: { $in: assignedIds } }).lean()
    : [];
  const progressByWordId = new Map(progressList.map((p) => [String(p.vocabularyId), p]));

  const orderedWords = assignedIds
    .map((id) => wordById.get(String(id)))
    .filter(Boolean)
    .map((w) => {
      const p = progressByWordId.get(String(w._id));
      return {
        ...w,
        id: String(w._id),
        category: w.categoryId && typeof w.categoryId === "object"
          ? { id: String(w.categoryId._id), name: w.categoryId.name, slug: w.categoryId.slug, icon: w.categoryId.icon }
          : null,
        progress: p
          ? {
              status: p.status,
              isFavorite: p.isFavorite,
              correctCount: p.correctCount,
              wrongCount: p.wrongCount,
              reviewCount: p.reviewCount,
              intervalDays: p.intervalDays,
              nextReviewAt: p.nextReviewAt,
              learnedAt: p.learnedAt,
            }
          : null,
      };
    });

  return {
    date: today,
    timezone,
    dailyGoal: goal,
    newWordsCount: orderedWords.length,
    completedCount: (activity.learnedWordIds || []).length,
    isGoalCompleted: Boolean(activity.isGoalCompleted),
    learnedWordIds: (activity.learnedWordIds || []).map(String),
    words: orderedWords,
    settings,
  };
}
