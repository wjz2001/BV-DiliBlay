package dev.aaa1115910.bv.wjzdanmaku.live

import dev.aaa1115910.bv.wjzdanmaku.DanmakuLiveEventType

data class DanmakuLiveBusinessState(
    val roomId: Long = 0L,
    val lastEventType: DanmakuLiveEventType? = null,
    val lastEventTimestampMs: Long = 0L,
    val popularity: Int? = null,
    val popularityText: String? = null,
    val onlineRankCount: Int? = null,
    val unknownEventCount: Long = 0L,
)

