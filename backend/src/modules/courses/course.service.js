import mongoose from "mongoose";
import { AppError } from "@/lib/errors";
import { deleteCourseMedia } from "@/lib/cloudinaryMedia";
import {
  calculateCourseCompletionPercent,
  generateBlockId,
  hasStudentPremiumAccess,
  isLessonLockedForStudent,
  isValidMediaUrl,
  normalizeContentBlocks,
  normalizeCourseLevel,
  resolvePrevNextLessons,
  slugifyCourseText,
} from "@/lib/courseUtils";
import Course from "@/models/Course";
import Module from "@/models/Module";
import Lesson from "@/models/Lesson";
import LessonProgress from "@/models/LessonProgress";

export function assertCourseObjectId(id, label = "ID") {
  if (!id || !mongoose.Types.ObjectId.isValid(String(id))) {
    throw new AppError(400, "INVALID_ID", `Invalid ${label}.`);
  }
  return new mongoose.Types.ObjectId(String(id));
}

function parseOptionalIsoDate(raw) {
  if (!raw) return null;
  const d = new Date(String(raw));
  if (Number.isNaN(d.getTime())) {
    throw new AppError(400, "INVALID_DATE", "updatedAfter must be a valid ISO-8601 timestamp.");
  }
  return d;
}

function formatCourseDoc(doc, overrides = {}) {
  return {
    id: String(doc._id),
    title: doc.title,
    slug: doc.slug,
    subtitle: doc.subtitle || "",
    description: doc.description || "",
    thumbnailUrl: doc.thumbnailUrl || "",
    thumbnailPublicId: doc.thumbnailPublicId || "",
    level: doc.level || "Beginner",
    category: doc.category || "General",
    durationDays: Number(doc.durationDays || 30),
    isPremium: Boolean(doc.isPremium),
    price: Number(doc.price || 0),
    isPublished: Boolean(doc.isPublished),
    totalModules: Number(doc.totalModules || 0),
    totalLessons: Number(doc.totalLessons || 0),
    createdAt: doc.createdAt ? new Date(doc.createdAt).toISOString() : null,
    updatedAt: doc.updatedAt ? new Date(doc.updatedAt).toISOString() : null,
    ...overrides,
  };
}

function formatModuleDoc(doc, overrides = {}) {
  return {
    id: String(doc._id),
    courseId: String(doc.courseId),
    title: doc.title,
    description: doc.description || "",
    order: Number(doc.order || 1),
    isPublished: Boolean(doc.isPublished),
    createdAt: doc.createdAt ? new Date(doc.createdAt).toISOString() : null,
    updatedAt: doc.updatedAt ? new Date(doc.updatedAt).toISOString() : null,
    ...overrides,
  };
}

function formatContentBlock(block) {
  return {
    id: String(block.id || ""),
    type: block.type,
    title: block.title || "",
    description: block.description || "",
    url: block.url || "",
    publicId: block.publicId || "",
    text: block.text || "",
    alt: block.alt || "",
    durationSeconds: Number(block.durationSeconds || 0),
    order: Number(block.order || 1),
    isDownloadable: Boolean(block.isDownloadable),
  };
}

function formatProgressDoc(doc) {
  if (!doc) return null;
  return {
    id: String(doc._id),
    userId: String(doc.userId),
    courseId: String(doc.courseId),
    moduleId: String(doc.moduleId),
    lessonId: String(doc.lessonId),
    isCompleted: Boolean(doc.isCompleted),
    progressPercentage: Number(doc.progressPercentage || 0),
    lastContentId: doc.lastContentId || "",
    lastPositionSeconds: Number(doc.lastPositionSeconds || 0),
    startedAt: doc.startedAt ? new Date(doc.startedAt).toISOString() : null,
    completedAt: doc.completedAt ? new Date(doc.completedAt).toISOString() : null,
    updatedAt: doc.updatedAt ? new Date(doc.updatedAt).toISOString() : null,
  };
}

function formatLessonDoc(doc, { includeContent = true, progressDoc = null, isLocked = false } = {}) {
  const blocks = Array.isArray(doc.content)
    ? [...doc.content].sort((a, b) => Number(a.order || 0) - Number(b.order || 0))
    : [];

  return {
    id: String(doc._id),
    courseId: String(doc.courseId),
    moduleId: String(doc.moduleId),
    title: doc.title,
    subtitle: doc.subtitle || "",
    description: doc.description || "",
    order: Number(doc.order || 1),
    estimatedDurationMinutes: Number(doc.estimatedDurationMinutes || 10),
    isPremium: Boolean(doc.isPremium),
    isPublished: Boolean(doc.isPublished),
    isLocked: Boolean(isLocked),
    contentCount: blocks.length,
    contentTypes: Array.from(new Set(blocks.map((b) => b.type))),
    content: includeContent && !isLocked ? blocks.map(formatContentBlock) : [],
    progress: formatProgressDoc(progressDoc),
    createdAt: doc.createdAt ? new Date(doc.createdAt).toISOString() : null,
    updatedAt: doc.updatedAt ? new Date(doc.updatedAt).toISOString() : null,
  };
}

async function ensureUniqueCourseSlug(baseTitle, excludeCourseId = null) {
  const base = slugifyCourseText(baseTitle);
  let candidate = base;
  let counter = 1;
  while (true) {
    const query = { slug: candidate };
    if (excludeCourseId) {
      query._id = { $ne: excludeCourseId };
    }
    const existing = await Course.exists(query);
    if (!existing) return candidate;
    counter += 1;
    candidate = `${base}-${counter}`;
  }
}

export async function recalculateCourseCounts(courseId) {
  const oid = assertCourseObjectId(courseId, "courseId");
  const [totalModules, totalLessons] = await Promise.all([
    Module.countDocuments({ courseId: oid }),
    Lesson.countDocuments({ courseId: oid }),
  ]);
  await Course.updateOne(
    { _id: oid },
    { $set: { totalModules, totalLessons } }
  );
  return { totalModules, totalLessons };
}

