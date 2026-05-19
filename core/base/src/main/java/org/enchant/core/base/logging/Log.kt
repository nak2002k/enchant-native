package org.enchant.core.base.logging

import kotlin.reflect.KClass

object Log {

    private var logger: Logger = DefaultLogger

    fun initialize(logger: Logger) {
        this.logger = logger
    }

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

    fun tag(clazz: Class<*>): String {
        val simpleName = clazz.simpleName
        return if (simpleName.length > 23) simpleName.substring(0, 23) else simpleName
    }

    fun tag(clazz: KClass<*>): String = tag(clazz.java)

    interface Logger {
        fun v(tag: String, message: String?, t: Throwable?)
        fun d(tag: String, message: String?, t: Throwable?)
        fun i(tag: String, message: String?, t: Throwable?)
        fun w(tag: String, message: String?, t: Throwable?)
        fun e(tag: String, message: String?, t: Throwable?)
    }

    private object DefaultLogger : Logger {
        override fun v(tag: String, message: String?, t: Throwable?) {}
        override fun d(tag: String, message: String?, t: Throwable?) {}
        override fun i(tag: String, message: String?, t: Throwable?) {}
        override fun w(tag: String, message: String?, t: Throwable?) {}
        override fun e(tag: String, message: String?, t: Throwable?) {}
    }
}
