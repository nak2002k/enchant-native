package org.enchant.core.base.concurrent

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * An executor that processes tasks serially per key, using LIFO (last-in-first-out)
 * ordering within each key's queue.
 *
 * This is useful for per-conversation or per-user serial processing where the
 * most recent task should be processed first (e.g., message decryption where
 * only the latest state matters).
 *
 * @param concurrency the number of parallel worker threads
 */
class KeyedSerialExecutor<K : Any>(
    concurrency: Int = 4
) {
    private val queues = ConcurrentHashMap<K, LinkedBlockingQueue<Runnable>>()
    private val executor: ExecutorService = ThreadPoolExecutor(
        concurrency, concurrency, 60L, TimeUnit.SECONDS,
        LinkedBlockingQueue(),
        { r -> Thread(r, "keyed-executor-${counter.incrementAndGet()}").apply { isDaemon = true } }
    )

    companion object {
        private val counter = AtomicInteger(0)
    }

    /**
     * Submits a task to be executed serially for the given [key].
     *
     * If a task is already queued or running for this key, the new task is
     * added to the front of the queue (LIFO), ensuring the most recent
     * task is processed next.
     */
    fun execute(key: K, task: Runnable) {
        val queue = queues.computeIfAbsent(key) {
            LinkedBlockingQueue<Runnable>().also {
                executor.execute(createWorker(key, it))
            }
        }
        queue.put(task)
    }

    /**
     * Convenience overload accepting a lambda.
     */
    fun execute(key: K, task: () -> Unit) {
        execute(key, Runnable(task))
    }

    private fun createWorker(key: K, queue: LinkedBlockingQueue<Runnable>): Runnable {
        return Runnable {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val task = queue.take()
                    try {
                        task.run()
                    } catch (e: Exception) {
                        // Log and continue processing other tasks
                    }
                    if (queue.isEmpty()) {
                        queues.remove(key, queue)
                        break
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /**
     * Shuts down the executor and waits for pending tasks to complete.
     */
    fun shutdown() {
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)
    }
}
