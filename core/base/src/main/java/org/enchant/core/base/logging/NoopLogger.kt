package org.enchant.core.base.logging

/**
 * A logger that discards all messages. Used as the default logger before
 * [Log.initialize] is called, and for test environments where log output
 * is not needed.
 */
object NoopLogger : Log.Logger {
    override fun v(tag: String, message: String?, t: Throwable?) {}
    override fun d(tag: String, message: String?, t: Throwable?) {}
    override fun i(tag: String, message: String?, t: Throwable?) {}
    override fun w(tag: String, message: String?, t: Throwable?) {}
    override fun e(tag: String, message: String?, t: Throwable?) {}
}
