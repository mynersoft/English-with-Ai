import { AppError } from "@/lib/errors";
import { adminAuth } from "@/lib/firebaseAdmin";
import { connectDB } from "@/lib/db";
import { apiHandler } from "@/lib/apiHandler";
import User from "@/models/User";
import { upsertUserFromToken } from "@/modules/auth/auth.service";

export function extractBearerToken(request) {
  const header = request.headers.get("authorization") || "";
  const match = header.match(/^Bearer (.+)$/i);
  if (!match) throw new AppError(401, "UNAUTHORIZED", "Missing authentication token.");
  return match[1];
}

/**
 * Verifies the Firebase ID token from "Authorization: Bearer <token>".
 * Uses fast local cryptographic verification by default (<1ms via cached Google keys).
 * Pass { checkRevoked: true } only for sensitive destructive operations (e.g. account deletion).
 */
export async function verifyRequestToken(request, { checkRevoked = false } = {}) {
  const token = extractBearerToken(request);
  let authClient;
  try {
    authClient = adminAuth();
  } catch (err) {
    console.error("Firebase Admin config error:", err?.message);
    throw new AppError(
      500,
      "FIREBASE_ADMIN_MISCONFIGURED",
      err?.message || "Firebase Admin SDK is not configured on the server."
    );
  }
  try {
    return await authClient.verifyIdToken(token, checkRevoked);
  } catch (err) {
    console.error("verifyIdToken failed:", err?.code || err?.message);
    throw new AppError(401, "UNAUTHORIZED", "Invalid or expired token. Please log in again.");
  }
}

/**
 * Protects a route: valid token + existing/auto-synced active user (+ optional role check).
 * Runs token verification and MongoDB connection setup concurrently.
 */
export function withAuth(handler, { roles, limit, checkRevoked = false } = {}) {
  return apiHandler(async (request, context) => {
    const [decoded] = await Promise.all([
      verifyRequestToken(request, { checkRevoked }),
      connectDB(),
    ]);
    let user = await User.findOne({ firebaseUid: decoded.uid }).lean();
    if (!user) {
      ({ user } = await upsertUserFromToken(decoded));
    }
    if (user.status !== "active") throw new AppError(403, "ACCOUNT_DISABLED", "This account is deactivated.");
    if (roles && !roles.includes(user.role)) throw new AppError(403, "FORBIDDEN", "You do not have access.");
    return handler(request, { ...context, decoded, user });
  }, { limit });
}
