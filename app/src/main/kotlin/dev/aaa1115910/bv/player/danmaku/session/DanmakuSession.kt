package dev.aaa1115910.bv.player.danmaku.session

import dev.aaa1115910.bv.player.danmaku.config.DanmakuConfig
import dev.aaa1115910.bv.player.danmaku.config.DanmakuConfigApplyAction
import dev.aaa1115910.bv.player.danmaku.config.DanmakuConfigDiffer
import dev.aaa1115910.bv.player.danmaku.engine.DanmakuEngine
import dev.aaa1115910.bv.player.danmaku.engine.DanmakuEngineInputEvent
import dev.aaa1115910.bv.player.danmaku.engine.DanmakuInputStreamType
import dev.aaa1115910.bv.player.danmaku.engine.SimpleDanmakuEngine
import dev.aaa1115910.bv.player.danmaku.filter.DanmakuFilterBlockReason
import dev.aaa1115910.bv.player.danmaku.filter.DanmakuFilterChain
import dev.aaa1115910.bv.player.danmaku.filter.DanmakuFilterResult
import dev.aaa1115910.bv.player.danmaku.model.DanmakuFilterRule
import dev.aaa1115910.bv.player.danmaku.model.DanmakuItem
import dev.aaa1115910.bv.player.danmaku.model.DanmakuLiveEvent
import dev.aaa1115910.bv.player.danmaku.model.DanmakuLiveEventType
import dev.aaa1115910.bv.player.danmaku.model.DanmakuSessionEvent
import dev.aaa1115910.bv.player.danmaku.model.DanmakuSessionEventType
import dev.aaa1115910.bv.player.danmaku.model.DanmakuTrackType
import dev.aaa1115910.bv.player.danmaku.model.TimelineWindow
import dev.aaa1115910.bv.player.danmaku.model.VodDanmakuSegmentIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface PlaybackClock {
    fun currentPositionMs(): Long

    fun playbackSpeed(): Float = 1f
}

data class SessionHandle(
    val sessionId: String,
)

data class LiveSessionHandle(
    val sessionId: String,
    val roomId: Long,
)

private enum class VodDanmakuRequestRole {
    Current,
    Backfill,
    Prefetch,
}

interface DanmakuSession {
    fun startVod(
        aid: Long,
        cid: Long,
        startMs: Long,
        playbackClock: PlaybackClock,
    ): SessionHandle

    fun attachLive(
        roomId: Long,
        eventStream: DanmakuLiveEventStream,
        buffer: DanmakuLiveBuffer = DefaultDanmakuLiveBuffer(),
    ): LiveSessionHandle

    fun attachLive(
        roomId: Long,
        input: DanmakuLiveInput,
        buffer: DanmakuLiveBuffer = DefaultDanmakuLiveBuffer(),
    ): LiveSessionHandle

    fun flushLiveBuffer(sessionId: String, reason: String = "manual_flush"): Int

    fun resetLiveBuffer(sessionId: String, reason: String = "manual_reset"): Int

    fun stop(sessionId: String, reason: String = "stop")

    fun release()

    fun dispatch(event: SessionEvent)

    fun setEventListener(listener: DanmakuSessionEventListener)
}

fun interface DanmakuSessionEventListener {
    fun onEvent(event: DanmakuSessionEvent)
}

