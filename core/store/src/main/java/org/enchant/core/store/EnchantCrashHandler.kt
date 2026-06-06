package org.enchant.core.store

import android.app.Application
import org.enchant.core.base.SecurePreferences
import org.enchant.core.base.logging.Scrubber
import kotlin.system.exitProcess

/**
 * Uncaught exception handler that ensures all pending store writes are flushed
 * before the app crashes. This prevents data loss from writes still in the async queue.
 *
 * Also clears sensitive data from memory on crash to prevent data leakage in crash reports.
 *
 * Based on proven crash handler patterns for secure messaging apps.
 *
 * Install during Application.onCreate() BEFORE any other initialization:
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
            val scrubbedMessage = Scrubber.scrub(flushError.message)
            android.util.Log.e("EnchantCrash", "Failed to flush writes during crash: $scrubbedMessage")
        }

        clearSensitiveData()
        defaultHandler?.uncaughtException(t, e)
        exitProcess(1)
    }

    private fun clearSensitiveData() {
        try {
            SecurePreferences.clearAll()
        } catch (_: Exception) {
        }
    }

    companion object {
        fun install(application: Application) {
            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            val crashHandler = EnchantCrashHandler(currentHandler)
            Thread.setDefaultUncaughtExceptionHandler(crashHandler)
        }
    }
}
