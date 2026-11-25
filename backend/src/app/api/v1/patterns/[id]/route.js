import { NextResponse } from "next/server";
import Pattern from "@/models/Pattern";
import UserPatternProgress from "@/models/UserPatternProgress";
import { connectDB } from "@/lib/db";
import { isAdmin } from "@/lib/adminGuard";
import { optionalAuthUid } from "@/middleware/auth";
import { listSlots } from "@/lib/patternSlots";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const audioInfo = (a) => ({
  url: a?.url ?? null,
  source: a?.source ?? null,
  stale: a?.stale ?? false,
});

// GET /api/v1/patterns/:id -> full pattern for the sentence screen
export async function GET(req, { params }) {
  try {
    const [{ id }, , userId] = await Promise.all([params, connectDB(), optionalAuthUid(req)]);
    const [p, completedDoc] = await Promise.all([
      Pattern.findById(id).lean(),
      userId
        ? UserPatternProgress.exists({ userId, patternId: id, isCompleted: true })
        : Promise.resolve(null),
    ]);
    if (!p) return NextResponse.json({ error: "Not found" }, { status: 404 });

    return NextResponse.json(
      {
        id: p._id,
        title: p.title,
        meaning: p.meaning,
        explanation: p.explanation ?? "",
        isPremium: p.isPremium,
        order: p.order ?? 0,
        isCompleted: Boolean(completedDoc),
        titleAudio: audioInfo(p.titleAudio),
        meaningAudio: audioInfo(p.meaningAudio),
        examples: (p.examples || []).map((e) => ({
          id: e._id,
          en: e.en,
          bn: e.bn,
          enAudio: audioInfo(e.enAudio),
          bnAudio: audioInfo(e.bnAudio),
        })),
      },
      { headers: { "Cache-Control": "no-store" } }
    );
  } catch (err) {
    console.error("GET /api/v1/patterns/[id] failed:", err);
    return NextResponse.json(
      { error: err?.message || "Failed to load pattern" },
      { status: err?.status || 500 }
    );
  }
}

// PUT /api/v1/patterns/:id -> edit text (admin). Changed text => audio marked stale.
// Send existing examples with their "id" so their audio is kept.
export async function PUT(req, { params }) {
  try {
    const [{ id }, adminOk, body] = await Promise.all([
      params,
      isAdmin(req),
      req.json().catch(() => null),
    ]);
    if (!adminOk) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
    if (!body) return NextResponse.json({ error: "Invalid JSON body" }, { status: 400 });

    await connectDB();
    const p = await Pattern.findById(id);
    if (!p) return NextResponse.json({ error: "Not found" }, { status: 404 });

    if (body.title !== undefined && body.title !== p.title) {
      p.title = body.title;
      if (p.titleAudio?.url) p.titleAudio.stale = true;
    }
    if (body.meaning !== undefined && body.meaning !== p.meaning) {
      p.meaning = body.meaning;
      if (p.meaningAudio?.url) p.meaningAudio.stale = true;
    }
    if (body.explanation !== undefined) p.explanation = body.explanation;
    if (body.isPremium !== undefined) p.isPremium = !!body.isPremium;
    if (body.order !== undefined) p.order = body.order;

    const publicIdsToDelete = [];
    if (Array.isArray(body.examples)) {
      const oldById = new Map(p.examples.map((e) => [String(e._id), e]));
      const keptIds = new Set();
      const next = body.examples.map((inc) => {
        const old = inc.id ? oldById.get(String(inc.id)) : null;
        if (!old) return { en: inc.en, bn: inc.bn }; // new example
        keptIds.add(String(old._id));
        const ex = old.toObject();
        if (inc.en !== old.en) {
          ex.en = inc.en;
          if (ex.enAudio?.url) ex.enAudio.stale = true;
        }
        if (inc.bn !== old.bn) {
          ex.bn = inc.bn;
          if (ex.bnAudio?.url) ex.bnAudio.stale = true;
        }
        return ex;
      });

      // collect audio publicIds of removed examples for parallel cleanup
      for (const old of p.examples) {
        if (!keptIds.has(String(old._id))) {
          if (old.enAudio?.publicId) publicIdsToDelete.push(old.enAudio.publicId);
          if (old.bnAudio?.publicId) publicIdsToDelete.push(old.bnAudio.publicId);
        }
      }
      p.examples = next;
    }

    if (publicIdsToDelete.length > 0) {
      const { deleteAudio } = await import("@/lib/audio");
      await Promise.all([
        p.save(),
        ...publicIdsToDelete.map((pid) => deleteAudio(pid)),
      ]);
    } else {
      await p.save();
    }

    return NextResponse.json({ ok: true });
  } catch (err) {
    console.error("PUT /api/v1/patterns/[id] failed:", err);
    return NextResponse.json(
      { error: err?.message || "Failed to update pattern" },
      { status: err?.status || 500 }
    );
  }
}

// DELETE /api/v1/patterns/:id -> remove pattern + all its audio (admin)
export async function DELETE(req, { params }) {
  try {
    const [{ id }, adminOk] = await Promise.all([params, isAdmin(req)]);
    if (!adminOk) return NextResponse.json({ error: "Unauthorized" }, { status: 401 });

    await connectDB();
    const p = await Pattern.findById(id);
    if (!p) return NextResponse.json({ error: "Not found" }, { status: 404 });

    const publicIds = listSlots(p)
      .map((s) => s.holder[s.field]?.publicId)
      .filter(Boolean);

    if (publicIds.length > 0) {
      const { deleteAudio } = await import("@/lib/audio");
      await Promise.all([
        p.deleteOne(),
        ...publicIds.map((pid) => deleteAudio(pid)),
      ]);
    } else {
      await p.deleteOne();
    }
    return NextResponse.json({ ok: true });
  } catch (err) {
    console.error("DELETE /api/v1/patterns/[id] failed:", err);
    return NextResponse.json(
      { error: err?.message || "Failed to delete pattern" },
      { status: err?.status || 500 }
    );
  }
}