sealed class SessionEvent(
    open val sessionId: String,
    open val timestampMs: Long,
    open val sourceId: String,
    open val sequence: Long,
    open val payload: Map<String, Any?> = emptyMap(),
) {
    data class Add(
        override val sessionId: String,
        override val timestampMs: Long,
        override val sourceId: String,
        override val sequence: Long,
        val items: List<DanmakuItem>,
        val reason: String,
        val positionMs: Long? = null,
    ) : SessionEvent(sessionId, timestampMs, sourceId, sequence)

    data class Repopulate(
        override val sessionId: String,
        override val timestampMs: Long,
        override val sourceId: String,
        override val sequence: Long,
        val positionMs: Long,
        val viewportWidth: Int,
        val viewportHeight: Int,
        val reason: String,
    ) : SessionEvent(sessionId, timestampMs, sourceId, sequence)

    data class Clear(
        override val sessionId: String,
        override val timestampMs: Long,
        override val sourceId: String,
        override val sequence: Long,
        val reason: String,
    ) : SessionEvent(sessionId, timestampMs, sourceId, sequence)

    data class ResetSource(
        override val sessionId: String,
        override val timestampMs: Long,
        override val sourceId: String,
        override val sequence: Long,
        val mediaId: String,
        val mediaType: String,
        val initialPositionMs: Long,
        val keepConfig: Boolean,
    ) : SessionEvent(sessionId, timestampMs, sourceId, sequence)

    data class ConfigChanged(
        override val sessionId: String,
        override val timestampMs: Long,
        override val sourceId: String,
        override val sequence: Long,
        val config: DanmakuConfig,
        val configVersion: Int,
        val affectsLayout: Boolean,
        override val payload: Map<String, Any?> = emptyMap(),
    ) : SessionEvent(sessionId, timestampMs, sourceId, sequence, payload)

    data class FilterChanged(
        override val sessionId: String,
        override val timestampMs: Long,
        override val sourceId: String,
        override val sequence: Long,
        val filterRule: DanmakuFilterRule,
        val filterVersion: Int,
        val reason: String,
    ) : SessionEvent(sessionId, timestampMs, sourceId, sequence)

    data class Seek(
        override val sessionId: String,
        override val timestampMs: Long,
        override val sourceId: String,
        override val sequence: Long,
        val positionMs: Long,
        val fromPositionMs: Long? = null,
        val forceFetch: Boolean,
        val reason: String,
        override val payload: Map<String, Any?> = emptyMap(),
    ) : SessionEvent(sessionId, timestampMs, sourceId, sequence, payload)

    data class RefreshFromPosition(
        override val sessionId: String,
        override val timestampMs: Long,
        override val sourceId: String,
        override val sequence: Long,
        val positionMs: Long,
        val forceFetch: Boolean,
        val reason: String,
        override val payload: Map<String, Any?> = emptyMap(),
    ) : SessionEvent(sessionId, timestampMs, sourceId, sequence, payload)

    sealed class Live(
        override val sessionId: String,
        override val timestampMs: Long,
        override val sourceId: String,
        override val sequence: Long,
        open val roomId: Long,
    ) : SessionEvent(sessionId, timestampMs, sourceId, sequence) {
        data class Connect(
            override val sessionId: String,
            override val timestampMs: Long,
            override val sourceId: String,
            override val sequence: Long,
            override val roomId: Long,
            val initialConfig: DanmakuConfig,
        ) : Live(sessionId, timestampMs, sourceId, sequence, roomId)

        data class Disconnect(
            override val sessionId: String,
            override val timestampMs: Long,
            override val sourceId: String,
            override val sequence: Long,
            override val roomId: Long,
            val reason: String,
        ) : Live(sessionId, timestampMs, sourceId, sequence, roomId)

        data class Incoming(
            override val sessionId: String,
            override val timestampMs: Long,
            override val sourceId: String,
            override val sequence: Long,
            override val roomId: Long,
            val event: DanmakuLiveEvent,
            val receiveTimeMs: Long,
        ) : Live(sessionId, timestampMs, sourceId, sequence, roomId)

        data class Flush(
            override val sessionId: String,
            override val timestampMs: Long,
            override val sourceId: String,
            override val sequence: Long,
            override val roomId: Long,
            val reason: String,
        ) : Live(sessionId, timestampMs, sourceId, sequence, roomId)

        data class Reset(
            override val sessionId: String,
            override val timestampMs: Long,
            override val sourceId: String,
            override val sequence: Long,
            override val roomId: Long,
            val reason: String,
        ) : Live(sessionId, timestampMs, sourceId, sequence, roomId)

        data class Reconnect(
            override val sessionId: String,
            override val timestampMs: Long,
            override val sourceId: String,
            override val sequence: Long,
            override val roomId: Long,
            val reason: String,
            val lastSequence: Long? = null,
        ) : Live(sessionId, timestampMs, sourceId, sequence, roomId)

        data class StateChanged(
            override val sessionId: String,
            override val timestampMs: Long,
            override val sourceId: String,
            override val sequence: Long,
            override val roomId: Long,
            val state: String,
            val error: Throwable? = null,
        ) : Live(sessionId, timestampMs, sourceId, sequence, roomId)
    }
}

