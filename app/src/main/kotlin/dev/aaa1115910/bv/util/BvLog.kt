package dev.aaa1115910.bv.util

import android.util.Log

object BvLog {
    fun i(tag: String, message: String) {
        InAppLogBuffer.append(
            level = "INFO",
            loggerName = tag,
            message = message
        )
        Log.i(tag, message)
    }

    fun w(tag: String, message: String) {
        InAppLogBuffer.append(
            level = "WARN",
            loggerName = tag,
            message = message
        )
        Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        InAppLogBuffer.append(
            level = "ERROR",
            loggerName = tag,
            message = if (throwable == null) message else "$message\n${throwable.stackTraceToString()}"
        )
        if (throwable == null) {
            Log.e(tag, message)
        } else {
            Log.e(tag, message, throwable)
        }
    }
}
