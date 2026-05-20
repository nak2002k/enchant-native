package org.enchant.core.calls

import org.enchant.core.calls.webrtc.IceCandidateHandler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("IceCandidateHandler")
class IceCandidateHandlerTest {

    private lateinit var handler: IceCandidateHandler

    @BeforeEach
    fun setUp() { handler = IceCandidateHandler() }

    @Test fun `serialize produces correct format`() {
        val candidate = org.webrtc.IceCandidate("0", 0, "candidate:123")
        val serialized = handler.serialize(candidate)
        assertEquals("0|0|candidate:123", serialized)
    }

    @Test fun `parse valid candidate`() {
        val parsed = handler.parse("0|0|candidate:123")
        assertNotNull(parsed)
        assertEquals("0", parsed!!.sdpMid)
        assertEquals(0, parsed.sdpMLineIndex)
        assertEquals("candidate:123", parsed.sdp)
    }

    @Test fun `parse invalid candidate returns null`() {
        assertNull(handler.parse("invalid"))
        assertNull(handler.parse("a|b|c"))
    }

    @Test fun `queue and drain`() {
        handler.queueRaw("0|0|candidate:1")
        handler.queueRaw("1|1|candidate:2")
        assertEquals(2, handler.pendingCount())
        handler.clear()
        assertEquals(0, handler.pendingCount())
    }
}