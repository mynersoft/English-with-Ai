package com.example.feature.reading

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.UiState
import com.example.core.ui.CelebrationEvent
import com.example.core.ui.CelebrationRules
import com.example.core.ui.CelebrationStyle
import com.example.core.ui.DailyGoalCelebrationTracker
import com.example.data.repository.ReadingRepository
import com.example.domain.model.ReadingDashboardData
import com.example.domain.model.ReadingDashboardStats
import com.example.domain.model.ReadingPassageSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReadingDashboardSection(val label: String) {
    DISCOVER("All Passages"),
    RECOMMENDED("Recommended"),
    RECENT("Recent"),
    HISTORY("History")
}

data class ReadingDashboardFilterState(
    val selectedLevel: String = "ALL",
    val selectedCategoryId: String = "",
    val searchQuery: String = "",
    val selectedSection: ReadingDashboardSection = ReadingDashboardSection.DISCOVER,
    val passages: List<ReadingPassageSummary> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalPassages: Int = 0,
    val isLoadingPassages: Boolean = false,
    val isLoadingMore: Boolean = false,
    val passagesError: String? = null
)

class ReadingDashboardViewModel(
    private val repository: ReadingRepository = ReadingRepository()
) : ViewModel() {

    private val _dashboardState = MutableStateFlow<UiState<ReadingDashboardData>>(UiState.Loading)
    val dashboardState: StateFlow<UiState<ReadingDashboardData>> = _dashboardState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _filterState = MutableStateFlow(ReadingDashboardFilterState())
    val filterState: StateFlow<ReadingDashboardFilterState> = _filterState.asStateFlow()

    private val _celebrationEvent = MutableStateFlow<CelebrationEvent?>(null)
    val celebrationEvent: StateFlow<CelebrationEvent?> = _celebrationEvent.asStateFlow()

    private var searchDebounceJob: Job? = null
    private var initialLevelApplied = false

    init {
        loadAll(silent = false)
    }

    fun dismissCelebration() {
        _celebrationEvent.value = null
    }

    fun checkDailyGoalReached(context: Context, stats: ReadingDashboardStats) {
        val celebrated = DailyGoalCelebrationTracker.celebratedReadingGoalToday(context)
        if (CelebrationRules.shouldCelebrateReadingDailyGoal(stats.todayMinutes, stats.dailyGoalMinutes, celebrated)) {
            DailyGoalCelebrationTracker.markReadingGoalCelebratedToday(context, stats.dailyGoalMinutes)
            _celebrationEvent.value = CelebrationEvent(
                title = "Daily Reading Goal Completed!",
                subtitle = "${stats.todayMinutes} / ${stats.dailyGoalMinutes} min read today!",
                style = CelebrationStyle.GOAL_BURST
            )
        }
    }

    fun loadAll(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent && _dashboardState.value !is UiState.Success) {
                _dashboardState.value = UiState.Loading
            } else {
                _isRefreshing.value = true
            }
            val result = repository.fetchDashboard()
            result.fold(
                onSuccess = { data ->
                    _dashboardState.value = UiState.Success(data)
                    if (!initialLevelApplied) {
                        initialLevelApplied = true
                        val pref = data.settings.preferredLevel
                        if (pref.isNotBlank() && pref != "ALL" && _filterState.value.selectedLevel == "ALL") {
                            _filterState.update { it.copy(selectedLevel = pref) }
                        }
                    }
                    loadPassages(page = 1, append = false)
                },
                onFailure = { err ->
                    if (_dashboardState.value !is UiState.Success) {
                        _dashboardState.value = UiState.Error(err.message ?: "Failed to load reading dashboard.")
                    }
                }
            )
            _isRefreshing.value = false
        }
    }

    fun selectSection(section: ReadingDashboardSection) {
        _filterState.update { it.copy(selectedSection = section) }
    }

    fun selectLevel(level: String) {
        if (_filterState.value.selectedLevel == level) return
        _filterState.update { it.copy(selectedLevel = level) }
        loadPassages(page = 1, append = false)
    }

    fun selectCategory(categoryId: String) {
        val next = if (_filterState.value.selectedCategoryId == categoryId) "" else categoryId
        _filterState.update { it.copy(selectedCategoryId = next) }
        loadPassages(page = 1, append = false)
    }

    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(320)
            loadPassages(page = 1, append = false)
        }
    }

    fun clearFilters() {
        _filterState.update {
            it.copy(selectedLevel = "ALL", selectedCategoryId = "", searchQuery = "")
        }
        loadPassages(page = 1, append = false)
    }

    fun loadMorePassages() {
        val cur = _filterState.value
        if (cur.isLoadingPassages || cur.isLoadingMore || cur.page >= cur.totalPages) return
        loadPassages(page = cur.page + 1, append = true)
    }

    fun loadPassages(page: Int = 1, append: Boolean = false) {
        viewModelScope.launch {
            val cur = _filterState.value
            _filterState.update {
                it.copy(
                    isLoadingPassages = !append,
                    isLoadingMore = append,
                    passagesError = null
                )
            }
            val result = repository.fetchPassages(
                page = page,
                limit = 15,
                level = cur.selectedLevel,
                categoryId = cur.selectedCategoryId,
                search = cur.searchQuery.trim()
            )
            result.fold(
                onSuccess = { paged ->
                    _filterState.update { state ->
                        val merged = if (append) {
                            (state.passages + paged.items).distinctBy { it.id }
                        } else {
                            paged.items
                        }
                        state.copy(
                            passages = merged,
                            page = paged.page,
                            totalPages = paged.totalPages,
                            totalPassages = paged.total,
                            isLoadingPassages = false,
                            isLoadingMore = false,
                            passagesError = null
                        )
                    }
                },
                onFailure = { err ->
                    _filterState.update {
                        it.copy(
                            isLoadingPassages = false,
                            isLoadingMore = false,
                            passagesError = err.message ?: "Failed to load passages."
                        )
                    }
                }
            )
        }
    }
}
