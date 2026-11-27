import mongoose from "mongoose";
import { AppError } from "@/lib/errors";
import { DEFAULT_TIMEZONE, getLocalDateString, normalizeTimezone, shiftLocalDate } from "@/lib/vocabSrs";
import { buildWeekWindow, computeStreaksFromActiveDates } from "@/lib/studentStats";
import {
  READING_CEFR_LEVELS,
  buildEnglishWordCandidates,
  evaluateComprehensionAnswers,
  isValidOptionalAudioUrl,
  normalizeAccent,
  normalizeReadingGoalMinutes,
  normalizeSpeed,
  slugifyReadingText,
  splitTextIntoSentences,
} from "@/lib/readingUtils";
import User from "@/models/User";
import UserSettings from "@/models/UserSettings";
import ReadingCategory from "@/models/ReadingCategory";
import ReadingPassage from "@/models/ReadingPassage";
import UserReadingProgress from "@/models/UserReadingProgress";
import ReadingAttempt from "@/models/ReadingAttempt";
import ReadingDailyActivity from "@/models/ReadingDailyActivity";
import Vocabulary from "@/models/Vocabulary";
import VocabularyCategory from "@/models/VocabularyCategory";
import UserVocabularyProgress from "@/models/UserVocabularyProgress";

export function assertReadingObjectId(id, label = "ID") {
  if (!id || !mongoose.Types.ObjectId.isValid(String(id))) {
    throw new AppError(400, "INVALID_ID", `Invalid ${label}.`);
  }
  return new mongoose.Types.ObjectId(String(id));
}

function mapCefrFromLearningLevel(learningLevel) {
  const raw = String(learningLevel || "").toLowerCase();
  if (raw === "intermediate") return "B1";
  if (raw === "advanced") return "C1";
  if (raw === "beginner") return "A1";
  return "ALL";
}

export async function getUserReadingSettings(userDoc) {
  const settingsDoc = await UserSettings.findOne({ user: userDoc._id })
    .select("reading vocabulary explanationLanguage notifications")
    .lean();
  const r = settingsDoc?.reading || {};
  const v = settingsDoc?.vocabulary || {};

  const fallbackLevel = v.preferredLevel || mapCefrFromLearningLevel(userDoc.learningLevel);
  const preferredLevel =
    r.preferredLevel === "ALL" || READING_CEFR_LEVELS.includes(r.preferredLevel)
      ? r.preferredLevel
      : fallbackLevel === "ALL" || READING_CEFR_LEVELS.includes(fallbackLevel)
      ? fallbackLevel
      : "ALL";

  const dailyGoalMinutes = normalizeReadingGoalMinutes(
    r.dailyGoalMinutes ?? userDoc.dailyGoalMinutes ?? 15
  );

  const showBengali =
    typeof r.showBengali === "boolean"
      ? r.showBengali
      : settingsDoc?.explanationLanguage !== "en";

  const reminderEnabled =
    typeof r.reminderEnabled === "boolean"
      ? r.reminderEnabled
      : Boolean(v.reminderEnabled || settingsDoc?.notifications?.lessonReminder);

  const reminderTime =
    typeof r.reminderTime === "string" && /^\d{2}:\d{2}$/.test(r.reminderTime)
      ? r.reminderTime
      : typeof v.reminderTime === "string" && /^\d{2}:\d{2}$/.test(v.reminderTime)
      ? v.reminderTime
      : "20:00";

  return {
    preferredLevel,
    accent: normalizeAccent(r.accent),
    voiceName: String(r.voiceName || ""),
    defaultSpeed: normalizeSpeed(r.defaultSpeed),
    showBengali,
    dailyGoalMinutes,
    reminderEnabled,
    reminderTime,
    timezone: normalizeTimezone(v.timezone || DEFAULT_TIMEZONE),
  };
}

export async function updateUserReadingSettings(userDoc, body = {}) {
  const current = await getUserReadingSettings(userDoc);

  let nextLevel = current.preferredLevel;
  if (body.preferredLevel !== undefined) {
    const lvl = String(body.preferredLevel || "").trim().toUpperCase();
    if (lvl !== "ALL" && !READING_CEFR_LEVELS.includes(lvl)) {
      throw new AppError(400, "INVALID_LEVEL", "preferredLevel must be ALL or A1..C2.");
    }
    nextLevel = lvl;
  }

  const nextAccent = body.accent !== undefined ? normalizeAccent(body.accent) : current.accent;
  const nextVoiceName =
    body.voiceName !== undefined ? String(body.voiceName || "").trim().slice(0, 120) : current.voiceName;
  const nextSpeed =
    body.defaultSpeed !== undefined ? normalizeSpeed(body.defaultSpeed) : current.defaultSpeed;
  const nextShowBengali =
    body.showBengali !== undefined ? Boolean(body.showBengali) : current.showBengali;
  const nextDailyGoal =
    body.dailyGoalMinutes !== undefined
      ? normalizeReadingGoalMinutes(body.dailyGoalMinutes)
      : current.dailyGoalMinutes;
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

  await Promise.all([
    UserSettings.findOneAndUpdate(
      { user: userDoc._id },
      {
        $set: {
          "reading.preferredLevel": nextLevel,
          "reading.accent": nextAccent,
          "reading.voiceName": nextVoiceName,
          "reading.defaultSpeed": nextSpeed,
          "reading.showBengali": nextShowBengali,
          "reading.dailyGoalMinutes": nextDailyGoal,
          "reading.reminderEnabled": nextReminderEnabled,
          "reading.reminderTime": nextReminderTime,
          explanationLanguage: nextShowBengali ? "bn" : "en",
          "notifications.lessonReminder": nextReminderEnabled,
        },
        $setOnInsert: { user: userDoc._id },
      },
      { upsert: true, new: true }
    ),
    body.dailyGoalMinutes !== undefined
      ? User.updateOne({ _id: userDoc._id }, { $set: { dailyGoalMinutes: nextDailyGoal } })
      : Promise.resolve(),
  ]);

  return {
    preferredLevel: nextLevel,
    accent: nextAccent,
    voiceName: nextVoiceName,
    defaultSpeed: nextSpeed,
    showBengali: nextShowBengali,
    dailyGoalMinutes: nextDailyGoal,
    reminderEnabled: nextReminderEnabled,
    reminderTime: nextReminderTime,
    timezone: current.timezone,
  };
}

