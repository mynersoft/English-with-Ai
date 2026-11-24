package com.example.data.repository

import android.content.Context
import com.example.BuildConfig
import com.example.core.network.NetworkClient
import com.example.domain.model.CsvImportSummary
import com.example.domain.model.DailyWordsData
import com.example.domain.model.PaginatedWordsData
import com.example.domain.model.QuizMatchingLeft
import com.example.domain.model.QuizMatchingRight
import com.example.domain.model.QuizQuestionItem
import com.example.domain.model.QuizResultData
import com.example.domain.model.QuizResultRow
import com.example.domain.model.QuizSessionData
import com.example.domain.model.QuizSubmissionAnswer
import com.example.domain.model.ReviewCounts
import com.example.domain.model.ReviewTabData
import com.example.domain.model.SentenceCheckResult
import com.example.domain.model.VocabCategorySummary
import com.example.domain.model.VocabDashboardData
import com.example.domain.model.VocabExample
import com.example.domain.model.VocabProgressAnalytics
import com.example.domain.model.VocabRecentActivity
import com.example.domain.model.VocabSettings
import com.example.domain.model.VocabSpeakingOverview
import com.example.domain.model.VocabSpeakingSession
import com.example.domain.model.VocabWord
import com.example.domain.model.VocabWordProgress
import com.example.domain.model.WeeklyActivityPoint
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class BackendApiException(
    message: String,
    val code: String? = null,
    val httpStatus: Int = 500
) : IOException(message)

class VocabularyRepository(context: Context? = null) {
    private val prefs = context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun fetchDashboard(): Result<VocabDashboardData> = safeCall {
        val data = requestJson("GET", "/api/vocabulary/dashboard")
        cacheDashboardJson(data.toString())
        parseDashboard(data, fromCache = false)
    }

    fun getCachedDashboard(): VocabDashboardData? {
        val raw = prefs?.getString(KEY_DASHBOARD_CACHE, null) ?: return null
        return runCatching { parseDashboard(JSONObject(raw), fromCache = true) }.getOrNull()
    }

    suspend fun fetchCategories(): Result<List<VocabCategorySummary>> = safeCall {
        val data = requestJson("GET", "/api/vocabulary/categories")
        parseCategoryArray(data.optJSONArray("categories"))
    }

    suspend fun fetchWords(
        page: Int = 1,
        limit: Int = 20,
        level: String = "ALL",
        categoryId: String = "",
        search: String = ""
    ): Result<PaginatedWordsData> = safeCall {
        val q = buildString {
            append("?page=").append(page)
            append("&limit=").append(limit)
            if (level.isNotBlank() && level != "ALL") append("&level=").append(urlEnc(level))
            if (categoryId.isNotBlank()) append("&categoryId=").append(urlEnc(categoryId))
            if (search.isNotBlank()) append("&search=").append(urlEnc(search))
        }
        val data = requestJson("GET", "/api/vocabulary/words$q")
        PaginatedWordsData(
            items = parseWordArray(data.optJSONArray("items")),
            page = data.optInt("page", page),
            limit = data.optInt("limit", limit),
            total = data.optInt("total", 0),
            totalPages = data.optInt("totalPages", 1)
        )
    }

    suspend fun fetchWordDetail(wordId: String): Result<VocabWord> = safeCall {
        val data = requestJson("GET", "/api/vocabulary/words/$wordId")
        parseWord(data.getJSONObject("word"))
    }

    suspend fun fetchDailyWords(): Result<DailyWordsData> = safeCall {
        val data = requestJson("GET", "/api/vocabulary/daily")
        DailyWordsData(
            date = data.optString("date"),
            timezone = data.optString("timezone", "Asia/Dhaka"),
            dailyGoal = data.optInt("dailyGoal", 5),
            newWordsDone = data.optInt("newWordsDone", 0),
            isGoalCompleted = data.optBoolean("isGoalCompleted", false),
            words = parseWordArray(data.optJSONArray("words"))
        )
    }

    suspend fun recordLearned(vocabularyId: String, knowsWord: Boolean): Result<DailyWordsData> = safeCall {
        val payload = JSONObject()
            .put("vocabularyId", vocabularyId)
            .put("knowsWord", knowsWord)
        val data = requestJson("POST", "/api/vocabulary/learn", payload)
        val daily = data.optJSONObject("daily") ?: JSONObject()
        DailyWordsData(
            date = daily.optString("date"),
            dailyGoal = daily.optInt("dailyGoal", 5),
            newWordsDone = daily.optInt("newWordsDone", 0),
            isGoalCompleted = daily.optBoolean("isGoalCompleted", false)
        )
    }

