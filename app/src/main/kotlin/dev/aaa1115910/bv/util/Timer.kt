package dev.aaa1115910.bv.util

import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.Timer
import java.util.TimerTask

fun timeTask(
    delay: Long,
    period: Long,
    tag: String,
    showLogs: Boolean = true,
    onTick: (() -> Unit)?
): Timer {
    val logger = KotlinLogging.logger { }
    val mainHandler = Handler(Looper.getMainLooper())
    val timer = Timer()
    timer.schedule(object : TimerTask() {
        override fun run() {
            if (showLogs) logger.info { "[$tag] Time task run" }
            mainHandler.post {
                onTick?.invoke()  // ✅ 现在运行在主线程
            }
        }
    }, delay, period)
    return timer
}
