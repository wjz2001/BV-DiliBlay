package dev.aaa1115910.bv.player.danmaku.session

import dev.aaa1115910.bv.player.danmaku.model.TimelineWindow
import kotlin.math.abs

/**
 * Playback-time policy adapted from animeko's TimeBasedDanmakuSession.
 *
 * This algorithm owns only playback time semantics:
 * - tick: normal clock-driven playback progress.
 * - advance: caller-driven playback progress with the same repopulate policy as tick.
 * - seek: user/player seek, always repopulates around the target time and increments seek serial.
 * - refresh: force reload from a position while preserving the current refresh pipeline.
 * - repopulate: manual/layout repopulation without changing source loading state.
 */
internal interface SessionAlgorithm {
    fun reset(positionMs: Long = 0L)

    fun requestRepopulate()

    fun advance(
        positionMs: Long,
        playbackSpeed: Float,
        reason: String,
    ): DanmakuSessionStep

    fun tick(
        positionMs: Long,
        playbackSpeed: Float,
        reason: String,
    ): DanmakuSessionStep

    fun seek(
        positionMs: Long,
        fromPositionMs: Long?,
        playbackSpeed: Float,
        reason: String,
    ): DanmakuSessionStep

    fun refresh(
        positionMs: Long,
        playbackSpeed: Float,
        reason: String,
        windowMs: Long? = null,
    ): DanmakuSessionStep

    fun repopulate(
        positionMs: Long,
        playbackSpeed: Float,
        reason: String,
        windowMs: Long? = null,
    ): DanmakuSessionStep
}

internal class DanmakuSessionAlgorithm(
    repopulateThresholdMs: Long = DEFAULT_REPOPULATE_THRESHOLD_MS,
    repopulateDistanceMs: Long = DEFAULT_REPOPULATE_DISTANCE_MS,
) : SessionAlgorithm {
    private val timeline = SessionTimelineState()
    private val repopulatePolicy = SessionRepopulatePolicy(
        thresholdMs = repopulateThresholdMs,
        distanceMs = repopulateDistanceMs,
    )

    override fun reset(positionMs: Long) {
        timeline.reset(positionMs)
    }

    override fun requestRepopulate() {
        timeline.requestRepopulate()
    }

    override fun advance(
        positionMs: Long,
        playbackSpeed: Float,
        reason: String,
    ): DanmakuSessionStep {
        return evaluate(
            SessionAlgorithmCommand.Advance(
                positionMs = positionMs,
                playbackSpeed = playbackSpeed,
                reason = reason,
            )
        )
    }

    override fun tick(
        positionMs: Long,
        playbackSpeed: Float,
        reason: String,
    ): DanmakuSessionStep {
        return evaluate(
            SessionAlgorithmCommand.Tick(
                positionMs = positionMs,
                playbackSpeed = playbackSpeed,
                reason = reason,
            )
        )
    }

    override fun seek(
        positionMs: Long,
        fromPositionMs: Long?,
        playbackSpeed: Float,
        reason: String,
    ): DanmakuSessionStep {
        return evaluate(
            SessionAlgorithmCommand.Seek(
                positionMs = positionMs,
                playbackSpeed = playbackSpeed,
                reason = reason,
                previousPositionMs = fromPositionMs,
            )
        )
    }

    override fun refresh(
        positionMs: Long,
        playbackSpeed: Float,
        reason: String,
        windowMs: Long?,
    ): DanmakuSessionStep {
        return evaluate(
            SessionAlgorithmCommand.Refresh(
                positionMs = positionMs,
                playbackSpeed = playbackSpeed,
                reason = reason,
                windowMs = windowMs,
            )
        )
    }

    override fun repopulate(
        positionMs: Long,
        playbackSpeed: Float,
        reason: String,
        windowMs: Long?,
    ): DanmakuSessionStep {
        return evaluate(
            SessionAlgorithmCommand.Repopulate(
                positionMs = positionMs,
                playbackSpeed = playbackSpeed,
                reason = reason,
                windowMs = windowMs,
            )
        )
    }

    private fun evaluate(command: SessionAlgorithmCommand): DanmakuSessionStep {
        val frame = timeline.advanceTo(command.toTimelineRequest())
        val repopulate = repopulatePolicy.evaluate(command = command, frame = frame)
        return DanmakuSessionStep(
            command = command.type,
            positionMs = frame.positionMs,
            initialPositionMs = frame.initialPositionMs,
            previousPositionMs = frame.previousPositionMs,
            playbackSpeed = frame.playbackSpeed,
            deltaMs = frame.deltaMs,
            motion = frame.motion,
            action = if (repopulate == null) DanmakuSessionAction.Advance else DanmakuSessionAction.Repopulate,
            repopulateWindow = repopulate?.window,
            repopulateTrigger = repopulate?.trigger,
            repopulateReason = repopulate?.reason ?: command.reason,
            seekSerial = frame.seekSerial,
            reason = command.reason,
        )
    }

    private companion object {
        const val DEFAULT_REPOPULATE_THRESHOLD_MS = 3_000L
        const val DEFAULT_REPOPULATE_DISTANCE_MS = 20_000L
    }
}

