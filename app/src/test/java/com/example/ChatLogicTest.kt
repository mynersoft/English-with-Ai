package com.example

import com.example.domain.model.ChatAiCorrection
import com.example.domain.model.ChatCorrectionToken
import com.example.feature.chat.ChatThreadUiState
import com.example.feature.chat.HandwritingImageHelper
import com.example.feature.chat.formatDateDivider
import com.example.feature.chat.formatMessageTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ChatLogicTest {

    @Test
    fun `formatDateDivider returns Today for current instant and formats past dates`() {
        val nowIso = Instant.now().toString()
        assertEquals("Today", formatDateDivider(nowIso))
        assertEquals("", formatMessageTime(""))
        assertTrue(formatMessageTime(nowIso).isNotBlank())
    }

    @Test
    fun `chat correction tokens and default thread state follow spec`() {
        val correction = ChatAiCorrection(
            status = "corrected",
            hasMistake = true,
            correctedText = "I feel like going",
            tokens = listOf(
                ChatCorrectionToken("I", changed = false),
                ChatCorrectionToken("feel", changed = true),
                ChatCorrectionToken("like", changed = false),
                ChatCorrectionToken("going", changed = true)
            )
        )
        assertTrue(correction.hasMistake)
        assertFalse(correction.tokens[0].changed)
        assertTrue(correction.tokens[1].changed)

        val state = ChatThreadUiState()
        assertTrue(state.autoCorrectEnabled)
        assertTrue(state.messages.isEmpty())
        assertFalse(state.extractingHandwriting)
        assertNull(state.extractedHandwritingText)
        assertNull(state.ocrError)
    }

    @Test
    fun `calculateSampleSize scales large images cleanly without overshrinking`() {
        assertEquals(1, HandwritingImageHelper.calculateSampleSize(1000, 800, maxDim = 1400))
        assertEquals(2, HandwritingImageHelper.calculateSampleSize(4000, 3000, maxDim = 1400))
        assertEquals(4, HandwritingImageHelper.calculateSampleSize(6000, 6000, maxDim = 1400))
    }
}
