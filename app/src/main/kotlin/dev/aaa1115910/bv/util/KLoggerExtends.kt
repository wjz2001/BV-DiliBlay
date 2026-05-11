package dev.aaa1115910.bv.util

import dev.aaa1115910.bv.BuildConfig
import io.github.oshai.kotlinlogging.KLogger

fun KLogger.fInfo(msg: () -> Any?) {
    val message = msg.toStringSafe()
    InAppLogBuffer.append(
        level = "INFO",
        loggerName = name,
        message = message
    )
    info { message }
}

fun KLogger.fWarn(msg: () -> Any?) {
    val message = msg.toStringSafe()
    InAppLogBuffer.append(
        level = "WARN",
        loggerName = name,
        message = message
    )
    warn { message }
}

fun KLogger.fDebug(msg: () -> Any?) {
    if (BuildConfig.DEBUG) {
        val message = msg.toStringSafe()
        InAppLogBuffer.append(
            level = "DEBUG",
            loggerName = name,
            message = message
        )
        debug { message }
    }
}

fun KLogger.fError(msg: () -> Any?) {
    val message = msg.toStringSafe()
    InAppLogBuffer.append(
        level = "ERROR",
        loggerName = name,
        message = message
    )
    error { message }
}

fun KLogger.fException(throwable: Throwable, msg: () -> Any?) {
    val message = msg.toStringSafe()
    InAppLogBuffer.append(
        level = "ERROR",
        loggerName = name,
        message = "$message\n${throwable.stackTraceToString()}"
    )
    error(throwable) { message }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun (() -> Any?).toStringSafe(): String {
    return try {
        invoke().toString()
    } catch (e: Exception) {
        ErrorMessageProducer.getErrorLog(e)
    }
}

internal object ErrorMessageProducer {
    fun getErrorLog(e: Exception): String {
        if (System.getProperties().containsKey("kotlin-logging.throwOnMessageError")) {
            throw e
        } else {
            return "Log message invocation failed: $e"
        }
    }
}
