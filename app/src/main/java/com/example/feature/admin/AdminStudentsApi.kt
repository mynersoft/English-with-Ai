package com.example.feature.admin

import androidx.compose.runtime.Immutable
import com.example.core.network.NetworkClient
import com.example.core.network.sanitizeForBackendFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

@Immutable
data class AdminStudentProgressSummary(
    val vocabularyLearned: Int = 0,
    val patternsPracticed: Int = 0,
    val readingCompleted: Int = 0,
    val readingMinutes: Int = 0
)

@Immutable
data class AdminStudentSummary(
    val id: String,
    val firebaseUid: String,
    val name: String,
    val email: String,
    val photoUrl: String? = null,
    val role: String = "student",
    val status: String = "active",
    val subscriptionStatus: String = "free",
    val subscriptionPlan: String = "free",
    val subscriptionLabel: String = "Free",
    val subscriptionExpiresAt: String? = null,
    val learningLevel: String = "beginner",
    val dailyGoalMinutes: Int = 20,
    val authProviders: List<String> = emptyList(),
    val lastLoginAt: String? = null,
    val createdAt: String? = null,
    val progressSummary: AdminStudentProgressSummary = AdminStudentProgressSummary()
)

@Immutable
data class AdminStudentCounts(
    val totalStudents: Int = 0,
    val activeCount: Int = 0,
    val suspendedCount: Int = 0,
    val deactivatedCount: Int = 0,
    val premiumActiveCount: Int = 0
)

@Immutable
data class AdminStudentListPage(
    val items: List<AdminStudentSummary> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0,
    val totalPages: Int = 1,
    val counts: AdminStudentCounts = AdminStudentCounts()
)

@Immutable
data class AdminStudentModuleProgress(
    val key: String,
    val title: String,
    val percent: Int,
    val summary: String
)

@Immutable
data class AdminStudentDetailedStats(
    val streakDays: Int = 0,
    val longestStreak: Int = 0,
    val totalActiveDays: Int = 0,
    val vocabularyLearned: Int = 0,
    val patternsPracticed: Int = 0,
    val speakingSessions: Int = 0,
    val chatMessagesSent: Int = 0,
    val quizzesCompleted: Int = 0,
    val readingPassagesCompleted: Int = 0,
    val readingMinutes: Int = 0,
    val averageQuizScore: Int = 0,
    val overallPercent: Int = 0
)

@Immutable
data class AdminStudentDetail(
    val student: AdminStudentSummary,
    val stats: AdminStudentDetailedStats = AdminStudentDetailedStats(),
    val modules: List<AdminStudentModuleProgress> = emptyList()
)

object AdminStudentsApi {

    suspend fun listStudents(
        page: Int = 1,
        limit: Int = 20,
        search: String = "",
        accountStatus: String = "all",
        subscriptionStatus: String = "all"
    ): Result<AdminStudentListPage> = safeCall {
        val q = buildString {
            append("?page=").append(page)
            append("&limit=").append(limit)
            if (search.isNotBlank()) append("&search=").append(urlEnc(search.trim()))
            if (accountStatus != "all") append("&status=").append(urlEnc(accountStatus))
            if (subscriptionStatus != "all") append("&subscriptionStatus=").append(urlEnc(subscriptionStatus))
        }
        val data = requestJson("GET", "/api/v1/admin/students$q")
        val itemsArr = data.optJSONArray("items") ?: JSONArray()
        val countsObj = data.optJSONObject("counts") ?: JSONObject()

        AdminStudentListPage(
            items = List(itemsArr.length()) { i -> parseStudentSummary(itemsArr.getJSONObject(i)) },
            page = data.optInt("page", page),
            limit = data.optInt("limit", limit),
            total = data.optInt("total", 0),
            totalPages = data.optInt("totalPages", 1),
            counts = AdminStudentCounts(
                totalStudents = countsObj.optInt("totalStudents", 0),
                activeCount = countsObj.optInt("activeCount", 0),
                suspendedCount = countsObj.optInt("suspendedCount", 0),
                deactivatedCount = countsObj.optInt("deactivatedCount", 0),
                premiumActiveCount = countsObj.optInt("premiumActiveCount", 0)
            )
        )
    }

