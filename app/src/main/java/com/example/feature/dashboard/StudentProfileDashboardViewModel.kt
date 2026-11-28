package com.example.feature.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.UiState
import com.example.data.repository.CourseRepository
import com.example.data.repository.StudentDashboardRepository
import com.example.domain.model.PurchasedCourseItem
import com.example.domain.model.RecommendedCourseItem
import com.example.domain.model.StudentDashboardData
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StudentProfileDashboardViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: StudentDashboardRepository = StudentDashboardRepository(application)
    private val courseRepository: CourseRepository = CourseRepository(application)

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
            val dashboardDeferred = async { repository.fetchStudentDashboard() }
            val catalogDeferred = async { runCatching { courseRepository.getStudentCourses(page = 1, limit = 12) }.getOrNull() }

            val dashResult = dashboardDeferred.await()
            val catalog = catalogDeferred.await()

            dashResult
                .onSuccess { data ->
                    val enriched = enrichCoursesIfNeeded(data, catalog)
                    _state.value = UiState.Success(enriched)
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

    private fun enrichCoursesIfNeeded(
        data: StudentDashboardData,
        catalog: com.example.domain.model.StudentCourseListResponse?
    ): StudentDashboardData {
        if (catalog == null) return data

        val purchased = if (data.purchasedCourses.isNotEmpty()) {
            data.purchasedCourses
        } else {
            catalog.courses
                .filter { it.isPurchased || (it.isPremium && it.hasAccess) }
                .map { c ->
                    PurchasedCourseItem(
                        courseId = c.id,
                        title = c.title,
                        subtitle = c.subtitle,
                        level = c.level.label,
                        category = c.category,
                        price = c.price,
                        totalLessons = c.totalLessons,
                        completedLessons = c.completedLessons,
                        completionPercent = c.completionPercentage
                    )
                }
        }

        val purchasedIds = purchased.map { it.courseId }.toSet()
        val recommended = if (data.recommendedCourses.isNotEmpty()) {
            data.recommendedCourses
        } else {
            catalog.courses
                .filter { !purchasedIds.contains(it.id) }
                .sortedByDescending { it.isPremium }
                .take(3)
                .map { c ->
                    RecommendedCourseItem(
                        courseId = c.id,
                        title = c.title,
                        subtitle = c.subtitle,
                        level = c.level.label,
                        category = c.category,
                        isPremium = c.isPremium,
                        price = c.price,
                        totalLessons = c.totalLessons,
                        durationDays = c.durationDays
                    )
                }
        }

        val resolvedPlan = if (
            data.user.planLabel.equals("Free", ignoreCase = true) &&
            (catalog.hasPremiumAccess || purchased.isNotEmpty())
        ) {
            "Premium"
        } else {
            data.user.planLabel
        }

        return data.copy(
            user = data.user.copy(planLabel = resolvedPlan),
            stats = data.stats.copy(purchasedCoursesCount = maxOf(data.stats.purchasedCoursesCount, purchased.size)),
            purchasedCourses = purchased,
            recommendedCourses = recommended
        )
    }
}
