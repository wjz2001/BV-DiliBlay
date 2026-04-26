package dev.aaa1115910.bv.player.danmaku.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.aaa1115910.biliapi.entity.danmaku.DanmakuMaskFrame
import dev.aaa1115910.bv.player.danmaku.core.DanmakuCoreItem
import dev.aaa1115910.bv.player.danmaku.core.DanmakuCoreLocation
import dev.aaa1115910.bv.player.danmaku.core.DanmakuTimeRange
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

typealias DanmakuSessionEvent = dev.aaa1115910.bv.player.danmaku.api.DanmakuSessionEvent
typealias DanmakuSessionEventType = dev.aaa1115910.bv.player.danmaku.api.DanmakuSessionEventType

enum class DanmakuCacheState {
    Init,
    Rendering,
    Rendered,
}

enum class DanmakuTrackType {
    Scroll,
    Top,
    Bottom,
}

data class DanmakuItem(
    override val id: Long,
    val userId: Long? = null,
    override val timeMs: Int,
    override val text: String,
    val mode: Int,
    val textSize: Int,
    val color: Int,
    val level: Int,
    val timestamp: Int? = null,
    val pool: Int? = null,
    val midHash: String? = null,
    override val source: String,
    override val arrivalTimeMs: Long,
    val cacheState: DanmakuCacheState = DanmakuCacheState.Init,
    val cacheKey: String? = null,
    val isActive: Boolean = false,
    val trackType: DanmakuTrackType = DanmakuTrackType.Scroll,
    val lane: Int = 0,
    val startTimeMs: Int = timeMs,
    val durationMs: Int = 0,
    val pxPerMs: Float = 0f,
    val textWidthPx: Float = 0f,
) : DanmakuCoreItem {
    override val senderId: String?
        get() = userId?.toString() ?: midHash

    override val colorRgb: Int
        get() = color

    override val location: DanmakuCoreLocation
        get() = when (trackType) {
            DanmakuTrackType.Top -> DanmakuCoreLocation.Top
            DanmakuTrackType.Bottom -> DanmakuCoreLocation.Bottom
            DanmakuTrackType.Scroll -> DanmakuCoreLocation.Scroll
        }
}

data class DanmakuItemRef(
    val item: DanmakuItem,
    val x: Float,
    val y: Float,
)

data class DanmakuRenderSnapshot(
    val positionMs: Double,
    val frameId: Long,
    val items: List<DanmakuItemRef>,
    val yTop: FloatArray,
    val count: Int,
    val configVersion: Int,
    val viewportWidth: Int,
    val viewportHeight: Int,
)

data class DanmakuRenderFrame(
    val snapshot: DanmakuRenderSnapshot,
    val playbackPositionMs: Long,
    val playbackSpeed: Float,
    val reason: String,
    val frameTimeNanos: Long? = null,
    val hasMaskOverride: Boolean? = null,
) {
    val hasMask: Boolean
        get() = hasMaskOverride ?: false
}

enum class DanmakuLiveEventType {
    Danmaku,
    PopularityChange,
    OnlineRankCount,
    Unknown,
}

data class DanmakuLiveEvent(
    val type: DanmakuLiveEventType,
    val timestampMs: Long,
    val roomId: Long,
    val content: String? = null,
    val userId: Long? = null,
    val username: String? = null,
    val medalName: String? = null,
    val medalLevel: Int? = null,
    val mode: Int? = null,
    val fontSize: Int? = null,
    val color: Int? = null,
    val userLevel: Int? = null,
    val popularity: Int? = null,
    val popularityText: String? = null,
    val onlineRankCount: Int? = null,
)

data class DanmakuFilterRule(
    val allowScroll: Boolean = true,
    val allowTop: Boolean = true,
    val allowBottom: Boolean = true,
    val minLevel: Int = 0,
    val blockedKeywords: Set<String> = emptySet(),
    val blockedUsers: Set<Long> = emptySet(),
)

