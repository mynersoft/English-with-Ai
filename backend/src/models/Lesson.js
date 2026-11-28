import mongoose from "mongoose";

const lessonContentBlockSchema = new mongoose.Schema(
  {
    id: { type: String, required: true, trim: true },
    type: {
      type: String,
      enum: ["video", "pdf", "text", "image"],
      required: true,
    },
    title: { type: String, default: "", trim: true, maxlength: 200 },
    description: { type: String, default: "", trim: true, maxlength: 1000 },
    url: { type: String, default: "", trim: true },
    publicId: { type: String, default: "", trim: true },
    text: { type: String, default: "" },
    alt: { type: String, default: "", trim: true, maxlength: 300 },
    durationSeconds: { type: Number, default: 0, min: 0 },
    order: { type: Number, required: true, default: 1, min: 0 },
    isDownloadable: { type: Boolean, default: false },
  },
  { _id: false }
);

const lessonSchema = new mongoose.Schema(
  {
    courseId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Course",
      required: true,
      index: true,
    },
    moduleId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Module",
      required: true,
      index: true,
    },
    title: { type: String, required: true, trim: true, maxlength: 160 },
    subtitle: { type: String, default: "", trim: true, maxlength: 240 },
    description: { type: String, default: "", trim: true, maxlength: 3000 },
    order: { type: Number, required: true, default: 1, min: 0 },
    estimatedDurationMinutes: { type: Number, default: 10, min: 1, max: 600 },
    isPremium: { type: Boolean, default: false, index: true },
    isPublished: { type: Boolean, default: false, index: true },
    content: { type: [lessonContentBlockSchema], default: [] },
    createdByUid: { type: String, default: null },
  },
  { timestamps: true }
);

lessonSchema.index({ courseId: 1, moduleId: 1, order: 1, createdAt: 1 });
lessonSchema.index({ moduleId: 1, isPublished: 1, order: 1 });
lessonSchema.index({ courseId: 1, isPublished: 1, updatedAt: -1 });

export default mongoose.models.Lesson || mongoose.model("Lesson", lessonSchema);
