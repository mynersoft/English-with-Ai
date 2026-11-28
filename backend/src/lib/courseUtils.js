import crypto from "crypto";
import { AppError } from "./errors.js";

export const COURSE_LEVELS = ["Beginner", "Intermediate", "Advanced"];
export const CONTENT_BLOCK_TYPES = ["video", "pdf", "text", "image"];

export function normalizeCourseLevel(raw, fallback = "Beginner") {
  const s = String(raw || "").trim().toLowerCase();
  if (s === "beginner") return "Beginner";
  if (s === "intermediate") return "Intermediate";
  if (s === "advanced") return "Advanced";
  if (fallback && COURSE_LEVELS.includes(fallback)) return fallback;
  return "Beginner";
}

export function slugifyCourseText(text) {
  return String(text || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 96) || "course";
}

export function isValidMediaUrl(url, { allowEmpty = true } = {}) {
  const raw = String(url || "").trim();
  if (!raw) return allowEmpty;
  try {
    const parsed = new URL(raw);
    return parsed.protocol === "https:" || parsed.protocol === "http:";
  } catch {
    return false;
  }
}

export function sanitizeRichText(input) {
  const raw = String(input || "");
  if (!raw) return "";
  // Strip executable tags & inline event handlers while keeping formatted prose/markdown/safe tags intact.
  return raw
    .replace(/<\s*(script|iframe|object|embed|style|form|input|button)[^>]*>[\s\S]*?<\s*\/\s*\1\s*>/gi, "")
    .replace(/<\s*(script|iframe|object|embed|style|form|input|button)[^>]*\/?\s*>/gi, "")
    .replace(/\son[a-z]+\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)/gi, "")
    .replace(/javascript\s*:/gi, "")
    .trim();
}

export function generateBlockId() {
  return `blk_${crypto.randomBytes(6).toString("hex")}`;
}

export function normalizeContentBlocks(rawBlocks = []) {
  if (!Array.isArray(rawBlocks)) {
    throw new AppError(400, "VALIDATION_ERROR", "Lesson content must be an array.");
  }

  const normalized = rawBlocks.map((item, idx) => {
    if (!item || typeof item !== "object") {
      throw new AppError(400, "VALIDATION_ERROR", `Content block #${idx + 1} is invalid.`);
    }

    const type = String(item.type || "").trim().toLowerCase();
    if (!CONTENT_BLOCK_TYPES.includes(type)) {
      throw new AppError(
        400,
        "INVALID_CONTENT_TYPE",
        `Content block #${idx + 1} has unsupported type "${type}". Expected video, pdf, text, or image.`
      );
    }

    const title = String(item.title || "").trim().slice(0, 200);
    const description = String(item.description || "").trim().slice(0, 1000);
    const url = String(item.url || "").trim();
    const publicId = String(item.publicId || "").trim().slice(0, 300);
    const text = type === "text" ? sanitizeRichText(item.text || "") : String(item.text || "").trim();
    const alt = String(item.alt || "").trim().slice(0, 300);
    const durationSeconds = Math.max(0, Math.round(Number(item.durationSeconds || 0) || 0));
    const order = Number.isFinite(Number(item.order)) ? Number(item.order) : idx + 1;
    const isDownloadable = Boolean(item.isDownloadable);
    const id = String(item.id || "").trim() || generateBlockId();

    if (type === "text") {
      if (!text && !title) {
        throw new AppError(400, "VALIDATION_ERROR", `Text block #${idx + 1} cannot be empty.`);
      }
    } else {
      if (!url || !isValidMediaUrl(url, { allowEmpty: false })) {
        throw new AppError(
          400,
          "INVALID_MEDIA_URL",
          `${type.toUpperCase()} block #${idx + 1} requires a valid http/https URL.`
        );
      }
    }

    return {
      id,
      type,
      title: title || (type === "text" ? "Lesson Notes" : `${type.toUpperCase()} Resource`),
      description,
      url,
      publicId,
      text,
      alt,
      durationSeconds: type === "video" ? durationSeconds : 0,
      order,
      isDownloadable: type === "video" || type === "pdf" ? isDownloadable : false,
    };
  });

  normalized.sort((a, b) => a.order - b.order);
  return normalized.map((block, index) => ({
    ...block,
    order: index + 1,
  }));
}

