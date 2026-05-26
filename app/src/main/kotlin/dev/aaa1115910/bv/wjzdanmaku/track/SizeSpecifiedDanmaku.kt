package dev.aaa1115910.bv.wjzdanmaku.track

interface SizeSpecifiedDanmaku {
    val danmakuWidthPx: Float
    val danmakuHeightPx: Float
}

interface FloatingSizeSpecifiedDanmaku : SizeSpecifiedDanmaku {
    val speedPxPerMs: Float
    val durationMs: Int
}
