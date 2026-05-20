package org.enchant.core.base.logging

/**
 * A logger that multiplexes log calls to multiple delegate loggers.
 *
 * Use this when you need to write logs to more than one destination
 * simultaneously (e.g., logcat + persistent file + crash reporter).
 *
 * Usage:
 * ```
 * val compound = CompoundLogger(AndroidLogger, PersistentLogger(context))
 * Log.initialize(compound)
 * ```
 */
class CompoundLogger(private vararg val delegates: Log.Logger) : Log.Logger {

    override fun v(tag: String, message: String?, t: Throwable?) {
        for (delegate in delegates) delegate.v(tag, message, t)
    }

    override fun d(tag: String, message: String?, t: Throwable?) {
        for (delegate in delegates) delegate.d(tag, message, t)
    }

    override fun i(tag: String, message: String?, t: Throwable?) {
        for (delegate in delegates) delegate.i(tag, message, t)
    }

    override fun w(tag: String, message: String?, t: Throwable?) {
        for (delegate in delegates) delegate.w(tag, message, t)
    }

    override fun e(tag: String, message: String?, t: Throwable?) {
        for (delegate in delegates) delegate.e(tag, message, t)
    }

    override fun flush() {
        for (delegate in delegates) delegate.flush()
    }

    override fun blockUntilAllWritesFinished() {
        for (delegate in delegates) delegate.blockUntilAllWritesFinished()
    }
}