export function hasStudentPremiumAccess(userDoc, now = new Date()) {
  if (!userDoc) return false;
  if (userDoc.role === "admin") return true;
  const subStatus = String(userDoc.subscriptionStatus || "free").toLowerCase();
  if (subStatus !== "active" && subStatus !== "trial") return false;
  if (userDoc.subscriptionExpiresAt) {
    const exp = new Date(userDoc.subscriptionExpiresAt);
    if (!Number.isNaN(exp.getTime()) && exp.getTime() <= now.getTime()) {
      return false;
    }
  }
  return true;
}

export function isLessonLockedForStudent({ course, lesson, hasPremiumAccess, hasPurchasedCourse = false }) {
  if (hasPremiumAccess || hasPurchasedCourse) return false;
  // Lesson explicitly marked premium is always locked for non-premium users.
  if (Boolean(lesson?.isPremium)) return true;
  // Free lessons inside a course are accessible unless both course and lesson require premium.
  // Note: If course is premium and lesson.isPremium is explicitly false, it acts as a free preview lesson.
  return false;
}

export function calculateCourseCompletionPercent(completedCount, totalPublishedLessons) {
  const total = Math.max(0, Number(totalPublishedLessons || 0));
  const done = Math.max(0, Number(completedCount || 0));
  if (total === 0) return 0;
  return Math.max(0, Math.min(100, Math.round((done / total) * 100)));
}

export const MEDIA_UPLOAD_RULES = {
  image: {
    resourceType: "image",
    folder: "english-ai/courses/images",
    maxBytes: 10 * 1024 * 1024, // 10 MB
    mimeTypes: new Set(["image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif"]),
    extensions: new Set([".jpg", ".jpeg", ".png", ".webp", ".gif"]),
  },
  video: {
    resourceType: "video",
    folder: "english-ai/courses/videos",
    maxBytes: 100 * 1024 * 1024, // 100 MB
    mimeTypes: new Set([
      "video/mp4",
      "video/webm",
      "video/quicktime",
      "video/x-matroska",
      "video/mpeg",
    ]),
    extensions: new Set([".mp4", ".webm", ".mov", ".mkv", ".mpeg"]),
  },
  pdf: {
    resourceType: "raw",
    folder: "english-ai/courses/pdfs",
    maxBytes: 25 * 1024 * 1024, // 25 MB
    mimeTypes: new Set(["application/pdf"]),
    extensions: new Set([".pdf"]),
  },
};

export function validateMediaUpload({ mediaType, mimeType = "", fileName = "", sizeBytes = 0 }) {
  const type = String(mediaType || "").trim().toLowerCase();
  const rule = MEDIA_UPLOAD_RULES[type];
  if (!rule) {
    throw new AppError(400, "INVALID_MEDIA_TYPE", "mediaType must be image, video, or pdf.");
  }

  const normalizedMime = String(mimeType || "").trim().toLowerCase().split(";")[0];
  const extMatch = String(fileName || "").trim().toLowerCase().match(/(\.[a-z0-9]+)$/);
  const ext = extMatch ? extMatch[1] : "";

  const mimeOk = normalizedMime && rule.mimeTypes.has(normalizedMime);
  const extOk = ext && rule.extensions.has(ext);
  if (!mimeOk && !extOk) {
    throw new AppError(
      400,
      "UNSUPPORTED_FILE_TYPE",
      `Unsupported ${type} file format. Allowed: ${Array.from(rule.extensions).join(", ")}.`
    );
  }

  const bytes = Number(sizeBytes || 0);
  if (!Number.isFinite(bytes) || bytes <= 0) {
    throw new AppError(400, "EMPTY_FILE", "Uploaded file cannot be empty.");
  }
  if (bytes > rule.maxBytes) {
    const maxMb = Math.round(rule.maxBytes / (1024 * 1024));
    throw new AppError(413, "FILE_TOO_LARGE", `${type.toUpperCase()} exceeds the ${maxMb} MB limit.`);
  }

  return { type, rule, ext };
}

