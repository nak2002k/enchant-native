package org.enchant.core.base

import android.os.HandlerThread
import android.os.Process
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object EnchantExecutors {

    val UNBOUNDED: ExecutorService = ThreadPoolExecutor(
        2, 64, 60L, TimeUnit.SECONDS,
        LinkedBlockingQueue(1024),
        NumberedThreadFactory("unbounded", PRIORITY_BACKGROUND_THREAD)
    ) { runnable, _ -> runnable.run() }

    val BOUNDED: ExecutorService = Executors.newFixedThreadPool(
        4, NumberedThreadFactory("bounded", PRIORITY_BACKGROUND_THREAD)
    )

    val SERIAL: ExecutorService = Executors.newSingleThreadExecutor(
        NumberedThreadFactory("serial", PRIORITY_BACKGROUND_THREAD)
    )

    val BOUNDED_IO: ExecutorService = newCachedBoundedExecutor(
        name = "io-bounded",
        priority = PRIORITY_IMPORTANT_BACKGROUND_THREAD,
        minThreads = 1,
        maxThreads = 32,
        timeoutSeconds = 30
    )

    fun newCachedSingleThreadExecutor(name: String, priority: Int): ExecutorService {
        val executor = ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS, LinkedBlockingQueue()) { r ->
            Thread(r, name).apply {
                isDaemon = true
                Process.setThreadPriority(priority)
            }
        }
        executor.allowCoreThreadTimeOut(true)
        return executor
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

    fun getAndStartHandlerThread(name: String, priority: Int): HandlerThread {
        return HandlerThread(name, priority).apply { start() }
    }

    class NumberedThreadFactory(
        private val baseName: String,
        private val priority: Int
    ) : ThreadFactory {
        private val counter = AtomicInteger(0)

        override fun newThread(r: Runnable): Thread {
            return object : Thread(r, "$baseName-${counter.incrementAndGet()}") {
                override fun run() {
                    Process.setThreadPriority(priority)
                    super.run()
                }
            }.apply { isDaemon = true }
        }
    }

    private const val PRIORITY_BACKGROUND_THREAD = Process.THREAD_PRIORITY_BACKGROUND
    private const val PRIORITY_IMPORTANT_BACKGROUND_THREAD =
        Process.THREAD_PRIORITY_DEFAULT + Process.THREAD_PRIORITY_LESS_FAVORABLE
}
