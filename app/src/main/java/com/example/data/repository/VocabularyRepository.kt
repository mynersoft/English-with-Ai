package com.example.data.repository

import android.content.Context
import com.example.BuildConfig
import com.example.R
import com.example.core.common.OperationType
import com.example.core.common.handleFirestoreError
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
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class BackendApiException(
    message: String,
    val code: String? = null,
    val httpStatus: Int = 500
) : IOException(message)

class VocabularyRepository(context: Context? = null) {
    private val prefs = context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val firestore: FirebaseFirestore? = context?.applicationContext?.let { appCtx ->
        runCatching {
            FirebaseFirestore.getInstance(appCtx.getString(R.string.firestore_database_id))
        }.getOrNull()
    }

    suspend fun fetchDashboard(): Result<VocabDashboardData> = safeCall {
        val data = requestJson("GET", "/api/vocabulary/dashboard")
        cacheDashboardJson(data.toString())
        val parsed = parseDashboard(data, fromCache = false)
        val effectiveGoal = resolveEffectiveDailyGoal(parsed.dailyGoal)
        val dateKey = resolveTodayDateKey(parsed.date, parsed.timezone)
        val effectiveDone = mergeTodayDoneCount(dateKey, parsed.newWordsDone, effectiveGoal)
        parsed.copy(
            dailyGoal = effectiveGoal,
            newWordsDone = effectiveDone,
            isGoalCompleted = effectiveDone >= effectiveGoal
        )
    }

    fun getCachedDashboard(): VocabDashboardData? {
        val raw = prefs?.getString(KEY_DASHBOARD_CACHE, null) ?: return null
        return runCatching {
            val parsed = parseDashboard(JSONObject(raw), fromCache = true)
            val savedGoal = getCachedSettingsLocally()?.dailyGoal?.coerceIn(MIN_DAILY_GOAL, MAX_DAILY_GOAL)
            val effectiveGoal = savedGoal ?: parsed.dailyGoal.coerceIn(MIN_DAILY_GOAL, MAX_DAILY_GOAL)
            val dateKey = resolveTodayDateKey(parsed.date, parsed.timezone)
            val effectiveDone = mergeTodayDoneCount(dateKey, parsed.newWordsDone, effectiveGoal)
            parsed.copy(
                dailyGoal = effectiveGoal,
                newWordsDone = effectiveDone,
                isGoalCompleted = effectiveDone >= effectiveGoal
            )
        }.getOrNull()
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
        val remoteGoal = data.optInt("dailyGoal", MIN_DAILY_GOAL)
        val effectiveGoal = resolveEffectiveDailyGoal(remoteGoal)
        val timezone = data.optString("timezone", "Asia/Dhaka")
        val dateKey = resolveTodayDateKey(data.optString("date"), timezone)
        val newWordsDone = mergeTodayDoneCount(dateKey, data.optInt("newWordsDone", 0), effectiveGoal)
        val assignedWords = parseWordArray(data.optJSONArray("words"))
        val combinedWords = supplementDailyWordsIfNeeded(assignedWords, effectiveGoal)
        DailyWordsData(
            date = dateKey,
            timezone = timezone,
            dailyGoal = effectiveGoal,
            newWordsDone = newWordsDone,
            isGoalCompleted = newWordsDone >= effectiveGoal,
            words = combinedWords
        )
    }

