package dev.aaa1115910.bv.player.danmaku.core

import kotlin.jvm.JvmInline

@JvmInline
value class DanmakuSourceId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "DanmakuSourceId must not be blank" }
    }

    companion object {
        val Bilibili = DanmakuSourceId("Bilibili")
        val Live = DanmakuSourceId("Live")
        val Local = DanmakuSourceId("Local")
    }
}

enum class DanmakuCoreLocation {
    Scroll,
    Top,
    Bottom,
}

data class DanmakuCoreContent(
    val playTimeMillis: Long,
    val colorRgb: Int,
    val text: String,
    val location: DanmakuCoreLocation,
)

data class DanmakuCoreInfo(
    val id: String,
    val sourceId: DanmakuSourceId,
    val senderId: String?,
    val content: DanmakuCoreContent,
) {
    val playTimeMillis: Long
        get() = content.playTimeMillis

    val colorRgb: Int
        get() = content.colorRgb

    val text: String
        get() = content.text

    val location: DanmakuCoreLocation
        get() = content.location
}

interface DanmakuCoreItem {
    val id: Long
    val timeMs: Int
    val text: String
    val source: String
    val arrivalTimeMs: Long

    val coreId: String
        get() = id.toString()

    val playTimeMillis: Long
        get() = timeMs.toLong()

    val sourceId: DanmakuSourceId
        get() = DanmakuSourceId(source.ifBlank { DanmakuSourceId.Local.value })

    val senderId: String?
        get() = null

    val colorRgb: Int
        get() = DEFAULT_DANMAKU_COLOR_RGB

    val location: DanmakuCoreLocation
        get() = DanmakuCoreLocation.Scroll

    val content: DanmakuCoreContent
        get() = DanmakuCoreContent(
            playTimeMillis = playTimeMillis,
            colorRgb = colorRgb,
            text = text,
            location = location,
        )

    val coreInfo: DanmakuCoreInfo
        get() = DanmakuCoreInfo(
            id = coreId,
            sourceId = sourceId,
            senderId = senderId,
            content = content,
        )

    companion object {
        const val DEFAULT_DANMAKU_COLOR_RGB = 0xFFFFFF
    }
}

data class BasicDanmakuCoreItem(
    override val id: Long,
    override val timeMs: Int,
    override val text: String,
    override val source: String,
    override val arrivalTimeMs: Long,
    override val senderId: String? = null,
    override val colorRgb: Int = DanmakuCoreItem.DEFAULT_DANMAKU_COLOR_RGB,
    override val location: DanmakuCoreLocation = DanmakuCoreLocation.Scroll,
) : DanmakuCoreItem

fun DanmakuCoreInfo.toCoreItem(
    numericId: Long = id.toLongOrNull() ?: id.hashCode().toLong(),
    arrivalTimeMs: Long = playTimeMillis,
): BasicDanmakuCoreItem {
    return BasicDanmakuCoreItem(
        id = numericId,
        timeMs = playTimeMillis.toIntSaturated(),
        text = text,
        source = sourceId.value,
        arrivalTimeMs = arrivalTimeMs,
        senderId = senderId,
        colorRgb = colorRgb,
        location = location,
    )
}

interface DanmakuTimeRange {
    val startMs: Long
    val endMs: Long

    fun contains(timeMs: Long): Boolean {
        return timeMs >= startMs && timeMs < endMs
    }
}

data class DanmakuTimeWindow(
    override val startMs: Long,
    override val endMs: Long,
) : DanmakuTimeRange {
    init {
        require(endMs >= startMs) { "endMs must be greater than or equal to startMs" }
    }
}

private fun Long.toIntSaturated(): Int {
    return coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}
