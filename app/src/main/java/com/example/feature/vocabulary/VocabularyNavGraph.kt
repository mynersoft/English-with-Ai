package com.example.feature.vocabulary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.common.UiState
import com.example.core.ui.CelebrationOverlay
import com.example.ui.theme.EnglishAITheme

private object VocabRoutes {
    const val DASHBOARD = "vocab_dashboard"
    const val CATEGORIES = "vocab_categories"
    const val BROWSER = "vocab_browser"
    const val WORD_DETAIL = "vocab_word_detail"
    const val FLASHCARDS = "vocab_flashcards"
    const val QUIZ = "vocab_quiz"
    const val SENTENCE_BUILDING = "vocab_sentence_building"
    const val REVIEW = "vocab_review"
    const val SPEAKING = "vocab_speaking"
    const val PROGRESS = "vocab_progress"
    const val SETTINGS = "vocab_settings"
    const val FAVORITES = "vocab_favorites"
    const val ADMIN = "vocab_admin"
}

@Composable
fun VocabularyRoute(
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    vm: VocabularyViewModel = viewModel()
) {
    val adminVm: VocabAdminViewModel = viewModel()
    val nav = rememberNavController()

    val dashboardState by vm.dashboardState.collectAsStateWithLifecycle()
    val categoriesState by vm.categoriesState.collectAsStateWithLifecycle()
    val wordsState by vm.wordsState.collectAsStateWithLifecycle()
    val dailyState by vm.dailyState.collectAsStateWithLifecycle()
    val deck by vm.wordDeck.collectAsStateWithLifecycle()
    val deckIndex by vm.currentDeckIndex.collectAsStateWithLifecycle()
    val reviewState by vm.reviewState.collectAsStateWithLifecycle()
    val selectedReviewTab by vm.selectedReviewTab.collectAsStateWithLifecycle()
    val quizState by vm.quizState.collectAsStateWithLifecycle()
    val sentenceState by vm.sentenceState.collectAsStateWithLifecycle()
    val speakingState by vm.speakingState.collectAsStateWithLifecycle()
    val progressState by vm.progressState.collectAsStateWithLifecycle()
    val settingsState by vm.settingsState.collectAsStateWithLifecycle()
    val favoritesState by vm.favoritesState.collectAsStateWithLifecycle()
    val isAdmin by vm.isAdmin.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val celebration by vm.celebrationEvent.collectAsStateWithLifecycle()
    val adminState by adminVm.state.collectAsStateWithLifecycle()

    val autoPronounce = (settingsState as? UiState.Success)?.data?.autoPronounce ?: true

    EnglishAITheme(darkTheme = false) {
        CompositionLocalProvider(LocalVocabDarkTheme provides false) {
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = nav,
                    startDestination = VocabRoutes.DASHBOARD
                ) {
        composable(VocabRoutes.DASHBOARD) {
            VocabularyDashboardScreen(
                dashboardState = dashboardState,
                isAdmin = isAdmin,
                feedback = feedback,
                bottomBar = bottomBar,
                onBack = onBack,
                onRetry = { vm.loadDashboard() },
                onContinueLearning = {
                    vm.clearFeedback()
                    vm.loadDailyWords(openDeck = true)
                    nav.navigate(VocabRoutes.WORD_DETAIL)
                },
                onStartReview = {
                    vm.clearFeedback()
                    vm.loadReviewTab("due")
                    nav.navigate(VocabRoutes.REVIEW)
                },
                onOpenFlashcards = {
                    vm.clearFeedback()
                    vm.loadDailyWords(openDeck = true)
                    nav.navigate(VocabRoutes.FLASHCARDS)
                },
                onOpenQuiz = {
                    vm.clearFeedback()
                    nav.navigate(VocabRoutes.QUIZ)
                },
                onOpenSentenceBuilding = {
                    vm.clearFeedback()
                    vm.loadSentencePracticeWords()
                    nav.navigate(VocabRoutes.SENTENCE_BUILDING)
                },
                onOpenSpeaking = {
                    vm.clearFeedback()
                    vm.loadSpeakingPractice()
                    nav.navigate(VocabRoutes.SPEAKING)
                },
                onOpenCategories = {
                    vm.loadCategories()
                    nav.navigate(VocabRoutes.CATEGORIES)
                },
                onOpenCategoryWords = { categoryId ->
                    nav.navigate("${VocabRoutes.BROWSER}?categoryId=$categoryId")
                },
                onOpenProgress = {
                    vm.loadProgressAnalytics()
                    nav.navigate(VocabRoutes.PROGRESS)
                },
                onOpenSettings = {
                    vm.clearFeedback()
                    vm.loadCategories()
                    vm.loadSettings()
                    nav.navigate(VocabRoutes.SETTINGS)
                },
                onOpenFavorites = {
                    nav.navigate(VocabRoutes.FAVORITES)
                },
                onOpenAdmin = {
                    adminVm.clearMessage()
                    nav.navigate(VocabRoutes.ADMIN)
                }
            )
        }

        composable(VocabRoutes.CATEGORIES) {
            CategoriesScreen(
                categoriesState = categoriesState,
                onBack = { nav.popBackStack() },
                onRetry = { vm.loadCategories() },
                onSelectCategory = { catId ->
                    nav.navigate("${VocabRoutes.BROWSER}?categoryId=$catId")
                }
            )
        }

        composable(
            route = "${VocabRoutes.BROWSER}?categoryId={categoryId}",
            arguments = listOf(
                navArgument("categoryId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val catId = entry.arguments?.getString("categoryId").orEmpty()
            WordBrowserScreen(
                title = "Browse Vocabulary",
                wordsState = wordsState,
                initialCategoryId = catId,
                onBack = { nav.popBackStack() },
                onLoadWords = { page, level, categoryId, search ->
                    vm.loadWords(page = page, level = level, categoryId = categoryId, search = search)
                },
                onOpenDeck = { words, idx ->
                    vm.clearFeedback()
                    vm.setDeckWords(words, idx)
                    nav.navigate(VocabRoutes.WORD_DETAIL)
                }
            )
        }

        composable(VocabRoutes.WORD_DETAIL) {
            WordDetailDeckScreen(
                dailyState = dailyState,
                deck = deck,
                currentIndex = deckIndex,
                autoPronounce = autoPronounce,
                feedback = feedback,
                onBack = { nav.popBackStack() },
                onRetry = { vm.loadDailyWords(openDeck = true) },
                onMove = { delta -> vm.moveDeck(delta) },
                onToggleBookmark = { id -> vm.toggleBookmark(id) },
                onKnowWord = { id, knows -> vm.markWordLearned(id, knows) }
            )
        }

        composable(VocabRoutes.FLASHCARDS) {
            FlashcardsScreen(
                dailyState = dailyState,
                deck = deck,
                currentIndex = deckIndex,
                feedback = feedback,
                onBack = { nav.popBackStack() },
                onRetry = { vm.loadDailyWords(openDeck = true) },
                onMove = { delta -> vm.moveDeck(delta) },
                onKnowWord = { id, knows -> vm.markWordLearned(id, knows) }
            )
        }

        composable(VocabRoutes.QUIZ) {
            VocabQuizScreen(
                quizState = quizState,
                onBack = { nav.popBackStack() },
                onStartQuiz = { type, count -> vm.startQuiz(quizType = type, count = count) },
                onSelectAnswer = { vid, opt -> vm.selectQuizAnswer(vid, opt) },
                onPairMatching = { vid, meaning -> vm.pairMatchingWord(vid, meaning) },
                onMoveQuestion = { delta -> vm.moveQuizQuestion(delta) },
                onSubmitQuiz = { vm.submitCurrentQuiz() }
            )
        }

        composable(VocabRoutes.SENTENCE_BUILDING) {
            VocabSentenceBuildingScreen(
                state = sentenceState,
                onBack = { nav.popBackStack() },
                onRetryLoad = { vm.loadSentencePracticeWords() },
                onMoveWord = { delta -> vm.moveSentenceWord(delta) },
                onCheckSentence = { vid, sentence -> vm.checkSentence(vid, sentence) }
            )
        }

        composable(VocabRoutes.REVIEW) {
            VocabReviewScreen(
                selectedTab = selectedReviewTab,
                reviewState = reviewState,
                feedback = feedback,
                onBack = { nav.popBackStack() },
                onSelectTab = { tab -> vm.loadReviewTab(tab) },
                onSubmitReview = { vid, q -> vm.submitWordReview(vid, q) }
            )
        }

        composable(VocabRoutes.SPEAKING) {
            VocabSpeakingPracticeScreen(
                state = speakingState,
                onBack = { nav.popBackStack() },
                onRetryLoad = { vm.loadSpeakingPractice() },
                onSubmitTranscript = { text, sec -> vm.submitSpeakingTranscript(text, sec) }
            )
        }

        composable(VocabRoutes.PROGRESS) {
            VocabProgressAnalyticsScreen(
                progressState = progressState,
                onBack = { nav.popBackStack() },
                onRetry = { vm.loadProgressAnalytics() }
            )
        }

        composable(VocabRoutes.SETTINGS) {
            VocabSettingsScreen(
                settingsState = settingsState,
                categoriesState = categoriesState,
                feedback = feedback,
                onBack = { nav.popBackStack() },
                onRetry = { vm.loadSettings() },
                onSave = { settings -> vm.saveSettings(settings) }
            )
        }

        composable(VocabRoutes.FAVORITES) {
            VocabFavoritesScreen(
                favoritesState = favoritesState,
                onBack = { nav.popBackStack() },
                onRetry = { vm.loadFavorites() },
                onOpenDeck = { words, idx ->
                    vm.setDeckWords(words, idx)
                    nav.navigate(VocabRoutes.WORD_DETAIL)
                },
                onToggleBookmark = { id -> vm.toggleBookmark(id) }
            )
        }

        composable(VocabRoutes.ADMIN) {
            VocabAdminScreen(
                isAdmin = isAdmin,
                state = adminState,
                onBack = { nav.popBackStack() },
                onInit = { adminVm.loadInitial() },
                onFilterChange = { search, level, cat -> adminVm.updateFilters(search, level, cat) },
                onPageChange = { p -> adminVm.loadWords(p) },
                onSaveWord = { word, done -> adminVm.saveWord(word, done) },
                onTogglePublish = { id, pub -> adminVm.togglePublish(id, pub) },
                onDeleteWord = { id -> adminVm.deleteWord(id) },
                onSaveCategory = { id, name, desc, parent, active -> adminVm.saveCategory(id, name, desc, parent, active) },
                onDeleteCategory = { id -> adminVm.deleteCategory(id) },
                onImportCsv = { csv -> adminVm.importCsv(csv) }
            )
        }
                }
                CelebrationOverlay(
                    event = celebration,
                    onDismiss = { vm.dismissCelebration() }
                )
            }
        }
    }
}