internal enum class DanmakuSessionCommandType {
    Tick,
    Advance,
    Seek,
    Refresh,
    Repopulate,
}

internal enum class DanmakuSessionAction {
    Advance,
    Repopulate,
}

internal enum class DanmakuSessionMotion {
    Initial,
    Same,
    Forward,
    Backward,
}

internal enum class DanmakuSessionRepopulateTrigger {
    Initial,
    Manual,
    Seek,
    Refresh,
    TimeJump,
    ;

    val reason: String
        get() = when (this) {
            Initial -> "initial_repopulate"
            Manual -> "requested_repopulate"
            Seek -> "seek"
            Refresh -> "refresh_from_position"
            TimeJump -> "time_jump"
        }
}

internal data class DanmakuSessionStep(
    val command: DanmakuSessionCommandType,
    val positionMs: Long,
    val initialPositionMs: Long,
    val previousPositionMs: Long?,
    val playbackSpeed: Float,
    val deltaMs: Long?,
    val motion: DanmakuSessionMotion,
    val action: DanmakuSessionAction,
    val repopulateWindow: TimelineWindow?,
    val repopulateTrigger: DanmakuSessionRepopulateTrigger?,
    val repopulateReason: String,
    val seekSerial: Long,
    val reason: String,
) {
    val shouldRepopulate: Boolean
        get() = action == DanmakuSessionAction.Repopulate

    val isTick: Boolean
        get() = command == DanmakuSessionCommandType.Tick

    val isSeek: Boolean
        get() = command == DanmakuSessionCommandType.Seek

    val isRefresh: Boolean
        get() = command == DanmakuSessionCommandType.Refresh

    val isManualRepopulate: Boolean
        get() = command == DanmakuSessionCommandType.Repopulate

    val shouldRequestVodSegment: Boolean
        get() = isTick || command == DanmakuSessionCommandType.Advance || isSeek || isRefresh

    val shouldRebuildVodFromPosition: Boolean
        get() = isRefresh
}

private sealed class SessionAlgorithmCommand {
    abstract val type: DanmakuSessionCommandType
    abstract val positionMs: Long
    abstract val playbackSpeed: Float
    abstract val reason: String
    open val previousPositionMs: Long? = null
    open val windowMs: Long? = null
    open val explicitRepopulateTrigger: DanmakuSessionRepopulateTrigger? = null
    open val incrementsSeekSerial: Boolean = false
    open val keepCallerReason: Boolean = false

    fun toTimelineRequest(): SessionTimelineRequest {
        return SessionTimelineRequest(
            positionMs = positionMs.coerceAtLeast(0L),
            playbackSpeed = playbackSpeed.safePlaybackSpeed(),
            previousPositionMs = previousPositionMs?.coerceAtLeast(0L),
            incrementsSeekSerial = incrementsSeekSerial,
        )
    }

    fun repopulateReason(trigger: DanmakuSessionRepopulateTrigger): String {
        return if (keepCallerReason) reason else trigger.reason
    }

    data class Tick(
        override val positionMs: Long,
        override val playbackSpeed: Float,
        override val reason: String,
    ) : SessionAlgorithmCommand() {
        override val type: DanmakuSessionCommandType = DanmakuSessionCommandType.Tick
    }

    data class Advance(
        override val positionMs: Long,
        override val playbackSpeed: Float,
        override val reason: String,
    ) : SessionAlgorithmCommand() {
        override val type: DanmakuSessionCommandType = DanmakuSessionCommandType.Advance
    }

    data class Seek(
        override val positionMs: Long,
        override val playbackSpeed: Float,
        override val reason: String,
        override val previousPositionMs: Long?,
    ) : SessionAlgorithmCommand() {
        override val type: DanmakuSessionCommandType = DanmakuSessionCommandType.Seek
        override val explicitRepopulateTrigger: DanmakuSessionRepopulateTrigger = DanmakuSessionRepopulateTrigger.Seek
        override val incrementsSeekSerial: Boolean = true
        override val keepCallerReason: Boolean = true
    }

    data class Refresh(
        override val positionMs: Long,
        override val playbackSpeed: Float,
        override val reason: String,
        override val windowMs: Long?,
    ) : SessionAlgorithmCommand() {
        override val type: DanmakuSessionCommandType = DanmakuSessionCommandType.Refresh
        override val explicitRepopulateTrigger: DanmakuSessionRepopulateTrigger = DanmakuSessionRepopulateTrigger.Refresh
        override val keepCallerReason: Boolean = true
    }

    data class Repopulate(
        override val positionMs: Long,
        override val playbackSpeed: Float,
        override val reason: String,
        override val windowMs: Long?,
    ) : SessionAlgorithmCommand() {
        override val type: DanmakuSessionCommandType = DanmakuSessionCommandType.Repopulate
        override val explicitRepopulateTrigger: DanmakuSessionRepopulateTrigger = DanmakuSessionRepopulateTrigger.Manual
        override val keepCallerReason: Boolean = true
    }
}

