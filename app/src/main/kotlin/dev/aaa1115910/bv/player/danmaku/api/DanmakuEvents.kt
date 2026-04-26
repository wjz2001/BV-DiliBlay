package dev.aaa1115910.bv.player.danmaku.api

import dev.aaa1115910.bv.player.danmaku.core.DanmakuCoreEventKind
import dev.aaa1115910.bv.player.danmaku.core.DanmakuEventEnvelope

enum class DanmakuSessionEventType {
    SessionStarted,
    SessionStopped,
    VodSegmentRequested,
    VodSegmentAppended,
    VodRepopulateRequested,
    LiveConnected,
    LiveDisconnected,
    LiveStateChanged,
    LiveEventReceived,
    LiveEventDropped,
    LiveBufferFlushed,
    LiveBufferReset,
    FilterRuleUpdated,
    DanmakuFiltered,
    DanmakuAccepted,
    SeekNotified,
    RefreshRequested,
    Error,
}

data class DanmakuSessionEvent(
    val type: DanmakuSessionEventType,
    override val sessionId: String,
    override val timestampMs: Long,
    override val payload: Map<String, Any?> = emptyMap(),
) : DanmakuEventEnvelope {
    override val kind: DanmakuCoreEventKind
        get() = when (type) {
            DanmakuSessionEventType.SessionStarted,
            DanmakuSessionEventType.SessionStopped,
            DanmakuSessionEventType.VodSegmentRequested,
            DanmakuSessionEventType.LiveConnected,
            DanmakuSessionEventType.LiveDisconnected,
            DanmakuSessionEventType.LiveStateChanged,
            DanmakuSessionEventType.LiveBufferFlushed,
            DanmakuSessionEventType.LiveBufferReset,
            DanmakuSessionEventType.FilterRuleUpdated,
            DanmakuSessionEventType.SeekNotified,
            DanmakuSessionEventType.RefreshRequested,
            -> DanmakuCoreEventKind.Lifecycle

            DanmakuSessionEventType.DanmakuAccepted,
            DanmakuSessionEventType.VodSegmentAppended,
            DanmakuSessionEventType.LiveEventReceived,
            -> DanmakuCoreEventKind.Add

            DanmakuSessionEventType.VodRepopulateRequested -> DanmakuCoreEventKind.Repopulate
            DanmakuSessionEventType.LiveEventDropped,
            DanmakuSessionEventType.DanmakuFiltered,
            -> DanmakuCoreEventKind.Clear

            DanmakuSessionEventType.Error -> DanmakuCoreEventKind.Error
        }
}
