package dev.aaa1115910.bv.wjzdanmaku.api

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
    val sessionId: String,
    val timestampMs: Long,
    val payload: Map<String, Any?> = emptyMap(),
)

sealed interface DanmakuHostCommand {
    data class Seek(
        val positionMs: Long,
        val forceFetch: Boolean,
        val reason: String,
    ) : DanmakuHostCommand

    data class RefreshFromPosition(
        val positionMs: Long,
        val forceFetch: Boolean,
        val reason: String,
    ) : DanmakuHostCommand

    data class Clear(
        val reason: String,
    ) : DanmakuHostCommand
}