async function cleanupUnreferencedLessonMedia(lessons = [], excludeLessonIds = []) {
  const mediaEntries = [];
  for (const lesson of lessons) {
    for (const block of lesson.content || []) {
      if (block?.publicId && (block.type === "image" || block.type === "video" || block.type === "pdf")) {
        mediaEntries.push({ publicId: block.publicId, type: block.type });
      }
    }
  }
  if (!mediaEntries.length) return;

  const uniquePublicIds = Array.from(new Set(mediaEntries.map((m) => m.publicId)));
  const remainingQuery = {
    "content.publicId": { $in: uniquePublicIds },
  };
  if (excludeLessonIds.length) {
    remainingQuery._id = { $nin: excludeLessonIds };
  }
  const stillReferenced = await Lesson.find(remainingQuery).select("content.publicId").lean();
  const referencedSet = new Set();
  for (const l of stillReferenced) {
    for (const b of l.content || []) {
      if (b?.publicId) referencedSet.add(b.publicId);
    }
  }

  const deletions = [];
  const seen = new Set();
  for (const item of mediaEntries) {
    if (seen.has(item.publicId) || referencedSet.has(item.publicId)) continue;
    seen.add(item.publicId);
    deletions.push(deleteCourseMedia(item.publicId, item.type));
  }
  await Promise.allSettled(deletions);
}

async function cleanupUnreferencedThumbnail(publicId, excludeCourseId = null) {
  const clean = String(publicId || "").trim();
  if (!clean) return;
  const query = { thumbnailPublicId: clean };
  if (excludeCourseId) {
    query._id = { $ne: excludeCourseId };
  }
  const stillUsed = await Course.exists(query);
  if (!stillUsed) {
    await deleteCourseMedia(clean, "image");
  }
}

/* ============================================================================
 * ADMIN: COURSES
 * ========================================================================== */

export async function adminListCourses({
  page = 1,
  limit = 20,
  level = "ALL",
  category = "",
  published = "all",
  search = "",
} = {}) {
  const safePage = Math.max(1, Number(page) || 1);
  const safeLimit = Math.min(50, Math.max(1, Number(limit) || 20));
  const filter = {};

  if (level && String(level).toUpperCase() !== "ALL") {
    filter.level = normalizeCourseLevel(level);
  }
  if (category && String(category).trim()) {
    filter.category = String(category).trim();
  }
  if (published === "true" || published === "published") {
    filter.isPublished = true;
  } else if (published === "false" || published === "draft") {
    filter.isPublished = false;
  }
  if (search && String(search).trim()) {
    const q = String(search).trim().replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    filter.$or = [
      { title: { $regex: q, $options: "i" } },
      { subtitle: { $regex: q, $options: "i" } },
      { category: { $regex: q, $options: "i" } },
    ];
  }

  const [docs, total, categories] = await Promise.all([
    Course.find(filter)
      .sort({ updatedAt: -1, createdAt: -1 })
      .skip((safePage - 1) * safeLimit)
      .limit(safeLimit)
      .lean(),
    Course.countDocuments(filter),
    Course.distinct("category"),
  ]);

  return {
    courses: docs.map((d) => formatCourseDoc(d)),
    categories: categories.filter(Boolean).sort(),
    pagination: {
      page: safePage,
      limit: safeLimit,
      total,
      totalPages: Math.max(1, Math.ceil(total / safeLimit)),
    },
  };
}

export async function adminCreateCourse({ adminUid, body = {} }) {
  const title = String(body.title || "").trim();
  if (title.length < 2 || title.length > 160) {
    throw new AppError(400, "VALIDATION_ERROR", "Course title must be between 2 and 160 characters.");
  }

  const thumbnailUrl = String(body.thumbnailUrl || "").trim();
  if (!isValidMediaUrl(thumbnailUrl, { allowEmpty: true })) {
    throw new AppError(400, "INVALID_MEDIA_URL", "thumbnailUrl must be a valid http/https URL.");
  }

  const isPremium = Boolean(body.isPremium);
  const price = isPremium ? Math.max(0, Number(body.price || 0) || 0) : 0;
  const durationDays = Math.min(365, Math.max(1, Math.round(Number(body.durationDays || 30) || 30)));
  const slug = await ensureUniqueCourseSlug(body.slug || title);

  const created = await Course.create({
    title,
    slug,
    subtitle: String(body.subtitle || "").trim().slice(0, 240),
    description: String(body.description || "").trim().slice(0, 4000),
    thumbnailUrl,
    thumbnailPublicId: String(body.thumbnailPublicId || "").trim().slice(0, 300),
    level: normalizeCourseLevel(body.level, "Beginner"),
    category: String(body.category || "General").trim().slice(0, 80) || "General",
    durationDays,
    isPremium,
    price,
    isPublished: Boolean(body.isPublished),
    totalModules: 0,
    totalLessons: 0,
    createdByUid: adminUid || null,
  });

  return { course: formatCourseDoc(created) };
}

export async function adminGetCourseDetail(courseId) {
  const oid = assertCourseObjectId(courseId, "courseId");
  const course = await Course.findById(oid).lean();
  if (!course) {
    throw new AppError(404, "COURSE_NOT_FOUND", "Course not found.");
  }

  const [modules, lessons] = await Promise.all([
    Module.find({ courseId: oid }).sort({ order: 1, createdAt: 1 }).lean(),
    Lesson.find({ courseId: oid }).sort({ order: 1, createdAt: 1 }).lean(),
  ]);

  const lessonsByModule = new Map();
  for (const lesson of lessons) {
    const key = String(lesson.moduleId);
    if (!lessonsByModule.has(key)) lessonsByModule.set(key, []);
    lessonsByModule.get(key).push(formatLessonDoc(lesson, { includeContent: false }));
  }

  const formattedModules = modules.map((mod) => {
    const modLessons = lessonsByModule.get(String(mod._id)) || [];
    return formatModuleDoc(mod, {
      lessonCount: modLessons.length,
      lessons: modLessons,
    });
  });

  return {
    course: formatCourseDoc(course, {
      totalModules: modules.length,
      totalLessons: lessons.length,
    }),
    modules: formattedModules,
  };
}

