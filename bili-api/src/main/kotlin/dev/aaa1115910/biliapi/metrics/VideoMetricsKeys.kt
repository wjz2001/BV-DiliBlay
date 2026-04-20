package dev.aaa1115910.biliapi.metrics

object VideoMetricsKeys {
    fun statKey(aid: Long): String {
        return "video_metrics:stat:v1:src-web-detail:aid:$aid"
    }

    fun reqBvidKey(normalizedBvid: String): String {
        return "video_metrics:req:bvid:$normalizedBvid"
    }

    fun aliasKey(normalizedBvid: String): String {
        return "video_metrics:alias:bvid:$normalizedBvid"
    }

    fun contextKey(request: VideoMetricsRequest): String {
        val cid = request.cid ?: 0L
        return request.aid?.let { aid ->
            "video_metrics:req:aid:$aid:cid:$cid"
        } ?: run {
            val normalizedBvid = request.bvid.normalizeBvidOrNullForKey()
                ?: error("bvid should not be null when aid is absent")
            "video_metrics:req:bvid:$normalizedBvid:cid:$cid"
        }
    }

    fun deferredPrefetchKey(request: VideoMetricsRequest): String {
        return request.aid?.let { aid ->
            "video_metrics:deferred:aid:$aid:cid:${request.cid ?: 0L}"
        } ?: run {
            val normalizedBvid = request.bvid.normalizeBvidOrNullForKey()
                ?: error("bvid should not be null when aid is absent")
            "video_metrics:deferred:bvid:$normalizedBvid:cid:${request.cid ?: 0L}"
        }
    }

    private fun String?.normalizeBvidOrNullForKey(): String? {
        return this?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
    }
}
