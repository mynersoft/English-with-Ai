import mongoose from "mongoose";

const moduleSchema = new mongoose.Schema(
  {
    courseId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Course",
      required: true,
      index: true,
    },
    title: { type: String, required: true, trim: true, maxlength: 160 },
    description: { type: String, default: "", trim: true, maxlength: 1500 },
    order: { type: Number, required: true, default: 1, min: 0 },
    isPublished: { type: Boolean, default: false, index: true },
  },
  { timestamps: true }
);

moduleSchema.index({ courseId: 1, order: 1, createdAt: 1 });
moduleSchema.index({ courseId: 1, isPublished: 1, order: 1 });
moduleSchema.index({ updatedAt: -1 });

export default mongoose.models.Module || mongoose.model("Module", moduleSchema);
