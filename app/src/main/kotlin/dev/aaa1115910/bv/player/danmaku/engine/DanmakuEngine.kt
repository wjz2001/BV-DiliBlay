package dev.aaa1115910.bv.player.danmaku.engine

import dev.aaa1115910.bv.player.danmaku.config.DanmakuConfig
import dev.aaa1115910.bv.player.danmaku.config.DanmakuConfigDiff
import dev.aaa1115910.bv.player.danmaku.config.DanmakuLaneDensity
import dev.aaa1115910.bv.player.danmaku.filter.DanmakuFilterChain
import dev.aaa1115910.bv.player.danmaku.filter.DanmakuFilterResult
import dev.aaa1115910.bv.player.danmaku.model.DanmakuFilterRule
import dev.aaa1115910.bv.player.danmaku.model.DanmakuItem
import dev.aaa1115910.bv.player.danmaku.model.DanmakuItemRef
import dev.aaa1115910.bv.player.danmaku.model.DanmakuRenderSnapshot
import dev.aaa1115910.bv.player.danmaku.model.DanmakuSpeedModel
import dev.aaa1115910.bv.player.danmaku.model.DanmakuTrackType
import dev.aaa1115910.bv.player.danmaku.model.LayoutSnapshot
import dev.aaa1115910.bv.player.danmaku.model.TimelineWindow
import kotlin.math.max
import kotlin.math.min

interface DanmakuEngine {
    fun add(items: List<DanmakuItem>, source: String): Int

    fun updateConfig(config: DanmakuConfig, reason: String): DanmakuConfig

    fun updateFilterRule(rule: DanmakuFilterRule, version: Int, reason: String): DanmakuFilterRule

    fun onInput(event: DanmakuEngineInputEvent): Int

    fun advance(request: DanmakuEngineAdvanceRequest): LayoutSnapshot

    fun repopulate(
        reason: String,
        configDiff: DanmakuConfigDiff?,
        timelineWindow: TimelineWindow,
    ): LayoutSnapshot

    fun clear(reason: String)
}

enum class DanmakuInputStreamType {
    Vod,
    Live,
}

sealed class DanmakuEngineInputEvent {
    data class Append(
        val items: List<DanmakuItem>,
        val source: String,
        val streamType: DanmakuInputStreamType,
    ) : DanmakuEngineInputEvent()

    data class Clear(
        val reason: String,
    ) : DanmakuEngineInputEvent()
}

data class DanmakuEngineAdvanceRequest(
    val positionMs: Long,
    val viewportWidth: Int = 0,
    val viewportHeight: Int = 0,
    val playbackSpeed: Float = 1f,
    val reason: String = "tick",
)

data class DanmakuEngineDropPolicy(
    val maxCatchUpLagMs: Long = 5_000L,
    val maxSpawnPerFrame: Int = 192,
    val maxActiveItems: Int = 4_096,
)

data class DanmakuEngineDropStats(
    val totalCatchUpDroppedItems: Long = 0L,
    val lastCatchUpDroppedItems: Int = 0,
    val totalPlacementDroppedItems: Long = 0L,
    val lastPlacementDroppedItems: Int = 0,
)

