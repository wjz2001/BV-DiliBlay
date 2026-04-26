package dev.aaa1115910.bv.player.danmaku.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.aaa1115910.bv.player.danmaku.config.DanmakuConfig
import dev.aaa1115910.bv.player.danmaku.config.DanmakuConfigApplyAction
import dev.aaa1115910.bv.player.danmaku.config.DanmakuConfigDiff
import dev.aaa1115910.bv.player.danmaku.config.DanmakuConfigDiffer
import dev.aaa1115910.bv.player.danmaku.config.DanmakuConfigSourceMode
import dev.aaa1115910.bv.player.danmaku.config.DanmakuLaneDensity
import dev.aaa1115910.bv.player.danmaku.live.DanmakuLiveBusinessState
import dev.aaa1115910.bv.player.danmaku.live.DanmakuLiveEventType
import dev.aaa1115910.bv.player.danmaku.model.DanmakuRenderFrame
import dev.aaa1115910.bv.player.danmaku.model.DanmakuRenderSnapshot
import dev.aaa1115910.bv.player.danmaku.model.DanmakuSessionEvent
import dev.aaa1115910.bv.player.danmaku.model.DanmakuSessionEventType
import dev.aaa1115910.bv.player.danmaku.model.DanmakuSpeedModel
import dev.aaa1115910.bv.player.danmaku.model.DanmakuTrackType
import dev.aaa1115910.bv.player.danmaku.renderer.ComposeDanmakuRenderer
import dev.aaa1115910.bv.player.danmaku.renderer.DanmakuRendererState
import dev.aaa1115910.bv.player.danmaku.renderer.DanmakuRendererStats
import kotlin.math.max

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
        private set
    @set:kotlin.jvm.JvmName("setBoundState")
    var isBound by mutableStateOf(isBound)
        private set
    var isVisible by mutableStateOf(isVisible)
        private set
    var isPlaying by mutableStateOf(isPlaying)
        private set
    var currentPositionMs by mutableStateOf(currentPositionMs.coerceAtLeast(0L))
        private set
    var playbackSpeed by mutableStateOf(playbackSpeed.coerceAtLeast(0.1f))
        private set
    var lastSnapshotFrameId by mutableStateOf(lastSnapshotFrameId)
        private set
    var lastConfigVersion by mutableStateOf(lastConfigVersion)
        private set
    var maskEnabled by mutableStateOf(maskEnabled)
        private set
    var sourceMode by mutableStateOf("none")
        private set
    var phase by mutableStateOf(if (isBound) DanmakuHostPhase.Bound else DanmakuHostPhase.Idle)
        private set
    var sourceType by mutableStateOf(DanmakuHostSourceType.None)
        private set
    var viewportState by mutableStateOf(DanmakuHostViewportState())
        private set
    var trackSnapshot by mutableStateOf(DanmakuHostTrackSnapshot())
        private set
    var trackActivity by mutableStateOf(DanmakuHostTrackActivity())
        private set
    var trackCapacity by mutableStateOf(DanmakuHostTrackCapacity())
        private set
    var configState by mutableStateOf(DanmakuHostConfigState())
        private set
    var lastConfigApplyAction by mutableStateOf(DanmakuConfigApplyAction.UpdateOnly)
        private set
    var updateOnlyKeys by mutableStateOf<Set<String>>(emptySet())
        private set
    var staticUpdateKeys by mutableStateOf<Set<String>>(emptySet())
        private set
    var repopulateKeys by mutableStateOf<Set<String>>(emptySet())
        private set
    var configUpdateSerial by mutableStateOf(0L)
        private set
    var staticUpdateSerial by mutableStateOf(0L)
        private set
    var repopulateSerial by mutableStateOf(0L)
        private set
    var layoutSerial by mutableStateOf(0L)
        private set
    var sourceSerial by mutableStateOf(0L)
        private set
    var playbackSerial by mutableStateOf(0L)
        private set
    var visibilitySerial by mutableStateOf(0L)
        private set
    var frameSerial by mutableStateOf(0L)
        private set
    var renderSerial by mutableStateOf(0L)
        private set
    var clearSerial by mutableStateOf(0L)
        private set
    var phaseTransitionSerial by mutableStateOf(0L)
        private set
    var stateMachineSerial by mutableStateOf(0L)
        private set
    var activeItemCount by mutableStateOf(0)
        private set
    var pendingUpdateOnly by mutableStateOf(false)
        private set
    var pendingStaticUpdate by mutableStateOf(false)
        private set
    var pendingRepopulate by mutableStateOf(false)
        private set
    var pendingWorkCount by mutableStateOf(0)
        private set
    var primaryAction by mutableStateOf(DanmakuHostInternalAction.None)
        private set
    var lastCoordinationReason by mutableStateOf("init")
        private set
    var lastSessionEventType by mutableStateOf<DanmakuSessionEventType?>(null)
        private set
    var lastSessionEventTimestampMs by mutableStateOf(0L)
        private set
    var liveState by mutableStateOf("idle")
        private set
    var liveBusinessState by mutableStateOf(DanmakuLiveBusinessState())
        private set
    var lastErrorMessage by mutableStateOf<String?>(null)
        private set
    var phaseTransition by mutableStateOf(DanmakuHostPhaseTransition())
        private set
    var coordination by mutableStateOf(DanmakuHostCoordination())
        private set
    var renderStats by mutableStateOf(DanmakuHostRenderStats())
        private set
    var readiness by mutableStateOf(DanmakuHostReadiness())
        private set
    var workPlan by mutableStateOf(DanmakuHostWorkPlan())
        private set
    var stateMachine by mutableStateOf(DanmakuHostStateMachine())
        private set

    val canRender: Boolean
        get() = canRenderForPhase(phase)

    val requiresEngineAdvance: Boolean
        get() = workPlan.requiresEngineAdvance

    val hasPendingConfigWork: Boolean
        get() = pendingUpdateOnly || pendingStaticUpdate || pendingRepopulate

    private var observedConfig: DanmakuConfig? = null
    private val snapshotRenderer = ComposeDanmakuRenderer()

    init {
        publishCoordination(reason = "init")
    }

    fun setBound(bound: Boolean) {
        if (isBound == bound) return
        isBound = bound
        if (bound.not()) {
            isPlaying = false
            resetRenderActivity()
        }
        lastCoordinationReason = if (bound) "host_bound" else "host_unbound"
        refreshPhase()
    }

    fun updateVisibility(visible: Boolean) {
        if (isVisible == visible) return
        isVisible = visible
        visibilitySerial += 1
        lastCoordinationReason = if (visible) "host_visible" else "host_hidden"
        refreshPhase()
    }

    fun updateSource(type: DanmakuHostSourceType, sessionId: String = this.sessionId) {
        val previousLayoutKey = trackSnapshot.layoutKey
        sourceType = type
        sourceMode = type.modeName
        this.sessionId = sessionId
        sourceSerial += 1
        lastErrorMessage = null
        if (type == DanmakuHostSourceType.None) {
            liveState = "idle"
            liveBusinessState = DanmakuLiveBusinessState()
            resetRenderActivity()
        } else {
            refreshTrackSnapshot(observedConfig, reason = "source_changed")
        }
        if (previousLayoutKey != trackSnapshot.layoutKey) layoutSerial += 1
        lastCoordinationReason = "source_${type.modeName}"
        refreshPhase()
    }

    fun updatePlayback(playing: Boolean, positionMs: Long = currentPositionMs, speed: Float = playbackSpeed) {
        isPlaying = playing
        currentPositionMs = positionMs.coerceAtLeast(0L)
        playbackSpeed = speed.coerceAtLeast(0.1f)
        playbackSerial += 1
        lastCoordinationReason = if (playing) "playback_running" else "playback_paused"
        refreshPhase()
    }

    fun updateViewport(width: Int, height: Int, config: DanmakuConfig): DanmakuHostTrackSnapshot {
        val nextViewport = DanmakuHostViewportState(width.coerceAtLeast(1), height.coerceAtLeast(1))
        if (nextViewport != viewportState) layoutSerial += 1
        viewportState = nextViewport
        refreshTrackSnapshot(config, reason = "viewport_changed")
        lastCoordinationReason = "viewport_changed"
        refreshPhase()
        return trackSnapshot
    }

    fun observeConfig(config: DanmakuConfig): DanmakuHostConfigObservation {
        val oldConfig = observedConfig ?: config
        val diff = DanmakuConfigDiffer.diff(oldConfig = oldConfig, newConfig = config)
        observedConfig = config
        lastConfigVersion += 1
        configUpdateSerial += 1
        lastConfigApplyAction = diff.applyAction
        updateOnlyKeys = diff.updateOnlyKeys
        staticUpdateKeys = diff.staticUpdateKeys
        repopulateKeys = diff.repopulateKeys
        pendingUpdateOnly = pendingUpdateOnly || diff.updateOnlyKeys.isNotEmpty()
        pendingStaticUpdate = pendingStaticUpdate || diff.staticUpdateKeys.isNotEmpty()
        pendingRepopulate = pendingRepopulate || diff.repopulateKeys.isNotEmpty()
        if (diff.staticUpdateKeys.isNotEmpty()) staticUpdateSerial += 1
        if (diff.repopulateKeys.isNotEmpty()) repopulateSerial += 1
        configState = DanmakuHostConfigState.from(
            configVersion = lastConfigVersion,
            diff = diff,
            enabled = config.enabled,
            pendingUpdateOnly = pendingUpdateOnly,
            pendingStaticUpdate = pendingStaticUpdate,
            pendingRepopulate = pendingRepopulate,
        )
        refreshTrackSnapshot(config, reason = "config_${diff.applyAction.name}")
        lastCoordinationReason = "config_${diff.applyAction.name}"
        refreshPhase()
        return DanmakuHostConfigObservation(
            configVersion = lastConfigVersion,
            diff = diff,
            applyAction = diff.applyAction,
            configState = configState,
            trackSnapshot = trackSnapshot,
            trackCapacity = trackCapacity,
            coordination = coordination,
            stateMachine = stateMachine,
            workPlan = workPlan,
            needsStyleUpdate = workPlan.needsRendererStyleUpdate,
            needsStaticUpdate = workPlan.needsStaticTrackUpdate,
            needsRepopulate = workPlan.needsRepopulate,
            engineUpdateReason = diff.engineUpdateReason(),
            repopulateReason = diff.repopulateReason(),
        )
    }

    fun markConfigApplied(action: DanmakuConfigApplyAction = lastConfigApplyAction, reason: String) {
        lastConfigApplyAction = action
        when (action) {
            DanmakuConfigApplyAction.UpdateOnly,
            DanmakuConfigApplyAction.RepopulateRequired -> {
                pendingUpdateOnly = false
                pendingStaticUpdate = false
                pendingRepopulate = false
            }
            DanmakuConfigApplyAction.StaticUpdate -> {
                pendingUpdateOnly = false
                pendingStaticUpdate = false
            }
        }
        configState = configState.copy(
            applyAction = action,
            pendingUpdateOnly = pendingUpdateOnly,
            pendingStaticUpdate = pendingStaticUpdate,
            pendingRepopulate = pendingRepopulate,
            applied = pendingUpdateOnly.not() && pendingStaticUpdate.not() && pendingRepopulate.not(),
        )
        lastCoordinationReason = reason
        refreshPhase()
    }

    fun applyRendererState(rendererState: DanmakuRendererState) {
        val frame = rendererState.frame
        val snapshot = rendererState.snapshot
        if (frame != null && snapshot != null) {
            currentPositionMs = frame.playbackPositionMs.coerceAtLeast(0L)
            playbackSpeed = frame.playbackSpeed.coerceAtLeast(0.1f)
            frameSerial += 1
            trackActivity = DanmakuHostTrackActivity.from(snapshot)
            trackSnapshot = trackSnapshot.withActivity(trackActivity, snapshot.frameId)
        } else {
            trackActivity = DanmakuHostTrackActivity()
            trackSnapshot = trackSnapshot.withActivity(trackActivity, 0L)
        }
        lastSnapshotFrameId = rendererState.lastSnapshotFrameId
        activeItemCount = rendererState.activeItemCount
        trackCapacity = resolveTrackCapacity(trackSnapshot)
        maskEnabled = rendererState.maskEnabled
        renderSerial = rendererState.renderSerial
        clearSerial = rendererState.clearSerial
        renderStats = rendererState.renderStats
        lastCoordinationReason = rendererState.reason
        refreshPhase()
    }

    fun applyRenderFrame(frame: DanmakuRenderFrame) {
        applyRendererState(snapshotRenderer.render(frame))
    }

    fun applyRenderSnapshot(
        snapshot: DanmakuRenderSnapshot,
        positionMs: Long,
        speed: Float,
        hasMask: Boolean,
        reason: String = "render",
        frameTimeNanos: Long? = null,
    ) {
        applyRenderFrame(
            DanmakuRenderFrame(
                snapshot = snapshot,
                playbackPositionMs = positionMs,
                playbackSpeed = speed,
                reason = reason,
                frameTimeNanos = frameTimeNanos,
                hasMaskOverride = hasMask,
            )
        )
    }

    fun onRendererCleared(reason: String) {
        applyRendererState(snapshotRenderer.clear(reason))
    }

    fun onSessionEvent(event: DanmakuSessionEvent) {
        sessionId = event.sessionId
        lastSessionEventType = event.type
        lastSessionEventTimestampMs = event.timestampMs
        when (event.type) {
            DanmakuSessionEventType.SessionStarted -> lastErrorMessage = null
            DanmakuSessionEventType.SessionStopped -> {
                isPlaying = false
                liveState = if (sourceType == DanmakuHostSourceType.Live) "disconnected" else liveState
            }
            DanmakuSessionEventType.LiveConnected -> {
                liveState = "connected"
                updateLiveBusinessRoomId(event)
                lastErrorMessage = null
            }
            DanmakuSessionEventType.LiveDisconnected -> {
                liveState = "disconnected"
                updateLiveBusinessRoomId(event)
            }
            DanmakuSessionEventType.LiveStateChanged -> {
                liveState = event.payload["state"] as? String ?: liveState
                updateLiveBusinessRoomId(event)
                lastErrorMessage = event.payload["error"] as? String
            }
            DanmakuSessionEventType.LiveEventReceived -> updateLiveBusinessState(event)
            DanmakuSessionEventType.Error -> {
                lastErrorMessage = event.payload["message"] as? String
                    ?: event.payload["error"] as? String
                    ?: "unknown"
            }
            else -> Unit
        }
        lastCoordinationReason = "session_${event.type.name}"
        refreshPhase()
    }

    private fun resetRenderActivity() {
        activeItemCount = 0
        trackActivity = DanmakuHostTrackActivity()
        trackSnapshot = trackSnapshot.withActivity(trackActivity, lastSnapshotFrameId)
        trackCapacity = resolveTrackCapacity(trackSnapshot)
    }

    private fun refreshTrackSnapshot(config: DanmakuConfig?, reason: String) {
        if (config == null || viewportState.isReady.not()) return
        val previousLayoutKey = trackSnapshot.layoutKey
        val nextSnapshot = DanmakuHostTrackSnapshot.from(
            viewport = viewportState,
            config = config,
            sourceType = sourceType,
        ).withActivity(trackActivity, lastSnapshotFrameId)
        if (nextSnapshot.layoutKey != previousLayoutKey) layoutSerial += 1
        trackSnapshot = nextSnapshot
        trackCapacity = resolveTrackCapacity(nextSnapshot)
        lastCoordinationReason = reason
    }

    private fun updateLiveBusinessRoomId(event: DanmakuSessionEvent) {
        val roomId = (event.payload["roomId"] as? Number)?.toLong() ?: return
        liveBusinessState = liveBusinessState.copy(roomId = roomId)
    }

    private fun updateLiveBusinessState(event: DanmakuSessionEvent) {
        val eventType = (event.payload["eventType"] as? String)
            ?.let { typeName -> runCatching { DanmakuLiveEventType.valueOf(typeName) }.getOrNull() }
        val roomId = (event.payload["roomId"] as? Number)?.toLong() ?: liveBusinessState.roomId
        val eventTimestampMs = (event.payload["eventTimestampMs"] as? Number)?.toLong() ?: event.timestampMs
        val baseState = liveBusinessState.copy(roomId = roomId)
        liveBusinessState = when (eventType) {
            DanmakuLiveEventType.PopularityChange -> baseState.copy(
                lastEventType = eventType,
                lastEventTimestampMs = eventTimestampMs,
                popularity = (event.payload["popularity"] as? Number)?.toInt(),
                popularityText = event.payload["popularityText"] as? String,
            )
            DanmakuLiveEventType.OnlineRankCount -> baseState.copy(
                lastEventType = eventType,
                lastEventTimestampMs = eventTimestampMs,
                onlineRankCount = (event.payload["onlineRankCount"] as? Number)?.toInt(),
            )
            DanmakuLiveEventType.Unknown -> baseState.copy(
                lastEventType = eventType,
                lastEventTimestampMs = eventTimestampMs,
                unknownEventCount = baseState.unknownEventCount + 1,
            )
            else -> baseState
        }
    }

    private fun refreshPhase() {
        publishCoordination(reason = lastCoordinationReason)
    }

    private fun publishCoordination(reason: String) {
        val previousPhase = phase
        val nextPhase = resolvePhase()
        val nextTransition = if (previousPhase != nextPhase) {
            phaseTransitionSerial += 1
            DanmakuHostPhaseTransition(previousPhase, nextPhase, phaseTransitionSerial, reason)
        } else {
            phaseTransition.copy(reason = reason)
        }
        val nextTrackCapacity = resolveTrackCapacity(trackSnapshot)
        val nextReadiness = resolveReadiness(nextPhase, nextTrackCapacity)
        val nextWorkPlan = resolveWorkPlan(nextPhase, nextReadiness, nextTrackCapacity, reason)
        val nextPrimaryAction = resolvePrimaryAction(nextWorkPlan)
        val nextPendingWorkCount = nextWorkPlan.types.size
        stateMachineSerial += 1
        val nextStateMachine = DanmakuHostStateMachine(
            phase = nextPhase,
            sourceType = sourceType,
            sourceMode = sourceMode,
            sessionId = sessionId,
            readiness = nextReadiness,
            workPlan = nextWorkPlan,
            viewportState = viewportState,
            trackSnapshot = trackSnapshot,
            trackActivity = trackActivity,
            trackCapacity = nextTrackCapacity,
            activeItemCount = activeItemCount,
            configVersion = lastConfigVersion,
            configState = configState,
            configApplyAction = lastConfigApplyAction,
            updateOnlyKeys = updateOnlyKeys,
            staticUpdateKeys = staticUpdateKeys,
            repopulateKeys = repopulateKeys,
            pendingUpdateOnly = pendingUpdateOnly,
            pendingStaticUpdate = pendingStaticUpdate,
            pendingRepopulate = pendingRepopulate,
            primaryAction = nextPrimaryAction,
            pendingWorkCount = nextPendingWorkCount,
            renderStats = renderStats,
            transition = nextTransition,
            serials = DanmakuHostSerials(
                configUpdateSerial = configUpdateSerial,
                staticUpdateSerial = staticUpdateSerial,
                repopulateSerial = repopulateSerial,
                layoutSerial = layoutSerial,
                sourceSerial = sourceSerial,
                playbackSerial = playbackSerial,
                visibilitySerial = visibilitySerial,
                frameSerial = frameSerial,
                renderSerial = renderSerial,
                clearSerial = clearSerial,
                phaseTransitionSerial = phaseTransitionSerial,
                stateMachineSerial = stateMachineSerial,
            ),
            reason = reason,
        )
        phase = nextPhase
        phaseTransition = nextTransition
        trackCapacity = nextTrackCapacity
        readiness = nextReadiness
        workPlan = nextWorkPlan
        primaryAction = nextPrimaryAction
        pendingWorkCount = nextPendingWorkCount
        stateMachine = nextStateMachine
        coordination = DanmakuHostCoordination(
            phase = nextPhase,
            sourceType = sourceType,
            sourceMode = sourceMode,
            sessionId = sessionId,
            configVersion = lastConfigVersion,
            configState = configState,
            applyAction = lastConfigApplyAction,
            viewportState = viewportState,
            trackSnapshot = trackSnapshot,
            trackCapacity = nextTrackCapacity,
            activeItemCount = activeItemCount,
            pendingUpdateOnly = pendingUpdateOnly,
            pendingStaticUpdate = pendingStaticUpdate,
            pendingRepopulate = pendingRepopulate,
            canRender = canRenderForPhase(nextPhase),
            requiresEngineAdvance = nextWorkPlan.requiresEngineAdvance,
            primaryAction = nextPrimaryAction,
            pendingWorkCount = nextPendingWorkCount,
            layoutSerial = layoutSerial,
            sourceSerial = sourceSerial,
            playbackSerial = playbackSerial,
            renderSerial = renderSerial,
            clearSerial = clearSerial,
            renderStats = renderStats,
            readiness = nextReadiness,
            workPlan = nextWorkPlan,
            stateMachine = nextStateMachine,
            transition = nextTransition,
            reason = reason,
        )
    }

    private fun resolveReadiness(nextPhase: DanmakuHostPhase, nextTrackCapacity: DanmakuHostTrackCapacity): DanmakuHostReadiness {
        return DanmakuHostReadiness(
            bound = isBound,
            visible = isVisible,
            sourceReady = sourceType != DanmakuHostSourceType.None,
            viewportReady = viewportState.isReady,
            trackReady = trackSnapshot.hasReadyTrack,
            trackHasCapacity = nextTrackCapacity.hasCapacity,
            configObserved = lastConfigVersion > 0,
            configEnabled = configState.enabled,
            rendererReady = renderStats.renderedFrameCount > 0L || lastSnapshotFrameId > 0L,
            errorFree = lastErrorMessage == null,
            canRender = canRenderForPhase(nextPhase),
        )
    }

    private fun resolveWorkPlan(
        nextPhase: DanmakuHostPhase,
        nextReadiness: DanmakuHostReadiness,
        nextTrackCapacity: DanmakuHostTrackCapacity,
        reason: String,
    ): DanmakuHostWorkPlan {
        val workTypes = linkedSetOf<DanmakuHostWorkType>()
        if (nextReadiness.configObserved.not()) workTypes += DanmakuHostWorkType.ObserveConfig
        if (nextReadiness.viewportReady && trackSnapshot.totalLaneCount > 0) workTypes += DanmakuHostWorkType.SyncTrackState
        when {
            pendingRepopulate -> workTypes += DanmakuHostWorkType.RepopulateTracks
            pendingStaticUpdate -> workTypes += DanmakuHostWorkType.UpdateTrackStaticProperties
            pendingUpdateOnly -> workTypes += DanmakuHostWorkType.UpdateRendererStyle
        }
        if (canRenderForPhase(nextPhase) && nextReadiness.viewportReady && nextReadiness.trackReady) {
            workTypes += DanmakuHostWorkType.AdvanceEngine
            if (nextTrackCapacity.hasCapacity || activeItemCount > 0) workTypes += DanmakuHostWorkType.RenderFrame
        }
        if (shouldClearRendererFor(nextPhase)) workTypes += DanmakuHostWorkType.ClearRenderer
        return DanmakuHostWorkPlan(types = workTypes, reason = reason, phase = nextPhase)
    }

    private fun resolvePrimaryAction(plan: DanmakuHostWorkPlan): DanmakuHostInternalAction {
        return when {
            plan.shouldClearRenderer -> DanmakuHostInternalAction.ClearRenderer
            plan.needsRepopulate -> DanmakuHostInternalAction.Repopulate
            plan.needsStaticTrackUpdate -> DanmakuHostInternalAction.ApplyStaticUpdate
            plan.needsRendererStyleUpdate -> DanmakuHostInternalAction.ApplyUpdateOnly
            plan.requiresRenderFrame -> DanmakuHostInternalAction.AdvanceAndRender
            plan.requiresEngineAdvance -> DanmakuHostInternalAction.AdvanceOnly
            plan.needsTrackStateSync -> DanmakuHostInternalAction.SyncTracks
            plan.needsConfigObservation -> DanmakuHostInternalAction.ObserveConfig
            else -> DanmakuHostInternalAction.None
        }
    }

    private fun resolveTrackCapacity(snapshot: DanmakuHostTrackSnapshot): DanmakuHostTrackCapacity = DanmakuHostTrackCapacity.from(snapshot)

    private fun shouldClearRendererFor(value: DanmakuHostPhase): Boolean {
        if (activeItemCount <= 0) return false
        return value == DanmakuHostPhase.Idle ||
            value == DanmakuHostPhase.Hidden ||
            value == DanmakuHostPhase.Disabled ||
            value == DanmakuHostPhase.Error ||
            sourceType == DanmakuHostSourceType.None
    }

    private fun canRenderForPhase(value: DanmakuHostPhase): Boolean {
        return isBound && isVisible && configState.enabled && viewportState.isReady && trackSnapshot.hasReadyTrack &&
            (value == DanmakuHostPhase.Running || value == DanmakuHostPhase.Paused)
    }

    private fun resolvePhase(): DanmakuHostPhase {
        return when {
            isBound.not() -> DanmakuHostPhase.Idle
            lastErrorMessage != null -> DanmakuHostPhase.Error
            isVisible.not() -> DanmakuHostPhase.Hidden
            configState.enabled.not() -> DanmakuHostPhase.Disabled
            sourceType == DanmakuHostSourceType.None -> DanmakuHostPhase.Bound
            viewportState.isReady.not() -> DanmakuHostPhase.Preparing
            trackSnapshot.hasReadyTrack.not() -> DanmakuHostPhase.Preparing
            isPlaying -> DanmakuHostPhase.Running
            else -> DanmakuHostPhase.Paused
        }
    }
}

