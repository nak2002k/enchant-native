package org.enchant.core.base.logging

import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Android platform logger that writes to [android.util.Log].
 *
 * All log operations are dispatched to a single-threaded executor to prevent
 * log interleaving when multiple threads write simultaneously. This ensures
 * that log entries appear in chronological order in logcat.
 *
 * The executor uses a bounded queue with a rejection policy that runs the
 * task in the calling thread if the queue is full, preventing log loss under
 * heavy load.
 */
object AndroidLogger : Log.Logger {

    private val logExecutor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(1024),
        { r -> Thread(r, "enchant-log-writer").apply { isDaemon = true } }
    ).apply {
        setRejectedExecutionHandler { runnable, _ -> runnable.run() }
    }

    override fun v(tag: String, message: String?, t: Throwable?) {
        logExecutor.execute {
            if (message != null && t != null) android.util.Log.v(tag, message, t)
            else if (message != null) android.util.Log.v(tag, message)
            else if (t != null) android.util.Log.v(tag, "", t)
        }
    }

    override fun d(tag: String, message: String?, t: Throwable?) {
        logExecutor.execute {
            if (message != null && t != null) android.util.Log.d(tag, message, t)
            else if (message != null) android.util.Log.d(tag, message)
            else if (t != null) android.util.Log.d(tag, "", t)
        }
    }

    override fun i(tag: String, message: String?, t: Throwable?) {
        logExecutor.execute {
            if (message != null && t != null) android.util.Log.i(tag, message, t)
            else if (message != null) android.util.Log.i(tag, message)
            else if (t != null) android.util.Log.i(tag, "", t)
        }
    }

    override fun w(tag: String, message: String?, t: Throwable?) {
        logExecutor.execute {
            if (message != null && t != null) android.util.Log.w(tag, message, t)
            else if (message != null) android.util.Log.w(tag, message)
            else if (t != null) android.util.Log.w(tag, "", t)
        }
    }

    override fun e(tag: String, message: String?, t: Throwable?) {
        logExecutor.execute {
            if (message != null && t != null) android.util.Log.e(tag, message, t)
            else if (message != null) android.util.Log.e(tag, message)
            else if (t != null) android.util.Log.e(tag, "", t)
        }
    }

    override fun flush() {
    }

    override fun blockUntilAllWritesFinished() {
        val latch = CountDownLatch(1)
        logExecutor.execute { latch.countDown() }
        latch.await(5, TimeUnit.SECONDS)
    }
}
