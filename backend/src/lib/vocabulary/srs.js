import { AppError } from "@/lib/errors";

// Initial schedule milestones: Day 0 (learn) -> 1 -> 3 -> 7 -> 14 -> 30 -> SM-2 factor
const INITIAL_INTERVALS = [1, 3, 7, 14, 30];
const MS_PER_DAY = 24 * 60 * 60 * 1000;

/**
 * Computes the next SM-2-inspired review state.
 * quality:
 *  0-2: Incorrect or difficult recall -> resets repetition to 0, schedules immediate/Day 1 review
 *  3:   Correct with difficulty
 *  4:   Correct
 *  5:   Easy recall
 */
export function computeNextSrsState(existing, quality, now = new Date()) {
  const q = Number(quality);
  if (!Number.isInteger(q) || q < 0 || q > 5) {
    throw new AppError(400, "VALIDATION_ERROR", "Review quality must be an integer between 0 and 5.");
  }

  const prevRep = existing?.repetition ?? 0;
  const prevEf = existing?.easeFactor ?? 2.5;
  const prevInterval = existing?.intervalDays ?? 0;
  const prevCorrect = existing?.correctCount ?? 0;
  const prevWrong = existing?.wrongCount ?? 0;
  const prevReviews = existing?.reviewCount ?? 0;

  // SM-2 ease factor update clamped at 1.3
  const nextEfRaw = prevEf + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02));
  const easeFactor = Number(Math.max(1.3, nextEfRaw).toFixed(2));

  let repetition;
  let intervalDays;
  let nextReviewAt;
  let correctCount = prevCorrect;
  let wrongCount = prevWrong;
  let status;

  if (q < 3) {
    // Incorrect or difficult recall: reset repetition streak & queue for prompt review
    repetition = 0;
    intervalDays = 1;
    wrongCount += 1;
    status = "learning";
    // Schedule immediately so the missed word surfaces in "Due Today" and "Difficult Words"
    nextReviewAt = new Date(now.getTime());
  } else {
    repetition = prevRep + 1;
    correctCount += 1;
    if (repetition <= INITIAL_INTERVALS.length) {
      intervalDays = INITIAL_INTERVALS[repetition - 1];
    } else {
      intervalDays = Math.max(30, Math.round(prevInterval * easeFactor));
    }
    nextReviewAt = new Date(now.getTime() + intervalDays * MS_PER_DAY);

    // Mastered requires repeated successful reviews AND a sufficiently long interval (>= 14 days)
    const strongMastery = repetition >= 4 && intervalDays >= 14 && q >= 4;
    const fullCycleMastery = repetition >= 5 && intervalDays >= 30;
    status = strongMastery || fullCycleMastery ? "mastered" : "reviewing";
  }

  return {
    status,
    correctCount,
    wrongCount,
    reviewCount: prevReviews + 1,
    intervalDays,
    easeFactor,
    repetition,
    lastQuality: q,
    lastReviewedAt: now,
    nextReviewAt,
    learnedAt: existing?.learnedAt || now,
  };
}
