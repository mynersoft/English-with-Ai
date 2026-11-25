import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  buildMonthCalendar,
  buildWeekWindow,
  computeStreaksFromActiveDates,
} from "../src/lib/studentStats.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

test("buildWeekWindow builds 7-day Sun-Sat week with today marker and English range", () => {
  // 2026-11-25 is Wednhjesday
  const res = buildWeekWindow("2026-11-25");
  assert.equal(res.weekRangeLabel, "Nov 22 - Nov 28");
  assert.equal(res.days.length, 7);
  assert.equal(res.days[0].dayShort, "Sun");
  assert.equal(res.days[0].dayNumber, "22");
  assert.equal(res.days[3].dayShort, "Wed");
  assert.equal(res.days[3].isToday, true);
  assert.equal(res.days[6].dayShort, "Sat");
  assert.equal(res.days[6].dayNumber, "28");
});

test("computeStreaksFromActiveDates calculates current and longest streaks accurately", () => {
  const active = new Set(["2026-11-18", "2026-11-19", "2026-11-20", "2026-11-24", "2026-11-25"]);
  const res = computeStreaksFromActiveDates(active, "2026-11-25");
  assert.equal(res.currentStreak, 2);
  assert.equal(res.longestStreak, 3);

  const empty = computeStreaksFromActiveDates(new Set(), "2026-11-25");
  assert.equal(empty.currentStreak, 0);
  assert.equal(empty.longestStreak, 0);
});

test("buildMonthCalendar extracts active days for current month", () => {
  const active = new Set(["2026-10-31", "2026-11-05", "2026-11-25"]);
  const cal = buildMonthCalendar("2026-11-25", active);
  assert.equal(cal.monthLabel, "November 2026");
  assert.equal(cal.daysInMonth, 30);
  assert.equal(cal.todayDay, 25);
  assert.deepEqual(cal.activeDaysOfMonth, [5, 25]);
});

test("Student dashboard, activity & pattern progress routes enforce withAuth", () => {
  const routes = [
    "../src/app/api/v1/student/dashboard/route.js",
    "../src/app/api/v1/student/activity/route.js",
    "../src/app/api/v1/patterns/[id]/progress/route.js",
  ];
  for (const rel of routes) {
    const src = fs.readFileSync(path.join(__dirname, rel), "utf8");
    assert.match(src, /withAuth/, `${rel} must use withAuth`);
    assert.ok(!/body\.userId|searchParams\.get\(["']userId/.test(src), `${rel} must not trust client userId`);
  }
});

test("Student dashboard service computes readingReport, readingActive, and reading module", () => {
  const src = fs.readFileSync(path.join(__dirname, "../src/modules/student/student.service.js"), "utf8");
  assert.match(src, /readingReport\s*:/);
  assert.match(src, /readingActive\s*:/);
  assert.match(src, /key:\s*"reading"/);
  assert.match(src, /ReadingPassage/);
  assert.match(src, /UserReadingProgress/);
  assert.match(src, /ReadingAttempt/);
});
