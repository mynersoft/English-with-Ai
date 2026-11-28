package com.example

import com.example.data.repository.Correction
import com.example.data.repository.SpeakingReplyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakingJsonRecoveryTest {

    @Test
    fun parseReplyAndCorrection_recoversTruncatedJsonWithUnicodeBanglaEscapes() {
        val truncatedRawReply = """
            {
              "reply": "I think you mean you wake up and brush your teeth! What do you eat for breakfast?",
              "correction": {
                "hasMistake": true,
                "original": "I get a really in the morning and press my trip",
                "corrected": "I get up early in the morning and brush my teeth.",
                "explanationBn": "\u09b8\u0995\u09be\u09b2\u09c7 \u0998\u09c1\u09ae \u09a5\u09c7\u0995\u09c7 \u0993\u09a0\u09be 'get up'\u0964\u09b
        """.trimIndent()

        val result = SpeakingReplyParser.parseReplyAndCorrection(
            rawReply = truncatedRawReply,
            serverCorrection = null,
            originalMessage = "I get a really in the morning and press my trip"
        )

        assertEquals(
            "I think you mean you wake up and brush your teeth! What do you eat for breakfast?",
            result.reply
        )
        assertFalse(result.reply.trimStart().startsWith("{"))
        assertNotNull(result.correction)
        assertEquals("I get a really in the morning and press my trip", result.correction?.original)
        assertEquals("I get up early in the morning and brush my teeth.", result.correction?.corrected)
        assertTrue(result.correction?.explanationBn?.contains("সকালে") == true)
        assertFalse(result.correction?.explanationBn?.contains("\\u09") == true)
    }

    @Test
    fun parseReplyAndCorrection_handlesNormalReplyAndDecodesServerCorrection() {
        val result = SpeakingReplyParser.parseReplyAndCorrection(
            rawReply = "Great! Tell me more about your family.",
            serverCorrection = Correction(
                original = "I have two brother",
                corrected = "I have two brothers.",
                explanationBn = "\\u09a6\\u09c1\\u0987\\u099c\\u09a8 \\u09ad\\u09be\\u0987"
            ),
            originalMessage = "I have two brother"
        )

        assertEquals("Great! Tell me more about your family.", result.reply)
        assertNotNull(result.correction)
        assertEquals("I have two brothers.", result.correction?.corrected)
        assertEquals("দুইজন ভাই", result.correction?.explanationBn)
    }

    @Test
    fun parseReplyAndCorrection_suppressesNoOpCorrection() {
        val result = SpeakingReplyParser.parseReplyAndCorrection(
            rawReply = """{"reply":"Nice to meet you!","correction":{"hasMistake":true,"original":"Hello","corrected":"hello","explanationBn":""}}""",
            serverCorrection = null,
            originalMessage = "Hello"
        )
        assertEquals("Nice to meet you!", result.reply)
        assertNull(result.correction)
    }
}
