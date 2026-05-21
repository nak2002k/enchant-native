package org.enchant.core.store

import android.app.Application
import kotlin.system.exitProcess

/**
 * Uncaught exception handler that ensures all pending store writes are flushed
 * before the app crashes. This prevents data loss from writes still in the async queue.
 *
 * Mirrors Signal's SignalUncaughtExceptionHandler behavior.
 *
 * Install during Application.onCreate():
 * ```
 * EnchantCrashHandler.install(this)
 * ```
 */
class EnchantCrashHandler(
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            EnchantStore.blockUntilAllWritesFinished()
        } catch (flushError: Exception) {
            android.util.Log.e("EnchantCrash", "Failed to flush writes during crash", flushError)
        }

        defaultHandler?.uncaughtException(t, e)
        exitProcess(1)
    }

    companion object {
        fun install(application: Application) {
            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            val crashHandler = EnchantCrashHandler(currentHandler)
            Thread.setDefaultUncaughtExceptionHandler(crashHandler)
        }
    }
}
