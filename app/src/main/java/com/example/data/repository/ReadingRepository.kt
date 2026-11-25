package com.example.data.repository

import com.example.BuildConfig
import com.example.core.network.NetworkClient
import com.example.domain.model.PaginatedReadingPassages
import com.example.domain.model.ReadingAttemptAnswerResult
import com.example.domain.model.ReadingAttemptRecord
import com.example.domain.model.ReadingCategory
import com.example.domain.model.ReadingDashboardData
import com.example.domain.model.ReadingDashboardStats
import com.example.domain.model.ReadingPassageBundle
import com.example.domain.model.ReadingPassageDetail
import com.example.domain.model.ReadingPassageSummary
import com.example.domain.model.ReadingQuestion
import com.example.domain.model.ReadingQuestionType
import com.example.domain.model.ReadingSaveWordResult
import com.example.domain.model.ReadingSentence
import com.example.domain.model.ReadingSettings
import com.example.domain.model.ReadingUserProgress
import com.example.domain.model.ReadingVocabItem
import com.example.domain.model.ReadingWeeklyDay
import com.example.domain.model.ReadingWordLookup
import com.example.domain.model.TextMatchingSummary
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
import java.io.IOException
import java.net.URLEncoder

class ReadingRepository {

    suspend fun fetchDashboard(): Result<ReadingDashboardData> = safeCall {
        val data = requestJson("GET", "/api/v1/reading/dashboard")
        val statsObj = data.optJSONObject("stats") ?: JSONObject()
        val weekArr = data.optJSONArray("weeklyActivity") ?: JSONArray()
        val catsArr = data.optJSONArray("categories") ?: JSONArray()

        ReadingDashboardData(
            stats = ReadingDashboardStats(
                dailyGoalMinutes = statsObj.optInt("dailyGoalMinutes", 15),
                todayMinutes = statsObj.optInt("todayMinutes", 0),
                completedPassagesCount = statsObj.optInt("completedPassagesCount", 0),
                totalPublishedPassages = statsObj.optInt("totalPublishedPassages", 0),
                streakDays = statsObj.optInt("streakDays", 0),
                longestStreak = statsObj.optInt("longestStreak", 0),
                totalReadingMinutes = statsObj.optInt("totalReadingMinutes", 0)
            ),
            continueReading = data.optJSONObject("continueReading")?.let(::parsePassageSummary),
            recent = parsePassageList(data.optJSONArray("recent")),
            recommended = parsePassageList(data.optJSONArray("recommended")),
            history = parseAttemptList(data.optJSONArray("history")),
            weekRangeLabel = data.optString("weekRangeLabel"),
            weeklyActivity = List(weekArr.length()) { i ->
                val w = weekArr.getJSONObject(i)
                ReadingWeeklyDay(
                    date = w.optString("date"),
                    dayNumber = w.optString("dayNumber"),
                    dayShort = w.optString("dayShort"),
                    isToday = w.optBoolean("isToday", false),
                    minutes = w.optInt("minutes", 0),
                    completedCount = w.optInt("completedCount", 0),
                    isActive = w.optBoolean("isActive", false)
                )
            },
            categories = parseCategoryList(catsArr),
            settings = parseSettings(data.optJSONObject("settings"))
        )
    }

    suspend fun fetchPassages(
        page: Int = 1,
        limit: Int = 15,
        level: String = "ALL",
        categoryId: String = "",
        search: String = ""
    ): Result<PaginatedReadingPassages> = safeCall {
        val q = buildString {
            append("?page=").append(page)
            append("&limit=").append(limit)
            if (level.isNotBlank() && level != "ALL") append("&level=").append(urlEnc(level))
            if (categoryId.isNotBlank()) append("&categoryId=").append(urlEnc(categoryId))
            if (search.isNotBlank()) append("&search=").append(urlEnc(search))
        }
        val data = requestJson("GET", "/api/v1/reading/passages$q")
        PaginatedReadingPassages(
            items = parsePassageList(data.optJSONArray("items")),
            page = data.optInt("page", page),
            limit = data.optInt("limit", limit),
            total = data.optInt("total", 0),
            totalPages = data.optInt("totalPages", 1)
        )
    }

    suspend fun fetchPassageDetail(passageId: String): Result<ReadingPassageBundle> = safeCall {
        val data = requestJson("GET", "/api/v1/reading/passages/$passageId")
        parsePassageBundle(data)
    }

