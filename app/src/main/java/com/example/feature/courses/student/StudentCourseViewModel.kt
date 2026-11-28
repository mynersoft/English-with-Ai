package com.example.feature.courses.student

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.BackendApiException
import com.example.data.repository.CourseRepository
import com.example.domain.model.CoursePagination
import com.example.domain.model.CourseSummary
import com.example.domain.model.LessonNavLink
import com.example.domain.model.LessonProgressModel
import com.example.domain.model.LessonSummary
import com.example.domain.model.ModuleWithLessons
import com.example.domain.model.SslCommerzCheckoutSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudentCourseCatalogUiState(
    val isLoading: Boolean = false,
    val courses: List<CourseSummary> = emptyList(),
    val categories: List<String> = emptyList(),
    val hasPremiumAccess: Boolean = false,
    val pagination: CoursePagination = CoursePagination(),
    val searchQuery: String = "",
    val levelFilter: String = "ALL",
    val categoryFilter: String = "",
    val error: String? = null
)

data class StudentCourseDetailUiState(
    val isLoading: Boolean = false,
    val isDownloadingOffline: Boolean = false,
    val course: CourseSummary? = null,
    val modules: List<ModuleWithLessons> = emptyList(),
    val hasPremiumAccess: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

data class StudentLessonViewerUiState(
    val isLoading: Boolean = false,
    val isSavingProgress: Boolean = false,
    val lesson: LessonSummary? = null,
    val course: CourseSummary? = null,
    val module: ModuleWithLessons? = null,
    val progress: LessonProgressModel? = null,
    val previousLesson: LessonNavLink? = null,
    val nextLesson: LessonNavLink? = null,
    val isLockedError: Boolean = false,
    val error: String? = null
)

data class CourseCheckoutUiState(
    val isStartingCheckout: Boolean = false,
    val isVerifyingPayment: Boolean = false,
    val session: SslCommerzCheckoutSession? = null,
    val error: String? = null
)

class StudentCourseViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = CourseRepository(application)

    private val _catalogState = MutableStateFlow(StudentCourseCatalogUiState())
    val catalogState: StateFlow<StudentCourseCatalogUiState> = _catalogState.asStateFlow()

    private val _detailState = MutableStateFlow(StudentCourseDetailUiState())
    val detailState: StateFlow<StudentCourseDetailUiState> = _detailState.asStateFlow()

    private val _lessonState = MutableStateFlow(StudentLessonViewerUiState())
    val lessonState: StateFlow<StudentLessonViewerUiState> = _lessonState.asStateFlow()

    private val _checkoutState = MutableStateFlow(CourseCheckoutUiState())
    val checkoutState: StateFlow<CourseCheckoutUiState> = _checkoutState.asStateFlow()

    fun loadCatalog(page: Int = 1) {
        val s = _catalogState.value
        viewModelScope.launch {
            _catalogState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                repo.getStudentCourses(
                    page = page,
                    limit = 20,
                    level = s.levelFilter,
                    category = s.categoryFilter,
                    search = s.searchQuery
                )
            }.onSuccess { res ->
                _catalogState.update {
                    it.copy(
                        isLoading = false,
                        courses = res.courses,
                        categories = res.categories,
                        hasPremiumAccess = res.hasPremiumAccess,
                        pagination = res.pagination
                    )
                }
            }.onFailure { err ->
                _catalogState.update {
                    it.copy(isLoading = false, error = err.message ?: "Unable to load courses.")
                }
            }
        }
    }

    fun updateFilters(search: String? = null, level: String? = null, category: String? = null) {
        _catalogState.update {
            it.copy(
                searchQuery = search ?: it.searchQuery,
                levelFilter = level ?: it.levelFilter,
                categoryFilter = category ?: it.categoryFilter
            )
        }
        loadCatalog(1)
    }

    fun loadCourseDetail(courseId: String) {
        viewModelScope.launch {
            _detailState.update { it.copy(isLoading = true, error = null, message = null) }
            runCatching {
                repo.getStudentCourseDetail(courseId)
            }.onSuccess { payload ->
                _detailState.update {
                    it.copy(
                        isLoading = false,
                        course = payload.course,
                        modules = payload.modules,
                        hasPremiumAccess = payload.hasPremiumAccess
                    )
                }
            }.onFailure { err ->
                _detailState.update {
                    it.copy(isLoading = false, error = err.message ?: "Unable to load course details.")
                }
            }
        }
    }

    fun clearDetailMessage() {
        _detailState.update { it.copy(message = null, error = null) }
    }

    fun downloadCourseOffline(courseId: String) {
        viewModelScope.launch {
            _detailState.update { it.copy(isDownloadingOffline = true, message = null, error = null) }
            runCatching {
                repo.downloadCourseForOffline(courseId)
            }.onSuccess { payload ->
                _detailState.update {
                    it.copy(
                        isDownloadingOffline = false,
                        course = payload.course,
                        modules = payload.modules,
                        message = "Course downloaded for offline study."
                    )
                }
            }.onFailure { err ->
                _detailState.update {
                    it.copy(
                        isDownloadingOffline = false,
                        error = err.message ?: "Offline download failed."
                    )
                }
            }
        }
    }

    fun loadLesson(lessonId: String) {
        viewModelScope.launch {
            _lessonState.update {
                it.copy(isLoading = true, isLockedError = false, error = null)
            }
            runCatching {
                repo.getLessonDetail(lessonId = lessonId, isPreview = false)
            }.onSuccess { payload ->
                _lessonState.update {
                    it.copy(
                        isLoading = false,
                        lesson = payload.lesson,
                        course = payload.course,
                        module = payload.module,
                        progress = payload.progress,
                        previousLesson = payload.previousLesson,
                        nextLesson = payload.nextLesson,
                        isLockedError = false
                    )
                }
            }.onFailure { err ->
                val locked = (err as? BackendApiException)?.httpStatus == 403 ||
                    (err as? BackendApiException)?.code == "PREMIUM_REQUIRED"
                _lessonState.update {
                    it.copy(
                        isLoading = false,
                        isLockedError = locked,
                        error = err.message ?: "Unable to open lesson."
                    )
                }
            }
        }
    }

    fun recordVideoProgress(blockId: String, positionSeconds: Int, durationSeconds: Int) {
        val lesson = _lessonState.value.lesson ?: return
        val pct = if (durationSeconds > 0) {
            ((positionSeconds.toFloat() / durationSeconds) * 100f).toInt().coerceIn(0, 100)
        } else {
            _lessonState.value.progress?.progressPercentage ?: 0
        }
        val autoComplete = durationSeconds > 0 && positionSeconds >= (durationSeconds * 0.9f)

        viewModelScope.launch {
            val updated = repo.saveLessonProgress(
                lessonId = lesson.id,
                courseId = lesson.courseId,
                moduleId = lesson.moduleId,
                isCompleted = if (autoComplete) true else null,
                progressPercentage = pct,
                lastContentId = blockId,
                lastPositionSeconds = positionSeconds
            )
            _lessonState.update { it.copy(progress = updated) }
        }
    }

    fun toggleLessonComplete(markComplete: Boolean) {
        val lesson = _lessonState.value.lesson ?: return
        viewModelScope.launch {
            _lessonState.update { it.copy(isSavingProgress = true) }
            val updated = repo.saveLessonProgress(
                lessonId = lesson.id,
                courseId = lesson.courseId,
                moduleId = lesson.moduleId,
                isCompleted = markComplete,
                progressPercentage = if (markComplete) 100 else 50,
                lastContentId = _lessonState.value.progress?.lastContentId,
                lastPositionSeconds = _lessonState.value.progress?.lastPositionSeconds
            )
            _lessonState.update { it.copy(isSavingProgress = false, progress = updated) }
            // Refresh course progress in background
            runCatching { repo.getStudentCourseDetail(lesson.courseId) }.onSuccess { payload ->
                _detailState.update {
                    it.copy(course = payload.course, modules = payload.modules)
                }
            }
        }
    }

    fun startCoursePurchase(courseId: String, cusPhone: String = "") {
        if (_checkoutState.value.isStartingCheckout) return
        viewModelScope.launch {
            _checkoutState.update { it.copy(isStartingCheckout = true, error = null) }
            _detailState.update { it.copy(error = null, message = null) }
            runCatching {
                repo.initCourseSslCommerzCheckout(courseId = courseId, cusPhone = cusPhone)
            }.onSuccess { session ->
                val urlLower = session.gatewayUrl.trim().lowercase()
                if (!urlLower.startsWith("https://") && !urlLower.startsWith("http://")) {
                    val msg = "Invalid payment gateway URL received."
                    _checkoutState.update { it.copy(isStartingCheckout = false, error = msg) }
                    _detailState.update { it.copy(error = msg) }
                    return@onSuccess
                }
                _checkoutState.update {
                    it.copy(
                        isStartingCheckout = false,
                        session = session,
                        error = null
                    )
                }
            }.onFailure { err ->
                val msg = err.message ?: "Unable to start SSLCOMMERZ payment."
                _checkoutState.update { it.copy(isStartingCheckout = false, error = msg) }
                _detailState.update { it.copy(error = msg) }
                if (_lessonState.value.isLockedError) {
                    _lessonState.update { it.copy(error = msg) }
                }
            }
        }
    }

    fun verifySslCommerzPayment(
        tranId: String,
        courseId: String,
        activeLessonId: String? = null,
        closeOnTerminalOutcome: Boolean = false
    ) {
        if (_checkoutState.value.isVerifyingPayment) return
        viewModelScope.launch {
            _checkoutState.update { it.copy(isVerifyingPayment = true, error = null) }
            runCatching {
                repo.getCoursePurchaseStatus(tranId)
            }.onSuccess { result ->
                if (result.status == "paid" || result.hasAccess) {
                    val unlockedMsg = result.message.ifBlank {
                        "Course unlocked! Payment verified via SSLCOMMERZ."
                    }
                    _checkoutState.update {
                        it.copy(isVerifyingPayment = false, session = null, error = null)
                    }
                    _detailState.update { it.copy(message = unlockedMsg, error = null) }
                    loadCourseDetail(courseId)
                    loadCatalog(_catalogState.value.pagination.page)
                    if (!activeLessonId.isNullOrBlank()) {
                        loadLesson(activeLessonId)
                    }
                } else if (
                    closeOnTerminalOutcome &&
                    result.status in listOf("cancelled", "failed", "validation_failed")
                ) {
                    val statusMsg = result.message.ifBlank { "Payment was not completed." }
                    _checkoutState.update {
                        it.copy(isVerifyingPayment = false, session = null, error = null)
                    }
                    _detailState.update { it.copy(error = statusMsg) }
                    if (!activeLessonId.isNullOrBlank() && _lessonState.value.isLockedError) {
                        _lessonState.update { it.copy(error = statusMsg) }
                    }
                } else {
                    val pendingMsg = result.message.ifBlank {
                        "Payment is still pending verification."
                    }
                    _checkoutState.update {
                        it.copy(isVerifyingPayment = false, error = pendingMsg)
                    }
                }
            }.onFailure { err ->
                _checkoutState.update {
                    it.copy(
                        isVerifyingPayment = false,
                        error = err.message ?: "Unable to verify payment status."
                    )
                }
            }
        }
    }

    fun dismissCheckout(activeLessonId: String? = null) {
        val currentSession = _checkoutState.value.session
        if (currentSession == null) {
            _checkoutState.update { it.copy(error = null) }
            return
        }
        viewModelScope.launch {
            _checkoutState.update { it.copy(isVerifyingPayment = true) }
            val statusResult = runCatching {
                repo.getCoursePurchaseStatus(currentSession.tranId)
            }.getOrNull()

            _checkoutState.update {
                it.copy(isVerifyingPayment = false, session = null, error = null)
            }

            if (statusResult != null && (statusResult.status == "paid" || statusResult.hasAccess)) {
                _detailState.update {
                    it.copy(
                        message = statusResult.message.ifBlank {
                            "Course unlocked! Payment verified via SSLCOMMERZ."
                        },
                        error = null
                    )
                }
                loadCourseDetail(currentSession.courseId)
                loadCatalog(_catalogState.value.pagination.page)
                if (!activeLessonId.isNullOrBlank()) {
                    loadLesson(activeLessonId)
                }
            }
        }
    }
}
