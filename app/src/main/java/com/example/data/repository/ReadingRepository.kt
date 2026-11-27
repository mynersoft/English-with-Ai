package com.example.data.repository

import com.example.core.network.NetworkClient
import com.example.core.network.sanitizeForBackendFailure
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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class ReadingRepository {

    suspend fun fetchDashboard(forceRefresh: Boolean = false): Result<ReadingDashboardData> = safeCall {
        val uid = Firebase.auth.currentUser?.uid.orEmpty()
        if (!forceRefresh) {
            getCachedDashboard(uid)?.let { return@safeCall it }
        }
        val data = requestJson("GET", "/api/v1/reading/dashboard")
        val statsObj = data.optJSONObject("stats") ?: JSONObject()
        val weekArr = data.optJSONArray("weeklyActivity") ?: JSONArray()
        val catsArr = data.optJSONArray("categories") ?: JSONArray()

        val dashboard = ReadingDashboardData(
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
        putCachedDashboard(uid, dashboard)
        dashboard
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
        invalidateDashboardCache()
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
        invalidateDashboardCache()
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
        invalidateDashboardCache()
        parseAttempt(data.getJSONObject("attempt"))
    }

    suspend fun lookupWord(word: String, passageId: String?): Result<ReadingWordLookup> = safeCall {
        val cacheKey = "${passageId.orEmpty()}:${word.trim().lowercase()}"
        getCachedWordLookup(cacheKey)?.let { return@safeCall it }

        val candidates = WordLookupCandidates.build(word)
        val primary = candidates.firstOrNull() ?: word.trim()
        val q = buildString {
            append("?word=").append(urlEnc(primary))
            if (!passageId.isNullOrBlank()) append("&passageId=").append(urlEnc(passageId))
        }
        var lastError: Throwable? = null
        val direct = try {
            val data = requestJson("GET", "/api/v1/reading/word-lookup$q")
            ReadingWordLookup(
                word = data.optString("word", primary),
                found = data.optBoolean("found", false),
                meaningBn = data.optStringOrNull("meaningBn"),
                pronunciation = data.optString("pronunciation"),
                partOfSpeech = data.optString("partOfSpeech"),
                example = data.optString("example"),
                existingVocabularyId = data.optStringOrNull("existingVocabularyId"),
                inUserVocabulary = data.optBoolean("inUserVocabulary", false)
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            lastError = t
            null
        }

        if (direct != null && direct.found && !direct.meaningBn.isNullOrBlank()) {
            putCachedWordLookup(cacheKey, direct)
            return@safeCall direct
        }

        // Fall back to /api/vocabulary/words across exact & primary base candidate.
        val vocabHit = lookupFromVocabularyCatalog(candidates)
        if (vocabHit != null) {
            putCachedWordLookup(cacheKey, vocabHit)
            return@safeCall vocabHit
        }

        if (direct != null) {
            putCachedWordLookup(cacheKey, direct)
            return@safeCall direct
        }
        throw lastError ?: BackendApiException("Could not look up word.", "LOOKUP_FAILED", 500)
    }

    private suspend fun lookupFromVocabularyCatalog(candidates: List<String>): ReadingWordLookup? {
        for (candidate in candidates.take(2)) {
            val res = runCatching {
                requestJson("GET", "/api/vocabulary/words?search=${urlEnc(candidate)}&limit=15")
            }.getOrNull() ?: continue
            val items = res.optJSONArray("items") ?: continue
            var bestObj: JSONObject? = null
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val w = obj.optString("word").trim().lowercase()
                val meaning = obj.optString("meaning").trim()
                if (meaning.isBlank()) continue
                if (candidates.any { it == w }) {
                    bestObj = obj
                    break
                }
                if (bestObj == null && w.startsWith(candidate)) {
                    bestObj = obj
                }
            }
            if (bestObj != null) {
                val meaning = bestObj.optString("meaning").trim()
                val examples = bestObj.optJSONArray("examples")
                val firstExampleEn = examples?.optJSONObject(0)?.optString("en")?.trim().orEmpty()
                return ReadingWordLookup(
                    word = bestObj.optString("word").ifBlank { candidate },
                    found = meaning.isNotBlank(),
                    meaningBn = meaning.ifBlank { null },
                    pronunciation = bestObj.optString("pronunciation"),
                    partOfSpeech = bestObj.optString("partOfSpeech"),
                    example = firstExampleEn,
                    existingVocabularyId = bestObj.optStringOrNull("id"),
                    inUserVocabulary = !bestObj.isNull("progress") && bestObj.optJSONObject("progress") != null
                )
            }
        }
        return null
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
        invalidateWordLookup("${passageId.orEmpty()}:${word.trim().lowercase()}")
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
        invalidateDashboardCache()
        parseSettings(data.optJSONObject("settings"))
    }

    suspend fun updateVoicePreferences(accent: String, voiceName: String): Result<ReadingSettings> = safeCall {
        val payload = JSONObject()
            .put("accent", accent)
            .put("voiceName", voiceName)
        val data = requestJson("PUT", "/api/v1/reading/settings", payload)
        invalidateDashboardCache()
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
                Result.failure(e.sanitizeForBackendFailure())
            }
        }

    private suspend fun requestJson(
        method: String,
        path: String,
        body: JSONObject? = null,
        useAdminClient: Boolean = false
    ): JSONObject = NetworkClient.executeAuthorizedJson(
        method = method,
        path = path,
        body = body,
        client = if (useAdminClient) NetworkClient.admin else NetworkClient.standard,
        notFoundHint = "Reading API not found on server (404). Deploy latest backend to Vercel."
    )

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }

    private fun urlEnc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        private const val DASHBOARD_TTL_MS = 45_000L
        private const val LOOKUP_TTL_MS = 600_000L
        private val cacheLock = Any()

        private var cachedDashboard: ReadingDashboardData? = null
        private var cachedDashboardUid: String = ""
        private var cachedDashboardAtMs: Long = 0L

        private data class CachedLookupEntry(val result: ReadingWordLookup, val savedAtMs: Long)

        private val wordLookupCache =
            object : LinkedHashMap<String, CachedLookupEntry>(64, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedLookupEntry>?): Boolean =
                    size > 160
            }

        fun getCachedDashboard(uid: String): ReadingDashboardData? = synchronized(cacheLock) {
            val data = cachedDashboard ?: return null
            if (uid != cachedDashboardUid || System.currentTimeMillis() - cachedDashboardAtMs > DASHBOARD_TTL_MS) {
                cachedDashboard = null
                return null
            }
            return data
        }

        fun putCachedDashboard(uid: String, data: ReadingDashboardData) = synchronized(cacheLock) {
            cachedDashboard = data
            cachedDashboardUid = uid
            cachedDashboardAtMs = System.currentTimeMillis()
        }

        fun invalidateDashboardCache() = synchronized(cacheLock) {
            cachedDashboard = null
            cachedDashboardAtMs = 0L
        }

        fun getCachedWordLookup(key: String): ReadingWordLookup? = synchronized(cacheLock) {
            val entry = wordLookupCache[key] ?: return null
            if (System.currentTimeMillis() - entry.savedAtMs > LOOKUP_TTL_MS) {
                wordLookupCache.remove(key)
                return null
            }
            return entry.result
        }

        fun putCachedWordLookup(key: String, result: ReadingWordLookup) = synchronized(cacheLock) {
            wordLookupCache[key] = CachedLookupEntry(result, System.currentTimeMillis())
        }

        fun invalidateWordLookup(key: String) = synchronized(cacheLock) {
            wordLookupCache.remove(key)
        }
    }
}

