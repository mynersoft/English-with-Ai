import mongoose from "mongoose";
import { AppError } from "@/lib/errors";
import { generateText } from "@/lib/aiProvider";
import {
  CEFR_LEVELS,
  DEFAULT_DAILY_NEW_WORDS,
  DEFAULT_TIMEZONE,
  calculateSrsSchedule,
  calculateStreak,
  getLocalDateString,
  normalizeDailyGoal,
  normalizeTimezone,
  shiftLocalDate,
} from "@/lib/vocabSrs";
import UserSettings from "@/models/UserSettings";
import VocabularyCategory from "@/models/VocabularyCategory";
import Vocabulary from "@/models/Vocabulary";
import UserVocabularyProgress from "@/models/UserVocabularyProgress";
import VocabularyDailyActivity from "@/models/VocabularyDailyActivity";
import VocabularyQuizAttempt from "@/models/VocabularyQuizAttempt";
import VocabularySpeakingSession from "@/models/VocabularySpeakingSession";

export function assertObjectId(id, label = "ID") {
  if (!id || !mongoose.Types.ObjectId.isValid(String(id))) {
    throw new AppError(400, "INVALID_ID", `Invalid ${label}.`);
  }
  return new mongoose.Types.ObjectId(String(id));
}

export async function getUserVocabSettings(userDoc) {
  const doc = await UserSettings.findOne({ user: userDoc._id })
    .select("vocabulary")
    .lean();
  const v = doc?.vocabulary || {};
  return {
    dailyGoal: normalizeDailyGoal(v.dailyGoal ?? DEFAULT_DAILY_NEW_WORDS),
    preferredLevel:
      v.preferredLevel === "ALL" || CEFR_LEVELS.includes(v.preferredLevel)
        ? v.preferredLevel
        : "A1",
    preferredCategoryIds: Array.isArray(v.preferredCategoryIds)
      ? v.preferredCategoryIds.map((id) => String(id))
      : [],
    autoPronounce: v.autoPronounce !== false,
    reminderEnabled: Boolean(v.reminderEnabled),
    reminderTime: typeof v.reminderTime === "string" && /^\d{2}:\d{2}$/.test(v.reminderTime)
      ? v.reminderTime
      : "20:00",
    timezone: normalizeTimezone(v.timezone || DEFAULT_TIMEZONE),
  };
}

export async function updateUserVocabSettings(userDoc, body = {}) {
  const current = await getUserVocabSettings(userDoc);
  const nextDailyGoal =
    body.dailyGoal !== undefined ? normalizeDailyGoal(body.dailyGoal) : current.dailyGoal;

  let nextLevel = current.preferredLevel;
  if (body.preferredLevel !== undefined) {
    const lvl = String(body.preferredLevel || "").trim().toUpperCase();
    if (lvl !== "ALL" && !CEFR_LEVELS.includes(lvl)) {
      throw new AppError(400, "INVALID_LEVEL", "preferredLevel must be ALL or A1..C2.");
    }
    nextLevel = lvl;
  }

  let nextCategoryIds = current.preferredCategoryIds;
  if (Array.isArray(body.preferredCategoryIds)) {
    nextCategoryIds = body.preferredCategoryIds
      .map((id) => String(id).trim())
      .filter((id) => mongoose.Types.ObjectId.isValid(id));
  }

  const nextAutoPronounce =
    body.autoPronounce !== undefined ? Boolean(body.autoPronounce) : current.autoPronounce;
  const nextReminderEnabled =
    body.reminderEnabled !== undefined ? Boolean(body.reminderEnabled) : current.reminderEnabled;

  let nextReminderTime = current.reminderTime;
  if (body.reminderTime !== undefined) {
    const t = String(body.reminderTime || "").trim();
    if (!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(t)) {
      throw new AppError(400, "INVALID_TIME", "reminderTime must be HH:mm (00:00-23:59).");
    }
    nextReminderTime = t;
  }

  let nextTimezone = current.timezone;
  if (body.timezone !== undefined) {
    nextTimezone = normalizeTimezone(body.timezone);
  }

  await UserSettings.findOneAndUpdate(
    { user: userDoc._id },
    {
      $set: {
        "vocabulary.dailyGoal": nextDailyGoal,
        "vocabulary.preferredLevel": nextLevel,
        "vocabulary.preferredCategoryIds": nextCategoryIds.map(
          (id) => new mongoose.Types.ObjectId(id)
        ),
        "vocabulary.autoPronounce": nextAutoPronounce,
        "vocabulary.reminderEnabled": nextReminderEnabled,
        "vocabulary.reminderTime": nextReminderTime,
        "vocabulary.timezone": nextTimezone,
      },
      $setOnInsert: { user: userDoc._id },
    },
    { upsert: true, new: true }
  );

  return {
    dailyGoal: nextDailyGoal,
    preferredLevel: nextLevel,
    preferredCategoryIds: nextCategoryIds,
    autoPronounce: nextAutoPronounce,
    reminderEnabled: nextReminderEnabled,
    reminderTime: nextReminderTime,
    timezone: nextTimezone,
  };
}

