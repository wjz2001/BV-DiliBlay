package dev.aaa1115910.bv.player.danmaku.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface DanmakuCoreSessionHandle {
    val sessionId: String
}

enum class DanmakuCoreSessionKind {
    Vod,
    Live,
}

data class DanmakuCoreSessionDescriptor(
    override val sessionId: String,
    val kind: DanmakuCoreSessionKind,
    val sourceId: DanmakuSourceId,
    val mediaId: String,
) : DanmakuCoreSessionHandle

enum class DanmakuCoreEventKind {
    Add,
    Repopulate,
    Clear,
    Lifecycle,
    Error,
    Custom,
}

interface DanmakuEventEnvelope {
    val sessionId: String
    val timestampMs: Long
    val kind: DanmakuCoreEventKind
        get() = DanmakuCoreEventKind.Custom
    val payload: Map<String, Any?>
}

interface DanmakuCoreEventStream<out E : DanmakuEventEnvelope> {
    val events: Flow<E>
}

interface DanmakuCoreCollectionBoundary<out T : DanmakuCoreItem> {
    val descriptor: DanmakuCoreSessionDescriptor?
        get() = null

    val collection: DanmakuCollection<T>

    fun snapshot(): List<T> {
        return collection.snapshot()
    }

    fun window(range: DanmakuTimeRange): List<T> {
        return collection.window(range)
    }
}

interface DanmakuCoreSessionBoundary<out T : DanmakuCoreItem, out E : DanmakuEventEnvelope> :
    DanmakuCoreSessionHandle,
    DanmakuCoreEventStream<E>,
    DanmakuCoreCollectionBoundary<T> {
    fun requestRepopulate()

    fun release()
}

sealed class DanmakuCollectionEvent<out T : DanmakuCoreItem>(
    override val sessionId: String,
    override val timestampMs: Long,
    override val kind: DanmakuCoreEventKind,
    override val payload: Map<String, Any?> = emptyMap(),
) : DanmakuEventEnvelope {
    data class Add<out T : DanmakuCoreItem>(
        override val sessionId: String,
        override val timestampMs: Long,
        val item: T,
        override val payload: Map<String, Any?> = emptyMap(),
    ) : DanmakuCollectionEvent<T>(sessionId, timestampMs, DanmakuCoreEventKind.Add, payload)

    data class Repopulate<out T : DanmakuCoreItem>(
        override val sessionId: String,
        override val timestampMs: Long,
        val items: List<T>,
        val playTimeMs: Long,
        override val payload: Map<String, Any?> = emptyMap(),
    ) : DanmakuCollectionEvent<T>(sessionId, timestampMs, DanmakuCoreEventKind.Repopulate, payload)

    data class Clear(
        override val sessionId: String,
        override val timestampMs: Long,
        val reason: String,
        override val payload: Map<String, Any?> = emptyMap(),
    ) : DanmakuCollectionEvent<Nothing>(sessionId, timestampMs, DanmakuCoreEventKind.Clear, payload)
}

interface DanmakuPlaybackCollection<T : DanmakuCoreItem> {
    val totalCount: Flow<Int?>

    fun at(
        progressMs: Flow<Long>,
        playbackSpeed: () -> Float = { 1f },
    ): DanmakuPlaybackSession<T>
}

interface DanmakuPlaybackSession<out T : DanmakuCoreItem> : DanmakuCoreEventStream<DanmakuCollectionEvent<T>> {
    val descriptor: DanmakuCoreSessionDescriptor?
        get() = null

    fun requestRepopulate()
}

fun <T : DanmakuCoreItem> emptyDanmakuPlaybackSession(
    sessionId: String = "empty",
    timestampMs: Long = 0L,
    descriptor: DanmakuCoreSessionDescriptor? = null,
): DanmakuPlaybackSession<T> {
    return object : DanmakuPlaybackSession<T> {
        override val descriptor: DanmakuCoreSessionDescriptor? = descriptor
        override val events: Flow<DanmakuCollectionEvent<T>> = flowOf(
            DanmakuCollectionEvent.Repopulate(
                sessionId = descriptor?.sessionId ?: sessionId,
                timestampMs = timestampMs,
                items = emptyList(),
                playTimeMs = 0L,
            )
        )

        override fun requestRepopulate() = Unit
    }
}

interface DanmakuCoreSession<in E : DanmakuEventEnvelope> {
    fun dispatch(event: E)

    fun release()
}

fun interface DanmakuCoreEventListener<in E : DanmakuEventEnvelope> {
    fun onEvent(event: E)
}