internal object WordLookupCandidates {
    private val irregulars = mapOf(
        "am" to "be",
        "is" to "be",
        "are" to "be",
        "was" to "be",
        "were" to "be",
        "been" to "be",
        "has" to "have",
        "had" to "have",
        "does" to "do",
        "did" to "do",
        "done" to "do",
        "went" to "go",
        "gone" to "go",
        "goes" to "go",
        "ate" to "eat",
        "eaten" to "eat",
        "drank" to "drink",
        "drunk" to "drink",
        "slept" to "sleep",
        "woke" to "wake",
        "wrote" to "write",
        "written" to "write",
        "spoke" to "speak",
        "spoken" to "speak",
        "took" to "take",
        "taken" to "take",
        "made" to "make",
        "came" to "come",
        "saw" to "see",
        "seen" to "see",
        "gave" to "give",
        "given" to "give",
        "got" to "get",
        "ran" to "run",
        "sat" to "sit",
        "stood" to "stand",
        "said" to "say",
        "told" to "tell",
        "taught" to "teach",
        "bought" to "buy",
        "brought" to "bring",
        "thought" to "think",
        "felt" to "feel",
        "found" to "find",
        "kept" to "keep",
        "left" to "leave",
        "met" to "meet",
        "paid" to "pay",
        "children" to "child",
        "men" to "man",
        "women" to "woman",
        "teeth" to "tooth",
        "feet" to "foot",
        "people" to "person",
        "better" to "good",
        "best" to "good",
        "worse" to "bad",
        "worst" to "bad"
    )

