import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import {
  buildLastNDays,
  buildSubscriptionBreakdown,
  evaluateSystemHealth,
  formatUptimeLabel,
  resolveSafeAiDescriptor,
  safePercent,
} from "../src/lib/adminDashboardUtils.js";

test("safePercent handles edge cases and clamps 0..100", () => {
  assert.equal(safePercent(0, 0), 0);
  assert.equal(safePercent(5, 0), 0);
  assert.equal(safePercent(-3, 10), 0);
  assert.equal(safePercent(25, 100), 25);
  assert.equal(safePercent(150, 100), 100);
});

test("formatUptimeLabel renders readable durations", () => {
  assert.equal(formatUptimeLabel(45), "45s");
  assert.equal(formatUptimeLabel(185), "3m");
  assert.equal(formatUptimeLabel(7320), "2h 2m");
  assert.equal(formatUptimeLabel(93600), "1d 2h");
});

test("buildLastNDays returns ordered sequence ending at today", () => {
  const days = buildLastNDays("2026-11-27", 7);
  assert.equal(days.length, 7);
  assert.equal(days[0].date, "2026-11-21");
  assert.equal(days[6].date, "2026-11-27");
  assert.equal(days[6].isToday, true);
  assert.equal(days[0].isToday, false);
});

test("buildSubscriptionBreakdown computes conversion rate and normalizes all buckets", () => {
  const sub = buildSubscriptionBreakdown({
    statusCounts: { active: 15, trial: 5, free: 70, expired: 8, cancelled: 2 },
    planCounts: { free: 70, monthly: 12, yearly: 6, lifetime: 2 },
    totalStudents: 100,
    expiringSoonCount: 3,
  });
  assert.equal(sub.paidActiveCount, 15);
  assert.equal(sub.trialCount, 5);
  assert.equal(sub.premiumOrTrialCount, 20);
  assert.equal(sub.paidConversionPercent, 15);
  assert.equal(sub.expiringSoonCount, 3);
  assert.equal(sub.byStatus.length, 5);
  assert.equal(sub.byPlan.length, 4);
});

test("evaluateSystemHealth reports healthy, degraded, or issue accurately without leaking secrets", () => {
  const healthy = evaluateSystemHealth({
    mongoConnected: true,
    mongoPingMs: 12,
    firebaseAdminReady: true,
    fcmReady: true,
    aiConfigured: true,
    aiProvider: "gemini",
    aiModel: "gemini-2.0-flash",
    uptimeSeconds: 3600,
    memoryUsedMb: 120,
    fcmEnabledStudents: 42,
  });
  assert.equal(healthy.status, "healthy");
  assert.equal(healthy.checks.length, 4);

  const degraded = evaluateSystemHealth({
    mongoConnected: true,
    mongoPingMs: 15,
    firebaseAdminReady: true,
    fcmReady: true,
    aiConfigured: false,
  });
  assert.equal(degraded.status, "degraded");

  const issue = evaluateSystemHealth({
    mongoConnected: false,
    mongoPingMs: null,
    firebaseAdminReady: true,
    fcmReady: false,
    aiConfigured: true,
  });
  assert.equal(issue.status, "issue");

  const descriptor = resolveSafeAiDescriptor({ AI_API_KEY: "secret-key-xyz", AI_PROVIDER: "gemini" });
  assert.equal(descriptor.configured, true);
  assert.equal(JSON.stringify(descriptor).includes("secret-key-xyz"), false);
});

test("admin dashboard route enforces withAdmin guard and never leaks raw secrets", () => {
  const routePath = path.resolve("backend/src/app/api/v1/admin/dashboard/route.js");
  const servicePath = path.resolve("backend/src/modules/admin/adminDashboard.service.js");
  const routeSrc = fs.readFileSync(routePath, "utf8");
  const serviceSrc = fs.readFileSync(servicePath, "utf8");

  assert.match(routeSrc, /withAdmin\(/);
  assert.equal(serviceSrc.includes("MONGODB_URI"), false);
  assert.equal(serviceSrc.includes("FIREBASE_PRIVATE_KEY"), false);
});
