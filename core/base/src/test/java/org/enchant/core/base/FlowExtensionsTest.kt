package org.enchant.core.base

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class FlowExtensionsTest {

    @Test
    fun `throttleLatest emits first value immediately`() = runTest {
        val source = flow { emit(1); emit(2); emit(3) }
        val result = source.throttleLatest(50.milliseconds).toList()
        assertEquals(listOf(1, 3), result)
    }

    @Test
    fun `throttleLatest with emitImmediately skips throttling`() = runTest {
        val source = flow { emit(1) }
        val result = source.throttleLatest(50.milliseconds, emitImmediately = { it == 1 }).toList()
        assertEquals(listOf(1), result)
    }

    @Test
    fun `throttleLatest emits nothing for empty flow`() = runTest {
        val source = flow<Int> { }
        val result = source.throttleLatest(50.milliseconds).toList()
        assertEquals(emptyList<Int>(), result)
    }
}
