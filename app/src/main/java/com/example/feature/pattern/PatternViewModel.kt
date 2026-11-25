package com.example.feature.pattern

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.PatternDetail
import com.example.data.repository.PatternRepository
import com.example.data.repository.PatternSummary
import com.example.data.repository.StudentDashboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PatternFilter(val label: String) {
    ALL("All"),
    TO_LEARN("To Learn"),
    LEARNED("Learned")
}

data class PatternListUiState(
    val isLoading: Boolean = true,
    val patterns: List<PatternSummary> = emptyList(),
    val filter: PatternFilter = PatternFilter.ALL,
    val togglingIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val actionError: String? = null
) {
    val learnedCount: Int
        get() = patterns.count { it.isCompleted }

    val unlearnedCount: Int
        get() = patterns.count { !it.isCompleted }

    val filteredPatterns: List<PatternSummary>
        get() = when (filter) {
            PatternFilter.ALL -> patterns
            PatternFilter.TO_LEARN -> patterns.filter { !it.isCompleted }
            PatternFilter.LEARNED -> patterns.filter { it.isCompleted }
        }
}

data class PatternDetailUiState(
    val selectedPatternId: String? = null,
    val isLoading: Boolean = false,
    val pattern: PatternDetail? = null,
    val isTogglingCompleted: Boolean = false,
    val errorMessage: String? = null,
    val actionError: String? = null
)

class PatternViewModel(
    private val repository: PatternRepository = PatternRepository(),
    private val studentRepo: StudentDashboardRepository = StudentDashboardRepository()
) : ViewModel() {

    private val _listState = MutableStateFlow(PatternListUiState())
    val listState: StateFlow<PatternListUiState> = _listState.asStateFlow()

    private val _detailState = MutableStateFlow(PatternDetailUiState())
    val detailState: StateFlow<PatternDetailUiState> = _detailState.asStateFlow()

    init {
        loadPatterns()
    }

    fun loadPatterns() {
        viewModelScope.launch {
            _listState.update { it.copy(isLoading = true, errorMessage = null, actionError = null) }
            repository.fetchPatterns()
                .onSuccess { items ->
                    _listState.update {
                        it.copy(
                            isLoading = false,
                            patterns = items,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { err ->
                    _listState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Failed to load patterns from database."
                        )
                    }
                }
        }
    }

    fun setFilter(filter: PatternFilter) {
        _listState.update { it.copy(filter = filter) }
    }

    fun clearActionError() {
        _listState.update { it.copy(actionError = null) }
        _detailState.update { it.copy(actionError = null) }
    }

    fun togglePatternCompleted(patternId: String, targetCompleted: Boolean? = null) {
        if (patternId in _listState.value.togglingIds) return
        val currentCompleted = _listState.value.patterns
            .firstOrNull { it.id == patternId }
            ?.isCompleted
            ?: _detailState.value.pattern?.takeIf { it.id == patternId }?.isCompleted
            ?: false
        val nextCompleted = targetCompleted ?: !currentCompleted

        // Optimistic update for responsive feedback + rollback on failure
        _listState.update { state ->
            state.copy(
                togglingIds = state.togglingIds + patternId,
                actionError = null,
                patterns = state.patterns.map {
                    if (it.id == patternId) it.copy(isCompleted = nextCompleted) else it
                }
            )
        }
        _detailState.update { detail ->
            val updatedPattern = detail.pattern?.let {
                if (it.id == patternId) it.copy(isCompleted = nextCompleted) else it
            }
            detail.copy(
                isTogglingCompleted = detail.selectedPatternId == patternId,
                actionError = null,
                pattern = updatedPattern
            )
        }

        viewModelScope.launch {
            repository.setPatternCompleted(patternId, nextCompleted)
                .onSuccess { confirmed ->
                    _listState.update { state ->
                        state.copy(
                            togglingIds = state.togglingIds - patternId,
                            patterns = state.patterns.map {
                                if (it.id == patternId) it.copy(isCompleted = confirmed) else it
                            }
                        )
                    }
                    _detailState.update { detail ->
                        detail.copy(
                            isTogglingCompleted = false,
                            pattern = detail.pattern?.let {
                                if (it.id == patternId) it.copy(isCompleted = confirmed) else it
                            }
                        )
                    }
                }
                .onFailure { err ->
                    val message = err.message ?: "Could not save learned status."
                    _listState.update { state ->
                        state.copy(
                            togglingIds = state.togglingIds - patternId,
                            actionError = message,
                            patterns = state.patterns.map {
                                if (it.id == patternId) it.copy(isCompleted = currentCompleted) else it
                            }
                        )
                    }
                    _detailState.update { detail ->
                        detail.copy(
                            isTogglingCompleted = false,
                            actionError = message,
                            pattern = detail.pattern?.let {
                                if (it.id == patternId) it.copy(isCompleted = currentCompleted) else it
                            }
                        )
                    }
                }
        }
    }

    fun selectPattern(patternId: String) {
        viewModelScope.launch {
            val cachedCompleted = _listState.value.patterns
                .firstOrNull { it.id == patternId }
                ?.isCompleted
                ?: false
            val isToggleInFlight = patternId in _listState.value.togglingIds
            _detailState.update {
                PatternDetailUiState(
                    selectedPatternId = patternId,
                    isLoading = true,
                    pattern = null,
                    isTogglingCompleted = isToggleInFlight,
                    errorMessage = null
                )
            }
            repository.fetchPatternDetail(patternId)
                .onSuccess { detail ->
                    val resolvedDetail = detail.copy(
                        isCompleted = if (patternId in _listState.value.togglingIds) {
                            cachedCompleted
                        } else {
                            detail.isCompleted
                        }
                    )
                    _detailState.update {
                        it.copy(
                            isLoading = false,
                            pattern = resolvedDetail,
                            errorMessage = null
                        )
                    }
                    launch { studentRepo.recordPatternPractice(patternId) }
                }
                .onFailure { err ->
                    _detailState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Failed to load pattern details."
                        )
                    }
                }
        }
    }

    fun clearSelectedPattern() {
        _detailState.value = PatternDetailUiState()
    }
}
