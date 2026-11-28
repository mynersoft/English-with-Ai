package com.example.data.repository

import com.example.core.network.NetworkClient
import com.example.core.network.sanitizeForBackendFailure
import com.example.domain.model.CourseReportData
import com.example.domain.model.CourseReportModule
import com.example.domain.model.PurchasedCourseItem
import com.example.domain.model.ReadingReportRecentAttempt
import com.example.domain.model.ReadingReportSummary
import com.example.domain.model.RecommendedCourseItem
import com.example.domain.model.StreakCalendarData
import com.example.domain.model.StudentDashboardData
import com.example.domain.model.StudentDashboardStats
import com.example.domain.model.StudentProfileSummary
import com.example.domain.model.WeeklyActivityDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class StudentDashboardRepository(
    context: android.content.Context? = null
) {
    private val speakingTracker: SpeakingProgressTracker? =
        context?.let { SpeakingProgressTracker(it) }

    suspend fun fetchStudentDashboard(): Result<StudentDashboardData> = withContext(Dispatchers.IO) {
        try {
            val data = requestJson("GET", "/api/v1/student/dashboard")
            val userObj = data.optJSONObject("user") ?: JSONObject()
            val statsObj = data.optJSONObject("stats") ?: JSONObject()
            val weekArr = data.optJSONArray("weeklyActivity") ?: JSONArray()
            val calObj = data.optJSONObject("streakCalendar") ?: JSONObject()
            val activeDaysArr = calObj.optJSONArray("activeDaysOfMonth") ?: JSONArray()
            val readRepObj = data.optJSONObject("readingReport") ?: JSONObject()
            val recentAttArr = readRepObj.optJSONArray("recentAttempts") ?: JSONArray()
            val reportObj = data.optJSONObject("courseReport") ?: JSONObject()
            val modulesArr = reportObj.optJSONArray("modules") ?: JSONArray()
            val purchasedArr = data.optJSONArray("purchasedCourses") ?: JSONArray()
            val recommendedArr = data.optJSONArray("recommendedCourses") ?: JSONArray()

            val purchasedList = List(purchasedArr.length()) { i ->
                val p = purchasedArr.getJSONObject(i)
                PurchasedCourseItem(
                    courseId = p.optString("courseId"),
                    title = p.optString("title", "Premium Course"),
                    subtitle = p.optString("subtitle"),
                    level = p.optString("level", "Beginner"),
                    category = p.optString("category", "General"),
                    price = p.optDouble("price", 0.0),
                    currency = p.optString("currency", "BDT"),
                    amountPaid = p.optDouble("amountPaid", 0.0),
                    paidAt = p.optString("paidAt"),
                    tranId = p.optString("tranId"),
                    totalLessons = p.optInt("totalLessons", 0),
                    completedLessons = p.optInt("completedLessons", 0),
                    completionPercent = p.optInt("completionPercent", 0)
                )
            }

            val recommendedList = List(recommendedArr.length()) { i ->
                val r = recommendedArr.getJSONObject(i)
                RecommendedCourseItem(
                    courseId = r.optString("courseId"),
                    title = r.optString("title", "Course"),
                    subtitle = r.optString("subtitle"),
                    level = r.optString("level", "Beginner"),
                    category = r.optString("category", "General"),
                    isPremium = r.optBoolean("isPremium", false),
                    price = r.optDouble("price", 0.0),
                    totalLessons = r.optInt("totalLessons", 0),
                    durationDays = r.optInt("durationDays", 30)
                )
            }

            val serverDashboard = StudentDashboardData(
                user = StudentProfileSummary(
                    id = userObj.optString("id"),
                    name = userObj.optString("name", "Learner"),
                    email = userObj.optString("email"),
                    photoUrl = userObj.optString("photoUrl").takeIf { it.isNotBlank() && it != "null" },
                    role = userObj.optString("role", "student"),
                    planLabel = userObj.optString("planLabel", "Free"),
                    learningLevel = userObj.optString("learningLevel", "Beginner"),
                    dailyGoalMinutes = userObj.optInt("dailyGoalMinutes", 20),
                    timezone = userObj.optString("timezone", "Asia/Dhaka")
                ),
                stats = StudentDashboardStats(
                    streakDays = statsObj.optInt("streakDays", 0),
                    vocabularyLearned = statsObj.optInt("vocabularyLearned", 0),
                    patternsPracticed = statsObj.optInt("patternsPracticed", 0),
                    speakingSessions = statsObj.optInt("speakingSessions", 0),
                    speakingMinutes = statsObj.optInt("speakingMinutes", 0),
                    chatMessagesSent = statsObj.optInt("chatMessagesSent", 0),
                    quizzesCompleted = statsObj.optInt("quizzesCompleted", 0),
                    activeChatRooms = statsObj.optInt("activeChatRooms", 0),
                    readingPassagesCompleted = statsObj.optInt("readingPassagesCompleted", 0),
                    readingMinutes = statsObj.optInt("readingMinutes", 0),
                    purchasedCoursesCount = statsObj.optInt("purchasedCoursesCount", purchasedList.size)
                ),
                purchasedCourses = purchasedList,
                recommendedCourses = recommendedList,
                weekRangeLabel = data.optString("weekRangeLabel"),
                weeklyActivity = List(weekArr.length()) { i ->
                    val d = weekArr.getJSONObject(i)
                    WeeklyActivityDay(
                        date = d.optString("date"),
                        dayNumber = d.optString("dayNumber"),
                        dayShort = d.optString("dayShort"),
                        isToday = d.optBoolean("isToday", false),
                        patternsActive = d.optBoolean("patternsActive", false),
                        speakingActive = d.optBoolean("speakingActive", false),
                        vocabularyActive = d.optBoolean("vocabularyActive", false),
                        readingActive = d.optBoolean("readingActive", false),
                        practiceActive = d.optBoolean("practiceActive", false)
                    )
                },
                streakCalendar = StreakCalendarData(
                    monthLabel = calObj.optString("monthLabel"),
                    year = calObj.optInt("year", 2026),
                    month = calObj.optInt("month", 1),
                    daysInMonth = calObj.optInt("daysInMonth", 30),
                    firstDayOfWeek = calObj.optInt("firstDayOfWeek", 0),
                    todayDay = calObj.optInt("todayDay", 1),
                    activeDaysOfMonth = List(activeDaysArr.length()) { i -> activeDaysArr.optInt(i) },
                    longestStreak = calObj.optInt("longestStreak", 0),
                    totalActiveDays = calObj.optInt("totalActiveDays", 0)
                ),
                readingReport = ReadingReportSummary(
                    completedPassages = readRepObj.optInt("completedPassages", 0),
                    inProgressPassages = readRepObj.optInt("inProgressPassages", 0),
                    totalPassages = readRepObj.optInt("totalPassages", 0),
                    completionPercent = readRepObj.optInt("completionPercent", 0),
                    totalMinutes = readRepObj.optInt("totalMinutes", 0),
                    todayMinutes = readRepObj.optInt("todayMinutes", 0),
                    dailyGoalMinutes = readRepObj.optInt("dailyGoalMinutes", 15),
                    quizAttemptsCount = readRepObj.optInt("quizAttemptsCount", 0),
                    averageQuizScore = readRepObj.optInt("averageQuizScore", 0),
                    readAloudCount = readRepObj.optInt("readAloudCount", 0),
                    averageReadAloudScore = readRepObj.optInt("averageReadAloudScore", 0),
                    recentAttempts = List(recentAttArr.length()) { i ->
                        val a = recentAttArr.getJSONObject(i)
                        ReadingReportRecentAttempt(
                            id = a.optString("id"),
                            passageId = a.optString("passageId"),
                            passageTitle = a.optString("passageTitle"),
                            level = a.optString("level", "A1"),
                            mode = a.optString("mode", "comprehension"),
                            scorePercent = a.optInt("scorePercent", 0),
                            createdAt = a.optString("createdAt")
                        )
                    }
                ),
                courseReport = CourseReportData(
                    overallPercent = reportObj.optInt("overallPercent", 0),
                    modules = List(modulesArr.length()) { i ->
                        val m = modulesArr.getJSONObject(i)
                        CourseReportModule(
                            key = m.optString("key"),
                            title = m.optString("title"),
                            percent = m.optInt("percent", 0),
                            summary = m.optString("summary"),
                            route = m.optString("route")
                        )
                    }
                )
            )
            val localSpeaking = speakingTracker?.getStats()
            Result.success(
                if (localSpeaking != null) mergeSpeakingProgress(serverDashboard, localSpeaking) else serverDashboard
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.sanitizeForBackendFailure())
        }
    }

    suspend fun recordPatternPractice(patternId: String) = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("type", "pattern")
                .put("patternId", patternId)
            requestJson("POST", "/api/v1/student/activity", payload)
        }
    }

    suspend fun recordSpeakingActivity(durationSeconds: Int, topic: String) = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("type", "speaking")
                .put("durationSeconds", durationSeconds)
                .put("topic", topic)
            requestJson("POST", "/api/v1/student/activity", payload)
        }
    }

    private suspend fun requestJson(
        method: String,
        path: String,
        body: JSONObject? = null
    ): JSONObject = NetworkClient.executeAuthorizedJson(
        method = method,
        path = path,
        body = body,
        notFoundHint = "Student dashboard API not found on server (404). Deploy latest backend to Vercel."
    )

    companion object {

        internal fun mergeSpeakingProgress(
            data: StudentDashboardData,
            local: LocalSpeakingStats
        ): StudentDashboardData {
            val existingSpeakingModule = data.courseReport.modules.firstOrNull {
                it.key == "speaking" || it.route == "speaking"
            }
            val serverTurns = existingSpeakingModule?.summary
                ?.trim()
                ?.substringBefore(' ')
                ?.toIntOrNull()
                ?: 0

            val mergedTurns = maxOf(serverTurns, local.turns)
            val mergedSessions = maxOf(data.stats.speakingSessions, local.sessions)
            val mergedMinutes = maxOf(data.stats.speakingMinutes, local.minutes)

            val updatedWeekly = if (local.activeDates.isEmpty()) {
                data.weeklyActivity
            } else {
                data.weeklyActivity.map { day ->
                    if (local.activeDates.contains(day.date)) {
                        day.copy(speakingActive = true)
                    } else {
                        day
                    }
                }
            }

            val hasActivity = mergedTurns > 0 || mergedSessions > 0 || mergedMinutes > 0
            val computedSpeakingPercent = if (hasActivity) {
                maxOf(mergedMinutes * 4, mergedTurns * 5).coerceIn(5, 100)
            } else {
                0
            }
            val speakingSummary = if (hasActivity) {
                "$mergedMinutes min talked with AI · $mergedTurns turns · $mergedSessions sessions completed"
            } else {
                existingSpeakingModule?.summary?.takeIf { it.isNotBlank() }
                    ?: "0 min talked with AI · 0 turns"
            }

            val updatedModules = if (existingSpeakingModule != null) {
                data.courseReport.modules.map { mod ->
                    if (mod.key == "speaking" || mod.route == "speaking") {
                        mod.copy(
                            percent = maxOf(mod.percent, computedSpeakingPercent),
                            summary = speakingSummary
                        )
                    } else {
                        mod
                    }
                }
            } else {
                data.courseReport.modules + CourseReportModule(
                    key = "speaking",
                    title = "AI Speaking",
                    percent = computedSpeakingPercent,
                    summary = speakingSummary,
                    route = "speaking"
                )
            }

            val avgPercent = if (updatedModules.isNotEmpty()) {
                updatedModules.sumOf { it.percent } / updatedModules.size
            } else {
                0
            }

            return data.copy(
                stats = data.stats.copy(
                    speakingSessions = mergedSessions,
                    speakingMinutes = mergedMinutes
                ),
                weeklyActivity = updatedWeekly,
                courseReport = data.courseReport.copy(
                    overallPercent = maxOf(data.courseReport.overallPercent, avgPercent),
                    modules = updatedModules
                )
            )
        }
    }
}
