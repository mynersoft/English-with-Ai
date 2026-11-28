package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.BuildConfig
import com.example.core.network.NetworkClient
import com.example.data.local.CachedCourseEntity
import com.example.data.local.CachedLessonEntity
import com.example.data.local.CachedLessonProgressEntity
import com.example.data.local.CachedModuleEntity
import com.example.data.local.CourseMediaFileCache
import com.example.data.local.CourseOfflineDatabase
import com.example.domain.model.AdminCourseListResponse
import com.example.domain.model.ContentBlockType
import com.example.domain.model.CourseDetailPayload
import com.example.domain.model.CourseDomainUtils
import com.example.domain.model.CourseJsonMapper
import com.example.domain.model.CourseLevel
import com.example.domain.model.CoursePagination
import com.example.domain.model.CourseSummary
import com.example.domain.model.EnrollmentStatus
import com.example.domain.model.LessonContentBlock
import com.example.domain.model.LessonNavLink
import com.example.domain.model.LessonProgressModel
import com.example.domain.model.LessonSummary
import com.example.domain.model.LessonViewerPayload
import com.example.domain.model.ModuleWithLessons
import com.example.domain.model.StudentCourseListResponse
import com.example.domain.model.UploadedMediaResult
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class CourseRepository(private val context: Context) {
    private val appContext = context.applicationContext
    private val dao by lazy { CourseOfflineDatabase.getInstance(appContext).courseDao() }

    private fun currentUid(): String = Firebase.auth.currentUser?.uid ?: "guest"

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /* ========================================================================
     * ADMIN: COURSES, MODULES, LESSONS & MEDIA UPLOAD
     * ====================================================================== */

    suspend fun adminListCourses(
        page: Int = 1,
        limit: Int = 20,
        level: String = "ALL",
        category: String = "",
        published: String = "all",
        search: String = ""
    ): AdminCourseListResponse = withContext(Dispatchers.IO) {
        val path = buildString {
            append("/api/admin/courses?page=$page&limit=$limit")
            append("&level=${enc(level)}")
            if (category.isNotBlank()) append("&category=${enc(category)}")
            append("&published=${enc(published)}")
            if (search.isNotBlank()) append("&search=${enc(search)}")
        }
        val data = NetworkClient.executeAuthorizedJson("GET", path, client = NetworkClient.admin)
        val arr = data.optJSONArray("courses") ?: JSONArray()
        val courses = buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { add(CourseJsonMapper.parseCourse(it)) }
            }
        }
        val catArr = data.optJSONArray("categories") ?: JSONArray()
        val categories = buildList {
            for (i in 0 until catArr.length()) {
                val c = catArr.optString(i)
                if (c.isNotBlank()) add(c)
            }
        }
        val pgObj = data.optJSONObject("pagination")
        val pagination = CoursePagination(
            page = pgObj?.optInt("page", page) ?: page,
            limit = pgObj?.optInt("limit", limit) ?: limit,
            total = pgObj?.optInt("total", courses.size) ?: courses.size,
            totalPages = pgObj?.optInt("totalPages", 1) ?: 1
        )
        AdminCourseListResponse(courses = courses, categories = categories, pagination = pagination)
    }

    suspend fun adminGetCourseDetail(courseId: String): CourseDetailPayload = withContext(Dispatchers.IO) {
        val data = NetworkClient.executeAuthorizedJson(
            method = "GET",
            path = "/api/admin/courses/$courseId",
            client = NetworkClient.admin
        )
        val course = CourseJsonMapper.parseCourse(data.getJSONObject("course"))
        val modArr = data.optJSONArray("modules") ?: JSONArray()
        val modules = buildList {
            for (i in 0 until modArr.length()) {
                modArr.optJSONObject(i)?.let { add(CourseJsonMapper.parseModule(it)) }
            }
        }.sortedBy { it.order }
        CourseDetailPayload(course = course, modules = modules, hasPremiumAccess = true)
    }

    suspend fun adminSaveCourse(
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
        isPublished: Boolean
    ): CourseSummary = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("title", title.trim())
            put("subtitle", subtitle.trim())
            put("description", description.trim())
            put("thumbnailUrl", thumbnailUrl.trim())
            put("thumbnailPublicId", thumbnailPublicId.trim())
            put("level", level.apiValue)
            put("category", category.trim().ifBlank { "General" })
            put("durationDays", durationDays.coerceIn(1, 365))
            put("isPremium", isPremium)
            put("price", if (isPremium) price.coerceAtLeast(0.0) else 0.0)
            put("isPublished", isPublished)
        }
        val data = if (courseId.isNullOrBlank()) {
            NetworkClient.executeAuthorizedJson("POST", "/api/admin/courses", payload, NetworkClient.admin)
        } else {
            NetworkClient.executeAuthorizedJson("PATCH", "/api/admin/courses/$courseId", payload, NetworkClient.admin)
        }
        CourseJsonMapper.parseCourse(data.getJSONObject("course"))
    }

    suspend fun adminToggleCoursePublish(courseId: String, isPublished: Boolean): CourseSummary =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("isPublished", isPublished)
            val data = NetworkClient.executeAuthorizedJson(
                "PATCH",
                "/api/admin/courses/$courseId",
                payload,
                NetworkClient.admin
            )
            CourseJsonMapper.parseCourse(data.getJSONObject("course"))
        }

    suspend fun adminDeleteCourse(courseId: String) = withContext(Dispatchers.IO) {
        NetworkClient.executeAuthorizedJson("DELETE", "/api/admin/courses/$courseId", null, NetworkClient.admin)
    }

    suspend fun adminCreateModule(
        courseId: String,
        title: String,
        description: String,
        isPublished: Boolean
    ): ModuleWithLessons = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("courseId", courseId)
            put("title", title.trim())
            put("description", description.trim())
            put("isPublished", isPublished)
        }
        val data = NetworkClient.executeAuthorizedJson("POST", "/api/admin/modules", payload, NetworkClient.admin)
        CourseJsonMapper.parseModule(data.getJSONObject("module"))
    }

    suspend fun adminUpdateModule(
        moduleId: String,
        title: String? = null,
        description: String? = null,
        isPublished: Boolean? = null
    ): ModuleWithLessons = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            if (title != null) put("title", title.trim())
            if (description != null) put("description", description.trim())
            if (isPublished != null) put("isPublished", isPublished)
        }
        val data = NetworkClient.executeAuthorizedJson(
            "PATCH",
            "/api/admin/modules/$moduleId",
            payload,
            NetworkClient.admin
        )
        CourseJsonMapper.parseModule(data.getJSONObject("module"))
    }

    suspend fun adminDeleteModule(moduleId: String) = withContext(Dispatchers.IO) {
        NetworkClient.executeAuthorizedJson("DELETE", "/api/admin/modules/$moduleId", null, NetworkClient.admin)
    }

    suspend fun adminReorderModules(courseId: String, moduleIds: List<String>) = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("courseId", courseId)
            put("moduleIds", JSONArray(moduleIds))
        }
        NetworkClient.executeAuthorizedJson("PATCH", "/api/admin/modules", payload, NetworkClient.admin)
    }

    suspend fun adminGetLesson(lessonId: String): LessonViewerPayload = withContext(Dispatchers.IO) {
        val data = NetworkClient.executeAuthorizedJson(
            "GET",
            "/api/admin/lessons/$lessonId",
            client = NetworkClient.admin
        )
        parseLessonViewerPayload(data, forcePreview = true)
    }

    suspend fun adminSaveLesson(
        lessonId: String?,
        courseId: String,
        moduleId: String,
        title: String,
        subtitle: String,
        description: String,
        estimatedDurationMinutes: Int,
        isPremium: Boolean,
        isPublished: Boolean,
        content: List<LessonContentBlock>
    ): LessonSummary = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("courseId", courseId)
            put("moduleId", moduleId)
            put("title", title.trim())
            put("subtitle", subtitle.trim())
            put("description", description.trim())
            put("estimatedDurationMinutes", estimatedDurationMinutes.coerceIn(1, 600))
            put("isPremium", isPremium)
            put("isPublished", isPublished)
            put("content", CourseJsonMapper.contentBlocksToJsonArray(content))
        }
        val data = if (lessonId.isNullOrBlank()) {
            NetworkClient.executeAuthorizedJson("POST", "/api/admin/lessons", payload, NetworkClient.admin)
        } else {
            NetworkClient.executeAuthorizedJson("PATCH", "/api/admin/lessons/$lessonId", payload, NetworkClient.admin)
        }
        CourseJsonMapper.parseLesson(data.getJSONObject("lesson"))
    }

    suspend fun adminToggleLessonPublish(lessonId: String, isPublished: Boolean): LessonSummary =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("isPublished", isPublished)
            val data = NetworkClient.executeAuthorizedJson(
                "PATCH",
                "/api/admin/lessons/$lessonId",
                payload,
                NetworkClient.admin
            )
            CourseJsonMapper.parseLesson(data.getJSONObject("lesson"))
        }

    suspend fun adminDuplicateLesson(lessonId: String): LessonSummary = withContext(Dispatchers.IO) {
        val data = NetworkClient.executeAuthorizedJson(
            "POST",
            "/api/admin/lessons/$lessonId/duplicate",
            JSONObject(),
            NetworkClient.admin
        )
        CourseJsonMapper.parseLesson(data.getJSONObject("lesson"))
    }

    suspend fun adminDeleteLesson(lessonId: String) = withContext(Dispatchers.IO) {
        NetworkClient.executeAuthorizedJson("DELETE", "/api/admin/lessons/$lessonId", null, NetworkClient.admin)
    }

    suspend fun adminReorderLessons(moduleId: String, lessonIds: List<String>) = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("moduleId", moduleId)
            put("lessonIds", JSONArray(lessonIds))
        }
        NetworkClient.executeAuthorizedJson("PATCH", "/api/admin/lessons", payload, NetworkClient.admin)
    }

    suspend fun uploadCourseMediaFromUri(
        uri: Uri,
        mediaType: String
    ): UploadedMediaResult = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            throw BackendApiException("Backend URL is not configured.", "CONFIG_MISSING", 500)
        }
        val user = Firebase.auth.currentUser
            ?: throw BackendApiException("Please sign in to upload media.", "UNAUTHORIZED", 401)
        var token = user.getIdToken(false).await().token
            ?: throw BackendApiException("Could not verify authentication token.", "UNAUTHORIZED", 401)

        val resolver = appContext.contentResolver
        val detectedMime = resolver.getType(uri).orEmpty().ifBlank {
            when (mediaType.lowercase()) {
                "pdf" -> "application/pdf"
                "video" -> "video/mp4"
                else -> "image/jpeg"
            }
        }
        val fileName = resolveFileName(uri) ?: when (mediaType.lowercase()) {
            "pdf" -> "lesson-resource.pdf"
            "video" -> "lesson-video.mp4"
            else -> "course-image.jpg"
        }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw BackendApiException("Could not read selected file.", "FILE_READ_ERROR", 400)
        if (bytes.isEmpty()) {
            throw BackendApiException("Selected file is empty.", "EMPTY_FILE", 400)
        }

        var refreshed = false
        for (attempt in 0..1) {
            val fileReqBody = bytes.toRequestBody(detectedMime.toMediaTypeOrNull())
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("mediaType", mediaType.lowercase())
                .addFormDataPart("file", fileName, fileReqBody)
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/admin/courses/media")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .post(multipart)
                .build()

            var retryWithFreshToken = false
            val uploaded = NetworkClient.admin.newCall(request).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (res.code == 401 && !refreshed) {
                    refreshed = true
                    val fresh = runCatching { user.getIdToken(true).await().token }.getOrNull()
                    if (!fresh.isNullOrBlank()) {
                        token = fresh
                        retryWithFreshToken = true
                        return@use null
                    }
                }
                val parsed = runCatching { JSONObject(raw) }.getOrNull()
                if (!res.isSuccessful || parsed?.optBoolean("success", true) == false) {
                    val err = parsed?.optJSONObject("error")
                    val code = err?.optString("code")?.takeIf { it.isNotBlank() }
                    val msg = err?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: "Media upload failed (${res.code})"
                    throw BackendApiException(msg, code, res.code)
                }
                val data = parsed?.optJSONObject("data") ?: parsed ?: JSONObject()
                UploadedMediaResult(
                    url = data.optString("url"),
                    publicId = data.optString("publicId"),
                    mediaType = data.optString("mediaType", mediaType),
                    durationSeconds = data.optInt("durationSeconds", 0),
                    bytes = data.optLong("bytes", bytes.size.toLong())
                )
            }
            if (!retryWithFreshToken && uploaded != null) {
                return@withContext uploaded
            }
        }
        throw BackendApiException("Media upload failed after token refresh.", "AUTH_ERROR", 401)
    }

    private fun resolveFileName(uri: Uri): String? {
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /* ========================================================================
     * STUDENT: COURSES, DETAILS, LESSON VIEWER & OFFLINE SYNC
     * ====================================================================== */

    suspend fun getStudentCourses(
        page: Int = 1,
        limit: Int = 20,
        level: String = "ALL",
        category: String = "",
        search: String = ""
    ): StudentCourseListResponse = withContext(Dispatchers.IO) {
        val uid = currentUid()
        runCatching { flushPendingProgress() }

        try {
            val path = buildString {
                append("/api/courses?page=$page&limit=$limit")
                append("&level=${enc(level)}")
                if (category.isNotBlank()) append("&category=${enc(category)}")
                if (search.isNotBlank()) append("&search=${enc(search)}")
            }
            val data = NetworkClient.executeAuthorizedJson("GET", path)
            val arr = data.optJSONArray("courses") ?: JSONArray()
            val existingCachedMap = dao.getCourses(uid).associateBy { it.courseId }
            val parsedCourses = buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { obj ->
                        val parsed = CourseJsonMapper.parseCourse(obj)
                        val offlineReady = existingCachedMap[parsed.id]?.isOfflineAvailable == true
                        add(parsed.copy(isOfflineAvailable = offlineReady))
                    }
                }
            }

            if (parsedCourses.isNotEmpty()) {
                val nowMs = System.currentTimeMillis()
                dao.upsertCourses(parsedCourses.map { it.toEntity(uid, nowMs) })
            }

            val catArr = data.optJSONArray("categories") ?: JSONArray()
            val categories = buildList {
                for (i in 0 until catArr.length()) {
                    val c = catArr.optString(i)
                    if (c.isNotBlank()) add(c)
                }
            }
            val pgObj = data.optJSONObject("pagination")
            val pagination = CoursePagination(
                page = pgObj?.optInt("page", page) ?: page,
                limit = pgObj?.optInt("limit", limit) ?: limit,
                total = pgObj?.optInt("total", parsedCourses.size) ?: parsedCourses.size,
                totalPages = pgObj?.optInt("totalPages", 1) ?: 1
            )
            StudentCourseListResponse(
                courses = parsedCourses,
                categories = categories,
                hasPremiumAccess = data.optBoolean("hasPremiumAccess", false),
                pagination = pagination,
                serverTimestamp = data.optString("serverTimestamp").takeIf { it.isNotBlank() }
            )
        } catch (err: Exception) {
            // Offline fallback from Room cache
            val cached = dao.getCourses(uid).map { it.toDomain() }
            if (cached.isEmpty()) throw err
            val filtered = cached.filter { course ->
                val levelOk = level.equals("ALL", ignoreCase = true) ||
                    course.level.apiValue.equals(level, ignoreCase = true)
                val catOk = category.isBlank() || course.category.equals(category, ignoreCase = true)
                val searchOk = search.isBlank() ||
                    course.title.contains(search, ignoreCase = true) ||
                    course.subtitle.contains(search, ignoreCase = true) ||
                    course.category.contains(search, ignoreCase = true)
                levelOk && catOk && searchOk
            }
            StudentCourseListResponse(
                courses = filtered,
                categories = cached.map { it.category }.distinct().sorted(),
                hasPremiumAccess = filtered.any { it.hasAccess && it.isPremium },
                pagination = CoursePagination(1, filtered.size.coerceAtLeast(1), filtered.size, 1)
            )
        }
    }

    suspend fun getStudentCourseDetail(
        courseId: String,
        includeContent: Boolean = false
    ): CourseDetailPayload = withContext(Dispatchers.IO) {
        val uid = currentUid()
        runCatching { flushPendingProgress() }

        try {
            val path = "/api/courses/$courseId" + if (includeContent) "?includeContent=true" else ""
            val data = NetworkClient.executeAuthorizedJson("GET", path)
            val prevCourse = dao.getCourse(uid, courseId)
            val course = CourseJsonMapper.parseCourse(data.getJSONObject("course"))
                .copy(isOfflineAvailable = prevCourse?.isOfflineAvailable == true)
            val modArr = data.optJSONArray("modules") ?: JSONArray()
            val modules = buildList {
                for (i in 0 until modArr.length()) {
                    modArr.optJSONObject(i)?.let { add(CourseJsonMapper.parseModule(it)) }
                }
            }.sortedBy { it.order }

            cacheCourseHierarchy(uid, course, modules, includeContent)
            CourseDetailPayload(
                course = course,
                modules = modules,
                hasPremiumAccess = data.optBoolean("hasPremiumAccess", false)
            )
        } catch (err: Exception) {
            loadCourseDetailFromCache(uid, courseId) ?: throw err
        }
    }

    suspend fun downloadCourseForOffline(courseId: String): CourseDetailPayload = withContext(Dispatchers.IO) {
        val uid = currentUid()
        val detail = getStudentCourseDetail(courseId = courseId, includeContent = true)
        detail.modules.forEach { mod ->
            mod.lessons.filter { !it.isLocked }.forEach { lesson ->
                lesson.content.forEach { block ->
                    if (block.url.isNotBlank()) {
                        when (block.type) {
                            ContentBlockType.PDF -> runCatching {
                                CourseMediaFileCache.downloadAndCache(appContext, block.url, ".pdf")
                            }
                            ContentBlockType.IMAGE -> runCatching {
                                CourseMediaFileCache.downloadAndCache(appContext, block.url, ".jpg")
                            }
                            ContentBlockType.VIDEO -> if (block.isDownloadable) {
                                runCatching {
                                    CourseMediaFileCache.downloadAndCache(appContext, block.url, ".mp4")
                                }
                            }
                            ContentBlockType.TEXT -> Unit
                        }
                    }
                }
            }
        }
        dao.setCourseOfflineAvailable(uid, courseId, true)
        detail.copy(course = detail.course.copy(isOfflineAvailable = true))
    }

    suspend fun getLessonDetail(
        lessonId: String,
        isPreview: Boolean = false
    ): LessonViewerPayload = withContext(Dispatchers.IO) {
        val uid = currentUid()
        if (isPreview) {
            val data = NetworkClient.executeAuthorizedJson(
                method = "GET",
                path = "/api/lessons/$lessonId?preview=true",
                client = NetworkClient.admin
            )
            return@withContext parseLessonViewerPayload(data, forcePreview = true)
        }

        try {
            val data = NetworkClient.executeAuthorizedJson("GET", "/api/lessons/$lessonId")
            val payload = parseLessonViewerPayload(data, forcePreview = false)
            val existingCourse = dao.getCourse(uid, payload.lesson.courseId)
            val contentJson = CourseJsonMapper.contentBlocksToJsonArray(payload.lesson.content).toString()
            dao.upsertLessons(
                listOf(payload.lesson.toEntity(uid, contentJson, hasFullContent = true))
            )
            payload.progress?.let { prog ->
                dao.upsertSingleProgress(prog.toEntity(uid, pendingSync = false))
            }
            if (payload.course != null && existingCourse == null) {
                dao.upsertCourses(listOf(payload.course.toEntity(uid, System.currentTimeMillis())))
            }
            payload
        } catch (err: Exception) {
            // Do not bypass 403 PREMIUM_REQUIRED with stale cache
            if (err is BackendApiException && err.httpStatus in listOf(401, 403, 404)) {
                throw err
            }
            loadLessonViewerFromCache(uid, lessonId) ?: throw err
        }
    }

    suspend fun saveLessonProgress(
        lessonId: String,
        courseId: String,
        moduleId: String,
        isCompleted: Boolean?,
        progressPercentage: Int?,
        lastContentId: String?,
        lastPositionSeconds: Int?
    ): LessonProgressModel = withContext(Dispatchers.IO) {
        val uid = currentUid()
        val existing = dao.getLessonProgress(uid, lessonId)

        val resolvedPercent = when {
            isCompleted == true -> 100
            progressPercentage != null -> maxOf(existing?.progressPercentage ?: 0, progressPercentage.coerceIn(0, 100))
            else -> existing?.progressPercentage ?: 0
        }
        val resolvedCompleted = when (isCompleted) {
            true -> true
            false -> false
            null -> existing?.isCompleted == true || resolvedPercent >= 100
        }
        val resolvedPos = lastPositionSeconds?.coerceAtLeast(0) ?: existing?.lastPositionSeconds ?: 0
        val resolvedContentId = lastContentId ?: existing?.lastContentId.orEmpty()

        val localEntity = CachedLessonProgressEntity(
            userId = uid,
            lessonId = lessonId,
            courseId = courseId,
            moduleId = moduleId,
            isCompleted = resolvedCompleted,
            progressPercentage = if (resolvedCompleted) 100 else resolvedPercent,
            lastContentId = resolvedContentId,
            lastPositionSeconds = resolvedPos,
            startedAt = existing?.startedAt,
            completedAt = existing?.completedAt,
            updatedAt = existing?.updatedAt,
            pendingSync = true
        )
        dao.upsertSingleProgress(localEntity)

        return@withContext try {
            val body = JSONObject().apply {
                if (isCompleted != null) put("isCompleted", isCompleted)
                if (progressPercentage != null) put("progressPercentage", progressPercentage.coerceIn(0, 100))
                if (lastContentId != null) put("lastContentId", lastContentId)
                if (lastPositionSeconds != null) put("lastPositionSeconds", lastPositionSeconds.coerceAtLeast(0))
            }
            val data = NetworkClient.executeAuthorizedJson("POST", "/api/lessons/$lessonId/progress", body)
            val parsed = CourseJsonMapper.parseProgress(data.optJSONObject("progress"))
                ?: localEntity.toDomain()
            dao.upsertSingleProgress(parsed.toEntity(uid, pendingSync = false))
            parsed
        } catch (_: Exception) {
            localEntity.toDomain()
        }
    }

    suspend fun flushPendingProgress() = withContext(Dispatchers.IO) {
        val uid = currentUid()
        val pending = dao.getPendingSyncProgress(uid)
        if (pending.isEmpty()) return@withContext
        for (item in pending) {
            runCatching {
                val body = JSONObject().apply {
                    put("isCompleted", item.isCompleted)
                    put("progressPercentage", item.progressPercentage)
                    put("lastContentId", item.lastContentId)
                    put("lastPositionSeconds", item.lastPositionSeconds)
                }
                val data = NetworkClient.executeAuthorizedJson(
                    "POST",
                    "/api/lessons/${item.lessonId}/progress",
                    body
                )
                val synced = CourseJsonMapper.parseProgress(data.optJSONObject("progress"))
                if (synced != null) {
                    dao.upsertSingleProgress(synced.toEntity(uid, pendingSync = false))
                } else {
                    dao.upsertSingleProgress(item.copy(pendingSync = false))
                }
            }
        }
    }

    /* ========================================================================
     * INTERNAL HELPERS & ROOM MAPPERS
     * ====================================================================== */

    private fun parseLessonViewerPayload(data: JSONObject, forcePreview: Boolean): LessonViewerPayload {
        val lesson = CourseJsonMapper.parseLesson(data.getJSONObject("lesson"))
        val course = data.optJSONObject("course")?.let { CourseJsonMapper.parseCourse(it) }
        val module = data.optJSONObject("module")?.let { CourseJsonMapper.parseModule(it) }
        val progress = CourseJsonMapper.parseProgress(data.optJSONObject("progress")) ?: lesson.progress
        val prev = CourseJsonMapper.parseLessonNavLink(data.optJSONObject("previousLesson"))
        val next = CourseJsonMapper.parseLessonNavLink(data.optJSONObject("nextLesson"))
        return LessonViewerPayload(
            lesson = lesson,
            course = course,
            module = module,
            progress = progress,
            previousLesson = prev,
            nextLesson = next,
            isPreview = forcePreview || data.optBoolean("isPreview", false)
        )
    }

    private suspend fun cacheCourseHierarchy(
        uid: String,
        course: CourseSummary,
        modules: List<ModuleWithLessons>,
        hasFullContent: Boolean
    ) {
        val nowMs = System.currentTimeMillis()
        dao.upsertCourses(listOf(course.toEntity(uid, nowMs)))
        dao.deleteModulesForCourse(uid, course.id)
        dao.upsertModules(modules.map { it.toEntity(uid) })

        val existingLessonMap = dao.getLessonsForCourse(uid, course.id).associateBy { it.lessonId }
        val lessonEntities = mutableListOf<CachedLessonEntity>()
        val progressEntities = mutableListOf<CachedLessonProgressEntity>()

        modules.forEach { mod ->
            mod.lessons.forEach { lesson ->
                val existing = existingLessonMap[lesson.id]
                val shouldKeepOldFullContent = !hasFullContent && existing?.hasFullContent == true && !lesson.isLocked
                val contentJson = if (shouldKeepOldFullContent) {
                    existing!!.contentJson
                } else {
                    CourseJsonMapper.contentBlocksToJsonArray(lesson.content).toString()
                }
                lessonEntities.add(
                    lesson.toEntity(
                        userId = uid,
                        contentJson = contentJson,
                        hasFullContent = (hasFullContent && !lesson.isLocked) || shouldKeepOldFullContent
                    )
                )
                lesson.progress?.let { prog ->
                    progressEntities.add(prog.toEntity(uid, pendingSync = false))
                }
            }
        }
        dao.deleteLessonsForCourse(uid, course.id)
        if (lessonEntities.isNotEmpty()) dao.upsertLessons(lessonEntities)
        if (progressEntities.isNotEmpty()) dao.upsertProgress(progressEntities)
    }

    private suspend fun loadCourseDetailFromCache(uid: String, courseId: String): CourseDetailPayload? {
        val courseEntity = dao.getCourse(uid, courseId) ?: return null
        val modEntities = dao.getModulesForCourse(uid, courseId)
        val lessonEntities = dao.getLessonsForCourse(uid, courseId)
        val progressMap = dao.getProgressForCourse(uid, courseId).associateBy { it.lessonId }

        val lessonsByMod = lessonEntities.groupBy { it.moduleId }
        val modules = modEntities.map { mod ->
            val modLessons = (lessonsByMod[mod.moduleId] ?: emptyList())
                .sortedBy { it.lessonOrder }
                .map { it.toDomain(progressMap[it.lessonId]?.toDomain()) }
            mod.toDomain(modLessons)
        }.sortedBy { it.order }

        val allLessons = modules.flatMap { it.lessons }
        val completed = allLessons.count { it.progress?.isCompleted == true }
        val total = allLessons.size
        val pct = CourseDomainUtils.calculateCourseCompletionPercent(completed, total)
        val enrollment = when {
            total > 0 && completed >= total -> EnrollmentStatus.COMPLETED
            allLessons.any { it.progress != null } -> EnrollmentStatus.IN_PROGRESS
            else -> EnrollmentStatus.NOT_STARTED
        }
        val continueId = allLessons.firstOrNull { !it.isLocked && it.progress?.isCompleted != true }?.id
            ?: allLessons.firstOrNull { !it.isLocked }?.id

        return CourseDetailPayload(
            course = courseEntity.toDomain().copy(
                totalModules = modules.size,
                totalLessons = total,
                completedLessons = completed,
                completionPercentage = pct,
                enrollmentStatus = enrollment,
                continueLessonId = continueId
            ),
            modules = modules,
            hasPremiumAccess = courseEntity.hasAccess
        )
    }

    private suspend fun loadLessonViewerFromCache(uid: String, lessonId: String): LessonViewerPayload? {
        val lessonEntity = dao.getLesson(uid, lessonId) ?: return null
        if (lessonEntity.isLocked || !lessonEntity.hasFullContent) return null

        val courseEntity = dao.getCourse(uid, lessonEntity.courseId)
        val modEntities = dao.getModulesForCourse(uid, lessonEntity.courseId).sortedBy { it.moduleOrder }
        val allLessons = dao.getLessonsForCourse(uid, lessonEntity.courseId)
        val lessonsByMod = allLessons.groupBy { it.moduleId }
        val ordered = modEntities.flatMap { m ->
            (lessonsByMod[m.moduleId] ?: emptyList()).sortedBy { it.lessonOrder }
        }
        val idx = ordered.indexOfFirst { it.lessonId == lessonId }
        val prev = if (idx > 0) ordered[idx - 1].let {
            LessonNavLink(it.lessonId, it.moduleId, it.title, it.isPremium)
        } else null
        val next = if (idx in 0 until ordered.lastIndex) ordered[idx + 1].let {
            LessonNavLink(it.lessonId, it.moduleId, it.title, it.isPremium)
        } else null

        val prog = dao.getLessonProgress(uid, lessonId)?.toDomain()
        val modEntity = modEntities.firstOrNull { it.moduleId == lessonEntity.moduleId }
        return LessonViewerPayload(
            lesson = lessonEntity.toDomain(prog),
            course = courseEntity?.toDomain(),
            module = modEntity?.toDomain(emptyList()),
            progress = prog,
            previousLesson = prev,
            nextLesson = next,
            isPreview = false
        )
    }
}

