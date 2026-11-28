import { ok } from "@/lib/response";
import { withAdmin } from "@/lib/adminGuard";
import { AppError } from "@/lib/errors";
import {
  createSignedCourseMediaUploadParams,
  deleteCourseMedia,
  uploadCourseMedia,
} from "@/lib/cloudinaryMedia";
import { readJson } from "@/validators/auth.validator";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const POST = withAdmin(async (req) => {
  const { searchParams } = new URL(req.url);
  const contentType = String(req.headers.get("content-type") || "").toLowerCase();

  if (searchParams.get("mode") === "sign" || contentType.includes("application/json")) {
    const body = await readJson(req);
    const mediaType = body.mediaType || searchParams.get("mediaType") || "image";
    const signed = createSignedCourseMediaUploadParams({ mediaType });
    return ok(signed);
  }

  let formData;
  try {
    formData = await req.formData();
  } catch {
    throw new AppError(400, "INVALID_FORM_DATA", "Expected multipart/form-data file upload.");
  }

  const file = formData.get("file");
  const mediaType = String(formData.get("mediaType") || searchParams.get("mediaType") || "").trim();

  if (!file || typeof file.arrayBuffer !== "function") {
    throw new AppError(400, "MISSING_FILE", "A file field is required in multipart/form-data.");
  }

  const arrayBuffer = await file.arrayBuffer();
  const buffer = Buffer.from(arrayBuffer);
  const uploaded = await uploadCourseMedia({
    buffer,
    mediaType,
    mimeType: file.type || "",
    fileName: file.name || `${mediaType || "asset"}`,
  });

  return ok(uploaded, 201);
}, { limit: 30 });

export const DELETE = withAdmin(async (req) => {
  const { searchParams } = new URL(req.url);
  let publicId = searchParams.get("publicId") || "";
  let mediaType = searchParams.get("mediaType") || "image";

  if (!publicId) {
    const body = await readJson(req);
    publicId = String(body.publicId || "").trim();
    mediaType = String(body.mediaType || mediaType).trim();
  }

  if (!publicId) {
    throw new AppError(400, "VALIDATION_ERROR", "publicId is required to delete media.");
  }

  await deleteCourseMedia(publicId, mediaType);
  return ok({ deleted: true, publicId });
}, { limit: 30 });
