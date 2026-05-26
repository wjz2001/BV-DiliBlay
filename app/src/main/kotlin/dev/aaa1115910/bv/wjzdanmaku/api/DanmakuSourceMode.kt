package dev.aaa1115910.bv.wjzdanmaku.api

sealed class DanmakuSourceMode {
    object None : DanmakuSourceMode()

    data class Vod(
        val aid: Long,
        val cid: Long,
    ) : DanmakuSourceMode()

    data class Live(
        val roomId: Long,
        val eventStream: DanmakuLiveEventStream? = null,
        val input: DanmakuLiveInput? = null,
    ) : DanmakuSourceMode()
}
