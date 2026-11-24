import { NextResponse } from "next/server";
import Pattern from "@/models/Pattern";
import { connectDB, isAdmin, listSlots } from "@/lib/audio";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// GET /api/v1/patterns  -> list screen (title, meaning, premium, count, audio)
export async function GET() {
  try {
    await connectDB();
    const patterns = await Pattern.find().sort({ order: 1, createdAt: 1 }).lean();
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
          sentenceCount: p.examples.length,
          audioTotal: slots.length,
          audioReady,
          audioStale,
          titleAudioUrl: p.titleAudio?.url ?? null,
          meaningAudioUrl: p.meaningAudio?.url ?? null,
        };
      })
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
    if (!(await isAdmin(req))) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
    await connectDB();
    const body = await req.json();
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