    suspend fun submitReview(vocabularyId: String, quality: Int): Result<VocabWordProgress> = safeCall {
        val payload = JSONObject()
            .put("vocabularyId", vocabularyId)
            .put("quality", quality)
        val data = requestJson("POST", "/api/vocabulary/review", payload)
        parseProgress(data.optJSONObject("progress"))
    }

    suspend fun fetchReviewTab(tab: String = "due", page: Int = 1): Result<ReviewTabData> = safeCall {
        val data = requestJson("GET", "/api/vocabulary/review/due?tab=${urlEnc(tab)}&page=$page&limit=30")
        val countsObj = data.optJSONObject("counts") ?: JSONObject()
        ReviewTabData(
            tab = data.optString("tab", tab),
            counts = ReviewCounts(
                due = countsObj.optInt("due", 0),
                difficult = countsObj.optInt("difficult", 0),
                learning = countsObj.optInt("learning", 0),
                mastered = countsObj.optInt("mastered", 0)
            ),
            items = parseWordArray(data.optJSONArray("items"))
        )
    }

    suspend fun toggleFavorite(vocabularyId: String, isFavorite: Boolean? = null): Result<Boolean> = safeCall {
        val payload = JSONObject().put("vocabularyId", vocabularyId)
        if (isFavorite != null) payload.put("isFavorite", isFavorite)
        val data = requestJson("POST", "/api/vocabulary/favorites", payload)
        data.optBoolean("isFavorite", false)
    }

    suspend fun fetchFavorites(): Result<List<VocabWord>> = safeCall {
        val data = requestJson("GET", "/api/vocabulary/favorites?limit=50")
        parseWordArray(data.optJSONArray("items"))
    }

    suspend fun generateQuiz(
        quizType: String = "mixed",
        count: Int = 5,
        level: String = "ALL",
        categoryId: String = ""
    ): Result<QuizSessionData> = safeCall {
        val q = buildString {
            append("?type=").append(urlEnc(quizType))
            append("&count=").append(count)
            if (level.isNotBlank() && level != "ALL") append("&level=").append(urlEnc(level))
            if (categoryId.isNotBlank()) append("&categoryId=").append(urlEnc(categoryId))
        }
        val data = requestJson("GET", "/api/vocabulary/quiz$q")
        val qArr = data.optJSONArray("questions") ?: JSONArray()
        val questions = List(qArr.length()) { i ->
            val o = qArr.getJSONObject(i)
            val opts = o.optJSONArray("options").toStringList()
            val leftArr = o.optJSONArray("leftItems") ?: JSONArray()
            val rightArr = o.optJSONArray("rightItems") ?: JSONArray()
            QuizQuestionItem(
                vocabularyId = o.optString("vocabularyId"),
                questionType = o.optString("questionType"),
                prompt = o.optString("prompt"),
                hint = o.optString("hint"),
                audioUrl = o.optStringOrNull("audioUrl"),
                options = opts,
                leftItems = List(leftArr.length()) { j ->
                    val l = leftArr.getJSONObject(j)
                    QuizMatchingLeft(
                        vocabularyId = l.optString("vocabularyId"),
                        word = l.optString("word"),
                        pronunciation = l.optString("pronunciation")
                    )
                },
                rightItems = List(rightArr.length()) { j ->
                    val r = rightArr.getJSONObject(j)
                    QuizMatchingRight(
                        optionId = r.optString("optionId"),
                        meaning = r.optString("meaning")
                    )
                }
            )
        }
        QuizSessionData(quizType = data.optString("quizType", quizType), questions = questions)
    }

