package com.example.feature.admin

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

@Immutable
data class AiInstructionsConfig(
    val speakingBasePrompt: String = "",
    val beginnerRule: String = "",
    val intermediateRule: String = "",
    val advancedRule: String = "",
    val chatGrammarPrompt: String = "",
    val vocabSentencePrompt: String = "",
    val speakingEvalPrompt: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("speakingBasePrompt", speakingBasePrompt)
        put("beginnerRule", beginnerRule)
        put("intermediateRule", intermediateRule)
        put("advancedRule", advancedRule)
        put("chatGrammarPrompt", chatGrammarPrompt)
        put("vocabSentencePrompt", vocabSentencePrompt)
        put("speakingEvalPrompt", speakingEvalPrompt)
    }
}

@Immutable
data class AiConversationConfig(
    val maxHistoryTurns: Int = 10,
    val replyMaxSentences: Int = 3,
    val defaultTopic: String = "Daily Life",
    val defaultLevel: String = "beginner",
    val autoCorrectGrammar: Boolean = true,
    val includeBanglaExplanation: Boolean = true,
    val speakingEnabled: Boolean = true,
    val chatGrammarEnabled: Boolean = true,
    val vocabEvalEnabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("maxHistoryTurns", maxHistoryTurns)
        put("replyMaxSentences", replyMaxSentences)
        put("defaultTopic", defaultTopic)
        put("defaultLevel", defaultLevel)
        put("autoCorrectGrammar", autoCorrectGrammar)
        put("includeBanglaExplanation", includeBanglaExplanation)
        put("speakingEnabled", speakingEnabled)
        put("chatGrammarEnabled", chatGrammarEnabled)
        put("vocabEvalEnabled", vocabEvalEnabled)
    }
}

@Immutable
data class AiUsageLimitsConfig(
    val enforceLimits: Boolean = true,
    val freeDailyTurns: Int = 20,
    val premiumDailyTurns: Int = 200,
    val dailyCostCapUsd: Double = 25.0,
    val rpmPerUser: Int = 20,
    val maxMessageChars: Int = 500
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("enforceLimits", enforceLimits)
        put("freeDailyTurns", freeDailyTurns)
        put("premiumDailyTurns", premiumDailyTurns)
        put("dailyCostCapUsd", dailyCostCapUsd)
        put("rpmPerUser", rpmPerUser)
        put("maxMessageChars", maxMessageChars)
    }
}

@Immutable
data class AiPricingConfig(
    val inputPerMillionUsd: Double = 0.1,
    val outputPerMillionUsd: Double = 0.4
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("inputPerMillionUsd", inputPerMillionUsd)
        put("outputPerMillionUsd", outputPerMillionUsd)
    }
}

@Immutable
data class AiConfigModel(
    val isEnabled: Boolean = true,
    val apiKeyConfigured: Boolean = false,
    val provider: String = "gemini",
    val model: String = "gemini-2.0-flash",
    val temperature: Float = 0.6f,
    val maxOutputTokens: Int = 500,
    val requestTimeoutMs: Int = 25000,
    val instructions: AiInstructionsConfig = AiInstructionsConfig(),
    val conversation: AiConversationConfig = AiConversationConfig(),
    val limits: AiUsageLimitsConfig = AiUsageLimitsConfig(),
    val pricing: AiPricingConfig = AiPricingConfig(),
    val updatedBy: String = "",
    val updatedAt: String? = null
)

@Immutable
data class AiModelOption(
    val id: String,
    val label: String,
    val inputPerMillionUsd: Double,
    val outputPerMillionUsd: Double
)

@Immutable
data class AiProviderOption(
    val id: String,
    val label: String,
    val defaultModel: String,
    val models: List<AiModelOption> = emptyList()
)

@Immutable
data class AiUsageBucketStats(
    val requests: Int = 0,
    val successCount: Int = 0,
    val errorCount: Int = 0,
    val blockedCount: Int = 0,
    val promptTokens: Long = 0L,
    val completionTokens: Long = 0L,
    val totalTokens: Long = 0L,
    val estimatedCostUsd: Double = 0.0,
    val avgLatencyMs: Int = 0,
    val errorRatePercent: Int = 0,
    val legacyInteractions: Int = 0
)

