package com.example.feature.pattern

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.PatternDetail
import com.example.data.repository.PatternRepository
import com.example.data.repository.PatternSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PatternListUiState(
    val isLoading: Boolean = true,
    val patterns: List<PatternSummary> = emptyList(),
    val errorMessage: String? = null
)

data class PatternDetailUiState(
    val selectedPatternId: String? = null,
    val isLoading: Boolean = false,
    val pattern: PatternDetail? = null,
    val errorMessage: String? = null
)

class PatternViewModel(
    private val repository: PatternRepository = PatternRepository()
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
            _listState.update { it.copy(isLoading = true, errorMessage = null) }
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

    fun selectPattern(patternId: String) {
        viewModelScope.launch {
            _detailState.update {
                PatternDetailUiState(
                    selectedPatternId = patternId,
                    isLoading = true,
                    pattern = null,
                    errorMessage = null
                )
            }
            repository.fetchPatternDetail(patternId)
                .onSuccess { detail ->
                    _detailState.update {
                        it.copy(
                            isLoading = false,
                            pattern = detail,
                            errorMessage = null
                        )
                    }
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
