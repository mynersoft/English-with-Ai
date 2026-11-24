import { AppError } from "@/lib/errors";
import Vocabulary from "@/models/Vocabulary";
import UserVocabularyProgress from "@/models/UserVocabularyProgress";
import VocabularyDailyActivity from "@/models/VocabularyDailyActivity";
import VocabularyQuizAttempt, { QUIZ_TYPES } from "@/models/VocabularyQuizAttempt";
import { computeNextSrsState } from "@/lib/vocabulary/srs";
import { getLocalDateString, getOrCreateVocabSettings } from "@/lib/vocabulary/dailyWords";
import { escapeRegex, isValidObjectId } from "@/lib/vocabulary/validators";

export function shuffleArray(items) {
  const arr = [...items];
  for (let i = arr.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

function maskTargetWordInSentence(sentence, word) {
  if (!sentence || !word) return "_____";
  const regex = new RegExp(`\\b${escapeRegex(word)}\\w*\\b`, "i");
  if (regex.test(sentence)) {
    return sentence.replace(regex, "_____");
  }
  return `${sentence} (_____)`;
}

/**
 * Generates randomized, non-repeating quiz payloads WITHOUT exposing correct answers.
 */
export async function generateQuizQuestions({
  userId,
  quizType = "en_to_bn",
  count = 5,
  level,
  categoryId,
  source = "mixed",
}) {
  if (!QUIZ_TYPES.includes(quizType)) {
    throw new AppError(400, "VALIDATION_ERROR", `Unsupported quizType: ${quizType}`);
  }
  const safeCount = Math.min(10, Math.max(2, Number(count) || 5));

  let candidateIds = null;
  if (source === "learned") {
    candidateIds = await UserVocabularyProgress.find({ userId }).distinct("vocabularyId");
  } else if (source === "due") {
    candidateIds = await UserVocabularyProgress.find({
      userId,
      nextReviewAt: { $lte: new Date() },
    }).distinct("vocabularyId");
  }

  const query = { isPublished: true };
  if (level) query.level = String(level).toUpperCase();
  if (categoryId && isValidObjectId(categoryId)) query.categoryId = categoryId;
  if (candidateIds && candidateIds.length > 0) {
    query._id = { $in: candidateIds };
  }

  let pool = await Vocabulary.find(query).limit(120).lean();
  // Fallback to all published words if filtered learned/due pool is too small for distractors
  if (pool.length < 4 && (level || categoryId || candidateIds)) {
    pool = await Vocabulary.find({ isPublished: true }).limit(120).lean();
  }

  if (pool.length < 2) {
    throw new AppError(
      400,
      "INSUFFICIENT_VOCABULARY",
      "At least 2 published vocabulary words are needed to start a quiz."
    );
  }

  const chosen = shuffleArray(pool).slice(0, Math.min(safeCount, pool.length));

  if (quizType === "word_match") {
    const pairs = chosen.slice(0, Math.min(6, chosen.length));
    return {
      quizType,
      totalQuestions: pairs.length,
      leftItems: shuffleArray(
        pairs.map((w) => ({
          vocabularyId: String(w._id),
          word: w.word,
          pronunciation: w.pronunciation || "",
        }))
      ),
      rightItems: shuffleArray(
        pairs.map((w) => ({
          vocabularyId: String(w._id),
          meaning: w.meaning,
        }))
      ),
      questions: [],
    };
  }

  const questions = chosen.map((target, idx) => {
    const distractors = shuffleArray(
      pool.filter((p) => String(p._id) !== String(target._id))
    ).slice(0, 3);

    if (quizType === "en_to_bn" || quizType === "listening") {
      const options = shuffleArray([target, ...distractors]).map((item) => ({
        text: item.meaning,
      }));
      return {
        questionId: `${String(target._id)}_${idx}`,
        vocabularyId: String(target._id),
        prompt: quizType === "listening" ? "Listen and choose the Bengali meaning" : target.word,
        pronunciation: quizType === "listening" ? "" : target.pronunciation || "",
        partOfSpeech: target.partOfSpeech,
        audioUrl: target.audioUrl || null,
        // For browser Web Speech fallback only when no MP3 audioUrl is stored:
        speakHint: !target.audioUrl && quizType === "listening" ? target.word : null,
        options,
      };
    }

    if (quizType === "bn_to_en") {
      const options = shuffleArray([target, ...distractors]).map((item) => ({
        text: item.word,
      }));
      return {
        questionId: `${String(target._id)}_${idx}`,
        vocabularyId: String(target._id),
        prompt: target.meaning,
        partOfSpeech: target.partOfSpeech,
        options,
      };
    }

    // fill_blank
    const ex = (target.examples || [])[0];
    const sentenceWithBlank = ex
      ? maskTargetWordInSentence(ex.en, target.word)
      : `Choose the right word: _____ (${target.meaning})`;
    const options = shuffleArray([target, ...distractors]).map((item) => ({
      text: item.word,
    }));
    return {
      questionId: `${String(target._id)}_${idx}`,
      vocabularyId: String(target._id),
      prompt: sentenceWithBlank,
      meaningHint: ex?.bn || target.meaning,
      options,
    };
  });

  return {
    quizType,
    totalQuestions: questions.length,
    questions,
  };
}

/**
 * Grades answers authoritatively on the server, updates SM-2 progress + daily stats,
 * saves VocabularyQuizAttempt, and returns feedback.
 */
export async function gradeAndSaveQuizAttempt({ userId, quizType, answers, timeTaken = 0 }) {
  if (!QUIZ_TYPES.includes(quizType)) {
    throw new AppError(400, "VALIDATION_ERROR", `Unsupported quizType: ${quizType}`);
  }
  if (!Array.isArray(answers) || answers.length === 0) {
    throw new AppError(400, "VALIDATION_ERROR", "At least one answer is required.");
  }

  // Deduplicate vocabularyIds within one quiz submission
  const seen = new Set();
  const cleanAnswers = [];
  for (const a of answers) {
    if (!a || !isValidObjectId(a.vocabularyId)) continue;
    const vid = String(a.vocabularyId);
    if (seen.has(vid)) continue;
    seen.add(vid);
    cleanAnswers.push({
      vocabularyId: vid,
      selectedAnswer: String(a.selectedAnswer ?? "").trim(),
      matchedMeaning: a.matchedMeaning !== undefined ? String(a.matchedMeaning).trim() : null,
    });
  }

  if (cleanAnswers.length === 0) {
    throw new AppError(400, "VALIDATION_ERROR", "No valid answers submitted.");
  }

  const vocabDocs = await Vocabulary.find({
    _id: { $in: cleanAnswers.map((a) => a.vocabularyId) },
  }).lean();
  const vocabMap = new Map(vocabDocs.map((v) => [String(v._id), v]));

  const existingProgressDocs = await UserVocabularyProgress.find({
    userId,
    vocabularyId: { $in: cleanAnswers.map((a) => a.vocabularyId) },
  }).lean();
  const progressMap = new Map(existingProgressDocs.map((p) => [String(p.vocabularyId), p]));

  const now = new Date();
  let correctCount = 0;
  let wrongCount = 0;
  const results = [];
  const bulkOps = [];

  for (const item of cleanAnswers) {
    const vocab = vocabMap.get(item.vocabularyId);
    if (!vocab) continue;

    let expectedAnswer;
    let isCorrect;
    if (quizType === "en_to_bn" || quizType === "listening") {
      expectedAnswer = vocab.meaning;
      isCorrect = item.selectedAnswer.toLowerCase() === vocab.meaning.trim().toLowerCase();
    } else if (quizType === "word_match") {
      expectedAnswer = vocab.meaning;
      const submitted = item.matchedMeaning ?? item.selectedAnswer;
      isCorrect = submitted.toLowerCase() === vocab.meaning.trim().toLowerCase();
    } else {
      expectedAnswer = vocab.word;
      isCorrect = item.selectedAnswer.toLowerCase() === vocab.word.trim().toLowerCase();
    }

    if (isCorrect) correctCount += 1;
    else wrongCount += 1;

    const quality = isCorrect ? 4 : 1;
    const prev = progressMap.get(item.vocabularyId);
    const nextSrs = computeNextSrsState(prev, quality, now);

    bulkOps.push({
      updateOne: {
        filter: { userId, vocabularyId: vocab._id },
        update: {
          $set: nextSrs,
          $setOnInsert: { userId, vocabularyId: vocab._id, isFavorite: false },
        },
        upsert: true,
      },
    });

    results.push({
      vocabularyId: String(vocab._id),
      word: vocab.word,
      meaning: vocab.meaning,
      userAnswer: item.selectedAnswer || item.matchedMeaning || "",
      correctAnswer: expectedAnswer,
      isCorrect,
    });
  }

  const totalQuestions = results.length;
  if (totalQuestions === 0) {
    throw new AppError(400, "VALIDATION_ERROR", "No matching vocabulary words found.");
  }

  const score = Math.round((correctCount / totalQuestions) * 100);
  const safeTime = Math.max(0, Math.min(7200, Math.round(Number(timeTaken) || 0)));

  const settings = await getOrCreateVocabSettings(userId);
  const today = getLocalDateString(settings.timezone, now);

  const [attempt] = await Promise.all([
    VocabularyQuizAttempt.create({
      userId,
      quizType,
      vocabularyIds: results.map((r) => r.vocabularyId),
      totalQuestions,
      correctAnswers: correctCount,
      wrongAnswers: wrongCount,
      score,
      timeTaken: safeTime,
      completedAt: now,
    }),
    bulkOps.length ? UserVocabularyProgress.bulkWrite(bulkOps) : Promise.resolve(),
    VocabularyDailyActivity.updateOne(
      { userId, date: today },
      {
        $setOnInsert: {
          userId,
          date: today,
          timezone: settings.timezone,
          dailyGoal: Math.min(5, settings.dailyGoal || 5),
        },
        $inc: {
          quizCount: 1,
          reviewedWords: totalQuestions,
          correctAnswers: correctCount,
          wrongAnswers: wrongCount,
        },
      },
      { upsert: true }
    ),
  ]);

  return {
    attemptId: String(attempt._id),
    quizType,
    totalQuestions,
    correctAnswers: correctCount,
    wrongAnswers: wrongCount,
    score,
    timeTaken: safeTime,
    results,
  };
}
