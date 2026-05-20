package org.enchant.core.base.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogTest {

    @Test
    fun `default logger is NoopLogger`() {
        Log.initialize(NoopLogger)
        Log.d("Test", "message")
    }

    @Test
    fun `tag truncates to 23 characters max`() {
        val tag = Log.tag(LogTest::class.java)
        assertTrue(tag.length <= 23)
    }

    @Test
    fun `tag uses simple class name`() {
        val tag = Log.tag(LogTest::class.java)
        assertEquals("LogTest", tag)
    }

    @Test
    fun `internal returns disabled logger by default`() {
        val internal = Log.internal()
        internal.d("Test", "should not appear")
    }

    @Test
    fun `internal returns enabled logger when check is true`() {
        Log.setInternalCheck(object : Log.InternalCheck {
            override fun isInternal(): Boolean = true
        })
        Log.initialize(NoopLogger)
        val internal = Log.internal()
        internal.d("Test", "message")
        Log.setInternalCheck(object : Log.InternalCheck {
            override fun isInternal(): Boolean = false
        })
    }

    @Test
    fun `flush does not throw`() {
        Log.initialize(NoopLogger)
        Log.flush()
    }

    @Test
    fun `blockUntilAllWritesFinished does not throw`() {
        Log.initialize(NoopLogger)
        Log.blockUntilAllWritesFinished()
    }

    @Test
    fun `tag from KClass works`() {
        val tag = Log.tag(LogTest::class)
        assertEquals("LogTest", tag)
    }
}

class NoopLoggerTest {

    @Test
    fun `all methods are no-op`() {
        NoopLogger.v("tag", "msg", null)
        NoopLogger.d("tag", "msg", null)
        NoopLogger.i("tag", "msg", null)
        NoopLogger.w("tag", "msg", null)
        NoopLogger.e("tag", "msg", null)
        NoopLogger.flush()
        NoopLogger.blockUntilAllWritesFinished()
    }
}

class CompoundLoggerTest {

    @Test
    fun `dispatches to all delegates`() {
        val logger1 = TrackingLogger()
        val logger2 = TrackingLogger()
        val compound = CompoundLogger(logger1, logger2)

        compound.d("tag", "hello", null)
        assertEquals(1, logger1.debugCount)
        assertEquals(1, logger2.debugCount)
    }

    @Test
    fun `flush calls all delegates`() {
        val logger1 = TrackingLogger()
        val logger2 = TrackingLogger()
        val compound = CompoundLogger(logger1, logger2)

        compound.flush()
        assertEquals(1, logger1.flushCount)
        assertEquals(1, logger2.flushCount)
    }

    @Test
    fun `blockUntilAllWritesFinished calls all delegates`() {
        val logger1 = TrackingLogger()
        val logger2 = TrackingLogger()
        val compound = CompoundLogger(logger1, logger2)

        compound.blockUntilAllWritesFinished()
        assertEquals(1, logger1.blockCount)
        assertEquals(1, logger2.blockCount)
    }

    @Test
    fun `handles empty delegate list`() {
        val compound = CompoundLogger()
        compound.d("tag", "msg", null)
        compound.flush()
        compound.blockUntilAllWritesFinished()
    }

    private class TrackingLogger : Log.Logger {
        var debugCount = 0
        var flushCount = 0
        var blockCount = 0

        override fun v(tag: String, message: String?, t: Throwable?) {}
        override fun d(tag: String, message: String?, t: Throwable?) { debugCount++ }
        override fun i(tag: String, message: String?, t: Throwable?) {}
        override fun w(tag: String, message: String?, t: Throwable?) {}
        override fun e(tag: String, message: String?, t: Throwable?) {}
        override fun flush() { flushCount++ }
        override fun blockUntilAllWritesFinished() { blockCount++ }
    }
}
