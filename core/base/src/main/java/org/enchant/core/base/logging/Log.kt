package org.enchant.core.base.logging

import kotlin.reflect.KClass

/**
 * Centralized logging facade with pluggable backends, PII scrubbing, and
 * conditional verbose logging for internal builds.
 *
 * All log calls pass through the configured [Logger] implementation. The
 * default is a no-op logger; call [initialize] with [AndroidLogger] or a
 * [CompoundLogger] at app startup.
 *
 * Usage:
 * ```
 * Log.initialize(AndroidLogger)
 * Log.d(TAG, "User logged in")
 * Log.w(TAG, "Retry attempt", exception)
 *
 * // Internal-only verbose logging:
 * Log.internal().d(TAG, "Packet dump: ${hexDump}")
 * ```
 */
object Log {

    @Volatile
    private var logger: Logger = NoopLogger
    @Volatile
    private var internalCheck: InternalCheck = NoInternalCheck

    /**
     * Sets the active logger. Thread-safe; safe to call at any time.
     */
    fun initialize(logger: Logger) {
        this.logger = logger
    }

    /**
     * Sets the internal check that controls whether [internal] logs are emitted.
     */
    fun setInternalCheck(check: InternalCheck) {
        this.internalCheck = check
    }

    // -- Standard logging -----------------------------------------------------------------

    fun v(tag: String, message: String) = logger.v(tag, message, null)
    fun v(tag: String, message: String, t: Throwable?) = logger.v(tag, message, t)
    fun d(tag: String, message: String) = logger.d(tag, message, null)
    fun d(tag: String, message: String, t: Throwable?) = logger.d(tag, message, t)
    fun i(tag: String, message: String) = logger.i(tag, message, null)
    fun i(tag: String, message: String, t: Throwable?) = logger.i(tag, message, t)
    fun w(tag: String, message: String) = logger.w(tag, message, null)
    fun w(tag: String, message: String, t: Throwable?) = logger.w(tag, message, t)
    fun e(tag: String, message: String) = logger.e(tag, message, null)
    fun e(tag: String, message: String, t: Throwable?) = logger.e(tag, message, t)

    // -- Internal (conditional verbose) logging -------------------------------------------

    /**
     * Returns a logging interface that only emits logs when [InternalCheck] returns true.
     *
     * Use this for verbose debugging information that should only appear in
     * internal/beta builds or when explicitly enabled by the user.
     *
     * ```
     * Log.internal().d(TAG, "Raw bytes: ${data.toHexString()}")
     * ```
     */
    fun internal(): InternalLogger {
        return if (internalCheck.isInternal()) InternalLoggerEnabled else InternalLoggerDisabled
    }

    // -- Flush and synchronization --------------------------------------------------------

    /**
     * Hints to the logger that pending log writes should be flushed to their
     * destination. Useful before crash reporting to ensure recent logs survive.
     */
    fun flush() {
        logger.flush()
    }

    /**
     * Blocks until all pending log writes have completed.
     *
     * Use this in crash handlers to ensure that the last log entries are
     * persisted before the process terminates.
     */
    fun blockUntilAllWritesFinished() {
        logger.blockUntilAllWritesFinished()
    }

    // -- Tag helpers ----------------------------------------------------------------------

    /**
     * Creates a log tag from a Java class, truncating to 23 characters
     * (Android's log tag limit).
     */
    fun tag(clazz: Class<*>): String {
        val simpleName = clazz.simpleName
        return if (simpleName.length > 23) simpleName.substring(0, 23) else simpleName
    }

    fun tag(clazz: KClass<*>): String = tag(clazz.java)

    // -- Interfaces -----------------------------------------------------------------------

    interface Logger {
        fun v(tag: String, message: String?, t: Throwable?)
        fun d(tag: String, message: String?, t: Throwable?)
        fun i(tag: String, message: String?, t: Throwable?)
        fun w(tag: String, message: String?, t: Throwable?)
        fun e(tag: String, message: String?, t: Throwable?)

        /** Flushes pending log writes. Default: no-op. */
        fun flush() {}

        /** Blocks until all pending writes complete. Default: no-op. */
        fun blockUntilAllWritesFinished() {}
    }

    interface InternalCheck {
        fun isInternal(): Boolean
    }

    interface InternalLogger {
        fun v(tag: String, message: String)
        fun d(tag: String, message: String)
        fun i(tag: String, message: String)
        fun w(tag: String, message: String)
        fun e(tag: String, message: String)
    }

    private object NoInternalCheck : InternalCheck {
        override fun isInternal(): Boolean = false
    }

    private object InternalLoggerEnabled : InternalLogger {
        override fun v(tag: String, message: String) = logger.v(tag, message, null)
        override fun d(tag: String, message: String) = logger.d(tag, message, null)
        override fun i(tag: String, message: String) = logger.i(tag, message, null)
        override fun w(tag: String, message: String) = logger.w(tag, message, null)
        override fun e(tag: String, message: String) = logger.e(tag, message, null)
    }

    private object InternalLoggerDisabled : InternalLogger {
        override fun v(tag: String, message: String) {}
        override fun d(tag: String, message: String) {}
        override fun i(tag: String, message: String) {}
        override fun w(tag: String, message: String) {}
        override fun e(tag: String, message: String) {}
    }
}
