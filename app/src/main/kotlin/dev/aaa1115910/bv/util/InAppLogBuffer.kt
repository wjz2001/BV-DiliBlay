package dev.aaa1115910.bv.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object InAppLogBuffer {
    private const val MAX_LOG_COUNT = 4000
    private val timeFormatter = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val logs = CopyOnWriteArrayList<String>()

    fun append(level: String, loggerName: String, message: String) {
        val timestamp = synchronized(timeFormatter) {
            timeFormatter.format(Date())
        }
        logs += "$timestamp $level/$loggerName: $message"
        trimIfNeeded()
    }

    fun snapshot(): List<String> = logs.toList()

    private fun trimIfNeeded() {
        val overflow = logs.size - MAX_LOG_COUNT
        if (overflow <= 0) return
        repeat(overflow) {
            if (logs.isNotEmpty()) {
                logs.removeAt(0)
            }
        }
    }
}
