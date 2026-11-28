import mongoose from "mongoose";
import { connectDB } from "@/lib/db";
import { AppError } from "@/lib/errors";
import { rateLimit } from "@/lib/rateLimit";
import { buildLastNDays } from "@/lib/adminDashboardUtils";
import { DEFAULT_TIMEZONE, getLocalDateString } from "@/lib/vocabSrs";
import {
  AI_FEATURE_LABELS,
  PROVIDER_CATALOG,
  SPEAKING_TOPICS,
  buildDefaultAiConfig,
  evaluateQuotaDecision,
  normalizeAiConfigPatch,
  resolveEnvDefaultProviderAndModel,
} from "@/lib/aiConfigUtils";
import AiConfig from "@/models/AiConfig";
import AiUsageLog from "@/models/AiUsageLog";
import User from "@/models/User";
import VocabularyDailyActivity from "@/models/VocabularyDailyActivity";
import VocabularySpeakingSession from "@/models/VocabularySpeakingSession";
import ChatMessage from "@/models/ChatMessage";

const CACHE_TTL_MS = 10_000;
let cachedConfig = null;
let cachedAt = 0;

function getUtcDayStart(date = new Date()) {
  return new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
}

function sanitizeConfigDoc(doc) {
  const defaults = buildDefaultAiConfig(process.env);
  const envInfo = resolveEnvDefaultProviderAndModel(process.env);
  if (!doc) {
    return {
      ...defaults,
      apiKeyConfigured: envInfo.apiKeyConfigured,
      updatedBy: "",
      updatedAt: null,
    };
  }
  const merged = normalizeAiConfigPatch(defaults, {
    isEnabled: doc.isEnabled,
    provider: doc.provider,
    model: doc.model,
    temperature: doc.temperature,
    maxOutputTokens: doc.maxOutputTokens,
    requestTimeoutMs: doc.requestTimeoutMs,
    instructions: doc.instructions,
    conversation: doc.conversation,
    limits: doc.limits,
    pricing: doc.pricing,
  });
  return {
    ...merged,
    apiKeyConfigured: envInfo.apiKeyConfigured,
    updatedBy: String(doc.updatedBy || ""),
    updatedAt: doc.updatedAt ? new Date(doc.updatedAt).toISOString() : null,
  };
}

export function invalidateAiConfigCache() {
  cachedConfig = null;
  cachedAt = 0;
}

export async function getEffectiveAiConfig({ forceRefresh = false } = {}) {
  const now = Date.now();
  if (!forceRefresh && cachedConfig && now - cachedAt < CACHE_TTL_MS) {
    return cachedConfig;
  }

  try {
    if (mongoose.connection?.readyState !== 1) {
      await connectDB();
    }
    let doc = await AiConfig.findOne({ key: "default" }).lean();
    if (!doc) {
      const initial = buildDefaultAiConfig(process.env);
      try {
        doc = await AiConfig.findOneAndUpdate(
          { key: "default" },
          { $setOnInsert: { key: "default", ...initial } },
          { upsert: true, new: true, lean: true }
        );
      } catch {
        doc = await AiConfig.findOne({ key: "default" }).lean();
      }
    }
    cachedConfig = sanitizeConfigDoc(doc);
    cachedAt = now;
    return cachedConfig;
  } catch {
    const fallback = sanitizeConfigDoc(null);
    return fallback;
  }
}

export async function updateAiConfig({ adminUid = "", body = {} }) {
  await connectDB();
  const current = await getEffectiveAiConfig({ forceRefresh: true });

  let normalized;
  try {
    normalized = normalizeAiConfigPatch(current, body || {}, process.env);
  } catch (err) {
    throw new AppError(400, "VALIDATION_ERROR", err?.message || "Invalid AI configuration.");
  }

  const doc = await AiConfig.findOneAndUpdate(
    { key: "default" },
    {
      $set: {
        ...normalized,
        updatedBy: String(adminUid || ""),
      },
      $setOnInsert: { key: "default" },
    },
    { upsert: true, new: true, lean: true }
  );

  invalidateAiConfigCache();
  const updated = sanitizeConfigDoc(doc);
  cachedConfig = updated;
  cachedAt = Date.now();
  return updated;
}

