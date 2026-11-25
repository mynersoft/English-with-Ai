package com.example.domain.model

data class StudentProfileSummary(
    val id: String = "",
    val name: String = "Learner",
    val email: String = "",
    val photoUrl: String? = null,
    val role: String = "student",
    val planLabel: String = "Free",
    val learningLevel: String = "Beginner",
    val timezone: String = "Asia/Dhaka"
)

data class StudentDashboardStats(
    val streakDays: Int = 0,
    val vocabularyLearned: Int = 0,
    val patternsPracticed: Int = 0,
    val speakingSessions: Int = 0,
    val chatMessagesSent: Int = 0,
    val quizzesCompleted: Int = 0,
    val activeChatRooms: Int = 0,
    val readingPassagesCompleted: Int = 0,
    val readingMinutes: Int = 0
)

data class WeeklyActivityDay(
    val date: String,
    val dayNumber: String,
    val dayShort: String,
    val isToday: Boolean,
    val patternsActive: Boolean,
    val speakingActive: Boolean,
    val vocabularyActive: Boolean,
    val readingActive: Boolean = false,
    val practiceActive: Boolean
)

data class StreakCalendarData(
    val monthLabel: String = "",
    val year: Int = 2026,
    val month: Int = 1,
    val daysInMonth: Int = 30,
    val firstDayOfWeek: Int = 0,
    val todayDay: Int = 1,
    val activeDaysOfMonth: List<Int> = emptyList(),
    val longestStreak: Int = 0,
    val totalActiveDays: Int = 0
)

data class CourseReportModule(
    val key: String,
    val title: String,
    val percent: Int,
    val summary: String,
    val route: String
)

data class CourseReportData(
    val overallPercent: Int = 0,
    val modules: List<CourseReportModule> = emptyList()
)

data class ReadingReportRecentAttempt(
    val id: String = "",
    val passageId: String = "",
    val passageTitle: String = "",
    val level: String = "A1",
    val mode: String = "comprehension",
    val scorePercent: Int = 0,
    val createdAt: String = ""
)

data class ReadingReportSummary(
    val completedPassages: Int = 0,
    val inProgressPassages: Int = 0,
    val totalPassages: Int = 0,
    val completionPercent: Int = 0,
    val totalMinutes: Int = 0,
    val todayMinutes: Int = 0,
    val dailyGoalMinutes: Int = 15,
    val quizAttemptsCount: Int = 0,
    val averageQuizScore: Int = 0,
    val readAloudCount: Int = 0,
    val averageReadAloudScore: Int = 0,
    val recentAttempts: List<ReadingReportRecentAttempt> = emptyList()
)

data class StudentDashboardData(
    val user: StudentProfileSummary = StudentProfileSummary(),
    val stats: StudentDashboardStats = StudentDashboardStats(),
    val weekRangeLabel: String = "",
    val weeklyActivity: List<WeeklyActivityDay> = emptyList(),
    val streakCalendar: StreakCalendarData = StreakCalendarData(),
    val readingReport: ReadingReportSummary = ReadingReportSummary(),
    val courseReport: CourseReportData = CourseReportData()
)
