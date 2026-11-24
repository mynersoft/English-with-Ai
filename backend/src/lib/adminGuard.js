import { connectDB } from "@/lib/db";
import { adminAuth } from "@/lib/firebaseAdmin";
import User from "@/models/User";

// ---------- Admin guard (Bearer token or x-admin-key) ----------
// Kept separate from TTS/Cloudinary SDKs so lightweight routes (/patterns, /admin/me)
// do not pay cold-start import/init costs for audio libraries.
export async function isAdmin(req) {
  const adminKey = process.env.ADMIN_KEY;
  if (adminKey && req.headers.get("x-admin-key") === adminKey) {
    return true;
  }

  const header = req.headers.get("authorization") || "";
  const match = header.match(/^Bearer (.+)$/i);
  if (!match) return false;

  try {
    // Local JWT verification against cached Google public keys (<1ms vs ~250ms remote revocation check)
    const decoded = await adminAuth().verifyIdToken(match[1], false);
    if (decoded.admin === true) return true;

    const allowedEmails = (process.env.ADMIN_EMAILS || "")
      .split(",")
      .map((e) => e.trim().toLowerCase())
      .filter(Boolean);
    if (decoded.email && decoded.email_verified && allowedEmails.includes(decoded.email.toLowerCase())) {
      return true;
    }

    await connectDB();
    const user = await User.findOne({ firebaseUid: decoded.uid })
      .select("status role")
      .lean();
    return Boolean(user && user.status === "active" && user.role === "admin");
  } catch {
    return false;
  }
}
