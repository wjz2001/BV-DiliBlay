package dev.aaa1115910.bv.player.danmaku.live

import dev.aaa1115910.biliapi.http.entity.live.DanmakuEvent
import dev.aaa1115910.biliapi.http.entity.live.LiveEvent
import dev.aaa1115910.biliapi.http.entity.live.OnlineRankCountEvent
import dev.aaa1115910.biliapi.http.entity.live.PopularityChangeEvent

fun interface DanmakuLiveEventStream {
    fun collect(consumer: (DanmakuLiveEvent) -> Unit)
}

fun interface DanmakuLiveInput {
    fun collect(roomId: Long, consumer: (DanmakuLiveEvent) -> Unit)
}

interface DanmakuLiveEventConverter<T> {
    fun convert(roomId: Long, event: T, receiveTimeMs: Long = System.currentTimeMillis()): DanmakuLiveEvent
}

object BiliLiveEventConverter : DanmakuLiveEventConverter<LiveEvent> {
    override fun convert(roomId: Long, event: LiveEvent, receiveTimeMs: Long): DanmakuLiveEvent {
        return when (event) {
            is DanmakuEvent -> DanmakuLiveEvent(
                type = DanmakuLiveEventType.Danmaku,
                timestampMs = receiveTimeMs,
                roomId = roomId,
                content = event.content,
                userId = event.mid,
                username = event.username,
                medalName = event.medalName,
                medalLevel = event.medalLevel,
                mode = event.mode,
                fontSize = event.fontSize,
                color = event.color,
                userLevel = event.userLevel,
            )

            is PopularityChangeEvent -> DanmakuLiveEvent(
                type = DanmakuLiveEventType.PopularityChange,
                timestampMs = receiveTimeMs,
                roomId = roomId,
                popularity = event.popularity,
                popularityText = event.popularityText,
            )

            is OnlineRankCountEvent -> DanmakuLiveEvent(
                type = DanmakuLiveEventType.OnlineRankCount,
                timestampMs = receiveTimeMs,
                roomId = roomId,
                onlineRankCount = event.count,
            )

            else -> DanmakuLiveEvent(
                type = DanmakuLiveEventType.Unknown,
                timestampMs = receiveTimeMs,
                roomId = roomId,
            )
        }
    }
}

class BiliLiveEventStream(
    private val roomId: Long,
    private val upstream: (consumer: (LiveEvent) -> Unit) -> Unit,
    private val converter: DanmakuLiveEventConverter<LiveEvent> = BiliLiveEventConverter,
) : DanmakuLiveEventStream {
    override fun collect(consumer: (DanmakuLiveEvent) -> Unit) {
        upstream { event ->
            consumer(converter.convert(roomId, event))
        }
    }
}

fun interface DanmakuLiveFilter {
    fun accept(event: DanmakuLiveEvent): Boolean
}

enum class DanmakuLiveDropReason {
    BufferFull,
    EventFiltered,
    NonDanmaku,
}

data class DanmakuLiveDropDecision(
    val dropIncoming: Boolean,
    val dropOldest: Boolean,
    val reason: DanmakuLiveDropReason,
)

fun interface DanmakuLiveDropStrategy {
    fun decide(bufferSize: Int, maxBufferSize: Int, event: DanmakuLiveEvent): DanmakuLiveDropDecision?
}

object DropIncomingWhenFullStrategy : DanmakuLiveDropStrategy {
    override fun decide(bufferSize: Int, maxBufferSize: Int, event: DanmakuLiveEvent): DanmakuLiveDropDecision? {
        if (bufferSize < maxBufferSize) return null
        return DanmakuLiveDropDecision(
            dropIncoming = true,
            dropOldest = false,
            reason = DanmakuLiveDropReason.BufferFull,
        )
    }
}

object DropOldestWhenFullStrategy : DanmakuLiveDropStrategy {
    override fun decide(bufferSize: Int, maxBufferSize: Int, event: DanmakuLiveEvent): DanmakuLiveDropDecision? {
        if (bufferSize < maxBufferSize) return null
        return DanmakuLiveDropDecision(
            dropIncoming = false,
            dropOldest = true,
            reason = DanmakuLiveDropReason.BufferFull,
        )
    }
}

data class DanmakuLiveBufferResult(
    val accepted: Boolean,
    val dropped: Boolean,
    val dropReason: DanmakuLiveDropReason? = null,
    val bufferSize: Int,
)

interface DanmakuLiveBuffer {
    fun append(event: DanmakuLiveEvent): DanmakuLiveBufferResult

    fun flush(): List<DanmakuLiveEvent>

    fun clear()

    fun size(): Int
}

class DefaultDanmakuLiveBuffer(
    maxBufferSize: Int = DEFAULT_MAX_BUFFER_SIZE,
    private val dropStrategy: DanmakuLiveDropStrategy = DropOldestWhenFullStrategy,
    private val filter: DanmakuLiveFilter = DanmakuLiveFilter { true },
) : DanmakuLiveBuffer {
    private val capacity = maxBufferSize.coerceAtLeast(1)
    private val stateLock = Any()
    private val events = ArrayDeque<DanmakuLiveEvent>()

    override fun append(event: DanmakuLiveEvent): DanmakuLiveBufferResult = withStateLock {
        if (!filter.accept(event)) {
            DanmakuLiveBufferResult(
                accepted = false,
                dropped = true,
                dropReason = DanmakuLiveDropReason.EventFiltered,
                bufferSize = events.size,
            )
        } else {
            val decision = dropStrategy.decide(events.size, capacity, event)
            var droppedOldest = decision?.dropOldest == true && events.isNotEmpty()
            if (droppedOldest) {
                events.removeFirst()
            }
            if (decision?.dropIncoming == true) {
                DanmakuLiveBufferResult(
                    accepted = false,
                    dropped = true,
                    dropReason = decision.reason,
                    bufferSize = events.size,
                )
            } else {
                events.addLast(event)
                while (events.size > capacity) {
                    events.removeFirst()
                    droppedOldest = true
                }
                DanmakuLiveBufferResult(
                    accepted = true,
                    dropped = droppedOldest,
                    dropReason = if (droppedOldest) DanmakuLiveDropReason.BufferFull else decision?.reason,
                    bufferSize = events.size,
                )
            }
        }
    }

    override fun flush(): List<DanmakuLiveEvent> = withStateLock {
        if (events.isEmpty()) {
            emptyList()
        } else {
            val snapshot = events.toList()
            events.clear()
            snapshot
        }
    }

    override fun clear() {
        withStateLock {
            events.clear()
        }
    }

    override fun size(): Int = withStateLock {
        events.size
    }

    private inline fun <T> withStateLock(block: () -> T): T {
        return synchronized(stateLock) { block() }
    }

    companion object {
        const val DEFAULT_MAX_BUFFER_SIZE = 500
    }
}