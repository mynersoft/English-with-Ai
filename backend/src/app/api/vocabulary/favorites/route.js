import { ok } from "@/lib/response";
import { withAuth } from "@/middleware/auth";
import { readJson } from "@/validators/auth.validator";
import Vocabulary from "@/models/Vocabulary";
import UserVocabularyProgress from "@/models/UserVocabularyProgress";
import { AppError } from "@/lib/errors";
import { assertObjectId } from "@/modules/vocabulary/vocabulary.service";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/vocabulary/favorites?page=1&limit=30
export const GET = withAuth(async (request, { decoded }) => {
  const { searchParams } = new URL(request.url);
  const page = Math.max(1, Number(searchParams.get("page")) || 1);
  const limit = Math.max(1, Math.min(50, Number(searchParams.get("limit")) || 30));
  const skip = (page - 1) * limit;

  const filter = { userId: decoded.uid, isFavorite: true };
  const [rows, total] = await Promise.all([
    UserVocabularyProgress.find(filter)
      .sort({ updatedAt: -1 })
      .skip(skip)
      .limit(limit)
      .populate({
        path: "vocabularyId",
        match: { isPublished: true },
        populate: { path: "categoryId", select: "name" },
      })
      .lean(),
    UserVocabularyProgress.countDocuments(filter),
  ]);

  const items = rows
    .filter((r) => Boolean(r.vocabularyId))
    .map((r) => {
      const w = r.vocabularyId;
      return {
        id: String(w._id),
        word: w.word,
        meaning: w.meaning,
        pronunciation: w.pronunciation || "",
        audioUrl: w.audioUrl || null,
        partOfSpeech: w.partOfSpeech || "noun",
        level: w.level || "A1",
        categoryName: w.categoryId?.name || "",
        examples: (w.examples || []).map((e) => ({ en: e.en, bn: e.bn })),
        synonyms: w.synonyms || [],
        antonyms: w.antonyms || [],
        status: r.status || "new",
        isFavorite: true,
      };
    });

  return ok({ items, page, limit, total });
}, { limit: 60 });

// POST /api/vocabulary/favorites -> body: { vocabularyId, isFavorite? }
export const POST = withAuth(async (request, { decoded }) => {
  const body = await readJson(request);
  const vocabId = assertObjectId(body?.vocabularyId, "vocabularyId");

  const word = await Vocabulary.findOne({ _id: vocabId, isPublished: true }).select("_id").lean();
  if (!word) {
    throw new AppError(404, "WORD_NOT_FOUND", "Vocabulary word not found.");
  }

  const existing = await UserVocabularyProgress.findOne({
    userId: decoded.uid,
    vocabularyId: vocabId,
  })
    .select("isFavorite")
    .lean();

  const nextFav =
    typeof body?.isFavorite === "boolean" ? body.isFavorite : !Boolean(existing?.isFavorite);

  const updated = await UserVocabularyProgress.findOneAndUpdate(
    { userId: decoded.uid, vocabularyId: vocabId },
    {
      $setOnInsert: { userId: decoded.uid, vocabularyId: vocabId },
      $set: { isFavorite: nextFav },
    },
    { upsert: true, new: true, setDefaultsOnInsert: true }
  ).lean();

  return ok({ vocabularyId: String(vocabId), isFavorite: Boolean(updated.isFavorite) });
}, { limit: 60 });