    fun build(rawWord: String): List<String> {
        val cleaned = rawWord
            .trim()
            .replace(Regex("^[^a-zA-Z]+|[^a-zA-Z'’-]+$"), "")
            .replace('’', '\'')
            .replace('‘', '\'')
            .lowercase()
        if (cleaned.isBlank()) return emptyList()

        val result = linkedSetOf<String>()
        fun add(w: String) {
            val s = w.trim().lowercase()
            if (s.isNotEmpty() && s.matches(Regex("[a-z][a-z'-]*"))) {
                result.add(s)
            }
        }

        add(cleaned)
        val noPossessive = cleaned
            .removeSuffix("'s")
            .let { if (it.endsWith("s'")) it.dropLast(1) else it }
            .trimEnd('\'')
        add(noPossessive)

        val base = noPossessive.ifBlank { cleaned }
        irregulars[base]?.let(::add)

        if (base.length > 4 && base.endsWith("ies")) add(base.dropLast(3) + "y")
        if (base.length > 4 && base.endsWith("ves")) {
            add(base.dropLast(3) + "f")
            add(base.dropLast(3) + "fe")
        }
        if (base.length > 3 && base.endsWith("es")) {
            if (base.endsWith("ches") || base.endsWith("shes") || base.endsWith("xes") ||
                base.endsWith("zes") || base.endsWith("sses") || base.endsWith("oes")
            ) {
                add(base.dropLast(2))
            }
            add(base.dropLast(1))
        }
        if (base.length > 2 && base.endsWith("s") && !base.endsWith("ss")) {
            add(base.dropLast(1))
        }
        if (base.length > 4 && base.endsWith("ied")) add(base.dropLast(3) + "y")
        if (base.length > 3 && base.endsWith("ed")) {
            val stem = base.dropLast(2)
            add(stem)
            add(stem + "e")
            if (stem.length >= 3 && stem.last() == stem[stem.length - 2]) add(stem.dropLast(1))
        }
        if (base.length > 4 && base.endsWith("ing")) {
            if (base.endsWith("ying")) add(base.dropLast(4) + "ie")
            val stem = base.dropLast(3)
            add(stem)
            add(stem + "e")
            if (stem.length >= 3 && stem.last() == stem[stem.length - 2]) add(stem.dropLast(1))
        }
        if (base.length > 4 && base.endsWith("ier")) add(base.dropLast(3) + "y")
        if (base.length > 5 && base.endsWith("iest")) add(base.dropLast(4) + "y")
        if (base.length > 3 && base.endsWith("er")) {
            val stem = base.dropLast(2)
            add(stem)
            add(stem + "e")
        }
        if (base.length > 4 && base.endsWith("est")) {
            val stem = base.dropLast(3)
            add(stem)
            add(stem + "e")
        }
        if (base.length > 4 && base.endsWith("ily")) add(base.dropLast(3) + "y")
        if (base.length > 3 && base.endsWith("ly")) {
            add(base.dropLast(2))
            add(base.dropLast(2) + "le")
        }
        return result.toList()
    }
}