/** Expands selected category IDs to also include any active child subcategories. */
export async function expandCategoryIds(categoryIds = []) {
  const validIds = categoryIds
    .map((id) => String(id).trim())
    .filter((id) => mongoose.Types.ObjectId.isValid(id));
  if (validIds.length === 0) return [];
  const objIds = validIds.map((id) => new mongoose.Types.ObjectId(id));
  const children = await VocabularyCategory.find({
    parentId: { $in: objIds },
    isActive: true,
  })
    .select("_id")
    .lean();
  const merged = new Set(validIds);
  for (const c of children) merged.add(String(c._id));
  return Array.from(merged).map((id) => new mongoose.Types.ObjectId(id));
}

export async function ensureTodayActivity(userId, dailyGoal, todayStr) {
  const safeGoal = normalizeDailyGoal(dailyGoal);
  let doc = await VocabularyDailyActivity.findOne({ userId, date: todayStr }).lean();
  if (doc) {
    if (doc.dailyGoal !== safeGoal) {
      doc = await VocabularyDailyActivity.findOneAndUpdate(
        { userId, date: todayStr },
        {
          $set: {
            dailyGoal: safeGoal,
            isGoalCompleted: Number(doc.newWords || 0) >= safeGoal,
          },
        },
        { new: true }
      ).lean();
    }
    return doc;
  }

  try {
    doc = await VocabularyDailyActivity.findOneAndUpdate(
      { userId, date: todayStr },
      {
        $setOnInsert: {
          userId,
          date: todayStr,
          dailyGoal: safeGoal,
          newWords: 0,
          newWordIds: [],
          reviewedWords: 0,
          quizCount: 0,
          correctAnswers: 0,
          wrongAnswers: 0,
          speakingMinutes: 0,
          isGoalCompleted: false,
        },
      },
      { upsert: true, new: true, setDefaultsOnInsert: true }
    ).lean();
    return doc;
  } catch (err) {
    if (err?.code === 11000) {
      return VocabularyDailyActivity.findOne({ userId, date: todayStr }).lean();
    }
    throw err;
  }
}

function serializeWord(word, progress = null, categoryName = "") {
  return {
    id: String(word._id),
    word: word.word,
    slug: word.slug,
    meaning: word.meaning,
    pronunciation: word.pronunciation || "",
    audioUrl: word.audioUrl || null,
    imageUrl: word.imageUrl || null,
    partOfSpeech: word.partOfSpeech || "noun",
    level: word.level || "A1",
    categoryId: word.categoryId ? String(word.categoryId._id || word.categoryId) : "",
    categoryName: categoryName || word.categoryId?.name || "",
    examples: (word.examples || []).map((e) => ({ en: e.en, bn: e.bn })),
    synonyms: word.synonyms || [],
    antonyms: word.antonyms || [],
    tags: word.tags || [],
    difficulty: word.difficulty || 1,
    isPublished: word.isPublished !== false,
    progress: progress
      ? {
          status: progress.status || "new",
          isFavorite: Boolean(progress.isFavorite),
          correctCount: progress.correctCount || 0,
          wrongCount: progress.wrongCount || 0,
          reviewCount: progress.reviewCount || 0,
          intervalDays: progress.intervalDays || 0,
          easeFactor: progress.easeFactor || 2.5,
          repetition: progress.repetition || 0,
          lastQuality: progress.lastQuality ?? null,
          lastReviewedAt: progress.lastReviewedAt || null,
          nextReviewAt: progress.nextReviewAt || null,
          learnedAt: progress.learnedAt || null,
        }
      : {
          status: "new",
          isFavorite: false,
          correctCount: 0,
          wrongCount: 0,
          reviewCount: 0,
          intervalDays: 0,
          easeFactor: 2.5,
          repetition: 0,
          lastQuality: null,
          lastReviewedAt: null,
          nextReviewAt: null,
          learnedAt: null,
        },
  };
}

/**
 * Atomically assigns up to `cap` (max 5) daily new words for the user's LOCAL day.
 * Concurrent calls cannot exceed the daily cap or assign duplicates.
 */