enum class DanmakuHostPhase { Idle, Bound, Preparing, Running, Paused, Hidden, Disabled, Error }

enum class DanmakuHostSourceType(val modeName: String) { None("none"), Vod("vod"), Live("live") }

enum class DanmakuHostWorkType {
    ObserveConfig,
    SyncTrackState,
    UpdateRendererStyle,
    UpdateTrackStaticProperties,
    RepopulateTracks,
    AdvanceEngine,
    RenderFrame,
    ClearRenderer,
}

enum class DanmakuHostInternalAction {
    None,
    ObserveConfig,
    SyncTracks,
    ApplyUpdateOnly,
    ApplyStaticUpdate,
    Repopulate,
    AdvanceOnly,
    AdvanceAndRender,
    ClearRenderer,
}

data class DanmakuHostReadiness(
    val bound: Boolean = false,
    val visible: Boolean = true,
    val sourceReady: Boolean = false,
    val viewportReady: Boolean = false,
    val trackReady: Boolean = false,
    val trackHasCapacity: Boolean = false,
    val configObserved: Boolean = false,
    val configEnabled: Boolean = true,
    val rendererReady: Boolean = false,
    val errorFree: Boolean = true,
    val canRender: Boolean = false,
) {
    val hostReady: Boolean get() = bound && visible && configEnabled && errorFree
    val renderReady: Boolean get() = hostReady && sourceReady && viewportReady && trackReady
}

