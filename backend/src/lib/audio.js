import textToSpeech from "@google-cloud/text-to-speech";
import { v2 as cloudinary } from "cloudinary";
import { connectDB } from "@/lib/db";
import { adminAuth } from "@/lib/firebaseAdmin";
import User from "@/models/User";

// ---------- DB ----------
export { connectDB };

// ---------- Admin guard (Bearer token or x-admin-key) ----------
export async function isAdmin(req) {
  const adminKey = process.env.ADMIN_KEY;
  if (adminKey && req.headers.get("x-admin-key") === adminKey) {
    return true;
  }

  const header = req.headers.get("authorization") || "";
  const match = header.match(/^Bearer (.+)$/i);
  if (!match) return false;

  try {
    const decoded = await adminAuth().verifyIdToken(match[1], true);
    if (decoded.admin === true) return true;

    const allowedEmails = (process.env.ADMIN_EMAILS || "")
      .split(",")
      .map((e) => e.trim().toLowerCase())
      .filter(Boolean);
    if (decoded.email && decoded.email_verified && allowedEmails.includes(decoded.email.toLowerCase())) {
      return true;
    }

    await connectDB();
    const user = await User.findOne({ firebaseUid: decoded.uid }).lean();
    return Boolean(user && user.status === "active" && user.role === "admin");
  } catch {
    return false;
  }
}

// ---------- Google TTS ----------
const client = new textToSpeech.TextToSpeechClient({
  credentials: JSON.parse(process.env.GOOGLE_TTS_CREDENTIALS || "{}"),
});

const VOICES = {
  en: { languageCode: "en-US", name: "en-US-Neural2-F" },
  bn: { languageCode: "bn-IN", name: "bn-IN-Wavenet-A" },
};

// "I'd like to + Verb" -> "I'd like to, plus Verb"; "আমি ... করতে চাই" -> no "..."
export function cleanForSpeech(text) {
  return text
    .replace(/\.\.\./g, " ")
    .replace(/\+/g, ", plus ")
    .replace(/[()]/g, ", ")
    .replace(/\s+/g, " ")
    .trim();
}

export async function synthesize(text, lang) {
  const [res] = await client.synthesizeSpeech({
    input: { text: cleanForSpeech(text) },
    voice: VOICES[lang],
    audioConfig: { audioEncoding: "MP3", speakingRate: lang === "en" ? 0.9 : 0.95 },
  });
  return Buffer.from(res.audioContent);
}

// ---------- Cloudinary ----------
cloudinary.config({
  cloud_name: process.env.CLOUDINARY_CLOUD_NAME,
  api_key: process.env.CLOUDINARY_API_KEY,
  api_secret: process.env.CLOUDINARY_API_SECRET,
});

// Cloudinary stores audio under resource_type "video"
export function uploadAudio(buffer, publicId) {
  return new Promise((resolve, reject) => {
    cloudinary.uploader
      .upload_stream(
        { resource_type: "video", public_id: publicId, overwrite: true, invalidate: true },
        (err, result) => (err ? reject(err) : resolve(result))
      )
      .end(buffer);
  });
}

export async function deleteAudio(publicId) {
  if (!publicId) return;
  try {
    await cloudinary.uploader.destroy(publicId, { resource_type: "video", invalidate: true });
  } catch (e) {
    console.error("cloudinary delete failed", e);
  }
}

// ---------- Audio "slots" of a pattern ----------
// Every piece of text that can have audio: title, meaning, each example (en + bn)
export function listSlots(p) {
  const slots = [
    { key: "title", text: p.title, lang: "en", holder: p, field: "titleAudio" },
    { key: "meaning", text: p.meaning, lang: "bn", holder: p, field: "meaningAudio" },
  ];
  p.examples.forEach((ex) => {
    slots.push({ key: `ex_${ex._id}_en`, text: ex.en, lang: "en", holder: ex, field: "enAudio" });
    slots.push({ key: `ex_${ex._id}_bn`, text: ex.bn, lang: "bn", holder: ex, field: "bnAudio" });
  });
  return slots;
}

// body: { target: "all" | "title" | "meaning" | "example", index, lang }
export function pickSlots(p, { target, index, lang }) {
  const all = listSlots(p);
  if (target === "all") return all;
  if (target === "title") return all.filter((s) => s.key === "title");
  if (target === "meaning") return all.filter((s) => s.key === "meaning");
  if (target === "example") {
    const ex = p.examples[index];
    if (!ex) return [];
    return all.filter((s) => s.key === `ex_${ex._id}_${lang}`);
  }
  return [];
}

export const publicIdFor = (patternId, key) => `patterns/${patternId}/${key}`;
