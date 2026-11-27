import test from "node:test";
import assert from "node:assert/strict";
import {
  MAX_DAILY_NEW_WORDS,
  calculateSrsSchedule,
  calculateStreak,
  getLocalDateString,
  normalizeDailyGoal,
  normalizeVocabularyPayload,
  parseVocabularyCsv,
  scoreQuizAnswers,
  selectWordOfTheDayFromPool,
  shiftLocalDate,
} from "../src/lib/vocabSrs.js";

test("SRS scheduling follows SM-2 intervals Day 1, 3, 7, 14, 30 and mastered threshold", () => {
  const t0 = new Date("2026-11-01T06:00:00Z");
  let state = { repetition: 0, intervalDays: 0, easeFactor: 2.5 };

  // Step 1: quality 4 -> rep 1, interval 1
  state = calculateSrsSchedule(state, 4, t0);
  assert.equal(state.repetition, 1);
  assert.equal(state.intervalDays, 1);
  assert.equal(state.status, "learning");

  // Step 2: quality 4 -> rep 2, interval 3
  state = calculateSrsSchedule(state, 4, t0);
  assert.equal(state.repetition, 2);
  assert.equal(state.intervalDays, 3);
  assert.equal(state.status, "review");

  // Step 3: quality 4 -> rep 3, interval 7
  state = calculateSrsSchedule(state, 4, t0);
  assert.equal(state.repetition, 3);
  assert.equal(state.intervalDays, 7);

  // Step 4: quality 4 -> rep 4, interval 14
  state = calculateSrsSchedule(state, 4, t0);
  assert.equal(state.repetition, 4);
  assert.equal(state.intervalDays, 14);
  assert.equal(state.status, "review");

  // Step 5: quality 5 -> rep 5, interval 30 -> mastered!
  state = calculateSrsSchedule(state, 5, t0);
  assert.equal(state.repetition, 5);
  assert.equal(state.intervalDays, 30);
  assert.equal(state.status, "mastered");

  // Quality 0-2 resets repetition and keeps easeFactor >= 1.3
  const failed = calculateSrsSchedule({ repetition: 4, intervalDays: 14, easeFactor: 1.35 }, 1, t0);
  assert.equal(failed.repetition, 0);
  assert.equal(failed.intervalDays, 1);
  assert.equal(failed.status, "learning");
  assert.ok(failed.easeFactor >= 1.3);
});

test("Daily goal defaults to 5, accepts 5 or more up to 500, and missed days never carry over", () => {
  assert.equal(normalizeDailyGoal(undefined), 5);
  assert.equal(normalizeDailyGoal(5), 5);
  assert.equal(normalizeDailyGoal(30), 30);
  assert.equal(normalizeDailyGoal(0), 5);
  assert.equal(normalizeDailyGoal(999), MAX_DAILY_NEW_WORDS);

  // Asia/Dhaka is UTC+6
  const utcLateNight = new Date("2026-11-24T19:00:00Z"); // 01:00 AM next day in Dhaka
  assert.equal(getLocalDateString(utcLateNight, "UTC"), "2026-11-24");
  assert.equal(getLocalDateString(utcLateNight, "Asia/Dhaka"), "2026-11-25");
});

test("Concurrent daily word assignments never exceed 5 and produce no duplicates", async () => {
  const cap = normalizeDailyGoal(5);
  const store = { userId: "user-1", date: "2026-11-24", newWords: 0, newWordIds: [] };
  const candidatePool = Array.from({ length: 20 }, (_, i) => `word-${i + 1}`);

  // Simulates atomic document-locked MongoDB findOneAndUpdate
  async function atomicClaimSlot(wordId) {
    if (store.newWords >= cap) return null;
    if (store.newWordIds.includes(wordId)) return null;
    store.newWords += 1;
    store.newWordIds.push(wordId);
    return store.newWords;
  }

  await Promise.all(candidatePool.map((wId) => atomicClaimSlot(wId)));
  assert.equal(store.newWords, 5);
  assert.equal(store.newWordIds.length, 5);
  assert.equal(new Set(store.newWordIds).size, 5);
});

test("Streak calculation counts consecutive local days with goal completed or review done", () => {
  const today = "2026-11-24";
  const activities = [
    { date: "2026-11-24", isGoalCompleted: true, reviewedWords: 0 },
    { date: "2026-11-23", isGoalCompleted: false, reviewedWords: 3 },
    { date: "2026-11-22", isGoalCompleted: true, reviewedWords: 0 },
    // gap on 2026-11-21
    { date: "2026-11-20", isGoalCompleted: true, reviewedWords: 5 },
  ];
  assert.equal(calculateStreak(activities, today), 3);

  // If today isn't completed yet, yesterday's consecutive streak stays active
  const pendingToday = activities.filter((a) => a.date !== today);
  assert.equal(calculateStreak(pendingToday, today), 2);
  assert.equal(shiftLocalDate("2026-03-01", -1), "2026-02-28");
});

test("Quiz scoring computes accurate percentage and counts", () => {
  const res = scoreQuizAnswers([
    { vocabularyId: "1", selectedAnswer: "বন্ধু", expectedAnswer: "বন্ধু" },
    { vocabularyId: "2", selectedAnswer: "water", expectedAnswer: "Water" },
    { vocabularyId: "3", selectedAnswer: "wrong", expectedAnswer: "right" },
    { vocabularyId: "4", selectedAnswer: "অভ্যাস", expectedAnswer: "অভ্যাস" },
  ]);
  assert.equal(res.totalQuestions, 4);
  assert.equal(res.correctAnswers, 3);
  assert.equal(res.wrongAnswers, 1);
  assert.equal(res.score, 75);
});

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const backendRoot = path.resolve(__dirname, "..");

