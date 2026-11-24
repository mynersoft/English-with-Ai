import textToSpeech from "@google-cloud/text-to-speech";
import { v2 as cloudinary } from "cloudinary";
import { connectDB } from "@/lib/db";
import { isAdmin } from "@/lib/adminGuard";
import { listSlots, pickSlots, publicIdFor } from "@/lib/patternSlots";

export { connectDB, isAdmin, listSlots, pickSlots, publicIdFor };

// ---------- Lazy Google TTS singleton ----------
let ttsClient = null;
function getTtsClient() {
  if (!ttsClient) {
    ttsClient = new textToSpeech.TextToSpeechClient({
      credentials: JSON.parse(process.env.GOOGLE_TTS_CREDENTIALS || "{}"),
    });
  }
  return ttsClient;
}

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
  const [res] = await getTtsClient().synthesizeSpeech({
    input: { text: cleanForSpeech(text) },
    voice: VOICES[lang],
    audioConfig: { audioEncoding: "MP3", speakingRate: lang === "en" ? 0.9 : 0.95 },
  });
  return Buffer.from(res.audioContent);
}

// ---------- Lazy Cloudinary config ----------
let cloudinaryReady = false;
function ensureCloudinary() {
  if (!cloudinaryReady) {
    cloudinary.config({
      cloud_name: process.env.CLOUDINARY_CLOUD_NAME,
      api_key: process.env.CLOUDINARY_API_KEY,
      api_secret: process.env.CLOUDINARY_API_SECRET,
    });
    cloudinaryReady = true;
  }
}

// Cloudinary stores audio under resource_type "video"
export function uploadAudio(buffer, publicId) {
  ensureCloudinary();
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
  ensureCloudinary();
  try {
    await cloudinary.uploader.destroy(publicId, { resource_type: "video", invalidate: true });
  } catch (e) {
    console.error("cloudinary delete failed", e);
  }
}
