package com.example

import com.example.domain.model.ContentBlockType
import com.example.domain.model.CourseDomainUtils
import com.example.domain.model.CourseJsonMapper
import com.example.domain.model.CourseLevel
import com.example.domain.model.EnrollmentStatus
import com.example.domain.model.LessonContentBlock
import com.example.feature.courses.ui.RichTextBlock
import com.example.feature.courses.ui.RichTextParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CourseSystemUnitTest {

    @Test
    fun `reorderList moves items accurately and handles invalid bounds safely`() {
        val original = listOf("A", "B", "C", "D")
        assertEquals(listOf("B", "C", "A", "D"), CourseDomainUtils.reorderList(original, 0, 2))
        assertEquals(listOf("D", "A", "B", "C"), CourseDomainUtils.reorderList(original, 3, 0))
        assertEquals(original, CourseDomainUtils.reorderList(original, -1, 2))
        assertEquals(original, CourseDomainUtils.reorderList(original, 1, 99))
    }

    @Test
    fun `normalizeBlockOrders assigns sequential 1-based orders`() {
        val blocks = listOf(
            LessonContentBlock(id = "b1", type = ContentBlockType.TEXT, text = "Intro", order = 5),
            LessonContentBlock(id = "b2", type = ContentBlockType.VIDEO, url = "https://cdn/v.mp4", order = 9),
            LessonContentBlock(id = "b3", type = ContentBlockType.PDF, url = "https://cdn/d.pdf", order = 2)
        )
        val normalized = CourseDomainUtils.normalizeBlockOrders(blocks)
        assertEquals(listOf(1, 2, 3), normalized.map { it.order })
        assertEquals(listOf("b1", "b2", "b3"), normalized.map { it.id })
    }

    @Test
    fun `calculateCourseCompletionPercent and isLessonLocked enforce access and percentage bounds`() {
        assertEquals(0, CourseDomainUtils.calculateCourseCompletionPercent(0, 0))
        assertEquals(50, CourseDomainUtils.calculateCourseCompletionPercent(2, 4))
        assertEquals(100, CourseDomainUtils.calculateCourseCompletionPercent(7, 5))
        assertEquals(0, CourseDomainUtils.calculateCourseCompletionPercent(-2, 5))

        assertTrue(CourseDomainUtils.isLessonLocked(lessonIsPremium = true, hasPremiumAccess = false))
        assertFalse(CourseDomainUtils.isLessonLocked(lessonIsPremium = true, hasPremiumAccess = true))
        assertFalse(CourseDomainUtils.isLessonLocked(lessonIsPremium = false, hasPremiumAccess = false))
    }

    @Test
    fun `formatDurationSeconds formats minutes and seconds with zero padding`() {
        assertEquals("0:00", CourseDomainUtils.formatDurationSeconds(-5))
        assertEquals("1:05", CourseDomainUtils.formatDurationSeconds(65))
        assertEquals("12:30", CourseDomainUtils.formatDurationSeconds(750))
    }

    @Test
    fun `RichTextParser parses markdown headings lists links bold and italic`() {
        val raw = """
            # Lesson Overview
            ## Key Grammar Rule
            Practice **present simple** with *daily habits* and visit [Grammar Guide](https://example.com/guide).
            - Subject + Verb
            1. First example
        """.trimIndent()

        val blocks = RichTextParser.parse(raw)
        assertTrue(blocks.any { it is RichTextBlock.Heading && it.level == 1 })
        assertTrue(blocks.any { it is RichTextBlock.Heading && it.level == 2 })
        assertTrue(blocks.any { it is RichTextBlock.BulletItem })
        assertTrue(blocks.any { it is RichTextBlock.NumberedItem && it.number == 1 })

        val paragraph = blocks.filterIsInstance<RichTextBlock.Paragraph>().first()
        assertTrue(paragraph.runs.any { it.bold && it.text == "present simple" })
        assertTrue(paragraph.runs.any { it.italic && it.text == "daily habits" })
        assertTrue(paragraph.runs.any { it.linkUrl == "https://example.com/guide" && it.text == "Grammar Guide" })
    }

    @Test
    fun `RichTextParser preserves HTML headings strong em anchors and list items`() {
        val html = "<h1>Welcome</h1><p>Use <strong>active</strong> and <em>clear</em> speech. <a href=\"https://example.com/notes\">Notes</a></p><ul><li>Read aloud</li></ul>"
        val blocks = RichTextParser.parse(html)

        assertTrue(blocks.any { it is RichTextBlock.Heading && it.level == 1 && it.runs.any { r -> r.text == "Welcome" } })
        val para = blocks.filterIsInstance<RichTextBlock.Paragraph>().first()
        assertTrue(para.runs.any { it.bold && it.text == "active" })
        assertTrue(para.runs.any { it.italic && it.text == "clear" })
        assertTrue(para.runs.any { it.linkUrl == "https://example.com/notes" && it.text == "Notes" })
        assertTrue(blocks.any { it is RichTextBlock.BulletItem && it.runs.any { r -> r.text == "Read aloud" } })
    }

    @Test
    fun `CourseJsonMapper parses course lesson content blocks and progress cleanly`() {
        val courseJson = JSONObject().apply {
            put("id", "course_1")
            put("title", "Fluent Everyday English")
            put("level", "Intermediate")
            put("category", "Speaking")
            put("durationDays", 21)
            put("isPremium", true)
            put("price", 19.99)
            put("isPublished", true)
            put("totalModules", 3)
            put("totalLessons", 12)
            put("completedLessons", 6)
            put("completionPercentage", 50)
            put("enrollmentStatus", "in_progress")
            put("hasAccess", true)
        }
        val parsedCourse = CourseJsonMapper.parseCourse(courseJson)
        assertEquals("course_1", parsedCourse.id)
        assertEquals(CourseLevel.INTERMEDIATE, parsedCourse.level)
        assertEquals(EnrollmentStatus.IN_PROGRESS, parsedCourse.enrollmentStatus)
        assertEquals(50, parsedCourse.completionPercentage)
        assertTrue(parsedCourse.isPremium)

        val blocksInput = listOf(
            LessonContentBlock(
                id = "blk_2",
                type = ContentBlockType.PDF,
                title = "Worksheet",
                url = "https://res.cloudinary.com/demo/raw/upload/sheet.pdf",
                publicId = "courses/sheet",
                order = 2,
                isDownloadable = true
            ),
            LessonContentBlock(
                id = "blk_1",
                type = ContentBlockType.VIDEO,
                title = "Intro Video",
                url = "https://res.cloudinary.com/demo/video/upload/intro.mp4",
                publicId = "courses/intro",
                durationSeconds = 180,
                order = 1,
                isDownloadable = false
            )
        )
        val serialized = CourseJsonMapper.contentBlocksToJsonArray(blocksInput)
        assertEquals(2, serialized.length())
        assertEquals(1, serialized.getJSONObject(0).getInt("order"))
        assertEquals(2, serialized.getJSONObject(1).getInt("order"))

        val lessonJson = JSONObject().apply {
            put("id", "lesson_1")
            put("courseId", "course_1")
            put("moduleId", "mod_1")
            put("title", "Greetings")
            put("order", 1)
            put("estimatedDurationMinutes", 15)
            put("isPublished", true)
            put("content", JSONArray().apply {
                put(serialized.getJSONObject(1))
                put(serialized.getJSONObject(0))
            })
            put("progress", JSONObject().apply {
                put("lessonId", "lesson_1")
                put("isCompleted", false)
                put("progressPercentage", 45)
                put("lastContentId", "blk_1")
                put("lastPositionSeconds", 82)
            })
        }
        val parsedLesson = CourseJsonMapper.parseLesson(lessonJson)
        assertEquals("lesson_1", parsedLesson.id)
        assertEquals(2, parsedLesson.content.size)
        assertNotNull(parsedLesson.progress)
        assertEquals(82, parsedLesson.progress?.lastPositionSeconds)
        assertEquals("blk_1", parsedLesson.progress?.lastContentId)
    }
}
