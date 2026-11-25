package com.example.feature.reading

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ReadingRepository
import com.example.domain.model.ReadingAttemptRecord
import com.example.domain.model.ReadingPassageBundle
import com.example.domain.model.ReadingPassageDetail
import com.example.domain.model.ReadingPracticeMode
import com.example.domain.model.ReadingUserProgress
import com.example.domain.model.ReadingWordLookup
import com.example.domain.model.TextMatchComparisonResult
import com.example.feature.reading.audio.AudioManager
import com.example.feature.reading.audio.AudioManagerState
import com.example.feature.reading.audio.AudioProviderConfig
import com.example.feature.reading.audio.ProviderRegistry
import com.example.feature.reading.audio.TextMatchComparator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class WordPopupState(
    val isVisible: Boolean = false,
    val word: String = "",
    val isLoading: Boolean = false,
    val lookup: ReadingWordLookup? = null,
    val isSaving: Boolean = false,
    val savedDone: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

data class ReadAloudUiState(
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val comparison: TextMatchComparisonResult? = null,
    val micStatusMessage: String? = null,
    val isSavingAttempt: Boolean = false,
    val savedAttemptMessage: String? = null
)

data class QuizUiState(
    val selectedAnswers: Map<String, String> = emptyMap(),
    val isSubmitting: Boolean = false,
    val latestAttempt: ReadingAttemptRecord? = null,
    val submitError: String? = null
)

data class PassageReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val passage: ReadingPassageDetail? = null,
    val progress: ReadingUserProgress? = null,
    val attempts: List<ReadingAttemptRecord> = emptyList(),
    val mode: ReadingPracticeMode = ReadingPracticeMode.LISTEN_AND_READ,
    val showBengali: Boolean = true,
    val readSentenceIds: Set<String> = emptySet(),
    val lastSentenceIndex: Int = 0,
    val totalSessionSeconds: Int = 0,
    val isSavingProgress: Boolean = false,
    val progressMessage: String? = null,
    val wordPopup: WordPopupState = WordPopupState(),
    val readAloud: ReadAloudUiState = ReadAloudUiState(),
    val quiz: QuizUiState = QuizUiState()
)