class SimpleDanmakuSession(
    private val engine: DanmakuEngine = SimpleDanmakuEngine(),
    private val vodSegmentFetcher: VodDanmakuSegmentFetcher = BiliVodDanmakuSegmentFetcher(),
    private val vodPollingIntervalMs: Long = 1_000L,
    private val liveFlushIntervalMs: Long = 250L,
) : DanmakuSession {
    private val filterChain = DanmakuFilterChain()
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vodMutex = Mutex()
    private var listener: DanmakuSessionEventListener = DanmakuSessionEventListener { }
    private var lastEvent: SessionEvent? = null
    private var currentConfig: DanmakuConfig = DanmakuConfig()
    private var currentFilterRule: DanmakuFilterRule = currentConfig.toFilterRule()
    private var currentFilterVersion: Int = 0
    private var currentSessionId: String? = null
    private var currentVodAid: Long? = null
    private var currentVodCid: Long? = null
    private val defaultRepopulateWindowMs = 30_000L
    private val vodSegmentIndex = VodDanmakuSegmentIndex()
    private val vodSegmentCache = mutableMapOf<VodDanmakuSegmentRequest, VodDanmakuSegmentResult>()
    private val loadingVodSegments = mutableSetOf<VodDanmakuSegmentRequest>()
    private val appendedVodSegments = mutableSetOf<VodDanmakuSegmentRequest>()
    private val appendedVodItemKeys = mutableSetOf<String>()
    private val accumulatedVodItems = mutableListOf<DanmakuItem>()
    private var vodPollingJob: Job? = null
    private var vodGeneration: Long = 0L
    private var liveBuffer: DanmakuLiveBuffer = DefaultDanmakuLiveBuffer()
    private var liveCollectJob: Job? = null
    private var liveFlushJob: Job? = null
    private var liveSequence: Long = 0L
    private var activeLiveRoomId: Long? = null
    private var liveReconnectAttempts: Int = 0
    private var released: Boolean = false

    override fun startVod(aid: Long, cid: Long, startMs: Long, playbackClock: PlaybackClock): SessionHandle {
        val sessionId = "vod-$aid-$cid"
        if (released) {
            publish(DanmakuSessionEventType.Error, sessionId, mapOf("reason" to "start_after_release", "message" to "session released"))
            return SessionHandle(sessionId)
        }
        currentSessionId = sessionId
        currentVodAid = aid
        currentVodCid = cid
        activeLiveRoomId = null
        vodPollingJob?.cancel()
        liveCollectJob?.cancel()
        liveFlushJob?.cancel()
        liveBuffer.clear()
        resetVodState()
        vodGeneration += 1
        publish(
            type = DanmakuSessionEventType.SessionStarted,
            sessionId = sessionId,
            payload = mapOf("mode" to "vod", "aid" to aid, "cid" to cid, "startMs" to startMs),
        )
        scheduleVodSegments(sessionId, aid, cid, startMs, "start_vod", includeBackfill = true)
        vodPollingJob = sessionScope.launch { pollVodSegments(sessionId, aid, cid, playbackClock) }
        return SessionHandle(sessionId)
    }

    override fun attachLive(roomId: Long, eventStream: DanmakuLiveEventStream, buffer: DanmakuLiveBuffer): LiveSessionHandle {
        val sessionId = "live-$roomId"
        val sourceId = "live-$roomId"
        if (released) {
            publish(DanmakuSessionEventType.Error, sessionId, mapOf("roomId" to roomId, "reason" to "attach_after_release", "message" to "session released"))
            return LiveSessionHandle(sessionId, roomId)
        }
        currentSessionId = sessionId
        currentVodAid = null
        currentVodCid = null
        activeLiveRoomId = roomId
        vodPollingJob?.cancel()
        liveCollectJob?.cancel()
        liveFlushJob?.cancel()
        liveBuffer.clear()
        buffer.clear()
        liveBuffer = buffer
        liveSequence = 0L
        liveReconnectAttempts = 0
        dispatch(SessionEvent.Live.Connect(sessionId, System.currentTimeMillis(), sourceId, nextLiveSequence(), roomId, currentConfig))
        liveFlushJob = sessionScope.launch { pollLiveBuffer(sessionId, roomId, sourceId) }
        liveCollectJob = sessionScope.launch { collectLiveWithRetry(sessionId, roomId, sourceId, eventStream) }
        return LiveSessionHandle(sessionId, roomId)
    }

    override fun attachLive(roomId: Long, input: DanmakuLiveInput, buffer: DanmakuLiveBuffer): LiveSessionHandle {
        return attachLive(roomId, DanmakuLiveEventStream { consumer -> input.collect(roomId, consumer) }, buffer)
    }

    override fun flushLiveBuffer(sessionId: String, reason: String): Int {
        val roomId = sessionId.removePrefix("live-").toLongOrNull() ?: return 0
        if (!isActiveLiveSession(sessionId, roomId)) return 0
        return onLiveFlush(SessionEvent.Live.Flush(sessionId, System.currentTimeMillis(), sessionId, nextLiveSequence(), roomId, reason))
    }

    override fun resetLiveBuffer(sessionId: String, reason: String): Int {
        val roomId = sessionId.removePrefix("live-").toLongOrNull() ?: return 0
        if (!isActiveLiveSession(sessionId, roomId)) return 0
        return onLiveReset(SessionEvent.Live.Reset(sessionId, System.currentTimeMillis(), sessionId, nextLiveSequence(), roomId, reason))
    }

    override fun stop(sessionId: String, reason: String) {
        if (currentSessionId != sessionId) return
        val roomId = sessionId.removePrefix("live-").toLongOrNull()
        vodPollingJob?.cancel()
        vodPollingJob = null
        if (roomId != null) {
            dispatch(SessionEvent.Live.StateChanged(sessionId, System.currentTimeMillis(), sessionId, nextLiveSequence(), roomId, "stopping"))
        }
        liveCollectJob?.cancel()
        liveCollectJob = null
        liveFlushJob?.cancel()
        liveFlushJob = null
        val droppedCount = liveBuffer.flush().size
        if (roomId != null) {
            publish(
                DanmakuSessionEventType.LiveBufferReset,
                sessionId,
                mapOf("roomId" to roomId, "sourceId" to sessionId, "droppedCount" to droppedCount, "reason" to reason),
            )
            publish(DanmakuSessionEventType.LiveDisconnected, sessionId, mapOf("roomId" to roomId, "reason" to reason))
        }
        publish(DanmakuSessionEventType.SessionStopped, sessionId, mapOf("reason" to reason))
        currentSessionId = null
        currentVodAid = null
        currentVodCid = null
        activeLiveRoomId = null
        liveReconnectAttempts = 0
        resetVodState()
    }

    override fun release() {
        if (released) return
        currentSessionId?.let { stop(it, "release") }
        released = true
        listener = DanmakuSessionEventListener { }
        sessionScope.cancel()
    }

    override fun dispatch(event: SessionEvent) {
        if (released && event !is SessionEvent.Live.Disconnect) return
        lastEvent = event
        when (event) {
            is SessionEvent.FilterChanged -> onFilterChanged(event)
            is SessionEvent.ConfigChanged -> onConfigChanged(event)
            is SessionEvent.Add -> onVodAdd(event)
            is SessionEvent.Repopulate -> onRepopulate(event)
            is SessionEvent.Seek -> onSeek(event)
            is SessionEvent.RefreshFromPosition -> onRefreshFromPosition(event)
            is SessionEvent.Live.Incoming -> onLiveIncoming(event)
            is SessionEvent.Live.Connect -> onLiveConnect(event)
            is SessionEvent.Live.Flush -> onLiveFlush(event)
            is SessionEvent.Live.Reset -> onLiveReset(event)
            is SessionEvent.Live.Reconnect -> onLiveReconnect(event)
            is SessionEvent.Live.Disconnect -> onLiveDisconnect(event)
            is SessionEvent.Live.StateChanged -> onLiveStateChanged(event)
            else -> Unit
        }
    }

    override fun setEventListener(listener: DanmakuSessionEventListener) {
        this.listener = listener
    }

    private fun onFilterChanged(event: SessionEvent.FilterChanged) {
        currentFilterRule = event.filterRule
        currentFilterVersion = event.filterVersion
        engine.updateFilterRule(currentFilterRule, currentFilterVersion, event.reason)
        publish(
            DanmakuSessionEventType.FilterRuleUpdated,
            event.sessionId,
            mapOf(
                "reason" to event.reason,
                "filterVersion" to event.filterVersion,
                "allowScroll" to event.filterRule.allowScroll,
                "allowTop" to event.filterRule.allowTop,
                "allowBottom" to event.filterRule.allowBottom,
                "minLevel" to event.filterRule.minLevel,
                "blockedKeywords" to event.filterRule.blockedKeywords.size,
                "blockedUsers" to event.filterRule.blockedUsers.size,
            ),
        )
    }

    private fun onConfigChanged(event: SessionEvent.ConfigChanged) {
        val oldConfig = currentConfig
        val configDiff = DanmakuConfigDiffer.diff(oldConfig, event.config)
        val shouldRepopulate = configDiff.applyAction == DanmakuConfigApplyAction.RepopulateRequired || event.affectsLayout
        currentConfig = event.config
        currentFilterRule = event.config.mergeToFilterRule(currentFilterRule)
        currentFilterVersion = event.configVersion
        if (shouldRepopulate) {
            val positionMs = event.payload.longValue("positionMs")?.coerceAtLeast(0L) ?: 0L
            val windowMs = event.payload.longValue("windowMs")?.coerceAtLeast(1L) ?: defaultRepopulateWindowMs
            val endMs = (positionMs + windowMs).coerceAtLeast(positionMs)
            engine.repopulate("config_changed", configDiff, TimelineWindow(positionMs, endMs))
            publish(
                DanmakuSessionEventType.VodRepopulateRequested,
                event.sessionId,
                mapOf(
                    "reason" to "config_changed",
                    "positionMs" to positionMs,
                    "startMs" to positionMs,
                    "endMs" to endMs,
                    "configVersion" to event.configVersion,
                    "repopulateKeys" to configDiff.repopulateKeys.toList(),
                ),
            )
        } else {
            engine.updateConfig(currentConfig, "config_changed_update_only")
        }
        engine.updateFilterRule(currentFilterRule, currentFilterVersion, "config_changed")
        publish(
            DanmakuSessionEventType.FilterRuleUpdated,
            event.sessionId,
            mapOf(
                "reason" to "config_changed",
                "configVersion" to event.configVersion,
                "filterVersion" to currentFilterVersion,
                "applyAction" to configDiff.applyAction.name,
                "updateOnlyKeys" to configDiff.updateOnlyKeys.toList(),
                "repopulateKeys" to configDiff.repopulateKeys.toList(),
            ),
        )
    }

    private fun onVodAdd(event: SessionEvent.Add) {
        val segmentIndex = event.sequence.toInt().coerceAtLeast(1)
        val dedupedItems = appendVodItems(event.items)
        val acceptedItems = mutableListOf<DanmakuItem>()
        var filteredCount = 0
        dedupedItems.forEach { item ->
            when (val result = filterChain.evaluate(item, currentFilterRule)) {
                is DanmakuFilterResult.Accepted -> acceptedItems += item
                is DanmakuFilterResult.Rejected -> {
                    filteredCount += 1
                    publishFiltered(event.sessionId, event.sourceId, result.reason, result.detail, isLive = false)
                }
            }
        }
        val acceptedCount = engine.onInput(DanmakuEngineInputEvent.Append(acceptedItems, event.sourceId, DanmakuInputStreamType.Vod))
        val trimmed = trimVodState(segmentIndex)
        if (trimmed) rebuildEngineFromAccumulated("vod_trim")
        if (acceptedCount > 0) {
            publish(
                DanmakuSessionEventType.DanmakuAccepted,
                event.sessionId,
                mapOf("sourceId" to event.sourceId, "acceptedCount" to acceptedCount, "filteredCount" to filteredCount, "isLive" to false),
            )
        }
        publish(
            DanmakuSessionEventType.VodSegmentAppended,
            event.sessionId,
            mapOf(
                "sourceId" to event.sourceId,
                "receivedCount" to event.items.size,
                "appendedCount" to dedupedItems.size,
                "duplicateCount" to event.items.size - dedupedItems.size,
                "acceptedCount" to acceptedCount,
                "filteredCount" to filteredCount,
                "totalCount" to accumulatedVodItems.size,
                "reason" to event.reason,
                "positionMs" to event.positionMs,
                "maxCachedSegments" to MAX_VOD_CACHED_SEGMENTS,
                "maxAppendedSegments" to MAX_VOD_APPENDED_SEGMENTS,
                "maxAccumulatedItems" to MAX_ACCUMULATED_VOD_ITEMS,
                "trimmed" to trimmed,
            ),
        )
    }

    private fun onRepopulate(event: SessionEvent.Repopulate) {
        val startMs = event.positionMs.coerceAtLeast(0L)
        val endMs = (startMs + 30_000L).coerceAtLeast(startMs)
        engine.repopulate(event.reason, null, TimelineWindow(startMs, endMs))
        publish(DanmakuSessionEventType.VodRepopulateRequested, event.sessionId, mapOf("positionMs" to event.positionMs, "startMs" to startMs, "endMs" to endMs, "reason" to event.reason))
    }

    private fun onSeek(event: SessionEvent.Seek) {
        val aid = event.payload.longValue("aid") ?: currentVodAid
        val cid = event.payload.longValue("cid") ?: currentVodCid
        scheduleVodSegments(event.sessionId, aid, cid, event.positionMs, event.reason, event.forceFetch, includeBackfill = true)
        publish(DanmakuSessionEventType.SeekNotified, event.sessionId, mapOf("positionMs" to event.positionMs, "fromPositionMs" to event.fromPositionMs, "forceFetch" to event.forceFetch, "reason" to event.reason))
    }

    private fun onRefreshFromPosition(event: SessionEvent.RefreshFromPosition) {
        val aid = event.payload.longValue("aid") ?: currentVodAid
        val cid = event.payload.longValue("cid") ?: currentVodCid
        val positionMs = event.positionMs.coerceAtLeast(0L)
        val segmentIndex = vodSegmentIndex.indexOf(positionMs)
        val retainedCount = if (aid != null && cid != null) {
            vodGeneration += 1
            rebuildVodFromPosition(event.sessionId, positionMs, segmentIndex, event.reason)
        } else {
            0
        }
        scheduleVodSegments(event.sessionId, aid, cid, positionMs, event.reason, event.forceFetch, positionMs, includeBackfill = true)
        publish(DanmakuSessionEventType.RefreshRequested, event.sessionId, mapOf("positionMs" to positionMs, "segmentIndex" to segmentIndex, "forceFetch" to event.forceFetch, "reason" to event.reason, "retainedCount" to retainedCount))
    }

    private fun scheduleVodSegments(
        sessionId: String,
        aid: Long?,
        cid: Long?,
        positionMs: Long,
        reason: String,
        forceCurrentFetch: Boolean = false,
        minAppendTimeMs: Long? = null,
        includeBackfill: Boolean = false,
    ) {
        val safePositionMs = positionMs.coerceAtLeast(0L)
        val currentSegment = vodSegmentIndex.segmentOf(safePositionMs)
        if (includeBackfill) {
            val nearLeftBoundary = safePositionMs - currentSegment.startMs <= VOD_BOUNDARY_BACKFILL_WINDOW_MS
            if (nearLeftBoundary && currentSegment.index > 1) {
                requestVodSegment(
                    sessionId = sessionId,
                    aid = aid,
                    cid = cid,
                    positionMs = safePositionMs,
                    reason = reason,
                    forceFetch = false,
                    minAppendTimeMs = null,
                    segmentIndex = currentSegment.index - 1,
                    requestRole = VodDanmakuRequestRole.Backfill,
                )
            }
        }
        requestVodSegment(
            sessionId = sessionId,
            aid = aid,
            cid = cid,
            positionMs = safePositionMs,
            reason = reason,
            forceFetch = forceCurrentFetch,
            minAppendTimeMs = minAppendTimeMs,
            segmentIndex = currentSegment.index,
            requestRole = VodDanmakuRequestRole.Current,
        )
        val nearRightBoundary = currentSegment.endMs - safePositionMs <= VOD_PREFETCH_LOOKAHEAD_MS
        if (nearRightBoundary) {
            requestVodSegment(
                sessionId = sessionId,
                aid = aid,
                cid = cid,
                positionMs = safePositionMs,
                reason = reason,
                forceFetch = false,
                minAppendTimeMs = null,
                segmentIndex = currentSegment.index + 1,
                requestRole = VodDanmakuRequestRole.Prefetch,
            )
        }
    }

    private fun requestVodSegment(
        sessionId: String,
        aid: Long?,
        cid: Long?,
        positionMs: Long,
        reason: String,
        forceFetch: Boolean = false,
        minAppendTimeMs: Long? = null,
        segmentIndex: Int? = null,
        requestRole: VodDanmakuRequestRole = VodDanmakuRequestRole.Current,
    ) {
        if (aid == null || cid == null) {
            publish(DanmakuSessionEventType.Error, sessionId, mapOf("reason" to reason, "message" to "missing vod aid or cid", "positionMs" to positionMs.coerceAtLeast(0L)))
            return
        }
        val segment = segmentIndex?.let { vodSegmentIndex.segmentAt(it) } ?: vodSegmentIndex.segmentOf(positionMs)
        val requestGeneration = vodGeneration
        val request = VodDanmakuSegmentRequest(aid, cid, segment.index)
        if (!forceFetch && (appendedVodSegments.contains(request) || loadingVodSegments.contains(request))) return
        publish(
            DanmakuSessionEventType.VodSegmentRequested,
            sessionId,
            mapOf(
                "aid" to aid,
                "cid" to cid,
                "positionMs" to positionMs.coerceAtLeast(0L),
                "segmentIndex" to segment.index,
                "segmentStartMs" to segment.startMs,
                "segmentEndMs" to segment.endMs,
                "segmentDurationMs" to vodSegmentIndex.segmentDurationMs,
                "forceFetch" to forceFetch,
                "reason" to reason,
                "requestRole" to requestRole.name,
                "loadingSegments" to loadingVodSegments.size,
                "cachedSegments" to vodSegmentCache.size,
                "appendedSegments" to appendedVodSegments.size,
            ),
        )
        sessionScope.launch { loadVodSegment(sessionId, request, positionMs, reason, forceFetch, minAppendTimeMs, requestGeneration) }
    }

    private suspend fun pollVodSegments(sessionId: String, aid: Long, cid: Long, playbackClock: PlaybackClock) {
        while (currentCoroutineContext().isActive && currentSessionId == sessionId && !released) {
            scheduleVodSegments(sessionId, aid, cid, playbackClock.currentPositionMs().coerceAtLeast(0L), "polling")
            delay(vodPollingIntervalMs.coerceAtLeast(100L))
        }
    }

    private suspend fun pollLiveBuffer(sessionId: String, roomId: Long, sourceId: String) {
        while (currentCoroutineContext().isActive && isActiveLiveSession(sessionId, roomId)) {
            dispatch(SessionEvent.Live.Flush(sessionId, System.currentTimeMillis(), sourceId, nextLiveSequence(), roomId, "live_tick"))
            delay(liveFlushIntervalMs.coerceAtLeast(50L))
        }
    }

    private suspend fun collectLiveWithRetry(
        sessionId: String,
        roomId: Long,
        sourceId: String,
        eventStream: DanmakuLiveEventStream,
    ) {
        while (currentCoroutineContext().isActive && isActiveLiveSession(sessionId, roomId)) {
            try {
                dispatch(SessionEvent.Live.StateChanged(sessionId, System.currentTimeMillis(), sourceId, nextLiveSequence(), roomId, "connecting"))
                eventStream.collect { event ->
                    val receiveTimeMs = System.currentTimeMillis()
                    if (liveReconnectAttempts != 0) liveReconnectAttempts = 0
                    dispatch(SessionEvent.Live.StateChanged(sessionId, receiveTimeMs, sourceId, nextLiveSequence(), roomId, "collecting"))
                    dispatch(SessionEvent.Live.Incoming(sessionId, receiveTimeMs, sourceId, nextLiveSequence(), roomId, event, receiveTimeMs))
                }
                if (isActiveLiveSession(sessionId, roomId)) {
                    dispatch(SessionEvent.Live.Disconnect(sessionId, System.currentTimeMillis(), sourceId, nextLiveSequence(), roomId, "input_completed"))
                }
                return
            } catch (e: CancellationException) {
                if (isActiveLiveSession(sessionId, roomId)) {
                    dispatch(SessionEvent.Live.StateChanged(sessionId, System.currentTimeMillis(), sourceId, nextLiveSequence(), roomId, "cancelled"))
                }
                throw e
            } catch (e: Throwable) {
                if (!isActiveLiveSession(sessionId, roomId)) return
                liveReconnectAttempts += 1
                dispatch(SessionEvent.Live.StateChanged(sessionId, System.currentTimeMillis(), sourceId, nextLiveSequence(), roomId, "error", e))
                publish(
                    DanmakuSessionEventType.Error,
                    sessionId,
                    mapOf(
                        "roomId" to roomId,
                        "sourceId" to sourceId,
                        "reason" to "live_collect_error",
                        "message" to e.message,
                        "errorClass" to e::class.simpleName,
                        "attempt" to liveReconnectAttempts,
                    ),
                )
                if (liveReconnectAttempts > MAX_LIVE_RECONNECT_ATTEMPTS) {
                    dispatch(SessionEvent.Live.Disconnect(sessionId, System.currentTimeMillis(), sourceId, nextLiveSequence(), roomId, "max_reconnect_attempts"))
                    return
                }
                dispatch(SessionEvent.Live.Reconnect(sessionId, System.currentTimeMillis(), sourceId, nextLiveSequence(), roomId, "collect_error", liveSequence))
                delay(liveReconnectDelayMs(liveReconnectAttempts))
            }
        }
    }

    private suspend fun loadVodSegment(
        sessionId: String,
        request: VodDanmakuSegmentRequest,
        positionMs: Long,
        reason: String,
        forceFetch: Boolean,
        minAppendTimeMs: Long? = null,
        generation: Long,
    ) {
        if (generation != vodGeneration) return
        val cachedResult = vodMutex.withLock {
            if (!forceFetch && appendedVodSegments.contains(request)) return
            if (!forceFetch) vodSegmentCache[request] else null
        }
        if (cachedResult != null) {
            appendVodSegment(sessionId, cachedResult, positionMs, "${reason}_cache", minAppendTimeMs)
            return
        }
        val shouldFetch = vodMutex.withLock {
            if (loadingVodSegments.contains(request)) false else {
                loadingVodSegments += request
                true
            }
        }
        if (!shouldFetch) return
        try {
            val result = vodSegmentFetcher.fetch(request)
            if (currentSessionId != sessionId || generation != vodGeneration) return
            vodMutex.withLock {
                vodSegmentCache[request] = result
                trimVodSegmentCache(request.segmentIndex)
            }
            appendVodSegment(sessionId, result, positionMs, reason, minAppendTimeMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            publish(DanmakuSessionEventType.Error, sessionId, mapOf("reason" to reason, "aid" to request.aid, "cid" to request.cid, "segmentIndex" to request.segmentIndex, "message" to e.message))
        } finally {
            vodMutex.withLock { loadingVodSegments -= request }
        }
    }

    private fun appendVodSegment(
        sessionId: String,
        result: VodDanmakuSegmentResult,
        positionMs: Long,
        reason: String,
        minAppendTimeMs: Long? = null,
    ) {
        appendedVodSegments += result.request
        val items = minAppendTimeMs?.let { minTimeMs -> result.items.filter { it.timeMs.toLong() >= minTimeMs } } ?: result.items
        dispatch(SessionEvent.Add(sessionId, System.currentTimeMillis(), result.sourceId, result.request.segmentIndex.toLong(), items, reason, positionMs))
    }

    private fun appendVodItems(items: List<DanmakuItem>): List<DanmakuItem> {
        if (items.isEmpty()) return emptyList()
        val dedupedItems = items.filter { item -> appendedVodItemKeys.add(item.dedupKey()) }
        if (dedupedItems.isNotEmpty()) {
            accumulatedVodItems += dedupedItems
            accumulatedVodItems.sortWith(compareBy({ it.timeMs }, { it.id }, { it.text }))
        }
        return dedupedItems
    }

    private fun rebuildVodFromPosition(sessionId: String, positionMs: Long, segmentIndex: Int, reason: String): Int {
        val retainedItems = accumulatedVodItems.filter { it.timeMs.toLong() < positionMs }
        vodSegmentCache.keys.removeAll { it.segmentIndex >= segmentIndex }
        loadingVodSegments.removeAll { it.segmentIndex >= segmentIndex }
        appendedVodSegments.removeAll { it.segmentIndex >= segmentIndex }
        appendedVodItemKeys.clear()
        appendedVodItemKeys += retainedItems.map { it.dedupKey() }
        accumulatedVodItems.clear()
        accumulatedVodItems += retainedItems
        trimVodState((segmentIndex - 1).coerceAtLeast(1))
        rebuildEngineFromAccumulated("refresh_from_position")
        dispatch(SessionEvent.Repopulate(sessionId, System.currentTimeMillis(), "vod-refresh", segmentIndex.toLong(), positionMs, 0, 0, reason))
        return accumulatedVodItems.size
    }

    private fun trimVodState(anchorSegmentIndex: Int): Boolean {
        val minSegmentIndex = (anchorSegmentIndex - MAX_VOD_APPENDED_SEGMENTS + 1).coerceAtLeast(1)
        var trimmed = trimVodSegmentCache(anchorSegmentIndex)
        trimmed = appendedVodSegments.removeAll { it.segmentIndex < minSegmentIndex } || trimmed
        while (appendedVodSegments.size > MAX_VOD_APPENDED_SEGMENTS) {
            val oldest = appendedVodSegments.minByOrNull { it.segmentIndex } ?: break
            appendedVodSegments.remove(oldest)
            trimmed = true
        }
        val minRetainedTimeMs = vodSegmentIndex.segmentAt(minSegmentIndex).startMs
        val beforeItems = accumulatedVodItems.size
        accumulatedVodItems.removeAll { it.timeMs.toLong() < minRetainedTimeMs }
        if (accumulatedVodItems.size > MAX_ACCUMULATED_VOD_ITEMS) {
            val dropCount = accumulatedVodItems.size - MAX_ACCUMULATED_VOD_ITEMS
            accumulatedVodItems.subList(0, dropCount).clear()
        }
        if (accumulatedVodItems.size != beforeItems) {
            rebuildVodItemKeys()
            trimmed = true
        }
        return trimmed
    }

    private fun trimVodSegmentCache(anchorSegmentIndex: Int): Boolean {
        val minSegmentIndex = (anchorSegmentIndex - MAX_VOD_CACHED_SEGMENTS + 1).coerceAtLeast(1)
        var trimmed = vodSegmentCache.keys.removeAll { it.segmentIndex < minSegmentIndex }
        while (vodSegmentCache.size > MAX_VOD_CACHED_SEGMENTS) {
            val oldest = vodSegmentCache.keys.minByOrNull { it.segmentIndex } ?: break
            vodSegmentCache.remove(oldest)
            trimmed = true
        }
        return trimmed
    }

    private fun rebuildVodItemKeys() {
        appendedVodItemKeys.clear()
        appendedVodItemKeys += accumulatedVodItems.map { it.dedupKey() }
    }

    private fun rebuildEngineFromAccumulated(reason: String) {
        engine.clear(reason = reason)
        accumulatedVodItems.groupBy { it.source }.forEach { (source, items) ->
            engine.onInput(DanmakuEngineInputEvent.Append(items, source, DanmakuInputStreamType.Vod))
        }
    }

    private fun resetVodState() {
        vodSegmentCache.clear()
        loadingVodSegments.clear()
        appendedVodSegments.clear()
        appendedVodItemKeys.clear()
        accumulatedVodItems.clear()
    }

    private fun onLiveIncoming(event: SessionEvent.Live.Incoming) {
        if (!isActiveLiveSession(event.sessionId, event.roomId)) return
        publish(
            DanmakuSessionEventType.LiveEventReceived,
            event.sessionId,
            mapOf(
                "roomId" to event.roomId,
                "eventType" to event.event.type.name,
                "eventTimestampMs" to event.event.timestampMs,
                "popularity" to event.event.popularity,
                "popularityText" to event.event.popularityText,
                "onlineRankCount" to event.event.onlineRankCount,
            ),
        )
        if (event.event.type != DanmakuLiveEventType.Danmaku) {
            publishLiveDropped(event.sessionId, event.sourceId, event.roomId, DanmakuLiveDropReason.NonDanmaku, liveBuffer.size(), event.event.type.name)
            return
        }
        val result = liveBuffer.append(event.event)
        if (result.dropped) {
            publishLiveDropped(event.sessionId, event.sourceId, event.roomId, result.dropReason ?: DanmakuLiveDropReason.BufferFull, result.bufferSize, event.event.type.name)
        }
    }

    private fun onLiveConnect(event: SessionEvent.Live.Connect) {
        if (!isActiveLiveSession(event.sessionId, event.roomId)) return
        publish(DanmakuSessionEventType.LiveConnected, event.sessionId, mapOf("roomId" to event.roomId, "sourceId" to event.sourceId, "state" to "connected"))
    }

    private fun onLiveStateChanged(event: SessionEvent.Live.StateChanged) {
        if (!isActiveLiveSession(event.sessionId, event.roomId)) return
        publish(
            DanmakuSessionEventType.LiveStateChanged,
            event.sessionId,
            mapOf(
                "roomId" to event.roomId,
                "sourceId" to event.sourceId,
                "state" to event.state,
                "error" to event.error?.message,
                "attempt" to liveReconnectAttempts,
            ),
        )
    }

    private fun onLiveReconnect(event: SessionEvent.Live.Reconnect) {
        if (!isActiveLiveSession(event.sessionId, event.roomId)) return
        publish(
            DanmakuSessionEventType.LiveStateChanged,
            event.sessionId,
            mapOf(
                "roomId" to event.roomId,
                "sourceId" to event.sourceId,
                "state" to "reconnecting",
                "reason" to event.reason,
                "attempt" to liveReconnectAttempts,
                "lastSequence" to event.lastSequence,
            ),
        )
    }

    private fun onLiveFlush(event: SessionEvent.Live.Flush): Int {
        if (!isActiveLiveSession(event.sessionId, event.roomId)) return 0
        val events = liveBuffer.flush()
        if (events.isEmpty()) {
            publish(DanmakuSessionEventType.LiveBufferFlushed, event.sessionId, mapOf("roomId" to event.roomId, "sourceId" to event.sourceId, "receivedCount" to 0, "acceptedCount" to 0, "filteredCount" to 0, "reason" to event.reason))
            return 0
        }
        val acceptedItems = mutableListOf<DanmakuItem>()
        var filteredCount = 0
        events.forEach { liveEvent ->
            val item = liveEvent.toDanmakuItem(event.roomId, event.sourceId, liveEvent.timestampMs) ?: return@forEach
            when (val result = filterChain.evaluate(item, currentFilterRule)) {
                is DanmakuFilterResult.Accepted -> acceptedItems += item
                is DanmakuFilterResult.Rejected -> {
                    filteredCount += 1
                    publishFiltered(event.sessionId, event.sourceId, result.reason, result.detail, isLive = true)
                }
            }
        }
        val acceptedCount = engine.onInput(DanmakuEngineInputEvent.Append(acceptedItems, event.sourceId, DanmakuInputStreamType.Live))
        if (acceptedCount > 0) {
            publish(DanmakuSessionEventType.DanmakuAccepted, event.sessionId, mapOf("sourceId" to event.sourceId, "acceptedCount" to acceptedCount, "filteredCount" to filteredCount, "isLive" to true))
        }
        publish(DanmakuSessionEventType.LiveBufferFlushed, event.sessionId, mapOf("roomId" to event.roomId, "sourceId" to event.sourceId, "receivedCount" to events.size, "acceptedCount" to acceptedCount, "filteredCount" to filteredCount, "reason" to event.reason))
        return acceptedCount
    }

    private fun onLiveReset(event: SessionEvent.Live.Reset): Int {
        if (!isActiveLiveSession(event.sessionId, event.roomId)) return 0
        val droppedCount = liveBuffer.flush().size
        publish(DanmakuSessionEventType.LiveBufferReset, event.sessionId, mapOf("roomId" to event.roomId, "sourceId" to event.sourceId, "droppedCount" to droppedCount, "reason" to event.reason))
        return droppedCount
    }

    private fun onLiveDisconnect(event: SessionEvent.Live.Disconnect) {
        if (currentSessionId != event.sessionId || activeLiveRoomId != event.roomId) return
        liveFlushJob?.cancel()
        liveFlushJob = null
        liveCollectJob?.let { job -> if (job.isActive) job.cancel() }
        liveCollectJob = null
        liveBuffer.clear()
        activeLiveRoomId = null
        liveReconnectAttempts = 0
        publish(DanmakuSessionEventType.LiveDisconnected, event.sessionId, mapOf("roomId" to event.roomId, "reason" to event.reason))
    }

    private fun publishLiveDropped(sessionId: String, sourceId: String, roomId: Long, reason: DanmakuLiveDropReason, bufferSize: Int, eventType: String) {
        publish(DanmakuSessionEventType.LiveEventDropped, sessionId, mapOf("sourceId" to sourceId, "roomId" to roomId, "reason" to reason.name, "bufferSize" to bufferSize, "eventType" to eventType, "maxBufferSize" to DefaultDanmakuLiveBuffer.DEFAULT_MAX_BUFFER_SIZE))
    }

    private fun publishFiltered(sessionId: String, sourceId: String, reason: DanmakuFilterBlockReason, detail: String, isLive: Boolean) {
        publish(DanmakuSessionEventType.DanmakuFiltered, sessionId, mapOf("sourceId" to sourceId, "reason" to reason.name, "detail" to detail, "isLive" to isLive))
    }

    private fun publish(type: DanmakuSessionEventType, sessionId: String = currentSessionId ?: "unknown", payload: Map<String, Any?> = emptyMap()) {
        listener.onEvent(DanmakuSessionEvent(type, sessionId, System.currentTimeMillis(), payload))
    }

    private fun nextLiveSequence(): Long {
        liveSequence += 1
        return liveSequence
    }

    private fun isActiveLiveSession(sessionId: String, roomId: Long): Boolean {
        return !released && currentSessionId == sessionId && activeLiveRoomId == roomId
    }

    private fun liveReconnectDelayMs(attempt: Int): Long {
        var delayMs = INITIAL_LIVE_RECONNECT_DELAY_MS
        repeat((attempt - 1).coerceAtLeast(0)) {
            delayMs = (delayMs * 2).coerceAtMost(MAX_LIVE_RECONNECT_DELAY_MS)
        }
        return delayMs
    }

    private companion object {
        const val MAX_VOD_CACHED_SEGMENTS = 6
        const val MAX_VOD_APPENDED_SEGMENTS = 8
        const val MAX_ACCUMULATED_VOD_ITEMS = 12_000
        const val VOD_PREFETCH_LOOKAHEAD_MS = 15_000L
        const val VOD_BOUNDARY_BACKFILL_WINDOW_MS = 15_000L
        const val MAX_LIVE_RECONNECT_ATTEMPTS = 5
        const val INITIAL_LIVE_RECONNECT_DELAY_MS = 1_000L
        const val MAX_LIVE_RECONNECT_DELAY_MS = 30_000L
    }
}

private fun Map<String, Any?>.longValue(key: String): Long? {
    return (this[key] as? Number)?.toLong()
}

private fun DanmakuItem.dedupKey(): String {
    return cacheKey ?: "$source-$id-$timeMs-$text"
}

private fun DanmakuLiveEvent.toDanmakuItem(roomId: Long, source: String, receiveTimeMs: Long): DanmakuItem? {
    if (type != DanmakuLiveEventType.Danmaku) return null
    val text = content?.trim().orEmpty()
    if (text.isBlank()) return null
    return DanmakuItem(
        id = timestampMs,
        userId = userId,
        timeMs = 0,
        text = text,
        mode = mode ?: 1,
        textSize = fontSize ?: 25,
        color = color ?: 0xFFFFFF,
        level = userLevel ?: 0,
        source = source.ifBlank { "live-$roomId" },
        arrivalTimeMs = receiveTimeMs,
        trackType = mode.toTrackType(),
    )
}

private fun Int?.toTrackType(): DanmakuTrackType {
    return when (this) {
        5 -> DanmakuTrackType.Top
        4 -> DanmakuTrackType.Bottom
        else -> DanmakuTrackType.Scroll
    }
}
