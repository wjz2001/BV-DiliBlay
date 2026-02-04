package dev.aaa1115910.bv.util

import dev.aaa1115910.bv.BuildConfig
import io.github.oshai.kotlinlogging.KLogger

fun KLogger.fInfo(msg: () -> Any?) {
    info(msg)
}

fun KLogger.fWarn(msg: () -> Any?) {
    warn(msg)
}

fun KLogger.fDebug(msg: () -> Any?) {
    if (BuildConfig.DEBUG) {
        info(msg)
    }
}

fun KLogger.fError(msg: () -> Any?) {
    error(msg)
}

fun KLogger.fException(throwable: Throwable, msg: () -> Any?) {
    warn { "$msg: ${throwable.stackTraceToString()}" }
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
