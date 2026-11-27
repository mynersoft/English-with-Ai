export const ACCOUNT_STATUSES = ["active", "suspended", "deactivated"];
export const SUBSCRIPTION_STATUSES = ["free", "trial", "active", "expired", "cancelled"];
export const SUBSCRIPTION_PLANS = ["free", "monthly", "yearly", "lifetime"];

export function normalizeAccountStatus(raw) {
  const val = String(raw || "").trim().toLowerCase();
  if (!ACCOUNT_STATUSES.includes(val)) {
    return null;
  }
  return val;
}

export function normalizeSubscriptionInput(body = {}) {
  const rawStatus = String(body.subscriptionStatus ?? body.status ?? "")
    .trim()
    .toLowerCase();
  if (!SUBSCRIPTION_STATUSES.includes(rawStatus)) {
    return {
      ok: false,
      code: "INVALID_SUBSCRIPTION_STATUS",
      message: `subscriptionStatus must be one of: ${SUBSCRIPTION_STATUSES.join(", ")}.`,
    };
  }

  let plan = String(body.subscriptionPlan ?? body.plan ?? "")
    .trim()
    .toLowerCase();

  if (rawStatus === "free") {
    plan = "free";
  } else if (!plan) {
    plan = "monthly";
  }

  if (!SUBSCRIPTION_PLANS.includes(plan)) {
    return {
      ok: false,
      code: "INVALID_SUBSCRIPTION_PLAN",
      message: `subscriptionPlan must be one of: ${SUBSCRIPTION_PLANS.join(", ")}.`,
    };
  }

  let expiresAt = null;
  const rawExp = body.subscriptionExpiresAt ?? body.expiresAt;
  if (rawStatus !== "free" && plan !== "lifetime" && rawExp !== undefined && rawExp !== null && String(rawExp).trim() !== "") {
    const parsed = new Date(rawExp);
    if (Number.isNaN(parsed.getTime())) {
      return {
        ok: false,
        code: "INVALID_EXPIRY_DATE",
        message: "subscriptionExpiresAt must be a valid ISO date string or null.",
      };
    }
    expiresAt = parsed;
  }

  return {
    ok: true,
    subscriptionStatus: rawStatus,
    subscriptionPlan: plan,
    subscriptionExpiresAt: expiresAt,
  };
}

export function buildAdminStudentSearchFilter({
  search = "",
  accountStatus = "all",
  subscriptionStatus = "all",
} = {}) {
  const query = { role: "student" };

  const normAccount = String(accountStatus || "all").trim().toLowerCase();
  if (normAccount && normAccount !== "all" && ACCOUNT_STATUSES.includes(normAccount)) {
    query.status = normAccount;
  }

  const normSub = String(subscriptionStatus || "all").trim().toLowerCase();
  if (normSub && normSub !== "all" && SUBSCRIPTION_STATUSES.includes(normSub)) {
    if (normSub === "free") {
      query.$and = [
        ...(query.$and || []),
        {
          $or: [
            { subscriptionStatus: "free" },
            { subscriptionStatus: { $exists: false } },
            { subscriptionStatus: null },
          ],
        },
      ];
    } else {
      query.subscriptionStatus = normSub;
    }
  }

  const cleanSearch = String(search || "").trim();
  if (cleanSearch) {
    const escaped = cleanSearch.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const rx = new RegExp(escaped, "i");
    query.$and = [
      ...(query.$and || []),
      {
        $or: [{ name: rx }, { email: rx }, { firebaseUid: rx }],
      },
    ];
  }

  return query;
}

export function formatSubscriptionLabel(status = "free", plan = "free") {
  const s = SUBSCRIPTION_STATUSES.includes(status) ? status : "free";
  const p = SUBSCRIPTION_PLANS.includes(plan) ? plan : "free";
  if (s === "free") return "Free";
  if (s === "active") {
    if (p === "lifetime") return "Active · Lifetime";
    if (p === "yearly") return "Active · Yearly";
    if (p === "monthly") return "Active · Monthly";
    return "Active";
  }
  return s.charAt(0).toUpperCase() + s.slice(1);
}

export function serializeAdminStudentSummary(userDoc, progress = {}) {
  const subStatus = userDoc.subscriptionStatus || "free";
  const subPlan = userDoc.subscriptionPlan || "free";
  return {
    id: String(userDoc._id),
    firebaseUid: String(userDoc.firebaseUid || ""),
    name: String(userDoc.name || "Learner"),
    email: String(userDoc.email || ""),
    photoUrl: userDoc.photoUrl || null,
    role: userDoc.role || "student",
    status: userDoc.status || "active",
    subscriptionStatus: subStatus,
    subscriptionPlan: subPlan,
    subscriptionLabel: formatSubscriptionLabel(subStatus, subPlan),
    subscriptionExpiresAt: userDoc.subscriptionExpiresAt
      ? new Date(userDoc.subscriptionExpiresAt).toISOString()
      : null,
    learningLevel: userDoc.learningLevel || "beginner",
    dailyGoalMinutes: Number(userDoc.dailyGoalMinutes || 20),
    authProviders: Array.isArray(userDoc.authProviders) ? userDoc.authProviders : [],
    lastLoginAt: userDoc.lastLoginAt ? new Date(userDoc.lastLoginAt).toISOString() : null,
    createdAt: userDoc.createdAt ? new Date(userDoc.createdAt).toISOString() : null,
    progressSummary: {
      vocabularyLearned: Number(progress.vocabularyLearned || 0),
      patternsPracticed: Number(progress.patternsPracticed || 0),
      readingCompleted: Number(progress.readingCompleted || 0),
      readingMinutes: Number(progress.readingMinutes || 0),
    },
  };
}
