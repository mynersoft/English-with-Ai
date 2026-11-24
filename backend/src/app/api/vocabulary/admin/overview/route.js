import { NextResponse } from "next/server";
import { connectDB } from "@/lib/db";
import { isAdmin } from "@/lib/adminGuard";
import Vocabulary from "@/models/Vocabulary";
import VocabularyCategory from "@/models/VocabularyCategory";
import UserVocabularyProgress from "@/models/UserVocabularyProgress";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/vocabulary/admin/overview
export async function GET(req) {
  if (!(await isAdmin(req))) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }
  await connectDB();

  const [
    totalWords,
    publishedWords,
    totalCategories,
    levelAgg,
    activeLearners,
    audioMissing,
  ] = await Promise.all([
    Vocabulary.countDocuments({}),
    Vocabulary.countDocuments({ isPublished: true }),
    VocabularyCategory.countDocuments({}),
    Vocabulary.aggregate([
      { $group: { _id: "$level", count: { $sum: 1 } } },
    ]),
    UserVocabularyProgress.distinct("userId").then((ids) => ids.length),
    Vocabulary.countDocuments({ $or: [{ audioUrl: null }, { audioUrl: "" }] }),
  ]);

  return NextResponse.json({
    success: true,
    data: {
      totalWords,
      publishedWords,
      draftWords: Math.max(0, totalWords - publishedWords),
      totalCategories,
      activeLearners,
      audioMissing,
      levelBreakdown: ["A1", "A2", "B1", "B2", "C1", "C2"].map((lvl) => ({
        level: lvl,
        count: levelAgg.find((r) => r._id === lvl)?.count || 0,
      })),
    },
  });
}
