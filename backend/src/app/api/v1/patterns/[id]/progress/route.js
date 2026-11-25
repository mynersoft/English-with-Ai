import { NextResponse } from "next/server";
import mongoose from "mongoose";
import { withAuth } from "@/middleware/auth";
import { AppError } from "@/lib/errors";
import Pattern from "@/models/Pattern";
import UserPatternProgress from "@/models/UserPatternProgress";
import { recordStudentActivity } from "@/modules/student/student.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// POST /api/v1/patterns/:id/progress -> mark or unmark a sentence pattern as learned
export const POST = withAuth(async (req, { params, user }) => {
  const { id } = await params;
  if (!id || !mongoose.Types.ObjectId.isValid(id)) {
    throw new AppError(400, "INVALID_PATTERN_ID", "Invalid pattern ID.");
  }

  const body = await req.json().catch(() => ({}));
  const completed = body?.completed === undefined ? true : Boolean(body.completed);

  const exists = await Pattern.exists({ _id: id });
  if (!exists) {
    throw new AppError(404, "PATTERN_NOT_FOUND", "Sentence pattern not found.");
  }

  const userId = user.firebaseUid;
  const now = new Date();

  await UserPatternProgress.findOneAndUpdate(
    { userId, patternId: id },
    {
      $set: {
        isCompleted: completed,
        completedAt: completed ? now : null,
      },
    },
    { upsert: true, new: true }
  );

  if (completed) {
    await recordStudentActivity({ userDoc: user, type: "pattern", patternId: id, now });
  }

  const completedCount = await UserPatternProgress.countDocuments({
    userId,
    isCompleted: true,
  });

  return NextResponse.json({
    ok: true,
    patternId: id,
    isCompleted: completed,
    completedCount,
  });
});
