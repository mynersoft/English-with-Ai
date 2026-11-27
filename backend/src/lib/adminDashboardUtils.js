const SUBSCRIPTION_STATUS_META = [
  { key: "active", label: "Active Paid" },
  { key: "trial", label: "Trial" },
  { key: "free", label: "Free" },
  { key: "expired", label: "Expired" },
  { key: "cancelled", label: "Cancelled" },
];

const SUBSCRIPTION_PLAN_META = [
  { key: "free", label: "Free" },
  { key: "monthly", label: "Monthly" },
  { key: "yearly", label: "Yearly" },
  { key: "lifetime", label: "Lifetime" },
];

const DEFAULT_PROVIDER_MODELS = {
  gemini: "gemini-2.0-flash",
  groq: "llama-3.3-70b-versatile",
  anthropic: "claude-haiku-5-5",
};

export function safePercent(numerator, denominator) {
  const num = Number(numerator || 0);
  const den = Number(denominator || 0);
  if (!Number.isFinite(num) || !Number.isFinite(den) || den <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round((num / den) * 100)));
}

export function formatUptimeLabel(uptimeSeconds) {
  const secs = Math.max(0, Math.floor(Number(uptimeSeconds || 0)));
  const days = Math.floor(secs / 86400);
  const hours = Math.floor((secs % 86400) / 3600);
  const mins = Math.floor((secs % 3600) / 60);
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${mins}m`;
  if (mins > 0) return `${mins}m`;
  return `${secs}s`;
}

export function resolveSafeAiDescriptor(env = process.env) {
  const provider = String(env.AI_PROVIDER || "gemini").trim().toLowerCase() || "gemini";
  const model =
    String(env.AI_MODEL || "").trim() ||
    DEFAULT_PROVIDER_MODELS[provider] ||
    "configured-model";
  const configured = Boolean(String(env.AI_API_KEY || "").trim());
  return { provider, model, configured };
}

export function buildLastNDays(todayStr, daysCount = 7) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(todayStr || ""));
  const baseDate = match
    ? new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])))
    : new Date();
  const safeCount = Math.max(1, Math.min(31, Number(daysCount || 7)));
  const dayNames = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
  const list = [];
  for (let offset = safeCount - 1; offset >= 0; offset -= 1) {
    const d = new Date(baseDate.getTime() - offset * 24 * 60 * 60 * 1000);
    const yyyy = d.getUTCFullYear();
    const mm = String(d.getUTCMonth() + 1).padStart(2, "0");
    const dd = String(d.getUTCDate()).padStart(2, "0");
    list.push({
      date: `${yyyy}-${mm}-${dd}`,
      dayShort: dayNames[d.getUTCDay()],
      isToday: offset === 0,
    });
  }
  return list;
}

export function buildSubscriptionBreakdown({
  statusCounts = {},
  planCounts = {},
  totalStudents = 0,
  expiringSoonCount = 0,
}) {
  const normalizedTotal = Math.max(0, Number(totalStudents || 0));
  const byStatus = SUBSCRIPTION_STATUS_META.map(({ key, label }) => {
    const count = Math.max(0, Number(statusCounts[key] || 0));
    return {
      key,
      label,
      count,
      percent: safePercent(count, normalizedTotal),
    };
  });

  const byPlan = SUBSCRIPTION_PLAN_META.map(({ key, label }) => {
    const count = Math.max(0, Number(planCounts[key] || 0));
    return {
      key,
      label,
      count,
      percent: safePercent(count, normalizedTotal),
    };
  });

  const paidActiveCount = Math.max(0, Number(statusCounts.active || 0));
  const trialCount = Math.max(0, Number(statusCounts.trial || 0));
  const freeCount = Math.max(0, Number(statusCounts.free || 0));
  const expiredCount = Math.max(0, Number(statusCounts.expired || 0));
  const cancelledCount = Math.max(0, Number(statusCounts.cancelled || 0));

  return {
    totalStudents: normalizedTotal,
    paidActiveCount,
    trialCount,
    freeCount,
    expiredCount,
    cancelledCount,
    premiumOrTrialCount: paidActiveCount + trialCount,
    paidConversionPercent: safePercent(paidActiveCount, normalizedTotal),
    expiringSoonCount: Math.max(0, Number(expiringSoonCount || 0)),
    byStatus,
    byPlan,
  };
}

export function evaluateSystemHealth({
  mongoConnected = false,
  mongoPingMs = null,
  firebaseAdminReady = false,
  fcmReady = false,
  aiConfigured = false,
  aiProvider = "gemini",
  aiModel = "gemini-2.0-flash",
  uptimeSeconds = 0,
  memoryUsedMb = 0,
  memoryRssMb = 0,
  fcmEnabledStudents = 0,
}) {
  const dbStatus = mongoConnected
    ? mongoPingMs != null && mongoPingMs > 800
      ? "degraded"
      : "healthy"
    : "issue";
  const authStatus = firebaseAdminReady ? "healthy" : "issue";
  const aiStatus = aiConfigured ? "healthy" : "degraded";
  const pushStatus = firebaseAdminReady && fcmReady ? "healthy" : "degraded";

  let overallStatus = "healthy";
  let summaryLabel = "All Systems Operational";
  if (dbStatus === "issue" || authStatus === "issue") {
    overallStatus = "issue";
    summaryLabel = "Core Service Attention Needed";
  } else if (dbStatus === "degraded" || aiStatus === "degraded" || pushStatus === "degraded") {
    overallStatus = "degraded";
    summaryLabel = aiConfigured ? "Degraded Performance" : "AI Provider Key Not Configured";
  }

  return {
    status: overallStatus,
    summaryLabel,
    uptimeSeconds: Math.max(0, Math.floor(Number(uptimeSeconds || 0))),
    uptimeLabel: formatUptimeLabel(uptimeSeconds),
    memoryUsedMb: Math.max(0, Math.round(Number(memoryUsedMb || 0))),
    memoryRssMb: Math.max(0, Math.round(Number(memoryRssMb || 0))),
    dbLatencyMs: mongoPingMs != null ? Math.max(0, Math.round(Number(mongoPingMs))) : null,
    fcmEnabledStudents: Math.max(0, Number(fcmEnabledStudents || 0)),
    checks: [
      {
        key: "mongodb",
        label: "MongoDB Database",
        status: dbStatus,
        detail: mongoConnected
          ? mongoPingMs != null
            ? `Connected · ${Math.round(mongoPingMs)} ms ping`
            : "Connected"
          : "Disconnected",
      },
      {
        key: "firebase_auth",
        label: "Firebase Auth Admin",
        status: authStatus,
        detail: firebaseAdminReady ? "Token verification ready" : "Admin SDK unavailable",
      },
      {
        key: "ai_service",
        label: "AI Engine",
        status: aiStatus,
        detail: aiConfigured ? `${aiProvider} (${aiModel}) ready` : "API key not configured",
      },
      {
        key: "fcm_push",
        label: "Push Notifications (FCM)",
        status: pushStatus,
        detail:
          firebaseAdminReady && fcmReady
            ? `${fcmEnabledStudents} student devices registered`
            : "Messaging unavailable",
      },
    ],
  };
}