private fun CourseSummary.toEntity(userId: String, nowMs: Long) = CachedCourseEntity(
    userId = userId,
    courseId = id,
    title = title,
    slug = slug,
    subtitle = subtitle,
    description = description,
    thumbnailUrl = thumbnailUrl,
    thumbnailPublicId = thumbnailPublicId,
    level = level.apiValue,
    category = category,
    durationDays = durationDays,
    isPremium = isPremium,
    price = price,
    isPublished = isPublished,
    totalModules = totalModules,
    totalLessons = totalLessons,
    completedLessons = completedLessons,
    completionPercentage = completionPercentage,
    enrollmentStatus = enrollmentStatus.apiValue,
    continueLessonId = continueLessonId,
    hasAccess = hasAccess,
    isOfflineAvailable = isOfflineAvailable,
    updatedAt = updatedAt,
    cachedAtMs = nowMs
)

private fun CachedCourseEntity.toDomain() = CourseSummary(
    id = courseId,
    title = title,
    slug = slug,
    subtitle = subtitle,
    description = description,
    thumbnailUrl = thumbnailUrl,
    thumbnailPublicId = thumbnailPublicId,
    level = CourseLevel.fromApi(level),
    category = category,
    durationDays = durationDays,
    isPremium = isPremium,
    price = price,
    isPublished = isPublished,
    totalModules = totalModules,
    totalLessons = totalLessons,
    completedLessons = completedLessons,
    completionPercentage = completionPercentage,
    enrollmentStatus = EnrollmentStatus.fromApi(enrollmentStatus),
    continueLessonId = continueLessonId,
    hasAccess = hasAccess,
    isOfflineAvailable = isOfflineAvailable,
    updatedAt = updatedAt
)

