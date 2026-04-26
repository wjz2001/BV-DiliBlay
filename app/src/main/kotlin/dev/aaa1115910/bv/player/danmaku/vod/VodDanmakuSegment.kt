package dev.aaa1115910.bv.player.danmaku.vod

data class VodDanmakuSegment(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
) {
    fun contains(positionMs: Long): Boolean {
        val safePositionMs = positionMs.coerceAtLeast(0L)
        return safePositionMs >= startMs && safePositionMs < endMs
    }
}

data class VodDanmakuSegmentIndex(
    val segmentDurationMs: Long = DEFAULT_SEGMENT_DURATION_MS,
    val prefetchOffsetMs: Long = 0L,
) {
    init {
        require(segmentDurationMs > 0L) { "segmentDurationMs must be positive" }
        require(prefetchOffsetMs >= 0L) { "prefetchOffsetMs must be non-negative" }
    }

    fun indexOf(positionMs: Long): Int {
        val safePositionMs = positionMs.coerceAtLeast(0L)
        val mappedPositionMs = safePositionMs.saturatingPlus(prefetchOffsetMs)
        val rawIndex = mappedPositionMs / segmentDurationMs + 1L
        return rawIndex.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun segmentOf(positionMs: Long): VodDanmakuSegment {
        return segmentAt(indexOf(positionMs))
    }

    fun segmentAt(index: Int): VodDanmakuSegment {
        val safeIndex = index.coerceAtLeast(1)
        val startMs = (safeIndex.toLong() - 1L) * segmentDurationMs
        return VodDanmakuSegment(
            index = safeIndex,
            startMs = startMs,
            endMs = startMs.saturatingPlus(segmentDurationMs),
        )
    }

    fun countForDuration(durationMs: Long): Int {
        if (durationMs <= 0L) return 0
        val count = (durationMs - 1L) / segmentDurationMs + 1L
        return count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    companion object {
        const val DEFAULT_SEGMENT_DURATION_MS = 6 * 60 * 1000L
    }
}


private fun Long.saturatingPlus(other: Long): Long {
    return if (Long.MAX_VALUE - this < other) Long.MAX_VALUE else this + other
}