const PASSAGE_SUMMARY_FIELDS =
  "title slug level categoryId summary estimatedMinutes audioUrl isPublished sortOrder createdAt updatedAt sentences.sentenceId vocabulary.word questions.questionId";

function formatPassageSummary(p, progressDoc = null, categoryNameMap = null) {
  const catIdStr = String(p.categoryId?._id || p.categoryId || "");
  const resolvedCatName =
    p.categoryName ||
    p.categoryId?.name ||
    (categoryNameMap && catIdStr ? categoryNameMap.get(catIdStr) || "" : "");

  return {
    id: String(p._id),
    title: p.title,
    slug: p.slug,
    level: p.level,
    categoryId: catIdStr,
    categoryName: resolvedCatName,
    summary: p.summary || "",
    estimatedMinutes: p.estimatedMinutes || 3,
    sentenceCount:
      typeof p.sentenceCount === "number"
        ? p.sentenceCount
        : Array.isArray(p.sentences)
        ? p.sentences.length
        : 0,
    vocabCount:
      typeof p.vocabCount === "number"
        ? p.vocabCount
        : Array.isArray(p.vocabulary)
        ? p.vocabulary.length
        : 0,
    questionCount:
      typeof p.questionCount === "number"
        ? p.questionCount
        : Array.isArray(p.questions)
        ? p.questions.length
        : 0,
    hasAudioUrl:
      typeof p.hasAudioUrl === "boolean" ? p.hasAudioUrl : Boolean(p.audioUrl),
    isPublished: Boolean(p.isPublished),
    status: progressDoc?.status || "not_started",
    lastSentenceIndex: progressDoc?.lastSentenceIndex || 0,
    progressPercent: progressDoc?.progressPercent || 0,
    totalSeconds: progressDoc?.totalSeconds || 0,
    lastReadAt: progressDoc?.lastReadAt || null,
    completedAt: progressDoc?.completedAt || null,
  };
}