    suspend fun saveProgress(
        passageId: String,
        lastSentenceIndex: Int,
        lastSentenceId: String,
        readSentenceIds: List<String>,
        elapsedDeltaSeconds: Int,
        clientSequence: Int,
        markCompleted: Boolean
    ): Result<ReadingUserProgress> = safeCall {
        val payload = JSONObject()
            .put("lastSentenceIndex", lastSentenceIndex)
            .put("lastSentenceId", lastSentenceId)
            .put("readSentenceIds", JSONArray(readSentenceIds))
            .put("elapsedDeltaSeconds", elapsedDeltaSeconds)
            .put("clientSequence", clientSequence)
            .put("markCompleted", markCompleted)
        val data = requestJson("POST", "/api/v1/reading/passages/$passageId/progress", payload)
        parseUserProgress(data)
    }

    suspend fun submitComprehensionAttempt(
        passageId: String,
        answers: Map<String, String>
    ): Result<ReadingAttemptRecord> = safeCall {
        val arr = JSONArray()
        answers.forEach { (qid, ans) ->
            arr.put(JSONObject().put("questionId", qid).put("answer", ans))
        }
        val payload = JSONObject()
            .put("mode", "comprehension")
            .put("answers", arr)
        val data = requestJson("POST", "/api/v1/reading/passages/$passageId/attempt", payload)
        parseAttempt(data.getJSONObject("attempt"))
    }

    suspend fun submitReadAloudAttempt(
        passageId: String,
        matchedWords: Int,
        missingWords: Int,
        extraWords: Int,
        mismatchedWords: Int
    ): Result<ReadingAttemptRecord> = safeCall {
        val payload = JSONObject()
            .put("mode", "read_aloud")
            .put("matchedWords", matchedWords)
            .put("missingWords", missingWords)
            .put("extraWords", extraWords)
            .put("mismatchedWords", mismatchedWords)
        val data = requestJson("POST", "/api/v1/reading/passages/$passageId/attempt", payload)
        parseAttempt(data.getJSONObject("attempt"))
    }

    suspend fun lookupWord(word: String, passageId: String?): Result<ReadingWordLookup> = safeCall {
        val q = buildString {
            append("?word=").append(urlEnc(word))
            if (!passageId.isNullOrBlank()) append("&passageId=").append(urlEnc(passageId))
        }
        val data = requestJson("GET", "/api/v1/reading/word-lookup$q")
        ReadingWordLookup(
            word = data.optString("word", word),
            found = data.optBoolean("found", false),
            meaningBn = data.optStringOrNull("meaningBn"),
            pronunciation = data.optString("pronunciation"),
            partOfSpeech = data.optString("partOfSpeech"),
            example = data.optString("example"),
            existingVocabularyId = data.optStringOrNull("existingVocabularyId"),
            inUserVocabulary = data.optBoolean("inUserVocabulary", false)
        )
    }

    suspend fun saveWordToVocabulary(
        word: String,
        passageId: String?,
        meaningBn: String?,
        pronunciation: String = "",
        partOfSpeech: String = "",
        example: String = "",
        level: String = "A1"
    ): Result<ReadingSaveWordResult> = safeCall {
        val payload = JSONObject()
            .put("word", word)
            .put("passageId", passageId ?: "")
            .put("meaningBn", meaningBn ?: "")
            .put("pronunciation", pronunciation)
            .put("partOfSpeech", partOfSpeech)
            .put("example", example)
            .put("level", level)
        val data = requestJson("POST", "/api/v1/reading/vocabulary/save", payload)
        ReadingSaveWordResult(
            saved = data.optBoolean("saved", true),
            alreadyExisted = data.optBoolean("alreadyExisted", false),
            vocabularyId = data.optString("vocabularyId"),
            word = data.optString("word", word)
        )
    }

    suspend fun fetchSettings(): Result<ReadingSettings> = safeCall {
        val data = requestJson("GET", "/api/v1/reading/settings")
        parseSettings(data.optJSONObject("settings"))
    }

    suspend fun updateSettings(settings: ReadingSettings): Result<ReadingSettings> = safeCall {
        val payload = JSONObject()
            .put("preferredLevel", settings.preferredLevel)
            .put("accent", settings.accent)
            .put("voiceName", settings.voiceName)
            .put("defaultSpeed", settings.defaultSpeed.toDouble())
            .put("showBengali", settings.showBengali)
            .put("dailyGoalMinutes", settings.dailyGoalMinutes)
            .put("reminderEnabled", settings.reminderEnabled)
            .put("reminderTime", settings.reminderTime)
        val data = requestJson("PUT", "/api/v1/reading/settings", payload)
        parseSettings(data.optJSONObject("settings"))
    }