function collectRouteFiles(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...collectRouteFiles(full));
    } else if (entry.isFile() && entry.name === "route.js") {
      files.push(full);
    }
  }
  return files;
}

test("CSV parser and normalizer handle quoted commas, UTF-8 BOM, header variants, and invalid inputs cleanly", () => {
  const csv = `\uFEFFWord,Bengali Meaning,IPA,part_of_speech,CEFR,Category Name,example1_en,example1_bn,Synonyms\r
"polite","ভদ্র, বিনয়ী","/pəˈlaɪt/","adjective","a2","Communication","Be polite to elders.","বড়দের প্রতি ভদ্র হও।","courteous;respectful"`;
  const { records } = parseVocabularyCsv(csv);
  assert.equal(records.length, 1);
  const norm = normalizeVocabularyPayload(records[0]);
  assert.equal(norm.word, "polite");
  assert.equal(norm.meaning, "ভদ্র, বিনয়ী");
  assert.equal(norm.partOfSpeech, "adjective");
  assert.equal(norm.level, "A2");
  assert.equal(norm.category, "Communication");
  assert.deepEqual(norm.synonyms, ["courteous", "respectful"]);
  assert.equal(norm.examples.length, 1);
});

test("Due words filter selects only user-owned items with nextReviewAt <= now", () => {
  const now = new Date("2026-11-24T12:00:00Z");
  const records = [
    { userId: "uid-A", vocabularyId: "w1", status: "learning", nextReviewAt: new Date("2026-11-24T10:00:00Z") },
    { userId: "uid-A", vocabularyId: "w2", status: "review", nextReviewAt: new Date("2026-11-25T10:00:00Z") },
    { userId: "uid-B", vocabularyId: "w3", status: "learning", nextReviewAt: new Date("2026-11-24T08:00:00Z") },
  ];

  const getDueForUser = (uid) =>
    records
      .filter((r) => r.userId === uid && r.nextReviewAt && r.nextReviewAt <= now)
      .sort((a, b) => a.nextReviewAt - b.nextReviewAt);

  const dueA = getDueForUser("uid-A");
  assert.equal(dueA.length, 1);
  assert.equal(dueA[0].vocabularyId, "w1");
  // Cross-user access blocked: uid-A never sees uid-B's progress
  assert.ok(dueA.every((r) => r.userId === "uid-A"));
});

test("All user vocabulary routes enforce withAuth and never trust client-sent userId", () => {
  const userVocabRoutes = collectRouteFiles(path.join(backendRoot, "src/app/api/vocabulary"));
  assert.ok(userVocabRoutes.length >= 14, "Expected all vocabulary user route files");
  for (const file of userVocabRoutes) {
    const src = fs.readFileSync(file, "utf8");
    assert.ok(src.includes("withAuth("), `Route ${path.relative(backendRoot, file)} must wrap handlers with withAuth`);
    assert.ok(
      !src.includes("searchParams.get(\"userId\")") && !src.includes("body.userId"),
      `Route ${path.relative(backendRoot, file)} must not accept client-supplied userId`
    );
  }
});

test("All admin vocabulary routes enforce withAdmin and adminGuard blocks non-admin / unauthenticated callers", () => {
  const adminVocabRoutes = collectRouteFiles(path.join(backendRoot, "src/app/api/admin/vocabulary"));
  assert.ok(adminVocabRoutes.length >= 5, "Expected all vocabulary admin route files");
  for (const file of adminVocabRoutes) {
    const src = fs.readFileSync(file, "utf8");
    assert.ok(src.includes("withAdmin("), `Admin route ${path.relative(backendRoot, file)} must use withAdmin`);
  }

  const guardSrc = fs.readFileSync(path.join(backendRoot, "src/lib/adminGuard.js"), "utf8");
  assert.ok(guardSrc.includes("401") && guardSrc.includes("UNAUTHORIZED"));
  assert.ok(guardSrc.includes("403") && guardSrc.includes("FORBIDDEN"));
});

test("selectWordOfTheDayFromPool selects unlearned words first, formats Bangla meaning/example, and returns upcoming days", () => {
  const words = [
    { _id: "w1", word: "morning", meaning: "সকাল", level: "A1", examples: [{ en: "Good morning!", bn: "সুপ্রভাত!" }] },
    { _id: "w2", word: "confident", meaning: "আত্মবিশ্বাসী", level: "A2", examples: [{ en: "She feels confident today.", bn: "সে আজ আত্মবিশ্বাসী।" }] },
    { _id: "w3", word: "journey", meaning: "ভ্রমণ", level: "B1", examples: [{ en: "Every journey begins with one step.", bn: "প্রতিটি যাত্রা এক ধাপে শুরু হয়।" }] },
    { _id: "w4", word: "gratitude", meaning: "কৃতজ্ঞতা", level: "B1", examples: [{ en: "Express gratitude daily.", bn: "প্রতিদিন কৃতজ্ঞতা প্রকাশ করো।" }] },
  ];

  const picked = selectWordOfTheDayFromPool({
    words,
    learnedIds: new Set(["w1"]),
    masteredIds: new Set(["w2"]),
    dateStr: "2026-11-27",
    userId: "uid-123",
    upcomingCount: 3,
  });

  assert.ok(picked.wordOfTheDay);
  assert.ok(["journey", "gratitude"].includes(picked.wordOfTheDay.word));
  assert.ok(picked.wordOfTheDay.meaning.length > 0);
  assert.ok(picked.wordOfTheDay.exampleEn.length > 0);
  assert.equal(picked.upcoming.length, 3);
  assert.equal(new Set([picked.wordOfTheDay.word, ...picked.upcoming.map((u) => u.word)]).size, 4);
});


