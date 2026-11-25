import mongoose from "mongoose";

const readingSentenceSchema = new mongoose.Schema(
  {
    sentenceId: { type: String, required: true, trim: true },
    order: { type: Number, required: true, min: 0 },
    en: { type: String, required: true, trim: true },
    bn: { type: String, default: "", trim: true },
  },
  { _id: false }
);

const readingVocabItemSchema = new mongoose.Schema(
  {
    word: { type: String, required: true, trim: true, maxlength: 100 },
    meaningBn: { type: String, default: "", trim: true, maxlength: 220 },
    pronunciation: { type: String, default: "", trim: true, maxlength: 120 },
    partOfSpeech: { type: String, default: "", trim: true, maxlength: 40 },
    example: { type: String, default: "", trim: true, maxlength: 300 },
  },
  { _id: false }
);

const readingQuestionSchema = new mongoose.Schema(
  {
    questionId: { type: String, required: true, trim: true },
    type: {
      type: String,
      enum: ["mcq", "true_false", "short_answer"],
      required: true,
    },
    prompt: { type: String, required: true, trim: true, maxlength: 500 },
    options: { type: [String], default: [] },
    correctAnswer: { type: String, required: true, trim: true, maxlength: 300 },
    explanationBn: { type: String, default: "", trim: true, maxlength: 600 },
  },
  { _id: false }
);

const readingPassageSchema = new mongoose.Schema(
  {
    title: { type: String, required: true, trim: true, maxlength: 160 },
    slug: { type: String, required: true, unique: true, lowercase: true, trim: true },
    level: {
      type: String,
      enum: ["A1", "A2", "B1", "B2", "C1", "C2"],
      default: "A1",
      index: true,
    },
    categoryId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "ReadingCategory",
      required: true,
      index: true,
    },
    summary: { type: String, default: "", trim: true, maxlength: 300 },
    englishText: { type: String, required: true, trim: true },
    bengaliTranslation: { type: String, default: "", trim: true },
    audioUrl: { type: String, default: null, trim: true },
    estimatedMinutes: { type: Number, default: 3, min: 1, max: 60 },
    sentences: { type: [readingSentenceSchema], default: [] },
    vocabulary: { type: [readingVocabItemSchema], default: [] },
    questions: { type: [readingQuestionSchema], default: [] },
    isPublished: { type: Boolean, default: false, index: true },
    sortOrder: { type: Number, default: 0 },
    createdByUid: { type: String, default: null },
  },
  { timestamps: true }
);

readingPassageSchema.index({ isPublished: 1, level: 1, categoryId: 1, sortOrder: 1, _id: -1 });
readingPassageSchema.index({ title: "text", summary: "text" });

export default mongoose.models.ReadingPassage ||
  mongoose.model("ReadingPassage", readingPassageSchema);