private fun ModuleWithLessons.toEntity(userId: String) = CachedModuleEntity(
    userId = userId,
    moduleId = id,
    courseId = courseId,
    title = title,
    description = description,
    moduleOrder = order,
    isPublished = isPublished,
    lessonCount = lessonCount,
    updatedAt = updatedAt
)

private fun CachedModuleEntity.toDomain(lessons: List<LessonSummary>) = ModuleWithLessons(
    id = moduleId,
    courseId = courseId,
    title = title,
    description = description,
    order = moduleOrder,
    isPublished = isPublished,
    lessonCount = lessonCount,
    lessons = lessons,
    updatedAt = updatedAt
)

private fun LessonSummary.toEntity(
    userId: String,
    contentJson: String,
    hasFullContent: Boolean
) = CachedLessonEntity(
    userId = userId,
    lessonId = id,
    courseId = courseId,
    moduleId = moduleId,
    title = title,
    subtitle = subtitle,
    description = description,
    lessonOrder = order,
    estimatedDurationMinutes = estimatedDurationMinutes,
    isPremium = isPremium,
    isPublished = isPublished,
    isLocked = isLocked,
    contentCount = contentCount,
    contentTypesCsv = contentTypes.joinToString(",") { it.apiValue },
    contentJson = contentJson,
    hasFullContent = hasFullContent,
    updatedAt = updatedAt
)

