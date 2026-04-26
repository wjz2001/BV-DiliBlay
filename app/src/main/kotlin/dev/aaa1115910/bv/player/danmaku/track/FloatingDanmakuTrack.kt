package dev.aaa1115910.bv.player.danmaku.track

class FloatingDanmakuTrack<T : FloatingSizeSpecifiedDanmaku>(
    val trackIndex: Int,
    private val trackWidthPx: () -> Float,
    var safeSeparationPx: Float,
) : DanmakuTrack<T, FloatingDanmaku<T>> {
    private val danmakuList = mutableListOf<FloatingDanmaku<T>>()

    override fun place(danmaku: T, nowMs: Long): FloatingDanmaku<T> {
        val floatingDanmaku = FloatingDanmaku(
            danmaku = danmaku,
            trackIndex = trackIndex,
            startTimeMs = nowMs,
        )
        danmakuList += floatingDanmaku
        return floatingDanmaku
    }

    override fun canPlace(danmaku: T, nowMs: Long): Boolean {
        val width = trackWidthPx().coerceAtLeast(1f)
        val previous = danmakuList.lastOrNull() ?: return true
        val previousTailX = previous.right(nowMs, width, safeSeparationPx)
        if (previousTailX > width) return false
        if (danmaku.speedPxPerMs <= previous.danmaku.speedPxPerMs) return true

        val previousRemainingMs = previous.danmaku.durationMs - (nowMs - previous.startTimeMs)
        if (previousRemainingMs <= 0L) return true
        val gapPx = (width - previousTailX).coerceAtLeast(0f)
        return gapPx >= (danmaku.speedPxPerMs - previous.danmaku.speedPxPerMs) * previousRemainingMs.toFloat()
    }

    override fun clearAll() {
        danmakuList.clear()
    }

    override fun tick(nowMs: Long) {
        if (danmakuList.isEmpty()) return
        val width = trackWidthPx().coerceAtLeast(1f)
        danmakuList.removeAll { danmaku -> danmaku.isGone(nowMs, width, safeSeparationPx) }
    }

    override fun iterator(): Iterator<FloatingDanmaku<T>> {
        return object : Iterator<FloatingDanmaku<T>> {
            private var index = 0

            override fun hasNext(): Boolean {
                return index < danmakuList.size
            }

            override fun next(): FloatingDanmaku<T> {
                if (!hasNext()) throw NoSuchElementException()
                return danmakuList[index++]
            }
        }
    }

    override fun toString(): String {
        return "FloatingTrack(index=$trackIndex, danmakuCount=${danmakuList.size})"
    }
}

class FloatingDanmaku<T : FloatingSizeSpecifiedDanmaku>(
    val danmaku: T,
    val trackIndex: Int,
    val startTimeMs: Long,
) {
    fun x(nowMs: Long, trackWidthPx: Float): Float {
        return trackWidthPx - (nowMs - startTimeMs).coerceAtLeast(0L).toFloat() * danmaku.speedPxPerMs
    }

    fun y(trackHeightPx: Float): Float {
        return trackHeightPx * trackIndex
    }

    internal fun right(nowMs: Long, trackWidthPx: Float, safeSeparationPx: Float): Float {
        return x(nowMs, trackWidthPx) + danmaku.danmakuWidthPx + safeSeparationPx.coerceAtLeast(0f)
    }

    internal fun isGone(nowMs: Long, trackWidthPx: Float, safeSeparationPx: Float): Boolean {
        val elapsedMs = nowMs - startTimeMs
        return elapsedMs >= danmaku.durationMs || right(nowMs, trackWidthPx, safeSeparationPx) <= 0f
    }
}
