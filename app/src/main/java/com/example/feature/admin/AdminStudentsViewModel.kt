package com.example.feature.admin

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.network.toUserFriendlyBackendMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Immutable
data class AdminStudentsUiState(
    val loading: Boolean = false,
    val items: List<AdminStudentSummary> = emptyList(),
    val counts: AdminStudentCounts = AdminStudentCounts(),
    val page: Int = 1,
    val total: Int = 0,
    val totalPages: Int = 1,
    val searchQuery: String = "",
    val accountStatusFilter: String = "all",
    val subscriptionFilter: String = "all",
    val selectedStudentId: String? = null,
    val detailLoading: Boolean = false,
    val selectedDetail: AdminStudentDetail? = null,
    val updatingStatus: Boolean = false,
    val updatingSubscription: Boolean = false,
    val message: String? = null,
    val error: String? = null
) {
    val isViewingDetail: Boolean get() = selectedStudentId != null
}

object AdminStudentRules {
    val ACCOUNT_STATUS_OPTIONS = listOf("active", "suspended", "deactivated")
    val SUBSCRIPTION_STATUS_OPTIONS = listOf("free", "trial", "active", "expired", "cancelled")
    val SUBSCRIPTION_PLAN_OPTIONS = listOf("free", "monthly", "yearly", "lifetime")

    fun addDaysIso(nowMillis: Long, days: Int): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, days)
        }
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(Date(cal.timeInMillis))
    }

    fun formatShortDate(iso: String?): String {
        if (iso.isNullOrBlank()) return "Never"
        return iso.take(10)
    }

    fun normalizeStatusLabel(status: String): String = when (status.lowercase()) {
        "active" -> "Active"
        "suspended" -> "Suspended"
        "deactivated" -> "Deactivated"
        "trial" -> "Trial"
        "expired" -> "Expired"
        "cancelled" -> "Cancelled"
        "free" -> "Free"
        "monthly" -> "Monthly"
        "yearly" -> "Yearly"
        "lifetime" -> "Lifetime"
        else -> status.replaceFirstChar { it.uppercase() }
    }
}

class AdminStudentsViewModel : ViewModel() {

    private val _state = MutableStateFlow(AdminStudentsUiState())
    val state: StateFlow<AdminStudentsUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var loadedOnce = false

    fun loadInitial() {
        if (loadedOnce && _state.value.items.isNotEmpty()) return
        loadedOnce = true
        loadStudents(page = 1)
    }

    fun refresh() {
        loadStudents(page = _state.value.page)
        val openId = _state.value.selectedStudentId
        if (!openId.isNullOrBlank()) {
            openStudentDetail(openId)
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(320L)
            loadStudents(page = 1)
        }
    }

    fun setAccountStatusFilter(status: String) {
        if (_state.value.accountStatusFilter == status) return
        _state.update { it.copy(accountStatusFilter = status) }
        loadStudents(page = 1)
    }

    fun setSubscriptionFilter(subStatus: String) {
        if (_state.value.subscriptionFilter == subStatus) return
        _state.update { it.copy(subscriptionFilter = subStatus) }
        loadStudents(page = 1)
    }

    fun loadStudents(page: Int = 1) {
        val snap = _state.value
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            AdminStudentsApi.listStudents(
                page = page,
                limit = 20,
                search = snap.searchQuery,
                accountStatus = snap.accountStatusFilter,
                subscriptionStatus = snap.subscriptionFilter
            ).fold(
                onSuccess = { res ->
                    _state.update {
                        it.copy(
                            loading = false,
                            items = res.items,
                            counts = res.counts,
                            page = res.page,
                            total = res.total,
                            totalPages = res.totalPages,
                            error = null
                        )
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = err.toUserFriendlyBackendMessage("Failed to load students.")
                        )
                    }
                }
            )
        }
    }

    fun openStudentDetail(studentId: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    selectedStudentId = studentId,
                    detailLoading = true,
                    error = null
                )
            }
            AdminStudentsApi.getStudentDetail(studentId).fold(
                onSuccess = { detail ->
                    _state.update {
                        it.copy(
                            detailLoading = false,
                            selectedDetail = detail,
                            error = null
                        )
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            detailLoading = false,
                            error = err.toUserFriendlyBackendMessage("Failed to load student profile.")
                        )
                    }
                }
            )
        }
    }

    fun closeStudentDetail() {
        _state.update {
            it.copy(
                selectedStudentId = null,
                selectedDetail = null,
                detailLoading = false
            )
        }
    }

    fun updateAccountStatus(studentId: String, nextStatus: String) {
        if (_state.value.updatingStatus) return
        viewModelScope.launch {
            _state.update { it.copy(updatingStatus = true, error = null, message = null) }
            AdminStudentsApi.updateAccountStatus(studentId, nextStatus).fold(
                onSuccess = { updatedStudent ->
                    _state.update { cur ->
                        val nextDetail = cur.selectedDetail?.let { d ->
                            if (d.student.id == updatedStudent.id) {
                                d.copy(
                                    student = updatedStudent.copy(
                                        progressSummary = d.student.progressSummary
                                    )
                                )
                            } else d
                        }
                        val nextItems = cur.items.map { item ->
                            if (item.id == updatedStudent.id) {
                                updatedStudent.copy(progressSummary = item.progressSummary)
                            } else item
                        }
                        cur.copy(
                            updatingStatus = false,
                            items = nextItems,
                            selectedDetail = nextDetail,
                            message = "Account status updated to ${AdminStudentRules.normalizeStatusLabel(nextStatus)}"
                        )
                    }
                    loadStudents(page = _state.value.page)
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            updatingStatus = false,
                            error = err.toUserFriendlyBackendMessage("Could not update account status.")
                        )
                    }
                }
            )
        }
    }

    fun updateSubscription(
        studentId: String,
        subscriptionStatus: String,
        subscriptionPlan: String,
        subscriptionExpiresAt: String?
    ) {
        if (_state.value.updatingSubscription) return
        viewModelScope.launch {
            _state.update { it.copy(updatingSubscription = true, error = null, message = null) }
            AdminStudentsApi.updateSubscription(
                studentId = studentId,
                subscriptionStatus = subscriptionStatus,
                subscriptionPlan = subscriptionPlan,
                subscriptionExpiresAt = subscriptionExpiresAt
            ).fold(
                onSuccess = { updatedStudent ->
                    _state.update { cur ->
                        val nextDetail = cur.selectedDetail?.let { d ->
                            if (d.student.id == updatedStudent.id) {
                                d.copy(
                                    student = updatedStudent.copy(
                                        progressSummary = d.student.progressSummary
                                    )
                                )
                            } else d
                        }
                        val nextItems = cur.items.map { item ->
                            if (item.id == updatedStudent.id) {
                                updatedStudent.copy(progressSummary = item.progressSummary)
                            } else item
                        }
                        cur.copy(
                            updatingSubscription = false,
                            items = nextItems,
                            selectedDetail = nextDetail,
                            message = "Subscription updated to ${updatedStudent.subscriptionLabel}"
                        )
                    }
                    loadStudents(page = _state.value.page)
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            updatingSubscription = false,
                            error = err.toUserFriendlyBackendMessage("Could not update subscription status.")
                        )
                    }
                }
            )
        }
    }

    fun clearBanner() {
        _state.update { it.copy(message = null, error = null) }
    }
}