data class DanmakuSpeedModel(
    val viewportWidthPx: Float,
    val textWidthPx: Float,
    val playbackSpeed: Float,
    val speedLevel: Int,
    val allowRandomJitter: Boolean = false,
    val randomSeed: Long = 0L,
    val randomJitterRatio: Float = DEFAULT_RANDOM_JITTER_RATIO,
    val baseRollingDurationMs: Int = BASE_ROLLING_DURATION_MS,
    val minDurationMs: Int = MIN_ROLLING_DURATION_MS,
    val maxDurationMs: Int = MAX_ROLLING_DURATION_MS,
    val maxLongSpeedRatio: Float = MAX_LONG_SCROLL_SPEED_RATIO,
) {
    val lengthStrategy: ScrollLengthStrategy = if (textWidthPx <= viewportWidthPx) {
        ScrollLengthStrategy.Short
    } else {
        ScrollLengthStrategy.Long
    }
    val basePxPerMs: Float = computeBasePxPerMs()
    val speedMultiplier: Float = when (speedLevel) {
        1 -> 1.5f
        2 -> 1.25f
        4 -> 0.75f
        5 -> 0.5f
        else -> 1f
    }
    val randomMultiplier: Float = computeRandomMultiplier()
    val finalPxPerMs: Float = (basePxPerMs * playbackSpeed * speedMultiplier * randomMultiplier)
        .coerceAtLeast(MIN_EFFECTIVE_SPEED_PX_PER_MS)
    val durationMs: Int = computeDurationMs()

    private fun computeBasePxPerMs(): Float {
        val safeViewportWidth = viewportWidthPx.coerceAtLeast(1f)
        val safeTextWidth = textWidthPx.coerceAtLeast(0f)
        val safeDuration = baseRollingDurationMs.coerceAtLeast(1).toFloat()
        val shortBasePxPerMs = safeViewportWidth / safeDuration
        val longRawBasePxPerMs = (safeViewportWidth + safeTextWidth) / safeDuration
        return when (lengthStrategy) {
            ScrollLengthStrategy.Short -> shortBasePxPerMs
            ScrollLengthStrategy.Long -> min(longRawBasePxPerMs, shortBasePxPerMs * maxLongSpeedRatio.coerceAtLeast(1f))
        }
    }

    private fun computeRandomMultiplier(): Float {
        if (!allowRandomJitter) return 1f
        val clampedRatio = randomJitterRatio.coerceIn(0f, MAX_RANDOM_JITTER_RATIO)
        if (clampedRatio == 0f) return 1f
        val mixed = (randomSeed * 1103515245L + 12345L) and 0x7fffffffL
        val normalized = mixed.toFloat() / 0x7fffffffL.toFloat()
        val centered = normalized * 2f - 1f
        return 1f + centered * clampedRatio
    }

    private fun computeDurationMs(): Int {
        val distancePx = (viewportWidthPx.coerceAtLeast(1f) + textWidthPx.coerceAtLeast(0f))
        val rawDuration = ceil(distancePx / finalPxPerMs).toInt().coerceAtLeast(1)
        return rawDuration.coerceIn(
            minDurationMs.coerceAtLeast(1),
            max(maxDurationMs, minDurationMs).coerceAtLeast(1),
        )
    }

    companion object {
        const val BASE_ROLLING_DURATION_MS = 7_800
        const val MIN_ROLLING_DURATION_MS = 2_500
        const val MAX_ROLLING_DURATION_MS = 20_000
        const val MAX_LONG_SCROLL_SPEED_RATIO = 1.5f
        const val DEFAULT_RANDOM_JITTER_RATIO = 0f
        const val MAX_RANDOM_JITTER_RATIO = 0.2f
        const val MIN_EFFECTIVE_SPEED_PX_PER_MS = 0.01f
    }
}

enum class ScrollLengthStrategy {
    Short,
    Long,
}

class DanmakuHostState(
    sessionId: String = "",
    isBound: Boolean = false,
    isVisible: Boolean = true,
    isPlaying: Boolean = false,
    currentPositionMs: Long = 0L,
    playbackSpeed: Float = 1f,
    lastSnapshotFrameId: Long = 0L,
    lastConfigVersion: Int = 0,
    maskEnabled: Boolean = false,
) {
    var sessionId by mutableStateOf(sessionId)
    var isBound by mutableStateOf(isBound)
    var isVisible by mutableStateOf(isVisible)
    var isPlaying by mutableStateOf(isPlaying)
    var currentPositionMs by mutableStateOf(currentPositionMs)
    var playbackSpeed by mutableStateOf(playbackSpeed)
    var lastSnapshotFrameId by mutableStateOf(lastSnapshotFrameId)
    var lastConfigVersion by mutableStateOf(lastConfigVersion)
    var maskEnabled by mutableStateOf(maskEnabled)
    var sourceMode by mutableStateOf("none")
}

data class TimelineWindow(
    override val startMs: Long,
    override val endMs: Long,
) : DanmakuTimeRange

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

data class LayoutSnapshot(
    val renderSnapshot: DanmakuRenderSnapshot,
)

data class MaskRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class MaskRegionSet(
    val viewportWidth: Int,
    val viewportHeight: Int,
    val frame: DanmakuMaskFrame? = null,
    val regions: List<MaskRegion> = emptyList(),
)

private fun Long.saturatingPlus(other: Long): Long {
    return if (Long.MAX_VALUE - this < other) Long.MAX_VALUE else this + other
}
