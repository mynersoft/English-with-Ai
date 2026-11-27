package com.example.feature.admin

import androidx.compose.runtime.Immutable
import com.example.core.network.NetworkClient
import com.example.core.network.sanitizeForBackendFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class AdminDauTrendPoint(
    val date: String,
    val dayShort: String,
    val isToday: Boolean,
    val activeUsers: Int
)

@Immutable
data class AdminDashboardStudentsMetrics(
    val totalRegistered: Int = 0,
    val activeAccounts: Int = 0,
    val activeLast7Days: Int = 0,
    val dailyActiveUsers: Int = 0,
    val dauPercentOfActive: Int = 0,
    val newToday: Int = 0,
    val newLast7Days: Int = 0,
    val suspended: Int = 0,
    val deactivated: Int = 0,
    val dailyActiveTrend: List<AdminDauTrendPoint> = emptyList()
)

@Immutable
data class AdminCourseModuleCompletion(
    val key: String,
    val title: String,
    val percent: Int,
    val completions: Int,
    val mastered: Int,
    val activeLearners: Int,
    val catalogCount: Int,
    val summary: String
)

@Immutable
data class AdminDashboardCourseCompletion(
    val overallPercent: Int = 0,
    val publishedWords: Int = 0,
    val publishedPatterns: Int = 0,
    val publishedPassages: Int = 0,
    val vocabQuizAttempts: Int = 0,
    val readingQuizAttempts: Int = 0,
    val avgReadingQuizScore: Int = 0,
    val modules: List<AdminCourseModuleCompletion> = emptyList()
)

@Immutable
data class AdminDashboardAiUsage(
    val configured: Boolean = false,
    val provider: String = "gemini",
    val model: String = "",
    val totalInteractions: Int = 0,
    val todayInteractions: Int = 0,
    val speakingTurnsTotal: Int = 0,
    val speakingTurnsToday: Int = 0,
    val speakingEvaluationsTotal: Int = 0,
    val speakingEvaluationsToday: Int = 0,
    val avgSpeakingScore: Int = 0,
    val chatGrammarChecksTotal: Int = 0,
    val chatGrammarChecksToday: Int = 0,
    val chatMistakesFixedTotal: Int = 0
)

@Immutable
data class AdminSubscriptionBucket(
    val key: String,
    val label: String,
    val count: Int,
    val percent: Int
)

@Immutable
data class AdminDashboardSubscriptions(
    val totalStudents: Int = 0,
    val paidActiveCount: Int = 0,
    val trialCount: Int = 0,
    val freeCount: Int = 0,
    val expiredCount: Int = 0,
    val cancelledCount: Int = 0,
    val premiumOrTrialCount: Int = 0,
    val paidConversionPercent: Int = 0,
    val expiringSoonCount: Int = 0,
    val byStatus: List<AdminSubscriptionBucket> = emptyList(),
    val byPlan: List<AdminSubscriptionBucket> = emptyList()
)

@Immutable
data class AdminHealthCheckItem(
    val key: String,
    val label: String,
    val status: String, // "healthy" | "degraded" | "issue"
    val detail: String
)

@Immutable
data class AdminDashboardSystemHealth(
    val status: String = "healthy",
    val summaryLabel: String = "Checking...",
    val uptimeSeconds: Long = 0L,
    val uptimeLabel: String = "0s",
    val memoryUsedMb: Int = 0,
    val memoryRssMb: Int = 0,
    val dbLatencyMs: Int? = null,
    val fcmEnabledStudents: Int = 0,
    val checks: List<AdminHealthCheckItem> = emptyList()
)

@Immutable
data class AdminDashboardOverview(
    val generatedAt: String = "",
    val students: AdminDashboardStudentsMetrics = AdminDashboardStudentsMetrics(),
    val courseCompletion: AdminDashboardCourseCompletion = AdminDashboardCourseCompletion(),
    val aiUsage: AdminDashboardAiUsage = AdminDashboardAiUsage(),
    val subscriptions: AdminDashboardSubscriptions = AdminDashboardSubscriptions(),
    val systemHealth: AdminDashboardSystemHealth = AdminDashboardSystemHealth()
)

object AdminDashboardApi {