    suspend fun submitQuiz(
        quizType: String,
        timeTakenSeconds: Int,
        answers: List<QuizSubmissionAnswer>
    ): Result<QuizResultData> = safeCall {
        val arr = JSONArray()
        answers.forEach { a ->
            arr.put(
                JSONObject()
                    .put("vocabularyId", a.vocabularyId)
                    .put("questionType", a.questionType)
                    .put("selectedAnswer", a.selectedAnswer)
            )
        }
        val payload = JSONObject()
            .put("quizType", quizType)
            .put("timeTaken", timeTakenSeconds)
            .put("answers", arr)
        val data = requestJson("POST", "/api/vocabulary/quiz", payload)
        val resArr = data.optJSONArray("results") ?: JSONArray()
        QuizResultData(
            attemptId = data.optString("attemptId"),
            quizType = data.optString("quizType", quizType),
            totalQuestions = data.optInt("totalQuestions", answers.size),
            correctAnswers = data.optInt("correctAnswers", 0),
            wrongAnswers = data.optInt("wrongAnswers", 0),
            score = data.optInt("score", 0),
            timeTaken = data.optInt("timeTaken", timeTakenSeconds),
            results = List(resArr.length()) { i ->
                val r = resArr.getJSONObject(i)
                QuizResultRow(
                    vocabularyId = r.optString("vocabularyId"),
                    questionType = r.optString("questionType"),
                    word = r.optString("word"),
                    meaning = r.optString("meaning"),
                    selectedAnswer = r.optString("selectedAnswer"),
                    correctAnswer = r.optString("correctAnswer"),
                    isCorrect = r.optBoolean("isCorrect", false)
                )
            }
        )
    }

    suspend fun checkSentence(vocabularyId: String, sentence: String): Result<SentenceCheckResult> =
        safeCall(client = NetworkClient.speaking) {
            val payload = JSONObject()
                .put("vocabularyId", vocabularyId)
                .put("sentence", sentence)
            val data = requestJson("POST", "/api/vocabulary/sentence-check", payload, NetworkClient.speaking)
            SentenceCheckResult(
                aiConfigured = data.optBoolean("aiConfigured", false),
                isValidBasic = data.optBoolean("isValidBasic", false),
                containsWord = data.optBoolean("containsWord", false),
                isCorrect = data.optBoolean("isCorrect", false),
                correctedSentence = data.optString("correctedSentence"),
                feedbackBn = data.optString("feedbackBn")
            )
        }

    suspend fun fetchSpeakingOverview(): Result<VocabSpeakingOverview> = safeCall {
        val data = requestJson("GET", "/api/vocabulary/speaking")
        val arr = data.optJSONArray("sessions") ?: JSONArray()
        VocabSpeakingOverview(
            aiConfigured = data.optBoolean("aiConfigured", false),
            sessions = List(arr.length()) { i -> parseSpeakingSession(arr.getJSONObject(i)) }
        )
    }

    suspend fun submitSpeakingPractice(
        transcript: String,
        durationSeconds: Int,
        vocabularyIds: List<String>
    ): Result<VocabSpeakingSession> = safeCall(client = NetworkClient.speaking) {
        val payload = JSONObject()
            .put("transcript", transcript)
            .put("duration", durationSeconds)
            .put("vocabularyIds", JSONArray(vocabularyIds))
        val data = requestJson("POST", "/api/vocabulary/speaking", payload, NetworkClient.speaking)
        parseSpeakingSession(data.getJSONObject("session"))
    }

    suspend fun fetchProgressAnalytics(): Result<VocabProgressAnalytics> = safeCall {
        val data = requestJson("GET", "/api/vocabulary/progress")
        val weeklyArr = data.optJSONArray("weeklyActivity") ?: JSONArray()
        val breakdown = data.optJSONObject("statusBreakdown") ?: JSONObject()
        val quizStats = data.optJSONObject("quizStats") ?: JSONObject()
        val speakingStats = data.optJSONObject("speakingStats") ?: JSONObject()
        VocabProgressAnalytics(
            timezone = data.optString("timezone", "Asia/Dhaka"),
            streak = data.optInt("streak", 0),
            weeklyActivity = List(weeklyArr.length()) { i ->
                val w = weeklyArr.getJSONObject(i)
                WeeklyActivityPoint(
                    date = w.optString("date"),
                    newWords = w.optInt("newWords", 0),
                    reviewedWords = w.optInt("reviewedWords", 0),
                    quizCount = w.optInt("quizCount", 0),
                    speakingMinutes = w.optInt("speakingMinutes", 0),
                    isGoalCompleted = w.optBoolean("isGoalCompleted", false)
                )
            },
            newCount = breakdown.optInt("new", 0),
            learningCount = breakdown.optInt("learning", 0),
            reviewCount = breakdown.optInt("review", 0),
            masteredCount = breakdown.optInt("mastered", 0),
            totalQuizzes = quizStats.optInt("totalQuizzes", 0),
            averageQuizScore = quizStats.optInt("averageScore", 0),
            quizAccuracyPercent = quizStats.optInt("accuracyPercent", 0),
            totalSpeakingSessions = speakingStats.optInt("totalSessions", 0),
            avgGrammarScore = speakingStats.optInt("avgGrammarScore", 0),
            avgVocabularyScore = speakingStats.optInt("avgVocabularyScore", 0),
            avgFluencyScore = speakingStats.optInt("avgFluencyScore", 0)
        )
    }

