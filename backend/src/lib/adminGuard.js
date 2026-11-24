import { AppError } from "@/lib/errors";
import { apiHandler } from "@/lib/apiHandler";
import { connectDB } from "@/lib/db";
import { adminAuth } from "@/lib/firebaseAdmin";
import User from "@/models/User";

export async function resolveAdminCaller(req) {
  const adminKey = process.env.ADMIN_KEY;
  if (adminKey && req.headers.get("x-admin-key") === adminKey) {
    return { isAdmin: true, authenticated: true, uid: "admin-key" };
  }

  const header = req.headers.get("authorization") || "";
  const match = header.match(/^Bearer (.+)$/i);
  if (!match) return { isAdmin: false, authenticated: false, uid: null };

  try {
    const decoded = await adminAuth().verifyIdToken(match[1], false);
    if (decoded.admin === true) {
      return { isAdmin: true, authenticated: true, uid: decoded.uid };
    }

    const allowedEmails = (process.env.ADMIN_EMAILS || "")
      .split(",")
      .map((e) => e.trim().toLowerCase())
      .filter(Boolean);
    if (decoded.email && decoded.email_verified && allowedEmails.includes(decoded.email.toLowerCase())) {
      return { isAdmin: true, authenticated: true, uid: decoded.uid };
    }

    await connectDB();
    const user = await User.findOne({ firebaseUid: decoded.uid })
      .select("status role")
      .lean();
    const ok = Boolean(user && user.status === "active" && user.role === "admin");
    return { isAdmin: ok, authenticated: true, uid: decoded.uid };
  } catch {
    return { isAdmin: false, authenticated: false, uid: null };
  }
}

export async function isAdmin(req) {
  const res = await resolveAdminCaller(req);
  return res.isAdmin;
}

export function withAdmin(handler, { limit = 40 } = {}) {
  return apiHandler(async (request, context) => {
    const [caller] = await Promise.all([resolveAdminCaller(request), connectDB()]);
    if (!caller.authenticated) {
      throw new AppError(401, "UNAUTHORIZED", "Missing or invalid authentication token.");
    }
    if (!caller.isAdmin) {
      throw new AppError(403, "FORBIDDEN", "Admin access is required.");
    }
    return handler(request, { ...context, adminUid: caller.uid });
  }, { limit });
}
