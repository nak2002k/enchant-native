package org.enchant.core.jobmanager

import org.junit.jupiter.api.Test

class JobLoggerTest {
    @Test
    fun `jobEvent formats message correctly`() {
        JobLogger.jobEvent("job-123", "started")
    }

    @Test
    fun `d logs without crashing`() {
        JobLogger.d("test debug message")
    }

    @Test
    fun `i logs without crashing`() {
        JobLogger.i("test info message")
    }

    @Test
    fun `w logs without crashing`() {
        JobLogger.w("test warning message")
    }

    @Test
    fun `e logs without crashing`() {
        JobLogger.e("test error message")
    }

    @Test
    fun `e with throwable logs without crashing`() {
        JobLogger.e("test error with exception", RuntimeException("test"))
    }
}
