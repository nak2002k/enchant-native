package org.enchant.core.base

import android.os.Process
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object ThreadPoolUtil {

    val UNBOUNDED: ExecutorService = Executors.newCachedThreadPool(
        NumberedThreadFactory("unbounded", Process.THREAD_PRIORITY_BACKGROUND)
    )

    val BOUNDED: ExecutorService = Executors.newFixedThreadPool(
        4, NumberedThreadFactory("bounded", Process.THREAD_PRIORITY_BACKGROUND)
    )

    val SERIAL: ExecutorService = Executors.newSingleThreadExecutor(
        NumberedThreadFactory("serial", Process.THREAD_PRIORITY_BACKGROUND)
    )

    val BOUNDED_IO: ExecutorService = newCachedBoundedExecutor(
        name = "io-bounded",
        priority = Process.THREAD_PRIORITY_DEFAULT,
        minThreads = 1,
        maxThreads = 32,
        timeoutSeconds = 30
    )

    class NumberedThreadFactory(
        private val name: String,
        private val priority: Int
    ) : ThreadFactory {
        private val counter = AtomicInteger(0)

        override fun newThread(r: Runnable): Thread {
            return Thread(r, "$name-${counter.incrementAndGet()}").apply {
                isDaemon = true
                Process.setThreadPriority(priority)
            }
        }
    }

    fun newCachedBoundedExecutor(
        name: String,
        priority: Int,
        minThreads: Int,
        maxThreads: Int,
        timeoutSeconds: Long
    ): ExecutorService {
        val threadPool = ThreadPoolExecutor(
            minThreads,
            maxThreads,
            timeoutSeconds,
            TimeUnit.SECONDS,
            object : LinkedBlockingQueue<Runnable>() {
                override fun offer(runnable: Runnable): Boolean {
                    return if (isEmpty()) super.offer(runnable) else false
                }
            },
            NumberedThreadFactory(name, priority)
        )

        threadPool.setRejectedExecutionHandler { runnable, executor ->
            try {
                executor.queue.put(runnable)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        return threadPool
    }
}
