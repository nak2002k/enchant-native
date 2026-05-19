package org.enchant.core.base

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ThreadPoolUtilTest {

    @Test
    fun `UNBOUNDED executor runs tasks`() {
        val latch = CountDownLatch(1)
        ThreadPoolUtil.UNBOUNDED.execute { latch.countDown() }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun `BOUNDED executor runs tasks`() {
        val latch = CountDownLatch(1)
        ThreadPoolUtil.BOUNDED.execute { latch.countDown() }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun `SERIAL executor runs tasks in order`() {
        val result = mutableListOf<Int>()
        val latch = CountDownLatch(3)
        ThreadPoolUtil.SERIAL.execute { result.add(1); latch.countDown() }
        ThreadPoolUtil.SERIAL.execute { result.add(2); latch.countDown() }
        ThreadPoolUtil.SERIAL.execute { result.add(3); latch.countDown() }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertTrue(result.size == 3)
    }

    @Test
    fun `BOUNDED_IO executor runs tasks`() {
        val latch = CountDownLatch(1)
        ThreadPoolUtil.BOUNDED_IO.execute { latch.countDown() }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun `NumberedThreadFactory creates named threads`() {
        val factory = ThreadPoolUtil.NumberedThreadFactory("test", Thread.NORM_PRIORITY)
        val thread = factory.newThread { }
        assertTrue(thread.name.startsWith("test-"))
    }

    @Test
    fun `NumberedThreadFactory increments counter`() {
        val factory = ThreadPoolUtil.NumberedThreadFactory("counter", Thread.NORM_PRIORITY)
        val t1 = factory.newThread { }
        val t2 = factory.newThread { }
        assertTrue(t2.name.endsWith("-2"))
    }

    @Test
    fun `newCachedBoundedExecutor runs and completes tasks`() {
        val executor = ThreadPoolUtil.newCachedBoundedExecutor("bounded-test", Thread.NORM_PRIORITY, 1, 4, 30)
        try {
            val latch = CountDownLatch(5)
            repeat(5) { executor.execute { latch.countDown() } }
            assertTrue(latch.await(2, TimeUnit.SECONDS))
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun `executor threads are daemon`() {
        val factory = ThreadPoolUtil.NumberedThreadFactory("daemon-test", Thread.NORM_PRIORITY)
        val thread = factory.newThread { }
        assertTrue(thread.isDaemon)
    }
}
