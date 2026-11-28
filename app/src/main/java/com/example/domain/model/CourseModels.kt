package com.example.domain.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class CourseLevel(val apiValue: String, val label: String) {
    BEGINNER("Beginner", "Beginner"),
    INTERMEDIATE("Intermediate", "Intermediate"),
    ADVANCED("Advanced", "Advanced");

    companion object {
        fun fromApi(raw: String?): CourseLevel {
            return when (raw?.trim()?.lowercase(Locale.US)) {
                "intermediate" -> INTERMEDIATE
                "advanced" -> ADVANCED
                else -> BEGINNER
            }
        }
    }
}

enum class ContentBlockType(val apiValue: String, val label: String) {
    VIDEO("video", "Video"),
    PDF("pdf", "PDF"),
    TEXT("text", "Text"),
    IMAGE("image", "Image");

    companion object {
        fun fromApi(raw: String?): ContentBlockType {
            return when (raw?.trim()?.lowercase(Locale.US)) {
                "video" -> VIDEO
                "pdf" -> PDF
                "image" -> IMAGE
                else -> TEXT
            }
        }
    }
}

enum class EnrollmentStatus(val apiValue: String, val label: String) {
    NOT_STARTED("not_started", "Not Started"),
    IN_PROGRESS("in_progress", "In Progress"),
    COMPLETED("completed", "Completed");

    companion object {
        fun fromApi(raw: String?): EnrollmentStatus {
            return when (raw?.trim()?.lowercase(Locale.US)) {
                "completed" -> COMPLETED
                "in_progress" -> IN_PROGRESS
                else -> NOT_STARTED
            }
        }
    }
}

data class LessonContentBlock(
    val id: String,
    val type: ContentBlockType,
    val title: String = "",
    val description: String = "",
    val url: String = "",
    val publicId: String = "",
    val text: String = "",
    val alt: String = "",
    val durationSeconds: Int = 0,
    val order: Int = 1,
    val isDownloadable: Boolean = false
)

data class LessonProgressModel(
    val id: String = "",
    val userId: String = "",
    val courseId: String = "",
    val moduleId: String = "",
    val lessonId: String = "",
    val isCompleted: Boolean = false,
    val progressPercentage: Int = 0,
    val lastContentId: String = "",
    val lastPositionSeconds: Int = 0,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val updatedAt: String? = null
)