private fun CachedLessonEntity.toDomain(progress: LessonProgressModel?): LessonSummary {
    val parsedBlocks = runCatching {
        CourseJsonMapper.parseContentBlocksArray(JSONArray(contentJson))
    }.getOrDefault(emptyList())
    val types = contentTypesCsv
        .split(",")
        .filter { it.isNotBlank() }
        .map { ContentBlockType.fromApi(it) }
    return LessonSummary(
        id = lessonId,
        courseId = courseId,
        moduleId = moduleId,
        title = title,
        subtitle = subtitle,
        description = description,
        order = lessonOrder,
        estimatedDurationMinutes = estimatedDurationMinutes,
        isPremium = isPremium,
        isPublished = isPublished,
        isLocked = isLocked,
        contentCount = contentCount,
        contentTypes = types,
        content = parsedBlocks,
        progress = progress,
        updatedAt = updatedAt
    )
}

private fun LessonProgressModel.toEntity(userId: String, pendingSync: Boolean) =
    CachedLessonProgressEntity(
        userId = userId,
        lessonId = lessonId,
        courseId = courseId,
        moduleId = moduleId,
        isCompleted = isCompleted,
        progressPercentage = progressPercentage,
        lastContentId = lastContentId,
        lastPositionSeconds = lastPositionSeconds,
        startedAt = startedAt,
        completedAt = completedAt,
        updatedAt = updatedAt,
        pendingSync = pendingSync
    )

private fun CachedLessonProgressEntity.toDomain() = LessonProgressModel(
    userId = userId,
    courseId = courseId,
    moduleId = moduleId,
    lessonId = lessonId,
    isCompleted = isCompleted,
    progressPercentage = progressPercentage,
    lastContentId = lastContentId,
    lastPositionSeconds = lastPositionSeconds,
    startedAt = startedAt,
    completedAt = completedAt,
    updatedAt = updatedAt
)