    suspend fun getStudentDetail(studentId: String): Result<AdminStudentDetail> = safeCall {
        val data = requestJson("GET", "/api/v1/admin/students/$studentId")
        val student = parseStudentSummary(data.getJSONObject("student"))
        val statsObj = data.optJSONObject("stats") ?: JSONObject()
        val streakObj = data.optJSONObject("streakCalendar") ?: JSONObject()
        val readingObj = data.optJSONObject("readingReport") ?: JSONObject()
        val courseObj = data.optJSONObject("courseReport") ?: JSONObject()
        val modulesArr = courseObj.optJSONArray("modules") ?: JSONArray()

        AdminStudentDetail(
            student = student,
            stats = AdminStudentDetailedStats(
                streakDays = statsObj.optInt("streakDays", 0),
                longestStreak = streakObj.optInt("longestStreak", 0),
                totalActiveDays = streakObj.optInt("totalActiveDays", 0),
                vocabularyLearned = statsObj.optInt("vocabularyLearned", 0),
                patternsPracticed = statsObj.optInt("patternsPracticed", 0),
                speakingSessions = statsObj.optInt("speakingSessions", 0),
                chatMessagesSent = statsObj.optInt("chatMessagesSent", 0),
                quizzesCompleted = statsObj.optInt("quizzesCompleted", 0),
                readingPassagesCompleted = statsObj.optInt("readingPassagesCompleted", 0),
                readingMinutes = statsObj.optInt("readingMinutes", 0),
                averageQuizScore = readingObj.optInt("averageQuizScore", 0),
                overallPercent = courseObj.optInt("overallPercent", 0)
            ),
            modules = List(modulesArr.length()) { i ->
                val m = modulesArr.getJSONObject(i)
                AdminStudentModuleProgress(
                    key = m.optString("key"),
                    title = m.optString("title"),
                    percent = m.optInt("percent", 0),
                    summary = m.optString("summary")
                )
            }
        )
    }

    suspend fun updateAccountStatus(
        studentId: String,
        status: String
    ): Result<AdminStudentSummary> = safeCall {
        val body = JSONObject().put("status", status)
        val data = requestJson("PATCH", "/api/v1/admin/students/$studentId/status", body)
        parseStudentSummary(data.getJSONObject("student"))
    }

    suspend fun updateSubscription(
        studentId: String,
        subscriptionStatus: String,
        subscriptionPlan: String,
        subscriptionExpiresAt: String?
    ): Result<AdminStudentSummary> = safeCall {
        val body = JSONObject()
            .put("subscriptionStatus", subscriptionStatus)
            .put("subscriptionPlan", subscriptionPlan)
        if (subscriptionExpiresAt.isNullOrBlank()) {
            body.put("subscriptionExpiresAt", JSONObject.NULL)
        } else {
            body.put("subscriptionExpiresAt", subscriptionExpiresAt)
        }
        val data = requestJson("PATCH", "/api/v1/admin/students/$studentId/subscription", body)
        parseStudentSummary(data.getJSONObject("student"))
    }

    private fun parseStudentSummary(o: JSONObject): AdminStudentSummary {
        val provArr = o.optJSONArray("authProviders") ?: JSONArray()
        val progObj = o.optJSONObject("progressSummary") ?: JSONObject()
        return AdminStudentSummary(
            id = o.optString("id"),
            firebaseUid = o.optString("firebaseUid"),
            name = o.optString("name", "Learner"),
            email = o.optString("email"),
            photoUrl = o.optStringOrNull("photoUrl"),
            role = o.optString("role", "student"),
            status = o.optString("status", "active"),
            subscriptionStatus = o.optString("subscriptionStatus", "free"),
            subscriptionPlan = o.optString("subscriptionPlan", "free"),
            subscriptionLabel = o.optString("subscriptionLabel", "Free"),
            subscriptionExpiresAt = o.optStringOrNull("subscriptionExpiresAt"),
            learningLevel = o.optString("learningLevel", "beginner"),
            dailyGoalMinutes = o.optInt("dailyGoalMinutes", 20),
            authProviders = List(provArr.length()) { i -> provArr.optString(i) }.filter { it.isNotBlank() },
            lastLoginAt = o.optStringOrNull("lastLoginAt"),
            createdAt = o.optStringOrNull("createdAt"),
            progressSummary = AdminStudentProgressSummary(
                vocabularyLearned = progObj.optInt("vocabularyLearned", 0),
                patternsPracticed = progObj.optInt("patternsPracticed", 0),
                readingCompleted = progObj.optInt("readingCompleted", 0),
                readingMinutes = progObj.optInt("readingMinutes", 0)
            )
        )
    }

    private suspend inline fun <T> safeCall(crossinline block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e.sanitizeForBackendFailure())
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
        client = NetworkClient.admin,
        notFoundHint = "Student Management API not found on server (404). Deploy latest backend to Vercel."
    )

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }

    private fun urlEnc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
