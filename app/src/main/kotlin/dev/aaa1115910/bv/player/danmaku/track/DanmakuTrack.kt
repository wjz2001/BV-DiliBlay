package dev.aaa1115910.bv.player.danmaku.track

interface DanmakuTrack<T : SizeSpecifiedDanmaku, D> : Iterable<D> {
    fun place(danmaku: T, nowMs: Long): D

    fun canPlace(danmaku: T, nowMs: Long): Boolean

    fun tryPlace(danmaku: T, nowMs: Long): D? {
        if (!canPlace(danmaku, nowMs)) return null
        return place(danmaku, nowMs)
    }

    fun clearAll()

    fun tick(nowMs: Long) {}
}
