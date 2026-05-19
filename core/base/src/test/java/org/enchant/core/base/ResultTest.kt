package org.enchant.core.base

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResultTest {

    @Test
    fun `success creates Success with value`() {
        val result = Result.success(42)
        assertTrue(result.isSuccess())
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `failure creates Failure with error`() {
        val result = Result.failure("error")
        assertTrue(result.isFailure())
        assertEquals("error", result.failureOrNull())
    }

    @Test
    fun `map transforms success value`() {
        val result = Result.success(42)
        val mapped = result.map { it * 2 }
        assertEquals(84, mapped.getOrNull())
    }

    @Test
    fun `map passes through failure`() {
        val result: Result<Int, String> = Result.failure("err")
        val mapped = result.map { it * 2 }
        assertEquals("err", mapped.failureOrNull())
    }

    @Test
    fun `either returns success branch`() {
        val result = Result.success(42)
        val output = result.either({ "got $it" }, { "fail: $it" })
        assertEquals("got 42", output)
    }

    @Test
    fun `either returns failure branch`() {
        val result: Result<Int, String> = Result.failure("err")
        val output = result.either({ "got $it" }, { "fail: $it" })
        assertEquals("fail: err", output)
    }

    @Test
    fun `flatMap chains success results`() {
        val result = Result.success(42)
        val chained = result.flatMap { Result.success(it * 2) }
        assertEquals(84, chained.getOrNull())
    }

    @Test
    fun `flatMap passes through failure`() {
        val result: Result<Int, String> = Result.failure("err")
        val chained = result.flatMap { Result.success(it * 2) }
        assertEquals("err", chained.failureOrNull())
    }

    @Test
    fun `getOrElse returns default for failure`() {
        val result: Result<Int, String> = Result.failure("err")
        assertEquals(0, result.getOrElse(0))
    }

    @Test
    fun `getOrElse returns value for success`() {
        val result = Result.success(42)
        assertEquals(42, result.getOrElse(0))
    }

    @Test
    fun `Try alias works with Throwable`() {
        val result: Try<Int> = Result.failure(RuntimeException("boom"))
        assertTrue(result.isFailure())
        assertEquals("boom", result.failureOrNull()?.message)
    }

    @Test
    fun `getOrNull returns null for failure`() {
        val result: Result<Int, String> = Result.failure("err")
        assertNull(result.getOrNull())
    }

    @Test
    fun `failureOrNull returns null for success`() {
        val result = Result.success(42)
        assertNull(result.failureOrNull())
    }
}
