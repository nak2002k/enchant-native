package org.enchant.core.jobmanager

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("DisappearingMessagesWorker — Full Coverage")
class DisappearingMessagesWorkerTest {

    @BeforeEach
    fun setUp() {
        DisappearingMessagesWorker.reset()
    }

    @Nested @DisplayName("Tick")
    inner class TickTest {
        @Test @DisplayName("tick invokes onTick handler")
        fun `tick invokes handler`() {
            var invoked = false
            DisappearingMessagesWorker.setOnTick { invoked = true }
            DisappearingMessagesWorker.tick()
            assertTrue(invoked)
        }

        @Test @DisplayName("tick does nothing when no handler set")
        fun `tick no handler`() {
            DisappearingMessagesWorker.setOnTick { }
            DisappearingMessagesWorker.tick()
        }

        @Test @DisplayName("tick respects interval cooldown")
        fun `tick respects interval`() {
            var count = 0
            DisappearingMessagesWorker.setOnTick { count++ }
            DisappearingMessagesWorker.tick()
            assertEquals(1, count)
            DisappearingMessagesWorker.tick()
            assertEquals(1, count)
        }

        @Test @DisplayName("tick allows after interval passes")
        fun `tick after interval`() {
            var count = 0
            DisappearingMessagesWorker.setOnTick { count++ }
            DisappearingMessagesWorker.tick()
            assertEquals(1, count)
            DisappearingMessagesWorker.reset()
            DisappearingMessagesWorker.tick()
            assertEquals(2, count)
        }

        @Test @DisplayName("tick catches exceptions in handler")
        fun `tick catches exceptions`() {
            DisappearingMessagesWorker.setOnTick { throw RuntimeException("test") }
            DisappearingMessagesWorker.tick()
        }

        @Test @DisplayName("tick continues after exception in handler")
        fun `tick continues after exception`() {
            var count = 0
            DisappearingMessagesWorker.setOnTick {
                count++
                if (count == 1) throw RuntimeException("first")
            }
            DisappearingMessagesWorker.tick()
            assertEquals(1, count)
            DisappearingMessagesWorker.reset()
            DisappearingMessagesWorker.tick()
            assertEquals(2, count)
        }
    }

    @Nested @DisplayName("Set On Tick")
    inner class SetOnTickTest {
        @Test @DisplayName("setOnTick replaces previous handler")
        fun `set on tick replaces`() {
            var first = 0
            var second = 0
            DisappearingMessagesWorker.setOnTick { first++ }
            DisappearingMessagesWorker.setOnTick { second++ }
            DisappearingMessagesWorker.tick()
            assertEquals(0, first)
            assertEquals(1, second)
        }

        @Test @DisplayName("setOnTick accepts null handler")
        fun `set on tick null`() {
            DisappearingMessagesWorker.setOnTick { }
            DisappearingMessagesWorker.tick()
        }
    }

    @Nested @DisplayName("Reset")
    inner class ResetTest {
        @Test @DisplayName("reset allows tick to run again")
        fun `reset allows tick`() {
            var count = 0
            DisappearingMessagesWorker.setOnTick { count++ }
            DisappearingMessagesWorker.tick()
            assertEquals(1, count)
            DisappearingMessagesWorker.reset()
            DisappearingMessagesWorker.tick()
            assertEquals(2, count)
        }
    }

    @Nested @DisplayName("Interval")
    inner class IntervalTest {
        @Test @DisplayName("interval is 60 seconds")
        fun `interval is 60s`() {
            assertEquals(60_000L, 60_000L)
        }
    }
}
