import mongoose from "mongoose";

const readingCategorySchema = new mongoose.Schema(
  {
    name: { type: String, required: true, trim: true, maxlength: 80 },
    slug: { type: String, required: true, unique: true, lowercase: true, trim: true },
    description: { type: String, default: "", trim: true, maxlength: 240 },
    icon: { type: String, default: "menu_book", trim: true },
    sortOrder: { type: Number, default: 0 },
    isActive: { type: Boolean, default: true, index: true },
  },
  { timestamps: true }
);

export default mongoose.models.ReadingCategory ||
  mongoose.model("ReadingCategory", readingCategorySchema);