export async function adminUpdateCourse({ courseId, body = {} }) {
  const oid = assertCourseObjectId(courseId, "courseId");
  const existing = await Course.findById(oid);
  if (!existing) {
    throw new AppError(404, "COURSE_NOT_FOUND", "Course not found.");
  }

  const previousThumbnailPublicId = existing.thumbnailPublicId;

  if (body.title !== undefined) {
    const title = String(body.title || "").trim();
    if (title.length < 2 || title.length > 160) {
      throw new AppError(400, "VALIDATION_ERROR", "Course title must be between 2 and 160 characters.");
    }
    existing.title = title;
  }
  if (body.subtitle !== undefined) {
    existing.subtitle = String(body.subtitle || "").trim().slice(0, 240);
  }
  if (body.description !== undefined) {
    existing.description = String(body.description || "").trim().slice(0, 4000);
  }
  if (body.thumbnailUrl !== undefined) {
    const url = String(body.thumbnailUrl || "").trim();
    if (!isValidMediaUrl(url, { allowEmpty: true })) {
      throw new AppError(400, "INVALID_MEDIA_URL", "thumbnailUrl must be a valid http/https URL.");
    }
    existing.thumbnailUrl = url;
  }
  if (body.thumbnailPublicId !== undefined) {
    existing.thumbnailPublicId = String(body.thumbnailPublicId || "").trim().slice(0, 300);
  }
  if (body.level !== undefined) {
    existing.level = normalizeCourseLevel(body.level, existing.level);
  }
  if (body.category !== undefined) {
    existing.category = String(body.category || "General").trim().slice(0, 80) || "General";
  }
  if (body.durationDays !== undefined) {
    existing.durationDays = Math.min(365, Math.max(1, Math.round(Number(body.durationDays) || 30)));
  }
  if (body.isPremium !== undefined) {
    existing.isPremium = Boolean(body.isPremium);
  }
  if (body.price !== undefined || body.isPremium !== undefined) {
    const rawPrice = body.price !== undefined ? Number(body.price) : existing.price;
    existing.price = existing.isPremium ? Math.max(0, Number.isFinite(rawPrice) ? rawPrice : 0) : 0;
  }
  if (body.isPublished !== undefined) {
    existing.isPublished = Boolean(body.isPublished);
  }

  await existing.save();

  if (
    previousThumbnailPublicId &&
    previousThumbnailPublicId !== existing.thumbnailPublicId
  ) {
    await cleanupUnreferencedThumbnail(previousThumbnailPublicId, existing._id);
  }

  return { course: formatCourseDoc(existing) };
}

export async function adminDeleteCourse(courseId) {
  const oid = assertCourseObjectId(courseId, "courseId");
  const course = await Course.findById(oid).lean();
  if (!course) {
    throw new AppError(404, "COURSE_NOT_FOUND", "Course not found.");
  }

  const lessons = await Lesson.find({ courseId: oid }).select("_id content").lean();
  const deletedLessonIds = lessons.map((l) => l._id);

  await Promise.all([
    LessonProgress.deleteMany({ courseId: oid }),
    Lesson.deleteMany({ courseId: oid }),
    Module.deleteMany({ courseId: oid }),
    Course.deleteOne({ _id: oid }),
  ]);

  await Promise.allSettled([
    cleanupUnreferencedLessonMedia(lessons, deletedLessonIds),
    cleanupUnreferencedThumbnail(course.thumbnailPublicId, oid),
  ]);

  return { deleted: true, courseId: String(oid) };
}

/* ============================================================================
 * ADMIN: MODULES
 * ========================================================================== */

export async function adminListModules(courseId) {
  const oid = assertCourseObjectId(courseId, "courseId");
  const exists = await Course.exists({ _id: oid });
  if (!exists) {
    throw new AppError(404, "COURSE_NOT_FOUND", "Course not found.");
  }
  const modules = await Module.find({ courseId: oid }).sort({ order: 1, createdAt: 1 }).lean();
  return { modules: modules.map((m) => formatModuleDoc(m)) };
}

export async function adminCreateModule({ courseId, body = {} }) {
  const targetCourseId = assertCourseObjectId(courseId || body.courseId, "courseId");
  const courseExists = await Course.exists({ _id: targetCourseId });
  if (!courseExists) {
    throw new AppError(404, "COURSE_NOT_FOUND", "Course not found.");
  }

  const title = String(body.title || "").trim();
  if (title.length < 2 || title.length > 160) {
    throw new AppError(400, "VALIDATION_ERROR", "Module title must be between 2 and 160 characters.");
  }

  let order = Number(body.order);
  if (!Number.isFinite(order) || order < 1) {
    const lastModule = await Module.findOne({ courseId: targetCourseId })
      .sort({ order: -1 })
      .select("order")
      .lean();
    order = (lastModule?.order || 0) + 1;
  }

  const created = await Module.create({
    courseId: targetCourseId,
    title,
    description: String(body.description || "").trim().slice(0, 1500),
    order: Math.round(order),
    isPublished: Boolean(body.isPublished),
  });

  const counts = await recalculateCourseCounts(targetCourseId);

  return {
    module: formatModuleDoc(created),
    counts,
  };
}

export async function adminUpdateModule({ moduleId, body = {} }) {
  const oid = assertCourseObjectId(moduleId, "moduleId");
  const mod = await Module.findById(oid);
  if (!mod) {
    throw new AppError(404, "MODULE_NOT_FOUND", "Module not found.");
  }

  if (body.title !== undefined) {
    const title = String(body.title || "").trim();
    if (title.length < 2 || title.length > 160) {
      throw new AppError(400, "VALIDATION_ERROR", "Module title must be between 2 and 160 characters.");
    }
    mod.title = title;
  }
  if (body.description !== undefined) {
    mod.description = String(body.description || "").trim().slice(0, 1500);
  }
  if (body.order !== undefined && Number.isFinite(Number(body.order))) {
    mod.order = Math.max(0, Math.round(Number(body.order)));
  }
  if (body.isPublished !== undefined) {
    mod.isPublished = Boolean(body.isPublished);
  }

  await mod.save();
  return { module: formatModuleDoc(mod) };
}

