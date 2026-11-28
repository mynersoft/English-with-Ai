import { v2 as cloudinary } from "cloudinary";
import { AppError } from "./errors.js";
import {
  MEDIA_UPLOAD_RULES,
  buildSignedUploadParams,
  validateMediaUpload,
} from "./courseUtils.js";

export { validateMediaUpload };

let cloudinaryConfigured = false;

function getCloudinaryCredentials() {
  const cloudName = String(process.env.CLOUDINARY_CLOUD_NAME || "").trim();
  const apiKey = String(process.env.CLOUDINARY_API_KEY || "").trim();
  const apiSecret = String(process.env.CLOUDINARY_API_SECRET || "").trim();
  if (!cloudName || !apiKey || !apiSecret) {
    throw new AppError(
      503,
      "STORAGE_NOT_CONFIGURED",
      "Cloudinary media storage is not configured on the server."
    );
  }
  return { cloudName, apiKey, apiSecret };
}

function ensureCloudinary() {
  const creds = getCloudinaryCredentials();
  if (!cloudinaryConfigured) {
    cloudinary.config({
      cloud_name: creds.cloudName,
      api_key: creds.apiKey,
      api_secret: creds.apiSecret,
      secure: true,
    });
    cloudinaryConfigured = true;
  }
  return creds;
}

export async function uploadCourseMedia({ buffer, mediaType, mimeType = "", fileName = "" }) {
  const { type, rule, ext } = validateMediaUpload({
    mediaType,
    mimeType,
    fileName,
    sizeBytes: buffer?.length || 0,
  });

  ensureCloudinary();

  const safeSlug =
    String(fileName || type)
      .replace(/\.[a-z0-9]+$/i, "")
      .toLowerCase()
      .replace(/[^a-z0-9_-]+/g, "-")
      .replace(/^-+|-+$/g, "")
      .slice(0, 48) || type;

  const uniqueSuffix = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`;
  const publicId =
    type === "pdf"
      ? `${rule.folder}/${safeSlug}-${uniqueSuffix}${ext || ".pdf"}`
      : `${rule.folder}/${safeSlug}-${uniqueSuffix}`;

  const result = await new Promise((resolve, reject) => {
    const uploadStream = cloudinary.uploader.upload_stream(
      {
        resource_type: rule.resourceType,
        public_id: publicId,
        overwrite: false,
        use_filename: false,
      },
      (err, res) => (err ? reject(err) : resolve(res))
    );
    uploadStream.end(buffer);
  });

  return {
    url: result.secure_url,
    publicId: result.public_id,
    mediaType: type,
    resourceType: rule.resourceType,
    bytes: Number(result.bytes || buffer.length || 0),
    durationSeconds: Math.max(0, Math.round(Number(result.duration || 0))),
  };
}

export function createSignedCourseMediaUploadParams({ mediaType }) {
  const { cloudName, apiKey, apiSecret } = getCloudinaryCredentials();
  return buildSignedUploadParams({
    mediaType,
    cloudName,
    apiKey,
    apiSecret,
  });
}

export async function deleteCourseMedia(publicId, mediaType = "image") {
  const cleanId = String(publicId || "").trim();
  if (!cleanId) return;
  try {
    ensureCloudinary();
  } catch {
    return;
  }
  const type = String(mediaType || "image").trim().toLowerCase();
  const resourceType = MEDIA_UPLOAD_RULES[type]?.resourceType || "image";
  try {
    await cloudinary.uploader.destroy(cleanId, {
      resource_type: resourceType,
      invalidate: true,
    });
  } catch (err) {
    console.error(`Cloudinary destroy failed for ${cleanId}:`, err?.message || err);
  }
}
