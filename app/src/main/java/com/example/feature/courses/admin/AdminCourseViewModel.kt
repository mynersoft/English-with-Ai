package com.example.feature.courses.admin

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.CourseRepository
import com.example.domain.model.ContentBlockType
import com.example.domain.model.CourseDomainUtils
import com.example.domain.model.CourseLevel
import com.example.domain.model.CoursePagination
import com.example.domain.model.CourseSummary
import com.example.domain.model.LessonContentBlock
import com.example.domain.model.LessonNavLink
import com.example.domain.model.LessonSummary
import com.example.domain.model.ModuleWithLessons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AdminCourseListUiState(
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val courses: List<CourseSummary> = emptyList(),
    val categories: List<String> = emptyList(),
    val pagination: CoursePagination = CoursePagination(),
    val searchQuery: String = "",
    val levelFilter: String = "ALL",
    val publishedFilter: String = "all",
    val error: String? = null,
    val dialogError: String? = null,
    val message: String? = null
)

data class AdminCourseDetailUiState(
    val isOpen: Boolean = false,
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val course: CourseSummary? = null,
    val modules: List<ModuleWithLessons> = emptyList(),
    val isPreviewCourseOpen: Boolean = false,
    val error: String? = null
)

data class AdminLessonEditorUiState(
    val isOpen: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isUploadingMedia: Boolean = false,
    val lessonId: String? = null,
    val courseId: String = "",
    val moduleId: String = "",
    val moduleTitle: String = "",
    val title: String = "",
    val subtitle: String = "",
    val description: String = "",
    val estimatedDurationMinutes: String = "10",
    val isPremium: Boolean = false,
    val isPublished: Boolean = false,
    val contentBlocks: List<LessonContentBlock> = emptyList(),
    val previewOpen: Boolean = false,
    val previewBlock: LessonContentBlock? = null,
    val previewPrevious: LessonNavLink? = null,
    val previewNext: LessonNavLink? = null,
    val error: String? = null
)

class AdminCourseViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = CourseRepository(application)

    private val _listState = MutableStateFlow(AdminCourseListUiState())
    val listState: StateFlow<AdminCourseListUiState> = _listState.asStateFlow()

    private val _detailState = MutableStateFlow(AdminCourseDetailUiState())
    val detailState: StateFlow<AdminCourseDetailUiState> = _detailState.asStateFlow()

    private val _editorState = MutableStateFlow(AdminLessonEditorUiState())
    val editorState: StateFlow<AdminLessonEditorUiState> = _editorState.asStateFlow()

    fun loadInitial() {
        loadCourses(1)
    }

    fun clearBanner() {
        _listState.update { it.copy(error = null, message = null) }
        _detailState.update { it.copy(error = null) }
        _editorState.update { it.copy(error = null) }
    }

    fun clearDialogError() {
        _listState.update { it.copy(dialogError = null) }
    }

    fun updateFilters(search: String? = null, level: String? = null, published: String? = null) {
        _listState.update {
            it.copy(
                searchQuery = search ?: it.searchQuery,
                levelFilter = level ?: it.levelFilter,
                publishedFilter = published ?: it.publishedFilter
            )
        }
        loadCourses(1)
    }

    fun loadCourses(page: Int = _listState.value.pagination.page) {
        val s = _listState.value
        viewModelScope.launch {
            _listState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                repo.adminListCourses(
                    page = page,
                    limit = 20,
                    level = s.levelFilter,
                    published = s.publishedFilter,
                    search = s.searchQuery
                )
            }.onSuccess { res ->
                _listState.update {
                    it.copy(
                        isLoading = false,
                        courses = res.courses,
                        categories = res.categories,
                        pagination = res.pagination
                    )
                }
            }.onFailure { err ->
                _listState.update {
                    it.copy(isLoading = false, error = err.message ?: "Failed to load courses.")
                }
            }
        }
    }

    fun saveCourse(
        courseId: String?,
        title: String,
        subtitle: String,
        description: String,
        thumbnailUrl: String,
        thumbnailPublicId: String,
        level: CourseLevel,
        category: String,
        durationDays: Int,
        isPremium: Boolean,
        price: Double,
        isPublished: Boolean,
        initialModuleTitle: String = "",
        initialLessonTitle: String = "",
        initialVideoUrl: String = "",
        initialVideoPublicId: String = "",
        initialVideoDuration: Int = 0,
        initialPdfUrl: String = "",
        initialPdfPublicId: String = "",
        initialTextContent: String = "",
        openModulesAfterSave: Boolean = false,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _listState.update { it.copy(isBusy = true, error = null, dialogError = null) }
            runCatching {
                val saved = repo.adminSaveCourse(
                    courseId = courseId,
                    title = title,
                    subtitle = subtitle,
                    description = description,
                    thumbnailUrl = thumbnailUrl,
                    thumbnailPublicId = thumbnailPublicId,
                    level = level,
                    category = category,
                    durationDays = durationDays,
                    isPremium = isPremium,
                    price = price,
                    isPublished = isPublished
                )
                val cleanMod = initialModuleTitle.trim()
                val cleanLesson = initialLessonTitle.trim()
                val cleanVideoUrl = initialVideoUrl.trim()
                val cleanPdfUrl = initialPdfUrl.trim()
                val cleanText = initialTextContent.trim()
                val hasInitialContent = cleanVideoUrl.isNotBlank() || cleanPdfUrl.isNotBlank() || cleanText.isNotBlank()

                val createdModule = if (courseId == null && (cleanMod.isNotBlank() || cleanLesson.isNotBlank() || hasInitialContent)) {
                    repo.adminCreateModule(
                        courseId = saved.id,
                        title = cleanMod.ifBlank { "Module 1" },
                        description = "",
                        isPublished = saved.isPublished
                    )
                } else {
                    null
                }

                val createdLesson = if (createdModule != null && (cleanLesson.isNotBlank() || hasInitialContent)) {
                    val blocks = mutableListOf<LessonContentBlock>()
                    if (cleanVideoUrl.isNotBlank()) {
                        blocks.add(
                            LessonContentBlock(
                                id = "blk_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                                type = ContentBlockType.VIDEO,
                                title = "Lesson Video",
                                url = cleanVideoUrl,
                                publicId = initialVideoPublicId.trim(),
                                durationSeconds = initialVideoDuration.coerceAtLeast(0),
                                order = blocks.size + 1,
                                isDownloadable = false
                            )
                        )
                    }
                    if (cleanPdfUrl.isNotBlank()) {
                        blocks.add(
                            LessonContentBlock(
                                id = "blk_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                                type = ContentBlockType.PDF,
                                title = "Study Material PDF",
                                url = cleanPdfUrl,
                                publicId = initialPdfPublicId.trim(),
                                order = blocks.size + 1,
                                isDownloadable = true
                            )
                        )
                    }
                    if (cleanText.isNotBlank()) {
                        blocks.add(
                            LessonContentBlock(
                                id = "blk_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                                type = ContentBlockType.TEXT,
                                title = "Lesson Notes",
                                text = cleanText,
                                order = blocks.size + 1
                            )
                        )
                    }
                    repo.adminSaveLesson(
                        lessonId = null,
                        courseId = saved.id,
                        moduleId = createdModule.id,
                        title = cleanLesson.ifBlank { "Lesson 1" },
                        subtitle = "",
                        description = "",
                        estimatedDurationMinutes = 10,
                        isPremium = saved.isPremium,
                        isPublished = saved.isPublished,
                        content = CourseDomainUtils.normalizeBlockOrders(blocks)
                    )
                } else {
                    null
                }

                Triple(saved, createdModule, createdLesson)
            }.onSuccess { (saved, createdModule, createdLesson) ->
                _listState.update {
                    it.copy(
                        isBusy = false,
                        dialogError = null,
                        message = if (courseId == null) "Course created." else "Course updated."
                    )
                }
                if (_detailState.value.course?.id == saved.id) {
                    _detailState.update { it.copy(course = saved) }
                }
                loadCourses(_listState.value.pagination.page)
                onDone()

                if (courseId == null && openModulesAfterSave) {
                    openCourseDetail(saved.id)
                    if (createdLesson != null && createdModule != null) {
                        _editorState.value = AdminLessonEditorUiState(
                            isOpen = true,
                            lessonId = createdLesson.id,
                            courseId = saved.id,
                            moduleId = createdModule.id,
                            moduleTitle = createdModule.title,
                            title = createdLesson.title,
                            subtitle = createdLesson.subtitle,
                            description = createdLesson.description,
                            estimatedDurationMinutes = createdLesson.estimatedDurationMinutes.toString(),
                            isPremium = createdLesson.isPremium,
                            isPublished = createdLesson.isPublished,
                            contentBlocks = createdLesson.content.sortedBy { b -> b.order }
                        )
                    }
                }
            }.onFailure { err ->
                val msg = err.message ?: "Failed to save course."
                _listState.update {
                    it.copy(isBusy = false, error = msg, dialogError = msg)
                }
            }
        }
    }

    fun uploadThumbnailImage(uri: Uri, onUploaded: (url: String, publicId: String) -> Unit) {
        uploadDialogMedia(uri, "image") { url, publicId, _ ->
            onUploaded(url, publicId)
        }
    }

    fun uploadDialogMedia(
        uri: Uri,
        mediaType: String,
        onUploaded: (url: String, publicId: String, durationSeconds: Int) -> Unit
    ) {
        viewModelScope.launch {
            _listState.update { it.copy(isBusy = true, error = null, dialogError = null) }
            runCatching {
                repo.uploadCourseMediaFromUri(uri, mediaType)
            }.onSuccess { res ->
                val label = when (mediaType.lowercase()) {
                    "video" -> "Video uploaded."
                    "pdf" -> "PDF uploaded."
                    else -> "Image uploaded."
                }
                _listState.update { it.copy(isBusy = false, message = label) }
                onUploaded(res.url, res.publicId, res.durationSeconds)
            }.onFailure { err ->
                val msg = err.message ?: "${mediaType.uppercase()} upload failed."
                _listState.update {
                    it.copy(isBusy = false, error = msg, dialogError = msg)
                }
            }
        }
    }

    fun toggleCoursePublish(courseId: String, isPublished: Boolean) {
        viewModelScope.launch {
            _listState.update { it.copy(isBusy = true, error = null) }
            runCatching {
                repo.adminToggleCoursePublish(courseId, isPublished)
            }.onSuccess { updated ->
                _listState.update {
                    it.copy(
                        isBusy = false,
                        courses = it.courses.map { c -> if (c.id == updated.id) updated else c },
                        message = if (isPublished) "Course published." else "Course moved to draft."
                    )
                }
                if (_detailState.value.course?.id == updated.id) {
                    _detailState.update { it.copy(course = updated) }
                }
            }.onFailure { err ->
                _listState.update {
                    it.copy(isBusy = false, error = err.message ?: "Failed to update publish status.")
                }
            }
        }
    }

    fun deleteCourse(courseId: String) {
        viewModelScope.launch {
            _listState.update { it.copy(isBusy = true, error = null) }
            runCatching {
                repo.adminDeleteCourse(courseId)
            }.onSuccess {
                _listState.update {
                    it.copy(isBusy = false, message = "Course deleted.")
                }
                if (_detailState.value.course?.id == courseId) {
                    _detailState.update { AdminCourseDetailUiState() }
                }
                loadCourses(1)
            }.onFailure { err ->
                _listState.update {
                    it.copy(isBusy = false, error = err.message ?: "Failed to delete course.")
                }
            }
        }
    }

    fun openCourseDetail(courseId: String) {
        viewModelScope.launch {
            _detailState.update {
                it.copy(isOpen = true, isLoading = true, error = null)
            }
            runCatching {
                repo.adminGetCourseDetail(courseId)
            }.onSuccess { payload ->
                _detailState.update {
                    it.copy(
                        isOpen = true,
                        isLoading = false,
                        course = payload.course,
                        modules = payload.modules
                    )
                }
            }.onFailure { err ->
                _detailState.update {
                    it.copy(isLoading = false, error = err.message ?: "Failed to load course details.")
                }
            }
        }
    }

    fun closeCourseDetail() {
        _detailState.update { AdminCourseDetailUiState() }
        loadCourses(_listState.value.pagination.page)
    }

    fun setCoursePreviewOpen(open: Boolean) {
        _detailState.update { it.copy(isPreviewCourseOpen = open) }
    }

    fun saveModule(
        courseId: String,
        moduleId: String?,
        title: String,
        description: String,
        isPublished: Boolean,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _detailState.update { it.copy(isBusy = true, error = null) }
            runCatching {
                if (moduleId.isNullOrBlank()) {
                    repo.adminCreateModule(courseId, title, description, isPublished)
                } else {
                    repo.adminUpdateModule(moduleId, title, description, isPublished)
                }
            }.onSuccess {
                _detailState.update { it.copy(isBusy = false) }
                _listState.update {
                    it.copy(message = if (moduleId == null) "Module added." else "Module updated.")
                }
                openCourseDetail(courseId)
                onDone()
            }.onFailure { err ->
                _detailState.update {
                    it.copy(isBusy = false, error = err.message ?: "Failed to save module.")
                }
            }
        }
    }

    fun toggleModulePublish(moduleId: String, isPublished: Boolean) {
        val courseId = _detailState.value.course?.id ?: return
        viewModelScope.launch {
            _detailState.update { it.copy(isBusy = true, error = null) }
            runCatching {
                repo.adminUpdateModule(moduleId = moduleId, isPublished = isPublished)
            }.onSuccess {
                _detailState.update { it.copy(isBusy = false) }
                openCourseDetail(courseId)
            }.onFailure { err ->
                _detailState.update {
                    it.copy(isBusy = false, error = err.message ?: "Failed to update module status.")
                }
            }
        }
    }

    fun deleteModule(moduleId: String) {
        val courseId = _detailState.value.course?.id ?: return
        viewModelScope.launch {
            _detailState.update { it.copy(isBusy = true, error = null) }
            runCatching {
                repo.adminDeleteModule(moduleId)
            }.onSuccess {
                _detailState.update { it.copy(isBusy = false) }
                _listState.update { it.copy(message = "Module deleted.") }
                openCourseDetail(courseId)
            }.onFailure { err ->
                _detailState.update {
                    it.copy(isBusy = false, error = err.message ?: "Failed to delete module.")
                }
            }
        }
    }

    fun moveModule(fromIndex: Int, toIndex: Int) {
        val current = _detailState.value
        val courseId = current.course?.id ?: return
        val reordered = CourseDomainUtils.reorderList(current.modules, fromIndex, toIndex)
            .mapIndexed { idx, mod -> mod.copy(order = idx + 1) }
        _detailState.update { it.copy(modules = reordered) }

        viewModelScope.launch {
            runCatching {
                repo.adminReorderModules(courseId, reordered.map { it.id })
            }.onFailure { err ->
                _detailState.update {
                    it.copy(error = err.message ?: "Failed to reorder modules.")
                }
                openCourseDetail(courseId)
            }
        }
    }

    fun moveLesson(moduleId: String, fromIndex: Int, toIndex: Int) {
        val current = _detailState.value
        val mod = current.modules.firstOrNull { it.id == moduleId } ?: return
        val reorderedLessons = CourseDomainUtils.reorderList(mod.lessons, fromIndex, toIndex)
            .mapIndexed { idx, l -> l.copy(order = idx + 1) }

        _detailState.update { state ->
            state.copy(
                modules = state.modules.map { m ->
                    if (m.id == moduleId) m.copy(lessons = reorderedLessons) else m
                }
            )
        }

        viewModelScope.launch {
            runCatching {
                repo.adminReorderLessons(moduleId, reorderedLessons.map { it.id })
            }.onFailure { err ->
                _detailState.update {
                    it.copy(error = err.message ?: "Failed to reorder lessons.")
                }
                current.course?.id?.let { openCourseDetail(it) }
            }
        }
    }

    fun toggleLessonPublish(lessonId: String, isPublished: Boolean) {
        val courseId = _detailState.value.course?.id ?: return
        viewModelScope.launch {
            _detailState.update { it.copy(isBusy = true, error = null) }
            runCatching {
                repo.adminToggleLessonPublish(lessonId, isPublished)
            }.onSuccess {
                _detailState.update { it.copy(isBusy = false) }
                _listState.update {
                    it.copy(message = if (isPublished) "Lesson published." else "Lesson set to draft.")
                }
                openCourseDetail(courseId)
            }.onFailure { err ->
                _detailState.update {
                    it.copy(isBusy = false, error = err.message ?: "Failed to update lesson status.")
                }
            }
        }
    }

    fun duplicateLesson(lessonId: String) {
        val courseId = _detailState.value.course?.id ?: return
        viewModelScope.launch {
            _detailState.update { it.copy(isBusy = true, error = null) }
            runCatching {
                repo.adminDuplicateLesson(lessonId)
            }.onSuccess {
                _detailState.update { it.copy(isBusy = false) }
                _listState.update { it.copy(message = "Lesson duplicated as draft.") }
                openCourseDetail(courseId)
            }.onFailure { err ->
                _detailState.update {
                    it.copy(isBusy = false, error = err.message ?: "Failed to duplicate lesson.")
                }
            }
        }
    }

    fun deleteLesson(lessonId: String) {
        val courseId = _detailState.value.course?.id ?: return
        viewModelScope.launch {
            _detailState.update { it.copy(isBusy = true, error = null) }
            runCatching {
                repo.adminDeleteLesson(lessonId)
            }.onSuccess {
                _detailState.update { it.copy(isBusy = false) }
                _listState.update { it.copy(message = "Lesson deleted.") }
                openCourseDetail(courseId)
            }.onFailure { err ->
                _detailState.update {
                    it.copy(isBusy = false, error = err.message ?: "Failed to delete lesson.")
                }
            }
        }
    }

    /* ========================================================================
     * LESSON EDITOR & PREVIEW
     * ====================================================================== */

    fun openNewLesson(courseId: String, moduleId: String, moduleTitle: String) {
        _editorState.value = AdminLessonEditorUiState(
            isOpen = true,
            courseId = courseId,
            moduleId = moduleId,
            moduleTitle = moduleTitle,
            estimatedDurationMinutes = "10",
            isPremium = _detailState.value.course?.isPremium == true
        )
    }

    fun openEditLesson(lessonId: String, moduleTitle: String, openInPreviewDirectly: Boolean = false) {
        viewModelScope.launch {
            _editorState.value = AdminLessonEditorUiState(
                isOpen = true,
                isLoading = true,
                lessonId = lessonId,
                moduleTitle = moduleTitle,
                previewOpen = openInPreviewDirectly
            )
            runCatching {
                repo.adminGetLesson(lessonId)
            }.onSuccess { payload ->
                val l = payload.lesson
                _editorState.update {
                    it.copy(
                        isLoading = false,
                        lessonId = l.id,
                        courseId = l.courseId,
                        moduleId = l.moduleId,
                        moduleTitle = payload.module?.title ?: moduleTitle,
                        title = l.title,
                        subtitle = l.subtitle,
                        description = l.description,
                        estimatedDurationMinutes = l.estimatedDurationMinutes.toString(),
                        isPremium = l.isPremium,
                        isPublished = l.isPublished,
                        contentBlocks = l.content.sortedBy { b -> b.order },
                        previewPrevious = payload.previousLesson,
                        previewNext = payload.nextLesson,
                        previewOpen = openInPreviewDirectly
                    )
                }
            }.onFailure { err ->
                _editorState.update {
                    it.copy(isLoading = false, error = err.message ?: "Failed to load lesson.")
                }
            }
        }
    }

    fun closeLessonEditor() {
        _editorState.value = AdminLessonEditorUiState()
    }

    fun updateLessonFields(
        title: String? = null,
        subtitle: String? = null,
        description: String? = null,
        duration: String? = null,
        isPremium: Boolean? = null,
        isPublished: Boolean? = null
    ) {
        _editorState.update {
            it.copy(
                title = title ?: it.title,
                subtitle = subtitle ?: it.subtitle,
                description = description ?: it.description,
                estimatedDurationMinutes = duration ?: it.estimatedDurationMinutes,
                isPremium = isPremium ?: it.isPremium,
                isPublished = isPublished ?: it.isPublished
            )
        }
    }

    fun createEmptyBlock(type: ContentBlockType): LessonContentBlock {
        val nextOrder = _editorState.value.contentBlocks.size + 1
        return LessonContentBlock(
            id = "blk_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            type = type,
            title = when (type) {
                ContentBlockType.VIDEO -> "Lesson Video"
                ContentBlockType.PDF -> "Study Material PDF"
                ContentBlockType.TEXT -> "Lesson Notes"
                ContentBlockType.IMAGE -> "Visual Aid"
            },
            order = nextOrder
        )
    }

    fun upsertBlock(block: LessonContentBlock) {
        _editorState.update { state ->
            val exists = state.contentBlocks.any { it.id == block.id }
            val updated = if (exists) {
                state.contentBlocks.map { if (it.id == block.id) block else it }
            } else {
                state.contentBlocks + block.copy(order = state.contentBlocks.size + 1)
            }
            state.copy(contentBlocks = CourseDomainUtils.normalizeBlockOrders(updated))
        }
    }

    fun removeBlock(blockId: String) {
        _editorState.update { state ->
            val updated = state.contentBlocks.filterNot { it.id == blockId }
            state.copy(contentBlocks = CourseDomainUtils.normalizeBlockOrders(updated))
        }
    }

    fun reorderContentBlocks(fromIndex: Int, toIndex: Int) {
        _editorState.update { state ->
            val reordered = CourseDomainUtils.reorderList(state.contentBlocks, fromIndex, toIndex)
            state.copy(contentBlocks = CourseDomainUtils.normalizeBlockOrders(reordered))
        }
    }

    fun uploadBlockMedia(
        uri: Uri,
        mediaType: String,
        onUploaded: (url: String, publicId: String, durationSeconds: Int) -> Unit
    ) {
        viewModelScope.launch {
            _editorState.update { it.copy(isUploadingMedia = true, error = null) }
            runCatching {
                repo.uploadCourseMediaFromUri(uri, mediaType)
            }.onSuccess { res ->
                _editorState.update { it.copy(isUploadingMedia = false) }
                onUploaded(res.url, res.publicId, res.durationSeconds)
            }.onFailure { err ->
                _editorState.update {
                    it.copy(
                        isUploadingMedia = false,
                        error = err.message ?: "Media upload failed."
                    )
                }
            }
        }
    }

    fun setFullPreviewOpen(open: Boolean) {
        _editorState.update { it.copy(previewOpen = open) }
    }

    fun setBlockPreview(block: LessonContentBlock?) {
        _editorState.update { it.copy(previewBlock = block) }
    }

    fun buildInMemoryPreviewLesson(): LessonSummary {
        val e = _editorState.value
        val ordered = CourseDomainUtils.normalizeBlockOrders(e.contentBlocks)
        return LessonSummary(
            id = e.lessonId ?: "draft_preview",
            courseId = e.courseId,
            moduleId = e.moduleId,
            title = e.title.ifBlank { "Untitled Draft Lesson" },
            subtitle = e.subtitle,
            description = e.description,
            order = 1,
            estimatedDurationMinutes = e.estimatedDurationMinutes.toIntOrNull()?.coerceIn(1, 600) ?: 10,
            isPremium = e.isPremium,
            isPublished = e.isPublished,
            isLocked = false,
            contentCount = ordered.size,
            contentTypes = ordered.map { it.type }.distinct(),
            content = ordered
        )
    }

    fun saveLesson(publish: Boolean, closeOnSuccess: Boolean = true) {
        val e = _editorState.value
        if (e.title.trim().length < 2) {
            _editorState.update { it.copy(error = "Lesson title must be at least 2 characters.") }
            return
        }
        val duration = e.estimatedDurationMinutes.toIntOrNull()?.coerceIn(1, 600) ?: 10
        val normalizedBlocks = CourseDomainUtils.normalizeBlockOrders(e.contentBlocks)

        viewModelScope.launch {
            _editorState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                repo.adminSaveLesson(
                    lessonId = e.lessonId,
                    courseId = e.courseId,
                    moduleId = e.moduleId,
                    title = e.title,
                    subtitle = e.subtitle,
                    description = e.description,
                    estimatedDurationMinutes = duration,
                    isPremium = e.isPremium,
                    isPublished = publish,
                    content = normalizedBlocks
                )
            }.onSuccess { saved ->
                _listState.update {
                    it.copy(message = if (publish) "Lesson published." else "Lesson saved as draft.")
                }
                if (closeOnSuccess) {
                    _editorState.value = AdminLessonEditorUiState()
                } else {
                    _editorState.update {
                        it.copy(
                            isSaving = false,
                            lessonId = saved.id,
                            isPublished = saved.isPublished,
                            contentBlocks = saved.content
                        )
                    }
                }
                openCourseDetail(e.courseId)
            }.onFailure { err ->
                _editorState.update {
                    it.copy(isSaving = false, error = err.message ?: "Failed to save lesson.")
                }
            }
        }
    }
}