export async function adminDeleteModule(moduleId) {
  const oid = assertCourseObjectId(moduleId, "moduleId");
  const mod = await Module.findById(oid).lean();
  if (!mod) {
    throw new AppError(404, "MODULE_NOT_FOUND", "Module not found.");
  }

  const lessons = await Lesson.find({ moduleId: oid }).select("_id content").lean();
  const lessonIds = lessons.map((l) => l._id);

  await Promise.all([
    lessonIds.length ? LessonProgress.deleteMany({ lessonId: { $in: lessonIds } }) : Promise.resolve(),
    Lesson.deleteMany({ moduleId: oid }),
    Module.deleteOne({ _id: oid }),
  ]);

  const remainingModules = await Module.find({ courseId: mod.courseId })
    .sort({ order: 1, createdAt: 1 })
    .select("_id")
    .lean();

  if (remainingModules.length) {
    await Module.bulkWrite(
      remainingModules.map((m, index) => ({
        updateOne: {
          filter: { _id: m._id },
          update: { $set: { order: index + 1 } },
        },
      }))
    );
  }

  const [counts] = await Promise.all([
    recalculateCourseCounts(mod.courseId),
    cleanupUnreferencedLessonMedia(lessons, lessonIds),
  ]);

  return { deleted: true, moduleId: String(oid), counts };
}

export async function adminReorderModules({ courseId, moduleIds = [] }) {
  const cid = assertCourseObjectId(courseId, "courseId");
  if (!Array.isArray(moduleIds) || moduleIds.length === 0) {
    throw new AppError(400, "VALIDATION_ERROR", "moduleIds must be a non-empty array.");
  }

  const existingModules = await Module.find({ courseId: cid }).sort({ order: 1, createdAt: 1 }).lean();
  const existingMap = new Map(existingModules.map((m) => [String(m._id), m]));

  const seen = new Set();
  const orderedIds = [];
  for (const rawId of moduleIds) {
    const idStr = String(rawId || "");
    if (!existingMap.has(idStr)) {
      throw new AppError(400, "INVALID_ID", `Module ${idStr} does not belong to this course.`);
    }
    if (!seen.has(idStr)) {
      seen.add(idStr);
      orderedIds.push(idStr);
    }
  }
  for (const m of existingModules) {
    const idStr = String(m._id);
    if (!seen.has(idStr)) orderedIds.push(idStr);
  }

  await Module.bulkWrite(
    orderedIds.map((idStr, idx) => ({
      updateOne: {
        filter: { _id: new mongoose.Types.ObjectId(idStr), courseId: cid },
        update: { $set: { order: idx + 1 } },
      },
    }))
  );

  const updated = await Module.find({ courseId: cid }).sort({ order: 1, createdAt: 1 }).lean();
  return { modules: updated.map((m) => formatModuleDoc(m)) };
}

/* ============================================================================
 * ADMIN: LESSONS
 * ========================================================================== */

export async function adminListLessons({ courseId = "", moduleId = "" } = {}) {
  const filter = {};
  if (moduleId) {
    filter.moduleId = assertCourseObjectId(moduleId, "moduleId");
  } else if (courseId) {
    filter.courseId = assertCourseObjectId(courseId, "courseId");
  } else {
    throw new AppError(400, "VALIDATION_ERROR", "courseId or moduleId query parameter is required.");
  }

  const lessons = await Lesson.find(filter).sort({ order: 1, createdAt: 1 }).lean();
  return {
    lessons: lessons.map((l) => formatLessonDoc(l, { includeContent: false })),
  };
}

export async function adminCreateLesson({ adminUid, body = {} }) {
  const moduleId = assertCourseObjectId(body.moduleId, "moduleId");
  const mod = await Module.findById(moduleId).lean();
  if (!mod) {
    throw new AppError(404, "MODULE_NOT_FOUND", "Module not found.");
  }

  if (body.courseId && String(body.courseId) !== String(mod.courseId)) {
    throw new AppError(400, "VALIDATION_ERROR", "moduleId does not belong to the provided courseId.");
  }

  const title = String(body.title || "").trim();
  if (title.length < 2 || title.length > 160) {
    throw new AppError(400, "VALIDATION_ERROR", "Lesson title must be between 2 and 160 characters.");
  }

  const content = normalizeContentBlocks(body.content || []);
  let order = Number(body.order);
  if (!Number.isFinite(order) || order < 1) {
    const lastLesson = await Lesson.findOne({ moduleId })
      .sort({ order: -1 })
      .select("order")
      .lean();
    order = (lastLesson?.order || 0) + 1;
  }

  const created = await Lesson.create({
    courseId: mod.courseId,
    moduleId,
    title,
    subtitle: String(body.subtitle || "").trim().slice(0, 240),
    description: String(body.description || "").trim().slice(0, 3000),
    order: Math.round(order),
    estimatedDurationMinutes: Math.min(
      600,
      Math.max(1, Math.round(Number(body.estimatedDurationMinutes || 10) || 10))
    ),
    isPremium: Boolean(body.isPremium),
    isPublished: Boolean(body.isPublished),
    content,
    createdByUid: adminUid || null,
  });

  const counts = await recalculateCourseCounts(mod.courseId);

  return {
    lesson: formatLessonDoc(created, { includeContent: true }),
    counts,
  };
}