class SimpleDanmakuEngine(
    private var config: DanmakuConfig = DanmakuConfig(),
    private val dropPolicy: DanmakuEngineDropPolicy = DanmakuEngineDropPolicy(),
) : DanmakuEngine {
    private val timelineItems = mutableListOf<DanmakuItem>()
    private val activeItems = mutableListOf<DanmakuItem>()
    private val filterChain = DanmakuFilterChain()
    private var filterRule = config.toFilterRule()
    private var filterVersion = 0
    private var frameId = 0L
    private var spawnIndex = 0
    private var sortedDirty = false
    private var lastPositionMs = 0L
    private var lastViewportWidth = DEFAULT_VIEWPORT_WIDTH_PX.toInt()
    private var lastViewportHeight = DEFAULT_VIEWPORT_HEIGHT_PX.toInt()
    private var lastPlaybackSpeed = DEFAULT_PLAYBACK_SPEED
    private var topLaneBusyUntilMs = DoubleArray(0)
    private var bottomLaneBusyUntilMs = DoubleArray(0)
    private var scrollLaneQueues: Array<ArrayDeque<DanmakuItem>> = emptyArray()
    private var currentDropStats = DanmakuEngineDropStats()

    val dropStats: DanmakuEngineDropStats
        get() = currentDropStats

    override fun add(items: List<DanmakuItem>, source: String): Int {
        return onInput(
            DanmakuEngineInputEvent.Append(
                items = items,
                source = source,
                streamType = DanmakuInputStreamType.Vod,
            )
        )
    }

    override fun updateConfig(config: DanmakuConfig, reason: String): DanmakuConfig {
        this.config = config
        filterRule = config.mergeToFilterRule(filterRule)
        return this.config
    }

    override fun updateFilterRule(
        rule: DanmakuFilterRule,
        version: Int,
        reason: String,
    ): DanmakuFilterRule {
        filterRule = rule
        filterVersion = version
        return filterRule
    }

    override fun onInput(event: DanmakuEngineInputEvent): Int {
        return when (event) {
            is DanmakuEngineInputEvent.Append -> append(event)
            is DanmakuEngineInputEvent.Clear -> {
                clear(event.reason)
                0
            }
        }
    }

    override fun advance(request: DanmakuEngineAdvanceRequest): LayoutSnapshot {
        val viewportWidth = request.viewportWidth.takeIf { it > 0 } ?: lastViewportWidth
        val viewportHeight = request.viewportHeight.takeIf { it > 0 } ?: lastViewportHeight
        lastViewportWidth = viewportWidth.coerceAtLeast(1)
        lastViewportHeight = viewportHeight.coerceAtLeast(1)
        lastPlaybackSpeed = request.playbackSpeed.coerceAtLeast(0.1f)
        val nowMs = request.positionMs.coerceAtLeast(0L)
        currentDropStats = currentDropStats.copy(
            lastCatchUpDroppedItems = 0,
            lastPlacementDroppedItems = 0,
        )

        if (config.enabled.not()) {
            frameId += 1
            activeItems.clear()
            return emptySnapshot(nowMs, lastViewportWidth, lastViewportHeight)
        }
        if (nowMs < lastPositionMs) {
            resetTimelinePointer(nowMs)
            clearActiveState()
        } else {
            skipExpiredBefore(nowMs)
            dropIfLagging(nowMs)
            pruneExpired(
                nowMs = nowMs.toDouble(),
                viewportWidth = lastViewportWidth,
            )
        }
        lastPositionMs = nowMs
        ensureSorted()
        ensureLaneStateBuffers(
            scrollLaneCount = layoutState(lastViewportHeight).scrollLaneCount,
            topLaneCount = layoutState(lastViewportHeight).topLaneCount,
            bottomLaneCount = layoutState(lastViewportHeight).bottomLaneCount,
        )
        cleanupLaneQueues(nowMs, lastViewportWidth)
        spawnDueItems(nowMs, lastViewportWidth, lastViewportHeight, lastPlaybackSpeed)
        return buildSnapshot(
            nowMs = nowMs,
            viewportWidth = lastViewportWidth,
            viewportHeight = lastViewportHeight,
        )
    }

    override fun repopulate(
        reason: String,
        configDiff: DanmakuConfigDiff?,
        timelineWindow: TimelineWindow,
    ): LayoutSnapshot {
        configDiff?.let {
            config = it.newConfig
            filterRule = it.newConfig.mergeToFilterRule(filterRule)
        }
        val startMs = timelineWindow.startMs.coerceAtLeast(0L)
        lastPositionMs = startMs
        resetTimelinePointer(startMs)
        clearActiveState()
        return advance(
            DanmakuEngineAdvanceRequest(
                positionMs = startMs,
                viewportWidth = lastViewportWidth,
                viewportHeight = lastViewportHeight,
                playbackSpeed = lastPlaybackSpeed,
                reason = reason,
            )
        )
    }

    override fun clear(reason: String) {
        timelineItems.clear()
        clearActiveState()
        spawnIndex = 0
        sortedDirty = false
        frameId += 1
        currentDropStats = DanmakuEngineDropStats()
    }

    private fun append(event: DanmakuEngineInputEvent.Append): Int {
        var acceptedNow = 0
        event.items.forEach { item ->
            if (item.source != event.source) return@forEach
            val scheduledItem = normalizeIncomingItem(item, event.streamType)
            if (filterChain.evaluate(scheduledItem, filterRule) is DanmakuFilterResult.Accepted) {
                acceptedNow += 1
            }
            timelineItems += scheduledItem
        }
        sortedDirty = sortedDirty || event.items.size > 1 || event.streamType == DanmakuInputStreamType.Live
        return acceptedNow
    }

    private fun normalizeIncomingItem(item: DanmakuItem, streamType: DanmakuInputStreamType): DanmakuItem {
        val scheduleTimeMs = when (streamType) {
            DanmakuInputStreamType.Vod -> item.timeMs.toLong().coerceAtLeast(0L)
            DanmakuInputStreamType.Live -> max(lastPositionMs, item.timeMs.toLong().coerceAtLeast(0L))
        }
        val normalizedTrackType = when (item.trackType) {
            DanmakuTrackType.Top,
            DanmakuTrackType.Bottom,
            DanmakuTrackType.Scroll,
            -> item.trackType
        }
        return item.copy(
            timeMs = scheduleTimeMs.toIntSaturated(),
            trackType = normalizedTrackType,
        )
    }

    private fun ensureSorted() {
        if (!sortedDirty) return
        timelineItems.sortWith(compareBy({ it.timeMs }, { it.arrivalTimeMs }, { it.id }))
        spawnIndex = spawnIndex.coerceIn(0, timelineItems.size)
        sortedDirty = false
    }

    private fun resetTimelinePointer(positionMs: Long) {
        ensureSorted()
        val rewindStart = (positionMs - MAX_REWIND_LOOKBACK_MS).coerceAtLeast(0L).toIntSaturated()
        spawnIndex = lowerBoundTime(rewindStart)
    }

    private fun lowerBoundTime(timeMs: Int): Int {
        var left = 0
        var right = timelineItems.size
        while (left < right) {
            val middle = (left + right) ushr 1
            if (timelineItems[middle].timeMs < timeMs) {
                left = middle + 1
            } else {
                right = middle
            }
        }
        return left
    }

    private fun skipExpiredBefore(nowMs: Long) {
        val ignoreBefore = (nowMs - MAX_REWIND_LOOKBACK_MS).coerceAtLeast(0L)
        while (spawnIndex < timelineItems.size && timelineItems[spawnIndex].timeMs.toLong() < ignoreBefore) {
            spawnIndex += 1
        }
    }

    private fun dropIfLagging(nowMs: Long) {
        val maxLagMs = dropPolicy.maxCatchUpLagMs.coerceAtLeast(0L)
        val dropBefore = (nowMs - maxLagMs).coerceAtLeast(0L)
        var dropped = 0
        while (spawnIndex < timelineItems.size && timelineItems[spawnIndex].timeMs.toLong() < dropBefore) {
            spawnIndex += 1
            dropped += 1
        }
        if (dropped > 0) {
            currentDropStats = currentDropStats.copy(
                totalCatchUpDroppedItems = currentDropStats.totalCatchUpDroppedItems + dropped,
                lastCatchUpDroppedItems = dropped,
            )
        }
    }

    private fun layoutState(viewportHeight: Int): LayoutState {
        val safeHeight = viewportHeight.coerceAtLeast(1)
        val areaFraction = config.area.coerceIn(0f, 1f)
        val usableHeight = (safeHeight * areaFraction).toInt().coerceAtLeast(1)
        val laneHeight = estimateLaneHeightPx().coerceAtLeast(1f)
        val laneCount = max(1, (usableHeight / laneHeight).toInt())
        return LayoutState(
            laneHeight = laneHeight,
            textHeight = estimateTextHeightPx(),
            scrollLaneCount = laneCount,
            topLaneCount = laneCount,
            bottomLaneCount = laneCount,
            topUsableHeight = usableHeight,
            bottomUsableHeight = usableHeight,
        )
    }

    private fun estimateLaneHeightPx(): Float {
        return (estimateTextHeightPx() * config.laneDensity.factor() + LANE_GAP_PX).coerceAtLeast(18f)
    }

    private fun estimateTextHeightPx(): Float {
        val scale = config.textSizeScale.coerceIn(25, 400) / 100f
        val canvasPadding = config.textPaddingPx.coerceAtLeast(0)
        val textSizePx = config.textSizeSp.coerceAtLeast(MIN_TEXT_SIZE_SP) * DEFAULT_DENSITY * scale
        return (textSizePx * TEXT_HEIGHT_FACTOR).coerceAtLeast(16f) + canvasPadding
    }

    private fun ensureLaneStateBuffers(scrollLaneCount: Int, topLaneCount: Int, bottomLaneCount: Int) {
        if (scrollLaneQueues.size < scrollLaneCount) {
            val old = scrollLaneQueues
            scrollLaneQueues = Array(scrollLaneCount) { lane -> old.getOrNull(lane) ?: ArrayDeque() }
        }
        if (topLaneBusyUntilMs.size < topLaneCount) {
            topLaneBusyUntilMs = topLaneBusyUntilMs.copyOf(topLaneCount)
        }
        if (bottomLaneBusyUntilMs.size < bottomLaneCount) {
            bottomLaneBusyUntilMs = bottomLaneBusyUntilMs.copyOf(bottomLaneCount)
        }
    }

    private fun clearActiveState() {
        activeItems.clear()
        scrollLaneQueues.forEach { it.clear() }
        topLaneBusyUntilMs.fill(0.0)
        bottomLaneBusyUntilMs.fill(0.0)
    }

    private fun cleanupLaneQueues(nowMs: Long, viewportWidth: Int) {
        for (queue in scrollLaneQueues) {
            while (queue.isNotEmpty()) {
                val first = queue.first()
                if (!first.isActive || isExpired(first, nowMs.toDouble(), viewportWidth)) {
                    queue.removeFirst()
                } else {
                    break
                }
            }
            while (queue.isNotEmpty()) {
                val last = queue.last()
                if (!last.isActive || isExpired(last, nowMs.toDouble(), viewportWidth)) {
                    queue.removeLast()
                } else {
                    break
                }
            }
        }
    }

    private fun pruneExpired(nowMs: Double, viewportWidth: Int) {
        if (activeItems.isEmpty()) return
        activeItems.removeAll { active ->
            val expired = isExpired(active, nowMs, viewportWidth)
            if (expired) {
                if (active.trackType == DanmakuTrackType.Scroll) {
                    val lane = active.lane
                    scrollLaneQueues.getOrNull(lane)?.remove(active)
                }
                active.copy(isActive = false)
            }
            expired
        }
    }

    private fun spawnDueItems(
        nowMs: Long,
        viewportWidth: Int,
        viewportHeight: Int,
        playbackSpeed: Float,
    ) {
        val layout = layoutState(viewportHeight)
        var attempts = 0
        ensureSorted()
        while (spawnIndex < timelineItems.size && timelineItems[spawnIndex].timeMs.toLong() <= nowMs) {
            if (attempts >= dropPolicy.maxSpawnPerFrame.coerceAtLeast(1)) break
            val item = timelineItems[spawnIndex]
            spawnIndex += 1
            attempts += 1
            if (item.text.isBlank()) continue
            if (filterChain.evaluate(item, filterRule) is DanmakuFilterResult.Rejected) continue
            if (activeItems.size >= dropPolicy.maxActiveItems.coerceAtLeast(1)) {
                recordPlacementDrop()
                continue
            }
            val textWidthPx = estimateTextWidthPx(item)
            val placed = when (item.trackType) {
                DanmakuTrackType.Scroll -> trySpawnScroll(item, nowMs, viewportWidth, playbackSpeed, textWidthPx, layout)
                DanmakuTrackType.Top -> trySpawnTop(item, nowMs, viewportWidth, textWidthPx, layout)
                DanmakuTrackType.Bottom -> trySpawnBottom(item, nowMs, viewportWidth, textWidthPx, layout)
            }
            if (!placed) recordPlacementDrop()
        }
    }

    private fun recordPlacementDrop() {
        currentDropStats = currentDropStats.copy(
            totalPlacementDroppedItems = currentDropStats.totalPlacementDroppedItems + 1,
            lastPlacementDroppedItems = currentDropStats.lastPlacementDroppedItems + 1,
        )
    }

    private fun trySpawnScroll(
        item: DanmakuItem,
        nowMs: Long,
        viewportWidth: Int,
        playbackSpeed: Float,
        textWidthPx: Float,
        layout: LayoutState,
    ): Boolean {
        val speedModel = DanmakuSpeedModel(
            viewportWidthPx = viewportWidth.toFloat().coerceAtLeast(1f),
            textWidthPx = textWidthPx,
            playbackSpeed = playbackSpeed,
            speedLevel = config.speedLevel,
            allowRandomJitter = false,
            randomSeed = item.id,
            baseRollingDurationMs = (
                DanmakuSpeedModel.BASE_ROLLING_DURATION_MS * config.durationMultiplier
            ).toInt().coerceIn(3_000, 60_000),
        )
        val laneCount = layout.scrollLaneCount
        val nowMsDouble = nowMs.toDouble()
        for (lane in 0 until laneCount) {
            val queue = scrollLaneQueues[lane]
            val previous = queue.lastOrNull()
            if (previous == null) {
                val activated = activateItem(
                    item = item,
                    lane = lane,
                    startTimeMs = nowMs,
                    durationMs = speedModel.durationMs,
                    pxPerMs = speedModel.finalPxPerMs,
                    textWidthPx = textWidthPx,
                )
                queue.addLast(activated)
                return true
            }
            val prevTailX = scrollX(viewportWidth, nowMsDouble, previous.startTimeMs, previous.pxPerMs) + previous.textWidthPx
            if (isScrollLaneAvailable(
                    viewportWidthPx = viewportWidth.toFloat(),
                    nowMs = nowMsDouble,
                    previous = previous,
                    previousTailX = prevTailX,
                    newSpeedPxPerMs = speedModel.finalPxPerMs,
                    marginPx = layout.textHeight * 0.6f,
                )
            ) {
                val activated = activateItem(
                    item = item,
                    lane = lane,
                    startTimeMs = nowMs,
                    durationMs = speedModel.durationMs,
                    pxPerMs = speedModel.finalPxPerMs,
                    textWidthPx = textWidthPx,
                )
                queue.addLast(activated)
                return true
            }
        }
        return false
    }

    private fun trySpawnTop(
        item: DanmakuItem,
        nowMs: Long,
        viewportWidth: Int,
        textWidthPx: Float,
        layout: LayoutState,
    ): Boolean {
        for (lane in 0 until layout.topLaneCount) {
            if (topLaneBusyUntilMs[lane] > nowMs) continue
            val activated = activateItem(
                item = item,
                lane = lane,
                startTimeMs = nowMs,
                durationMs = FIXED_TRACK_DURATION_MS,
                pxPerMs = 0f,
                textWidthPx = min(textWidthPx, viewportWidth.toFloat()),
            )
            topLaneBusyUntilMs[lane] = nowMs.toDouble() + activated.durationMs
            return true
        }
        return false
    }

    private fun trySpawnBottom(
        item: DanmakuItem,
        nowMs: Long,
        viewportWidth: Int,
        textWidthPx: Float,
        layout: LayoutState,
    ): Boolean {
        for (lane in 0 until layout.bottomLaneCount) {
            if (bottomLaneBusyUntilMs[lane] > nowMs) continue
            val activated = activateItem(
                item = item,
                lane = lane,
                startTimeMs = nowMs,
                durationMs = FIXED_TRACK_DURATION_MS,
                pxPerMs = 0f,
                textWidthPx = min(textWidthPx, viewportWidth.toFloat()),
            )
            bottomLaneBusyUntilMs[lane] = nowMs.toDouble() + activated.durationMs
            return true
        }
        return false
    }

    private fun activateItem(
        item: DanmakuItem,
        lane: Int,
        startTimeMs: Long,
        durationMs: Int,
        pxPerMs: Float,
        textWidthPx: Float,
    ): DanmakuItem {
        val activated = item.copy(
            lane = lane,
            startTimeMs = startTimeMs.toIntSaturated(),
            durationMs = durationMs,
            pxPerMs = pxPerMs,
            textWidthPx = textWidthPx,
            isActive = true,
        )
        activeItems += activated
        return activated
    }

    private fun buildSnapshot(
        nowMs: Long,
        viewportWidth: Int,
        viewportHeight: Int,
    ): LayoutSnapshot {
        val layout = layoutState(viewportHeight)
        val nowMsDouble = nowMs.toDouble()
        val refs = ArrayList<DanmakuItemRef>(activeItems.size)
        activeItems.forEach { item ->
            val x = when (item.trackType) {
                DanmakuTrackType.Scroll -> scrollX(viewportWidth, nowMsDouble, item.startTimeMs, item.pxPerMs)
                DanmakuTrackType.Top,
                DanmakuTrackType.Bottom,
                -> centerX(viewportWidth, item.textWidthPx)
            }
            val y = when (item.trackType) {
                DanmakuTrackType.Scroll -> (layout.laneHeight * item.lane)
                    .coerceAtMost((layout.topUsableHeight - layout.textHeight).coerceAtLeast(0f))

                DanmakuTrackType.Top -> (layout.laneHeight * item.lane)
                    .coerceAtMost((layout.topUsableHeight - layout.textHeight).coerceAtLeast(0f))

                DanmakuTrackType.Bottom -> {
                    val base = viewportHeight - layout.textHeight - config.bottomPaddingPx.coerceAtLeast(0)
                    (base - layout.laneHeight * item.lane)
                        .coerceAtLeast((viewportHeight - layout.bottomUsableHeight).toFloat())
                }
            }
            val visible = x + item.textWidthPx >= 0f && x <= viewportWidth
            if (visible) {
                refs += DanmakuItemRef(item = item, x = x, y = y)
            }
        }
        frameId += 1
        return LayoutSnapshot(
            renderSnapshot = DanmakuRenderSnapshot(
                positionMs = nowMsDouble,
                frameId = frameId,
                items = refs,
                yTop = FloatArray(refs.size) { refs[it].y },
                count = refs.size,
                configVersion = filterVersion,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            )
        )
    }

    private fun emptySnapshot(
        nowMs: Long,
        viewportWidth: Int,
        viewportHeight: Int,
    ): LayoutSnapshot {
        return LayoutSnapshot(
            renderSnapshot = DanmakuRenderSnapshot(
                positionMs = nowMs.toDouble(),
                frameId = frameId,
                items = emptyList(),
                yTop = FloatArray(0),
                count = 0,
                configVersion = filterVersion,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            )
        )
    }

    private fun isExpired(item: DanmakuItem, nowMs: Double, viewportWidth: Int): Boolean {
        val elapsed = nowMs - item.startTimeMs
        if (elapsed >= item.durationMs) return true
        if (item.trackType != DanmakuTrackType.Scroll) return false
        val tail = scrollX(viewportWidth, nowMs, item.startTimeMs, item.pxPerMs) + item.textWidthPx
        return tail < 0f
    }

    private fun scrollX(viewportWidth: Int, nowMs: Double, startTimeMs: Int, pxPerMs: Float): Float {
        return (viewportWidth - (nowMs - startTimeMs).coerceAtLeast(0.0) * pxPerMs).toFloat()
    }

    private fun centerX(viewportWidth: Int, textWidthPx: Float): Float {
        if (viewportWidth <= 0) return 0f
        return ((viewportWidth.toFloat() - textWidthPx) / 2f).coerceAtLeast(0f)
    }

    private fun isScrollLaneAvailable(
        viewportWidthPx: Float,
        nowMs: Double,
        previous: DanmakuItem,
        previousTailX: Float,
        newSpeedPxPerMs: Float,
        marginPx: Float,
    ): Boolean {
        val previousRemainingMs = previous.durationMs - (nowMs - previous.startTimeMs)
        if (previousRemainingMs <= 0) return true
        if (previousTailX + marginPx > viewportWidthPx) return false
        if (newSpeedPxPerMs <= previous.pxPerMs) return true
        val gapPx = (viewportWidthPx - previousTailX - marginPx).coerceAtLeast(0f)
        return gapPx >= (newSpeedPxPerMs - previous.pxPerMs) * previousRemainingMs.toFloat()
    }

    private fun estimateTextWidthPx(item: DanmakuItem): Float {
        if (item.textWidthPx > 0f) return item.textWidthPx
        val textLength = item.text.trim().length.coerceAtLeast(1)
        val configScale = config.textSizeScale.coerceIn(25, 400) / 100f
        val textSizePx = config.textSizeSp.coerceAtLeast(MIN_TEXT_SIZE_SP) * DEFAULT_DENSITY * configScale
        val sizeScale = textSizePx / DEFAULT_TEXT_SIZE_PX
        val canvasPadding = config.textPaddingPx.coerceAtLeast(0)
        return textLength * DEFAULT_CHAR_WIDTH_PX * sizeScale + canvasPadding
    }

    private data class LayoutState(
        val laneHeight: Float,
        val textHeight: Float,
        val scrollLaneCount: Int,
        val topLaneCount: Int,
        val bottomLaneCount: Int,
        val topUsableHeight: Int,
        val bottomUsableHeight: Int,
    )

    private fun DanmakuLaneDensity.factor(): Float {
        return when (this) {
            DanmakuLaneDensity.Sparse -> 1.25f
            DanmakuLaneDensity.Standard -> 1f
            DanmakuLaneDensity.Dense -> 0.85f
        }
    }

    private fun Long.toIntSaturated(): Int {
        return coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

    private companion object {
        const val DEFAULT_VIEWPORT_WIDTH_PX = 1920f
        const val DEFAULT_VIEWPORT_HEIGHT_PX = 1080f
        const val DEFAULT_PLAYBACK_SPEED = 1f
        const val DEFAULT_DENSITY = 1f
        const val TEXT_HEIGHT_FACTOR = 1.08f
        const val LANE_GAP_PX = 3f
        const val DEFAULT_CHAR_WIDTH_PX = 18f
        const val DEFAULT_TEXT_SIZE_PX = 25f
        const val MIN_TEXT_SIZE_SP = 1f
        const val FIXED_TRACK_DURATION_MS = 4_000
        const val MAX_REWIND_LOOKBACK_MS = 20_000L
    }
}