private data class SessionTimelineRequest(
    val positionMs: Long,
    val playbackSpeed: Float,
    val previousPositionMs: Long?,
    val incrementsSeekSerial: Boolean,
)

private data class SessionTimelineFrame(
    val positionMs: Long,
    val initialPositionMs: Long,
    val previousPositionMs: Long?,
    val playbackSpeed: Float,
    val deltaMs: Long?,
    val motion: DanmakuSessionMotion,
    val isInitialFrame: Boolean,
    val repopulateRequested: Boolean,
    val seekSerial: Long,
)

private class SessionTimelineState {
    private var initialPositionMs: Long = 0L
    private var lastPositionMs: Long? = null
    private var pendingRepopulate: Boolean = false
    private var seekSerial: Long = 0L

    fun reset(positionMs: Long) {
        initialPositionMs = positionMs.coerceAtLeast(0L)
        lastPositionMs = null
        pendingRepopulate = false
        seekSerial = 0L
    }

    fun requestRepopulate() {
        pendingRepopulate = true
    }

    fun advanceTo(request: SessionTimelineRequest): SessionTimelineFrame {
        val previousPositionMs = request.previousPositionMs ?: lastPositionMs
        val deltaMs = previousPositionMs?.let { request.positionMs - it }
        val nextSeekSerial = if (request.incrementsSeekSerial) seekSerial + 1L else seekSerial
        val frame = SessionTimelineFrame(
            positionMs = request.positionMs,
            initialPositionMs = initialPositionMs,
            previousPositionMs = previousPositionMs,
            playbackSpeed = request.playbackSpeed,
            deltaMs = deltaMs,
            motion = motionOf(previousPositionMs = previousPositionMs, deltaMs = deltaMs),
            isInitialFrame = previousPositionMs == null,
            repopulateRequested = pendingRepopulate,
            seekSerial = nextSeekSerial,
        )
        lastPositionMs = request.positionMs
        seekSerial = nextSeekSerial
        pendingRepopulate = false
        return frame
    }

    private fun motionOf(
        previousPositionMs: Long?,
        deltaMs: Long?,
    ): DanmakuSessionMotion {
        return when {
            previousPositionMs == null -> DanmakuSessionMotion.Initial
            deltaMs == null || deltaMs == 0L -> DanmakuSessionMotion.Same
            deltaMs > 0L -> DanmakuSessionMotion.Forward
            else -> DanmakuSessionMotion.Backward
        }
    }
}

private data class SessionRepopulateDecision(
    val trigger: DanmakuSessionRepopulateTrigger,
    val reason: String,
    val window: TimelineWindow,
)

private class SessionRepopulatePolicy(
    private val thresholdMs: Long,
    private val distanceMs: Long,
) {
    fun evaluate(
        command: SessionAlgorithmCommand,
        frame: SessionTimelineFrame,
    ): SessionRepopulateDecision? {
        val trigger = triggerFor(command = command, frame = frame) ?: return null
        return SessionRepopulateDecision(
            trigger = trigger,
            reason = command.repopulateReason(trigger),
            window = windowBeforePosition(
                positionMs = frame.positionMs,
                distanceMs = command.windowMs ?: distanceMs,
            ),
        )
    }

    private fun triggerFor(
        command: SessionAlgorithmCommand,
        frame: SessionTimelineFrame,
    ): DanmakuSessionRepopulateTrigger? {
        command.explicitRepopulateTrigger?.let { return it }
        if (frame.repopulateRequested) return DanmakuSessionRepopulateTrigger.Manual
        if (frame.isInitialFrame) return DanmakuSessionRepopulateTrigger.Initial
        val deltaMs = frame.deltaMs ?: return null
        return if (abs(deltaMs) >= thresholdFor(frame.playbackSpeed)) {
            DanmakuSessionRepopulateTrigger.TimeJump
        } else {
            null
        }
    }

    private fun thresholdFor(playbackSpeed: Float): Long {
        return (thresholdMs.coerceAtLeast(0L) * playbackSpeed.safePlaybackSpeed())
            .toLong()
            .coerceAtLeast(MIN_REPOPULATE_THRESHOLD_MS)
    }

    private fun windowBeforePosition(
        positionMs: Long,
        distanceMs: Long,
    ): TimelineWindow {
        val endMs = positionMs.coerceAtLeast(0L)
        val safeDistanceMs = distanceMs.coerceAtLeast(1L)
        return TimelineWindow(
            startMs = (endMs - safeDistanceMs).coerceAtLeast(0L),
            endMs = endMs,
        )
    }

    private companion object {
        const val MIN_REPOPULATE_THRESHOLD_MS = 3_000L
    }
}

private fun Float.safePlaybackSpeed(): Float {
    return takeIf { it.isFinite() && it > 0f } ?: 1f
}
