import mongoose from "mongoose";

const lessonProgressSchema = new mongoose.Schema(
  {
    userId: { type: String, required: true, trim: true }, // Firebase UID from verified token
    courseId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Course",
      required: true,
    },
    moduleId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Module",
      required: true,
    },
    lessonId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Lesson",
      required: true,
    },
    isCompleted: { type: Boolean, default: false },
    progressPercentage: { type: Number, default: 0, min: 0, max: 100 },
    lastContentId: { type: String, default: "", trim: true },
    lastPositionSeconds: { type: Number, default: 0, min: 0 },
    startedAt: { type: Date, default: Date.now },
    completedAt: { type: Date, default: null },
  },
  { timestamps: true }
);

lessonProgressSchema.index({ userId: 1, lessonId: 1 }, { unique: true });
lessonProgressSchema.index({ userId: 1, courseId: 1, updatedAt: -1 });
lessonProgressSchema.index({ courseId: 1, isCompleted: 1 });

export default mongoose.models.LessonProgress ||
  mongoose.model("LessonProgress", lessonProgressSchema);
