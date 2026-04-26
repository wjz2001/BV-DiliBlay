package dev.aaa1115910.bv.player.danmaku.track

class FixedDanmakuTrack<T : SizeSpecifiedDanmaku>(
    val trackIndex: Int,
    val fromBottom: Boolean,
    var durationMs: Long,
) : DanmakuTrack<T, FixedDanmaku<T>> {
    private var currentDanmaku: FixedDanmaku<T>? = null

    override fun place(danmaku: T, nowMs: Long): FixedDanmaku<T> {
        return FixedDanmaku(
            danmaku = danmaku,
            trackIndex = trackIndex,
            fromBottom = fromBottom,
            startTimeMs = nowMs,
        ).also { currentDanmaku = it }
    }

    override fun canPlace(danmaku: T, nowMs: Long): Boolean {
        val current = currentDanmaku ?: return true
        return current.isGone(nowMs, durationMs)
    }

    override fun clearAll() {
        currentDanmaku = null
    }

    override fun tick(nowMs: Long) {
        val current = currentDanmaku ?: return
        if (current.isGone(nowMs, durationMs)) currentDanmaku = null
    }

    override fun iterator(): Iterator<FixedDanmaku<T>> {
        return object : Iterator<FixedDanmaku<T>> {
            private var hasNext = currentDanmaku != null

            override fun hasNext(): Boolean {
                return hasNext
            }

            override fun next(): FixedDanmaku<T> {
                if (!hasNext) throw NoSuchElementException()
                hasNext = false
                return currentDanmaku ?: throw NoSuchElementException()
            }
        }
    }

    override fun toString(): String {
        return "FixedTrack(index=$trackIndex, placeTime=${currentDanmaku?.startTimeMs})"
    }
}

class FixedDanmaku<T : SizeSpecifiedDanmaku>(
    val danmaku: T,
    val trackIndex: Int,
    val fromBottom: Boolean,
    val startTimeMs: Long,
) {
    fun y(trackHeightPx: Float, hostHeightPx: Float): Float {
        return if (fromBottom) {
            hostHeightPx - (trackIndex + 1) * trackHeightPx
        } else {
            trackIndex * trackHeightPx
        }
    }

    internal fun isGone(nowMs: Long, durationMs: Long): Boolean {
        return nowMs - startTimeMs >= durationMs
    }
}
