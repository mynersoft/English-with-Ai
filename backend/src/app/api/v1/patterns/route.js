import { NextResponse } from "next/server";
import Pattern from "@/models/Pattern";
import UserPatternProgress from "@/models/UserPatternProgress";
import { connectDB } from "@/lib/db";
import { isAdmin } from "@/lib/adminGuard";
import { optionalAuthUid } from "@/middleware/auth";
import { listSlots } from "@/lib/patternSlots";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/v1/patterns  -> list screen (title, meaning, premium, count, audio, isCompleted)
export async function GET(req) {
  try {
    const [, userId] = await Promise.all([connectDB(), optionalAuthUid(req)]);

    // Project only list-view fields so large explanations and sentence texts aren't transferred.
    const [patterns, completedDocs] = await Promise.all([
      Pattern.find()
        .select(
          "title meaning isPremium order titleAudio.url titleAudio.stale meaningAudio.url meaningAudio.stale examples._id examples.enAudio.url examples.enAudio.stale examples.bnAudio.url examples.bnAudio.stale"
        )
        .sort({ order: 1, createdAt: 1 })
        .lean(),
      userId
        ? UserPatternProgress.find({ userId, isCompleted: true }).select("patternId").lean()
        : Promise.resolve([]),
    ]);

    const completedIds = new Set(completedDocs.map((d) => String(d.patternId)));

    return NextResponse.json(
      patterns.map((p) => {
        const slots = listSlots(p);
        const audioReady = slots.filter((s) => Boolean(s.holder?.[s.field]?.url)).length;
        const audioStale = slots.filter((s) => Boolean(s.holder?.[s.field]?.stale)).length;
        return {
          id: p._id,
          title: p.title,
          meaning: p.meaning,
          isPremium: p.isPremium,
          order: p.order ?? 0,
          sentenceCount: (p.examples || []).length,
          audioTotal: slots.length,
          audioReady,
          audioStale,
          titleAudioUrl: p.titleAudio?.url ?? null,
          meaningAudioUrl: p.meaningAudio?.url ?? null,
          isCompleted: completedIds.has(String(p._id)),
        };
      }),
      { headers: { "Cache-Control": "no-store" } }
    );
  } catch (err) {
    console.error("GET /api/v1/patterns failed:", err);
    return NextResponse.json(
      { error: err?.message || "Failed to load patterns" },
      { status: err?.status || 500 }
    );
  }
}

// POST /api/v1/patterns  -> add new pattern (admin)
export async function POST(req) {
  try {
    const [adminOk, body] = await Promise.all([isAdmin(req), req.json().catch(() => null)]);
    if (!adminOk) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
    if (!body || !body.title || !body.meaning) {
      return NextResponse.json({ error: "title and meaning are required" }, { status: 400 });
    }
    await connectDB();
    const pattern = await Pattern.create({
      title: body.title,
      meaning: body.meaning,
      explanation: body.explanation,
      isPremium: !!body.isPremium,
      order: body.order ?? 0,
      examples: (body.examples || []).map((e) => ({ en: e.en, bn: e.bn })),
    });
    return NextResponse.json(pattern, { status: 201 });
  } catch (err) {
    console.error("POST /api/v1/patterns failed:", err);
    return NextResponse.json(
      { error: err?.message || "Failed to create pattern" },
      { status: err?.status || 500 }
    );
  }
}