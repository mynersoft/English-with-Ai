package com.example

import com.example.feature.admin.AdminAiManagementParser
import com.example.feature.admin.AiConversationConfig
import com.example.feature.admin.AiInstructionsConfig
import com.example.feature.admin.AiUsageLimitsConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdminAiManagementUnitTest {

    @Test
    fun parseOverviewParsesConfigCatalogUsageAndLogsCorrectly() {
        val sample = JSONObject().apply {
            put("generatedAt", "2026-11-27T10:00:00Z")
            put(
                "config",
                JSONObject().apply {
                    put("isEnabled", true)
                    put("apiKeyConfigured", true)
                    put("provider", "groq")
                    put("model", "llama-3.3-70b-versatile")
                    put("temperature", 0.75)
                    put("maxOutputTokens", 700)
                    put("requestTimeoutMs", 30000)
                    put(
                        "instructions",
                        JSONObject().apply {
                            put("speakingBasePrompt", "Speak kindly on {{topic}}")
                            put("beginnerRule", "Keep words simple.")
                            put("intermediateRule", "Use natural phrases.")
                            put("advancedRule", "Use rich idioms.")
                            put("chatGrammarPrompt", "Fix grammar gently.")
                        }
                    )
                    put(
                        "conversation",
                        JSONObject().apply {
                            put("maxHistoryTurns", 12)
                            put("replyMaxSentences", 2)
                            put("defaultTopic", "Job Interviews")
                            put("defaultLevel", "intermediate")
                            put("autoCorrectGrammar", true)
                            put("includeBanglaExplanation", false)
                            put("speakingEnabled", true)
                            put("chatGrammarEnabled", false)
                            put("vocabEvalEnabled", true)
                        }
                    )
                    put(
                        "limits",
                        JSONObject().apply {
                            put("enforceLimits", true)
                            put("freeDailyTurns", 15)
                            put("premiumDailyTurns", 250)
                            put("dailyCostCapUsd", 19.5)
                            put("rpmPerUser", 12)
                            put("maxMessageChars", 600)
                        }
                    )
                    put(
                        "pricing",
                        JSONObject().apply {
                            put("inputPerMillionUsd", 0.59)
                            put("outputPerMillionUsd", 0.79)
                        }
                    )
                }
            )
            put(
                "catalog",
                JSONObject().apply {
                    put(
                        "providers",
                        JSONArray().put(
                            JSONObject().apply {
                                put("id", "groq")
                                put("label", "Groq Cloud")
                                put("defaultModel", "llama-3.3-70b-versatile")
                                put(
                                    "models",
                                    JSONArray().put(
                                        JSONObject().apply {
                                            put("id", "llama-3.3-70b-versatile")
                                            put("label", "Llama 3.3 70B")
                                            put("inputPerMillionUsd", 0.59)
                                            put("outputPerMillionUsd", 0.79)
                                        }
                                    )
                                )
                            }
                        )
                    )
                    put("speakingTopics", JSONArray().put("Daily Life").put("Job Interviews"))
                }
            )
            put(
                "usage",
                JSONObject().apply {
                    put("windowDays", 7)
                    put(
                        "today",
                        JSONObject().apply {
                            put("requests", 42)
                            put("successCount", 40)
                            put("errorCount", 2)
                            put("totalTokens", 18400)
                            put("estimatedCostUsd", 0.0124)
                            put("avgLatencyMs", 420)
                        }
                    )
                    put(
                        "window",
                        JSONObject().apply {
                            put("requests", 210)
                            put("totalTokens", 95000)
                            put("estimatedCostUsd", 0.084)
                        }
                    )
                    put(
                        "byFeature",
                        JSONArray().put(
                            JSONObject().apply {
                                put("feature", "speaking_turn")
                                put("label", "AI Speaking Partner")
                                put("requests", 150)
                                put("tokens", 70000)
                                put("costUsd", 0.062)
                                put("avgLatencyMs", 410)
                            }
                        )
                    )
                }
            )
        }

        val parsed = AdminAiManagementParser.parseOverview(sample)
        assertTrue(parsed.config.isEnabled)
        assertEquals("groq", parsed.config.provider)
        assertEquals("llama-3.3-70b-versatile", parsed.config.model)
        assertEquals(0.75f, parsed.config.temperature, 0.01f)
        assertEquals(12, parsed.config.conversation.maxHistoryTurns)
        assertFalse(parsed.config.conversation.chatGrammarEnabled)
        assertEquals(15, parsed.config.limits.freeDailyTurns)
        assertEquals(19.5, parsed.config.limits.dailyCostCapUsd, 0.001)
        assertEquals(42, parsed.usage.today.requests)
        assertEquals(18400L, parsed.usage.today.totalTokens)
        assertEquals(1, parsed.usage.byFeature.size)
        assertEquals("AI Speaking Partner", parsed.usage.byFeature[0].label)
    }

    @Test
    fun formattingAndSerializationHelpersProduceExpectedOutput() {
        assertEquals("$0.00", AdminAiManagementParser.formatUsd(0.0))
        assertEquals("$0.0042", AdminAiManagementParser.formatUsd(0.0042))
        assertEquals("$1.25", AdminAiManagementParser.formatUsd(1.25))
        assertEquals("1.5K", AdminAiManagementParser.formatCompactTokens(1500L))
        assertEquals("2.50M", AdminAiManagementParser.formatCompactTokens(2_500_000L))

        val instrJson = AiInstructionsConfig(speakingBasePrompt = "Prompt", beginnerRule = "Rule").toJson()
        assertEquals("Prompt", instrJson.getString("speakingBasePrompt"))
        assertEquals("Rule", instrJson.getString("beginnerRule"))

        val limitsJson = AiUsageLimitsConfig(freeDailyTurns = 25, premiumDailyTurns = 300).toJson()
        assertEquals(25, limitsJson.getInt("freeDailyTurns"))
        assertEquals(300, limitsJson.getInt("premiumDailyTurns"))

        val convJson = AiConversationConfig(maxHistoryTurns = 8, defaultTopic = "Travel").toJson()
        assertEquals(8, convJson.getInt("maxHistoryTurns"))
        assertEquals("Travel", convJson.getString("defaultTopic"))
    }
}