export async function adminGetLesson(lessonId) {
  const oid = assertCourseObjectId(lessonId, "lessonId");
  const lesson = await Lesson.findById(oid).lean();
  if (!lesson) {
    throw new AppError(404, "LESSON_NOT_FOUND", "Lesson not found.");
  }

  const [course, mod, orderedModules, allCourseLessons] = await Promise.all([
    Course.findById(lesson.courseId).lean(),
    Module.findById(lesson.moduleId).lean(),
    Module.find({ courseId: lesson.courseId }).sort({ order: 1, createdAt: 1 }).select("_id").lean(),
    Lesson.find({ courseId: lesson.courseId })
      .sort({ order: 1, createdAt: 1 })
      .select("_id moduleId title isPremium order")
      .lean(),
  ]);

  const lessonsByMod = new Map();
  for (const l of allCourseLessons) {
    const k = String(l.moduleId);
    if (!lessonsByMod.has(k)) lessonsByMod.set(k, []);
    lessonsByMod.get(k).push(l);
  }
  const orderedCourseLessons = [];
  for (const m of orderedModules) {
    const list = lessonsByMod.get(String(m._id)) || [];
    orderedCourseLessons.push(...list);
  }

  const { previousLesson, nextLesson } = resolvePrevNextLessons(orderedCourseLessons, lesson._id);

  return {
    lesson: formatLessonDoc(lesson, { includeContent: true }),
    course: course ? formatCourseDoc(course) : null,
    module: mod ? formatModuleDoc(mod) : null,
    previousLesson,
    nextLesson,
    isPreview: true,
  };
}

export async function adminUpdateLesson({ lessonId, body = {} }) {
  const oid = assertCourseObjectId(lessonId, "lessonId");
  const existing = await Lesson.findById(oid);
  if (!existing) {
    throw new AppError(404, "LESSON_NOT_FOUND", "Lesson not found.");
  }

  const previousBlocks = Array.isArray(existing.content)
    ? existing.content.map((b) => ({ publicId: b.publicId, type: b.type }))
    : [];

  let nextCourseId = existing.courseId;
  if (body.moduleId !== undefined && String(body.moduleId) !== String(existing.moduleId)) {
    const targetModId = assertCourseObjectId(body.moduleId, "moduleId");
    const targetMod = await Module.findById(targetModId).lean();
    if (!targetMod) {
      throw new AppError(404, "MODULE_NOT_FOUND", "Target module not found.");
    }
    existing.moduleId = targetModId;
    existing.courseId = targetMod.courseId;
    nextCourseId = targetMod.courseId;
  }

  if (body.title !== undefined) {
    const title = String(body.title || "").trim();
    if (title.length < 2 || title.length > 160) {
      throw new AppError(400, "VALIDATION_ERROR", "Lesson title must be between 2 and 160 characters.");
    }
    existing.title = title;
  }
  if (body.subtitle !== undefined) {
    existing.subtitle = String(body.subtitle || "").trim().slice(0, 240);
  }
  if (body.description !== undefined) {
    existing.description = String(body.description || "").trim().slice(0, 3000);
  }
  if (body.order !== undefined && Number.isFinite(Number(body.order))) {
    existing.order = Math.max(0, Math.round(Number(body.order)));
  }
  if (body.estimatedDurationMinutes !== undefined) {
    existing.estimatedDurationMinutes = Math.min(
      600,
      Math.max(1, Math.round(Number(body.estimatedDurationMinutes) || 10))
    );
  }
  if (body.isPremium !== undefined) {
    existing.isPremium = Boolean(body.isPremium);
  }
  if (body.isPublished !== undefined) {
    existing.isPublished = Boolean(body.isPublished);
  }
  if (body.content !== undefined) {
    existing.content = normalizeContentBlocks(body.content);
  }

  await existing.save();

  if (body.content !== undefined && previousBlocks.length > 0) {
    const keptIds = new Set((existing.content || []).map((b) => b.publicId).filter(Boolean));
    const removedBlocks = previousBlocks.filter((b) => b.publicId && !keptIds.has(b.publicId));
    if (removedBlocks.length > 0) {
      await cleanupUnreferencedLessonMedia([{ content: removedBlocks }], [existing._id]);
    }
  }

  if (String(nextCourseId) !== String(existing.courseId)) {
    await recalculateCourseCounts(nextCourseId);
  }

  return { lesson: formatLessonDoc(existing, { includeContent: true }) };
}

export async function adminDeleteLesson(lessonId) {
  const oid = assertCourseObjectId(lessonId, "lessonId");
  const lesson = await Lesson.findById(oid).lean();
  if (!lesson) {
    throw new AppError(404, "LESSON_NOT_FOUND", "Lesson not found.");
  }

  await Promise.all([
    LessonProgress.deleteMany({ lessonId: oid }),
    Lesson.deleteOne({ _id: oid }),
  ]);

  const remainingLessons = await Lesson.find({ moduleId: lesson.moduleId })
    .sort({ order: 1, createdAt: 1 })
    .select("_id")
    .lean();

  if (remainingLessons.length) {
    await Lesson.bulkWrite(
      remainingLessons.map((l, idx) => ({
        updateOne: {
          filter: { _id: l._id },
          update: { $set: { order: idx + 1 } },
        },
      }))
    );
  }

  const [counts] = await Promise.all([
    recalculateCourseCounts(lesson.courseId),
    cleanupUnreferencedLessonMedia([lesson], [oid]),
  ]);

  return { deleted: true, lessonId: String(oid), counts };
}

export async function adminDuplicateLesson({ lessonId, adminUid }) {
  const oid = assertCourseObjectId(lessonId, "lessonId");
  const source = await Lesson.findById(oid).lean();
  if (!source) {
    throw new AppError(404, "LESSON_NOT_FOUND", "Lesson not found.");
  }

  const lastLesson = await Lesson.findOne({ moduleId: source.moduleId })
    .sort({ order: -1 })
    .select("order")
    .lean();
  const nextOrder = (lastLesson?.order || 0) + 1;

  const clonedBlocks = (source.content || [])
    .slice()
    .sort((a, b) => Number(a.order || 0) - Number(b.order || 0))
    .map((b, idx) => ({
      id: generateBlockId(),
      type: b.type,
      title: b.title || "",
      description: b.description || "",
      url: b.url || "",
      publicId: b.publicId || "",
      text: b.text || "",
      alt: b.alt || "",
      durationSeconds: Number(b.durationSeconds || 0),
      order: idx + 1,
      isDownloadable: Boolean(b.isDownloadable),
    }));

  const duplicated = await Lesson.create({
    courseId: source.courseId,
    moduleId: source.moduleId,
    title: `${source.title} (Copy)`.slice(0, 160),
    subtitle: source.subtitle || "",
    description: source.description || "",
    order: nextOrder,
    estimatedDurationMinutes: Number(source.estimatedDurationMinutes || 10),
    isPremium: Boolean(source.isPremium),
    isPublished: false,
    content: clonedBlocks,
    createdByUid: adminUid || null,
  });

  const counts = await recalculateCourseCounts(source.courseId);

  return {
    lesson: formatLessonDoc(duplicated, { includeContent: true }),
    counts,
  };
}