    suspend fun updateVoicePreferences(accent: String, voiceName: String): Result<ReadingSettings> = safeCall {
        val payload = JSONObject()
            .put("accent", accent)
            .put("voiceName", voiceName)
        val data = requestJson("PUT", "/api/v1/reading/settings", payload)
        parseSettings(data.optJSONObject("settings"))
    }

    // ---------- ADMIN API ----------

    suspend fun adminFetchCategories(): Result<List<ReadingCategory>> = safeCall {
        val data = requestJson("GET", "/api/v1/admin/reading/categories", useAdminClient = true)
        parseCategoryList(data.optJSONArray("categories"))
    }

    suspend fun adminSaveCategory(
        id: String?,
        name: String,
        description: String,
        isActive: Boolean
    ): Result<ReadingCategory> = safeCall {
        val payload = JSONObject()
            .put("name", name)
            .put("description", description)
            .put("isActive", isActive)
        val data = if (id.isNullOrBlank()) {
            requestJson("POST", "/api/v1/admin/reading/categories", payload, useAdminClient = true)
        } else {
            requestJson("PUT", "/api/v1/admin/reading/categories/$id", payload, useAdminClient = true)
        }
        parseCategory(data.getJSONObject("category"))
    }

    suspend fun adminDeleteCategory(id: String): Result<Unit> = safeCall {
        requestJson("DELETE", "/api/v1/admin/reading/categories/$id", useAdminClient = true)
        Unit
    }

    suspend fun adminFetchPassages(
        page: Int = 1,
        level: String = "ALL",
        categoryId: String = "",
        published: String = "all",
        search: String = ""
    ): Result<PaginatedReadingPassages> = safeCall {
        val q = buildString {
            append("?page=").append(page)
            append("&limit=20")
            if (level.isNotBlank() && level != "ALL") append("&level=").append(urlEnc(level))
            if (categoryId.isNotBlank()) append("&categoryId=").append(urlEnc(categoryId))
            if (published != "all") append("&published=").append(urlEnc(published))
            if (search.isNotBlank()) append("&search=").append(urlEnc(search))
        }
        val data = requestJson("GET", "/api/v1/admin/reading/passages$q", useAdminClient = true)
        PaginatedReadingPassages(
            items = parsePassageList(data.optJSONArray("items")),
            page = data.optInt("page", page),
            limit = data.optInt("limit", 20),
            total = data.optInt("total", 0),
            totalPages = data.optInt("totalPages", 1)
        )
    }

    suspend fun adminFetchPassageDetail(id: String): Result<ReadingPassageBundle> = safeCall {
        val data = requestJson("GET", "/api/v1/admin/reading/passages/$id", useAdminClient = true)
        parsePassageBundle(data)
    }

    suspend fun adminSavePassage(
        id: String?,
        detail: ReadingPassageDetail
    ): Result<ReadingPassageBundle> = safeCall {
        val payload = JSONObject()
            .put("title", detail.summary.title)
            .put("level", detail.summary.level)
            .put("categoryId", detail.summary.categoryId)
            .put("summary", detail.summary.summary)
            .put("englishText", detail.englishText)
            .put("bengaliTranslation", detail.bengaliTranslation)
            .put("audioUrl", detail.audioUrl.orEmpty())
            .put("estimatedMinutes", detail.summary.estimatedMinutes)
            .put("isPublished", detail.summary.isPublished)
            .put("vocabulary", JSONArray().apply {
                detail.vocabulary.forEach { v ->
                    put(
                        JSONObject()
                            .put("word", v.word)
                            .put("meaningBn", v.meaningBn)
                            .put("pronunciation", v.pronunciation)
                            .put("partOfSpeech", v.partOfSpeech)
                            .put("example", v.example)
                    )
                }
            })
            .put("questions", JSONArray().apply {
                detail.questions.forEach { q ->
                    put(
                        JSONObject()
                            .put("questionId", q.questionId)
                            .put("type", q.type.wire)
                            .put("prompt", q.prompt)
                            .put("options", JSONArray(q.options))
                            .put("correctAnswer", q.correctAnswer)
                            .put("explanationBn", q.explanationBn)
                    )
                }
            })

        if (detail.sentences.isNotEmpty()) {
            payload.put("sentences", JSONArray().apply {
                detail.sentences.forEachIndexed { idx, s ->
                    put(
                        JSONObject()
                            .put("sentenceId", s.sentenceId)
                            .put("order", idx)
                            .put("en", s.en)
                            .put("bn", s.bn)
                    )
                }
            })
        }

        val data = if (id.isNullOrBlank()) {
            requestJson("POST", "/api/v1/admin/reading/passages", payload, useAdminClient = true)
        } else {
            requestJson("PUT", "/api/v1/admin/reading/passages/$id", payload, useAdminClient = true)
        }
        parsePassageBundle(data)
    }

