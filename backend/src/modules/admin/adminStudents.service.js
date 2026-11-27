import mongoose from "mongoose";
import { AppError } from "@/lib/errors";
import {
  buildAdminStudentSearchFilter,
  normalizeAccountStatus,
  normalizeSubscriptionInput,
  serializeAdminStudentSummary,
} from "@/lib/adminStudentUtils";
import { buildStudentDashboardData } from "@/modules/student/student.service";
import User from "@/models/User";
import UserVocabularyProgress from "@/models/UserVocabularyProgress";
import UserPatternProgress from "@/models/UserPatternProgress";
import UserReadingProgress from "@/models/UserReadingProgress";

function assertStudentObjectId(id) {
  if (!id || !mongoose.Types.ObjectId.isValid(String(id))) {
    throw new AppError(400, "INVALID_ID", "Invalid student ID.");
  }
  return new mongoose.Types.ObjectId(String(id));
}

export async function adminListStudents({
  page = 1,
  limit = 20,
  search = "",
  accountStatus = "all",
  subscriptionStatus = "all",
} = {}) {
  const safePage = Math.max(1, Number(page) || 1);
  const safeLimit = Math.max(1, Math.min(50, Number(limit) || 20));
  const skip = (safePage - 1) * safeLimit;

  const filter = buildAdminStudentSearchFilter({
    search,
    accountStatus,
    subscriptionStatus,
  });

  const [students, total, statusCountsAgg, premiumActiveCount] = await Promise.all([
    User.find(filter)
      .sort({ lastLoginAt: -1, createdAt: -1 })
      .skip(skip)
      .limit(safeLimit)
      .lean(),
    User.countDocuments(filter),
    User.aggregate([
      { $match: { role: "student" } },
      { $group: { _id: "$status", count: { $sum: 1 } } },
    ]),
    User.countDocuments({
      role: "student",
      subscriptionStatus: { $in: ["active", "trial"] },
    }),
  ]);

  const counts = {
    totalStudents: 0,
    activeCount: 0,
    suspendedCount: 0,
    deactivatedCount: 0,
    premiumActiveCount,
  };
  for (const row of statusCountsAgg) {
    const c = Number(row.count || 0);
    counts.totalStudents += c;
    if (row._id === "active") counts.activeCount = c;
    else if (row._id === "suspended") counts.suspendedCount = c;
    else if (row._id === "deactivated") counts.deactivatedCount = c;
  }

  const uids = students.map((u) => u.firebaseUid).filter(Boolean);
  let vocabByUid = new Map();
  let patternByUid = new Map();
  let readingByUid = new Map();

  if (uids.length > 0) {
    const [vocabAgg, patternAgg, readingAgg] = await Promise.all([
      UserVocabularyProgress.aggregate([
        { $match: { userId: { $in: uids }, learnedAt: { $ne: null } } },
        { $group: { _id: "$userId", count: { $sum: 1 } } },
      ]),
      UserPatternProgress.aggregate([
        { $match: { userId: { $in: uids }, isCompleted: true } },
        { $group: { _id: "$userId", count: { $sum: 1 } } },
      ]),
      UserReadingProgress.aggregate([
        { $match: { userId: { $in: uids } } },
        {
          $group: {
            _id: "$userId",
            completedCount: {
              $sum: { $cond: [{ $eq: ["$status", "completed"] }, 1, 0] },
            },
            totalSeconds: { $sum: { $ifNull: ["$totalSeconds", 0] } },
          },
        },
      ]),
    ]);

    vocabByUid = new Map(vocabAgg.map((r) => [r._id, Number(r.count || 0)]));
    patternByUid = new Map(patternAgg.map((r) => [r._id, Number(r.count || 0)]));
    readingByUid = new Map(
      readingAgg.map((r) => [
        r._id,
        {
          completedCount: Number(r.completedCount || 0),
          minutes: Math.round(Number(r.totalSeconds || 0) / 60),
        },
      ])
    );
  }

  const items = students.map((u) => {
    const rd = readingByUid.get(u.firebaseUid);
    return serializeAdminStudentSummary(u, {
      vocabularyLearned: vocabByUid.get(u.firebaseUid) || 0,
      patternsPracticed: patternByUid.get(u.firebaseUid) || 0,
      readingCompleted: rd?.completedCount || 0,
      readingMinutes: rd?.minutes || 0,
    });
  });

  return {
    items,
    page: safePage,
    limit: safeLimit,
    total,
    totalPages: Math.max(1, Math.ceil(total / safeLimit)),
    counts,
  };
}

export async function adminGetStudentDetail(studentId) {
  const objectId = assertStudentObjectId(studentId);
  const userDoc = await User.findById(objectId).lean();
  if (!userDoc) {
    throw new AppError(404, "STUDENT_NOT_FOUND", "Student not found.");
  }

  const dashboard = await buildStudentDashboardData({ userDoc });
  const summary = serializeAdminStudentSummary(userDoc, {
    vocabularyLearned: dashboard.stats?.vocabularyLearned || 0,
    patternsPracticed: dashboard.stats?.patternsPracticed || 0,
    readingCompleted: dashboard.stats?.readingPassagesCompleted || 0,
    readingMinutes: dashboard.stats?.readingMinutes || 0,
  });

  return {
    student: summary,
    stats: dashboard.stats,
    readingReport: dashboard.readingReport,
    courseReport: dashboard.courseReport,
    streakCalendar: {
      currentStreak: dashboard.stats?.streakDays || 0,
      longestStreak: dashboard.streakCalendar?.longestStreak || 0,
      totalActiveDays: dashboard.streakCalendar?.totalActiveDays || 0,
    },
  };
}

export async function adminUpdateStudentAccountStatus({ studentId, status, adminUid }) {
  const objectId = assertStudentObjectId(studentId);
  const nextStatus = normalizeAccountStatus(status);
  if (!nextStatus) {
    throw new AppError(
      400,
      "INVALID_ACCOUNT_STATUS",
      "Account status must be active, suspended, or deactivated."
    );
  }

  const existing = await User.findById(objectId).lean();
  if (!existing) {
    throw new AppError(404, "STUDENT_NOT_FOUND", "Student not found.");
  }
  if (existing.role === "admin" || (adminUid && existing.firebaseUid === adminUid)) {
    throw new AppError(403, "CANNOT_MODIFY_ADMIN", "Administrator account status cannot be changed here.");
  }

  const updated = await User.findByIdAndUpdate(
    objectId,
    { $set: { status: nextStatus } },
    { new: true }
  ).lean();

  return {
    student: serializeAdminStudentSummary(updated),
  };
}

export async function adminUpdateStudentSubscription({ studentId, body = {} }) {
  const objectId = assertStudentObjectId(studentId);
  const parsed = normalizeSubscriptionInput(body);
  if (!parsed.ok) {
    throw new AppError(400, parsed.code, parsed.message);
  }

  const existing = await User.findById(objectId).lean();
  if (!existing) {
    throw new AppError(404, "STUDENT_NOT_FOUND", "Student not found.");
  }

  const updated = await User.findByIdAndUpdate(
    objectId,
    {
      $set: {
        subscriptionStatus: parsed.subscriptionStatus,
        subscriptionPlan: parsed.subscriptionPlan,
        subscriptionExpiresAt: parsed.subscriptionExpiresAt,
      },
    },
    { new: true }
  ).lean();

  return {
    student: serializeAdminStudentSummary(updated),
  };
}