export async function getReadingDashboard({ userDoc, now = new Date() }) {
  const userId = userDoc.firebaseUid;
  const settings = await getUserReadingSettings(userDoc);
  const timezone = settings.timezone;
  const todayStr = getLocalDateString(now, timezone);
  const sixtyDaysAgoStr = shiftLocalDate(todayStr, -60);

  const [
    categories,
    allProgress,
    dailyDocs,
    recentAttempts,
    totalPublishedPassages,
  ] = await Promise.all([
    ReadingCategory.find({ isActive: true }).sort({ sortOrder: 1, name: 1 }).lean(),
    UserReadingProgress.find({ userId })
      .sort({ lastReadAt: -1 })
      .populate({
        path: "passageId",
        match: { isPublished: true },
        select: PASSAGE_SUMMARY_FIELDS,
        populate: { path: "categoryId", select: "name" },
      })
      .lean(),
    ReadingDailyActivity.find({
      userId,
      date: { $gte: sixtyDaysAgoStr, $lte: todayStr },
    }).lean(),
    ReadingAttempt.find({ userId })
      .sort({ createdAt: -1 })
      .limit(12)
      .populate({ path: "passageId", select: "title level" })
      .lean(),
    ReadingPassage.countDocuments({ isPublished: true }),
  ]);

  const validProgress = allProgress.filter((pr) => Boolean(pr.passageId));
  const completedIds = new Set(
    validProgress
      .filter((pr) => pr.status === "completed")
      .map((pr) => String(pr.passageId._id))
  );

  // Total reading minutes across all daily records (or sum of user passage progress)
  const totalSecondsFromProgress = validProgress.reduce(
    (sum, pr) => sum + Number(pr.totalSeconds || 0),
    0
  );
  const totalSecondsFromDaily = dailyDocs.reduce(
    (sum, d) => sum + Number(d.readingSeconds || 0),
    0
  );
  const totalReadingMinutes = Math.round(Math.max(totalSecondsFromProgress, totalSecondsFromDaily) / 60);

  const dailyByDate = new Map(dailyDocs.map((d) => [d.date, d]));
  const todayDoc = dailyByDate.get(todayStr);
  const todayMinutes = Math.round(Number(todayDoc?.readingSeconds || 0) / 60);

  const activeDates = new Set();
  for (const d of dailyDocs) {
    if ((d.readingSeconds || 0) >= 15 || (d.completedPassageIds || []).length > 0) {
      activeDates.add(d.date);
    }
  }
  const { currentStreak, longestStreak } = computeStreaksFromActiveDates(activeDates, todayStr);
  const { weekRangeLabel, days } = buildWeekWindow(todayStr);

  const weeklyActivity = days.map((d) => {
    const doc = dailyByDate.get(d.date);
    const secs = Number(doc?.readingSeconds || 0);
    const completedToday = Array.isArray(doc?.completedPassageIds)
      ? doc.completedPassageIds.length
      : 0;
    return {
      date: d.date,
      dayNumber: d.dayNumber,
      dayShort: d.dayShort,
      isToday: d.isToday,
      minutes: Math.round(secs / 60),
      completedCount: completedToday,
      isActive: secs >= 15 || completedToday > 0,
    };
  });

  const continueRow = validProgress.find((pr) => pr.status === "in_progress") || null;
  const continueReading = continueRow
    ? formatPassageSummary(continueRow.passageId, continueRow)
    : null;

  const recent = validProgress.slice(0, 6).map((pr) => formatPassageSummary(pr.passageId, pr));

  // Recommended: published passages matching user's preferredLevel (if not ALL) that are not completed yet
  const recFilter = { isPublished: true };
  if (settings.preferredLevel !== "ALL") {
    recFilter.level = settings.preferredLevel;
  }
  if (completedIds.size > 0) {
    recFilter._id = { $nin: Array.from(completedIds).map((id) => new mongoose.Types.ObjectId(id)) };
  }
  let recommendedDocs = await ReadingPassage.find(recFilter)
    .select(PASSAGE_SUMMARY_FIELDS)
    .sort({ sortOrder: 1, createdAt: -1 })
    .limit(6)
    .populate("categoryId", "name")
    .lean();

  // Fallback to any uncompleted published passages if preferred level had none left
  if (recommendedDocs.length === 0 && settings.preferredLevel !== "ALL") {
    const fallbackFilter = { isPublished: true };
    if (completedIds.size > 0) {
      fallbackFilter._id = {
        $nin: Array.from(completedIds).map((id) => new mongoose.Types.ObjectId(id)),
      };
    }
    recommendedDocs = await ReadingPassage.find(fallbackFilter)
      .select(PASSAGE_SUMMARY_FIELDS)
      .sort({ sortOrder: 1, createdAt: -1 })
      .limit(6)
      .populate("categoryId", "name")
      .lean();
  }

  const progressMap = new Map(validProgress.map((pr) => [String(pr.passageId._id), pr]));
  const recommended = recommendedDocs.map((p) =>
    formatPassageSummary(p, progressMap.get(String(p._id)) || null)
  );

  const history = recentAttempts.map((a) => ({
    id: String(a._id),
    passageId: String(a.passageId?._id || a.passageId || ""),
    passageTitle: a.passageId?.title || "Reading Passage",
    level: a.passageId?.level || "A1",
    mode: a.mode,
    scorePercent: a.scorePercent,
    correctCount: a.correctCount,
    totalCount: a.totalCount,
    createdAt: a.createdAt,
  }));

  return {
    stats: {
      dailyGoalMinutes: settings.dailyGoalMinutes,
      todayMinutes,
      completedPassagesCount: completedIds.size,
      totalPublishedPassages,
      streakDays: currentStreak,
      longestStreak,
      totalReadingMinutes,
    },
    continueReading,
    recent,
    recommended,
    history,
    weekRangeLabel,
    weeklyActivity,
    categories: categories.map((c) => ({
      id: String(c._id),
      name: c.name,
      slug: c.slug,
      description: c.description || "",
      icon: c.icon || "menu_book",
    })),
    settings,
  };
}