    suspend fun adminDeletePassage(id: String): Result<Unit> = safeCall {
        requestJson("DELETE", "/api/v1/admin/reading/passages/$id", useAdminClient = true)
        Unit
    }

    // ---------- PARSING & NETWORK ----------

    private fun parsePassageBundle(data: JSONObject): ReadingPassageBundle {
        val pObj = data.getJSONObject("passage")
        val summary = parsePassageSummary(pObj)
        val sArr = pObj.optJSONArray("sentences") ?: JSONArray()
        val vArr = pObj.optJSONArray("vocabulary") ?: JSONArray()
        val qArr = pObj.optJSONArray("questions") ?: JSONArray()

        val detail = ReadingPassageDetail(
            summary = summary,
            englishText = pObj.optString("englishText"),
            bengaliTranslation = pObj.optString("bengaliTranslation"),
            audioUrl = pObj.optStringOrNull("audioUrl"),
            sentences = List(sArr.length()) { i ->
                val s = sArr.getJSONObject(i)
                ReadingSentence(
                    sentenceId = s.optString("sentenceId", "s_$i"),
                    order = s.optInt("order", i),
                    en = s.optString("en"),
                    bn = s.optString("bn")
                )
            },
            vocabulary = List(vArr.length()) { i ->
                val v = vArr.getJSONObject(i)
                ReadingVocabItem(
                    word = v.optString("word"),
                    meaningBn = v.optString("meaningBn"),
                    pronunciation = v.optString("pronunciation"),
                    partOfSpeech = v.optString("partOfSpeech"),
                    example = v.optString("example")
                )
            },
            questions = List(qArr.length()) { i ->
                val q = qArr.getJSONObject(i)
                val optArr = q.optJSONArray("options") ?: JSONArray()
                ReadingQuestion(
                    questionId = q.optString("questionId", "q_$i"),
                    type = ReadingQuestionType.fromWire(q.optString("type", "mcq")),
                    prompt = q.optString("prompt"),
                    options = List(optArr.length()) { idx -> optArr.optString(idx) },
                    correctAnswer = q.optString("correctAnswer"),
                    explanationBn = q.optString("explanationBn")
                )
            }
        )

        return ReadingPassageBundle(
            passage = detail,
            progress = data.optJSONObject("progress")?.let(::parseUserProgress),
            attempts = parseAttemptList(data.optJSONArray("attempts"))
        )
    }