@Immutable
data class AiUsageDayPoint(
    val date: String,
    val dayShort: String,
    val isToday: Boolean,
    val requests: Int,
    val errors: Int,
    val tokens: Long,
    val costUsd: Double
)

@Immutable
data class AiFeatureUsageBreakdown(
    val feature: String,
    val label: String,
    val requests: Int,
    val tokens: Long,
    val costUsd: Double,
    val avgLatencyMs: Int
)

@Immutable
data class AiModelUsageBreakdown(
    val provider: String,
    val model: String,
    val requests: Int,
    val tokens: Long,
    val costUsd: Double
)

@Immutable
data class AiTopUserUsage(
    val userId: String,
    val name: String,
    val email: String,
    val isPremium: Boolean,
    val requests: Int,
    val tokens: Long,
    val costUsd: Double
)

@Immutable
data class AiRecentLogItem(
    val id: String,
    val userId: String,
    val feature: String,
    val featureLabel: String,
    val provider: String,
    val model: String,
    val totalTokens: Long,
    val estimatedCostUsd: Double,
    val latencyMs: Int,
    val status: String,
    val errorCode: String,
    val createdAt: String
)

@Immutable
data class AiUsageMonitorSummary(
    val windowDays: Int = 7,
    val today: AiUsageBucketStats = AiUsageBucketStats(),
    val window: AiUsageBucketStats = AiUsageBucketStats(),
    val allTime: AiUsageBucketStats = AiUsageBucketStats(),
    val dailySeries: List<AiUsageDayPoint> = emptyList(),
    val byFeature: List<AiFeatureUsageBreakdown> = emptyList(),
    val byModel: List<AiModelUsageBreakdown> = emptyList(),
    val topUsers: List<AiTopUserUsage> = emptyList(),
    val recentLogs: List<AiRecentLogItem> = emptyList()
)

@Immutable
data class AdminAiManagementOverview(
    val generatedAt: String = "",
    val config: AiConfigModel = AiConfigModel(),
    val providers: List<AiProviderOption> = emptyList(),
    val speakingTopics: List<String> = emptyList(),
    val usage: AiUsageMonitorSummary = AiUsageMonitorSummary()
)

object AdminAiManagementParser {

    fun formatUsd(amount: Double): String {
        val safe = if (amount.isFinite() && amount >= 0.0) amount else 0.0
        return when {
            safe == 0.0 -> "$0.00"
            safe < 0.01 -> String.format(Locale.US, "$%.4f", safe)
            else -> String.format(Locale.US, "$%.2f", safe)
        }
    }

    fun formatCompactTokens(tokens: Long): String {
        return when {
            tokens >= 1_000_000L -> String.format(Locale.US, "%.2fM", tokens / 1_000_000.0)
            tokens >= 1_000L -> String.format(Locale.US, "%.1fK", tokens / 1_000.0)
            else -> tokens.toString()
        }
    }