export async function getOrAssignDailyWords({ userId, settings, now = new Date() }) {
  const cap = normalizeDailyGoal(settings.dailyGoal);
  const todayStr = getLocalDateString(now, settings.timezone);

  let activity = await ensureTodayActivity(userId, cap, todayStr);
  let assignedIds = (activity.newWordIds || []).map((id) => String(id));

  if (assignedIds.length < cap) {
    const learnedRows = await UserVocabularyProgress.find({
      userId,
      learnedAt: { $ne: null },
    })
      .select("vocabularyId")
      .lean();

    const excludeIds = new Set([
      ...assignedIds,
      ...learnedRows.map((r) => String(r.vocabularyId)),
    ]);

    const query = {
      isPublished: true,
      _id: {
        $nin: Array.from(excludeIds)
          .filter((id) => mongoose.Types.ObjectId.isValid(id))
          .map((id) => new mongoose.Types.ObjectId(id)),
      },
    };
    if (settings.preferredLevel && settings.preferredLevel !== "ALL") {
      query.level = settings.preferredLevel;
    }
    const expandedCats = await expandCategoryIds(settings.preferredCategoryIds);
    if (expandedCats.length > 0) {
      query.categoryId = { $in: expandedCats };
    }

    const needed = cap - assignedIds.length;
    let candidates = await Vocabulary.find(query)
      .select("_id")
      .sort({ sortOrder: 1, _id: 1 })
      .limit(needed * 2)
      .lean();

    // If strict filter has fewer remaining words than needed, widen level/category while still excluding learned/unpublished.
    if (candidates.length < needed && (query.level || query.categoryId)) {
      const seenCandidateIds = new Set(candidates.map((c) => String(c._id)));
      for (const id of excludeIds) seenCandidateIds.add(id);
      const fallbackCandidates = await Vocabulary.find({
        isPublished: true,
        _id: {
          $nin: Array.from(seenCandidateIds)
            .filter((id) => mongoose.Types.ObjectId.isValid(id))
            .map((id) => new mongoose.Types.ObjectId(id)),
        },
      })
        .select("_id")
        .sort({ sortOrder: 1, _id: 1 })
        .limit(needed)
        .lean();
      candidates = [...candidates, ...fallbackCandidates];
    }

    const capGuardKey = `newWordIds.${cap - 1}`;
    for (const c of candidates) {
      if (assignedIds.length >= cap) break;
      const updated = await VocabularyDailyActivity.findOneAndUpdate(
        {
          userId,
          date: todayStr,
          newWordIds: { $ne: c._id },
          [capGuardKey]: { $exists: false },
        },
        { $push: { newWordIds: c._id } },
        { new: true }
      ).lean();

      if (!updated) {
        // Re-read in case a concurrent request filled the cap
        activity = await VocabularyDailyActivity.findOne({ userId, date: todayStr }).lean();
        assignedIds = (activity?.newWordIds || []).map((id) => String(id));
        if (assignedIds.length >= cap) break;
      } else {
        activity = updated;
        assignedIds = (updated.newWordIds || []).map((id) => String(id));
      }
    }
  }

  const objIds = assignedIds
    .slice(0, cap)
    .filter((id) => mongoose.Types.ObjectId.isValid(id))
    .map((id) => new mongoose.Types.ObjectId(id));

  if (objIds.length === 0) {
    return {
      date: todayStr,
      timezone: settings.timezone,
      dailyGoal: cap,
      newWordsDone: activity?.newWords || 0,
      isGoalCompleted: Boolean(activity?.isGoalCompleted),
      words: [],
    };
  }

  const [words, progressRows] = await Promise.all([
    Vocabulary.find({ _id: { $in: objIds }, isPublished: true })
      .populate("categoryId", "name")
      .lean(),
    UserVocabularyProgress.find({ userId, vocabularyId: { $in: objIds } }).lean(),
  ]);

  const wordById = new Map(words.map((w) => [String(w._id), w]));
  const progById = new Map(progressRows.map((p) => [String(p.vocabularyId), p]));

  const orderedWords = objIds
    .map((id) => wordById.get(String(id)))
    .filter(Boolean)
    .map((w) => serializeWord(w, progById.get(String(w._id))));

  return {
    date: todayStr,
    timezone: settings.timezone,
    dailyGoal: cap,
    newWordsDone: Math.min(cap, activity?.newWords || 0),
    isGoalCompleted: Boolean(activity?.isGoalCompleted),
    words: orderedWords,
  };
}

/**
 * Records learning a word ("I Know" -> knowsWord=true / quality=4, or "Don't Know" -> quality=1).
 * Atomically enforces the daily cap (<= 5 new words per local day) under concurrency.
 */
