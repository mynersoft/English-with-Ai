import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  buildAdminStudentSearchFilter,
  formatSubscriptionLabel,
  normalizeAccountStatus,
  normalizeSubscriptionInput,
  serializeAdminStudentSummary,
} from "../src/lib/adminStudentUtils.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

test("Admin student account status and subscription normalization validate inputs strictly", () => {
  assert.equal(normalizeAccountStatus("active"), "active");
  assert.equal(normalizeAccountStatus("SUSPENDED"), "suspended");
  assert.equal(normalizeAccountStatus("deactivated"), "deactivated");
  assert.equal(normalizeAccountStatus("deleted"), null);

  const freeRes = normalizeSubscriptionInput({
    subscriptionStatus: "free",
    subscriptionPlan: "yearly",
    subscriptionExpiresAt: "2027-01-01T00:00:00Z",
  });
  assert.equal(freeRes.ok, true);
  assert.equal(freeRes.subscriptionPlan, "free");
  assert.equal(freeRes.subscriptionExpiresAt, null);

  const activeMonthly = normalizeSubscriptionInput({
    subscriptionStatus: "active",
    subscriptionExpiresAt: "2027-05-01T00:00:00Z",
  });
  assert.equal(activeMonthly.ok, true);
  assert.equal(activeMonthly.subscriptionPlan, "monthly");
  assert.ok(activeMonthly.subscriptionExpiresAt instanceof Date);

  const invalidDate = normalizeSubscriptionInput({
    subscriptionStatus: "active",
    subscriptionPlan: "yearly",
    subscriptionExpiresAt: "not-a-date",
  });
  assert.equal(invalidDate.ok, false);
  assert.equal(invalidDate.code, "INVALID_EXPIRY_DATE");

  const invalidStatus = normalizeSubscriptionInput({ subscriptionStatus: "vip" });
  assert.equal(invalidStatus.ok, false);
});

test("Admin student search filter and summary serialization behave cleanly", () => {
  const filter = buildAdminStudentSearchFilter({
    search: "mahir+test@gmail.com",
    accountStatus: "suspended",
    subscriptionStatus: "free",
  });
  assert.equal(filter.role, "student");
  assert.equal(filter.status, "suspended");
  assert.equal(filter.$and.length, 2);

  assert.equal(formatSubscriptionLabel("active", "yearly"), "Active · Yearly");
  assert.equal(formatSubscriptionLabel("free", "free"), "Free");

  const summary = serializeAdminStudentSummary(
    {
      _id: "656000000000000000000001",
      firebaseUid: "uid-1",
      name: "Md Mahir",
      email: "mdmahir@example.com",
      status: "active",
      subscriptionStatus: "active",
      subscriptionPlan: "yearly",
      learningLevel: "intermediate",
      dailyGoalMinutes: 30,
      authProviders: ["google.com"],
    },
    { vocabularyLearned: 25, patternsPracticed: 8, readingCompleted: 4, readingMinutes: 42 }
  );
  assert.equal(summary.subscriptionLabel, "Active · Yearly");
  assert.equal(summary.progressSummary.vocabularyLearned, 25);
  assert.equal(summary.progressSummary.readingMinutes, 42);
});

test("All admin student management routes enforce withAdmin", () => {
  const routes = [
    "../src/app/api/v1/admin/students/route.js",
    "../src/app/api/v1/admin/students/[id]/route.js",
    "../src/app/api/v1/admin/students/[id]/status/route.js",
    "../src/app/api/v1/admin/students/[id]/subscription/route.js",
  ];
  for (const rel of routes) {
    const src = fs.readFileSync(path.join(__dirname, rel), "utf8");
    assert.match(src, /withAdmin\(/, `${rel} must use withAdmin`);
  }
});