data class DanmakuHostWorkPlan(
    val types: Set<DanmakuHostWorkType> = emptySet(),
    val reason: String = "init",
    val phase: DanmakuHostPhase = DanmakuHostPhase.Idle,
) {
    val needsConfigObservation: Boolean get() = DanmakuHostWorkType.ObserveConfig in types
    val needsTrackStateSync: Boolean get() = DanmakuHostWorkType.SyncTrackState in types
    val needsRendererStyleUpdate: Boolean get() = DanmakuHostWorkType.UpdateRendererStyle in types
    val needsStaticTrackUpdate: Boolean get() = DanmakuHostWorkType.UpdateTrackStaticProperties in types
    val needsRepopulate: Boolean get() = DanmakuHostWorkType.RepopulateTracks in types
    val requiresEngineAdvance: Boolean get() = DanmakuHostWorkType.AdvanceEngine in types
    val requiresRenderFrame: Boolean get() = DanmakuHostWorkType.RenderFrame in types
    val shouldClearRenderer: Boolean get() = DanmakuHostWorkType.ClearRenderer in types
    val pendingWorkCount: Int get() = types.size
}

data class DanmakuHostPhaseTransition(
    val from: DanmakuHostPhase = DanmakuHostPhase.Idle,
    val to: DanmakuHostPhase = DanmakuHostPhase.Idle,
    val serial: Long = 0L,
    val reason: String = "init",
) { val changed: Boolean get() = from != to }

