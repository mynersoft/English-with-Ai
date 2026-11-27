import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  buildEnglishWordCandidates,
  evaluateComprehensionAnswers,
  isValidOptionalAudioUrl,
  normalizeAccent,
  normalizeReadingGoalMinutes,
  normalizeSpeed,
  splitTextIntoSentences,
} from "../src/lib/readingUtils.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

test("splitTextIntoSentences creates stable IDs and preserves abbreviations", () => {
  const text = "Mr. Karim wakes up early. He drinks tea at 7.30 a.m.! Does he read books?";
  const first = splitTextIntoSentences(text);
  assert.equal(first.length, 3);
  assert.equal(first[0].en, "Mr. Karim wakes up early.");
  assert.equal(first[1].en, "He drinks tea at 7.30 a.m.!");
  assert.equal(first[2].en, "Does he read books?");

  // Preserves existing stable IDs and Bengali translations on re-save
  const withBn = first.map((s, i) => ({ ...s, bn: `bn_${i}` }));
  const second = splitTextIntoSentences(
    "Mr. Karim wakes up early. A brand new sentence. Does he read books?",
    withBn
  );
  assert.equal(second[0].sentenceId, first[0].sentenceId);
  assert.equal(second[0].bn, "bn_0");
  assert.equal(second[2].sentenceId, first[2].sentenceId);
});

test("evaluateComprehensionAnswers grades MCQ, True/False, and Short Answer accurately", () => {
  const questions = [
    {
      questionId: "q1",
      type: "mcq",
      prompt: "Where does she work?",
      options: ["Hospital", "School", "Bank"],
      correctAnswer: "School",
      explanationBn: "তিনি একটি স্কুলে কাজ করেন।",
    },
    {
      questionId: "q2",
      type: "true_false",
      prompt: "She walks to work.",
      options: ["True", "False"],
      correctAnswer: "True",
      explanationBn: "তিনি হেঁটে স্কুলে যান।",
    },
    {
      questionId: "q3",
      type: "short_answer",
      prompt: "What time does class start?",
      options: [],
      correctAnswer: "9 am|9:00 am",
      explanationBn: "ক্লাস সকাল ৯টায় শুরু হয়।",
    },
  ];

  const evalRes = evaluateComprehensionAnswers(questions, [
    { questionId: "q1", answer: "school" },
    { questionId: "q2", answer: "False" },
    { questionId: "q3", answer: "9:00 AM!" },
  ]);

  assert.equal(evalRes.totalCount, 3);
  assert.equal(evalRes.correctCount, 2);
  assert.equal(evalRes.scorePercent, 67);
  assert.equal(evalRes.answers[0].isCorrect, true);
  assert.equal(evalRes.answers[1].isCorrect, false);
  assert.equal(evalRes.answers[2].isCorrect, true);
});

test("Reading normalization and optional MP3 URL validation behave safely", () => {
  assert.equal(normalizeSpeed(0.8), 0.75);
  assert.equal(normalizeSpeed(1.4), 1.5);
  assert.equal(normalizeAccent("en-BD"), "en-BD");
  assert.equal(normalizeAccent("bangladeshi"), "en-BD");
  assert.equal(normalizeAccent("en-GB"), "en-GB");
  assert.equal(normalizeAccent("british"), "en-GB");
  assert.equal(normalizeAccent("fr-FR"), "en-US");
  assert.equal(normalizeReadingGoalMinutes(2), 5);
  assert.equal(normalizeReadingGoalMinutes(200), 120);
  assert.equal(isValidOptionalAudioUrl(""), true);
  assert.equal(isValidOptionalAudioUrl("https://cdn.example.com/audio.mp3"), true);
  assert.equal(isValidOptionalAudioUrl("javascript:alert(1)"), false);
});

test("All student & admin reading routes enforce withAuth / withAdmin and never trust client userId", () => {
  const studentRoutes = [
    "../src/app/api/v1/reading/dashboard/route.js",
    "../src/app/api/v1/reading/passages/route.js",
    "../src/app/api/v1/reading/passages/[id]/route.js",
    "../src/app/api/v1/reading/passages/[id]/progress/route.js",
    "../src/app/api/v1/reading/passages/[id]/attempt/route.js",
    "../src/app/api/v1/reading/word-lookup/route.js",
    "../src/app/api/v1/reading/vocabulary/save/route.js",
    "../src/app/api/v1/reading/settings/route.js",
  ];
  for (const rel of studentRoutes) {
    const src = fs.readFileSync(path.join(__dirname, rel), "utf8");
    assert.match(src, /withAuth/, `${rel} must use withAuth`);
    assert.ok(!/body\.userId|searchParams\.get\(["']userId/.test(src), `${rel} must not trust client userId`);
  }

  const adminRoutes = [
    "../src/app/api/v1/admin/reading/passages/route.js",
    "../src/app/api/v1/admin/reading/passages/[id]/route.js",
    "../src/app/api/v1/admin/reading/categories/route.js",
    "../src/app/api/v1/admin/reading/categories/[id]/route.js",
  ];
  for (const rel of adminRoutes) {
    const src = fs.readFileSync(path.join(__dirname, rel), "utf8");
    assert.match(src, /withAdmin/, `${rel} must use withAdmin`);
  }
});

test("buildEnglishWordCandidates resolves inflected, possessive, and irregular English words to base vocabulary forms", () => {
  assert.ok(buildEnglishWordCandidates("brushes").includes("brush"));
  assert.ok(buildEnglishWordCandidates("walked").includes("walk"));
  assert.ok(buildEnglishWordCandidates("stopped").includes("stop"));
  assert.ok(buildEnglishWordCandidates("writing").includes("write"));
  assert.ok(buildEnglishWordCandidates("studies").includes("study"));
  assert.ok(buildEnglishWordCandidates("friend's").includes("friend"));
  assert.ok(buildEnglishWordCandidates("went").includes("go"));
  assert.ok(buildEnglishWordCandidates("children").includes("child"));
  assert.equal(buildEnglishWordCandidates("walks")[0], "walks");
});

