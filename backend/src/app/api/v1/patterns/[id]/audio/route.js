import { NextResponse } from "next/server";
import Pattern from "@/models/Pattern";
import {
  connectDB,
  isAdmin,
  synthesize,
  uploadAudio,
  deleteAudio,
  pickSlots,
  publicIdFor,
} from "@/lib/audio";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 60;

const unauthorized = () => NextResponse.json({ error: "Unauthorized" }, { status: 401 });

async function runWithConcurrency(items, concurrency, worker) {
  let cursor = 0;
  const runners = Array.from({ length: Math.min(concurrency, items.length) }, async () => {
    while (cursor < items.length) {
      const idx = cursor++;
      await worker(items[idx]);
    }
  });
  await Promise.all(runners);
}

// POST /api/v1/patterns/:id/audio  -> generate audio with Google TTS
// body: { target: "all"|"title"|"meaning"|"example", index?, lang?: "en"|"bn", force?: boolean }
export async function POST(req, { params }) {
  try {
    const [{ id }, adminOk, body] = await Promise.all([
      params,
      isAdmin(req),
      req.json().catch(() => null),
    ]);
    if (!adminOk) return unauthorized();
    if (!body) return NextResponse.json({ error: "Invalid JSON body" }, { status: 400 });

    await connectDB();
    const force = !!body.force;
    const p = await Pattern.findById(id);
    if (!p) return NextResponse.json({ error: "Not found" }, { status: 404 });

    const slots = pickSlots(p, body);
    if (!slots.length) return NextResponse.json({ error: "Invalid target" }, { status: 400 });

    const generated = [];
    const skipped = [];
    const toGenerate = [];

    for (const s of slots) {
      const a = s.holder[s.field];
      const blocked = a?.source === "manual" && !force;
      const should = body.target === "all" ? !a?.url || a.stale || force : true;
      if (blocked || !should) {
        skipped.push(s.key);
      } else {
        toGenerate.push(s);
      }
    }

    await runWithConcurrency(toGenerate, 4, async (s) => {
      const buffer = await synthesize(s.text, s.lang);
      const result = await uploadAudio(buffer, publicIdFor(id, s.key));
      s.holder[s.field] = {
        url: result.secure_url,
        publicId: result.public_id,
        source: "tts",
        stale: false,
      };
      generated.push(s.key);
    });

    if (generated.length > 0) {
      await p.save();
    }
    return NextResponse.json({ generated, skipped });
  } catch (err) {
    console.error("POST /api/v1/patterns/[id]/audio failed:", err);
    return NextResponse.json(
      { error: err?.message || "Failed to generate audio" },
      { status: err?.status || 500 }
    );
  }
}

// PUT /api/v1/patterns/:id/audio  -> upload your own recorded mp3 (multipart/form-data)
export async function PUT(req, { params }) {
  try {
    const [{ id }, adminOk] = await Promise.all([params, isAdmin(req)]);
    if (!adminOk) return unauthorized();

    const [, form] = await Promise.all([connectDB(), req.formData()]);
    const file = form.get("file");
    const target = form.get("target");
    const index = form.get("index") !== null ? Number(form.get("index")) : undefined;
    const lang = form.get("lang") || undefined;
    if (!file) return NextResponse.json({ error: "file required" }, { status: 400 });

    const p = await Pattern.findById(id);
    if (!p) return NextResponse.json({ error: "Not found" }, { status: 404 });
    const [s] = pickSlots(p, { target, index, lang });
    if (!s) return NextResponse.json({ error: "Invalid target" }, { status: 400 });

    const buffer = Buffer.from(await file.arrayBuffer());
    const result = await uploadAudio(buffer, publicIdFor(id, s.key));
    s.holder[s.field] = {
      url: result.secure_url,
      publicId: result.public_id,
      source: "manual",
      stale: false,
    };
    await p.save();
    return NextResponse.json({ key: s.key, url: result.secure_url });
  } catch (err) {
    console.error("PUT /api/v1/patterns/[id]/audio failed:", err);
    return NextResponse.json(
      { error: err?.message || "Failed to upload audio" },
      { status: err?.status || 500 }
    );
  }
}

// DELETE /api/v1/patterns/:id/audio  -> remove audio. body: { target, index?, lang? }
export async function DELETE(req, { params }) {
  try {
    const [{ id }, adminOk, body] = await Promise.all([
      params,
      isAdmin(req),
      req.json().catch(() => null),
    ]);
    if (!adminOk) return unauthorized();
    if (!body) return NextResponse.json({ error: "Invalid JSON body" }, { status: 400 });

    await connectDB();
    const p = await Pattern.findById(id);
    if (!p) return NextResponse.json({ error: "Not found" }, { status: 404 });
    const [s] = pickSlots(p, body);
    if (!s) return NextResponse.json({ error: "Invalid target" }, { status: 400 });

    const publicId = s.holder[s.field]?.publicId;
    s.holder[s.field] = undefined;
    await Promise.all([deleteAudio(publicId), p.save()]);
    return NextResponse.json({ ok: true });
  } catch (err) {
    console.error("DELETE /api/v1/patterns/[id]/audio failed:", err);
    return NextResponse.json(
      { error: err?.message || "Failed to delete audio" },
      { status: err?.status || 500 }
    );
  }
}