    suspend fun recordLearned(vocabularyId: String, knowsWord: Boolean): Result<DailyWordsData> = safeCall {
        val effectiveGoal = resolveEffectiveDailyGoal(MIN_DAILY_GOAL)
        val payload = JSONObject()
            .put("vocabularyId", vocabularyId)
            .put("knowsWord", knowsWord)

        try {
            val data = requestJson("POST", "/api/vocabulary/learn", payload)
            saveKnownWordLocally(vocabularyId, knowsWord)
            val daily = data.optJSONObject("daily") ?: JSONObject()
            val dateKey = resolveTodayDateKey(daily.optString("date"))
            val updatedDone = recordDailyLearnedLocally(
                wordId = vocabularyId,
                dateKey = dateKey,
                remoteFloor = daily.optInt("newWordsDone", 0),
                isOverflow = false,
                effectiveGoal = effectiveGoal
            )
            updateCachedDashboardProgress(effectiveGoal, updatedDone)
            DailyWordsData(
                date = dateKey,
                dailyGoal = effectiveGoal,
                newWordsDone = updatedDone,
                isGoalCompleted = updatedDone >= effectiveGoal
            )
        } catch (err: BackendApiException) {
            val isDailyCapError = err.code == "DAILY_LIMIT_REACHED" ||
                err.httpStatus == 409 ||
                err.message.orEmpty().contains("Daily limit", ignoreCase = true)
            if (!isDailyCapError || effectiveGoal <= MIN_DAILY_GOAL) {
                throw err
            }

            // Persist the word's SRS learning state on the backend without hitting /learn's 5-word cap.
            val reviewPayload = JSONObject()
                .put("vocabularyId", vocabularyId)
                .put("quality", if (knowsWord) 4 else 1)
            runCatching { requestJson("POST", "/api/vocabulary/review", reviewPayload) }
                .onFailure { Log.w(TAG, "Overflow review sync failed: ${it.message}") }

            saveKnownWordLocally(vocabularyId, knowsWord)
            val dateKey = resolveTodayDateKey(null)
            val updatedDone = recordDailyLearnedLocally(
                wordId = vocabularyId,
                dateKey = dateKey,
                remoteFloor = MIN_DAILY_GOAL,
                isOverflow = true,
                effectiveGoal = effectiveGoal
            )
            updateCachedDashboardProgress(effectiveGoal, updatedDone)
            DailyWordsData(
                date = dateKey,
                dailyGoal = effectiveGoal,
                newWordsDone = updatedDone,
                isGoalCompleted = updatedDone >= effectiveGoal
            )
        }
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
        val firestoreSettings = readSettingsFromFirestore()
        if (firestoreSettings != null) {
            cacheSettingsLocally(firestoreSettings)
            return@safeCall firestoreSettings
        }

        val remoteResult = runCatching {
            val data = requestJson("GET", "/api/vocabulary/settings")
            parseSettings(data.getJSONObject("settings"))
        }
        val localCached = getCachedSettingsLocally()
        val remote = remoteResult.getOrNull()

        when {
            remote != null && remote.dailyGoal > MIN_DAILY_GOAL -> {
                cacheSettingsLocally(remote)
                remote
            }
            localCached != null -> localCached
            remote != null -> {
                cacheSettingsLocally(remote)
                remote
            }
            else -> throw (remoteResult.exceptionOrNull()
                ?: IllegalStateException("Failed to load vocabulary settings."))
        }
    }

