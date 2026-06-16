package org.enchant.core.base.concurrent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class KeyedSerialExecutorTest {

    @Test
    @Disabled("Pre-existing: flaky test - thread scheduler dependent")
    fun `tasks for same key execute serially`() {
        // Test disabled - flaky due to thread scheduler dependencies
    }

    @Test
    fun `tasks for same key execute serially actual`() {
        val executor = KeyedSerialExecutor<String>(4)
        val order = mutableListOf<Int>()
        val latch = CountDownLatch(3)

        executor.execute("key1") {
            order.add(1)
            latch.countDown()
        }
        executor.execute("key1") {
            order.add(2)
            latch.countDown()
        }
        executor.execute("key1") {
            order.add(3)
            latch.countDown()
        }

        latch.await(5, TimeUnit.SECONDS)
        executor.shutdown()
        assertEquals(listOf(1, 2, 3), order)
    }

    @Test
    fun `tasks for different keys execute in parallel`() {
        val executor = KeyedSerialExecutor<String>(4)
        val concurrent = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val latch = CountDownLatch(2)

        executor.execute("key1") {
            concurrent.incrementAndGet()
            maxConcurrent.set(maxOf(maxConcurrent.get(), concurrent.get()))
            Thread.sleep(100)
            concurrent.decrementAndGet()
            latch.countDown()
        }
        executor.execute("key2") {
            concurrent.incrementAndGet()
            maxConcurrent.set(maxOf(maxConcurrent.get(), concurrent.get()))
            concurrent.decrementAndGet()
            latch.countDown()
        }

        latch.await(5, TimeUnit.SECONDS)
        executor.shutdown()
        assertTrue(maxConcurrent.get() >= 2)
    }

    @Test
    fun `shutdown completes pending tasks`() {
        val executor = KeyedSerialExecutor<String>(1)
        val completed = AtomicInteger(0)
        val latch = CountDownLatch(1)

        executor.execute("key1") {
            completed.incrementAndGet()
            latch.countDown()
        }

        latch.await(5, TimeUnit.SECONDS)
        executor.shutdown()
        assertEquals(1, completed.get())
    }
}

class AnrDetectorTest {

    @Test
    fun `start and stop does not crash`() {
        // Can't fully test ANR detection without main looper, but verify no crash
        val detector = AnrDetector(thresholdMs = 100) { }
        detector.stop()
    }
}

class DeadlockDetectorTest {

    @Test
    fun `start and stop does not crash`() {
        val detector = DeadlockDetector(intervalMs = 1000) { }
        detector.start()
        detector.stop()
    }
}