export async function listReadingPassages({
  userId,
  page = 1,
  limit = 15,
  level = "ALL",
  categoryId = "",
  search = "",
}) {
  const safePage = Math.max(1, Number(page) || 1);
  const safeLimit = Math.max(1, Math.min(30, Number(limit) || 15));
  const skip = (safePage - 1) * safeLimit;

  const filter = { isPublished: true };
  const cleanLevel = String(level || "ALL").trim().toUpperCase();
  if (cleanLevel !== "ALL" && READING_CEFR_LEVELS.includes(cleanLevel)) {
    filter.level = cleanLevel;
  }
  if (categoryId && mongoose.Types.ObjectId.isValid(String(categoryId))) {
    filter.categoryId = new mongoose.Types.ObjectId(String(categoryId));
  }
  const cleanSearch = String(search || "").trim();
  if (cleanSearch) {
    const rx = new RegExp(cleanSearch.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
    filter.$or = [{ title: rx }, { summary: rx }];
  }

  const [docs, total, categories] = await Promise.all([
    ReadingPassage.aggregate([
      { $match: filter },
      { $sort: { sortOrder: 1, createdAt: -1 } },
      { $skip: skip },
      { $limit: safeLimit },
      {
        $project: {
          title: 1,
          slug: 1,
          level: 1,
          categoryId: 1,
          summary: 1,
          estimatedMinutes: 1,
          isPublished: 1,
          sentenceCount: { $size: { $ifNull: ["$sentences", []] } },
          vocabCount: { $size: { $ifNull: ["$vocabulary", []] } },
          questionCount: { $size: { $ifNull: ["$questions", []] } },
          hasAudioUrl: { $gt: [{ $strLenCP: { $ifNull: ["$audioUrl", ""] } }, 0] },
        },
      },
    ]),
    ReadingPassage.countDocuments(filter),
    ReadingCategory.find().select("_id name").lean(),
  ]);

  const categoryNameMap = new Map(categories.map((c) => [String(c._id), c.name]));
  const ids = docs.map((d) => d._id);
  const progressDocs = ids.length
    ? await UserReadingProgress.find({ userId, passageId: { $in: ids } }).lean()
    : [];
  const progressMap = new Map(progressDocs.map((pr) => [String(pr.passageId), pr]));

  return {
    items: docs.map((p) =>
      formatPassageSummary(p, progressMap.get(String(p._id)) || null, categoryNameMap)
    ),
    page: safePage,
    limit: safeLimit,
    total,
    totalPages: Math.max(1, Math.ceil(total / safeLimit)),
  };
}

export async function getReadingPassageDetail({ userId, passageId, includeUnpublished = false }) {
  const objId = assertReadingObjectId(passageId, "passageId");
  const filter = includeUnpublished ? { _id: objId } : { _id: objId, isPublished: true };

  const p = await ReadingPassage.findOne(filter).populate("categoryId", "name").lean();
  if (!p) {
    throw new AppError(404, "PASSAGE_NOT_FOUND", "Reading passage not found.");
  }

  const [progress, attempts] = await Promise.all([
    UserReadingProgress.findOne({ userId, passageId: objId }).lean(),
    ReadingAttempt.find({ userId, passageId: objId }).sort({ createdAt: -1 }).limit(10).lean(),
  ]);

  const sentences =
    Array.isArray(p.sentences) && p.sentences.length > 0
      ? p.sentences
      : splitTextIntoSentences(p.englishText);

  return {
    passage: {
      ...formatPassageSummary(p, progress),
      englishText: p.englishText,
      bengaliTranslation: p.bengaliTranslation || "",
      audioUrl: p.audioUrl || null,
      sentences: sentences.map((s, idx) => ({
        sentenceId: s.sentenceId,
        order: s.order ?? idx,
        en: s.en,
        bn: s.bn || "",
      })),
      vocabulary: (p.vocabulary || []).map((v) => ({
        word: v.word,
        meaningBn: v.meaningBn || "",
        pronunciation: v.pronunciation || "",
        partOfSpeech: v.partOfSpeech || "",
        example: v.example || "",
      })),
      questions: (p.questions || []).map((q) => ({
        questionId: q.questionId,
        type: q.type,
        prompt: q.prompt,
        options: Array.isArray(q.options) ? q.options : [],
        correctAnswer: q.correctAnswer,
        explanationBn: q.explanationBn || "",
      })),
    },
    progress: progress
      ? {
          status: progress.status,
          lastSentenceIndex: progress.lastSentenceIndex || 0,
          lastSentenceId: progress.lastSentenceId || "",
          readSentenceIds: progress.readSentenceIds || [],
          progressPercent: progress.progressPercent || 0,
          totalSeconds: progress.totalSeconds || 0,
          lastReadAt: progress.lastReadAt,
          completedAt: progress.completedAt,
        }
      : null,
    attempts: attempts.map((a) => ({
      id: String(a._id),
      mode: a.mode,
      scorePercent: a.scorePercent,
      correctCount: a.correctCount,
      totalCount: a.totalCount,
      answers: a.answers || [],
      textMatchingSummary: a.textMatchingSummary || null,
      createdAt: a.createdAt,
    })),
  };
}

export async function saveReadingProgress({ userDoc, passageId, body = {}, now = new Date() }) {
  const userId = userDoc.firebaseUid;
  const objId = assertReadingObjectId(passageId, "passageId");
  const passage = await ReadingPassage.findOne({ _id: objId, isPublished: true })
    .select("sentences")
    .lean();
  if (!passage) {
    throw new AppError(404, "PASSAGE_NOT_FOUND", "Reading passage not found.");
  }

  const totalSentences = Math.max(1, (passage.sentences || []).length);
  const lastSentenceIndex = Math.max(
    0,
    Math.min(totalSentences - 1, Math.round(Number(body.lastSentenceIndex || 0)))
  );
  const lastSentenceId = String(body.lastSentenceId || "").trim().slice(0, 64);
  const incomingReadIds = Array.isArray(body.readSentenceIds)
    ? body.readSentenceIds.map((id) => String(id).trim()).filter(Boolean).slice(0, 500)
    : [];
  const clientSequence = Math.max(0, Math.round(Number(body.clientSequence || 0)));
  const rawDelta = Math.round(Number(body.elapsedDeltaSeconds || 0));
  const safeDelta = Number.isFinite(rawDelta) ? Math.max(0, Math.min(300, rawDelta)) : 0;
  const markCompleted = Boolean(body.markCompleted);

  const existing = await UserReadingProgress.findOne({ userId, passageId: objId }).lean();
  const isDuplicateSeq =
    Boolean(existing) && clientSequence > 0 && clientSequence <= (existing.lastClientSequence || 0);

  const mergedReadIds = new Set(existing?.readSentenceIds || []);
  for (const sid of incomingReadIds) mergedReadIds.add(sid);
  if (lastSentenceId) mergedReadIds.add(lastSentenceId);

  const wasCompleted = existing?.status === "completed";
  const computedPercent = Math.min(
    100,
    Math.round((Math.max(mergedReadIds.size, lastSentenceIndex + 1) / totalSentences) * 100)
  );
  const isCompletedNow = wasCompleted || markCompleted || computedPercent >= 100;
  const finalPercent = isCompletedNow ? 100 : Math.max(existing?.progressPercent || 0, computedPercent);
  const effectiveDelta = isDuplicateSeq ? 0 : safeDelta;

  const updated = await UserReadingProgress.findOneAndUpdate(
    { userId, passageId: objId },
    {
      $setOnInsert: { userId, passageId: objId },
      $set: {
        status: isCompletedNow ? "completed" : "in_progress",
        lastSentenceIndex,
        lastSentenceId,
        readSentenceIds: Array.from(mergedReadIds),
        progressPercent: finalPercent,
        lastClientSequence: Math.max(existing?.lastClientSequence || 0, clientSequence),
        lastReadAt: now,
        completedAt: existing?.completedAt || (isCompletedNow ? now : null),
      },
      ...(effectiveDelta > 0 ? { $inc: { totalSeconds: effectiveDelta } } : {}),
    },
    { upsert: true, new: true }
  ).lean();

  // Update daily activity safely without duplicate completion counting
  const settings = await getUserReadingSettings(userDoc);
  const todayStr = getLocalDateString(now, settings.timezone);
  const dailyUpdate = {
    $setOnInsert: { userId, date: todayStr, dailyGoalMinutes: settings.dailyGoalMinutes },
  };
  if (effectiveDelta > 0) {
    dailyUpdate.$inc = { readingSeconds: effectiveDelta };
  }
  if (isCompletedNow) {
    dailyUpdate.$addToSet = { completedPassageIds: objId };
  }
  if (dailyUpdate.$inc || dailyUpdate.$addToSet || !wasCompleted) {
    await ReadingDailyActivity.findOneAndUpdate({ userId, date: todayStr }, dailyUpdate, {
      upsert: true,
    });
  }

  return {
    status: updated.status,
    lastSentenceIndex: updated.lastSentenceIndex,
    lastSentenceId: updated.lastSentenceId,
    readSentenceIds: updated.readSentenceIds,
    progressPercent: updated.progressPercent,
    totalSeconds: updated.totalSeconds,
    completedAt: updated.completedAt,
    deduplicated: isDuplicateSeq,
  };
}

export async function submitReadingAttempt({ userDoc, passageId, body = {}, now = new Date() }) {
  const userId = userDoc.firebaseUid;
  const objId = assertReadingObjectId(passageId, "passageId");
  const passage = await ReadingPassage.findOne({ _id: objId, isPublished: true }).lean();
  if (!passage) {
    throw new AppError(404, "PASSAGE_NOT_FOUND", "Reading passage not found.");
  }

  const mode = body.mode === "read_aloud" ? "read_aloud" : "comprehension";

  if (mode === "read_aloud") {
    const matchedWords = Math.max(0, Math.round(Number(body.matchedWords || 0)));
    const missingWords = Math.max(0, Math.round(Number(body.missingWords || 0)));
    const extraWords = Math.max(0, Math.round(Number(body.extraWords || 0)));
    const mismatchedWords = Math.max(0, Math.round(Number(body.mismatchedWords || 0)));
    const totalCount = Math.max(1, matchedWords + missingWords + mismatchedWords);
    const scorePercent = Math.min(100, Math.round((matchedWords / totalCount) * 100));

    const attempt = await ReadingAttempt.create({
      userId,
      passageId: objId,
      mode: "read_aloud",
      scorePercent,
      correctCount: matchedWords,
      totalCount,
      answers: [],
      textMatchingSummary: {
        matchedWords,
        missingWords,
        extraWords,
        mismatchedWords,
      },
    });

    return {
      attempt: {
        id: String(attempt._id),
        mode: attempt.mode,
        scorePercent: attempt.scorePercent,
        correctCount: attempt.correctCount,
        totalCount: attempt.totalCount,
        textMatchingSummary: attempt.textMatchingSummary,
        createdAt: attempt.createdAt,
      },
    };
  }

  const questions = Array.isArray(passage.questions) ? passage.questions : [];
  if (questions.length === 0) {
    throw new AppError(400, "NO_QUESTIONS", "This passage has no comprehension questions yet.");
  }

  const evaluated = evaluateComprehensionAnswers(questions, body.answers || []);
  const attempt = await ReadingAttempt.create({
    userId,
    passageId: objId,
    mode: "comprehension",
    scorePercent: evaluated.scorePercent,
    correctCount: evaluated.correctCount,
    totalCount: evaluated.totalCount,
    answers: evaluated.answers,
  });

  // Mark passage completed if comprehension quiz submitted cleanly
  await saveReadingProgress({
    userDoc,
    passageId,
    body: {
      lastSentenceIndex: Math.max(0, (passage.sentences || []).length - 1),
      markCompleted: true,
    },
    now,
  });

  return {
    attempt: {
      id: String(attempt._id),
      mode: attempt.mode,
      scorePercent: attempt.scorePercent,
      correctCount: attempt.correctCount,
      totalCount: attempt.totalCount,
      answers: attempt.answers,
      createdAt: attempt.createdAt,
    },
  };
}

export async function lookupReadingWord({ userId, rawWord, passageId = null }) {
  const clean = String(rawWord || "")
    .trim()
    .replace(/^[^a-zA-Z]+|[^a-zA-Z'’-]+$/g, "");
  if (!clean) {
    throw new AppError(400, "INVALID_WORD", "Word is required.");
  }
  const candidates = buildEnglishWordCandidates(clean);
  const candidateSlugs = candidates.map((c) => slugifyReadingText(c));
  const candidateRegexes = candidates.map(
    (c) => new RegExp(`^${c.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`, "i")
  );

  let passageVocabMatch = null;
  if (passageId && mongoose.Types.ObjectId.isValid(String(passageId))) {
    const p = await ReadingPassage.findById(passageId).select("vocabulary").lean();
    const vocabList = p?.vocabulary || [];
    for (const candidate of candidates) {
      passageVocabMatch = vocabList.find(
        (v) => String(v.word || "").trim().toLowerCase() === candidate
      );
      if (passageVocabMatch) break;
    }
  }

  // Check Vocabulary collection across exact + inflected base candidates
  const matchedVocabs = await Vocabulary.find({
    $or: [{ slug: { $in: candidateSlugs } }, { word: { $in: candidateRegexes } }],
  }).lean();

  let vocabDoc = null;
  for (const candidate of candidates) {
    const candidateSlug = slugifyReadingText(candidate);
    vocabDoc =
      matchedVocabs.find(
        (doc) =>
          doc.isPublished !== false &&
          (doc.slug === candidateSlug || String(doc.word || "").trim().toLowerCase() === candidate)
      ) ||
      matchedVocabs.find(
        (doc) =>
          doc.slug === candidateSlug || String(doc.word || "").trim().toLowerCase() === candidate
      );
    if (vocabDoc) break;
  }

  let inUserVocabulary = false;
  if (vocabDoc) {
    const existingProg = await UserVocabularyProgress.exists({
      userId,
      vocabularyId: vocabDoc._id,
    });
    inUserVocabulary = Boolean(existingProg);
  }

  const meaningBn =
    String(passageVocabMatch?.meaningBn || "").trim() || String(vocabDoc?.meaning || "").trim();
  const pronunciation =
    String(passageVocabMatch?.pronunciation || "").trim() ||
    String(vocabDoc?.pronunciation || "").trim();
  const partOfSpeech =
    String(passageVocabMatch?.partOfSpeech || "").trim() ||
    String(vocabDoc?.partOfSpeech || "").trim();
  const example =
    String(passageVocabMatch?.example || "").trim() ||
    String(vocabDoc?.examples?.[0]?.en || "").trim();

  const found = Boolean(meaningBn);
  return {
    word: passageVocabMatch?.word || vocabDoc?.word || clean,
    found,
    meaningBn: found ? meaningBn : null,
    pronunciation,
    partOfSpeech,
    example,
    existingVocabularyId: vocabDoc ? String(vocabDoc._id) : null,
    inUserVocabulary,
  };
}

export async function saveReadingWordToVocabulary({ userId, body = {} }) {
  const cleanWord = String(body.word || "")
    .trim()
    .replace(/^[^a-zA-Z]+|[^a-zA-Z'-]+$/g, "");
  if (!cleanWord) {
    throw new AppError(400, "INVALID_WORD", "Word is required.");
  }

  const lookup = await lookupReadingWord({
    userId,
    rawWord: cleanWord,
    passageId: body.passageId || null,
  });

  const explicitMeaning = String(body.meaningBn || "").trim();
  const finalMeaning = explicitMeaning || lookup.meaningBn || "";

  let vocabId = lookup.existingVocabularyId;
  if (!vocabId) {
    if (!finalMeaning || finalMeaning.toLowerCase() === "not available") {
      throw new AppError(
        400,
        "MEANING_NOT_AVAILABLE",
        "Meaning is not available for this word, so it cannot be saved."
      );
    }

    // Ensure default "Reading Practice" VocabularyCategory exists
    const catSlug = "reading-practice";
    const cat = await VocabularyCategory.findOneAndUpdate(
      { slug: catSlug },
      {
        $setOnInsert: {
          name: "Reading Practice",
          slug: catSlug,
          description: "Words saved from reading passages.",
          isActive: true,
        },
      },
      { upsert: true, new: true }
    ).lean();

    const slug = slugifyReadingText(cleanWord);
    const level = READING_CEFR_LEVELS.includes(String(body.level || "").toUpperCase())
      ? String(body.level).toUpperCase()
      : "A1";

    const exEn = String(body.example || lookup.example || "").trim();
    const createdOrExisting = await Vocabulary.findOneAndUpdate(
      { slug },
      {
        $setOnInsert: {
          word: cleanWord,
          slug,
          meaning: finalMeaning.slice(0, 200),
          pronunciation: String(body.pronunciation || lookup.pronunciation || "").slice(0, 120),
          partOfSpeech: String(body.partOfSpeech || lookup.partOfSpeech || "noun").toLowerCase(),
          level,
          categoryId: cat._id,
          examples: exEn ? [{ en: exEn, bn: finalMeaning }] : [],
          isPublished: true,
        },
      },
      { upsert: true, new: true }
    ).lean();

    vocabId = String(createdOrExisting._id);
  }

  const existingProgress = await UserVocabularyProgress.findOne({
    userId,
    vocabularyId: vocabId,
  }).lean();

  await UserVocabularyProgress.findOneAndUpdate(
    { userId, vocabularyId: vocabId },
    {
      $setOnInsert: {
        userId,
        vocabularyId: vocabId,
        status: "learning",
      },
      $set: {
        isFavorite: true,
      },
    },
    { upsert: true, new: true }
  );

  return {
    saved: true,
    alreadyExisted: Boolean(existingProgress),
    vocabularyId: vocabId,
    word: cleanWord,
  };
}

// ---------- ADMIN OPERATIONS ----------

export async function adminListReadingCategories() {
  const cats = await ReadingCategory.find().sort({ sortOrder: 1, name: 1 }).lean();
  const counts = await ReadingPassage.aggregate([
    { $group: { _id: "$categoryId", count: { $sum: 1 } } },
  ]);
  const countMap = new Map(counts.map((c) => [String(c._id), c.count]));
  return cats.map((c) => ({
    id: String(c._id),
    name: c.name,
    slug: c.slug,
    description: c.description || "",
    icon: c.icon || "menu_book",
    sortOrder: c.sortOrder || 0,
    isActive: Boolean(c.isActive),
    passageCount: countMap.get(String(c._id)) || 0,
  }));
}

export async function adminSaveReadingCategory({ id = null, body = {} }) {
  const name = String(body.name || "").trim();
  if (!name) throw new AppError(400, "VALIDATION_ERROR", "Category name is required.");
  const slug = slugifyReadingText(name);
  const payload = {
    name,
    slug,
    description: String(body.description || "").trim().slice(0, 240),
    icon: String(body.icon || "menu_book").trim(),
    sortOrder: Number.isFinite(Number(body.sortOrder)) ? Number(body.sortOrder) : 0,
    isActive: body.isActive !== undefined ? Boolean(body.isActive) : true,
  };

  if (id) {
    const objId = assertReadingObjectId(id, "categoryId");
    const updated = await ReadingCategory.findByIdAndUpdate(objId, { $set: payload }, { new: true }).lean();
    if (!updated) throw new AppError(404, "CATEGORY_NOT_FOUND", "Reading category not found.");
    return {
      id: String(updated._id),
      name: updated.name,
      slug: updated.slug,
      description: updated.description || "",
      icon: updated.icon || "menu_book",
      sortOrder: updated.sortOrder || 0,
      isActive: Boolean(updated.isActive),
    };
  }

  const existing = await ReadingCategory.findOne({ slug }).lean();
  if (existing) {
    throw new AppError(409, "DUPLICATE_CATEGORY", "A reading category with this name already exists.");
  }
  const created = await ReadingCategory.create(payload);
  return {
    id: String(created._id),
    name: created.name,
    slug: created.slug,
    description: created.description || "",
    icon: created.icon || "menu_book",
    sortOrder: created.sortOrder || 0,
    isActive: Boolean(created.isActive),
  };
}

export async function adminDeleteReadingCategory(id) {
  const objId = assertReadingObjectId(id, "categoryId");
  const count = await ReadingPassage.countDocuments({ categoryId: objId });
  if (count > 0) {
    throw new AppError(
      400,
      "CATEGORY_IN_USE",
      `Cannot delete category while ${count} passage(s) use it.`
    );
  }
  const res = await ReadingCategory.deleteOne({ _id: objId });
  if (res.deletedCount === 0) {
    throw new AppError(404, "CATEGORY_NOT_FOUND", "Reading category not found.");
  }
  return { deleted: true };
}

export async function adminListPassages({
  page = 1,
  limit = 20,
  level = "ALL",
  categoryId = "",
  published = "all",
  search = "",
}) {
  const safePage = Math.max(1, Number(page) || 1);
  const safeLimit = Math.max(1, Math.min(50, Number(limit) || 20));
  const skip = (safePage - 1) * safeLimit;

  const filter = {};
  const cleanLevel = String(level || "ALL").trim().toUpperCase();
  if (cleanLevel !== "ALL" && READING_CEFR_LEVELS.includes(cleanLevel)) {
    filter.level = cleanLevel;
  }
  if (categoryId && mongoose.Types.ObjectId.isValid(String(categoryId))) {
    filter.categoryId = new mongoose.Types.ObjectId(String(categoryId));
  }
  if (published === "published") filter.isPublished = true;
  if (published === "draft") filter.isPublished = false;

  const cleanSearch = String(search || "").trim();
  if (cleanSearch) {
    const rx = new RegExp(cleanSearch.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
    filter.$or = [{ title: rx }, { summary: rx }];
  }

  const [docs, total, categories] = await Promise.all([
    ReadingPassage.aggregate([
      { $match: filter },
      { $sort: { updatedAt: -1, sortOrder: 1 } },
      { $skip: skip },
      { $limit: safeLimit },
      {
        $project: {
          title: 1,
          slug: 1,
          level: 1,
          categoryId: 1,
          summary: 1,
          estimatedMinutes: 1,
          isPublished: 1,
          sentenceCount: { $size: { $ifNull: ["$sentences", []] } },
          vocabCount: { $size: { $ifNull: ["$vocabulary", []] } },
          questionCount: { $size: { $ifNull: ["$questions", []] } },
          hasAudioUrl: { $gt: [{ $strLenCP: { $ifNull: ["$audioUrl", ""] } }, 0] },
        },
      },
    ]),
    ReadingPassage.countDocuments(filter),
    ReadingCategory.find().select("_id name").lean(),
  ]);

  const categoryNameMap = new Map(categories.map((c) => [String(c._id), c.name]));

  return {
    items: docs.map((p) => formatPassageSummary(p, null, categoryNameMap)),
    page: safePage,
    limit: safeLimit,
    total,
    totalPages: Math.max(1, Math.ceil(total / safeLimit)),
  };
}

function sanitizeQuestions(rawQuestions = []) {
  if (!Array.isArray(rawQuestions)) return [];
  return rawQuestions
    .map((q, idx) => {
      const type = ["mcq", "true_false", "short_answer"].includes(q?.type) ? q.type : "mcq";
      const prompt = String(q?.prompt || "").trim();
      const correctAnswer = String(q?.correctAnswer || "").trim();
      if (!prompt || !correctAnswer) return null;
      const options =
        type === "true_false"
          ? ["True", "False"]
          : Array.isArray(q?.options)
          ? q.options.map((o) => String(o || "").trim()).filter(Boolean)
          : [];
      return {
        questionId: String(q?.questionId || `q_${idx + 1}`).trim(),
        type,
        prompt,
        options,
        correctAnswer,
        explanationBn: String(q?.explanationBn || "").trim(),
      };
    })
    .filter(Boolean);
}

function sanitizeVocabulary(rawVocab = []) {
  if (!Array.isArray(rawVocab)) return [];
  return rawVocab
    .map((v) => {
      const word = String(v?.word || "").trim();
      if (!word) return null;
      return {
        word,
        meaningBn: String(v?.meaningBn || "").trim(),
        pronunciation: String(v?.pronunciation || "").trim(),
        partOfSpeech: String(v?.partOfSpeech || "").trim(),
        example: String(v?.example || "").trim(),
      };
    })
    .filter(Boolean);
}

export async function adminSavePassage({ id = null, adminUid = null, body = {} }) {
  const title = String(body.title || "").trim();
  const englishText = String(body.englishText || "").trim();
  if (!title) throw new AppError(400, "VALIDATION_ERROR", "Passage title is required.");
  if (!englishText) throw new AppError(400, "VALIDATION_ERROR", "English text is required.");

  const level = READING_CEFR_LEVELS.includes(String(body.level || "A1").toUpperCase())
    ? String(body.level).toUpperCase()
    : "A1";
  const categoryId = assertReadingObjectId(body.categoryId, "categoryId");
  const catExists = await ReadingCategory.exists({ _id: categoryId });
  if (!catExists) throw new AppError(404, "CATEGORY_NOT_FOUND", "Selected reading category does not exist.");

  const audioUrlRaw = body.audioUrl ? String(body.audioUrl).trim() : null;
  if (audioUrlRaw && !isValidOptionalAudioUrl(audioUrlRaw)) {
    throw new AppError(400, "INVALID_AUDIO_URL", "Optional MP3 URL must start with http:// or https://");
  }

  let existingPassage = null;
  if (id) {
    const objId = assertReadingObjectId(id, "passageId");
    existingPassage = await ReadingPassage.findById(objId).lean();
    if (!existingPassage) throw new AppError(404, "PASSAGE_NOT_FOUND", "Reading passage not found.");
  }

  // Use explicit sentences if valid array supplied, otherwise deterministically split englishText with stable IDs
  let sentences;
  if (Array.isArray(body.sentences) && body.sentences.length > 0) {
    sentences = body.sentences
      .map((s, idx) => ({
        sentenceId: String(s?.sentenceId || `s_${idx + 1}`).trim(),
        order: idx,
        en: String(s?.en || "").trim(),
        bn: String(s?.bn || "").trim(),
      }))
      .filter((s) => Boolean(s.en));
  } else {
    sentences = splitTextIntoSentences(englishText, existingPassage?.sentences || []);
  }
  if (sentences.length === 0) {
    throw new AppError(400, "VALIDATION_ERROR", "Passage must contain at least one sentence.");
  }

  const baseSlug = slugifyReadingText(title);
  let slug = existingPassage?.slug || baseSlug;
  if (!existingPassage) {
    const conflict = await ReadingPassage.exists({ slug });
    if (conflict) slug = `${baseSlug}-${Date.now().toString(36).slice(-4)}`;
  }

  const estimatedMinutes = Math.max(
    1,
    Math.min(60, Math.round(Number(body.estimatedMinutes || Math.ceil(englishText.split(/\s+/).length / 100))))
  );

  const payload = {
    title,
    slug,
    level,
    categoryId,
    summary: String(body.summary || "").trim().slice(0, 300),
    englishText,
    bengaliTranslation: String(body.bengaliTranslation || "").trim(),
    audioUrl: audioUrlRaw || null,
    estimatedMinutes,
    sentences,
    vocabulary: sanitizeVocabulary(body.vocabulary),
    questions: sanitizeQuestions(body.questions),
    isPublished: Boolean(body.isPublished),
    sortOrder: Number.isFinite(Number(body.sortOrder)) ? Number(body.sortOrder) : 0,
  };

  if (existingPassage) {
    await ReadingPassage.updateOne({ _id: existingPassage._id }, { $set: payload });
    return getReadingPassageDetail({
      userId: adminUid || "admin",
      passageId: String(existingPassage._id),
      includeUnpublished: true,
    });
  }

  const created = await ReadingPassage.create({
    ...payload,
    createdByUid: adminUid || null,
  });
  return getReadingPassageDetail({
    userId: adminUid || "admin",
    passageId: String(created._id),
    includeUnpublished: true,
  });
}

export async function adminDeletePassage(id) {
  const objId = assertReadingObjectId(id, "passageId");
  const res = await ReadingPassage.deleteOne({ _id: objId });
  if (res.deletedCount === 0) {
    throw new AppError(404, "PASSAGE_NOT_FOUND", "Reading passage not found.");
  }
  await Promise.all([
    UserReadingProgress.deleteMany({ passageId: objId }),
    ReadingAttempt.deleteMany({ passageId: objId }),
  ]);
  return { deleted: true };
}