export async function resetAiConfig({ adminUid = "", section = "all" }) {
  await connectDB();
  const current = await getEffectiveAiConfig({ forceRefresh: true });
  const defaults = buildDefaultAiConfig(process.env);
  const safeSection = String(section || "all").trim().toLowerCase();

  let patch = {};
  if (safeSection === "instructions") {
    patch = { instructions: defaults.instructions };
  } else if (safeSection === "limits") {
    patch = { limits: defaults.limits, pricing: defaults.pricing, isEnabled: true };
  } else if (safeSection === "conversation") {
    patch = { conversation: defaults.conversation };
  } else if (safeSection === "model") {
    patch = {
      provider: defaults.provider,
      model: defaults.model,
      temperature: defaults.temperature,
      maxOutputTokens: defaults.maxOutputTokens,
      requestTimeoutMs: defaults.requestTimeoutMs,
      pricing: defaults.pricing,
    };
  } else {
    patch = defaults;
  }

  const next = normalizeAiConfigPatch(current, patch, process.env);
  const doc = await AiConfig.findOneAndUpdate(
    { key: "default" },
    {
      $set: {
        ...next,
        updatedBy: String(adminUid || ""),
      },
      $setOnInsert: { key: "default" },
    },
    { upsert: true, new: true, lean: true }
  );

  invalidateAiConfigCache();
  const updated = sanitizeConfigDoc(doc);
  cachedConfig = updated;
  cachedAt = Date.now();
  return updated;
}

export async function recordAiUsageLog({
  userId = "system",
  feature = "speaking_turn",
  provider = "gemini",
  model = "gemini-2.0-flash",
  promptTokens = 0,
  completionTokens = 0,
  totalTokens = 0,
  estimatedCostUsd = 0,
  latencyMs = 0,
  status = "success",
  errorCode = "",
}) {
  try {
    if (mongoose.connection?.readyState !== 1) return;
    await AiUsageLog.create({
      userId: String(userId || "system"),
      feature,
      provider: String(provider || "gemini"),
      model: String(model || ""),
      promptTokens: Math.max(0, Math.round(Number(promptTokens || 0))),
      completionTokens: Math.max(0, Math.round(Number(completionTokens || 0))),
      totalTokens: Math.max(0, Math.round(Number(totalTokens || 0))),
      estimatedCostUsd: Math.max(0, Number(estimatedCostUsd || 0)),
      latencyMs: Math.max(0, Math.round(Number(latencyMs || 0))),
      status,
      errorCode: String(errorCode || "").slice(0, 80),
    });
  } catch (err) {
    console.warn("Failed to persist AiUsageLog:", err?.message);
  }
}

