package dev.aaa1115910.biliapi.http.entity.danmaku

data class DanmakuData(
    val time: Float,
    val type: Int,
    val size: Int,
    val color: Int,
    val timestamp: Int,
    val pool: Int,
    val midHash: String,
    val dmid: Long,
    val level: Int,
    val text: String
)