data class DanmakuHostSerials(
    val configUpdateSerial: Long = 0L,
    val staticUpdateSerial: Long = 0L,
    val repopulateSerial: Long = 0L,
    val layoutSerial: Long = 0L,
    val sourceSerial: Long = 0L,
    val playbackSerial: Long = 0L,
    val visibilitySerial: Long = 0L,
    val frameSerial: Long = 0L,
    val renderSerial: Long = 0L,
    val clearSerial: Long = 0L,
    val phaseTransitionSerial: Long = 0L,
    val stateMachineSerial: Long = 0L,
)

data class DanmakuHostConfigState(
    val configVersion: Int = 0,
    val enabled: Boolean = true,
    val applyAction: DanmakuConfigApplyAction = DanmakuConfigApplyAction.UpdateOnly,
    val updateOnlyKeys: Set<String> = emptySet(),
    val staticUpdateKeys: Set<String> = emptySet(),
    val repopulateKeys: Set<String> = emptySet(),
    val affectsLayout: Boolean = false,
    val pendingUpdateOnly: Boolean = false,
    val pendingStaticUpdate: Boolean = false,
    val pendingRepopulate: Boolean = false,
    val applied: Boolean = true,
) {
    val hasPendingWork: Boolean get() = pendingUpdateOnly || pendingStaticUpdate || pendingRepopulate

    companion object {
        fun from(
            configVersion: Int,
            diff: DanmakuConfigDiff,
            enabled: Boolean,
            pendingUpdateOnly: Boolean,
            pendingStaticUpdate: Boolean,
            pendingRepopulate: Boolean,
        ): DanmakuHostConfigState {
            return DanmakuHostConfigState(
                configVersion = configVersion,
                enabled = enabled,
                applyAction = diff.applyAction,
                updateOnlyKeys = diff.updateOnlyKeys,
                staticUpdateKeys = diff.staticUpdateKeys,
                repopulateKeys = diff.repopulateKeys,
                affectsLayout = diff.affectsLayout,
                pendingUpdateOnly = pendingUpdateOnly,
                pendingStaticUpdate = pendingStaticUpdate,
                pendingRepopulate = pendingRepopulate,
                applied = pendingUpdateOnly.not() && pendingStaticUpdate.not() && pendingRepopulate.not(),
            )
        }
    }
}

