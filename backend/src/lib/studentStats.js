import {
  DEFAULT_TIMEZONE,
  getLocalDateString,
  normalizeTimezone,
  shiftLocalDate,
} from "./vocabSrs.js";

const SHORT_MONTHS = [
  "Jan", "Feb", "Mar", "Apr", "May", "Jun",
  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
];

const FULL_MONTHS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
];

const SHORT_DAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

/**
 * Returns Sunday-to-Saturday 7-day window containing `todayStr` (YYYY-MM-DD),
 * plus human-readable English range label like "Nov 22 - Nov 28".
 */
export function buildWeekWindow(todayStr) {
  const [y, m, d] = todayStr.split("-").map(Number);
  const utc = new Date(Date.UTC(y, m - 1, d, 12, 0, 0));
  const dow = utc.getUTCDay(); // 0=Sun .. 6=Sat
  const sundayStr = shiftLocalDate(todayStr, -dow);

  const days = [];
  for (let i = 0; i < 7; i++) {
    const dateStr = shiftLocalDate(sundayStr, i);
    const [, mm, dd] = dateStr.split("-").map(Number);
    days.push({
      date: dateStr,
      dayNumber: String(dd),
      dayShort: SHORT_DAYS[i],
      monthShort: SHORT_MONTHS[mm - 1] || "",
      isToday: dateStr === todayStr,
    });
  }

  const first = days[0];
  const last = days[6];
  const weekRangeLabel = `${first.monthShort} ${first.dayNumber} - ${last.monthShort} ${last.dayNumber}`;
  return { weekRangeLabel, days };
}

/**
 * Computes current streak and longest streak from a Set of active YYYY-MM-DD dates.
 */
export function computeStreaksFromActiveDates(activeDateSet, todayStr) {
  if (!activeDateSet || activeDateSet.size === 0) {
    return { currentStreak: 0, longestStreak: 0 };
  }

  let currentStreak = 0;
  const yesterdayStr = shiftLocalDate(todayStr, -1);
  let cursor = activeDateSet.has(todayStr)
    ? todayStr
    : activeDateSet.has(yesterdayStr)
      ? yesterdayStr
      : null;

  while (cursor && activeDateSet.has(cursor)) {
    currentStreak += 1;
    cursor = shiftLocalDate(cursor, -1);
  }

  // Compute longest streak across sorted active dates
  const sorted = Array.from(activeDateSet).sort();
  let longestStreak = 0;
  let run = 0;
  let prev = null;
  for (const ds of sorted) {
    if (prev && shiftLocalDate(prev, 1) === ds) {
      run += 1;
    } else {
      run = 1;
    }
    if (run > longestStreak) longestStreak = run;
    prev = ds;
  }

  return { currentStreak, longestStreak };
}

/**
 * Builds monthly calendar metadata for the user's current local month.
 */
export function buildMonthCalendar(todayStr, activeDateSet) {
  const [year, month, todayDay] = todayStr.split("-").map(Number);
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const firstDayOfWeek = new Date(Date.UTC(year, month - 1, 1)).getUTCDay(); // 0=Sun..6=Sat
  const monthPrefix = `${year}-${String(month).padStart(2, "0")}-`;

  const activeDaysOfMonth = [];
  for (let d = 1; d <= daysInMonth; d++) {
    const ds = `${monthPrefix}${String(d).padStart(2, "0")}`;
    if (activeDateSet.has(ds)) {
      activeDaysOfMonth.push(d);
    }
  }

  return {
    monthLabel: `${FULL_MONTHS[month - 1] || ""} ${year}`.trim(),
    year,
    month,
    daysInMonth,
    firstDayOfWeek,
    todayDay,
    activeDaysOfMonth,
  };
}

export function toLocalDateKey(dateLike, timezone = DEFAULT_TIMEZONE) {
  return getLocalDateString(new Date(dateLike), normalizeTimezone(timezone));
}