    fun parseOverview(root: JSONObject): AdminAiManagementOverview {
        val cfgObj = root.optJSONObject("config") ?: JSONObject()
        val instrObj = cfgObj.optJSONObject("instructions") ?: JSONObject()
        val convObj = cfgObj.optJSONObject("conversation") ?: JSONObject()
        val limitsObj = cfgObj.optJSONObject("limits") ?: JSONObject()
        val pricingObj = cfgObj.optJSONObject("pricing") ?: JSONObject()

        val config = AiConfigModel(
            isEnabled = cfgObj.optBoolean("isEnabled", true),
            apiKeyConfigured = cfgObj.optBoolean("apiKeyConfigured", false),
            provider = cfgObj.optString("provider", "gemini"),
            model = cfgObj.optString("model", "gemini-2.0-flash"),
            temperature = cfgObj.optDouble("temperature", 0.6).toFloat().coerceIn(0f, 1.5f),
            maxOutputTokens = cfgObj.optInt("maxOutputTokens", 500),
            requestTimeoutMs = cfgObj.optInt("requestTimeoutMs", 25000),
            instructions = AiInstructionsConfig(
                speakingBasePrompt = instrObj.optString("speakingBasePrompt"),
                beginnerRule = instrObj.optString("beginnerRule"),
                intermediateRule = instrObj.optString("intermediateRule"),
                advancedRule = instrObj.optString("advancedRule"),
                chatGrammarPrompt = instrObj.optString("chatGrammarPrompt"),
                vocabSentencePrompt = instrObj.optString("vocabSentencePrompt"),
                speakingEvalPrompt = instrObj.optString("speakingEvalPrompt")
            ),
            conversation = AiConversationConfig(
                maxHistoryTurns = convObj.optInt("maxHistoryTurns", 10),
                replyMaxSentences = convObj.optInt("replyMaxSentences", 3),
                defaultTopic = convObj.optString("defaultTopic", "Daily Life"),
                defaultLevel = convObj.optString("defaultLevel", "beginner"),
                autoCorrectGrammar = convObj.optBoolean("autoCorrectGrammar", true),
                includeBanglaExplanation = convObj.optBoolean("includeBanglaExplanation", true),
                speakingEnabled = convObj.optBoolean("speakingEnabled", true),
                chatGrammarEnabled = convObj.optBoolean("chatGrammarEnabled", true),
                vocabEvalEnabled = convObj.optBoolean("vocabEvalEnabled", true)
            ),
            limits = AiUsageLimitsConfig(
                enforceLimits = limitsObj.optBoolean("enforceLimits", true),
                freeDailyTurns = limitsObj.optInt("freeDailyTurns", 20),
                premiumDailyTurns = limitsObj.optInt("premiumDailyTurns", 200),
                dailyCostCapUsd = limitsObj.optDouble("dailyCostCapUsd", 25.0),
                rpmPerUser = limitsObj.optInt("rpmPerUser", 20),
                maxMessageChars = limitsObj.optInt("maxMessageChars", 500)
            ),
            pricing = AiPricingConfig(
                inputPerMillionUsd = pricingObj.optDouble("inputPerMillionUsd", 0.1),
                outputPerMillionUsd = pricingObj.optDouble("outputPerMillionUsd", 0.4)
            ),
            updatedBy = cfgObj.optString("updatedBy"),
            updatedAt = if (cfgObj.isNull("updatedAt")) null else cfgObj.optString("updatedAt")
        )

        val catalogObj = root.optJSONObject("catalog") ?: JSONObject()
        val providersArr = catalogObj.optJSONArray("providers") ?: JSONArray()
        val topicsArr = catalogObj.optJSONArray("speakingTopics") ?: JSONArray()

        val providers = List(providersArr.length()) { i ->
            val p = providersArr.getJSONObject(i)
            val mArr = p.optJSONArray("models") ?: JSONArray()
            AiProviderOption(
                id = p.optString("id"),
                label = p.optString("label"),
                defaultModel = p.optString("defaultModel"),
                models = List(mArr.length()) { j ->
                    val m = mArr.getJSONObject(j)
                    AiModelOption(
                        id = m.optString("id"),
                        label = m.optString("label"),
                        inputPerMillionUsd = m.optDouble("inputPerMillionUsd", 0.1),
                        outputPerMillionUsd = m.optDouble("outputPerMillionUsd", 0.4)
                    )
                }
            )
        }

        val topics = List(topicsArr.length()) { i -> topicsArr.optString(i) }.filter { it.isNotBlank() }

        val usageObj = root.optJSONObject("usage") ?: JSONObject()
        val dailyArr = usageObj.optJSONArray("dailySeries") ?: JSONArray()
        val featureArr = usageObj.optJSONArray("byFeature") ?: JSONArray()
        val modelArr = usageObj.optJSONArray("byModel") ?: JSONArray()
        val topUsersArr = usageObj.optJSONArray("topUsers") ?: JSONArray()
        val recentArr = usageObj.optJSONArray("recentLogs") ?: JSONArray()

        val usage = AiUsageMonitorSummary(
            windowDays = usageObj.optInt("windowDays", 7),
            today = parseBucket(usageObj.optJSONObject("today")),
            window = parseBucket(usageObj.optJSONObject("window")),
            allTime = parseBucket(usageObj.optJSONObject("allTime")),
            dailySeries = List(dailyArr.length()) { i ->
                val d = dailyArr.getJSONObject(i)
                AiUsageDayPoint(
                    date = d.optString("date"),
                    dayShort = d.optString("dayShort"),
                    isToday = d.optBoolean("isToday", false),
                    requests = d.optInt("requests", 0),
                    errors = d.optInt("errors", 0),
                    tokens = d.optLong("tokens", 0L),
                    costUsd = d.optDouble("costUsd", 0.0)
                )
            },
            byFeature = List(featureArr.length()) { i ->
                val f = featureArr.getJSONObject(i)
                AiFeatureUsageBreakdown(
                    feature = f.optString("feature"),
                    label = f.optString("label"),
                    requests = f.optInt("requests", 0),
                    tokens = f.optLong("tokens", 0L),
                    costUsd = f.optDouble("costUsd", 0.0),
                    avgLatencyMs = f.optInt("avgLatencyMs", 0)
                )
            },
            byModel = List(modelArr.length()) { i ->
                val m = modelArr.getJSONObject(i)
                AiModelUsageBreakdown(
                    provider = m.optString("provider"),
                    model = m.optString("model"),
                    requests = m.optInt("requests", 0),
                    tokens = m.optLong("tokens", 0L),
                    costUsd = m.optDouble("costUsd", 0.0)
                )
            },
            topUsers = List(topUsersArr.length()) { i ->
                val u = topUsersArr.getJSONObject(i)
                AiTopUserUsage(
                    userId = u.optString("userId"),
                    name = u.optString("name", "Student"),
                    email = u.optString("email"),
                    isPremium = u.optBoolean("isPremium", false),
                    requests = u.optInt("requests", 0),
                    tokens = u.optLong("tokens", 0L),
                    costUsd = u.optDouble("costUsd", 0.0)
                )
            },
            recentLogs = List(recentArr.length()) { i ->
                val l = recentArr.getJSONObject(i)
                AiRecentLogItem(
                    id = l.optString("id"),
                    userId = l.optString("userId"),
                    feature = l.optString("feature"),
                    featureLabel = l.optString("featureLabel", l.optString("feature")),
                    provider = l.optString("provider"),
                    model = l.optString("model"),
                    totalTokens = l.optLong("totalTokens", 0L),
                    estimatedCostUsd = l.optDouble("estimatedCostUsd", 0.0),
                    latencyMs = l.optInt("latencyMs", 0),
                    status = l.optString("status", "success"),
                    errorCode = l.optString("errorCode"),
                    createdAt = l.optString("createdAt")
                )
            }
        )

        return AdminAiManagementOverview(
            generatedAt = root.optString("generatedAt"),
            config = config,
            providers = providers,
            speakingTopics = topics,
            usage = usage
        )
    }

    private fun parseBucket(obj: JSONObject?): AiUsageBucketStats {
        if (obj == null) return AiUsageBucketStats()
        return AiUsageBucketStats(
            requests = obj.optInt("requests", 0),
            successCount = obj.optInt("successCount", 0),
            errorCount = obj.optInt("errorCount", 0),
            blockedCount = obj.optInt("blockedCount", 0),
            promptTokens = obj.optLong("promptTokens", 0L),
            completionTokens = obj.optLong("completionTokens", 0L),
            totalTokens = obj.optLong("totalTokens", 0L),
            estimatedCostUsd = obj.optDouble("estimatedCostUsd", 0.0),
            avgLatencyMs = obj.optInt("avgLatencyMs", 0),
            errorRatePercent = obj.optInt("errorRatePercent", 0),
            legacyInteractions = obj.optInt("legacyInteractions", 0)
        )
    }
}