export async function recordWordLearned({ userId, vocabularyId, knowsWord, settings, now = new Date() }) {
  const vocabObjId = assertObjectId(vocabularyId, "vocabularyId");
  const word = await Vocabulary.findOne({ _id: vocabObjId, isPublished: true }).lean();
  if (!word) {
    throw new AppError(404, "WORD_NOT_FOUND", "Vocabulary word not found.");
  }

  const cap = normalizeDailyGoal(settings.dailyGoal);
  const todayStr = getLocalDateString(now, settings.timezone);
  const quality = knowsWord ? 4 : 1;

  const existing = await UserVocabularyProgress.findOne({ userId, vocabularyId: vocabObjId }).lean();
  const alreadyLearned = Boolean(existing?.learnedAt);

  let activity = await ensureTodayActivity(userId, cap, todayStr);

  if (!alreadyLearned) {
    // Atomically increment newWords ONLY if still strictly below cap.
    const capGuardKey = `newWordIds.${cap - 1}`;
    let claimed = await VocabularyDailyActivity.findOneAndUpdate(
      {
        userId,
        date: todayStr,
        newWords: { $lt: cap },
        newWordIds: vocabObjId,
      },
      { $inc: { newWords: 1 } },
      { new: true }
    ).lean();

    if (!claimed) {
      // Word wasn't in pre-assigned newWordIds; allow slot if newWords < cap
      claimed = await VocabularyDailyActivity.findOneAndUpdate(
        {
          userId,
          date: todayStr,
          newWords: { $lt: cap },
          newWordIds: { $ne: vocabObjId },
          [capGuardKey]: { $exists: false },
        },
        {
          $inc: { newWords: 1 },
          $push: { newWordIds: vocabObjId },
        },
        { new: true }
      ).lean();
    }

    if (!claimed) {
      // One last check if user already hit cap while word wasn't in pre-assigned array
      const fallbackClaim = await VocabularyDailyActivity.findOneAndUpdate(
        {
          userId,
          date: todayStr,
          newWords: { $lt: cap },
        },
        { $inc: { newWords: 1 } },
        { new: true }
      ).lean();
      if (!fallbackClaim) {
        throw new AppError(
          409,
          "DAILY_LIMIT_REACHED",
          `Daily limit of ${cap} new words reached for today. Review or take a quiz!`
        );
      }
      claimed = fallbackClaim;
    }

    activity = claimed;
    if (claimed.newWords >= cap && !claimed.isGoalCompleted) {
      activity = await VocabularyDailyActivity.findOneAndUpdate(
        { userId, date: todayStr },
        { $set: { isGoalCompleted: true } },
        { new: true }
      ).lean();
    }
  }

  const srs = calculateSrsSchedule(existing || {}, quality, now);
  const progress = await UserVocabularyProgress.findOneAndUpdate(
    { userId, vocabularyId: vocabObjId },
    {
      $setOnInsert: {
        userId,
        vocabularyId: vocabObjId,
        learnedAt: now,
      },
      $set: {
        status: srs.status,
        intervalDays: srs.intervalDays,
        easeFactor: srs.easeFactor,
        repetition: srs.repetition,
        lastQuality: srs.lastQuality,
        lastReviewedAt: srs.lastReviewedAt,
        nextReviewAt: srs.nextReviewAt,
      },
      $inc: {
        reviewCount: 1,
        correctCount: srs.isCorrect ? 1 : 0,
        wrongCount: srs.isCorrect ? 0 : 1,
      },
    },
    { upsert: true, new: true, setDefaultsOnInsert: true }
  ).lean();

  if (!progress.learnedAt) {
    await UserVocabularyProgress.updateOne(
      { _id: progress._id, learnedAt: null },
      { $set: { learnedAt: now } }
    );
    progress.learnedAt = now;
  }

  return {
    progress: serializeWord(word, progress).progress,
    daily: {
      date: todayStr,
      dailyGoal: cap,
      newWordsDone: Math.min(cap, activity?.newWords || 0),
      isGoalCompleted: Boolean(activity?.isGoalCompleted),
    },
  };
}

export async function recordWordReview({ userId, vocabularyId, quality, settings, now = new Date() }) {
  const q = Number(quality);
  if (!Number.isFinite(q) || q < 0 || q > 5) {
    throw new AppError(400, "INVALID_QUALITY", "quality must be between 0 and 5.");
  }
  const vocabObjId = assertObjectId(vocabularyId, "vocabularyId");
  const word = await Vocabulary.findOne({ _id: vocabObjId, isPublished: true }).lean();
  if (!word) {
    throw new AppError(404, "WORD_NOT_FOUND", "Vocabulary word not found.");
  }

  const existing = await UserVocabularyProgress.findOne({ userId, vocabularyId: vocabObjId }).lean();
  const srs = calculateSrsSchedule(existing || {}, q, now);

  const cap = normalizeDailyGoal(settings.dailyGoal);
  const todayStr = getLocalDateString(now, settings.timezone);
  await ensureTodayActivity(userId, cap, todayStr);

  const [progress] = await Promise.all([
    UserVocabularyProgress.findOneAndUpdate(
      { userId, vocabularyId: vocabObjId },
      {
        $setOnInsert: {
          userId,
          vocabularyId: vocabObjId,
          learnedAt: now,
        },
        $set: {
          status: srs.status,
          intervalDays: srs.intervalDays,
          easeFactor: srs.easeFactor,
          repetition: srs.repetition,
          lastQuality: srs.lastQuality,
          lastReviewedAt: srs.lastReviewedAt,
          nextReviewAt: srs.nextReviewAt,
        },
        $inc: {
          reviewCount: 1,
          correctCount: srs.isCorrect ? 1 : 0,
          wrongCount: srs.isCorrect ? 0 : 1,
        },
      },
      { upsert: true, new: true, setDefaultsOnInsert: true }
    ).lean(),
    VocabularyDailyActivity.updateOne(
      { userId, date: todayStr },
      {
        $inc: {
          reviewedWords: 1,
          correctAnswers: srs.isCorrect ? 1 : 0,
          wrongAnswers: srs.isCorrect ? 0 : 1,
        },
      }
    ),
  ]);

  return {
    vocabularyId: String(vocabObjId),
    progress: serializeWord(word, progress).progress,
  };
}

