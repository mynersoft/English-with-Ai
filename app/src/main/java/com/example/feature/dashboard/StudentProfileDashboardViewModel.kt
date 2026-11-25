package com.example.feature.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.UiState
import com.example.data.repository.StudentDashboardRepository
import com.example.domain.model.StudentDashboardData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StudentProfileDashboardViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: StudentDashboardRepository = StudentDashboardRepository(application)

    private val _state = MutableStateFlow<UiState<StudentDashboardData>>(UiState.Loading)
    val state: StateFlow<UiState<StudentDashboardData>> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadDashboard()
    }

    fun loadDashboard(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent && _state.value !is UiState.Success) {
                _state.value = UiState.Loading
            } else {
                _isRefreshing.value = true
            }
            repository.fetchStudentDashboard()
                .onSuccess { data ->
                    _state.value = UiState.Success(data)
                    _isRefreshing.value = false
                }
                .onFailure { err ->
                    _isRefreshing.value = false
                    if (!silent || _state.value !is UiState.Success) {
                        _state.value = UiState.Error(
                            err.message ?: "Could not load student progress from server."
                        )
                    }
                }
        }
    }
}
