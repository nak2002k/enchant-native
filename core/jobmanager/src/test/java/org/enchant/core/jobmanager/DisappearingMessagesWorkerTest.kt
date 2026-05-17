package org.enchant.core.jobmanager

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@DisplayName("DisappearingMessagesWorker")
class DisappearingMessagesWorkerTest {

    @BeforeEach
    fun setUp() {
        DisappearingMessagesWorker.reset()
    }

    @Test
    @DisplayName("tick does not throw when handler not set")
    fun `tick without handler`() {
        assertDoesNotThrow { DisappearingMessagesWorker.tick() }
    }

    @Test
    @DisplayName("tick calls handler when set")
    fun `tick calls handler`() {
        var called = false
        DisappearingMessagesWorker.setOnTick { called = true }
        DisappearingMessagesWorker.tick()
        assertTrue(called)
    }

    @Test
    @DisplayName("tick respects interval (calls only once within 60s)")
    fun `tick interval`() {
        var callCount = 0
        DisappearingMessagesWorker.setOnTick { callCount++ }
        DisappearingMessagesWorker.tick()
        DisappearingMessagesWorker.tick() // second call within interval should be skipped
        assertEquals(1, callCount) // only first call executes
    }

    @Test
    @DisplayName("handler exception does not propagate")
    fun `handler exception handled`() {
        DisappearingMessagesWorker.setOnTick { throw RuntimeException("test error") }
        assertDoesNotThrow { DisappearingMessagesWorker.tick() }
    }
}
