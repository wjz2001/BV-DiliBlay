package dev.aaa1115910.bv.wjzdanmaku.api

import dev.aaa1115910.bv.wjzdanmaku.DanmakuLiveEvent

fun interface DanmakuLiveEventStream {
    fun collect(consumer: (DanmakuLiveEvent) -> Unit)
}

fun interface DanmakuLiveInput {
    fun collect(roomId: Long, consumer: (DanmakuLiveEvent) -> Unit)
}
