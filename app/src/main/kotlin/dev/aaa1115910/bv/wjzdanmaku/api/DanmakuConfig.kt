package dev.aaa1115910.bv.wjzdanmaku.api

enum class DanmakuLaneDensity {
    Sparse,
    Standard,
    Dense,
}

enum class DanmakuConfigSourceMode {
    Vod,
    Live,
}

data class DanmakuFilterRule(
    val allowScroll: Boolean = true,
    val allowTop: Boolean = true,
    val allowBottom: Boolean = true,
    val minLevel: Int = 0,
    val blockedKeywords: Set<String> = emptySet(),
    val blockedUsers: Set<Long> = emptySet(),
)

data class DanmakuConfig(
    val enabled: Boolean = false,
    val opacity: Float = 1f,
    val textSizeSp: Float = 40f,
    val textSizeScale: Int = 100,
    val colorful: Boolean = true,
    val textPaddingPx: Int = 6,
    val bottomPaddingPx: Int = 0,
    val speedLevel: Int = 3,
    val durationMultiplier: Float = 1f,
    val safeSeparation: Float = 36f,
    val area: Float = 1f,
    val laneDensity: DanmakuLaneDensity = DanmakuLaneDensity.Standard,
    val allowScroll: Boolean = true,
    val allowTop: Boolean = true,
    val allowBottom: Boolean = true,
    val minLevel: Int = 0,
    val vodMinLevel: Int = minLevel,
    val liveMinLevel: Int = minLevel,
) {
    fun minLevelFor(sourceMode: DanmakuConfigSourceMode): Int {
        return when (sourceMode) {
            DanmakuConfigSourceMode.Vod -> vodMinLevel
            DanmakuConfigSourceMode.Live -> liveMinLevel
        }
    }

    fun toFilterRule(sourceMode: DanmakuConfigSourceMode = DanmakuConfigSourceMode.Vod): DanmakuFilterRule {
        return DanmakuFilterRule(
            allowScroll = allowScroll,
            allowTop = allowTop,
            allowBottom = allowBottom,
            minLevel = minLevelFor(sourceMode),
        )
    }

    fun mergeToFilterRule(
        current: DanmakuFilterRule,
        sourceMode: DanmakuConfigSourceMode = DanmakuConfigSourceMode.Vod,
    ): DanmakuFilterRule {
        return current.copy(
            allowScroll = allowScroll,
            allowTop = allowTop,
            allowBottom = allowBottom,
            minLevel = minLevelFor(sourceMode),
        )
    }
}
