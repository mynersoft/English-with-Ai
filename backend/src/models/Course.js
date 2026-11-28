import mongoose from "mongoose";

const courseSchema = new mongoose.Schema(
  {
    title: { type: String, required: true, trim: true, maxlength: 160 },
    slug: { type: String, required: true, unique: true, lowercase: true, trim: true },
    subtitle: { type: String, default: "", trim: true, maxlength: 240 },
    description: { type: String, default: "", trim: true, maxlength: 4000 },
    thumbnailUrl: { type: String, default: "", trim: true },
    thumbnailPublicId: { type: String, default: "", trim: true },
    level: {
      type: String,
      enum: ["Beginner", "Intermediate", "Advanced"],
      default: "Beginner",
      index: true,
    },
    category: { type: String, required: true, default: "General", trim: true, maxlength: 80, index: true },
    durationDays: { type: Number, default: 30, min: 1, max: 365 },
    isPremium: { type: Boolean, default: false, index: true },
    price: { type: Number, default: 0, min: 0 },
    isPublished: { type: Boolean, default: false, index: true },
    totalModules: { type: Number, default: 0, min: 0 },
    totalLessons: { type: Number, default: 0, min: 0 },
    createdByUid: { type: String, default: null },
  },
  { timestamps: true }
);

courseSchema.index({ isPublished: 1, level: 1, category: 1, createdAt: -1 });
courseSchema.index({ isPublished: 1, updatedAt: -1 });
courseSchema.index({ title: "text", subtitle: "text", description: "text", category: "text" });

export default mongoose.models.Course || mongoose.model("Course", courseSchema);
