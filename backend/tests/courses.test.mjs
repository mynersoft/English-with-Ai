import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import {
  COURSE_LEVELS,
  CONTENT_BLOCK_TYPES,
  buildSignedUploadParams,
  calculateCourseCompletionPercent,
  hasStudentPremiumAccess,
  isLessonLockedForStudent,
  isValidMediaUrl,
  normalizeContentBlocks,
  normalizeCourseLevel,
  resolvePrevNextLessons,
  sanitizeRichText,
  slugifyCourseText,
  validateMediaUpload,
} from "../src/lib/courseUtils.js";

test("normalizeCourseLevel handles case-insensitive input and fallback", () => {
  assert.deepEqual(COURSE_LEVELS, ["Beginner", "Intermediate", "Advanced"]);
  assert.equal(normalizeCourseLevel("beginner"), "Beginner");
  assert.equal(normalizeCourseLevel("INTERMEDIATE"), "Intermediate");
  assert.equal(normalizeCourseLevel("Advanced"), "Advanced");
  assert.equal(normalizeCourseLevel("unknown", "Intermediate"), "Intermediate");
});

test("slugifyCourseText normalizes titles safely", () => {
  assert.equal(slugifyCourseText("Business English: Speaking 101!"), "business-english-speaking-101");
  assert.equal(slugifyCourseText("   "), "course");
});

test("isValidMediaUrl validates http/https URLs", () => {
  assert.equal(isValidMediaUrl("https://res.cloudinary.com/demo/video/upload/v1/sample.mp4", { allowEmpty: false }), true);
  assert.equal(isValidMediaUrl("javascript:alert(1)", { allowEmpty: false }), false);
  assert.equal(isValidMediaUrl("", { allowEmpty: true }), true);
  assert.equal(isValidMediaUrl("", { allowEmpty: false }), false);
});

test("sanitizeRichText strips script/iframe/inline handlers while preserving formatted HTML & Markdown", () => {
  const dirty = `<h1>Lesson</h1><p><strong>Hello</strong> world</p><script>alert("xss")</script><a href="https://example.com" onclick="evil()">Link</a>`;
  const clean = sanitizeRichText(dirty);
  assert.equal(clean.includes("<script"), false);
  assert.equal(clean.includes("onclick"), false);
  assert.equal(clean.includes("<h1>Lesson</h1>"), true);
  assert.equal(clean.includes("<strong>Hello</strong>"), true);
});

test("normalizeContentBlocks validates types, enforces ordering, and generates block IDs", () => {
  assert.deepEqual(CONTENT_BLOCK_TYPES, ["video", "pdf", "text", "image"]);

  const blocks = normalizeContentBlocks([
    { type: "image", title: "Diagram", url: "https://cdn.example.com/a.png", alt: "Grammar chart", order: 3 },
    { type: "text", title: "Intro", text: "## Heading\n\n**Bold** text", order: 1 },
    { type: "video", title: "Lecture", url: "https://cdn.example.com/v.mp4", durationSeconds: 185.4, isDownloadable: true, order: 2 },
    { type: "pdf", title: "Slides", url: "https://cdn.example.com/s.pdf", isDownloadable: false, order: 4 },
  ]);

  assert.equal(blocks.length, 4);
  assert.deepEqual(blocks.map((b) => b.type), ["text", "video", "image", "pdf"]);
  assert.deepEqual(blocks.map((b) => b.order), [1, 2, 3, 4]);
  assert.equal(blocks[1].durationSeconds, 185);
  assert.equal(blocks[1].isDownloadable, true);
  assert.equal(blocks[2].isDownloadable, false);
  assert.equal(blocks[3].isDownloadable, false);
  assert.ok(blocks.every((b) => b.id.startsWith("blk_")));

  assert.throws(
    () => normalizeContentBlocks([{ type: "audio", url: "https://cdn.example.com/a.mp3" }]),
    /unsupported type/i
  );
  assert.throws(
    () => normalizeContentBlocks([{ type: "text", text: "" }]),
    /cannot be empty/i
  );
  assert.throws(
    () => normalizeContentBlocks([{ type: "video", url: "ftp://invalid.com/v.mp4" }]),
    /valid http\/https URL/i
  );
});

test("hasStudentPremiumAccess and isLessonLockedForStudent enforce access control", () => {
  const now = new Date("2026-11-27T12:00:00Z");
  const freeUser = { role: "student", subscriptionStatus: "free" };
  const expiredUser = { role: "student", subscriptionStatus: "active", subscriptionExpiresAt: "2026-10-01T00:00:00Z" };
  const activeUser = { role: "student", subscriptionStatus: "active", subscriptionExpiresAt: "2027-01-01T00:00:00Z" };
  const adminUser = { role: "admin", subscriptionStatus: "free" };

  assert.equal(hasStudentPremiumAccess(freeUser, now), false);
  assert.equal(hasStudentPremiumAccess(expiredUser, now), false);
  assert.equal(hasStudentPremiumAccess(activeUser, now), true);
  assert.equal(hasStudentPremiumAccess(adminUser, now), true);

  const premiumCourse = { isPremium: true };
  const freePreviewLesson = { isPremium: false };
  const paidLesson = { isPremium: true };

  assert.equal(isLessonLockedForStudent({ course: premiumCourse, lesson: freePreviewLesson, hasPremiumAccess: false }), false);
  assert.equal(isLessonLockedForStudent({ course: premiumCourse, lesson: paidLesson, hasPremiumAccess: false }), true);
  assert.equal(isLessonLockedForStudent({ course: premiumCourse, lesson: paidLesson, hasPremiumAccess: true }), false);
  assert.equal(isLessonLockedForStudent({ course: premiumCourse, lesson: paidLesson, hasPremiumAccess: false, hasPurchasedCourse: true }), false);
});

