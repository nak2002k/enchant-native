package org.enchant.core.base

import org.enchant.core.base.logging.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StopwatchTest {

    private var capturedLog = ""

    private val testLogger = object : Log.Logger {
        override fun v(tag: String, message: String?, t: Throwable?) {}
        override fun d(tag: String, message: String?, t: Throwable?) { capturedLog = message ?: "" }
        override fun i(tag: String, message: String?, t: Throwable?) {}
        override fun w(tag: String, message: String?, t: Throwable?) {}
        override fun e(tag: String, message: String?, t: Throwable?) {}
    }

    @Test
    fun `split records a named split`() {
        val watch = Stopwatch("test")
        watch.split("first")
        val log = watch.stopAndGetLogString()
        assertTrue(log.contains("first"))
        assertTrue(log.contains("total"))
    }

    @Test
    fun `stopAndGetLogString includes title`() {
        val watch = Stopwatch("my-event")
        watch.split("step1")
        val log = watch.stopAndGetLogString()
        assertTrue(log.contains("[my-event]"))
    }

    @Test
    fun `stop logs via Log object`() {
        capturedLog = ""
        Log.initialize(testLogger)
        Log.initialize(testLogger)
        val watch = Stopwatch("test")
        watch.split("a")
        watch.stop("TAG")
        assertTrue(capturedLog.contains("[test]"))
    }

    @Test
    fun `logTime measures block execution`() {
        capturedLog = ""
        Log.initialize(testLogger)
        val result = logTime("TAG", "compute") { 42 }
        assertEquals(42, result)
        assertTrue(capturedLog.contains("compute"))
    }

    @Test
    fun `multiple splits produce ordered output`() {
        val watch = Stopwatch("multi")
        watch.split("start")
        watch.split("middle")
        val log = watch.stopAndGetLogString()
        assertTrue(log.indexOf("start") < log.indexOf("middle"))
        assertTrue(log.indexOf("middle") < log.indexOf("total"))
    }

    @Test
    fun `logTime returns block value`() {
        val result = logTime("TAG", "test") { "hello" }
        assertEquals("hello", result)
    }
}
