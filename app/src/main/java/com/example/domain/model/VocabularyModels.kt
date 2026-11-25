package com.example.domain.model

data class VocabCategorySummary(
    val id: String,
    val name: String,
    val slug: String,
    val description: String = "",
    val icon: String = "menu_book",
    val parentId: String? = null,
    val wordCount: Int = 0,
    val totalWordCount: Int = 0,
    val isActive: Boolean = true,
    val subcategories: List<VocabCategorySummary> = emptyList()
)

data class VocabExample(
    val en: String,
    val bn: String
)

data class VocabWordProgress(
    val status: String = "new", // new | learning | review | mastered
    val isFavorite: Boolean = false,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val reviewCount: Int = 0,
    val intervalDays: Int = 0,
    val easeFactor: Double = 2.5,
    val repetition: Int = 0,
    val lastQuality: Int? = null,
    val nextReviewAt: String? = null,
    val learnedAt: String? = null,
    val isKnown: Boolean = false
)

data class VocabWord(
    val id: String,
    val word: String,
    val slug: String = "",
    val meaning: String,
    val pronunciation: String = "",
    val audioUrl: String? = null,
    val imageUrl: String? = null,
    val partOfSpeech: String = "noun",
    val level: String = "A1",
    val categoryId: String = "",
    val categoryName: String = "",
    val examples: List<VocabExample> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val difficulty: Int = 1,
    val isPublished: Boolean = true,
    val sortOrder: Int = 0,
    val progress: VocabWordProgress = VocabWordProgress()
)

data class VocabRecentActivity(
    val type: String,
    val title: String,
    val subtitle: String,
    val timestamp: String
)

data class VocabDashboardData(
    val greetingName: String = "Learner",
    val date: String = "",
    val timezone: String = "Asia/Dhaka",
    val dailyGoal: Int = 5,
    val newWordsDone: Int = 0,
    val isGoalCompleted: Boolean = false,
    val reviewedToday: Int = 0,
    val dueReviews: Int = 0,
    val totalLearned: Int = 0,
    val masteredCount: Int = 0,
    val streak: Int = 0,
    val categories: List<VocabCategorySummary> = emptyList(),
    val recentActivity: List<VocabRecentActivity> = emptyList(),
    val isFromOfflineCache: Boolean = false
)

data class DailyWordsData(
    val date: String = "",
    val timezone: String = "Asia/Dhaka",
    val dailyGoal: Int = 5,
    val newWordsDone: Int = 0,
    val isGoalCompleted: Boolean = false,
    val words: List<VocabWord> = emptyList()
)

data class PaginatedWordsData(
    val items: List<VocabWord> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0,
    val totalPages: Int = 1
)

data class ReviewCounts(
    val due: Int = 0,
    val difficult: Int = 0,
    val learning: Int = 0,
    val mastered: Int = 0
)

data class ReviewTabData(
    val tab: String = "due",
    val counts: ReviewCounts = ReviewCounts(),
    val items: List<VocabWord> = emptyList()
)

data class QuizMatchingLeft(
    val vocabularyId: String,
    val word: String,
    val pronunciation: String
)

data class QuizMatchingRight(
    val optionId: String,
    val meaning: String
)

data class QuizQuestionItem(
    val vocabularyId: String,
    val questionType: String, // en_to_bn | bn_to_en | fill_blank | listening | matching
    val prompt: String,
    val hint: String = "",
    val audioUrl: String? = null,
    val options: List<String> = emptyList(),
    val leftItems: List<QuizMatchingLeft> = emptyList(),
    val rightItems: List<QuizMatchingRight> = emptyList()
)

data class QuizSessionData(
    val quizType: String,
    val questions: List<QuizQuestionItem>
)

data class QuizSubmissionAnswer(
    val vocabularyId: String,
    val questionType: String,
    val selectedAnswer: String
)

data class QuizResultRow(
    val vocabularyId: String,
    val questionType: String,
    val word: String,
    val meaning: String,
    val selectedAnswer: String,
    val correctAnswer: String,
    val isCorrect: Boolean
)

data class QuizResultData(
    val attemptId: String,
    val quizType: String,
    val totalQuestions: Int,
    val correctAnswers: Int,
    val wrongAnswers: Int,
    val score: Int,
    val timeTaken: Int,
    val results: List<QuizResultRow>
)

data class SentenceCheckResult(
    val aiConfigured: Boolean,
    val isValidBasic: Boolean,
    val containsWord: Boolean,
    val isCorrect: Boolean = false,
    val correctedSentence: String = "",
    val feedbackBn: String = ""
)

data class VocabSpeakingSession(
    val id: String,
    val transcript: String,
    val duration: Int,
    val grammarScore: Int,
    val vocabularyScore: Int,
    val fluencyScore: Int,
    val feedback: String,
    val createdAt: String
)

data class VocabSpeakingOverview(
    val aiConfigured: Boolean = false,
    val sessions: List<VocabSpeakingSession> = emptyList()
)

data class WeeklyActivityPoint(
    val date: String,
    val newWords: Int,
    val reviewedWords: Int,
    val quizCount: Int,
    val speakingMinutes: Int,
    val isGoalCompleted: Boolean
)

data class VocabProgressAnalytics(
    val timezone: String = "Asia/Dhaka",
    val streak: Int = 0,
    val weeklyActivity: List<WeeklyActivityPoint> = emptyList(),
    val newCount: Int = 0,
    val learningCount: Int = 0,
    val reviewCount: Int = 0,
    val masteredCount: Int = 0,
    val totalQuizzes: Int = 0,
    val averageQuizScore: Int = 0,
    val quizAccuracyPercent: Int = 0,
    val totalSpeakingSessions: Int = 0,
    val avgGrammarScore: Int = 0,
    val avgVocabularyScore: Int = 0,
    val avgFluencyScore: Int = 0
)

data class VocabSettings(
    val dailyGoal: Int = 5,
    val preferredLevel: String = "A1",
    val preferredCategoryIds: List<String> = emptyList(),
    val autoPronounce: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderTime: String = "20:00",
    val timezone: String = "Asia/Dhaka"
)

data class CsvImportSummary(
    val totalRows: Int,
    val inserted: Int,
    val duplicates: Int,
    val errors: List<String>
)