test("calculateCourseCompletionPercent and resolvePrevNextLessons compute accurately", () => {
  assert.equal(calculateCourseCompletionPercent(0, 0), 0);
  assert.equal(calculateCourseCompletionPercent(2, 5), 40);
  assert.equal(calculateCourseCompletionPercent(7, 5), 100);

  const lessons = [
    { _id: "l1", moduleId: "m1", title: "One", isPremium: false },
    { _id: "l2", moduleId: "m1", title: "Two", isPremium: true },
    { _id: "l3", moduleId: "m2", title: "Three", isPremium: false },
  ];

  const first = resolvePrevNextLessons(lessons, "l1");
  assert.equal(first.previousLesson, null);
  assert.equal(first.nextLesson.id, "l2");

  const middle = resolvePrevNextLessons(lessons, "l2");
  assert.equal(middle.previousLesson.id, "l1");
  assert.equal(middle.nextLesson.id, "l3");

  const last = resolvePrevNextLessons(lessons, "l3");
  assert.equal(last.previousLesson.id, "l2");
  assert.equal(last.nextLesson, null);
});

test("validateMediaUpload enforces file type and size limits, and signed upload params omit apiSecret", () => {
  assert.doesNotThrow(() =>
    validateMediaUpload({
      mediaType: "pdf",
      mimeType: "application/pdf",
      fileName: "worksheet.pdf",
      sizeBytes: 1024 * 500,
    })
  );
  assert.throws(
    () =>
      validateMediaUpload({
        mediaType: "image",
        mimeType: "application/pdf",
        fileName: "hack.pdf",
        sizeBytes: 1024,
      }),
    /Unsupported image file format/i
  );
  assert.throws(
    () =>
      validateMediaUpload({
        mediaType: "image",
        mimeType: "image/png",
        fileName: "huge.png",
        sizeBytes: 15 * 1024 * 1024,
      }),
    /exceeds the 10 MB limit/i
  );

  const signed = buildSignedUploadParams({
    mediaType: "video",
    cloudName: "demo-cloud",
    apiKey: "public-key-123",
    apiSecret: "top-secret-xyz",
    timestamp: 1700000000,
  });
  assert.equal(signed.cloudName, "demo-cloud");
  assert.equal(signed.apiKey, "public-key-123");
  assert.equal(signed.resourceType, "video");
  assert.ok(typeof signed.signature === "string" && signed.signature.length === 40);
  assert.equal(JSON.stringify(signed).includes("top-secret-xyz"), false);
});

test("all course/module/lesson admin and student routes enforce auth & never trust client userId", () => {
  const adminRoutes = [
    "backend/src/app/api/admin/courses/route.js",
    "backend/src/app/api/admin/courses/[id]/route.js",
    "backend/src/app/api/admin/courses/media/route.js",
    "backend/src/app/api/admin/modules/route.js",
    "backend/src/app/api/admin/modules/[id]/route.js",
    "backend/src/app/api/admin/lessons/route.js",
    "backend/src/app/api/admin/lessons/[id]/route.js",
    "backend/src/app/api/admin/lessons/[id]/duplicate/route.js",
  ];

  for (const rel of adminRoutes) {
    const src = fs.readFileSync(path.resolve(rel), "utf8");
    assert.match(src, /withAdmin\(/, `${rel} must guard every handler with withAdmin`);
  }

  const studentCoursesSrc = fs.readFileSync(path.resolve("backend/src/app/api/courses/route.js"), "utf8");
  const studentCourseDetailSrc = fs.readFileSync(path.resolve("backend/src/app/api/courses/[id]/route.js"), "utf8");
  const studentLessonSrc = fs.readFileSync(path.resolve("backend/src/app/api/lessons/[id]/route.js"), "utf8");
  const studentProgressSrc = fs.readFileSync(path.resolve("backend/src/app/api/lessons/[id]/progress/route.js"), "utf8");
  const serviceSrc = fs.readFileSync(path.resolve("backend/src/modules/courses/course.service.js"), "utf8");

  assert.match(studentCoursesSrc, /withAuth\(/);
  assert.match(studentCourseDetailSrc, /withAuth\(/);
  assert.match(studentLessonSrc, /withAuth\(/);
  assert.match(studentLessonSrc, /resolveAdminCaller\(/);
  assert.match(studentProgressSrc, /withAuth\(/);
  assert.equal(serviceSrc.includes("body.userId"), false);
  assert.match(serviceSrc, /userId:\s*userDoc\.firebaseUid/);
});