export async function enforceAiQuota({ userId = "system", feature = "speaking_turn", isPremium = false }) {
  const config = await getEffectiveAiConfig();

  const rpm = Math.max(1, Number(config?.limits?.rpmPerUser || 20));
  if (config?.limits?.enforceLimits !== false && userId && userId !== "system") {
    const withinRpm = rateLimit(`ai:user:${userId}`, { limit: rpm, windowMs: 60_000 });
    if (!withinRpm) {
      throw new AppError(
        429,
        "AI_RATE_LIMITED",
        `Too many AI requests. Limit is ${rpm} requests per minute.`
      );
    }
  }

  let userTodayCalls = 0;
  let todayTotalCostUsd = 0;

  if (config?.isEnabled !== false && config?.limits?.enforceLimits !== false) {
    try {
      if (mongoose.connection?.readyState === 1) {
        const dayStart = getUtcDayStart(new Date());
        const [userCount, costAgg] = await Promise.all([
          userId && userId !== "system"
            ? AiUsageLog.countDocuments({
                userId: String(userId),
                status: { $ne: "blocked" },
                createdAt: { $gte: dayStart },
              })
            : 0,
          AiUsageLog.aggregate([
            { $match: { createdAt: { $gte: dayStart }, status: { $ne: "blocked" } } },
            { $group: { _id: null, totalCost: { $sum: "$estimatedCostUsd" } } },
          ]),
        ]);
        userTodayCalls = Number(userCount || 0);
        todayTotalCostUsd = Number(costAgg?.[0]?.totalCost || 0);
      }
    } catch {
      // Fail open on transient DB count error
    }
  }

  const decision = evaluateQuotaDecision({
    config,
    feature,
    userIsPremium: Boolean(isPremium),
    userTodayCalls,
    todayTotalCostUsd,
  });

  if (!decision.allowed) {
    await recordAiUsageLog({
      userId,
      feature,
      provider: config.provider,
      model: config.model,
      status: "blocked",
      errorCode: decision.code || "AI_BLOCKED",
    });
    throw new AppError(decision.statusCode, decision.code, decision.message);
  }

  return config;
}

function summarizeBucket(row) {
  const requests = Number(row?.requests || 0);
  const successCount = Number(row?.successCount || 0);
  const errorCount = Number(row?.errorCount || 0);
  const blockedCount = Number(row?.blockedCount || 0);
  const promptTokens = Number(row?.promptTokens || 0);
  const completionTokens = Number(row?.completionTokens || 0);
  const totalTokens = Number(row?.totalTokens || 0);
  const estimatedCostUsd = Number(Number(row?.estimatedCostUsd || 0).toFixed(6));
  const avgLatencyMs = requests > 0 ? Math.round(Number(row?.totalLatencyMs || 0) / requests) : 0;
  const errorRatePercent = requests > 0 ? Math.round((errorCount / requests) * 100) : 0;
  return {
    requests,
    successCount,
    errorCount,
    blockedCount,
    promptTokens,
    completionTokens,
    totalTokens,
    estimatedCostUsd,
    avgLatencyMs,
    errorRatePercent,
  };
}