export async function getReviewLists({ userId, tab = "due", page = 1, limit = 30, now = new Date() }) {
  const safePage = Math.max(1, Number(page) || 1);
  const safeLimit = Math.max(1, Math.min(50, Number(limit) || 30));
  const skip = (safePage - 1) * safeLimit;

  const filter = { userId };
  let sort = { nextReviewAt: 1, updatedAt: -1 };

  if (tab === "due") {
    filter.learnedAt = { $ne: null };
    filter.status = { $ne: "mastered" };
    filter.$or = [{ nextReviewAt: { $lte: now } }, { nextReviewAt: null }];
    sort = { nextReviewAt: 1, lastReviewedAt: 1 };
  } else if (tab === "difficult") {
    filter.$or = [{ wrongCount: { $gt: 0 } }, { easeFactor: { $lt: 2.1 } }, { lastQuality: { $lte: 2 } }];
    sort = { wrongCount: -1, easeFactor: 1 };
  } else if (tab === "learning") {
    filter.status = "learning";
    sort = { updatedAt: -1 };
  } else if (tab === "mastered") {
    filter.status = "mastered";
    sort = { updatedAt: -1 };
  }

  const [rows, total, dueCount, difficultCount, learningCount, masteredCount] = await Promise.all([
    UserVocabularyProgress.find(filter)
      .sort(sort)
      .skip(skip)
      .limit(safeLimit)
      .populate({
        path: "vocabularyId",
        match: { isPublished: true },
        populate: { path: "categoryId", select: "name" },
      })
      .lean(),
    UserVocabularyProgress.countDocuments(filter),
    UserVocabularyProgress.countDocuments({
      userId,
      learnedAt: { $ne: null },
      status: { $ne: "mastered" },
      $or: [{ nextReviewAt: { $lte: now } }, { nextReviewAt: null }],
    }),
    UserVocabularyProgress.countDocuments({
      userId,
      $or: [{ wrongCount: { $gt: 0 } }, { easeFactor: { $lt: 2.1 } }, { lastQuality: { $lte: 2 } }],
    }),
    UserVocabularyProgress.countDocuments({ userId, status: "learning" }),
    UserVocabularyProgress.countDocuments({ userId, status: "mastered" }),
  ]);

  const items = rows
    .filter((r) => Boolean(r.vocabularyId))
    .map((r) => serializeWord(r.vocabularyId, r));

  return {
    tab,
    page: safePage,
    limit: safeLimit,
    total,
    counts: {
      due: dueCount,
      difficult: difficultCount,
      learning: learningCount,
      mastered: masteredCount,
    },
    items,
  };
}

export async function buildDashboardData({ userId, userDoc, settings, now = new Date() }) {
  const cap = normalizeDailyGoal(settings.dailyGoal);
  const todayStr = getLocalDateString(now, settings.timezone);
  const sixtyDaysAgoStr = shiftLocalDate(todayStr, -60);

  const [
    todayActivity,
    recentActivities,
    totalLearned,
    masteredCount,
    dueReviews,
    categories,
    categoryCounts,
    recentQuizzes,
    recentSpeaking,
  ] = await Promise.all([
    ensureTodayActivity(userId, cap, todayStr),
    VocabularyDailyActivity.find({
      userId,
      date: { $gte: sixtyDaysAgoStr, $lte: todayStr },
    })
      .sort({ date: -1 })
      .lean(),
    UserVocabularyProgress.countDocuments({ userId, learnedAt: { $ne: null } }),
    UserVocabularyProgress.countDocuments({ userId, status: "mastered" }),
    UserVocabularyProgress.countDocuments({
      userId,
      learnedAt: { $ne: null },
      status: { $ne: "mastered" },
      $or: [{ nextReviewAt: { $lte: now } }, { nextReviewAt: null }],
    }),
    VocabularyCategory.find({ isActive: true, parentId: null })
      .sort({ sortOrder: 1, name: 1 })
      .limit(16)
      .lean(),
    Vocabulary.aggregate([
      { $match: { isPublished: true } },
      { $group: { _id: "$categoryId", count: { $sum: 1 } } },
    ]),
    VocabularyQuizAttempt.find({ userId }).sort({ completedAt: -1 }).limit(3).lean(),
    VocabularySpeakingSession.find({ userId }).sort({ createdAt: -1 }).limit(3).lean(),
  ]);

  const countMap = new Map(categoryCounts.map((c) => [String(c._id), c.count]));
  const streak = calculateStreak(recentActivities, todayStr);

  const recentActivityFeed = [
    ...recentQuizzes.map((q) => ({
      type: "quiz",
      title: `Quiz (${q.quizType.replace(/_/g, " ")})`,
      subtitle: `${q.correctAnswers}/${q.totalQuestions} correct · ${q.score}%`,
      timestamp: q.completedAt,
    })),
    ...recentSpeaking.map((s) => ({
      type: "speaking",
      title: "Speaking Practice",
      subtitle: `Grammar ${s.grammarScore}% · Fluency ${s.fluencyScore}%`,
      timestamp: s.createdAt,
    })),
  ]
    .sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp))
    .slice(0, 5);

  return {
    greetingName: userDoc?.name || "Learner",
    date: todayStr,
    timezone: settings.timezone,
    dailyGoal: cap,
    newWordsDone: Math.min(cap, todayActivity?.newWords || 0),
    isGoalCompleted: Boolean(todayActivity?.isGoalCompleted),
    reviewedToday: todayActivity?.reviewedWords || 0,
    dueReviews,
    totalLearned,
    masteredCount,
    streak,
    categories: categories.map((c) => ({
      id: String(c._id),
      name: c.name,
      slug: c.slug,
      description: c.description || "",
      icon: c.icon || "menu_book",
      wordCount: countMap.get(String(c._id)) || 0,
    })),
    recentActivity: recentActivityFeed,
  };
}

