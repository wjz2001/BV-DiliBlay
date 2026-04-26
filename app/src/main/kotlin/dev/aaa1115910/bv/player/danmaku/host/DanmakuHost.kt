package dev.aaa1115910.bv.player.danmaku.host

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import dev.aaa1115910.biliapi.entity.danmaku.DanmakuMask
import dev.aaa1115910.bv.player.danmaku.config.DanmakuConfig
import dev.aaa1115910.bv.player.danmaku.config.DanmakuConfigApplyAction
import dev.aaa1115910.bv.player.danmaku.engine.DanmakuEngine
import dev.aaa1115910.bv.player.danmaku.engine.DanmakuEngineAdvanceRequest
import dev.aaa1115910.bv.player.danmaku.engine.SimpleDanmakuEngine
import dev.aaa1115910.bv.player.danmaku.mask.DanmakuMaskAdapter
import dev.aaa1115910.bv.player.danmaku.mask.DefaultDanmakuMaskAdapter
import dev.aaa1115910.bv.player.danmaku.mask.Viewport
import dev.aaa1115910.bv.player.danmaku.model.DanmakuSessionEvent
import dev.aaa1115910.bv.player.danmaku.model.MaskRegionSet
import dev.aaa1115910.bv.player.danmaku.model.TimelineWindow
import dev.aaa1115910.bv.player.danmaku.renderer.ComposeDanmakuRenderer
import dev.aaa1115910.bv.player.danmaku.renderer.DanmakuRenderer
import dev.aaa1115910.bv.player.danmaku.renderer.DanmakuRendererHost
import dev.aaa1115910.bv.player.danmaku.renderer.DanmakuRendererInput
import dev.aaa1115910.bv.player.danmaku.session.DanmakuLiveEventStream
import dev.aaa1115910.bv.player.danmaku.session.DanmakuLiveInput
import dev.aaa1115910.bv.player.danmaku.session.DanmakuSession
import dev.aaa1115910.bv.player.danmaku.session.PlaybackClock
import dev.aaa1115910.bv.player.danmaku.session.SessionEvent
import dev.aaa1115910.bv.player.danmaku.session.SimpleDanmakuSession
import dev.aaa1115910.bv.player.danmaku.ui.DanmakuHostSourceType
import dev.aaa1115910.bv.player.danmaku.ui.DanmakuHostState
import dev.aaa1115910.bv.util.calculateMaskDelay
import dev.aaa1115910.bv.viewmodel.player.DanmakuHostCommand
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

data class HostBinding(
    val player: Any,
    val hostState: DanmakuHostState,
    val engine: DanmakuEngine,
    val renderer: DanmakuRenderer,
)

sealed class DanmakuSourceMode {
    object None : DanmakuSourceMode()
    data class Vod(
        val aid: Long,
        val cid: Long,
    ) : DanmakuSourceMode()

    data class Live(
        val roomId: Long,
        val eventStream: DanmakuLiveEventStream? = null,
        val input: DanmakuLiveInput? = null,
    ) : DanmakuSourceMode()
}

interface DanmakuHost {
    fun bind(
        player: Any,
        hostState: DanmakuHostState,
        engine: DanmakuEngine,
        renderer: DanmakuRenderer,
        session: DanmakuSession? = null,
        maskAdapter: DanmakuMaskAdapter? = null,
    ): HostBinding

    fun refresh(positionMs: Long)
}

class SimpleDanmakuHost : DanmakuHost {
    private var binding: HostBinding? = null

    override fun bind(
        player: Any,
        hostState: DanmakuHostState,
        engine: DanmakuEngine,
        renderer: DanmakuRenderer,
        session: DanmakuSession?,
        maskAdapter: DanmakuMaskAdapter?,
    ): HostBinding {
        hostState.setBound(true)
        return HostBinding(
            player = player,
            hostState = hostState,
            engine = engine,
            renderer = renderer,
        ).also { binding = it }
    }

    override fun refresh(positionMs: Long) {
        val rendererState = binding?.renderer?.clear(reason = "host_refresh") ?: return
        binding?.hostState?.applyRendererState(rendererState)
    }
}