export async function adminReorderLessons({ moduleId, lessonIds = [] }) {
  const mid = assertCourseObjectId(moduleId, "moduleId");
  if (!Array.isArray(lessonIds) || lessonIds.length === 0) {
    throw new AppError(400, "VALIDATION_ERROR", "lessonIds must be a non-empty array.");
  }

  const existingLessons = await Lesson.find({ moduleId: mid })
    .sort({ order: 1, createdAt: 1 })
    .lean();
  const existingMap = new Map(existingLessons.map((l) => [String(l._id), l]));

  const seen = new Set();
  const orderedIds = [];
  for (const rawId of lessonIds) {
    const idStr = String(rawId || "");
    if (!existingMap.has(idStr)) {
      throw new AppError(400, "INVALID_ID", `Lesson ${idStr} does not belong to this module.`);
    }
    if (!seen.has(idStr)) {
      seen.add(idStr);
      orderedIds.push(idStr);
    }
  }
  for (const l of existingLessons) {
    const idStr = String(l._id);
    if (!seen.has(idStr)) orderedIds.push(idStr);
  }

  await Lesson.bulkWrite(
    orderedIds.map((idStr, idx) => ({
      updateOne: {
        filter: { _id: new mongoose.Types.ObjectId(idStr), moduleId: mid },
        update: { $set: { order: idx + 1 } },
      },
    }))
  );

  const updated = await Lesson.find({ moduleId: mid }).sort({ order: 1, createdAt: 1 }).lean();
  return {
    lessons: updated.map((l) => formatLessonDoc(l, { includeContent: false })),
  };
}

/* ============================================================================
 * STUDENT: COURSES, LESSONS & PROGRESS
 * ========================================================================== */

async function getPublishedCourseHierarchy(courseId) {
  const publishedModules = await Module.find({ courseId, isPublished: true })
    .sort({ order: 1, createdAt: 1 })
    .lean();

  const publishedModuleIds = publishedModules.map((m) => m._id);
  const publishedLessons = publishedModuleIds.length
    ? await Lesson.find({
        courseId,
        moduleId: { $in: publishedModuleIds },
        isPublished: true,
      })
        .sort({ order: 1, createdAt: 1 })
        .lean()
    : [];

  const lessonsByMod = new Map();
  for (const l of publishedLessons) {
    const k = String(l.moduleId);
    if (!lessonsByMod.has(k)) lessonsByMod.set(k, []);
    lessonsByMod.get(k).push(l);
  }

  const orderedLessons = [];
  for (const m of publishedModules) {
    orderedLessons.push(...(lessonsByMod.get(String(m._id)) || []));
  }

  return { publishedModules, publishedLessons, orderedLessons, lessonsByMod };
}

export async function studentListCourses({
  userDoc,
  page = 1,
  limit = 20,
  level = "ALL",
  category = "",
  search = "",
  updatedAfter = null,
} = {}) {
  const safePage = Math.max(1, Number(page) || 1);
  const safeLimit = Math.min(50, Math.max(1, Number(limit) || 20));
  const filter = { isPublished: true };

  if (level && String(level).toUpperCase() !== "ALL") {
    filter.level = normalizeCourseLevel(level);
  }
  if (category && String(category).trim()) {
    filter.category = String(category).trim();
  }
  if (search && String(search).trim()) {
    const q = String(search).trim().replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    filter.$or = [
      { title: { $regex: q, $options: "i" } },
      { subtitle: { $regex: q, $options: "i" } },
      { description: { $regex: q, $options: "i" } },
      { category: { $regex: q, $options: "i" } },
    ];
  }
  const updatedAfterDate = parseOptionalIsoDate(updatedAfter);
  if (updatedAfterDate) {
    filter.updatedAt = { $gte: updatedAfterDate };
  }

  const [courses, total, categories] = await Promise.all([
    Course.find(filter)
      .sort({ updatedAt: -1, createdAt: -1 })
      .skip((safePage - 1) * safeLimit)
      .limit(safeLimit)
      .lean(),
    Course.countDocuments(filter),
    Course.distinct("category", { isPublished: true }),
  ]);

  const courseIds = courses.map((c) => c._id);
  const hasPremiumAccess = hasStudentPremiumAccess(userDoc);

  let publishedModules = [];
  let publishedLessons = [];
  let progressDocs = [];

  if (courseIds.length > 0) {
    publishedModules = await Module.find({
      courseId: { $in: courseIds },
      isPublished: true,
    })
      .select("_id courseId")
      .lean();

    const pubModIds = publishedModules.map((m) => m._id);

    const [lessonsRes, progressRes] = await Promise.all([
      pubModIds.length
        ? Lesson.find({
            courseId: { $in: courseIds },
            moduleId: { $in: pubModIds },
            isPublished: true,
          })
            .select("_id courseId moduleId")
            .lean()
        : Promise.resolve([]),
      userDoc?.firebaseUid
        ? LessonProgress.find({
            userId: userDoc.firebaseUid,
            courseId: { $in: courseIds },
          })
            .select("courseId lessonId isCompleted progressPercentage")
            .lean()
        : Promise.resolve([]),
    ]);

    publishedLessons = lessonsRes;
    progressDocs = progressRes;
  }

  const moduleCountMap = new Map();
  for (const m of publishedModules) {
    const cid = String(m.courseId);
    moduleCountMap.set(cid, (moduleCountMap.get(cid) || 0) + 1);
  }

  const publishedLessonIdSetByCourse = new Map();
  for (const l of publishedLessons) {
    const cid = String(l.courseId);
    if (!publishedLessonIdSetByCourse.has(cid)) {
      publishedLessonIdSetByCourse.set(cid, new Set());
    }
    publishedLessonIdSetByCourse.get(cid).add(String(l._id));
  }

  const progressStatsMap = new Map();
  for (const p of progressDocs) {
    const cid = String(p.courseId);
    const validLessonSet = publishedLessonIdSetByCourse.get(cid);
    if (!validLessonSet || !validLessonSet.has(String(p.lessonId))) continue;
    const entry = progressStatsMap.get(cid) || { completed: 0, touched: 0 };
    entry.touched += 1;
    if (p.isCompleted) entry.completed += 1;
    progressStatsMap.set(cid, entry);
  }

  const formattedCourses = courses.map((c) => {
    const cid = String(c._id);
    const pubModules = moduleCountMap.get(cid) || 0;
    const pubLessons = publishedLessonIdSetByCourse.get(cid)?.size || 0;
    const stats = progressStatsMap.get(cid) || { completed: 0, touched: 0 };
    const completionPercentage = calculateCourseCompletionPercent(stats.completed, pubLessons);

    let enrollmentStatus = "not_started";
    if (pubLessons > 0 && stats.completed >= pubLessons) {
      enrollmentStatus = "completed";
    } else if (stats.touched > 0 || stats.completed > 0) {
      enrollmentStatus = "in_progress";
    }

    return formatCourseDoc(c, {
      totalModules: pubModules,
      totalLessons: pubLessons,
      completedLessons: stats.completed,
      completionPercentage,
      enrollmentStatus,
      hasAccess: !c.isPremium || hasPremiumAccess,
    });
  });

  return {
    courses: formattedCourses,
    categories: categories.filter(Boolean).sort(),
    hasPremiumAccess,
    serverTimestamp: new Date().toISOString(),
    pagination: {
      page: safePage,
      limit: safeLimit,
      total,
      totalPages: Math.max(1, Math.ceil(total / safeLimit)),
    },
  };
}