data class DanmakuHostStateMachine(
    val phase: DanmakuHostPhase = DanmakuHostPhase.Idle,
    val sourceType: DanmakuHostSourceType = DanmakuHostSourceType.None,
    val sourceMode: String = DanmakuHostSourceType.None.modeName,
    val sessionId: String = "",
    val readiness: DanmakuHostReadiness = DanmakuHostReadiness(),
    val workPlan: DanmakuHostWorkPlan = DanmakuHostWorkPlan(),
    val viewportState: DanmakuHostViewportState = DanmakuHostViewportState(),
    val trackSnapshot: DanmakuHostTrackSnapshot = DanmakuHostTrackSnapshot(),
    val trackActivity: DanmakuHostTrackActivity = DanmakuHostTrackActivity(),
    val trackCapacity: DanmakuHostTrackCapacity = DanmakuHostTrackCapacity(),
    val activeItemCount: Int = 0,
    val configVersion: Int = 0,
    val configState: DanmakuHostConfigState = DanmakuHostConfigState(),
    val configApplyAction: DanmakuConfigApplyAction = DanmakuConfigApplyAction.UpdateOnly,
    val updateOnlyKeys: Set<String> = emptySet(),
    val staticUpdateKeys: Set<String> = emptySet(),
    val repopulateKeys: Set<String> = emptySet(),
    val pendingUpdateOnly: Boolean = false,
    val pendingStaticUpdate: Boolean = false,
    val pendingRepopulate: Boolean = false,
    val primaryAction: DanmakuHostInternalAction = DanmakuHostInternalAction.None,
    val pendingWorkCount: Int = 0,
    val renderStats: DanmakuHostRenderStats = DanmakuHostRenderStats(),
    val transition: DanmakuHostPhaseTransition = DanmakuHostPhaseTransition(),
    val serials: DanmakuHostSerials = DanmakuHostSerials(),
    val reason: String = "init",
) {
    val hasPendingConfigWork: Boolean get() = pendingUpdateOnly || pendingStaticUpdate || pendingRepopulate
    val isRenderLoopEligible: Boolean get() = readiness.canRender && workPlan.requiresEngineAdvance
}