export const AVATAR_MAX_BYTES = 5 * 1024 * 1024; // 5 MB
export const AVATAR_FALLBACK_MAX_BYTES = 256 * 1024; // 256 KB inline fallback when Cloudinary is unconfigured
const AVATAR_MIME_TYPES = new Set(["image/jpeg", "image/jpg", "image/png", "image/webp"]);
const AVATAR_EXTENSIONS = new Set([".jpg", ".jpeg", ".png", ".webp"]);

export function validateStudentAvatarUpload({ mimeType = "", fileName = "", sizeBytes = 0 }) {
  const normalizedMime = String(mimeType || "").trim().toLowerCase().split(";")[0];
  const extMatch = String(fileName || "").trim().toLowerCase().match(/(\.[a-z0-9]+)$/);
  const ext = extMatch ? extMatch[1] : "";

  const mimeOk = normalizedMime && AVATAR_MIME_TYPES.has(normalizedMime);
  const extOk = ext && AVATAR_EXTENSIONS.has(ext);
  if (!mimeOk && !extOk) {
    throw new AppError(
      400,
      "UNSUPPORTED_FILE_TYPE",
      "Unsupported avatar image format. Please choose a JPG, PNG, or WebP photo."
    );
  }

  const bytes = Number(sizeBytes || 0);
  if (!Number.isFinite(bytes) || bytes <= 0) {
    throw new AppError(400, "EMPTY_FILE", "Avatar image cannot be empty.");
  }
  if (bytes > AVATAR_MAX_BYTES) {
    throw new AppError(413, "FILE_TOO_LARGE", "Avatar image exceeds the 5 MB limit.");
  }

  const resolvedMime = mimeOk
    ? normalizedMime === "image/jpg"
      ? "image/jpeg"
      : normalizedMime
    : ext === ".png"
      ? "image/png"
      : ext === ".webp"
        ? "image/webp"
        : "image/jpeg";

  return { mimeType: resolvedMime, bytes };
}

export function buildSignedUploadParams({ mediaType, cloudName, apiKey, apiSecret, timestamp = Math.floor(Date.now() / 1000) }) {
  const type = String(mediaType || "").trim().toLowerCase();
  const rule = MEDIA_UPLOAD_RULES[type];
  if (!rule) {
    throw new AppError(400, "INVALID_MEDIA_TYPE", "mediaType must be image, video, or pdf.");
  }
  const toSign = `folder=${rule.folder}&timestamp=${timestamp}${apiSecret}`;
  const signature = crypto.createHash("sha1").update(toSign).digest("hex");
  return {
    cloudName,
    apiKey,
    timestamp,
    signature,
    folder: rule.folder,
    resourceType: rule.resourceType,
    maxBytes: rule.maxBytes,
    uploadUrl: `https://api.cloudinary.com/v1_1/${cloudName}/${rule.resourceType}/upload`,
  };
}

export function resolvePrevNextLessons(orderedLessons = [], currentLessonId) {
  const targetId = String(currentLessonId || "");
  const idx = orderedLessons.findIndex((l) => String(l._id || l.id) === targetId);
  if (idx === -1) {
    return { previousLesson: null, nextLesson: null };
  }
  const prev = idx > 0 ? orderedLessons[idx - 1] : null;
  const next = idx < orderedLessons.length - 1 ? orderedLessons[idx + 1] : null;
  return {
    previousLesson: prev
      ? {
          id: String(prev._id || prev.id),
          moduleId: String(prev.moduleId),
          title: prev.title,
          isPremium: Boolean(prev.isPremium),
        }
      : null,
    nextLesson: next
      ? {
          id: String(next._id || next.id),
          moduleId: String(next.moduleId),
          title: next.title,
          isPremium: Boolean(next.isPremium),
        }
      : null,
  };
}