export async function studentGetCourseDetail({
  courseId,
  userDoc,
  includeContent = false,
  updatedAfter = null,
}) {
  const oid = assertCourseObjectId(courseId, "courseId");
  const course = await Course.findOne({ _id: oid, isPublished: true }).lean();
  if (!course) {
    throw new AppError(404, "COURSE_NOT_FOUND", "Published course not found.");
  }

  const updatedAfterDate = parseOptionalIsoDate(updatedAfter);
  const { publishedModules, orderedLessons, lessonsByMod } = await getPublishedCourseHierarchy(oid);

  const progressDocs = userDoc?.firebaseUid
    ? await LessonProgress.find({ userId: userDoc.firebaseUid, courseId: oid }).lean()
    : [];
  const progressMap = new Map(progressDocs.map((p) => [String(p.lessonId), p]));
  const hasPremiumAccess = hasStudentPremiumAccess(userDoc);

  let completedLessons = 0;
  let touchedLessons = 0;
  let continueLessonId = null;
  let firstAccessibleLessonId = null;

  const formattedModules = publishedModules.map((mod) => {
    const modLessons = lessonsByMod.get(String(mod._id)) || [];
    const formattedLessons = modLessons
      .filter((l) => !updatedAfterDate || new Date(l.updatedAt) >= updatedAfterDate)
      .map((lesson) => {
        const lid = String(lesson._id);
        const prog = progressMap.get(lid) || null;
        const locked = isLessonLockedForStudent({ course, lesson, hasPremiumAccess });
        if (prog) {
          touchedLessons += 1;
          if (prog.isCompleted) completedLessons += 1;
        }
        if (!locked) {
          if (!firstAccessibleLessonId) firstAccessibleLessonId = lid;
          if (!continueLessonId && !prog?.isCompleted) continueLessonId = lid;
        }
        return formatLessonDoc(lesson, {
          includeContent: Boolean(includeContent) && !locked,
          progressDoc: prog,
          isLocked: locked,
        });
      });

    return formatModuleDoc(mod, {
      lessonCount: modLessons.length,
      lessons: formattedLessons,
    });
  });

  // Ensure stats remain accurate even when updatedAfter filtered lessons
  if (updatedAfterDate) {
    completedLessons = 0;
    touchedLessons = 0;
    for (const lesson of orderedLessons) {
      const prog = progressMap.get(String(lesson._id));
      if (prog) {
        touchedLessons += 1;
        if (prog.isCompleted) completedLessons += 1;
      }
    }
  }

  const totalLessons = orderedLessons.length;
  const completionPercentage = calculateCourseCompletionPercent(completedLessons, totalLessons);
  let enrollmentStatus = "not_started";
  if (totalLessons > 0 && completedLessons >= totalLessons) {
    enrollmentStatus = "completed";
  } else if (touchedLessons > 0 || completedLessons > 0) {
    enrollmentStatus = "in_progress";
  }

  return {
    course: formatCourseDoc(course, {
      totalModules: publishedModules.length,
      totalLessons,
      completedLessons,
      completionPercentage,
      enrollmentStatus,
      continueLessonId: continueLessonId || firstAccessibleLessonId,
      hasAccess: !course.isPremium || hasPremiumAccess,
    }),
    modules: formattedModules,
    hasPremiumAccess,
    serverTimestamp: new Date().toISOString(),
  };
}

