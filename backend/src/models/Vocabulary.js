import mongoose from "mongoose";

const vocabularyExampleSchema = new mongoose.Schema(
  {
    en: { type: String, required: true, trim: true },
    bn: { type: String, required: true, trim: true },
  },
  { _id: false }
);

const vocabularySchema = new mongoose.Schema(
  {
    word: { type: String, required: true, trim: true, maxlength: 100 },
    slug: { type: String, required: true, unique: true, lowercase: true, trim: true },
    meaning: { type: String, required: true, trim: true, maxlength: 200 },
    pronunciation: { type: String, default: "", trim: true, maxlength: 120 },
    audioUrl: { type: String, default: null },
    imageUrl: { type: String, default: null },
    partOfSpeech: {
      type: String,
      default: "noun",
      trim: true,
      lowercase: true,
    },
    level: {
      type: String,
      enum: ["A1", "A2", "B1", "B2", "C1", "C2"],
      default: "A1",
      index: true,
    },
    categoryId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "VocabularyCategory",
      required: true,
      index: true,
    },
    examples: { type: [vocabularyExampleSchema], default: [] },
    synonyms: { type: [String], default: [] },
    antonyms: { type: [String], default: [] },
    tags: { type: [String], default: [] },
    difficulty: { type: Number, default: 1, min: 1, max: 5 },
    isPublished: { type: Boolean, default: true, index: true },
    sortOrder: { type: Number, default: 0 },
  },
  { timestamps: true }
);

vocabularySchema.index({ isPublished: 1, level: 1, categoryId: 1, sortOrder: 1 });
vocabularySchema.index({ isPublished: 1, sortOrder: 1, _id: 1 });
vocabularySchema.index({ word: 1 });

export default mongoose.models.Vocabulary ||
  mongoose.model("Vocabulary", vocabularySchema);
