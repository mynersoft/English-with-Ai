package com.example

import com.example.data.repository.CallRepository
import com.example.domain.model.CallMode
import com.example.domain.model.CallParticipantSummary
import com.example.domain.model.CallRoomSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRandomLogicTest {

    @Test
    fun callModeRandom_parsesAndMarksOneOnOne() {
        val randomMode = CallMode.fromApi("random")
        assertEquals(CallMode.RANDOM, randomMode)
        assertTrue(randomMode.isOneOnOne)
        assertTrue(CallMode.DIRECT.isOneOnOne)
        assertFalse(CallMode.GROUP_PUBLIC.isOneOnOne)
        assertFalse(CallMode.GROUP_PRIVATE.isOneOnOne)
    }

    @Test
    fun collectBusyUserIds_includesOnlyActiveAndRingingParticipants() {
        val rooms = listOf(
            CallRoomSummary(
                id = "r1",
                mode = CallMode.RANDOM,
                status = "ringing",
                hostUserId = "u1",
                targetUserId = "u2",
                participants = listOf(CallParticipantSummary(userId = "u1", name = "Host"))
            ),
            CallRoomSummary(
                id = "r2",
                mode = CallMode.GROUP_PUBLIC,
                status = "active",
                hostUserId = "u3",
                participants = listOf(
                    CallParticipantSummary(userId = "u3", name = "H3"),
                    CallParticipantSummary(userId = "u4", name = "P4")
                )
            ),
            CallRoomSummary(
                id = "r3",
                mode = CallMode.DIRECT,
                status = "declined",
                hostUserId = "u5",
                targetUserId = "u6",
                participants = listOf(CallParticipantSummary(userId = "u5", name = "FreeAgain"))
            ),
            CallRoomSummary(
                id = "r4",
                mode = CallMode.DIRECT,
                status = "ended",
                hostUserId = "u7",
                targetUserId = "u8"
            )
        )

        val busy = CallRepository.collectBusyUserIds(rooms)
        assertEquals(setOf("u1", "u2", "u3", "u4"), busy)
    }

    @Test
    fun callRoomSummary_effectiveRandomDetectsModeFlagAndTopic() {
        val byMode = CallRoomSummary(id = "r1", mode = CallMode.RANDOM)
        val byFlag = CallRoomSummary(id = "r2", mode = CallMode.DIRECT, isRandom = true)
        val byTopic = CallRoomSummary(id = "r3", mode = CallMode.DIRECT, topic = "Random 1-on-1 English Practice")
        val regularDirect = CallRoomSummary(id = "r4", mode = CallMode.DIRECT, topic = "1-on-1 English Video Call")

        assertTrue(byMode.effectiveRandom)
        assertTrue(byFlag.effectiveRandom)
        assertTrue(byTopic.effectiveRandom)
        assertFalse(regularDirect.effectiveRandom)
    }
}
