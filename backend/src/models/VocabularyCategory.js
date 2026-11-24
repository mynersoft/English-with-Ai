import mongoose from "mongoose";

const vocabularyCategorySchema = new mongoose.Schema(
  {
    name: { type: String, required: true, trim: true, maxlength: 80 },
    slug: { type: String, required: true, unique: true, lowercase: true, trim: true },
    description: { type: String, default: "", trim: true, maxlength: 300 },
    icon: { type: String, default: "menu_book", trim: true },
    image: { type: String, default: null },
    parentId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "VocabularyCategory",
      default: null,
      index: true,
    },
    sortOrder: { type: Number, default: 0 },
    isActive: { type: Boolean, default: true },
  },
  { timestamps: true }
);

vocabularyCategorySchema.index({ isActive: 1, sortOrder: 1, name: 1 });
vocabularyCategorySchema.index({ parentId: 1, sortOrder: 1 });

export default mongoose.models.VocabularyCategory ||
  mongoose.model("VocabularyCategory", vocabularyCategorySchema);
