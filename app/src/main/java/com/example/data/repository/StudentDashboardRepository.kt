package com.example.data.repository

import com.example.BuildConfig
import com.example.core.network.NetworkClient
import com.example.domain.model.CourseReportData
import com.example.domain.model.CourseReportModule
import com.example.domain.model.ReadingReportRecentAttempt
import com.example.domain.model.ReadingReportSummary
import com.example.domain.model.StreakCalendarData
import com.example.domain.model.StudentDashboardData
import com.example.domain.model.StudentDashboardStats
import com.example.domain.model.StudentProfileSummary
import com.example.domain.model.WeeklyActivityDay
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class StudentDashboardRepository {

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

            Result.success(
                StudentDashboardData(
                    user = StudentProfileSummary(
                        id = userObj.optString("id"),
                        name = userObj.optString("name", "Learner"),
                        email = userObj.optString("email"),
                        photoUrl = userObj.optString("photoUrl").takeIf { it.isNotBlank() && it != "null" },
                        role = userObj.optString("role", "student"),
                        planLabel = userObj.optString("planLabel", "Free"),
                        learningLevel = userObj.optString("learningLevel", "Beginner"),
                        timezone = userObj.optString("timezone", "Asia/Dhaka")
                    ),
                    stats = StudentDashboardStats(
                        streakDays = statsObj.optInt("streakDays", 0),
                        vocabularyLearned = statsObj.optInt("vocabularyLearned", 0),
                        patternsPracticed = statsObj.optInt("patternsPracticed", 0),
                        speakingSessions = statsObj.optInt("speakingSessions", 0),
                        chatMessagesSent = statsObj.optInt("chatMessagesSent", 0),
                        quizzesCompleted = statsObj.optInt("quizzesCompleted", 0),
                        activeChatRooms = statsObj.optInt("activeChatRooms", 0),
                        readingPassagesCompleted = statsObj.optInt("readingPassagesCompleted", 0),
                        readingMinutes = statsObj.optInt("readingMinutes", 0)
                    ),
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
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
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

    private suspend fun requestJson(
        method: String,
        path: String,
        body: JSONObject? = null
    ): JSONObject {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            throw BackendApiException("Backend URL is not configured.", "CONFIG_MISSING", 500)
        }
        val user = Firebase.auth.currentUser
            ?: throw BackendApiException("Please sign in first.", "UNAUTHORIZED", 401)
        val token = user.getIdToken(false).await().token
            ?: throw BackendApiException("Could not verify authentication token.", "UNAUTHORIZED", 401)

        val builder = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")

        val reqBody = body?.toString()?.toRequestBody(JSON_TYPE)
            ?: if (method == "POST") "{}".toRequestBody(JSON_TYPE) else null
        builder.method(method, reqBody)

        NetworkClient.standard.newCall(builder.build()).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!res.isSuccessful || json?.optBoolean("success", true) == false) {
                val errObj = json?.optJSONObject("error")
                val code = errObj?.optString("code")?.takeIf { it.isNotBlank() }
                val message = errObj?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: json?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: when {
                        res.code == 404 && raw.trimStart().startsWith("<") ->
                            "Student dashboard API not found on server (404). Deploy latest backend to Vercel."
                        raw.trimStart().startsWith("<") -> "Server error (${res.code})"
                        else -> "Request failed (${res.code})"
                    }
                throw BackendApiException(message, code, res.code)
            }
            return json?.optJSONObject("data") ?: json ?: JSONObject()
        }
    }

    private companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
