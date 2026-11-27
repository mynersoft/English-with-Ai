package com.example.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.network.toUserFriendlyBackendMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminDashboardUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val data: AdminDashboardOverview? = null,
    val error: String? = null
)

class AdminDashboardViewModel : ViewModel() {

    private val _state = MutableStateFlow(AdminDashboardUiState())
    val state: StateFlow<AdminDashboardUiState> = _state.asStateFlow()

    fun loadDashboard(forceRefresh: Boolean = false) {
        val current = _state.value
        if (current.loading || current.refreshing) return
        if (!forceRefresh && current.data != null) return

        viewModelScope.launch {
            _state.update {
                if (it.data == null) {
                    it.copy(loading = true, error = null)
                } else {
                    it.copy(refreshing = true, error = null)
                }
            }

            AdminDashboardApi.fetchDashboard()
                .onSuccess { overview ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            data = overview,
                            error = null
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = err.toUserFriendlyBackendMessage("Could not load admin analytics right now.")
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