@Composable
fun rememberDanmakuHostState(): DanmakuHostState {
    return remember { DanmakuHostState() }
}

@Composable
fun BvDanmakuSurface(
    currentTime: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    config: DanmakuConfig,
    mask: DanmakuMask?,
    sourceMode: DanmakuSourceMode,
    modifier: Modifier = Modifier,
    hostState: DanmakuHostState = rememberDanmakuHostState(),
    engine: DanmakuEngine = remember { SimpleDanmakuEngine(config) },
    renderer: DanmakuRenderer = remember { ComposeDanmakuRenderer() },
    session: DanmakuSession = remember(engine) { SimpleDanmakuSession(engine = engine) },
    maskEnabled: Boolean = mask != null,
    maskAdapter: DanmakuMaskAdapter = remember { DefaultDanmakuMaskAdapter() },
    videoAspectRatio: Float? = null,
    commandFlow: Flow<DanmakuHostCommand>? = null,
    onSessionEvent: (DanmakuSessionEvent) -> Unit = {},
) {
    val latestCurrentTime by rememberUpdatedState(currentTime)
    val latestPlaybackSpeed by rememberUpdatedState(playbackSpeed)
    val latestMask by rememberUpdatedState(mask)
    val latestSourceMode by rememberUpdatedState(sourceMode)
    val density = LocalDensity.current

    LaunchedEffect(session) {
        session.setEventListener { event ->
            hostState.onSessionEvent(event)
            onSessionEvent(event)
        }
    }

    LaunchedEffect(commandFlow, session, hostState) {
        commandFlow?.collect { command ->
            handleDanmakuHostCommand(
                command = command,
                sourceMode = latestSourceMode,
                hostState = hostState,
                engine = engine,
                renderer = renderer,
                session = session,
            )
        }
    }

    LaunchedEffect(sourceMode) {
        when (sourceMode) {
            DanmakuSourceMode.None -> {
                if (hostState.sessionId.isNotBlank()) {
                    session.stop(hostState.sessionId, reason = "source_none")
                }
                hostState.updateSource(DanmakuHostSourceType.None, sessionId = "")
                engine.clear(reason = "source_none")
                hostState.applyRendererState(renderer.clear(reason = "source_none"))
            }

            is DanmakuSourceMode.Vod -> {
                val handle = session.startVod(
                    aid = sourceMode.aid,
                    cid = sourceMode.cid,
                    startMs = latestCurrentTime.coerceAtLeast(0L),
                    playbackClock = object : PlaybackClock {
                        override fun currentPositionMs(): Long = latestCurrentTime.coerceAtLeast(0L)

                        override fun playbackSpeed(): Float = latestPlaybackSpeed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
                    },
                )
                hostState.updateSource(DanmakuHostSourceType.Vod, sessionId = handle.sessionId)
            }

            is DanmakuSourceMode.Live -> {
                val handle = when {
                    sourceMode.eventStream != null -> session.attachLive(
                        roomId = sourceMode.roomId,
                        eventStream = sourceMode.eventStream,
                    )

                    sourceMode.input != null -> session.attachLive(
                        roomId = sourceMode.roomId,
                        input = sourceMode.input,
                    )

                    else -> null
                }
                hostState.updateSource(DanmakuHostSourceType.Live, sessionId = handle?.sessionId.orEmpty())
            }
        }
    }

    LaunchedEffect(config) {
        val observation = hostState.observeConfig(config)
        if (hostState.sessionId.isNotBlank()) {
            session.dispatch(
                SessionEvent.ConfigChanged(
                    sessionId = hostState.sessionId,
                    timestampMs = System.currentTimeMillis(),
                    sourceId = hostState.sourceMode,
                    sequence = observation.configVersion.toLong(),
                    config = config,
                    configVersion = observation.configVersion,
                    affectsLayout = observation.diff.affectsLayout,
                    payload = mapOf("positionMs" to latestCurrentTime),
                )
            )
        } else {
            engine.updateConfig(config = config, reason = observation.engineUpdateReason)
            if (observation.applyAction == DanmakuConfigApplyAction.RepopulateRequired) {
                engine.repopulate(
                    reason = observation.repopulateReason,
                    configDiff = observation.diff,
                    timelineWindow = TimelineWindow(
                        startMs = latestCurrentTime.coerceAtLeast(0L),
                        endMs = latestCurrentTime.coerceAtLeast(0L) + REPOPULATE_WINDOW_MS,
                    ),
                )
            }
        }
        hostState.markConfigApplied(
            action = observation.applyAction,
            reason = "config_applied",
        )
    }

    LaunchedEffect(mask) {
        maskAdapter.reset()
    }

    DisposableEffect(Unit) {
        hostState.setBound(true)
        onDispose {
            if (hostState.sessionId.isNotBlank()) {
                session.stop(hostState.sessionId, reason = "host_dispose")
            }
            session.release()
            maskAdapter.reset()
            hostState.applyRendererState(renderer.clear(reason = "host_dispose"))
            hostState.setBound(false)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportWidth = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val viewportHeight = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        var currentMaskRegionSet by remember {
            mutableStateOf<MaskRegionSet?>(null)
        }

        LaunchedEffect(mask, maskEnabled, isPlaying, viewportWidth, viewportHeight, if (isPlaying) 0L else currentTime) {
            if (mask == null || maskEnabled.not()) {
                currentMaskRegionSet = null
                return@LaunchedEffect
            }

            while (true) {
                val safePositionMs = latestCurrentTime.coerceAtLeast(0L)
                val nextMaskRegionSet = maskAdapter.adapt(
                    mask = latestMask,
                    viewport = Viewport(
                        width = viewportWidth,
                        height = viewportHeight,
                    ),
                    positionMs = safePositionMs,
                )
                currentMaskRegionSet = nextMaskRegionSet
                delay(
                    calculateMaskDelay(
                        currentFrame = nextMaskRegionSet.frame,
                        currentTime = safePositionMs,
                        isPlaying = isPlaying,
                    )
                )
            }
        }

        LaunchedEffect(isPlaying, viewportWidth, viewportHeight) {
            hostState.updatePlayback(
                playing = isPlaying,
                positionMs = latestCurrentTime,
                speed = latestPlaybackSpeed,
            )
            if (isPlaying) {
                while (true) {
                    withFrameNanos { }
                    advanceDanmakuHost(
                        hostState = hostState,
                        engine = engine,
                        renderer = renderer,
                        config = config,
                        positionMs = latestCurrentTime,
                        playbackSpeed = latestPlaybackSpeed,
                        viewportWidth = viewportWidth,
                        viewportHeight = viewportHeight,
                        maskRegionSet = currentMaskRegionSet,
                        syncHostState = false,
                    )
                }
            } else {
                advanceDanmakuHost(
                    hostState = hostState,
                    engine = engine,
                    renderer = renderer,
                    config = config,
                    positionMs = latestCurrentTime,
                    playbackSpeed = latestPlaybackSpeed,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    maskRegionSet = currentMaskRegionSet,
                    reason = "paused",
                )
            }
        }

        LaunchedEffect(currentTime, playbackSpeed, mask, maskEnabled, currentMaskRegionSet, viewportWidth, viewportHeight) {
            if (!isPlaying) {
                advanceDanmakuHost(
                    hostState = hostState,
                    engine = engine,
                    renderer = renderer,
                    config = config,
                    positionMs = currentTime,
                    playbackSpeed = playbackSpeed,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    maskRegionSet = currentMaskRegionSet,
                    reason = "state_changed",
                )
            }
        }

        DanmakuRendererHost(
            renderer = renderer,
            modifier = Modifier.fillMaxSize(),
            config = config,
            videoAspectRatio = videoAspectRatio,
        )
    }
}

private fun handleDanmakuHostCommand(
    command: DanmakuHostCommand,
    sourceMode: DanmakuSourceMode,
    hostState: DanmakuHostState,
    engine: DanmakuEngine,
    renderer: DanmakuRenderer,
    session: DanmakuSession,
) {
    val sessionId = hostState.sessionId
    val timestampMs = System.currentTimeMillis()
    val sourceId = sourceMode.commandSourceId()

    when (command) {
        is DanmakuHostCommand.Seek -> {
            if (sessionId.isNotBlank() && DanmakuHostCommandStability.shouldDispatchSeek(sessionId, command.positionMs, timestampMs)) {
                session.dispatch(
                    SessionEvent.Seek(
                        sessionId = sessionId,
                        timestampMs = timestampMs,
                        sourceId = sourceId,
                        sequence = timestampMs,
                        positionMs = command.positionMs,
                        fromPositionMs = hostState.currentPositionMs,
                        forceFetch = command.forceFetch,
                        reason = command.reason,
                    )
                )
            }
        }

        is DanmakuHostCommand.RefreshFromPosition -> {
            if (sessionId.isNotBlank()) {
                session.dispatch(
                    SessionEvent.RefreshFromPosition(
                        sessionId = sessionId,
                        timestampMs = timestampMs,
                        sourceId = sourceId,
                        sequence = timestampMs,
                        positionMs = command.positionMs,
                        forceFetch = command.forceFetch,
                        reason = command.reason,
                    )
                )
            }
        }

        is DanmakuHostCommand.Clear -> {
            engine.clear(reason = command.reason)
            hostState.applyRendererState(renderer.clear(reason = command.reason))
        }
    }
}

private fun DanmakuSourceMode.commandSourceId(): String {
    return when (this) {
        DanmakuSourceMode.None -> "none"
        is DanmakuSourceMode.Vod -> "vod-$aid-$cid"
        is DanmakuSourceMode.Live -> "live-$roomId"
    }
}

private fun advanceDanmakuHost(
    hostState: DanmakuHostState,
    engine: DanmakuEngine,
    renderer: DanmakuRenderer,
    config: DanmakuConfig,
    positionMs: Long,
    playbackSpeed: Float,
    viewportWidth: Int,
    viewportHeight: Int,
    maskRegionSet: MaskRegionSet?,
    reason: String = "tick",
    syncHostState: Boolean = true,
) {
    val safePositionMs = positionMs.coerceAtLeast(0L)
    val safePlaybackSpeed = playbackSpeed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
    if (syncHostState) {
        hostState.updatePlayback(
            playing = hostState.isPlaying,
            positionMs = safePositionMs,
            speed = safePlaybackSpeed,
        )
        hostState.updateViewport(
            width = viewportWidth,
            height = viewportHeight,
            config = config,
        )
    }
    if (hostState.requiresEngineAdvance.not()) return

    val snapshot = engine.advance(
        DanmakuEngineAdvanceRequest(
            positionMs = safePositionMs,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            playbackSpeed = safePlaybackSpeed,
            reason = reason,
        )
    ).renderSnapshot

    val rendererState = renderer.render(
        input = DanmakuRendererInput.from(
            snapshot = snapshot,
            playbackPositionMs = safePositionMs,
            playbackSpeed = safePlaybackSpeed,
            reason = reason,
            frameTimeNanos = System.nanoTime(),
            hasMask = maskRegionSet?.frame != null,
            maskRegionSet = maskRegionSet,
        )
    )
    if (syncHostState) {
        hostState.applyRendererState(rendererState)
    }
}

private object DanmakuHostCommandStability {
    private var lastSeekSessionId: String = ""
    private var lastSeekSegmentIndex: Long = -1L
    private var lastSeekDispatchAtMs: Long = 0L

    fun shouldDispatchSeek(sessionId: String, positionMs: Long, nowMs: Long): Boolean {
        val segmentIndex = positionMs.coerceAtLeast(0L) / VOD_SEGMENT_DURATION_MS
        val sameBurst = sessionId == lastSeekSessionId &&
            segmentIndex == lastSeekSegmentIndex &&
            nowMs - lastSeekDispatchAtMs < SEEK_DISPATCH_MIN_INTERVAL_MS
        if (sameBurst) return false
        lastSeekSessionId = sessionId
        lastSeekSegmentIndex = segmentIndex
        lastSeekDispatchAtMs = nowMs
        return true
    }
}

private const val REPOPULATE_WINDOW_MS = 30_000L
private const val MIN_PLAYBACK_SPEED = 0.1f
private const val MAX_PLAYBACK_SPEED = 4f
private const val SEEK_DISPATCH_MIN_INTERVAL_MS = 180L
private const val VOD_SEGMENT_DURATION_MS = 6 * 60 * 1000L
