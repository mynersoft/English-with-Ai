import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { AppError } from "@/lib/errors";
import { uploadStudentAvatar } from "@/lib/cloudinaryMedia";
import User from "@/models/User";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// POST /api/v1/student/avatar -> upload/update the authenticated student's profile avatar
export const POST = withAuth(
  async (request, { user }) => {
    let formData;
    try {
      formData = await request.formData();
    } catch {
      throw new AppError(400, "INVALID_FORM_DATA", "Expected multipart/form-data file upload.");
    }

    const file = formData.get("file");
    if (!file || typeof file.arrayBuffer !== "function") {
      throw new AppError(400, "MISSING_FILE", "A photo file is required.");
    }

    const arrayBuffer = await file.arrayBuffer();
    const buffer = Buffer.from(arrayBuffer);

    const uploaded = await uploadStudentAvatar({
      buffer,
      mimeType: file.type || "",
      fileName: file.name || "avatar.jpg",
      userId: user.firebaseUid || String(user._id),
    });

    await User.findByIdAndUpdate(
      user._id,
      { $set: { photoUrl: uploaded.url } },
      { new: true }
    );

    return ok({
      photoUrl: uploaded.url,
      publicId: uploaded.publicId,
      storage: uploaded.storage,
      bytes: uploaded.bytes,
    });
  },
  { limit: 20 }
);

// DELETE /api/v1/student/avatar -> remove the authenticated student's profile avatar
export const DELETE = withAuth(
  async (_request, { user }) => {
    await User.findByIdAndUpdate(
      user._id,
      { $set: { photoUrl: null } },
      { new: true }
    );
    return ok({ photoUrl: null });
  },
  { limit: 20 }
);
