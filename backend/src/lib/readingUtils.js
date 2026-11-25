import crypto from "node:crypto";

export const READING_CEFR_LEVELS = ["A1", "A2", "B1", "B2", "C1", "C2"];
export const READING_SPEED_OPTIONS = [0.75, 1.0, 1.25, 1.5];
export const READING_ACCENTS = ["en-BD", "en-GB", "en-US"];

const ABBREV_MARKER = "\u0000DOT\u0000";
const ABBREV_REGEX = /\b(a\.m|p\.m|e\.g|i\.e|U\.S|U\.K|Mr|Mrs|Ms|Dr|Prof|Sr|Jr|St|vs|etc)\./gi;

export function slugifyReadingText(value = "") {
  return String(value || "")
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 72) || `reading-${Date.now()}`;
}

export function normalizeSpeed(raw) {
  const n = Number(raw);
  if (!Number.isFinite(n)) return 1.0;
  let best = 1.0;
  let minDiff = Infinity;
  for (const opt of READING_SPEED_OPTIONS) {
    const diff = Math.abs(opt - n);
    if (diff < minDiff) {
      minDiff = diff;
      best = opt;
    }
  }
  return best;
}

export function normalizeAccent(raw) {
  const s = String(raw || "").trim();
  const lower = s.toLowerCase();
  if (lower === "en-bd" || lower === "en_bd" || lower === "bd" || lower === "bangladeshi") {
    return "en-BD";
  }
  if (lower === "en-gb" || lower === "en_gb" || lower === "gb" || lower === "uk" || lower === "british") {
    return "en-GB";
  }
  return "en-US";
}

export function normalizeReadingGoalMinutes(raw) {
  const n = Math.round(Number(raw));
  if (!Number.isFinite(n)) return 15;
  return Math.max(5, Math.min(120, n));
}

export function isValidOptionalAudioUrl(raw) {
  const s = String(raw || "").trim();
  if (!s) return true;
  try {
    const u = new URL(s);
    return u.protocol === "https:" || u.protocol === "http:";
  } catch {
    return false;
  }
}

export function splitTextIntoSentences(englishText, existingSentences = []) {
  const cleaned = String(englishText || "").replace(/\r\n/g, "\n").trim();
  if (!cleaned) return [];

  const existingByText = new Map();
  const usedIds = new Set();
  if (Array.isArray(existingSentences)) {
    for (const s of existingSentences) {
      const textKey = String(s?.en || "").trim().toLowerCase();
      if (textKey && s?.sentenceId) {
        existingByText.set(textKey, {
          sentenceId: String(s.sentenceId).trim(),
          bn: String(s.bn || "").trim(),
        });
      }
    }
  }

  const lines = cleaned.split(/\n+/).map((l) => l.trim()).filter(Boolean);
  const rawSentences = [];

  for (const line of lines) {
    const protectedText = line
      .replace(ABBREV_REGEX, (m) => m.replace(/\./g, ABBREV_MARKER))
      .replace(/(\d)\.(\d)/g, `$1${ABBREV_MARKER}$2`);

    const matches = protectedText.match(/[^.!?]+(?:[.!?]+["'”’)]*)?|[^.!?]+$/g) || [];
    for (const part of matches) {
      const restored = part.replaceAll(ABBREV_MARKER, ".").trim();
      if (restored) rawSentences.push(restored);
    }
  }

  return rawSentences.map((en, idx) => {
    const key = en.toLowerCase();
    const matched = existingByText.get(key);
    let candidateId = matched?.sentenceId || "";
    if (!candidateId || usedIds.has(candidateId)) {
      const hash = crypto.createHash("sha1").update(`${idx}:${key}`).digest("hex").slice(0, 6);
      candidateId = `s_${idx + 1}_${hash}`;
    }
    usedIds.add(candidateId);
    const existingBn =
      Array.isArray(existingSentences) && existingSentences[idx]?.en?.trim().toLowerCase() === key
        ? String(existingSentences[idx]?.bn || "").trim()
        : matched?.bn || "";
    return {
      sentenceId: candidateId,
      order: idx,
      en,
      bn: existingBn,
    };
  });
}

function normalizeAnswerText(str) {
  return String(str || "")
    .trim()
    .toLowerCase()
    .replace(/[.,!?;:"'()]+/g, "")
    .replace(/\s+/g, " ");
}

export function evaluateComprehensionAnswers(questions = [], submittedAnswers = []) {
  const answerMap = new Map();
  if (Array.isArray(submittedAnswers)) {
    for (const item of submittedAnswers) {
      const qid = String(item?.questionId || "").trim();
      if (qid) answerMap.set(qid, String(item?.answer ?? item?.userAnswer ?? "").trim());
    }
  }

  let correctCount = 0;
  const results = questions.map((q) => {
    const qid = String(q.questionId || "");
    const userAnswer = answerMap.get(qid) || "";
    const normUser = normalizeAnswerText(userAnswer);

    let isCorrect = false;
    if (q.type === "short_answer") {
      const accepted = String(q.correctAnswer || "")
        .split("|")
        .map((a) => normalizeAnswerText(a))
        .filter(Boolean);
      isCorrect = Boolean(normUser) && accepted.some((acc) => acc === normUser);
    } else {
      isCorrect = Boolean(normUser) && normUser === normalizeAnswerText(q.correctAnswer);
    }

    if (isCorrect) correctCount += 1;
    return {
      questionId: qid,
      userAnswer,
      isCorrect,
      correctAnswer: String(q.correctAnswer || ""),
      explanationBn: String(q.explanationBn || ""),
    };
  });

  const totalCount = questions.length;
  const scorePercent = totalCount > 0 ? Math.round((correctCount / totalCount) * 100) : 0;
  return {
    correctCount,
    totalCount,
    scorePercent,
    answers: results,
  };
}
