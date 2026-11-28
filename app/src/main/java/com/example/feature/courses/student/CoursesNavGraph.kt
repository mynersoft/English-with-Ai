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
                }
            )
        }

        CourseStudentScreen.LESSON -> {
            StudentLessonViewerScreen(
                state = lessonState,
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
                }
            )
        }
    }
}