export async function getAdminAiManagementOverview({ windowDays = 7 } = {}) {
  await connectDB();
  const safeWindowDays = [1, 7, 30].includes(Number(windowDays)) ? Number(windowDays) : 7;
  const now = new Date();
  const todayStart = getUtcDayStart(now);
  const windowStart = new Date(todayStart.getTime() - (safeWindowDays - 1) * 24 * 60 * 60 * 1000);
  const todayStr = getLocalDateString(now, DEFAULT_TIMEZONE);
  const lastDays = buildLastNDays(todayStr, safeWindowDays);

  const [
    config,
    todayAgg,
    windowAgg,
    allTimeAgg,
    dailyRows,
    byFeatureRows,
    byModelRows,
    topUserRows,
    recentLogRows,
    legacyTurnsAgg,
    legacyEvalCount,
    legacyChatCount,
  ] = await Promise.all([
    getEffectiveAiConfig({ forceRefresh: true }),
    AiUsageLog.aggregate([
      { $match: { createdAt: { $gte: todayStart } } },
      {
        $group: {
          _id: null,
          requests: { $sum: 1 },
          successCount: { $sum: { $cond: [{ $eq: ["$status", "success"] }, 1, 0] } },
          errorCount: { $sum: { $cond: [{ $eq: ["$status", "error"] }, 1, 0] } },
          blockedCount: { $sum: { $cond: [{ $eq: ["$status", "blocked"] }, 1, 0] } },
          promptTokens: { $sum: "$promptTokens" },
          completionTokens: { $sum: "$completionTokens" },
          totalTokens: { $sum: "$totalTokens" },
          estimatedCostUsd: { $sum: "$estimatedCostUsd" },
          totalLatencyMs: { $sum: "$latencyMs" },
        },
      },
    ]),
    AiUsageLog.aggregate([
      { $match: { createdAt: { $gte: windowStart } } },
      {
        $group: {
          _id: null,
          requests: { $sum: 1 },
          successCount: { $sum: { $cond: [{ $eq: ["$status", "success"] }, 1, 0] } },
          errorCount: { $sum: { $cond: [{ $eq: ["$status", "error"] }, 1, 0] } },
          blockedCount: { $sum: { $cond: [{ $eq: ["$status", "blocked"] }, 1, 0] } },
          promptTokens: { $sum: "$promptTokens" },
          completionTokens: { $sum: "$completionTokens" },
          totalTokens: { $sum: "$totalTokens" },
          estimatedCostUsd: { $sum: "$estimatedCostUsd" },
          totalLatencyMs: { $sum: "$latencyMs" },
        },
      },
    ]),
    AiUsageLog.aggregate([
      {
        $group: {
          _id: null,
          requests: { $sum: 1 },
          successCount: { $sum: { $cond: [{ $eq: ["$status", "success"] }, 1, 0] } },
          errorCount: { $sum: { $cond: [{ $eq: ["$status", "error"] }, 1, 0] } },
          blockedCount: { $sum: { $cond: [{ $eq: ["$status", "blocked"] }, 1, 0] } },
          promptTokens: { $sum: "$promptTokens" },
          completionTokens: { $sum: "$completionTokens" },
          totalTokens: { $sum: "$totalTokens" },
          estimatedCostUsd: { $sum: "$estimatedCostUsd" },
          totalLatencyMs: { $sum: "$latencyMs" },
        },
      },
    ]),
    AiUsageLog.aggregate([
      { $match: { createdAt: { $gte: windowStart } } },
      {
        $group: {
          _id: {
            $dateToString: {
              format: "%Y-%m-%d",
              date: "$createdAt",
              timezone: DEFAULT_TIMEZONE,
            },
          },
          requests: { $sum: 1 },
          errors: { $sum: { $cond: [{ $ne: ["$status", "success"] }, 1, 0] } },
          tokens: { $sum: "$totalTokens" },
          costUsd: { $sum: "$estimatedCostUsd" },
        },
      },
    ]),
    AiUsageLog.aggregate([
      { $match: { createdAt: { $gte: windowStart } } },
      {
        $group: {
          _id: "$feature",
          requests: { $sum: 1 },
          tokens: { $sum: "$totalTokens" },
          costUsd: { $sum: "$estimatedCostUsd" },
          totalLatencyMs: { $sum: "$latencyMs" },
        },
      },
      { $sort: { requests: -1 } },
    ]),
    AiUsageLog.aggregate([
      { $match: { createdAt: { $gte: windowStart } } },
      {
        $group: {
          _id: { provider: "$provider", model: "$model" },
          requests: { $sum: 1 },
          tokens: { $sum: "$totalTokens" },
          costUsd: { $sum: "$estimatedCostUsd" },
        },
      },
      { $sort: { requests: -1 } },
    ]),
    AiUsageLog.aggregate([
      { $match: { createdAt: { $gte: windowStart }, userId: { $ne: "system" } } },
      {
        $group: {
          _id: "$userId",
          requests: { $sum: 1 },
          tokens: { $sum: "$totalTokens" },
          costUsd: { $sum: "$estimatedCostUsd" },
        },
      },
      { $sort: { costUsd: -1, requests: -1 } },
      { $limit: 6 },
    ]),
    AiUsageLog.find()
      .sort({ createdAt: -1 })
      .limit(12)
      .select("userId feature provider model totalTokens estimatedCostUsd latencyMs status errorCode createdAt")
      .lean(),
    VocabularyDailyActivity.aggregate([
      { $group: { _id: null, totalTurns: { $sum: "$aiSpeakingTurns" } } },
    ]),
    VocabularySpeakingSession.countDocuments(),
    ChatMessage.countDocuments({ "aiCorrection.status": { $in: ["correct", "corrected"] } }),
  ]);

  const userIds = topUserRows.map((r) => String(r._id)).filter(Boolean);
  const userDocs = userIds.length
    ? await User.find({ firebaseUid: { $in: userIds } })
        .select("firebaseUid name email subscriptionStatus")
        .lean()
    : [];
  const userMap = new Map(userDocs.map((u) => [String(u.firebaseUid), u]));

  const dailyMap = new Map(dailyRows.map((r) => [String(r._id), r]));
  const dailySeries = lastDays.map((day) => {
    const row = dailyMap.get(day.date);
    return {
      date: day.date,
      dayShort: day.dayShort,
      isToday: day.isToday,
      requests: Number(row?.requests || 0),
      errors: Number(row?.errors || 0),
      tokens: Number(row?.tokens || 0),
      costUsd: Number(Number(row?.costUsd || 0).toFixed(6)),
    };
  });

  const legacyInteractions =
    Number(legacyTurnsAgg?.[0]?.totalTurns || 0) +
    Number(legacyEvalCount || 0) +
    Number(legacyChatCount || 0);

  return {
    generatedAt: now.toISOString(),
    config,
    catalog: {
      providers: Object.entries(PROVIDER_CATALOG).map(([key, info]) => ({
        id: key,
        label: info.label,
        defaultModel: info.defaultModel,
        models: info.models,
      })),
      speakingTopics: SPEAKING_TOPICS,
    },
    usage: {
      windowDays: safeWindowDays,
      today: summarizeBucket(todayAgg[0]),
      window: summarizeBucket(windowAgg[0]),
      allTime: {
        ...summarizeBucket(allTimeAgg[0]),
        legacyInteractions,
      },
      dailySeries,
      byFeature: byFeatureRows.map((r) => {
        const reqs = Number(r.requests || 0);
        return {
          feature: String(r._id || "other"),
          label: AI_FEATURE_LABELS[r._id] || String(r._id || "Other"),
          requests: reqs,
          tokens: Number(r.tokens || 0),
          costUsd: Number(Number(r.costUsd || 0).toFixed(6)),
          avgLatencyMs: reqs > 0 ? Math.round(Number(r.totalLatencyMs || 0) / reqs) : 0,
        };
      }),
      byModel: byModelRows.map((r) => ({
        provider: String(r._id?.provider || "gemini"),
        model: String(r._id?.model || "default"),
        requests: Number(r.requests || 0),
        tokens: Number(r.tokens || 0),
        costUsd: Number(Number(r.costUsd || 0).toFixed(6)),
      })),
      topUsers: topUserRows.map((r) => {
        const uid = String(r._id);
        const u = userMap.get(uid);
        return {
          userId: uid,
          name: u?.name || "Student",
          email: u?.email || "",
          isPremium: u?.subscriptionStatus === "active" || u?.subscriptionStatus === "trial",
          requests: Number(r.requests || 0),
          tokens: Number(r.tokens || 0),
          costUsd: Number(Number(r.costUsd || 0).toFixed(6)),
        };
      }),
      recentLogs: recentLogRows.map((l) => ({
        id: String(l._id),
        userId: String(l.userId || "system"),
        feature: String(l.feature),
        featureLabel: AI_FEATURE_LABELS[l.feature] || l.feature,
        provider: String(l.provider),
        model: String(l.model),
        totalTokens: Number(l.totalTokens || 0),
        estimatedCostUsd: Number(Number(l.estimatedCostUsd || 0).toFixed(6)),
        latencyMs: Number(l.latencyMs || 0),
        status: String(l.status || "success"),
        errorCode: String(l.errorCode || ""),
        createdAt: l.createdAt ? new Date(l.createdAt).toISOString() : "",
      })),
    },
  };
}
