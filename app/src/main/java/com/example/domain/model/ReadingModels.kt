package com.example.domain.model

enum class ReadingCefrLevel(val code: String, val label: String) {
    ALL("ALL", "All Levels"),
    A1("A1", "A1 · Beginner"),
    A2("A2", "A2 · Elementary"),
    B1("B1", "B1 · Intermediate"),
    B2("B2", "B2 · Upper-Int"),
    C1("C1", "C1 · Advanced"),
    C2("C2", "C2 · Proficient")
}

data class ReadingCategory(
    val id: String,
    val name: String,
    val slug: String = "",
    val description: String = "",
    val icon: String = "menu_book",
    val isActive: Boolean = true,
    val passageCount: Int = 0
)

data class ReadingSentence(
    val sentenceId: String,
    val order: Int,
    val en: String,
    val bn: String = ""
)

data class ReadingVocabItem(
    val word: String,
    val meaningBn: String = "",
    val pronunciation: String = "",
    val partOfSpeech: String = "",
    val example: String = ""
)

enum class ReadingQuestionType(val wire: String, val displayLabel: String) {
    MCQ("mcq", "Multiple Choice"),
    TRUE_FALSE("true_false", "True / False"),
    SHORT_ANSWER("short_answer", "Short Answer");

    companion object {
        fun fromWire(value: String): ReadingQuestionType =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: MCQ
    }
}

data class ReadingQuestion(
    val questionId: String,
    val type: ReadingQuestionType,
    val prompt: String,
    val options: List<String> = emptyList(),
    val correctAnswer: String = "",
    val explanationBn: String = ""
)

data class ReadingPassageSummary(
    val id: String,
    val title: String,
    val slug: String = "",
    val level: String = "A1",
    val categoryId: String = "",
    val categoryName: String = "",
    val summary: String = "",
    val estimatedMinutes: Int = 3,
    val sentenceCount: Int = 0,
    val vocabCount: Int = 0,
    val questionCount: Int = 0,
    val hasAudioUrl: Boolean = false,
    val isPublished: Boolean = true,
    val status: String = "not_started", // not_started | in_progress | completed
    val lastSentenceIndex: Int = 0,
    val progressPercent: Int = 0,
    val totalSeconds: Int = 0,
    val lastReadAt: String? = null,
    val completedAt: String? = null
)

data class ReadingPassageDetail(
    val summary: ReadingPassageSummary,
    val englishText: String,
    val bengaliTranslation: String = "",
    val audioUrl: String? = null,
    val sentences: List<ReadingSentence> = emptyList(),
    val vocabulary: List<ReadingVocabItem> = emptyList(),
    val questions: List<ReadingQuestion> = emptyList()
)

data class ReadingUserProgress(
    val status: String = "not_started",
    val lastSentenceIndex: Int = 0,
    val lastSentenceId: String = "",
    val readSentenceIds: List<String> = emptyList(),
    val progressPercent: Int = 0,
    val totalSeconds: Int = 0
)

data class ReadingAttemptAnswerResult(
    val questionId: String,
    val userAnswer: String,
    val isCorrect: Boolean,
    val correctAnswer: String,
    val explanationBn: String = ""
)

data class TextMatchingSummary(
    val matchedWords: Int = 0,
    val missingWords: Int = 0,
    val extraWords: Int = 0,
    val mismatchedWords: Int = 0
)

data class ReadingAttemptRecord(
    val id: String,
    val passageId: String = "",
    val passageTitle: String = "",
    val level: String = "A1",
    val mode: String = "comprehension",
    val scorePercent: Int = 0,
    val correctCount: Int = 0,
    val totalCount: Int = 0,
    val answers: List<ReadingAttemptAnswerResult> = emptyList(),
    val textMatchingSummary: TextMatchingSummary? = null,
    val createdAt: String = ""
)

data class ReadingWeeklyDay(
    val date: String,
    val dayNumber: String,
    val dayShort: String,
    val isToday: Boolean,
    val minutes: Int,
    val completedCount: Int,
    val isActive: Boolean
)

data class ReadingDashboardStats(
    val dailyGoalMinutes: Int = 15,
    val todayMinutes: Int = 0,
    val completedPassagesCount: Int = 0,
    val totalPublishedPassages: Int = 0,
    val streakDays: Int = 0,
    val longestStreak: Int = 0,
    val totalReadingMinutes: Int = 0
)

data class ReadingSettings(
    val preferredLevel: String = "ALL",
    val accent: String = "en-US",
    val voiceName: String = "",
    val defaultSpeed: Float = 1.0f,
    val showBengali: Boolean = true,
    val dailyGoalMinutes: Int = 15,
    val reminderEnabled: Boolean = false,
    val reminderTime: String = "20:00",
    val timezone: String = "Asia/Dhaka"
)

data class ReadingDashboardData(
    val stats: ReadingDashboardStats = ReadingDashboardStats(),
    val continueReading: ReadingPassageSummary? = null,
    val recent: List<ReadingPassageSummary> = emptyList(),
    val recommended: List<ReadingPassageSummary> = emptyList(),
    val history: List<ReadingAttemptRecord> = emptyList(),
    val weekRangeLabel: String = "",
    val weeklyActivity: List<ReadingWeeklyDay> = emptyList(),
    val categories: List<ReadingCategory> = emptyList(),
    val settings: ReadingSettings = ReadingSettings()
)

data class PaginatedReadingPassages(
    val items: List<ReadingPassageSummary> = emptyList(),
    val page: Int = 1,
    val limit: Int = 15,
    val total: Int = 0,
    val totalPages: Int = 1
)

data class ReadingPassageBundle(
    val passage: ReadingPassageDetail,
    val progress: ReadingUserProgress? = null,
    val attempts: List<ReadingAttemptRecord> = emptyList()
)

data class ReadingWordLookup(
    val word: String,
    val found: Boolean,
    val meaningBn: String? = null,
    val pronunciation: String = "",
    val partOfSpeech: String = "",
    val example: String = "",
    val existingVocabularyId: String? = null,
    val inUserVocabulary: Boolean = false
)

data class ReadingSaveWordResult(
    val saved: Boolean,
    val alreadyExisted: Boolean,
    val vocabularyId: String,
    val word: String
)

enum class WordMatchStatus {
    MATCHED,
    MISSING,
    EXTRA,
    MISMATCHED
}

data class WordMatchToken(
    val text: String,
    val status: WordMatchStatus,
    val expectedWord: String? = null
)

data class TextMatchComparisonResult(
    val expectedText: String,
    val spokenText: String,
    val matchedCount: Int,
    val missingWords: List<String>,
    val extraWords: List<String>,
    val mismatchedPairs: List<Pair<String, String>>,
    val expectedTokens: List<WordMatchToken>,
    val spokenTokens: List<WordMatchToken>,
    val matchPercent: Int
)

enum class ReadingPracticeMode(val label: String) {
    LISTEN_AND_READ("Listen & Read"),
    READ_ALOUD("Read Aloud"),
    READ_AND_UNDERSTAND("Read & Understand")
}
