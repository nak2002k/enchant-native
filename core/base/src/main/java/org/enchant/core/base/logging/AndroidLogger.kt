package org.enchant.core.base.logging

object AndroidLogger : Log.Logger {

    override fun v(tag: String, message: String?, t: Throwable?) {
        if (message != null && t != null) android.util.Log.v(tag, message, t)
        else if (message != null) android.util.Log.v(tag, message)
        else if (t != null) android.util.Log.v(tag, "", t)
    }

    override fun d(tag: String, message: String?, t: Throwable?) {
        if (message != null && t != null) android.util.Log.d(tag, message, t)
        else if (message != null) android.util.Log.d(tag, message)
        else if (t != null) android.util.Log.d(tag, "", t)
    }

    override fun i(tag: String, message: String?, t: Throwable?) {
        if (message != null && t != null) android.util.Log.i(tag, message, t)
        else if (message != null) android.util.Log.i(tag, message)
        else if (t != null) android.util.Log.i(tag, "", t)
    }

    override fun w(tag: String, message: String?, t: Throwable?) {
        if (message != null && t != null) android.util.Log.w(tag, message, t)
        else if (message != null) android.util.Log.w(tag, message)
        else if (t != null) android.util.Log.w(tag, "", t)
    }

    override fun e(tag: String, message: String?, t: Throwable?) {
        if (message != null && t != null) android.util.Log.e(tag, message, t)
        else if (message != null) android.util.Log.e(tag, message)
        else if (t != null) android.util.Log.e(tag, "", t)
    }
}
