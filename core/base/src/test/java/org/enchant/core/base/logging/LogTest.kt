package org.enchant.core.base.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class LogTest {

    private val captured = mutableListOf<String>()

    private val testLogger = object : Log.Logger {
        override fun v(tag: String, message: String?, t: Throwable?) { captured.add("v:$tag:$message") }
        override fun d(tag: String, message: String?, t: Throwable?) { captured.add("d:$tag:$message") }
        override fun i(tag: String, message: String?, t: Throwable?) { captured.add("i:$tag:$message") }
        override fun w(tag: String, message: String?, t: Throwable?) { captured.add("w:$tag:$message") }
        override fun e(tag: String, message: String?, t: Throwable?) { captured.add("e:$tag:$message") }
    }

    @Test
    fun `initialize sets logger`() {
        Log.initialize(testLogger)
        Log.d("Tag", "msg")
        assertTrue(captured.any { it == "d:Tag:msg" })
    }

    @Test
    fun `v logs verbose`() {
        Log.initialize(testLogger)
        Log.v("Tag", "verbose")
        assertTrue(captured.any { it == "v:Tag:verbose" })
    }

    @Test
    fun `d logs debug`() {
        Log.initialize(testLogger)
        Log.d("Tag", "debug")
        assertTrue(captured.any { it == "d:Tag:debug" })
    }

    @Test
    fun `i logs info`() {
        Log.initialize(testLogger)
        Log.i("Tag", "info")
        assertTrue(captured.any { it == "i:Tag:info" })
    }

    @Test
    fun `w logs warn`() {
        Log.initialize(testLogger)
        Log.w("Tag", "warn")
        assertTrue(captured.any { it == "w:Tag:warn" })
    }

    @Test
    fun `e logs error`() {
        Log.initialize(testLogger)
        Log.e("Tag", "error")
        assertTrue(captured.any { it == "e:Tag:error" })
    }

    @Test
    fun `log with throwable includes throwable`() {
        Log.initialize(testLogger)
        Log.d("Tag", "msg", RuntimeException("test"))
        assertTrue(captured.any { it == "d:Tag:msg" })
    }

    @Test
    fun `tag from class truncates long names`() {
        val tag = Log.tag(ClassWithAVeryLongNameThatExceedsTwentyThreeChars::class.java)
        assertEquals(23, tag.length)
    }

    private class ClassWithAVeryLongNameThatExceedsTwentyThreeChars

    @Test
    fun `tag from short class returns full name`() {
        val tag = Log.tag(this.javaClass)
        assertEquals("LogTest", tag)
    }

    @Test
    fun `default logger is silent`() {
        Log.d("Tag", "should be silent")
        Log.e("Tag", "should not throw", RuntimeException())
    }

    @Test
    fun `AndroidLogger logs without throwing`() {
        Log.initialize(AndroidLogger)
        Log.v("V", "v")
        Log.d("D", "d")
        Log.i("I", "i")
        Log.w("W", "w")
        Log.e("E", "e")
        Log.d("D", "msg", RuntimeException("test"))
    }
}
