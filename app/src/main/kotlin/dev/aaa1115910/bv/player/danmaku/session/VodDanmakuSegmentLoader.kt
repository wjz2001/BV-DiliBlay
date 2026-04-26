package dev.aaa1115910.bv.player.danmaku.session

import dev.aaa1115910.biliapi.http.BiliHttpApi
import dev.aaa1115910.biliapi.http.entity.danmaku.DanmakuData
import dev.aaa1115910.bv.player.danmaku.model.DanmakuItem
import dev.aaa1115910.bv.player.danmaku.model.DanmakuTrackType

data class VodDanmakuSegmentRequest(
    val aid: Long,
    val cid: Long,
    val segmentIndex: Int,
)

data class VodDanmakuSegmentResult(
    val request: VodDanmakuSegmentRequest,
    val sourceId: String,
    val items: List<DanmakuItem>,
)

fun interface VodDanmakuSegmentFetcher {
    suspend fun fetch(request: VodDanmakuSegmentRequest): VodDanmakuSegmentResult
}

class BiliVodDanmakuSegmentFetcher(
    private val sessDataProvider: () -> String = { "" },
) : VodDanmakuSegmentFetcher {
    override suspend fun fetch(request: VodDanmakuSegmentRequest): VodDanmakuSegmentResult {
        val sourceId = request.sourceId()
        val items = BiliHttpApi.getDanmakuSeg(
            cid = request.cid,
            avid = request.aid,
            segmentIndex = request.segmentIndex,
            sessData = sessDataProvider(),
        ).map { data ->
            data.toDanmakuItem(source = sourceId, arrivalTimeMs = System.currentTimeMillis())
        }.sortedWith(compareBy({ it.timeMs }, { it.level }, { it.id }))

        return VodDanmakuSegmentResult(
            request = request,
            sourceId = sourceId,
            items = items,
        )
    }
}

private fun DanmakuData.toDanmakuItem(
    source: String,
    arrivalTimeMs: Long,
): DanmakuItem {
    return DanmakuItem(
        id = dmid,
        timeMs = (time * 1000).toInt(),
        text = text,
        mode = type,
        textSize = size,
        color = color and 0xFFFFFF,
        level = level,
        timestamp = timestamp,
        pool = pool,
        midHash = midHash,
        source = source,
        arrivalTimeMs = arrivalTimeMs,
        cacheKey = "$source-$dmid",
        trackType = type.toTrackType(),
    )
}

private fun Int.toTrackType(): DanmakuTrackType {
    return when (this) {
        5 -> DanmakuTrackType.Top
        4 -> DanmakuTrackType.Bottom
        else -> DanmakuTrackType.Scroll
    }
}

private fun VodDanmakuSegmentRequest.sourceId(): String {
    return "vod-$aid-$cid-seg-$segmentIndex"
}