    suspend fun saveSettings(settings: VocabSettings): Result<VocabSettings> = safeCall {
        val normalized = normalizeVocabSettings(settings)
        cacheSettingsLocally(normalized)
        updateCachedDashboardGoal(normalized.dailyGoal)

        val firestoreResult = saveSettingsToFirestore(normalized)
        val backendResult = syncSettingsToBackend(normalized)

        if (firestoreResult.isFailure && backendResult.isFailure && prefs == null) {
            throw (firestoreResult.exceptionOrNull()
                ?: backendResult.exceptionOrNull()
                ?: IllegalStateException("Could not save settings."))
        }
        normalized
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
        requestJson("DELETE", "/api/admin/vocabulary/words/$wordId").let { }
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
        requestJson("DELETE", "/api/admin/vocabulary/categories/$categoryId").let { }
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

    private fun normalizeVocabSettings(settings: VocabSettings): VocabSettings =
        Companion.normalizeVocabSettings(settings)

    private fun isPermissionDenied(error: Throwable): Boolean =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .any { it.code == FirebaseFirestoreException.Code.PERMISSION_DENIED }

    private suspend fun saveSettingsToFirestore(settings: VocabSettings): Result<Unit> {
        if (firestorePrefsUnavailable) {
            return Result.failure(IllegalStateException("Firestore vocabulary preferences unavailable"))
        }
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val uid = Firebase.auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("User must be signed in before saving settings."))
        val docPath = "users/$uid/preferences/vocabulary"
        return try {
            val docRef = db.collection("users")
                .document(uid)
                .collection("preferences")
                .document("vocabulary")
            val snapshot = docRef.get().await()
            val payload = hashMapOf<String, Any>(
                "userId" to uid,
                "dailyGoal" to settings.dailyGoal,
                "preferredLevel" to settings.preferredLevel,
                "preferredCategoryIds" to settings.preferredCategoryIds,
                "autoPronounce" to settings.autoPronounce,
                "reminderEnabled" to settings.reminderEnabled,
                "reminderTime" to settings.reminderTime,
                "timezone" to settings.timezone,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!snapshot.exists() || snapshot.getTimestamp("createdAt") == null) {
                payload["createdAt"] = FieldValue.serverTimestamp()
                docRef.set(payload).await()
            } else {
                docRef.update(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isPermissionDenied(e)) {
                firestorePrefsUnavailable = true
                Log.w(TAG, "Firestore vocabulary preferences write skipped (rules not deployed on remote project).")
            } else {
                handleFirestoreError(e, OperationType.WRITE, docPath)
            }
            Result.failure(e)
        }
    }

    private suspend fun readSettingsFromFirestore(): VocabSettings? {
        if (firestorePrefsUnavailable) return null
        val db = firestore ?: return null
        val uid = Firebase.auth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: return null
        val docPath = "users/$uid/preferences/vocabulary"
        return try {
            val snap = db.collection("users")
                .document(uid)
                .collection("preferences")
                .document("vocabulary")
                .get()
                .await()
            if (!snap.exists()) return null
            val rawCategories = (snap.get("preferredCategoryIds") as? List<*>)
                ?.mapNotNull { it as? String }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            normalizeVocabSettings(
                VocabSettings(
                    dailyGoal = snap.getLong("dailyGoal")?.toInt() ?: MIN_DAILY_GOAL,
                    preferredLevel = snap.getString("preferredLevel") ?: "A1",
                    preferredCategoryIds = rawCategories,
                    autoPronounce = snap.getBoolean("autoPronounce") ?: true,
                    reminderEnabled = snap.getBoolean("reminderEnabled") ?: false,
                    reminderTime = snap.getString("reminderTime") ?: "20:00",
                    timezone = snap.getString("timezone") ?: "Asia/Dhaka"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isPermissionDenied(e)) {
                firestorePrefsUnavailable = true
                Log.w(TAG, "Firestore vocabulary preferences read skipped (rules not deployed on remote project).")
            } else {
                handleFirestoreError(e, OperationType.GET, docPath)
            }
            null
        }
    }

    private suspend fun syncSettingsToBackend(settings: VocabSettings): Result<VocabSettings> {
        fun buildPayload(goal: Int): JSONObject = JSONObject()
            .put("dailyGoal", goal)
            .put("preferredLevel", settings.preferredLevel)
            .put("preferredCategoryIds", JSONArray(settings.preferredCategoryIds))
            .put("autoPronounce", settings.autoPronounce)
            .put("reminderEnabled", settings.reminderEnabled)
            .put("reminderTime", settings.reminderTime)
            .put("timezone", settings.timezone)

        return runCatching {
            val data = requestJson("PUT", "/api/vocabulary/settings", buildPayload(settings.dailyGoal))
            parseSettings(data.getJSONObject("settings"))
        }.recoverCatching { err ->
            // If an older deployed backend still rejects dailyGoal > 5 with HTTP 400,
            // keep the remaining preferences synced in MongoDB with dailyGoal = 5.
            if (settings.dailyGoal > MIN_DAILY_GOAL && err is BackendApiException && err.httpStatus == 400) {
                val data = requestJson("PUT", "/api/vocabulary/settings", buildPayload(MIN_DAILY_GOAL))
                parseSettings(data.getJSONObject("settings"))
            } else {
                throw err
            }
        }
    }

    private suspend fun resolveEffectiveDailyGoal(remoteGoal: Int): Int {
        val localGoal = getCachedSettingsLocally()?.dailyGoal
        if (localGoal != null) return localGoal.coerceIn(MIN_DAILY_GOAL, MAX_DAILY_GOAL)
        val firestoreSettings = readSettingsFromFirestore()
        if (firestoreSettings != null) {
            cacheSettingsLocally(firestoreSettings)
            return firestoreSettings.dailyGoal.coerceIn(MIN_DAILY_GOAL, MAX_DAILY_GOAL)
        }
        return remoteGoal.coerceIn(MIN_DAILY_GOAL, MAX_DAILY_GOAL)
    }

    private suspend fun supplementDailyWordsIfNeeded(
        assignedWords: List<VocabWord>,
        targetCount: Int
    ): List<VocabWord> {
        if (assignedWords.size >= targetCount) return assignedWords.take(targetCount)
        val settings = getCachedSettingsLocally() ?: readSettingsFromFirestore()
        val level = settings?.preferredLevel?.takeIf { it.isNotBlank() && it != "ALL" }
        val seenIds = assignedWords.mapTo(mutableSetOf()) { it.id }
        val combined = assignedWords.toMutableList()

        suspend fun fetchMore(levelFilter: String?) {
            if (combined.size >= targetCount) return
            val limit = targetCount.coerceIn(MIN_DAILY_GOAL, 100)
            val q = buildString {
                append("?page=1&limit=").append(limit)
                if (!levelFilter.isNullOrBlank()) append("&level=").append(urlEnc(levelFilter))
            }
            val pageData = runCatching { requestJson("GET", "/api/vocabulary/words$q") }.getOrNull() ?: return
            val candidates = parseWordArray(pageData.optJSONArray("items"))
            for (word in candidates) {
                if (combined.size >= targetCount) break
                if (word.id.isNotBlank() && seenIds.add(word.id) && !word.progress.isKnown && word.progress.learnedAt == null) {
                    combined.add(word)
                }
            }
        }

        fetchMore(level)
        if (combined.size < targetCount && level != null) {
            fetchMore(null)
        }
        return combined
    }

    private fun settingsCacheKey(): String {
        val uid = Firebase.auth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: "default"
        return "${KEY_SETTINGS_PREFIX}$uid"
    }

    private fun cacheSettingsLocally(settings: VocabSettings) {
        if (prefs == null) return
        val json = JSONObject()
            .put("dailyGoal", settings.dailyGoal)
            .put("preferredLevel", settings.preferredLevel)
            .put("preferredCategoryIds", JSONArray(settings.preferredCategoryIds))
            .put("autoPronounce", settings.autoPronounce)
            .put("reminderEnabled", settings.reminderEnabled)
            .put("reminderTime", settings.reminderTime)
            .put("timezone", settings.timezone)
        prefs.edit().putString(settingsCacheKey(), json.toString()).apply()
    }

    private fun getCachedSettingsLocally(): VocabSettings? {
        val raw = prefs?.getString(settingsCacheKey(), null) ?: return null
        return runCatching { parseSettings(JSONObject(raw)) }.getOrNull()
    }

    private fun updateCachedDashboardGoal(dailyGoal: Int) {
        val raw = prefs?.getString(KEY_DASHBOARD_CACHE, null) ?: return
        runCatching {
            val obj = JSONObject(raw)
            obj.put("dailyGoal", dailyGoal)
            obj.put("isGoalCompleted", obj.optInt("newWordsDone", 0) >= dailyGoal)
            cacheDashboardJson(obj.toString())
        }
    }

    private fun updateCachedDashboardProgress(dailyGoal: Int, newWordsDone: Int) {
        val raw = prefs?.getString(KEY_DASHBOARD_CACHE, null) ?: return
        runCatching {
            val obj = JSONObject(raw)
            obj.put("dailyGoal", dailyGoal)
            obj.put("newWordsDone", newWordsDone)
            obj.put("isGoalCompleted", newWordsDone >= dailyGoal)
            cacheDashboardJson(obj.toString())
        }
    }

    private fun currentUidOrDefault(): String =
        Firebase.auth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: "default"

    private fun resolveTodayDateKey(serverDate: String?, timezoneOverride: String? = null): String {
        val uid = currentUidOrDefault()
        val trimmed = serverDate?.trim().orEmpty()
        if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(trimmed)) {
            prefs?.edit()?.putString("${KEY_LAST_DATE_PREFIX}$uid", trimmed)?.apply()
            return trimmed
        }
        val cachedDate = prefs?.getString("${KEY_LAST_DATE_PREFIX}$uid", null)
        val tzId = timezoneOverride?.takeIf { it.isNotBlank() }
            ?: getCachedSettingsLocally()?.timezone
            ?: "Asia/Dhaka"
        val localToday = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone(tzId)
            }.format(Date())
        }.getOrElse {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        }
        return if (!cachedDate.isNullOrBlank() && cachedDate == localToday) cachedDate else localToday
    }

    private fun recordDailyLearnedLocally(
        wordId: String,
        dateKey: String,
        remoteFloor: Int,
        isOverflow: Boolean,
        effectiveGoal: Int
    ): Int {
        if (prefs == null || wordId.isBlank()) {
            return remoteFloor.coerceIn(0, effectiveGoal)
        }
        val uid = currentUidOrDefault()
        val allKey = "${KEY_DAILY_LEARNED_PREFIX}${uid}_$dateKey"
        val extraKey = "${KEY_DAILY_EXTRA_PREFIX}${uid}_$dateKey"
        val floorKey = "${KEY_DAILY_FLOOR_PREFIX}${uid}_$dateKey"

        val allIds = prefs.getStringSet(allKey, emptySet()).orEmpty().toMutableSet()
        allIds.add(wordId)

        val extraIds = prefs.getStringSet(extraKey, emptySet()).orEmpty().toMutableSet()
        if (isOverflow) {
            extraIds.add(wordId)
        } else {
            extraIds.remove(wordId)
        }

        val updatedFloor = maxOf(prefs.getInt(floorKey, 0), remoteFloor)

        prefs.edit()
            .putStringSet(allKey, allIds)
            .putStringSet(extraKey, extraIds)
            .putInt(floorKey, updatedFloor)
            .apply()

        return computeEffectiveDoneCount(
            remoteDone = updatedFloor,
            localAllCount = allIds.size,
            overflowCount = extraIds.size,
            effectiveGoal = effectiveGoal
        )
    }

    private fun mergeTodayDoneCount(dateKey: String, remoteDone: Int, effectiveGoal: Int): Int {
        if (prefs == null) return remoteDone.coerceIn(0, effectiveGoal)
        val uid = currentUidOrDefault()
        val allCount = prefs.getStringSet("${KEY_DAILY_LEARNED_PREFIX}${uid}_$dateKey", emptySet()).orEmpty().size
        val extraCount = prefs.getStringSet("${KEY_DAILY_EXTRA_PREFIX}${uid}_$dateKey", emptySet()).orEmpty().size
        val storedFloor = prefs.getInt("${KEY_DAILY_FLOOR_PREFIX}${uid}_$dateKey", 0)
        val bestRemote = maxOf(remoteDone, storedFloor)
        if (remoteDone > storedFloor) {
            prefs.edit().putInt("${KEY_DAILY_FLOOR_PREFIX}${uid}_$dateKey", remoteDone).apply()
        }
        return computeEffectiveDoneCount(
            remoteDone = bestRemote,
            localAllCount = allCount,
            overflowCount = extraCount,
            effectiveGoal = effectiveGoal
        )
    }

    private fun cacheDashboardJson(raw: String) {
        prefs?.edit()?.putString(KEY_DASHBOARD_CACHE, raw)?.apply()
    }

    private fun saveKnownWordLocally(wordId: String, knowsWord: Boolean) {
        if (prefs == null || wordId.isBlank()) return
        val updated = prefs.getStringSet(KEY_KNOWN_WORD_IDS, emptySet()).orEmpty().toMutableSet()
        if (knowsWord) updated.add(wordId) else updated.remove(wordId)
        prefs.edit().putStringSet(KEY_KNOWN_WORD_IDS, updated).apply()
    }

    private fun isLocallyMarkedKnown(wordId: String): Boolean {
        if (prefs == null || wordId.isBlank()) return false
        return prefs.getStringSet(KEY_KNOWN_WORD_IDS, emptySet()).orEmpty().contains(wordId)
    }

    private fun parseDashboard(data: JSONObject, fromCache: Boolean): VocabDashboardData {
        val actArr = data.optJSONArray("recentActivity") ?: JSONArray()
        return VocabDashboardData(
            greetingName = data.optString("greetingName", "Learner"),
            date = data.optString("date"),
            timezone = data.optString("timezone", "Asia/Dhaka"),
            dailyGoal = data.optInt("dailyGoal", MIN_DAILY_GOAL).coerceIn(MIN_DAILY_GOAL, MAX_DAILY_GOAL),
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
            progress = parseProgress(o.optJSONObject("progress"), wordId = o.optString("id"))
        )
    }

    private fun parseProgress(p: JSONObject?, wordId: String = ""): VocabWordProgress {
        val localKnown = isLocallyMarkedKnown(wordId)
        if (p == null) {
            return VocabWordProgress(
                status = if (localKnown) "mastered" else "new",
                isKnown = localKnown
            )
        }
        val rawStatus = p.optString("status", "new")
        val remoteKnown = p.optBoolean("isKnown", false) ||
            p.optBoolean("knowsWord", false) ||
            rawStatus.equals("mastered", ignoreCase = true) ||
            rawStatus.equals("known", ignoreCase = true)
        val known = remoteKnown || localKnown
        return VocabWordProgress(
            status = if (known && rawStatus.equals("new", ignoreCase = true)) "mastered" else rawStatus,
            isFavorite = p.optBoolean("isFavorite", false),
            correctCount = p.optInt("correctCount", 0),
            wrongCount = p.optInt("wrongCount", 0),
            reviewCount = p.optInt("reviewCount", 0),
            intervalDays = p.optInt("intervalDays", 0),
            easeFactor = p.optDouble("easeFactor", 2.5),
            repetition = p.optInt("repetition", 0),
            lastQuality = if (p.has("lastQuality") && !p.isNull("lastQuality")) p.optInt("lastQuality") else null,
            nextReviewAt = p.optStringOrNull("nextReviewAt"),
            learnedAt = p.optStringOrNull("learnedAt"),
            isKnown = known
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
        return normalizeVocabSettings(
            VocabSettings(
                dailyGoal = s.optInt("dailyGoal", MIN_DAILY_GOAL),
                preferredLevel = s.optString("preferredLevel", "A1"),
                preferredCategoryIds = s.optJSONArray("preferredCategoryIds").toStringList(),
                autoPronounce = s.optBoolean("autoPronounce", true),
                reminderEnabled = s.optBoolean("reminderEnabled", false),
                reminderTime = s.optString("reminderTime", "20:00"),
                timezone = s.optString("timezone", "Asia/Dhaka")
            )
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

    internal companion object {
        private const val TAG = "VocabularyRepository"
        private const val PREFS_NAME = "english_ai_vocab_cache"
        private const val KEY_DASHBOARD_CACHE = "last_dashboard_json"
        private const val KEY_KNOWN_WORD_IDS = "known_word_ids"
        private const val KEY_SETTINGS_PREFIX = "vocab_settings_"
        private const val KEY_LAST_DATE_PREFIX = "vocab_last_date_"
        private const val KEY_DAILY_LEARNED_PREFIX = "vocab_daily_learned_"
        private const val KEY_DAILY_EXTRA_PREFIX = "vocab_daily_extra_"
        private const val KEY_DAILY_FLOOR_PREFIX = "vocab_daily_floor_"
        const val MIN_DAILY_GOAL = 5
        const val MAX_DAILY_GOAL = 500
        private val VALID_LEVELS = setOf("A1", "A2", "B1", "B2", "C1", "C2")
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        @Volatile
        private var firestorePrefsUnavailable = false

        fun computeEffectiveDoneCount(
            remoteDone: Int,
            localAllCount: Int,
            overflowCount: Int,
            effectiveGoal: Int
        ): Int {
            val safeGoal = effectiveGoal.coerceIn(MIN_DAILY_GOAL, MAX_DAILY_GOAL)
            val raw = maxOf(remoteDone, localAllCount, remoteDone + overflowCount)
            return raw.coerceIn(0, safeGoal)
        }

        fun normalizeVocabSettings(settings: VocabSettings): VocabSettings {
            val level = settings.preferredLevel.trim().uppercase()
            val safeLevel = if (level == "ALL" || level in VALID_LEVELS) level else "A1"
            val time = settings.reminderTime.trim()
            val safeTime = if (Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(time)) time else "20:00"
            return VocabSettings(
                dailyGoal = settings.dailyGoal.coerceIn(MIN_DAILY_GOAL, MAX_DAILY_GOAL),
                preferredLevel = safeLevel,
                preferredCategoryIds = settings.preferredCategoryIds
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(100),
                autoPronounce = settings.autoPronounce,
                reminderEnabled = settings.reminderEnabled,
                reminderTime = safeTime,
                timezone = settings.timezone.trim().ifBlank { "Asia/Dhaka" }.take(80)
            )
        }
    }
}