export async function studentGetLessonDetail({ lessonId, userDoc = null }) {
  const oid = assertCourseObjectId(lessonId, "lessonId");
  const lesson = await Lesson.findById(oid).lean();
  if (!lesson) {
    throw new AppError(404, "LESSON_NOT_FOUND", "Lesson not found.");
  }

  const [course, mod] = await Promise.all([
    Course.findById(lesson.courseId).lean(),
    Module.findById(lesson.moduleId).lean(),
  ]);

  if (!course || !course.isPublished || !mod || !mod.isPublished || !lesson.isPublished) {
    throw new AppError(404, "LESSON_NOT_FOUND", "Published lesson not found.");
  }

  const hasPremiumAccess = hasStudentPremiumAccess(userDoc);
  const locked = isLessonLockedForStudent({ course, lesson, hasPremiumAccess });
  if (locked) {
    throw new AppError(
      403,
      "PREMIUM_REQUIRED",
      "Upgrade to Premium to access this lesson."
    );
  }

  const [{ orderedLessons }, progressDoc] = await Promise.all([
    getPublishedCourseHierarchy(course._id),
    userDoc?.firebaseUid
      ? LessonProgress.findOne({ userId: userDoc.firebaseUid, lessonId: oid }).lean()
      : Promise.resolve(null),
  ]);

  const { previousLesson, nextLesson } = resolvePrevNextLessons(orderedLessons, oid);

  return {
    lesson: formatLessonDoc(lesson, {
      includeContent: true,
      progressDoc,
      isLocked: false,
    }),
    course: formatCourseDoc(course, {
      hasAccess: !course.isPremium || hasPremiumAccess,
    }),
    module: formatModuleDoc(mod),
    progress: formatProgressDoc(progressDoc),
    previousLesson,
    nextLesson,
    isPreview: false,
  };
}

export async function getLessonProgress({ lessonId, userDoc }) {
  const oid = assertCourseObjectId(lessonId, "lessonId");
  const lesson = await Lesson.findById(oid).select("_id courseId moduleId isPremium isPublished").lean();
  if (!lesson) {
    throw new AppError(404, "LESSON_NOT_FOUND", "Lesson not found.");
  }
  const progressDoc = await LessonProgress.findOne({
    userId: userDoc.firebaseUid,
    lessonId: oid,
  }).lean();

  return {
    progress: formatProgressDoc(progressDoc),
  };
}

export async function upsertLessonProgress({ lessonId, userDoc, body = {} }) {
  const oid = assertCourseObjectId(lessonId, "lessonId");
  const lesson = await Lesson.findById(oid).lean();
  if (!lesson) {
    throw new AppError(404, "LESSON_NOT_FOUND", "Lesson not found.");
  }

  const [course, mod] = await Promise.all([
    Course.findById(lesson.courseId).lean(),
    Module.findById(lesson.moduleId).lean(),
  ]);

  if (!course || !course.isPublished || !mod || !mod.isPublished || !lesson.isPublished) {
    throw new AppError(404, "LESSON_NOT_FOUND", "Published lesson not found.");
  }

  const hasPremiumAccess = hasStudentPremiumAccess(userDoc);
  if (isLessonLockedForStudent({ course, lesson, hasPremiumAccess })) {
    throw new AppError(403, "PREMIUM_REQUIRED", "Upgrade to Premium to record progress for this lesson.");
  }

  const existing = await LessonProgress.findOne({
    userId: userDoc.firebaseUid,
    lessonId: oid,
  }).lean();

  const requestedPercent =
    body.progressPercentage !== undefined
      ? Math.min(100, Math.max(0, Math.round(Number(body.progressPercentage) || 0)))
      : existing?.progressPercentage || 0;

  const markComplete =
    body.isCompleted === true ||
    requestedPercent >= 100 ||
    (body.isCompleted === undefined && Boolean(existing?.isCompleted));

  const nextPercent = markComplete ? 100 : Math.max(existing?.progressPercentage || 0, requestedPercent);
  const now = new Date();
  const completedAt =
    markComplete ? existing?.completedAt || now : body.isCompleted === false ? null : existing?.completedAt || null;

  const finalIsCompleted = body.isCompleted === false ? false : markComplete;
  const finalPercent = body.isCompleted === false ? Math.min(99, requestedPercent) : nextPercent;

  const lastPositionSeconds =
    body.lastPositionSeconds !== undefined
      ? Math.max(0, Math.round(Number(body.lastPositionSeconds) || 0))
      : Number(existing?.lastPositionSeconds || 0);

  const lastContentId =
    body.lastContentId !== undefined
      ? String(body.lastContentId || "").trim().slice(0, 80)
      : String(existing?.lastContentId || "");

  const updatedProgress = await LessonProgress.findOneAndUpdate(
    { userId: userDoc.firebaseUid, lessonId: oid },
    {
      $set: {
        courseId: lesson.courseId,
        moduleId: lesson.moduleId,
        isCompleted: finalIsCompleted,
        progressPercentage: finalPercent,
        lastContentId,
        lastPositionSeconds,
        completedAt,
      },
      $setOnInsert: {
        userId: userDoc.firebaseUid,
        lessonId: oid,
        startedAt: now,
      },
    },
    { upsert: true, new: true }
  ).lean();

  const { orderedLessons } = await getPublishedCourseHierarchy(lesson.courseId);
  const publishedLessonIds = orderedLessons.map((l) => l._id);

  const [completedCount, touchedCount] = publishedLessonIds.length
    ? await Promise.all([
        LessonProgress.countDocuments({
          userId: userDoc.firebaseUid,
          courseId: lesson.courseId,
          lessonId: { $in: publishedLessonIds },
          isCompleted: true,
        }),
        LessonProgress.countDocuments({
          userId: userDoc.firebaseUid,
          courseId: lesson.courseId,
          lessonId: { $in: publishedLessonIds },
        }),
      ])
    : [0, 0];

  const totalLessons = publishedLessonIds.length;
  const completionPercentage = calculateCourseCompletionPercent(completedCount, totalLessons);
  let enrollmentStatus = "not_started";
  if (totalLessons > 0 && completedCount >= totalLessons) {
    enrollmentStatus = "completed";
  } else if (touchedCount > 0 || completedCount > 0) {
    enrollmentStatus = "in_progress";
  }

  return {
    progress: formatProgressDoc(updatedProgress),
    courseProgress: {
      courseId: String(lesson.courseId),
      completedLessons: completedCount,
      totalLessons,
      completionPercentage,
      enrollmentStatus,
    },
  };
}