data class LessonSummary(
    val id: String,
    val courseId: String,
    val moduleId: String,
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val order: Int = 1,
    val estimatedDurationMinutes: Int = 10,
    val isPremium: Boolean = false,
    val isPublished: Boolean = false,
    val isLocked: Boolean = false,
    val contentCount: Int = 0,
    val contentTypes: List<ContentBlockType> = emptyList(),
    val content: List<LessonContentBlock> = emptyList(),
    val progress: LessonProgressModel? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class ModuleWithLessons(
    val id: String,
    val courseId: String,
    val title: String,
    val description: String = "",
    val order: Int = 1,
    val isPublished: Boolean = false,
    val lessonCount: Int = 0,
    val lessons: List<LessonSummary> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class CourseSummary(
    val id: String,
    val title: String,
    val slug: String = "",
    val subtitle: String = "",
    val description: String = "",
    val thumbnailUrl: String = "",
    val thumbnailPublicId: String = "",
    val level: CourseLevel = CourseLevel.BEGINNER,
    val category: String = "General",
    val durationDays: Int = 30,
    val isPremium: Boolean = false,
    val price: Double = 0.0,
    val isPublished: Boolean = false,
    val totalModules: Int = 0,
    val totalLessons: Int = 0,
    val completedLessons: Int = 0,
    val completionPercentage: Int = 0,
    val enrollmentStatus: EnrollmentStatus = EnrollmentStatus.NOT_STARTED,
    val continueLessonId: String? = null,
    val hasAccess: Boolean = true,
    val isPurchased: Boolean = false,
    val isOfflineAvailable: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class SslCommerzCheckoutSession(
    val purchaseId: String,
    val tranId: String,
    val courseId: String,
    val courseTitle: String,
    val amount: Double,
    val currency: String = "BDT",
    val gatewayUrl: String,
    val isSandbox: Boolean = true
)

data class CoursePurchaseStatusResult(
    val purchaseId: String,
    val tranId: String,
    val courseId: String,
    val status: String,
    val amount: Double,
    val currency: String = "BDT",
    val cardType: String = "",
    val cardIssuer: String = "",
    val bankTranId: String = "",
    val hasAccess: Boolean = false,
    val message: String = ""
)

data class CourseDetailPayload(
    val course: CourseSummary,
    val modules: List<ModuleWithLessons>,
    val hasPremiumAccess: Boolean = false
)

data class LessonNavLink(
    val id: String,
    val moduleId: String,
    val title: String,
    val isPremium: Boolean = false
)

data class LessonViewerPayload(
    val lesson: LessonSummary,
    val course: CourseSummary?,
    val module: ModuleWithLessons?,
    val progress: LessonProgressModel?,
    val previousLesson: LessonNavLink?,
    val nextLesson: LessonNavLink?,
    val isPreview: Boolean = false
)

data class UploadedMediaResult(
    val url: String,
    val publicId: String,
    val mediaType: String,
    val durationSeconds: Int = 0,
    val bytes: Long = 0L
)

data class CoursePagination(
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0,
    val totalPages: Int = 1
)

data class AdminCourseListResponse(
    val courses: List<CourseSummary>,
    val categories: List<String>,
    val pagination: CoursePagination
)

data class StudentCourseListResponse(
    val courses: List<CourseSummary>,
    val categories: List<String>,
    val hasPremiumAccess: Boolean,
    val pagination: CoursePagination,
    val serverTimestamp: String? = null
)

object CourseDomainUtils {
    const val DEFAULT_PREMIUM_COURSE_PRICE_BDT = 499.0

    fun <T> reorderList(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
        if (fromIndex == toIndex) return items
        if (fromIndex !in items.indices || toIndex !in items.indices) return items
        val mutable = items.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        return mutable
    }

    fun normalizeBlockOrders(blocks: List<LessonContentBlock>): List<LessonContentBlock> {
        return blocks.mapIndexed { idx, block -> block.copy(order = idx + 1) }
    }

    fun calculateCourseCompletionPercent(completedLessons: Int, totalLessons: Int): Int {
        if (totalLessons <= 0) return 0
        return ((completedLessons.coerceAtLeast(0).toFloat() / totalLessons) * 100f)
            .toInt()
            .coerceIn(0, 100)
    }

    fun isLessonLocked(lessonIsPremium: Boolean, hasPremiumAccess: Boolean): Boolean {
        if (hasPremiumAccess) return false
        return lessonIsPremium
    }

    fun resolveDisplayCoursePrice(price: Double): Double {
        return if (price.isFinite() && price >= 10.0) price else DEFAULT_PREMIUM_COURSE_PRICE_BDT
    }

    fun formatCoursePrice(price: Double, currency: String = "BDT"): String {
        val resolved = resolveDisplayCoursePrice(price)
        val formattedNumber = if (resolved % 1.0 == 0.0) {
            resolved.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", resolved)
        }
        return if (currency.equals("BDT", ignoreCase = true)) {
            "৳$formattedNumber"
        } else {
            "${currency.uppercase(Locale.US)} $formattedNumber"
        }
    }

    fun formatDurationSeconds(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val mins = safe / 60
        val secs = safe % 60
        return String.format(Locale.US, "%d:%02d", mins, secs)
    }
}

object CourseJsonMapper {
    fun parseCourse(obj: JSONObject): CourseSummary {
        return CourseSummary(
            id = obj.optString("id"),
            title = obj.optString("title"),
            slug = obj.optString("slug"),
            subtitle = obj.optString("subtitle"),
            description = obj.optString("description"),
            thumbnailUrl = obj.optString("thumbnailUrl"),
            thumbnailPublicId = obj.optString("thumbnailPublicId"),
            level = CourseLevel.fromApi(obj.optString("level")),
            category = obj.optString("category", "General").ifBlank { "General" },
            durationDays = obj.optInt("durationDays", 30).coerceAtLeast(1),
            isPremium = obj.optBoolean("isPremium", false),
            price = obj.optDouble("price", 0.0).let { if (it.isNaN()) 0.0 else it },
            isPublished = obj.optBoolean("isPublished", false),
            totalModules = obj.optInt("totalModules", 0),
            totalLessons = obj.optInt("totalLessons", 0),
            completedLessons = obj.optInt("completedLessons", 0),
            completionPercentage = obj.optInt("completionPercentage", 0),
            enrollmentStatus = EnrollmentStatus.fromApi(obj.optString("enrollmentStatus")),
            continueLessonId = obj.optString("continueLessonId").takeIf { it.isNotBlank() && it != "null" },
            hasAccess = obj.optBoolean("hasAccess", true),
            isPurchased = obj.optBoolean("isPurchased", false),
            createdAt = obj.optString("createdAt").takeIf { it.isNotBlank() && it != "null" },
            updatedAt = obj.optString("updatedAt").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    fun parseSslCommerzSession(obj: JSONObject): SslCommerzCheckoutSession {
        return SslCommerzCheckoutSession(
            purchaseId = obj.optString("purchaseId"),
            tranId = obj.optString("tranId"),
            courseId = obj.optString("courseId"),
            courseTitle = obj.optString("courseTitle"),
            amount = obj.optDouble("amount", 0.0).let { if (it.isNaN()) 0.0 else it },
            currency = obj.optString("currency", "BDT").ifBlank { "BDT" },
            gatewayUrl = obj.optString("gatewayUrl"),
            isSandbox = obj.optBoolean("isSandbox", true)
        )
    }

    fun parsePurchaseStatus(obj: JSONObject): CoursePurchaseStatusResult {
        return CoursePurchaseStatusResult(
            purchaseId = obj.optString("purchaseId"),
            tranId = obj.optString("tranId"),
            courseId = obj.optString("courseId"),
            status = obj.optString("status", "pending"),
            amount = obj.optDouble("amount", 0.0).let { if (it.isNaN()) 0.0 else it },
            currency = obj.optString("currency", "BDT").ifBlank { "BDT" },
            cardType = obj.optString("cardType"),
            cardIssuer = obj.optString("cardIssuer"),
            bankTranId = obj.optString("bankTranId"),
            hasAccess = obj.optBoolean("hasAccess", false),
            message = obj.optString("message")
        )
    }

    fun parseContentBlock(obj: JSONObject, fallbackOrder: Int = 1): LessonContentBlock {
        return LessonContentBlock(
            id = obj.optString("id").ifBlank { "blk_local_$fallbackOrder" },
            type = ContentBlockType.fromApi(obj.optString("type")),
            title = obj.optString("title"),
            description = obj.optString("description"),
            url = obj.optString("url"),
            publicId = obj.optString("publicId"),
            text = obj.optString("text"),
            alt = obj.optString("alt"),
            durationSeconds = obj.optInt("durationSeconds", 0),
            order = obj.optInt("order", fallbackOrder),
            isDownloadable = obj.optBoolean("isDownloadable", false)
        )
    }

    fun contentBlocksToJsonArray(blocks: List<LessonContentBlock>): JSONArray {
        val arr = JSONArray()
        blocks.forEachIndexed { idx, block ->
            arr.put(
                JSONObject().apply {
                    put("id", block.id)
                    put("type", block.type.apiValue)
                    put("title", block.title)
                    put("description", block.description)
                    put("url", block.url)
                    put("publicId", block.publicId)
                    put("text", block.text)
                    put("alt", block.alt)
                    put("durationSeconds", block.durationSeconds)
                    put("order", idx + 1)
                    put("isDownloadable", block.isDownloadable)
                }
            )
        }
        return arr
    }

    fun parseContentBlocksArray(arr: JSONArray?): List<LessonContentBlock> {
        if (arr == null) return emptyList()
        val list = mutableListOf<LessonContentBlock>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            list.add(parseContentBlock(item, i + 1))
        }
        return list.sortedBy { it.order }
    }

    fun parseProgress(obj: JSONObject?): LessonProgressModel? {
        if (obj == null) return null
        val lessonId = obj.optString("lessonId")
        if (lessonId.isBlank()) return null
        return LessonProgressModel(
            id = obj.optString("id"),
            userId = obj.optString("userId"),
            courseId = obj.optString("courseId"),
            moduleId = obj.optString("moduleId"),
            lessonId = lessonId,
            isCompleted = obj.optBoolean("isCompleted", false),
            progressPercentage = obj.optInt("progressPercentage", 0),
            lastContentId = obj.optString("lastContentId"),
            lastPositionSeconds = obj.optInt("lastPositionSeconds", 0),
            startedAt = obj.optString("startedAt").takeIf { it.isNotBlank() && it != "null" },
            completedAt = obj.optString("completedAt").takeIf { it.isNotBlank() && it != "null" },
            updatedAt = obj.optString("updatedAt").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    fun parseLesson(obj: JSONObject): LessonSummary {
        val typesArr = obj.optJSONArray("contentTypes")
        val contentTypes = buildList {
            if (typesArr != null) {
                for (i in 0 until typesArr.length()) {
                    add(ContentBlockType.fromApi(typesArr.optString(i)))
                }
            }
        }
        val parsedBlocks = parseContentBlocksArray(obj.optJSONArray("content"))
        val resolvedTypes = if (contentTypes.isNotEmpty()) contentTypes else parsedBlocks.map { it.type }.distinct()

        return LessonSummary(
            id = obj.optString("id"),
            courseId = obj.optString("courseId"),
            moduleId = obj.optString("moduleId"),
            title = obj.optString("title"),
            subtitle = obj.optString("subtitle"),
            description = obj.optString("description"),
            order = obj.optInt("order", 1),
            estimatedDurationMinutes = obj.optInt("estimatedDurationMinutes", 10),
            isPremium = obj.optBoolean("isPremium", false),
            isPublished = obj.optBoolean("isPublished", false),
            isLocked = obj.optBoolean("isLocked", false),
            contentCount = obj.optInt("contentCount", parsedBlocks.size),
            contentTypes = resolvedTypes,
            content = parsedBlocks,
            progress = parseProgress(obj.optJSONObject("progress")),
            createdAt = obj.optString("createdAt").takeIf { it.isNotBlank() && it != "null" },
            updatedAt = obj.optString("updatedAt").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    fun parseModule(obj: JSONObject): ModuleWithLessons {
        val lessonsArr = obj.optJSONArray("lessons")
        val lessons = buildList {
            if (lessonsArr != null) {
                for (i in 0 until lessonsArr.length()) {
                    lessonsArr.optJSONObject(i)?.let { add(parseLesson(it)) }
                }
            }
        }.sortedBy { it.order }

        return ModuleWithLessons(
            id = obj.optString("id"),
            courseId = obj.optString("courseId"),
            title = obj.optString("title"),
            description = obj.optString("description"),
            order = obj.optInt("order", 1),
            isPublished = obj.optBoolean("isPublished", false),
            lessonCount = obj.optInt("lessonCount", lessons.size),
            lessons = lessons,
            createdAt = obj.optString("createdAt").takeIf { it.isNotBlank() && it != "null" },
            updatedAt = obj.optString("updatedAt").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    fun parseLessonNavLink(obj: JSONObject?): LessonNavLink? {
        if (obj == null) return null
        val id = obj.optString("id")
        if (id.isBlank()) return null
        return LessonNavLink(
            id = id,
            moduleId = obj.optString("moduleId"),
            title = obj.optString("title"),
            isPremium = obj.optBoolean("isPremium", false)
        )
    }
}
