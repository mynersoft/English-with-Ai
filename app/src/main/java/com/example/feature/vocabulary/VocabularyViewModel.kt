package com.example.feature.vocabulary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.core.common.UiState
import com.example.core.network.toUserFriendlyBackendMessage
import com.example.core.ui.CelebrationEvent
import com.example.core.ui.CelebrationRules
import com.example.core.ui.CelebrationStyle
import com.example.core.ui.DailyGoalCelebrationTracker
import com.example.data.repository.BackendApiException
import com.example.data.repository.VocabularyRepository
import com.example.domain.model.DailyWordsData
import com.example.domain.model.PaginatedWordsData
import com.example.domain.model.QuizResultData
import com.example.domain.model.QuizSessionData
import com.example.domain.model.QuizSubmissionAnswer
import com.example.domain.model.ReviewTabData
import com.example.domain.model.SentenceCheckResult
import com.example.domain.model.VocabCategorySummary
import com.example.domain.model.VocabDashboardData
import com.example.domain.model.VocabProgressAnalytics
import com.example.domain.model.VocabSettings
import com.example.domain.model.VocabSpeakingOverview
import com.example.domain.model.VocabWord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VocabActionFeedback(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

data class QuizUiState(
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val session: QuizSessionData? = null,
    val currentIndex: Int = 0,
    val selectedAnswers: Map<String, String> = emptyMap(),
    val matchingPairs: Map<String, String> = emptyMap(),
    val result: QuizResultData? = null,
    val startedAtMillis: Long = 0L
)

data class SentenceUiState(
    val words: List<VocabWord> = emptyList(),
    val currentIndex: Int = 0,
    val loading: Boolean = false,
    val checking: Boolean = false,
    val error: String? = null,
    val result: SentenceCheckResult? = null
)

data class SpeakingPracticeUiState(
    val loading: Boolean = false,
    val evaluating: Boolean = false,
    val error: String? = null,
    val overview: VocabSpeakingOverview = VocabSpeakingOverview(),
    val todayWords: List<VocabWord> = emptyList()
)

class VocabularyViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repo = VocabularyRepository(application)

    private val _dashboardState = MutableStateFlow<UiState<VocabDashboardData>>(UiState.Loading)
    val dashboardState: StateFlow<UiState<VocabDashboardData>> = _dashboardState.asStateFlow()

    private val _categoriesState = MutableStateFlow<UiState<List<VocabCategorySummary>>>(UiState.Loading)
    val categoriesState: StateFlow<UiState<List<VocabCategorySummary>>> = _categoriesState.asStateFlow()

    private val _wordsState = MutableStateFlow<UiState<PaginatedWordsData>>(UiState.Loading)
    val wordsState: StateFlow<UiState<PaginatedWordsData>> = _wordsState.asStateFlow()

    private val _dailyState = MutableStateFlow<UiState<DailyWordsData>>(UiState.Loading)
    val dailyState: StateFlow<UiState<DailyWordsData>> = _dailyState.asStateFlow()

    private val _wordDeck = MutableStateFlow<List<VocabWord>>(emptyList())
    val wordDeck: StateFlow<List<VocabWord>> = _wordDeck.asStateFlow()

    private val _currentDeckIndex = MutableStateFlow(0)
    val currentDeckIndex: StateFlow<Int> = _currentDeckIndex.asStateFlow()

    private val _reviewState = MutableStateFlow<UiState<ReviewTabData>>(UiState.Loading)
    val reviewState: StateFlow<UiState<ReviewTabData>> = _reviewState.asStateFlow()

    private val _selectedReviewTab = MutableStateFlow(savedStateHandle["vocab_review_tab"] ?: "due")
    val selectedReviewTab: StateFlow<String> = _selectedReviewTab.asStateFlow()

    private val _quizState = MutableStateFlow(
        QuizUiState(
            currentIndex = savedStateHandle["vocab_quiz_index"] ?: 0
        )
    )
    val quizState: StateFlow<QuizUiState> = _quizState.asStateFlow()

    private val _sentenceState = MutableStateFlow(SentenceUiState())
    val sentenceState: StateFlow<SentenceUiState> = _sentenceState.asStateFlow()

    private val _speakingState = MutableStateFlow(SpeakingPracticeUiState())
    val speakingState: StateFlow<SpeakingPracticeUiState> = _speakingState.asStateFlow()

    private val _progressState = MutableStateFlow<UiState<VocabProgressAnalytics>>(UiState.Loading)
    val progressState: StateFlow<UiState<VocabProgressAnalytics>> = _progressState.asStateFlow()

    private val _settingsState = MutableStateFlow<UiState<VocabSettings>>(UiState.Loading)
    val settingsState: StateFlow<UiState<VocabSettings>> = _settingsState.asStateFlow()

    private val _favoritesState = MutableStateFlow<UiState<List<VocabWord>>>(UiState.Loading)
    val favoritesState: StateFlow<UiState<List<VocabWord>>> = _favoritesState.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _feedback = MutableStateFlow(VocabActionFeedback())
    val feedback: StateFlow<VocabActionFeedback> = _feedback.asStateFlow()

    private val _celebrationEvent = MutableStateFlow<CelebrationEvent?>(null)
    val celebrationEvent: StateFlow<CelebrationEvent?> = _celebrationEvent.asStateFlow()

    init {
        loadDashboard()
        checkAdminAccess()
        loadSettings()
    }

    fun clearFeedback() {
        _feedback.value = VocabActionFeedback()
    }

    fun dismissCelebration() {
        _celebrationEvent.value = null
    }

    fun checkAdminAccess() {
        viewModelScope.launch {
            _isAdmin.value = repo.checkIsAdmin()
        }
    }

    fun loadDashboard() {
        viewModelScope.launch {
            val cached = repo.getCachedDashboard()
            if (cached != null && _dashboardState.value is UiState.Loading) {
                _dashboardState.value = UiState.Success(cached)
            } else if (_dashboardState.value !is UiState.Success) {
                _dashboardState.value = UiState.Loading
            }
            repo.fetchDashboard()
                .onSuccess { _dashboardState.value = UiState.Success(it) }
                .onFailure { err ->
                    val friendly = err.toUserFriendlyBackendMessage(
                        "Unable to reach the server. Please check your internet connection."
                    )
                    if (cached != null) {
                        _feedback.value = VocabActionFeedback(
                            message = "$friendly Showing saved offline progress.",
                            isError = true
                        )
                    } else {
                        _dashboardState.value = UiState.Error(friendly)
                    }
                }
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            _categoriesState.value = UiState.Loading
            repo.fetchCategories()
                .onSuccess { _categoriesState.value = UiState.Success(it) }
                .onFailure { _categoriesState.value = UiState.Error(it.message ?: "Failed to load categories.") }
        }
    }

    fun loadWords(
        page: Int = 1,
        level: String = "ALL",
        categoryId: String = "",
        search: String = ""
    ) {
        viewModelScope.launch {
            _wordsState.value = UiState.Loading
            repo.fetchWords(page = page, level = level, categoryId = categoryId, search = search)
                .onSuccess {
                    _wordsState.value = UiState.Success(it)
                }
                .onFailure {
                    _wordsState.value = UiState.Error(it.message ?: "Failed to load words.")
                }
        }
    }

    fun loadDailyWords(openDeck: Boolean = false) {
        viewModelScope.launch {
            _dailyState.value = UiState.Loading
            if (openDeck) {
                _wordDeck.update { list ->
                    list.filterNot { it.progress.status.equals("learning", ignoreCase = true) }
                }
            }
            repo.fetchDailyWords()
                .onSuccess { data ->
                    val filteredWords = data.words.filterNot {
                        it.progress.status.equals("learning", ignoreCase = true)
                    }
                    _dailyState.value = UiState.Success(data.copy(words = filteredWords))
                    if (openDeck || _wordDeck.value.isEmpty()) {
                        _wordDeck.value = filteredWords
                        _currentDeckIndex.value = 0
                    }
                }
                .onFailure {
                    _dailyState.value = UiState.Error(it.message ?: "Failed to load today's words.")
                }
        }
    }

    fun setDeckWords(words: List<VocabWord>, startIndex: Int = 0) {
        _wordDeck.value = words
        _currentDeckIndex.value = startIndex.coerceIn(0, (words.size - 1).coerceAtLeast(0))
    }

    fun openSingleWordInDeck(wordId: String) {
        viewModelScope.launch {
            repo.fetchWordDetail(wordId)
                .onSuccess { w ->
                    _wordDeck.value = listOf(w)
                    _currentDeckIndex.value = 0
                }
                .onFailure {
                    _feedback.value = VocabActionFeedback(message = it.message ?: "Failed to load word.", isError = true)
                }
        }
    }

    fun moveDeck(delta: Int) {
        val size = _wordDeck.value.size
        if (size == 0) return
        _feedback.value = VocabActionFeedback()
        _currentDeckIndex.value = (_currentDeckIndex.value + delta).coerceIn(0, size - 1)
    }

    fun markWordLearned(wordId: String, knowsWord: Boolean, advanceOnSuccess: Boolean = true) {
        val existingWord = _wordDeck.value.firstOrNull { it.id == wordId }
            ?: (_dailyState.value as? UiState.Success)?.data?.words?.firstOrNull { it.id == wordId }
            ?: (_wordsState.value as? UiState.Success)?.data?.items?.firstOrNull { it.id == wordId }
            ?: (_favoritesState.value as? UiState.Success)?.data?.firstOrNull { it.id == wordId }
        val wasAlreadyMastered = existingWord?.progress?.isKnown == true ||
            existingWord?.progress?.status.equals("mastered", ignoreCase = true)
        val wasGoalCompletedBefore = (_dailyState.value as? UiState.Success)?.data?.isGoalCompleted == true ||
            (_dashboardState.value as? UiState.Success)?.data?.let {
                it.dailyGoal > 0 && it.newWordsDone >= it.dailyGoal
            } == true

        viewModelScope.launch {
            _feedback.value = VocabActionFeedback(busy = true)
            repo.recordLearned(wordId, knowsWord)
                .onSuccess { updatedDaily ->
                    val newStatus = if (knowsWord) "mastered" else "learning"
                    fun VocabWord.withUpdatedProgress(): VocabWord =
                        if (id == wordId) {
                            copy(
                                progress = progress.copy(
                                    status = newStatus,
                                    isKnown = knowsWord,
                                    learnedAt = "now"
                                )
                            )
                        } else this

                    if (!knowsWord) {
                        val updatedDeck = _wordDeck.value.filterNot { it.id == wordId }
                        _wordDeck.value = updatedDeck
                        _currentDeckIndex.value = _currentDeckIndex.value.coerceIn(
                            0,
                            (updatedDeck.size - 1).coerceAtLeast(0)
                        )
                    } else {
                        _wordDeck.update { list -> list.map { it.withUpdatedProgress() } }
                    }
                    _dailyState.update { state ->
                        if (state is UiState.Success) {
                            val updatedDailyWords = if (!knowsWord) {
                                state.data.words.filterNot { it.id == wordId }
                            } else {
                                state.data.words.map { it.withUpdatedProgress() }
                            }
                            UiState.Success(
                                state.data.copy(
                                    newWordsDone = updatedDaily.newWordsDone,
                                    dailyGoal = updatedDaily.dailyGoal,
                                    isGoalCompleted = updatedDaily.isGoalCompleted,
                                    words = updatedDailyWords
                                )
                            )
                        } else state
                    }
                    _wordsState.update { state ->
                        if (state is UiState.Success) {
                            UiState.Success(
                                state.data.copy(items = state.data.items.map { it.withUpdatedProgress() })
                            )
                        } else state
                    }
                    _favoritesState.update { state ->
                        if (state is UiState.Success) {
                            UiState.Success(state.data.map { it.withUpdatedProgress() })
                        } else state
                    }
                    _feedback.value = VocabActionFeedback(
                        message = if (knowsWord) {
                            "Marked as known (${updatedDaily.newWordsDone}/${updatedDaily.dailyGoal} today)"
                        } else {
                            "Added to review queue (${updatedDaily.newWordsDone}/${updatedDaily.dailyGoal} today)"
                        }
                    )

                    val app = getApplication<Application>()
                    val dateKey = updatedDaily.date.ifBlank { DailyGoalCelebrationTracker.todayKey() }
                    val celebratedGoal = DailyGoalCelebrationTracker.celebratedVocabGoalForDate(app, dateKey)
                    val justCompletedGoal = CelebrationRules.shouldCelebrateVocabDailyGoal(
                        newWordsDone = updatedDaily.newWordsDone,
                        dailyGoal = updatedDaily.dailyGoal,
                        isGoalCompleted = updatedDaily.isGoalCompleted,
                        wasGoalCompletedBefore = wasGoalCompletedBefore,
                        alreadyCelebratedGoalForDate = celebratedGoal
                    )
                    val justMasteredWord = CelebrationRules.shouldCelebrateWordMastered(
                        knowsWord = knowsWord,
                        wasAlreadyMastered = wasAlreadyMastered
                    )
                    if (justCompletedGoal) {
                        DailyGoalCelebrationTracker.markVocabGoalCelebrated(app, dateKey, updatedDaily.dailyGoal)
                        _celebrationEvent.value = CelebrationEvent(
                            title = "Daily Goal Completed!",
                            subtitle = "You learned ${updatedDaily.newWordsDone}/${updatedDaily.dailyGoal} words today!",
                            style = CelebrationStyle.GOAL_BURST
                        )
                    } else if (justMasteredWord) {
                        val wordLabel = existingWord?.word?.takeIf { it.isNotBlank() } ?: "New word"
                        _celebrationEvent.value = CelebrationEvent(
                            title = "Word Mastered!",
                            subtitle = "\"$wordLabel\" marked as mastered",
                            style = CelebrationStyle.CONFETTI
                        )
                    }

                    if (knowsWord && advanceOnSuccess && _currentDeckIndex.value < _wordDeck.value.lastIndex) {
                        _currentDeckIndex.value += 1
                    }
                    loadDashboard()
                }
                .onFailure { err ->
                    _feedback.value = VocabActionFeedback(
                        message = err.message ?: "Could not save word progress.",
                        isError = true
                    )
                }
        }
    }

    fun toggleBookmark(wordId: String) {
        viewModelScope.launch {
            repo.toggleFavorite(wordId)
                .onSuccess { isFav ->
                    _wordDeck.update { list ->
                        list.map { w ->
                            if (w.id == wordId) w.copy(progress = w.progress.copy(isFavorite = isFav)) else w
                        }
                    }
                    _feedback.value = VocabActionFeedback(
                        message = if (isFav) "Bookmarked word" else "Removed bookmark"
                    )
                }
                .onFailure { err ->
                    _feedback.value = VocabActionFeedback(message = err.message ?: "Bookmark failed.", isError = true)
                }
        }
    }

    fun loadFavorites() {
        viewModelScope.launch {
            _favoritesState.value = UiState.Loading
            repo.fetchFavorites()
                .onSuccess { _favoritesState.value = UiState.Success(it) }
                .onFailure { _favoritesState.value = UiState.Error(it.message ?: "Failed to load bookmarks.") }
        }
    }

    fun loadReviewTab(tab: String = _selectedReviewTab.value) {
        _selectedReviewTab.value = tab
        savedStateHandle["vocab_review_tab"] = tab
        viewModelScope.launch {
            _reviewState.value = UiState.Loading
            repo.fetchReviewTab(tab = tab)
                .onSuccess { _reviewState.value = UiState.Success(it) }
                .onFailure { _reviewState.value = UiState.Error(it.message ?: "Failed to load reviews.") }
        }
    }

    fun submitWordReview(wordId: String, quality: Int) {
        val existingWord = (_reviewState.value as? UiState.Success)?.data?.items?.firstOrNull { it.id == wordId }
        val wasMastered = existingWord?.progress?.status.equals("mastered", ignoreCase = true)
        viewModelScope.launch {
            _feedback.value = VocabActionFeedback(busy = true)
            repo.submitReview(wordId, quality)
                .onSuccess {
                    _feedback.value = VocabActionFeedback(message = "Review saved! Next in ${it.intervalDays} day(s).")
                    if (!wasMastered && it.status.equals("mastered", ignoreCase = true)) {
                        val wordLabel = existingWord?.word?.takeIf { w -> w.isNotBlank() } ?: "Word"
                        _celebrationEvent.value = CelebrationEvent(
                            title = "Word Mastered!",
                            subtitle = "\"$wordLabel\" reached mastered status!",
                            style = CelebrationStyle.GOAL_BURST
                        )
                    }
                    loadReviewTab(_selectedReviewTab.value)
                    loadDashboard()
                }
                .onFailure {
                    _feedback.value = VocabActionFeedback(message = it.message ?: "Failed to save review.", isError = true)
                }
        }
    }

    // ---------- Quiz ----------

    fun startQuiz(quizType: String = "mixed", count: Int = 5, level: String = "ALL", categoryId: String = "") {
        savedStateHandle["vocab_quiz_index"] = 0
        viewModelScope.launch {
            _quizState.value = QuizUiState(loading = true)
            repo.generateQuiz(quizType = quizType, count = count, level = level, categoryId = categoryId)
                .onSuccess { session ->
                    _quizState.value = QuizUiState(
                        loading = false,
                        session = session,
                        currentIndex = 0,
                        startedAtMillis = System.currentTimeMillis()
                    )
                }
                .onFailure {
                    _quizState.value = QuizUiState(loading = false, error = it.message ?: "Unable to start quiz.")
                }
        }
    }

    fun selectQuizAnswer(vocabularyId: String, option: String) {
        _quizState.update { s ->
            s.copy(selectedAnswers = s.selectedAnswers + (vocabularyId to option))
        }
    }

    fun pairMatchingWord(vocabularyId: String, selectedMeaning: String) {
        _quizState.update { s ->
            s.copy(matchingPairs = s.matchingPairs + (vocabularyId to selectedMeaning))
        }
    }

    fun moveQuizQuestion(delta: Int) {
        _quizState.update { s ->
            val max = ((s.session?.questions?.size ?: 1) - 1).coerceAtLeast(0)
            val next = (s.currentIndex + delta).coerceIn(0, max)
            savedStateHandle["vocab_quiz_index"] = next
            s.copy(currentIndex = next)
        }
    }

    fun submitCurrentQuiz() {
        val state = _quizState.value
        val session = state.session ?: return
        val answers = if (session.quizType == "matching") {
            val matchQ = session.questions.firstOrNull() ?: return
            matchQ.leftItems.map { left ->
                QuizSubmissionAnswer(
                    vocabularyId = left.vocabularyId,
                    questionType = "matching",
                    selectedAnswer = state.matchingPairs[left.vocabularyId].orEmpty()
                )
            }
        } else {
            session.questions.map { q ->
                QuizSubmissionAnswer(
                    vocabularyId = q.vocabularyId,
                    questionType = q.questionType,
                    selectedAnswer = state.selectedAnswers[q.vocabularyId].orEmpty()
                )
            }
        }
        val elapsedSec = ((System.currentTimeMillis() - state.startedAtMillis) / 1000L).toInt().coerceAtLeast(1)

        viewModelScope.launch {
            _quizState.update { it.copy(submitting = true, error = null) }
            repo.submitQuiz(session.quizType, elapsedSec, answers)
                .onSuccess { res ->
                    _quizState.update { it.copy(submitting = false, result = res) }
                    loadDashboard()
                }
                .onFailure { err ->
                    _quizState.update { it.copy(submitting = false, error = err.message ?: "Quiz submission failed.") }
                }
        }
    }

    // ---------- Sentence Building ----------

    fun loadSentencePracticeWords() {
        viewModelScope.launch {
            _sentenceState.update { it.copy(loading = true, error = null, result = null) }
            repo.fetchDailyWords()
                .onSuccess { daily ->
                    _sentenceState.update {
                        it.copy(loading = false, words = daily.words, currentIndex = 0, result = null)
                    }
                }
                .onFailure { err ->
                    _sentenceState.update { it.copy(loading = false, error = err.message ?: "Failed to load words.") }
                }
        }
    }

    fun moveSentenceWord(delta: Int) {
        _sentenceState.update { s ->
            val max = (s.words.size - 1).coerceAtLeast(0)
            s.copy(currentIndex = (s.currentIndex + delta).coerceIn(0, max), result = null, error = null)
        }
    }

    fun checkSentence(vocabularyId: String, sentence: String) {
        if (sentence.isBlank()) {
            _sentenceState.update { it.copy(error = "Please type an English sentence first.") }
            return
        }
        viewModelScope.launch {
            _sentenceState.update { it.copy(checking = true, error = null) }
            repo.checkSentence(vocabularyId, sentence)
                .onSuccess { res -> _sentenceState.update { it.copy(checking = false, result = res) } }
                .onFailure { err -> _sentenceState.update { it.copy(checking = false, error = err.message ?: "Check failed.") } }
        }
    }

    // ---------- Speaking Practice ----------

    fun loadSpeakingPractice() {
        viewModelScope.launch {
            _speakingState.update { it.copy(loading = true, error = null) }
            val dailyRes = repo.fetchDailyWords()
            val overviewRes = repo.fetchSpeakingOverview()
            _speakingState.update { s ->
                s.copy(
                    loading = false,
                    todayWords = dailyRes.getOrNull()?.words.orEmpty(),
                    overview = overviewRes.getOrElse { s.overview },
                    error = overviewRes.exceptionOrNull()?.message ?: dailyRes.exceptionOrNull()?.message
                )
            }
        }
    }

    fun submitSpeakingTranscript(transcript: String, durationSeconds: Int) {
        val wordIds = _speakingState.value.todayWords.map { it.id }
        viewModelScope.launch {
            _speakingState.update { it.copy(evaluating = true, error = null) }
            repo.submitSpeakingPractice(transcript, durationSeconds, wordIds)
                .onSuccess { session ->
                    _speakingState.update { s ->
                        s.copy(
                            evaluating = false,
                            overview = s.overview.copy(
                                aiConfigured = true,
                                sessions = listOf(session) + s.overview.sessions
                            )
                        )
                    }
                    loadDashboard()
                }
                .onFailure { err ->
                    val notConfigured = (err as? BackendApiException)?.code == "AI_NOT_CONFIGURED"
                    _speakingState.update { s ->
                        s.copy(
                            evaluating = false,
                            overview = if (notConfigured) s.overview.copy(aiConfigured = false) else s.overview,
                            error = err.message ?: "Evaluation failed."
                        )
                    }
                }
        }
    }

    // ---------- Analytics & Settings ----------

    fun loadProgressAnalytics() {
        viewModelScope.launch {
            _progressState.value = UiState.Loading
            repo.fetchProgressAnalytics()
                .onSuccess { _progressState.value = UiState.Success(it) }
                .onFailure { _progressState.value = UiState.Error(it.message ?: "Failed to load statistics.") }
        }
    }

    fun loadSettings() {
        viewModelScope.launch {
            _settingsState.value = UiState.Loading
            repo.fetchSettings()
                .onSuccess { _settingsState.value = UiState.Success(it) }
                .onFailure { _settingsState.value = UiState.Error(it.message ?: "Failed to load settings.") }
        }
    }

    fun saveSettings(settings: VocabSettings) {
        viewModelScope.launch {
            _feedback.value = VocabActionFeedback(busy = true)
            repo.saveSettings(settings)
                .onSuccess { saved ->
                    _settingsState.value = UiState.Success(saved)
                    _feedback.value = VocabActionFeedback(
                        message = "Daily goal (${saved.dailyGoal} words/day) saved to database."
                    )
                    loadDashboard()
                }
                .onFailure { err ->
                    _feedback.value = VocabActionFeedback(message = err.message ?: "Could not save settings.", isError = true)
                }
        }
    }
}
