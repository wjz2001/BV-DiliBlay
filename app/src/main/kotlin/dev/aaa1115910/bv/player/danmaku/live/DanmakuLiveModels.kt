package dev.aaa1115910.bv.player.danmaku.live

enum class DanmakuLiveEventType {
    Danmaku,
    PopularityChange,
    OnlineRankCount,
    Unknown,
}

data class DanmakuLiveEvent(
    val type: DanmakuLiveEventType,
    val timestampMs: Long,
    val roomId: Long,
    val content: String? = null,
    val userId: Long? = null,
    val username: String? = null,
    val medalName: String? = null,
    val medalLevel: Int? = null,
    val mode: Int? = null,
    val fontSize: Int? = null,
    val color: Int? = null,
    val userLevel: Int? = null,
    val popularity: Int? = null,
    val popularityText: String? = null,
    val onlineRankCount: Int? = null,
)

data class DanmakuLiveBusinessState(
    val roomId: Long = 0L,
    val lastEventType: DanmakuLiveEventType? = null,
    val lastEventTimestampMs: Long = 0L,
    val popularity: Int? = null,
    val popularityText: String? = null,
    val onlineRankCount: Int? = null,
    val unknownEventCount: Long = 0L,
)

