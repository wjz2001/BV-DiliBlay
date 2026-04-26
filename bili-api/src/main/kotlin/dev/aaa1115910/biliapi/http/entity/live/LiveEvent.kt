package dev.aaa1115910.biliapi.http.entity.live

interface LiveEvent

data class DanmakuEvent(
    val content: String,
    val mid: Long,
    val username: String,
    val medalName: String? = null,
    val medalLevel: Int? = null,
    val mode: Int = 1,
    val fontSize: Int = 25,
    val color: Int = 0xFFFFFF,
    val userLevel: Int = 0
) : LiveEvent

data class PopularityChangeEvent(
    val popularity: Int,
    val popularityText: String
) : LiveEvent

data class OnlineRankCountEvent(
    val count: Int
) : LiveEvent