data class DanmakuHostCoordination(
    val phase: DanmakuHostPhase = DanmakuHostPhase.Idle,
    val sourceType: DanmakuHostSourceType = DanmakuHostSourceType.None,
    val sourceMode: String = DanmakuHostSourceType.None.modeName,
    val sessionId: String = "",
    val configVersion: Int = 0,
    val configState: DanmakuHostConfigState = DanmakuHostConfigState(),
    val applyAction: DanmakuConfigApplyAction = DanmakuConfigApplyAction.UpdateOnly,
    val viewportState: DanmakuHostViewportState = DanmakuHostViewportState(),
    val trackSnapshot: DanmakuHostTrackSnapshot = DanmakuHostTrackSnapshot(),
    val trackCapacity: DanmakuHostTrackCapacity = DanmakuHostTrackCapacity(),
    val activeItemCount: Int = 0,
    val pendingUpdateOnly: Boolean = false,
    val pendingStaticUpdate: Boolean = false,
    val pendingRepopulate: Boolean = false,
    val canRender: Boolean = false,
    val requiresEngineAdvance: Boolean = false,
    val primaryAction: DanmakuHostInternalAction = DanmakuHostInternalAction.None,
    val pendingWorkCount: Int = 0,
    val layoutSerial: Long = 0L,
    val sourceSerial: Long = 0L,
    val playbackSerial: Long = 0L,
    val renderSerial: Long = 0L,
    val clearSerial: Long = 0L,
    val renderStats: DanmakuHostRenderStats = DanmakuHostRenderStats(),
    val readiness: DanmakuHostReadiness = DanmakuHostReadiness(),
    val workPlan: DanmakuHostWorkPlan = DanmakuHostWorkPlan(),
    val stateMachine: DanmakuHostStateMachine = DanmakuHostStateMachine(),
    val transition: DanmakuHostPhaseTransition = DanmakuHostPhaseTransition(),
    val reason: String = "init",
) { val hasPendingConfigWork: Boolean get() = pendingUpdateOnly || pendingStaticUpdate || pendingRepopulate }

typealias DanmakuHostRenderStats = DanmakuRendererStats

enum class DanmakuHostTrackKind { Scroll, Top, Bottom }

data class DanmakuHostViewportState(val widthPx: Int = 0, val heightPx: Int = 0) {
    val isReady: Boolean get() = widthPx > 0 && heightPx > 0
}