    private fun parsePassageList(arr: JSONArray?): List<ReadingPassageSummary> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i -> parsePassageSummary(arr.getJSONObject(i)) }
    }

    private fun parsePassageSummary(o: JSONObject): ReadingPassageSummary = ReadingPassageSummary(
        id = o.optString("id"),
        title = o.optString("title"),
        slug = o.optString("slug"),
        level = o.optString("level", "A1"),
        categoryId = o.optString("categoryId"),
        categoryName = o.optString("categoryName"),
        summary = o.optString("summary"),
        estimatedMinutes = o.optInt("estimatedMinutes", 3),
        sentenceCount = o.optInt("sentenceCount", 0),
        vocabCount = o.optInt("vocabCount", 0),
        questionCount = o.optInt("questionCount", 0),
        hasAudioUrl = o.optBoolean("hasAudioUrl", false),
        isPublished = o.optBoolean("isPublished", true),
        status = o.optString("status", "not_started"),
        lastSentenceIndex = o.optInt("lastSentenceIndex", 0),
        progressPercent = o.optInt("progressPercent", 0),
        totalSeconds = o.optInt("totalSeconds", 0),
        lastReadAt = o.optStringOrNull("lastReadAt"),
        completedAt = o.optStringOrNull("completedAt")
    )

    private fun parseCategoryList(arr: JSONArray?): List<ReadingCategory> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i -> parseCategory(arr.getJSONObject(i)) }
    }

    private fun parseCategory(c: JSONObject): ReadingCategory = ReadingCategory(
        id = c.optString("id"),
        name = c.optString("name"),
        slug = c.optString("slug"),
        description = c.optString("description"),
        icon = c.optString("icon", "menu_book"),
        isActive = c.optBoolean("isActive", true),
        passageCount = c.optInt("passageCount", 0)
    )

    private fun parseUserProgress(p: JSONObject): ReadingUserProgress {
        val idsArr = p.optJSONArray("readSentenceIds") ?: JSONArray()
        return ReadingUserProgress(
            status = p.optString("status", "not_started"),
            lastSentenceIndex = p.optInt("lastSentenceIndex", 0),
            lastSentenceId = p.optString("lastSentenceId"),
            readSentenceIds = List(idsArr.length()) { i -> idsArr.optString(i) },
            progressPercent = p.optInt("progressPercent", 0),
            totalSeconds = p.optInt("totalSeconds", 0)
        )
    }

    private fun parseAttemptList(arr: JSONArray?): List<ReadingAttemptRecord> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i -> parseAttempt(arr.getJSONObject(i)) }
    }

    private fun parseAttempt(a: JSONObject): ReadingAttemptRecord {
        val ansArr = a.optJSONArray("answers") ?: JSONArray()
        val tmObj = a.optJSONObject("textMatchingSummary")
        return ReadingAttemptRecord(
            id = a.optString("id"),
            passageId = a.optString("passageId"),
            passageTitle = a.optString("passageTitle"),
            level = a.optString("level", "A1"),
            mode = a.optString("mode", "comprehension"),
            scorePercent = a.optInt("scorePercent", 0),
            correctCount = a.optInt("correctCount", 0),
            totalCount = a.optInt("totalCount", 0),
            answers = List(ansArr.length()) { i ->
                val ans = ansArr.getJSONObject(i)
                ReadingAttemptAnswerResult(
                    questionId = ans.optString("questionId"),
                    userAnswer = ans.optString("userAnswer"),
                    isCorrect = ans.optBoolean("isCorrect", false),
                    correctAnswer = ans.optString("correctAnswer"),
                    explanationBn = ans.optString("explanationBn")
                )
            },
            textMatchingSummary = tmObj?.let {
                TextMatchingSummary(
                    matchedWords = it.optInt("matchedWords", 0),
                    missingWords = it.optInt("missingWords", 0),
                    extraWords = it.optInt("extraWords", 0),
                    mismatchedWords = it.optInt("mismatchedWords", 0)
                )
            },
            createdAt = a.optString("createdAt")
        )
    }

    private fun parseSettings(s: JSONObject?): ReadingSettings {
        if (s == null) return ReadingSettings()
        return ReadingSettings(
            preferredLevel = s.optString("preferredLevel", "ALL"),
            accent = s.optString("accent", "en-US"),
            voiceName = s.optString("voiceName", ""),
            defaultSpeed = s.optDouble("defaultSpeed", 1.0).toFloat(),
            showBengali = s.optBoolean("showBengali", true),
            dailyGoalMinutes = s.optInt("dailyGoalMinutes", 15),
            reminderEnabled = s.optBoolean("reminderEnabled", false),
            reminderTime = s.optString("reminderTime", "20:00"),
            timezone = s.optString("timezone", "Asia/Dhaka")
        )
    }

    private suspend inline fun <T> safeCall(crossinline block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun requestJson(
        method: String,
        path: String,
        body: JSONObject? = null,
        useAdminClient: Boolean = false
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
            ?: if (method == "POST" || method == "PUT") "{}".toRequestBody(JSON_TYPE) else null
        builder.method(method, reqBody)

        val client = if (useAdminClient) NetworkClient.admin else NetworkClient.standard
        client.newCall(builder.build()).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!res.isSuccessful || json?.optBoolean("success", true) == false) {
                val errObj = json?.optJSONObject("error")
                val code = errObj?.optString("code")?.takeIf { it.isNotBlank() }
                val message = errObj?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: json?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: when {
                        res.code == 404 && raw.trimStart().startsWith("<") ->
                            "Reading API not found on server (404). Deploy latest backend to Vercel."
                        raw.trimStart().startsWith("<") -> "Server error (${res.code})"
                        else -> "Request failed (${res.code})"
                    }
                throw BackendApiException(message, code, res.code)
            }
            return json?.optJSONObject("data") ?: json ?: JSONObject()
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }

    private fun urlEnc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
