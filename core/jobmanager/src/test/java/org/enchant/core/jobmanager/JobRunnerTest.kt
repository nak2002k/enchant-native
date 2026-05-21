package org.enchant.core.jobmanager

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JobRunnerTest {
    @Test
    fun `runner name is set correctly`() {
        val runner = TestableJobRunner("CustomRunner-1", 0)
        assertEquals("CustomRunner-1", runner.name)
    }

    @Test
    fun `runner starts and stops`() {
        val runner = TestableJobRunner("TestRunner", 100)
        runner.start()
        Thread.sleep(200)
        runner.stopRunner()
        runner.join(1000)
        assertFalse(runner.isAlive)
    }

    @Test
    fun `runner with zero idle timeout runs until stopped`() {
        val runner = TestableJobRunner("TestRunner", 0)
        runner.start()
        Thread.sleep(100)
        assertTrue(runner.isAlive)
        runner.stopRunner()
        runner.join(1000)
        assertFalse(runner.isAlive)
    }

    private class TestableJobRunner(
        name: String,
        private val idleTimeoutMs: Long
    ) : Thread(name) {
        @Volatile
        private var running = true

        override fun run() {
            while (running) {
                if (idleTimeoutMs > 0) {
                    try {
                        sleep(idleTimeoutMs)
                    } catch (_: InterruptedException) {
                    }
                    running = false
                } else {
                    try {
                        sleep(50)
                    } catch (_: InterruptedException) {
                    }
                }
            }
        }

        fun stopRunner() {
            running = false
            interrupt()
        }
    }
}