function shuffleArray(arr) {
  const copy = [...arr];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

/**
 * Generates randomized, non-repeating quiz questions with correct answers hidden
 * until POST /api/vocabulary/quiz submission.
 */
export async function generateQuizSession({
  userId,
  quizType = "mixed",
  count = 5,
  level = "ALL",
  categoryId = "",
}) {
  const desiredCount = Math.max(1, Math.min(10, Number(count) || 5));

  const query = { isPublished: true };
  if (level && level !== "ALL" && CEFR_LEVELS.includes(level.toUpperCase())) {
    query.level = level.toUpperCase();
  }
  if (categoryId && mongoose.Types.ObjectId.isValid(categoryId)) {
    const expanded = await expandCategoryIds([categoryId]);
    query.categoryId = { $in: expanded };
  }

  // Prefer user's learned/assigned words first, then fill with published pool for distractors & variety
  const [learnedProgress, pool] = await Promise.all([
    UserVocabularyProgress.find({ userId }).select("vocabularyId").limit(50).lean(),
    Vocabulary.find(query).limit(120).lean(),
  ]);

  if (pool.length < 4) {
    throw new AppError(
      400,
      "NOT_ENOUGH_WORDS",
      "At least 4 published vocabulary words are needed to start a quiz."
    );
  }

  const learnedIds = new Set(learnedProgress.map((p) => String(p.vocabularyId)));
  const learnedWords = shuffleArray(pool.filter((w) => learnedIds.has(String(w._id))));
  const unlearnedWords = shuffleArray(pool.filter((w) => !learnedIds.has(String(w._id))));
  const orderedUnique = [...learnedWords, ...unlearnedWords];

  // Ensure no duplicate vocabulary word in one quiz
  const selected = [];
  const usedIds = new Set();
  for (const w of orderedUnique) {
    const wid = String(w._id);
    if (usedIds.has(wid)) continue;
    usedIds.add(wid);
    selected.push(w);
    if (selected.length >= desiredCount) break;
  }

  const allowedTypes =
    quizType === "mixed"
      ? ["en_to_bn", "bn_to_en", "fill_blank", "listening"]
      : [quizType];

  if (quizType === "matching") {
    const matchSlice = selected.slice(0, Math.min(5, selected.length));
    return {
      quizType: "matching",
      questions: [
        {
          questionType: "matching",
          pairsCount: matchSlice.length,
          leftItems: shuffleArray(
            matchSlice.map((w) => ({ vocabularyId: String(w._id), word: w.word, pronunciation: w.pronunciation || "" }))
          ),
          rightItems: shuffleArray(
            matchSlice.map((w) => ({ optionId: String(w._id), meaning: w.meaning }))
          ),
        },
      ],
    };
  }

  const questions = selected.map((w, idx) => {
    const qType = allowedTypes[idx % allowedTypes.length];
    const distractors = shuffleArray(pool.filter((other) => String(other._id) !== String(w._id))).slice(0, 3);

    if (qType === "bn_to_en") {
      const options = shuffleArray([w.word, ...distractors.map((d) => d.word)]);
      return {
        vocabularyId: String(w._id),
        questionType: "bn_to_en",
        prompt: w.meaning,
        hint: w.partOfSpeech || "",
        audioUrl: null,
        options,
      };
    }

    if (qType === "fill_blank") {
      const ex = (w.examples || []).find((e) =>
        e.en.toLowerCase().includes(w.word.toLowerCase())
      ) || w.examples?.[0];
      const regex = new RegExp(`\\b${w.word.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\b`, "i");
      const maskedSentence = ex
        ? ex.en.replace(regex, "_____")
        : `Choose the right word for: ${w.meaning} (_____)`;
      const options = shuffleArray([w.word, ...distractors.map((d) => d.word)]);
      return {
        vocabularyId: String(w._id),
        questionType: "fill_blank",
        prompt: maskedSentence,
        hint: ex?.bn || w.meaning,
        audioUrl: null,
        options,
      };
    }

    if (qType === "listening") {
      const options = shuffleArray([w.word, ...distractors.map((d) => d.word)]);
      return {
        vocabularyId: String(w._id),
        questionType: "listening",
        prompt: w.word, // Used strictly by client audio/TTS player, hidden in UI until submitted
        hint: w.meaning,
        audioUrl: w.audioUrl || null,
        options,
      };
    }

    // default en_to_bn
    const options = shuffleArray([w.meaning, ...distractors.map((d) => d.meaning)]);
    return {
      vocabularyId: String(w._id),
      questionType: "en_to_bn",
      prompt: w.word,
      hint: w.pronunciation || w.partOfSpeech || "",
      audioUrl: w.audioUrl || null,
      options,
    };
  });

  return {
    quizType,
    questions,
  };
}

/**
 * Evaluates submitted quiz answers against real DB documents, updates SRS word progress,
 * increments daily activity, and saves VocabularyQuizAttempt.
 */
export async function submitQuizAttempt({ userId, body, settings, now = new Date() }) {
  const quizType = String(body?.quizType || "mixed").trim();
  const timeTaken = Math.max(0, Math.round(Number(body?.timeTaken || 0)));
  const answers = Array.isArray(body?.answers) ? body.answers : [];
  if (answers.length === 0 || answers.length > 20) {
    throw new AppError(400, "INVALID_ANSWERS", "Provide between 1 and 20 quiz answers.");
  }

  const uniqueIds = Array.from(
    new Set(
      answers
        .map((a) => String(a?.vocabularyId || "").trim())
        .filter((id) => mongoose.Types.ObjectId.isValid(id))
    )
  );
  if (uniqueIds.length === 0) {
    throw new AppError(400, "INVALID_ANSWERS", "Valid vocabularyIds are required.");
  }

  const objIds = uniqueIds.map((id) => new mongoose.Types.ObjectId(id));
  const [words, existingProgressList] = await Promise.all([
    Vocabulary.find({ _id: { $in: objIds }, isPublished: true }).lean(),
    UserVocabularyProgress.find({ userId, vocabularyId: { $in: objIds } }).lean(),
  ]);

  const wordById = new Map(words.map((w) => [String(w._id), w]));
  const progressById = new Map(existingProgressList.map((p) => [String(p.vocabularyId), p]));

  const evaluated = [];
  for (const ans of answers) {
    const vid = String(ans?.vocabularyId || "").trim();
    const w = wordById.get(vid);
    if (!w) continue;
    const qType = String(ans?.questionType || quizType);
    const selected = String(ans?.selectedAnswer || "").trim();
    const expected = qType === "en_to_bn" || qType === "matching" ? w.meaning : w.word;
    const isCorrect = selected.toLowerCase() === expected.trim().toLowerCase();
    evaluated.push({
      vocabularyId: vid,
      questionType: qType,
      word: w.word,
      meaning: w.meaning,
      selectedAnswer: selected,
      correctAnswer: expected,
      isCorrect,
    });
  }

  if (evaluated.length === 0) {
    throw new AppError(400, "INVALID_ANSWERS", "No matching vocabulary words found.");
  }

  const totalQuestions = evaluated.length;
  const correctAnswers = evaluated.filter((e) => e.isCorrect).length;
  const wrongAnswers = totalQuestions - correctAnswers;
  const score = Math.round((correctAnswers / totalQuestions) * 100);

  const cap = normalizeDailyGoal(settings.dailyGoal);
  const todayStr = getLocalDateString(now, settings.timezone);
  await ensureTodayActivity(userId, cap, todayStr);

  // Update each word's SRS progress + save quiz attempt + update daily stats concurrently
  const progressOps = evaluated.map((ev) => {
    const existing = progressById.get(ev.vocabularyId) || {};
    const q = ev.isCorrect ? 4 : 1;
    const srs = calculateSrsSchedule(existing, q, now);
    const vidObj = new mongoose.Types.ObjectId(ev.vocabularyId);
    return UserVocabularyProgress.updateOne(
      { userId, vocabularyId: vidObj },
      {
        $setOnInsert: { userId, vocabularyId: vidObj, learnedAt: now },
        $set: {
          status: srs.status,
          intervalDays: srs.intervalDays,
          easeFactor: srs.easeFactor,
          repetition: srs.repetition,
          lastQuality: srs.lastQuality,
          lastReviewedAt: srs.lastReviewedAt,
          nextReviewAt: srs.nextReviewAt,
        },
        $inc: {
          reviewCount: 1,
          correctCount: ev.isCorrect ? 1 : 0,
          wrongCount: ev.isCorrect ? 0 : 1,
        },
      },
      { upsert: true }
    );
  });

  const [attempt] = await Promise.all([
    VocabularyQuizAttempt.create({
      userId,
      quizType,
      vocabularyIds: objIds,
      totalQuestions,
      correctAnswers,
      wrongAnswers,
      score,
      timeTaken,
      completedAt: now,
    }),
    VocabularyDailyActivity.updateOne(
      { userId, date: todayStr },
      {
        $inc: {
          quizCount: 1,
          reviewedWords: totalQuestions,
          correctAnswers,
          wrongAnswers,
        },
      }
    ),
    ...progressOps,
  ]);

  return {
    attemptId: String(attempt._id),
    quizType,
    totalQuestions,
    correctAnswers,
    wrongAnswers,
    score,
    timeTaken,
    results: evaluated,
  };
}

/**
 * Evaluates a user's sentence using the target word with server AI if configured.
 * If AI_API_KEY is not configured, clearly returns `aiConfigured: false` with a
 * deterministic basic check (no faked AI output).
 */
export async function evaluateSentenceWithAi({ wordObj, sentence }) {
  const trimmed = String(sentence || "").trim();
  const containsWord = trimmed.toLowerCase().includes(wordObj.word.toLowerCase());

  if (!process.env.AI_API_KEY) {
    return {
      aiConfigured: false,
      isValidBasic: trimmed.length >= 3 && containsWord,
      containsWord,
      correctedSentence: "",
      feedbackBn: containsWord
        ? "AI মূল্যায়ন কনফিগার করা নেই। বেসিক চেক: আপনার বাক্যে লক্ষ্য শব্দটি সঠিকভাবে ব্যবহৃত হয়েছে।"
        : `AI মূল্যায়ন কনফিগার করা নেই। বেসিক চেক: বাক্যে "${wordObj.word}" শব্দটি ব্যবহার করুন।`,
    };
  }

  const system = `You are a friendly English teacher for Bengali-speaking beginners.
Evaluate the student's English sentence using the target word "${wordObj.word}" (Bengali meaning: "${wordObj.meaning}").
Return ONLY valid JSON:
{
  "isCorrect": true or false,
  "containsWord": true or false,
  "correctedSentence": "natural corrected English sentence",
  "feedbackBn": "short, encouraging explanation in simple Bangla (2-3 sentences)"
}`;

  const raw = await generateText({
    system,
    messages: [{ role: "user", content: trimmed }],
    json: true,
    maxTokens: 300,
    temperature: 0.3,
  });

  const cleaned = String(raw || "").replace(/```json|```/gi, "").trim();
  const start = cleaned.indexOf("{");
  const end = cleaned.lastIndexOf("}");
  let parsed = null;
  if (start !== -1 && end !== -1) {
    try {
      parsed = JSON.parse(cleaned.slice(start, end + 1));
    } catch {
      parsed = null;
    }
  }

  if (!parsed) {
    return {
      aiConfigured: true,
      isValidBasic: containsWord,
      containsWord,
      isCorrect: containsWord,
      correctedSentence: trimmed,
      feedbackBn: String(raw || "").trim() || "আপনার বাক্যটি মূল্যায়ন করা হয়েছে।",
    };
  }

  return {
    aiConfigured: true,
    isValidBasic: containsWord,
    containsWord: parsed.containsWord !== undefined ? Boolean(parsed.containsWord) : containsWord,
    isCorrect: Boolean(parsed.isCorrect),
    correctedSentence: String(parsed.correctedSentence || trimmed).trim(),
    feedbackBn: String(parsed.feedbackBn || "").trim(),
  };
}

/**
 * Evaluates a speaking practice transcript using server AI if configured,
 * saves VocabularySpeakingSession, and returns scores + feedback.
 * If AI_API_KEY is missing, throws 503 AI_NOT_CONFIGURED (never fakes AI feedback).
 */
export async function evaluateSpeakingPractice({
  userId,
  transcript,
  duration = 0,
  vocabularyIds = [],
  settings,
  now = new Date(),
}) {
  const cleanTranscript = String(transcript || "").trim();
  if (!cleanTranscript) {
    throw new AppError(400, "TRANSCRIPT_REQUIRED", "Speaking transcript cannot be empty.");
  }

  if (!process.env.AI_API_KEY) {
    throw new AppError(
      503,
      "AI_NOT_CONFIGURED",
      "AI speaking evaluation is not configured on the server yet (missing AI_API_KEY)."
    );
  }

  const validObjIds = vocabularyIds
    .map((id) => String(id).trim())
    .filter((id) => mongoose.Types.ObjectId.isValid(id))
    .map((id) => new mongoose.Types.ObjectId(id));

  const targetWords = validObjIds.length
    ? await Vocabulary.find({ _id: { $in: validObjIds } }).select("word meaning").lean()
    : [];

  const wordsPrompt = targetWords.length
    ? targetWords.map((w) => `${w.word} (${w.meaning})`).join(", ")
    : "general daily English";

  const system = `You are "English AI", an encouraging spoken-English coach for Bengali learners.
Evaluate the student's spoken transcript. Target words to practice: ${wordsPrompt}.
Ignore missing punctuation from speech-to-text.
Return ONLY valid JSON:
{
  "grammarScore": 0-100 integer,
  "vocabularyScore": 0-100 integer,
  "fluencyScore": 0-100 integer,
  "feedback": "clear English & simple Bangla tips (3-4 sentences max) covering grammar, target word usage, and fluency"
}`;

  const raw = await generateText({
    system,
    messages: [{ role: "user", content: cleanTranscript }],
    json: true,
    maxTokens: 450,
    temperature: 0.4,
  });

  const cleaned = String(raw || "").replace(/```json|```/gi, "").trim();
  const start = cleaned.indexOf("{");
  const end = cleaned.lastIndexOf("}");
  let parsed = null;
  if (start !== -1 && end !== -1) {
    try {
      parsed = JSON.parse(cleaned.slice(start, end + 1));
    } catch {
      parsed = null;
    }
  }

  const clampScore = (n, fallback = 70) => {
    const num = Number(n);
    if (!Number.isFinite(num)) return fallback;
    return Math.max(0, Math.min(100, Math.round(num)));
  };

  const grammarScore = clampScore(parsed?.grammarScore, 70);
  const vocabularyScore = clampScore(parsed?.vocabularyScore, 70);
  const fluencyScore = clampScore(parsed?.fluencyScore, 70);
  const feedback = String(parsed?.feedback || raw || "Good speaking practice!").trim();

  const cap = normalizeDailyGoal(settings.dailyGoal);
  const todayStr = getLocalDateString(now, settings.timezone);
  const addedMinutes = Math.max(1, Math.round(Math.max(0, Number(duration || 0)) / 60));
  await ensureTodayActivity(userId, cap, todayStr);

  const [session] = await Promise.all([
    VocabularySpeakingSession.create({
      userId,
      vocabularyIds: validObjIds,
      transcript: cleanTranscript,
      duration: Math.max(0, Math.round(Number(duration || 0))),
      grammarScore,
      vocabularyScore,
      fluencyScore,
      feedback,
    }),
    VocabularyDailyActivity.updateOne(
      { userId, date: todayStr },
      { $inc: { speakingMinutes: addedMinutes } }
    ),
  ]);

  return {
    id: String(session._id),
    transcript: session.transcript,
    duration: session.duration,
    grammarScore: session.grammarScore,
    vocabularyScore: session.vocabularyScore,
    fluencyScore: session.fluencyScore,
    feedback: session.feedback,
    createdAt: session.createdAt,
  };
}