data class DanmakuHostTrackActivity(
    val scrollActiveCount: Int = 0,
    val topActiveCount: Int = 0,
    val bottomActiveCount: Int = 0,
) {
    val totalActiveCount: Int get() = scrollActiveCount + topActiveCount + bottomActiveCount

    companion object {
        fun from(snapshot: DanmakuRenderSnapshot): DanmakuHostTrackActivity {
            var scroll = 0
            var top = 0
            var bottom = 0
            snapshot.items.forEach { itemRef ->
                when (itemRef.item.trackType) {
                    DanmakuTrackType.Scroll -> scroll += 1
                    DanmakuTrackType.Top -> top += 1
                    DanmakuTrackType.Bottom -> bottom += 1
                }
            }
            return DanmakuHostTrackActivity(scroll, top, bottom)
        }
    }
}

data class DanmakuHostTrackCapacity(
    val scrollCapacity: Int = 0,
    val topCapacity: Int = 0,
    val bottomCapacity: Int = 0,
    val scrollAvailable: Int = 0,
    val topAvailable: Int = 0,
    val bottomAvailable: Int = 0,
) {
    val totalCapacity: Int get() = scrollCapacity + topCapacity + bottomCapacity
    val totalAvailable: Int get() = scrollAvailable + topAvailable + bottomAvailable
    val hasCapacity: Boolean get() = totalAvailable > 0
    val saturationRatio: Float get() = if (totalCapacity <= 0) 0f else (totalCapacity - totalAvailable).toFloat() / totalCapacity.toFloat()

    companion object {
        fun from(snapshot: DanmakuHostTrackSnapshot): DanmakuHostTrackCapacity {
            return DanmakuHostTrackCapacity(
                scrollCapacity = snapshot.scroll.capacity,
                topCapacity = snapshot.top.capacity,
                bottomCapacity = snapshot.bottom.capacity,
                scrollAvailable = snapshot.scroll.availableCapacity,
                topAvailable = snapshot.top.availableCapacity,
                bottomAvailable = snapshot.bottom.availableCapacity,
            )
        }
    }
}

data class DanmakuHostTrackState(
    val kind: DanmakuHostTrackKind,
    val enabled: Boolean,
    val laneCount: Int,
    val laneHeightPx: Float,
    val usableHeightPx: Int,
    val activeCount: Int = 0,
    val lastFrameId: Long = 0L,
    val startYPx: Float = 0f,
    val endYPx: Float = 0f,
    val fromBottom: Boolean = false,
    val safeSeparationPx: Float = 0f,
    val durationMs: Int = 0,
    val speedLevel: Int = 3,
    val durationMultiplier: Float = 1f,
    val minLevel: Int = 0,
) {
    val isReady: Boolean get() = enabled && laneCount > 0 && usableHeightPx > 0 && endYPx > startYPx
    val capacity: Int get() = if (isReady) laneCount else 0
    val availableCapacity: Int get() = (capacity - activeCount).coerceAtLeast(0)
    val hasCapacity: Boolean get() = availableCapacity > 0
    val occupancyRatio: Float get() = if (laneCount <= 0) 0f else activeCount.toFloat() / laneCount.toFloat()
}