    suspend fun fetchDashboard(): Result<AdminDashboardOverview> =
        withContext(Dispatchers.IO) {
            try {
                val data = NetworkClient.executeAuthorizedJson(
                    method = "GET",
                    path = "/api/v1/admin/dashboard",
                    client = NetworkClient.admin,
                    notFoundHint = "Admin Dashboard API not found on server (404). Deploy latest backend."
                )
                Result.success(parseDashboard(data))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e.sanitizeForBackendFailure())
            }
        }

    private fun parseDashboard(data: JSONObject): AdminDashboardOverview {
        val studentsObj = data.optJSONObject("students") ?: JSONObject()
        val trendArr = studentsObj.optJSONArray("dailyActiveTrend") ?: JSONArray()

        val courseObj = data.optJSONObject("courseCompletion") ?: JSONObject()
        val modulesArr = courseObj.optJSONArray("modules") ?: JSONArray()

        val aiObj = data.optJSONObject("aiUsage") ?: JSONObject()

        val subObj = data.optJSONObject("subscriptions") ?: JSONObject()
        val byStatusArr = subObj.optJSONArray("byStatus") ?: JSONArray()
        val byPlanArr = subObj.optJSONArray("byPlan") ?: JSONArray()

        val healthObj = data.optJSONObject("systemHealth") ?: JSONObject()
        val checksArr = healthObj.optJSONArray("checks") ?: JSONArray()

        return AdminDashboardOverview(
            generatedAt = data.optString("generatedAt"),
            students = AdminDashboardStudentsMetrics(
                totalRegistered = studentsObj.optInt("totalRegistered", 0),
                activeAccounts = studentsObj.optInt("activeAccounts", 0),
                activeLast7Days = studentsObj.optInt("activeLast7Days", 0),
                dailyActiveUsers = studentsObj.optInt("dailyActiveUsers", 0),
                dauPercentOfActive = studentsObj.optInt("dauPercentOfActive", 0),
                newToday = studentsObj.optInt("newToday", 0),
                newLast7Days = studentsObj.optInt("newLast7Days", 0),
                suspended = studentsObj.optInt("suspended", 0),
                deactivated = studentsObj.optInt("deactivated", 0),
                dailyActiveTrend = List(trendArr.length()) { i ->
                    val item = trendArr.getJSONObject(i)
                    AdminDauTrendPoint(
                        date = item.optString("date"),
                        dayShort = item.optString("dayShort"),
                        isToday = item.optBoolean("isToday", false),
                        activeUsers = item.optInt("activeUsers", 0)
                    )
                }
            ),
            courseCompletion = AdminDashboardCourseCompletion(
                overallPercent = courseObj.optInt("overallPercent", 0),
                publishedWords = courseObj.optInt("publishedWords", 0),
                publishedPatterns = courseObj.optInt("publishedPatterns", 0),
                publishedPassages = courseObj.optInt("publishedPassages", 0),
                vocabQuizAttempts = courseObj.optInt("vocabQuizAttempts", 0),
                readingQuizAttempts = courseObj.optInt("readingQuizAttempts", 0),
                avgReadingQuizScore = courseObj.optInt("avgReadingQuizScore", 0),
                modules = List(modulesArr.length()) { i ->
                    val m = modulesArr.getJSONObject(i)
                    AdminCourseModuleCompletion(
                        key = m.optString("key"),
                        title = m.optString("title"),
                        percent = m.optInt("percent", 0),
                        completions = m.optInt("completions", 0),
                        mastered = m.optInt("mastered", 0),
                        activeLearners = m.optInt("activeLearners", 0),
                        catalogCount = m.optInt("catalogCount", 0),
                        summary = m.optString("summary")
                    )
                }
            ),
            aiUsage = AdminDashboardAiUsage(
                configured = aiObj.optBoolean("configured", false),
                provider = aiObj.optString("provider", "gemini"),
                model = aiObj.optString("model"),
                totalInteractions = aiObj.optInt("totalInteractions", 0),
                todayInteractions = aiObj.optInt("todayInteractions", 0),
                speakingTurnsTotal = aiObj.optInt("speakingTurnsTotal", 0),
                speakingTurnsToday = aiObj.optInt("speakingTurnsToday", 0),
                speakingEvaluationsTotal = aiObj.optInt("speakingEvaluationsTotal", 0),
                speakingEvaluationsToday = aiObj.optInt("speakingEvaluationsToday", 0),
                avgSpeakingScore = aiObj.optInt("avgSpeakingScore", 0),
                chatGrammarChecksTotal = aiObj.optInt("chatGrammarChecksTotal", 0),
                chatGrammarChecksToday = aiObj.optInt("chatGrammarChecksToday", 0),
                chatMistakesFixedTotal = aiObj.optInt("chatMistakesFixedTotal", 0)
            ),
            subscriptions = AdminDashboardSubscriptions(
                totalStudents = subObj.optInt("totalStudents", 0),
                paidActiveCount = subObj.optInt("paidActiveCount", 0),
                trialCount = subObj.optInt("trialCount", 0),
                freeCount = subObj.optInt("freeCount", 0),
                expiredCount = subObj.optInt("expiredCount", 0),
                cancelledCount = subObj.optInt("cancelledCount", 0),
                premiumOrTrialCount = subObj.optInt("premiumOrTrialCount", 0),
                paidConversionPercent = subObj.optInt("paidConversionPercent", 0),
                expiringSoonCount = subObj.optInt("expiringSoonCount", 0),
                byStatus = parseBuckets(byStatusArr),
                byPlan = parseBuckets(byPlanArr)
            ),
            systemHealth = AdminDashboardSystemHealth(
                status = healthObj.optString("status", "healthy"),
                summaryLabel = healthObj.optString("summaryLabel", "Operational"),
                uptimeSeconds = healthObj.optLong("uptimeSeconds", 0L),
                uptimeLabel = healthObj.optString("uptimeLabel", "0s"),
                memoryUsedMb = healthObj.optInt("memoryUsedMb", 0),
                memoryRssMb = healthObj.optInt("memoryRssMb", 0),
                dbLatencyMs = if (healthObj.isNull("dbLatencyMs")) null else healthObj.optInt("dbLatencyMs", 0),
                fcmEnabledStudents = healthObj.optInt("fcmEnabledStudents", 0),
                checks = List(checksArr.length()) { i ->
                    val c = checksArr.getJSONObject(i)
                    AdminHealthCheckItem(
                        key = c.optString("key"),
                        label = c.optString("label"),
                        status = c.optString("status", "healthy"),
                        detail = c.optString("detail")
                    )
                }
            )
        )
    }

    private fun parseBuckets(arr: JSONArray): List<AdminSubscriptionBucket> =
        List(arr.length()) { i ->
            val b = arr.getJSONObject(i)
            AdminSubscriptionBucket(
                key = b.optString("key"),
                label = b.optString("label"),
                count = b.optInt("count", 0),
                percent = b.optInt("percent", 0)
            )
        }
}
