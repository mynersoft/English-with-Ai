import { AppError } from "@/lib/errors";
import { adminAuth } from "@/lib/firebaseAdmin";
import User from "@/models/User";
import UserSettings from "@/models/UserSettings";

export function toPublicUser(user) {
  return {
    id: user._id.toString(),
    name: user.name,
    email: user.email,
    photoUrl: user.photoUrl,
    role: user.role,
    status: user.status,
    learningLevel: user.learningLevel,
    dailyGoalMinutes: user.dailyGoalMinutes,
    createdAt: user.createdAt,
  };
}

/** Checks which Firebase sign-in method produced the token ("password" or "google.com"). */
export function assertProvider(decoded, expected) {
  const provider = decoded.firebase?.sign_in_provider;
  if (provider !== expected) {
    throw new AppError(400, "WRONG_PROVIDER", "This endpoint does not match the sign-in method used.");
  }
}

/** Creates the MongoDB profile on first sign-in, updates it on later ones. Safe to call repeatedly. */
export async function upsertUserFromToken(decoded, { name } = {}) {
  const email = decoded.email?.toLowerCase();
  if (!email) throw new AppError(400, "EMAIL_REQUIRED", "Your account has no email address.");
  const provider = decoded.firebase?.sign_in_provider || "unknown";

  // Single indexed lookup across firebaseUid / email instead of two sequential round-trips.
  const existing = await User.findOne({
    $or: [{ firebaseUid: decoded.uid }, { email }],
  })
    .select("_id firebaseUid email photoUrl role status")
    .lean();

  if (existing && existing.status !== "active") {
    throw new AppError(403, "ACCOUNT_DISABLED", "This account is deactivated.");
  }

  const allowedAdmins = (process.env.ADMIN_EMAILS || "")
    .split(",")
    .map((e) => e.trim().toLowerCase())
    .filter(Boolean);
  const isAdminEmail = Boolean(decoded.email_verified && allowedAdmins.includes(email));

  const rawName = (name || decoded.name || email.split("@")[0] || "Learner").trim();
  const safeName = (rawName.length >= 1 ? rawName : "Learner").slice(0, 80);

  let user;
  let isNewUser = false;
  if (existing) {
    const setFields = {
      firebaseUid: decoded.uid,
      lastLoginAt: new Date(),
    };
    if (decoded.picture && !existing.photoUrl) setFields.photoUrl = decoded.picture;
    if (isAdminEmail && existing.role !== "admin") setFields.role = "admin";
    user = await User.findByIdAndUpdate(
      existing._id,
      {
        $set: setFields,
        $addToSet: { authProviders: provider },
      },
      { new: true }
    ).lean();
  } else {
    isNewUser = true;
    const setFields = { lastLoginAt: new Date() };
    if (isAdminEmail) setFields.role = "admin";
    user = await User.findOneAndUpdate(
      { firebaseUid: decoded.uid },
      {
        $setOnInsert: {
          firebaseUid: decoded.uid,
          email,
          name: safeName,
          photoUrl: decoded.picture || null,
        },
        $set: setFields,
        $addToSet: { authProviders: provider },
      },
      { upsert: true, new: true, setDefaultsOnInsert: true }
    ).lean();

    await UserSettings.updateOne(
      { user: user._id },
      { $setOnInsert: { user: user._id } },
      { upsert: true }
    );
  }

  return { user, isNewUser };
}

/** Deletes app data first, then the Firebase account. Safe to retry if a step fails. */
export async function deleteAccount(decoded) {
  const user = await User.findOne({ firebaseUid: decoded.uid }).select("_id").lean();
  if (user) {
    await Promise.all([
      UserSettings.deleteOne({ user: user._id }),
      User.deleteOne({ _id: user._id }),
    ]);
  }
  try {
    await adminAuth().deleteUser(decoded.uid);
  } catch (err) {
    if (err?.code !== "auth/user-not-found") throw err;
  }
}