    suspend fun fetchSettings(): Result<VocabSettings> = safeCall {
        val data = requestJson("GET", "/api/vocabulary/settings")
        parseSettings(data.getJSONObject("settings"))
    }

    suspend fun saveSettings(settings: VocabSettings): Result<VocabSettings> = safeCall {
        val payload = JSONObject()
            .put("dailyGoal", settings.dailyGoal)
            .put("preferredLevel", settings.preferredLevel)
            .put("preferredCategoryIds", JSONArray(settings.preferredCategoryIds))
            .put("autoPronounce", settings.autoPronounce)
            .put("reminderEnabled", settings.reminderEnabled)
            .put("reminderTime", settings.reminderTime)
            .put("timezone", settings.timezone)
        val data = requestJson("PUT", "/api/vocabulary/settings", payload)
        parseSettings(data.getJSONObject("settings"))
    }

    // ---------- Admin Vocabulary APIs ----------

    suspend fun checkIsAdmin(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
            if (baseUrl.isBlank()) return@runCatching false
            val token = idToken()
            val req = Request.Builder()
                .url("$baseUrl/api/v1/admin/me")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            NetworkClient.standard.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use false
                JSONObject(res.body?.string().orEmpty()).optBoolean("admin", false)
            }
        }.getOrDefault(false)
    }

    suspend fun adminListWords(
        page: Int = 1,
        search: String = "",
        level: String = "ALL",
        categoryId: String = "",
        published: String = "all"
    ): Result<PaginatedWordsData> = safeCall {
        val q = buildString {
            append("?page=").append(page).append("&limit=20")
            if (search.isNotBlank()) append("&search=").append(urlEnc(search))
            if (level.isNotBlank() && level != "ALL") append("&level=").append(urlEnc(level))
            if (categoryId.isNotBlank()) append("&categoryId=").append(urlEnc(categoryId))
            if (published != "all") append("&published=").append(urlEnc(published))
        }
        val data = requestJson("GET", "/api/admin/vocabulary/words$q")
        PaginatedWordsData(
            items = parseWordArray(data.optJSONArray("items")),
            page = data.optInt("page", page),
            limit = data.optInt("limit", 20),
            total = data.optInt("total", 0),
            totalPages = data.optInt("totalPages", 1)
        )
    }

    suspend fun adminSaveWord(word: VocabWord): Result<String> = safeCall {
        val exArr = JSONArray()
        word.examples.forEach { ex ->
            if (ex.en.isNotBlank() && ex.bn.isNotBlank()) {
                exArr.put(JSONObject().put("en", ex.en.trim()).put("bn", ex.bn.trim()))
            }
        }
        val payload = JSONObject()
            .put("word", word.word.trim())
            .put("meaning", word.meaning.trim())
            .put("pronunciation", word.pronunciation.trim())
            .put("audioUrl", word.audioUrl?.trim()?.takeIf { it.isNotBlank() })
            .put("imageUrl", word.imageUrl?.trim()?.takeIf { it.isNotBlank() })
            .put("partOfSpeech", word.partOfSpeech.trim())
            .put("level", word.level)
            .put("examples", exArr)
            .put("synonyms", JSONArray(word.synonyms))
            .put("antonyms", JSONArray(word.antonyms))
            .put("tags", JSONArray(word.tags))
            .put("difficulty", word.difficulty)
            .put("isPublished", word.isPublished)
            .put("sortOrder", word.sortOrder)

        if (word.categoryId.isNotBlank()) {
            payload.put("categoryId", word.categoryId)
        } else if (word.categoryName.isNotBlank()) {
            payload.put("category", word.categoryName.trim())
        }

        val data = if (word.id.isBlank()) {
            requestJson("POST", "/api/admin/vocabulary/words", payload)
        } else {
            requestJson("PUT", "/api/admin/vocabulary/words/${word.id}", payload)
        }
        data.optString("id", word.id)
    }

    suspend fun adminTogglePublish(wordId: String, isPublished: Boolean): Result<Boolean> = safeCall {
        val payload = JSONObject().put("isPublished", isPublished)
        val data = requestJson("PUT", "/api/admin/vocabulary/words/$wordId", payload)
        data.optBoolean("isPublished", isPublished)
    }

    suspend fun adminDeleteWord(wordId: String): Result<Unit> = safeCall {
        requestJson("DELETE", "/api/admin/vocabulary/words/$wordId")
        Unit
    }

    suspend fun adminListCategories(): Result<List<VocabCategorySummary>> = safeCall {
        val data = requestJson("GET", "/api/admin/vocabulary/categories")
        parseCategoryArray(data.optJSONArray("categories"))
    }

    suspend fun adminSaveCategory(
        id: String?,
        name: String,
        description: String,
        parentId: String?,
        isActive: Boolean
    ): Result<String> = safeCall {
        val payload = JSONObject()
            .put("name", name.trim())
            .put("description", description.trim())
            .put("isActive", isActive)
        if (!parentId.isNullOrBlank()) payload.put("parentId", parentId) else payload.put("parentId", JSONObject.NULL)
        val data = if (id.isNullOrBlank()) {
            requestJson("POST", "/api/admin/vocabulary/categories", payload)
        } else {
            requestJson("PUT", "/api/admin/vocabulary/categories/$id", payload)
        }
        data.optString("id", id.orEmpty())
    }

    suspend fun adminDeleteCategory(categoryId: String): Result<Unit> = safeCall {
        requestJson("DELETE", "/api/admin/vocabulary/categories/$categoryId")
        Unit
    }

    suspend fun adminImportCsv(csvText: String): Result<CsvImportSummary> = safeCall {
        val payload = JSONObject().put("csv", csvText)
        val data = requestJson("POST", "/api/admin/vocabulary/import", payload, NetworkClient.admin)
        CsvImportSummary(
            totalRows = data.optInt("totalRows", 0),
            inserted = data.optInt("inserted", 0),
            duplicates = data.optInt("duplicates", 0),
            errors = data.optJSONArray("errors").toStringList()
        )
    }

    // ---------- Helpers ----------

    private suspend fun <T> safeCall(
        client: OkHttpClient = NetworkClient.standard,
        block: suspend () -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun idToken(): String {
        val user = Firebase.auth.currentUser
            ?: throw BackendApiException("Please sign in to continue.", "UNAUTHORIZED", 401)
        return user.getIdToken(false).await().token
            ?: throw BackendApiException("Could not verify authentication token.", "UNAUTHORIZED", 401)
    }

    private suspend fun requestJson(
        method: String,
        path: String,
        body: JSONObject? = null,
        client: OkHttpClient = NetworkClient.standard
    ): JSONObject {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            throw BackendApiException("Backend URL is not configured.", "CONFIG_MISSING", 500)
        }
        val token = idToken()
        val reqBuilder = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")

        val reqBody = body?.toString()?.toRequestBody(JSON_TYPE)
            ?: if (method == "POST" || method == "PUT") "{}".toRequestBody(JSON_TYPE) else null
        reqBuilder.method(method, reqBody)

        client.newCall(reqBuilder.build()).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!res.isSuccessful || json?.optBoolean("success", true) == false) {
                val errObj = json?.optJSONObject("error")
                val code = errObj?.optString("code")?.takeIf { it.isNotBlank() }
                val message = errObj?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: json?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: if (raw.trimStart().startsWith("<")) "Server error (${res.code})" else "Request failed (${res.code})"
                throw BackendApiException(message, code, res.code)
            }
            return json?.optJSONObject("data") ?: json ?: JSONObject()
        }
    }

    private fun cacheDashboardJson(raw: String) {
        prefs?.edit()?.putString(KEY_DASHBOARD_CACHE, raw)?.apply()
    }

    private fun parseDashboard(data: JSONObject, fromCache: Boolean): VocabDashboardData {
        val actArr = data.optJSONArray("recentActivity") ?: JSONArray()
        return VocabDashboardData(
            greetingName = data.optString("greetingName", "Learner"),
            date = data.optString("date"),
            timezone = data.optString("timezone", "Asia/Dhaka"),
            dailyGoal = data.optInt("dailyGoal", 5),
            newWordsDone = data.optInt("newWordsDone", 0),
            isGoalCompleted = data.optBoolean("isGoalCompleted", false),
            reviewedToday = data.optInt("reviewedToday", 0),
            dueReviews = data.optInt("dueReviews", 0),
            totalLearned = data.optInt("totalLearned", 0),
            masteredCount = data.optInt("masteredCount", 0),
            streak = data.optInt("streak", 0),
            categories = parseCategoryArray(data.optJSONArray("categories")),
            recentActivity = List(actArr.length()) { i ->
                val a = actArr.getJSONObject(i)
                VocabRecentActivity(
                    type = a.optString("type"),
                    title = a.optString("title"),
                    subtitle = a.optString("subtitle"),
                    timestamp = a.optString("timestamp")
                )
            },
            isFromOfflineCache = fromCache
        )
    }

    private fun parseCategoryArray(arr: JSONArray?): List<VocabCategorySummary> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            VocabCategorySummary(
                id = o.optString("id"),
                name = o.optString("name"),
                slug = o.optString("slug"),
                description = o.optString("description"),
                icon = o.optString("icon", "menu_book"),
                parentId = o.optStringOrNull("parentId"),
                wordCount = o.optInt("wordCount", 0),
                totalWordCount = o.optInt("totalWordCount", o.optInt("wordCount", 0)),
                isActive = o.optBoolean("isActive", true),
                subcategories = parseCategoryArray(o.optJSONArray("subcategories"))
            )
        }
    }

    private fun parseWordArray(arr: JSONArray?): List<VocabWord> {
        if (arr == null) return emptyList()
        return List(arr.length()) { i -> parseWord(arr.getJSONObject(i)) }
    }

    private fun parseWord(o: JSONObject): VocabWord {
        val exArr = o.optJSONArray("examples") ?: JSONArray()
        return VocabWord(
            id = o.optString("id"),
            word = o.optString("word"),
            slug = o.optString("slug"),
            meaning = o.optString("meaning"),
            pronunciation = o.optString("pronunciation"),
            audioUrl = o.optStringOrNull("audioUrl"),
            imageUrl = o.optStringOrNull("imageUrl"),
            partOfSpeech = o.optString("partOfSpeech", "noun"),
            level = o.optString("level", "A1"),
            categoryId = o.optString("categoryId"),
            categoryName = o.optString("categoryName"),
            examples = List(exArr.length()) { i ->
                val e = exArr.getJSONObject(i)
                VocabExample(en = e.optString("en"), bn = e.optString("bn"))
            },
            synonyms = o.optJSONArray("synonyms").toStringList(),
            antonyms = o.optJSONArray("antonyms").toStringList(),
            tags = o.optJSONArray("tags").toStringList(),
            difficulty = o.optInt("difficulty", 1),
            isPublished = o.optBoolean("isPublished", true),
            sortOrder = o.optInt("sortOrder", 0),
            progress = parseProgress(o.optJSONObject("progress"))
        )
    }

    private fun parseProgress(p: JSONObject?): VocabWordProgress {
        if (p == null) return VocabWordProgress()
        return VocabWordProgress(
            status = p.optString("status", "new"),
            isFavorite = p.optBoolean("isFavorite", false),
            correctCount = p.optInt("correctCount", 0),
            wrongCount = p.optInt("wrongCount", 0),
            reviewCount = p.optInt("reviewCount", 0),
            intervalDays = p.optInt("intervalDays", 0),
            easeFactor = p.optDouble("easeFactor", 2.5),
            repetition = p.optInt("repetition", 0),
            lastQuality = if (p.has("lastQuality") && !p.isNull("lastQuality")) p.optInt("lastQuality") else null,
            nextReviewAt = p.optStringOrNull("nextReviewAt"),
            learnedAt = p.optStringOrNull("learnedAt")
        )
    }

    private fun parseSpeakingSession(s: JSONObject): VocabSpeakingSession {
        return VocabSpeakingSession(
            id = s.optString("id"),
            transcript = s.optString("transcript"),
            duration = s.optInt("duration", 0),
            grammarScore = s.optInt("grammarScore", 0),
            vocabularyScore = s.optInt("vocabularyScore", 0),
            fluencyScore = s.optInt("fluencyScore", 0),
            feedback = s.optString("feedback"),
            createdAt = s.optString("createdAt")
        )
    }

    private fun parseSettings(s: JSONObject): VocabSettings {
        return VocabSettings(
            dailyGoal = s.optInt("dailyGoal", 5),
            preferredLevel = s.optString("preferredLevel", "A1"),
            preferredCategoryIds = s.optJSONArray("preferredCategoryIds").toStringList(),
            autoPronounce = s.optBoolean("autoPronounce", true),
            reminderEnabled = s.optBoolean("reminderEnabled", false),
            reminderTime = s.optString("reminderTime", "20:00"),
            timezone = s.optString("timezone", "Asia/Dhaka")
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { i -> optString(i) }.filter { it.isNotBlank() }
    }

    private fun urlEnc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private companion object {
        const val PREFS_NAME = "english_ai_vocab_cache"
        const val KEY_DASHBOARD_CACHE = "last_dashboard_json"
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
