package com.example.feature.courses.student

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class CourseStudentScreen {
    CATALOG,
    DETAIL,
    LESSON
}

@Composable
fun CoursesRoute(
    onBack: () -> Unit,
    vm: StudentCourseViewModel = viewModel()
) {
    val catalogState by vm.catalogState.collectAsStateWithLifecycle()
    val detailState by vm.detailState.collectAsStateWithLifecycle()
    val lessonState by vm.lessonState.collectAsStateWithLifecycle()
    val checkoutState by vm.checkoutState.collectAsStateWithLifecycle()

    var currentScreen by rememberSaveable { mutableStateOf(CourseStudentScreen.CATALOG) }
    var activeCourseId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeLessonId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.loadCatalog(1)
    }

    BackHandler {
        when (currentScreen) {
            CourseStudentScreen.LESSON -> {
                currentScreen = CourseStudentScreen.DETAIL
                activeCourseId?.let { vm.loadCourseDetail(it) }
            }
            CourseStudentScreen.DETAIL -> {
                currentScreen = CourseStudentScreen.CATALOG
                vm.loadCatalog(catalogState.pagination.page)
            }
            CourseStudentScreen.CATALOG -> onBack()
        }
    }

    when (currentScreen) {
        CourseStudentScreen.CATALOG -> {
            CourseCatalogScreen(
                state = catalogState,
                onBack = onBack,
                onRefresh = { vm.loadCatalog(catalogState.pagination.page) },
                onSearchChange = { vm.updateFilters(search = it) },
                onLevelChange = { vm.updateFilters(level = it) },
                onCategoryChange = { vm.updateFilters(category = it) },
                onOpenCourse = { courseId ->
                    activeCourseId = courseId
                    currentScreen = CourseStudentScreen.DETAIL
                    vm.loadCourseDetail(courseId)
                },
                onPageChange = { page -> vm.loadCatalog(page) }
            )
        }

        CourseStudentScreen.DETAIL -> {
            CourseDetailScreen(
                state = detailState,
                isStartingCheckout = checkoutState.isStartingCheckout,
                onBack = {
                    currentScreen = CourseStudentScreen.CATALOG
                    vm.loadCatalog(catalogState.pagination.page)
                },
                onRefresh = { activeCourseId?.let { vm.loadCourseDetail(it) } },
                onDownloadOffline = { activeCourseId?.let { vm.downloadCourseOffline(it) } },
                onOpenLesson = { lessonId ->
                    activeLessonId = lessonId
                    currentScreen = CourseStudentScreen.LESSON
                    vm.loadLesson(lessonId)
                },
                onPurchaseCourse = { cusPhone ->
                    val targetCourseId = detailState.course?.id ?: activeCourseId
                    if (!targetCourseId.isNullOrBlank()) {
                        vm.startCoursePurchase(targetCourseId, cusPhone)
                    }
                }
            )
        }

        CourseStudentScreen.LESSON -> {
            val lessonCourseId = lessonState.course?.id ?: activeCourseId
            StudentLessonViewerScreen(
                state = lessonState,
                isStartingCheckout = checkoutState.isStartingCheckout,
                onBack = {
                    currentScreen = CourseStudentScreen.DETAIL
                    activeCourseId?.let { vm.loadCourseDetail(it) }
                },
                onToggleComplete = { mark -> vm.toggleLessonComplete(mark) },
                onVideoProgress = { blockId, posSec, durSec ->
                    vm.recordVideoProgress(blockId, posSec, durSec)
                },
                onNavigateLesson = { nextOrPrevId ->
                    activeLessonId = nextOrPrevId
                    vm.loadLesson(nextOrPrevId)
                },
                onPurchaseCourse = lessonCourseId?.let { cid ->
                    { vm.startCoursePurchase(cid) }
                }
            )
        }
    }

    checkoutState.session?.let { session ->
        SslCommerzCheckoutDialog(
            session = session,
            isVerifying = checkoutState.isVerifyingPayment,
            errorMessage = checkoutState.error,
            onCallbackReached = { tranId, courseId ->
                vm.verifySslCommerzPayment(
                    tranId = tranId,
                    courseId = courseId,
                    activeLessonId = if (currentScreen == CourseStudentScreen.LESSON) activeLessonId else null,
                    closeOnTerminalOutcome = true
                )
            },
            onManualVerify = { tranId, courseId ->
                vm.verifySslCommerzPayment(
                    tranId = tranId,
                    courseId = courseId,
                    activeLessonId = if (currentScreen == CourseStudentScreen.LESSON) activeLessonId else null,
                    closeOnTerminalOutcome = false
                )
            },
            onDismiss = {
                vm.dismissCheckout(
                    activeLessonId = if (currentScreen == CourseStudentScreen.LESSON) activeLessonId else null
                )
            }
        )
    }
}