class PassageReaderViewModel(
    application: Application,
    private val repository: ReadingRepository = ReadingRepository(),
    providerConfig: AudioProviderConfig = AudioProviderConfig()
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, ReadingRepository(), AudioProviderConfig())

    private val registry = ProviderRegistry(application, providerConfig)
    val audioManager = AudioManager(registry)

    val audioState: StateFlow<AudioManagerState> = audioManager.state

    private val _uiState = MutableStateFlow(PassageReaderUiState())
    val uiState: StateFlow<PassageReaderUiState> = _uiState.asStateFlow()

    private var activePassageId: String = ""
    private var periodicSaveJob: Job? = null
    private var unsavedSeconds = 0
    private var dirtySentenceState = false
    private var clientSequence = 0
    private var lastSavedReadCount = 0

    fun loadPassage(passageId: String) {
        if (passageId.isBlank()) return
        activePassageId = passageId
        unsavedSeconds = 0
        dirtySentenceState = false

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }

            val settingsResult = repository.fetchSettings()
            val bundleResult = repository.fetchPassageDetail(passageId)

            settingsResult.onSuccess { settings ->
                audioManager.applySettings(
                    accent = settings.accent,
                    voiceId = settings.voiceName,
                    speed = settings.defaultSpeed
                )
                _uiState.update { it.copy(showBengali = settings.showBengali) }
            }

            bundleResult.fold(
                onSuccess = { bundle: ReadingPassageBundle ->
                    val detail = bundle.passage
                    val prog = bundle.progress
                    val initialIdx = (prog?.lastSentenceIndex ?: 0)
                        .coerceIn(0, (detail.sentences.size - 1).coerceAtLeast(0))
                    val readIds = prog?.readSentenceIds?.toSet().orEmpty()
                    lastSavedReadCount = readIds.size

                    audioManager.setSentences(
                        sentences = detail.sentences,
                        mp3Url = detail.audioUrl,
                        initialIndex = initialIdx
                    ) { idx, sentence ->
                        markSentenceRead(idx, sentence.sentenceId)
                    }

                    val sttSupported = audioManager.state.value.sttSupported
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = null,
                            passage = detail,
                            progress = prog,
                            attempts = bundle.attempts,
                            readSentenceIds = readIds,
                            lastSentenceIndex = initialIdx,
                            totalSessionSeconds = prog?.totalSeconds ?: 0,
                            readAloud = it.readAloud.copy(
                                micStatusMessage = if (!sttSupported) {
                                    "Speech recognition is not supported on this device."
                                } else null
                            )
                        )
                    }
                    startPeriodicProgressLoop()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = err.message ?: "Failed to load passage."
                        )
                    }
                }
            )
        }
    }

    private fun startPeriodicProgressLoop() {
        periodicSaveJob?.cancel()
        periodicSaveJob = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                delay(1000L)
                unsavedSeconds++
                tick++
                _uiState.update { it.copy(totalSessionSeconds = it.totalSessionSeconds + 1) }
                if (tick >= 15) {
                    tick = 0
                    flushProgress(markCompleted = false)
                }
            }
        }
    }

    fun selectMode(mode: ReadingPracticeMode) {
        if (mode != ReadingPracticeMode.READ_ALOUD && audioState.value.isListening) {
            audioManager.cancelReadAloudListening()
        }
        _uiState.update { it.copy(mode = mode) }
    }

    fun toggleBengali() {
        _uiState.update { it.copy(showBengali = !it.showBengali) }
    }

    fun playSentence(index: Int) {
        val sentence = _uiState.value.passage?.sentences?.getOrNull(index) ?: return
        markSentenceRead(index, sentence.sentenceId)
        audioManager.playSentence(index)
    }

    fun playFullPassage() {
        val sentences = _uiState.value.passage?.sentences.orEmpty()
        val startIdx = audioState.value.activeSentenceIndex.coerceIn(0, (sentences.size - 1).coerceAtLeast(0))
        sentences.getOrNull(startIdx)?.let { markSentenceRead(startIdx, it.sentenceId) }
        audioManager.playFullPassage(startIdx)
    }

    fun playOptionalMp3() {
        audioManager.playOptionalMp3()
    }

    fun pauseAudio() = audioManager.pause()
    fun resumeAudio() = audioManager.resume()
    fun stopAudio() = audioManager.stop()
    fun replayAudio() = audioManager.replay()
    fun previousSentence() {
        val prev = (audioState.value.activeSentenceIndex - 1).coerceAtLeast(0)
        _uiState.value.passage?.sentences?.getOrNull(prev)?.let { markSentenceRead(prev, it.sentenceId) }
        audioManager.previousSentence()
    }

    fun nextSentence() {
        val sentences = _uiState.value.passage?.sentences.orEmpty()
        if (sentences.isEmpty()) return
        val next = (audioState.value.activeSentenceIndex + 1).coerceAtMost(sentences.lastIndex)
        sentences.getOrNull(next)?.let { markSentenceRead(next, it.sentenceId) }
        audioManager.nextSentence()
    }

    fun setSpeed(speed: Float) = audioManager.setSpeed(speed)

    fun setAccent(accent: String) {
        audioManager.setAccent(accent)
        viewModelScope.launch {
            repository.updateVoicePreferences(accent = accent, voiceName = "")
        }
    }

    fun selectVoice(voiceId: String) {
        audioManager.selectVoice(voiceId)
        val accent = audioState.value.accent
        viewModelScope.launch {
            repository.updateVoicePreferences(accent = accent, voiceName = voiceId)
        }
    }

    fun previewVoice(voiceId: String) {
        audioManager.previewVoice(voiceId)
        val accent = audioState.value.accent
        viewModelScope.launch {
            repository.updateVoicePreferences(accent = accent, voiceName = voiceId)
        }
    }

    private fun markSentenceRead(index: Int, sentenceId: String) {
        _uiState.update { state ->
            val updated = state.readSentenceIds + sentenceId
            if (updated.size != state.readSentenceIds.size || state.lastSentenceIndex != index) {
                dirtySentenceState = true
            }
            state.copy(
                lastSentenceIndex = index,
                readSentenceIds = updated
            )
        }
    }

    fun markPassageCompleted() {
        val allIds = _uiState.value.passage?.sentences?.map { it.sentenceId }.orEmpty()
        _uiState.update {
            dirtySentenceState = true
            it.copy(
                readSentenceIds = it.readSentenceIds + allIds,
                lastSentenceIndex = (allIds.size - 1).coerceAtLeast(0)
            )
        }
        flushProgress(markCompleted = true)
    }

    fun flushProgress(markCompleted: Boolean = false) {
        val passage = _uiState.value.passage ?: return
        if (activePassageId.isBlank()) return
        val delta = unsavedSeconds
        val readChanged = dirtySentenceState || _uiState.value.readSentenceIds.size != lastSavedReadCount
        if (delta <= 0 && !readChanged && !markCompleted) return

        val snapshotIdx = _uiState.value.lastSentenceIndex.coerceIn(0, (passage.sentences.size - 1).coerceAtLeast(0))
        val snapshotSentenceId = passage.sentences.getOrNull(snapshotIdx)?.sentenceId.orEmpty()
        val snapshotReadIds = _uiState.value.readSentenceIds.toList()
        unsavedSeconds = 0
        dirtySentenceState = false
        lastSavedReadCount = snapshotReadIds.size
        clientSequence += 1
        val seq = clientSequence

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingProgress = true) }
            val result = repository.saveProgress(
                passageId = passage.summary.id,
                lastSentenceIndex = snapshotIdx,
                lastSentenceId = snapshotSentenceId,
                readSentenceIds = snapshotReadIds,
                elapsedDeltaSeconds = delta,
                clientSequence = seq,
                markCompleted = markCompleted
            )
            result.fold(
                onSuccess = { saved ->
                    _uiState.update {
                        it.copy(
                            progress = saved,
                            isSavingProgress = false,
                            progressMessage = if (markCompleted) "Passage marked completed!" else null
                        )
                    }
                },
                onFailure = {
                    // Restore unsaved delta so next flush retries cleanly
                    unsavedSeconds += delta
                    dirtySentenceState = true
                    _uiState.update { it.copy(isSavingProgress = false) }
                }
            )
        }
    }

    fun clearProgressMessage() {
        _uiState.update { it.copy(progressMessage = null) }
    }

    // ---------- WORD POPUP ----------

    fun onWordTapped(rawWord: String) {
        val cleaned = TextMatchComparator.normalizeWord(rawWord)
        if (cleaned.isBlank()) return

        val passage = _uiState.value.passage
        val localMatch = passage?.vocabulary?.firstOrNull {
            TextMatchComparator.normalizeWord(it.word) == cleaned
        }

        _uiState.update {
            it.copy(
                wordPopup = WordPopupState(
                    isVisible = true,
                    word = cleaned,
                    isLoading = true,
                    lookup = localMatch?.let { v ->
                        ReadingWordLookup(
                            word = v.word,
                            found = v.meaningBn.isNotBlank(),
                            meaningBn = v.meaningBn.takeIf { m -> m.isNotBlank() },
                            pronunciation = v.pronunciation,
                            partOfSpeech = v.partOfSpeech,
                            example = v.example
                        )
                    }
                )
            )
        }

        viewModelScope.launch {
            val result = repository.lookupWord(cleaned, passage?.summary?.id)
            result.fold(
                onSuccess = { lookup ->
                    _uiState.update { state ->
                        if (state.wordPopup.word != cleaned) state
                        else state.copy(
                            wordPopup = state.wordPopup.copy(
                                isLoading = false,
                                lookup = lookup,
                                savedDone = lookup.inUserVocabulary
                            )
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { state ->
                        if (state.wordPopup.word != cleaned) state
                        else state.copy(
                            wordPopup = state.wordPopup.copy(
                                isLoading = false,
                                error = if (state.wordPopup.lookup == null) err.message else null
                            )
                        )
                    }
                }
            )
        }
    }

    fun dismissWordPopup() {
        _uiState.update { it.copy(wordPopup = WordPopupState()) }
    }

    fun listenPopupWord() {
        val word = _uiState.value.wordPopup.word
        if (word.isNotBlank()) audioManager.speakWord(word)
    }

    fun savePopupWordToVocabulary() {
        val popup = _uiState.value.wordPopup
        val passage = _uiState.value.passage
        if (popup.word.isBlank() || popup.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(wordPopup = it.wordPopup.copy(isSaving = true, error = null, message = null)) }
            val result = repository.saveWordToVocabulary(
                word = popup.word,
                passageId = passage?.summary?.id,
                meaningBn = popup.lookup?.meaningBn,
                pronunciation = popup.lookup?.pronunciation.orEmpty(),
                partOfSpeech = popup.lookup?.partOfSpeech.orEmpty(),
                example = popup.lookup?.example.orEmpty(),
                level = passage?.summary?.level ?: "A1"
            )
            result.fold(
                onSuccess = { res ->
                    _uiState.update {
                        it.copy(
                            wordPopup = it.wordPopup.copy(
                                isSaving = false,
                                savedDone = true,
                                message = if (res.alreadyExisted) "Already in your vocabulary" else "Saved to Vocabulary"
                            )
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            wordPopup = it.wordPopup.copy(
                                isSaving = false,
                                error = err.message ?: "Could not save word."
                            )
                        )
                    }
                }
            )
        }
    }

    // ---------- READ ALOUD (VOICE TEXT MATCHING) ----------

    fun startReadAloudMic() {
        val activeIdx = audioState.value.activeSentenceIndex
        val targetSentence = _uiState.value.passage?.sentences?.getOrNull(activeIdx)?.en.orEmpty()
        _uiState.update {
            it.copy(
                readAloud = it.readAloud.copy(
                    partialTranscript = "",
                    finalTranscript = "",
                    comparison = null,
                    micStatusMessage = null,
                    savedAttemptMessage = null
                )
            )
        }
        audioManager.startReadAloudListening(
            onPartial = { partial ->
                _uiState.update {
                    it.copy(readAloud = it.readAloud.copy(partialTranscript = partial))
                }
            },
            onFinal = { finalText ->
                val result = audioManager.assessTextMatch(targetSentence, finalText)
                _uiState.value.passage?.sentences?.getOrNull(activeIdx)?.let {
                    markSentenceRead(activeIdx, it.sentenceId)
                }
                _uiState.update {
                    it.copy(
                        readAloud = it.readAloud.copy(
                            partialTranscript = finalText,
                            finalTranscript = finalText,
                            comparison = result,
                            micStatusMessage = null
                        )
                    )
                }
                persistReadAloudAttempt(result)
            },
            onError = { err ->
                _uiState.update {
                    it.copy(
                        readAloud = it.readAloud.copy(
                            micStatusMessage = err
                        )
                    )
                }
            }
        )
    }

    fun stopReadAloudMic() {
        audioManager.stopReadAloudListening()
    }

    fun onMicPermissionDenied() {
        _uiState.update {
            it.copy(
                readAloud = it.readAloud.copy(
                    micStatusMessage = "Microphone permission is required to speak sentences aloud."
                )
            )
        }
    }

    private fun persistReadAloudAttempt(comp: TextMatchComparisonResult) {
        val passageId = _uiState.value.passage?.summary?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(readAloud = it.readAloud.copy(isSavingAttempt = true)) }
            val result = repository.submitReadAloudAttempt(
                passageId = passageId,
                matchedWords = comp.matchedCount,
                missingWords = comp.missingWords.size,
                extraWords = comp.extraWords.size,
                mismatchedWords = comp.mismatchedPairs.size
            )
            result.fold(
                onSuccess = { attempt ->
                    _uiState.update {
                        it.copy(
                            attempts = listOf(attempt) + it.attempts,
                            readAloud = it.readAloud.copy(
                                isSavingAttempt = false,
                                savedAttemptMessage = "Text Matching saved (${attempt.scorePercent}%)"
                            )
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(readAloud = it.readAloud.copy(isSavingAttempt = false)) }
                }
            )
        }
    }

    // ---------- READ & UNDERSTAND (COMPREHENSION QUIZ) ----------

    fun selectQuizAnswer(questionId: String, answer: String) {
        _uiState.update { state ->
            val next = state.quiz.selectedAnswers + (questionId to answer)
            state.copy(quiz = state.quiz.copy(selectedAnswers = next, submitError = null))
        }
    }

    fun submitQuiz() {
        val passage = _uiState.value.passage ?: return
        if (passage.questions.isEmpty()) return
        val answers = _uiState.value.quiz.selectedAnswers

        viewModelScope.launch {
            _uiState.update { it.copy(quiz = it.quiz.copy(isSubmitting = true, submitError = null)) }
            val result = repository.submitComprehensionAttempt(passage.summary.id, answers)
            result.fold(
                onSuccess = { attempt ->
                    _uiState.update {
                        it.copy(
                            attempts = listOf(attempt) + it.attempts,
                            quiz = it.quiz.copy(
                                isSubmitting = false,
                                latestAttempt = attempt,
                                submitError = null
                            )
                        )
                    }
                    flushProgress(markCompleted = false)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            quiz = it.quiz.copy(
                                isSubmitting = false,
                                submitError = err.message ?: "Failed to submit quiz."
                            )
                        )
                    }
                }
            )
        }
    }

    fun clearQuizFeedback() {
        _uiState.update {
            it.copy(quiz = it.quiz.copy(latestAttempt = null, selectedAnswers = emptyMap(), submitError = null))
        }
    }

    override fun onCleared() {
        periodicSaveJob?.cancel()
        audioManager.release()
        super.onCleared()
    }
}