data class DanmakuHostTrackSnapshot(
    val scroll: DanmakuHostTrackState = DanmakuHostTrackState(DanmakuHostTrackKind.Scroll, true, 0, 0f, 0),
    val top: DanmakuHostTrackState = DanmakuHostTrackState(DanmakuHostTrackKind.Top, true, 0, 0f, 0),
    val bottom: DanmakuHostTrackState = DanmakuHostTrackState(DanmakuHostTrackKind.Bottom, true, 0, 0f, 0),
) {
    val totalLaneCount: Int get() = scroll.laneCount + top.laneCount + bottom.laneCount
    val activeCount: Int get() = scroll.activeCount + top.activeCount + bottom.activeCount
    val totalAvailableCapacity: Int get() = scroll.availableCapacity + top.availableCapacity + bottom.availableCapacity
    val enabledTrackCount: Int get() = listOf(scroll, top, bottom).count { it.enabled }
    val readyTrackCount: Int get() = listOf(scroll, top, bottom).count { it.isReady }
    val hasReadyTrack: Boolean get() = readyTrackCount > 0
    val hasCapacity: Boolean get() = scroll.hasCapacity || top.hasCapacity || bottom.hasCapacity
    val layoutKey: String
        get() = listOf(
            scroll.enabled,
            scroll.laneCount,
            scroll.laneHeightPx,
            scroll.usableHeightPx,
            top.enabled,
            top.laneCount,
            top.laneHeightPx,
            top.usableHeightPx,
            bottom.enabled,
            bottom.laneCount,
            bottom.laneHeightPx,
            bottom.usableHeightPx,
            scroll.startYPx,
            scroll.endYPx,
            top.startYPx,
            top.endYPx,
            bottom.startYPx,
            bottom.endYPx,
            bottom.fromBottom,
            scroll.safeSeparationPx,
            scroll.durationMs,
            scroll.speedLevel,
            scroll.durationMultiplier,
            scroll.minLevel,
            top.minLevel,
            bottom.minLevel,
        ).joinToString(separator = "|")

    fun withActivity(activity: DanmakuHostTrackActivity, frameId: Long): DanmakuHostTrackSnapshot {
        return copy(
            scroll = scroll.copy(activeCount = activity.scrollActiveCount, lastFrameId = frameId),
            top = top.copy(activeCount = activity.topActiveCount, lastFrameId = frameId),
            bottom = bottom.copy(activeCount = activity.bottomActiveCount, lastFrameId = frameId),
        )
    }

    companion object {
        fun from(
            viewport: DanmakuHostViewportState,
            config: DanmakuConfig,
            sourceType: DanmakuHostSourceType = DanmakuHostSourceType.None,
        ): DanmakuHostTrackSnapshot {
            val usableHeight = displayAreaHeight(viewport.heightPx, config.area)
            val textHeight = estimateTextHeightPx(config)
            val laneHeight = (textHeight * config.laneDensity.factor() + LANE_GAP_PX).coerceAtLeast(18f)
            val laneCount = laneCountFor(usableHeight, laneHeight)
            val fixedDurationMs = fixedDurationMs(config)
            val safeSeparationPx = config.safeSeparation.coerceAtLeast(0f)
            val displayAreaStartY = 0f
            val displayAreaEndY = usableHeight.toFloat()
            val minLevel = config.minLevelFor(sourceType.toConfigSourceMode())
            return DanmakuHostTrackSnapshot(
                scroll = DanmakuHostTrackState(
                    kind = DanmakuHostTrackKind.Scroll,
                    enabled = config.enabled && config.allowScroll,
                    laneCount = if (config.enabled && config.allowScroll) laneCount else 0,
                    laneHeightPx = laneHeight,
                    usableHeightPx = usableHeight,
                    startYPx = displayAreaStartY,
                    endYPx = displayAreaEndY,
                    safeSeparationPx = safeSeparationPx,
                    durationMs = DanmakuSpeedModel.BASE_ROLLING_DURATION_MS,
                    speedLevel = config.speedLevel,
                    durationMultiplier = config.durationMultiplier,
                    minLevel = minLevel,
                ),
                top = DanmakuHostTrackState(
                    kind = DanmakuHostTrackKind.Top,
                    enabled = config.enabled && config.allowTop,
                    laneCount = if (config.enabled && config.allowTop) laneCount else 0,
                    laneHeightPx = laneHeight,
                    usableHeightPx = usableHeight,
                    startYPx = displayAreaStartY,
                    endYPx = displayAreaEndY,
                    safeSeparationPx = safeSeparationPx,
                    durationMs = fixedDurationMs,
                    speedLevel = config.speedLevel,
                    durationMultiplier = config.durationMultiplier,
                    minLevel = minLevel,
                ),
                bottom = DanmakuHostTrackState(
                    kind = DanmakuHostTrackKind.Bottom,
                    enabled = config.enabled && config.allowBottom,
                    laneCount = if (config.enabled && config.allowBottom) laneCount else 0,
                    laneHeightPx = laneHeight,
                    usableHeightPx = usableHeight,
                    startYPx = displayAreaStartY,
                    endYPx = displayAreaEndY,
                    fromBottom = true,
                    safeSeparationPx = safeSeparationPx,
                    durationMs = fixedDurationMs,
                    speedLevel = config.speedLevel,
                    durationMultiplier = config.durationMultiplier,
                    minLevel = minLevel,
                ),
            )
        }

        private fun displayAreaHeight(viewportHeightPx: Int, area: Float): Int {
            val safeHeight = viewportHeightPx.coerceAtLeast(1)
            return (safeHeight * area.coerceIn(0f, 1f)).toInt().coerceAtLeast(1)
        }

        private fun laneCountFor(usableHeight: Int, laneHeight: Float): Int = max(1, (usableHeight / laneHeight.coerceAtLeast(1f)).toInt())

        private fun estimateTextHeightPx(config: DanmakuConfig): Float {
            val scale = config.textSizeScale.coerceIn(25, 400) / 100f
            val canvasPadding = config.textPaddingPx.coerceAtLeast(0)
            val textSizePx = config.textSizeSp.coerceAtLeast(MIN_TEXT_SIZE_SP) * DEFAULT_DENSITY * scale
            return (textSizePx * TEXT_HEIGHT_FACTOR).coerceAtLeast(16f) + canvasPadding
        }

        private fun fixedDurationMs(config: DanmakuConfig): Int {
            return (FIXED_TRACK_DURATION_MS * config.durationMultiplier.coerceIn(0.2f, 5f))
                .toInt()
                .coerceIn(DanmakuSpeedModel.MIN_ROLLING_DURATION_MS, DanmakuSpeedModel.MAX_ROLLING_DURATION_MS)
        }

        private fun DanmakuLaneDensity.factor(): Float {
            return when (this) {
                DanmakuLaneDensity.Sparse -> 1.25f
                DanmakuLaneDensity.Standard -> 1f
                DanmakuLaneDensity.Dense -> 0.85f
            }
        }

        private fun DanmakuHostSourceType.toConfigSourceMode(): DanmakuConfigSourceMode {
            return when (this) {
                DanmakuHostSourceType.Live -> DanmakuConfigSourceMode.Live
                DanmakuHostSourceType.None,
                DanmakuHostSourceType.Vod -> DanmakuConfigSourceMode.Vod
            }
        }

        private const val FIXED_TRACK_DURATION_MS = 4_000
        private const val DEFAULT_DENSITY = 1f
        private const val TEXT_HEIGHT_FACTOR = 1.08f
        private const val LANE_GAP_PX = 3f
        private const val MIN_TEXT_SIZE_SP = 1f
    }
}

data class DanmakuHostConfigObservation(
    val configVersion: Int,
    val diff: DanmakuConfigDiff,
    val applyAction: DanmakuConfigApplyAction = diff.applyAction,
    val configState: DanmakuHostConfigState = DanmakuHostConfigState(),
    val trackSnapshot: DanmakuHostTrackSnapshot? = null,
    val trackCapacity: DanmakuHostTrackCapacity = DanmakuHostTrackCapacity(),
    val coordination: DanmakuHostCoordination = DanmakuHostCoordination(),
    val stateMachine: DanmakuHostStateMachine = DanmakuHostStateMachine(),
    val workPlan: DanmakuHostWorkPlan = DanmakuHostWorkPlan(),
    val needsStyleUpdate: Boolean = false,
    val needsStaticUpdate: Boolean = false,
    val needsRepopulate: Boolean = false,
    val engineUpdateReason: String = "host_config_changed",
    val repopulateReason: String = "host_config_changed",
)

private fun DanmakuConfigDiff.engineUpdateReason(): String {
    return when (applyAction) {
        DanmakuConfigApplyAction.UpdateOnly -> "host_config_update_only"
        DanmakuConfigApplyAction.StaticUpdate -> "host_config_static_update"
        DanmakuConfigApplyAction.RepopulateRequired -> "host_config_repopulate"
    }
}

private fun DanmakuConfigDiff.repopulateReason(): String {
    return if (applyAction == DanmakuConfigApplyAction.RepopulateRequired) {
        "host_config_repopulate"
    } else {
        engineUpdateReason()
    }
}
