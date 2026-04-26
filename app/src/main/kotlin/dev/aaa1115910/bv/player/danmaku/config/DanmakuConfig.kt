package dev.aaa1115910.bv.player.danmaku.config

import dev.aaa1115910.bv.player.danmaku.model.DanmakuFilterRule

enum class DanmakuLaneDensity {
    Sparse,
    Standard,
    Dense,
}

enum class DanmakuConfigSourceMode {
    Vod,
    Live,
}

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

data class DanmakuConfigDiff(
    val oldConfig: DanmakuConfig,
    val newConfig: DanmakuConfig,
    val affectsLayout: Boolean,
    val updateOnlyKeys: Set<String>,
    val staticUpdateKeys: Set<String>,
    val repopulateKeys: Set<String>,
) {
    val applyAction: DanmakuConfigApplyAction
        get() = when {
            repopulateKeys.isNotEmpty() -> DanmakuConfigApplyAction.RepopulateRequired
            staticUpdateKeys.isNotEmpty() -> DanmakuConfigApplyAction.StaticUpdate
            else -> DanmakuConfigApplyAction.UpdateOnly
        }
}

enum class DanmakuConfigApplyAction {
    UpdateOnly,
    StaticUpdate,
    RepopulateRequired,
}

object DanmakuConfigKeys {
    const val ENABLED = "enabled"
    const val OPACITY = "opacity"
    const val TEXT_SIZE_SP = "textSizeSp"
    const val TEXT_SIZE_SCALE = "textSizeScale"
    const val COLORFUL = "colorful"
    const val TEXT_PADDING_PX = "textPaddingPx"
    const val BOTTOM_PADDING_PX = "bottomPaddingPx"
    const val SPEED_LEVEL = "speedLevel"
    const val DURATION_MULTIPLIER = "durationMultiplier"
    const val SAFE_SEPARATION = "safeSeparation"
    const val AREA = "area"
    const val LANE_DENSITY = "laneDensity"
    const val ALLOW_SCROLL = "allowScroll"
    const val ALLOW_TOP = "allowTop"
    const val ALLOW_BOTTOM = "allowBottom"
    const val MIN_LEVEL = "minLevel"
    const val VOD_MIN_LEVEL = "vodMinLevel"
    const val LIVE_MIN_LEVEL = "liveMinLevel"
}

object DanmakuConfigDiffer {
    fun diff(oldConfig: DanmakuConfig, newConfig: DanmakuConfig): DanmakuConfigDiff {
        val updateOnlyKeys = linkedSetOf<String>()
        val staticUpdateKeys = linkedSetOf<String>()
        val repopulateKeys = linkedSetOf<String>()

        if (oldConfig.enabled != newConfig.enabled) updateOnlyKeys += DanmakuConfigKeys.ENABLED
        if (oldConfig.opacity != newConfig.opacity) updateOnlyKeys += DanmakuConfigKeys.OPACITY
        if (oldConfig.textSizeSp != newConfig.textSizeSp) repopulateKeys += DanmakuConfigKeys.TEXT_SIZE_SP
        if (oldConfig.textSizeScale != newConfig.textSizeScale) repopulateKeys += DanmakuConfigKeys.TEXT_SIZE_SCALE
        if (oldConfig.colorful != newConfig.colorful) updateOnlyKeys += DanmakuConfigKeys.COLORFUL
        if (oldConfig.textPaddingPx != newConfig.textPaddingPx) repopulateKeys += DanmakuConfigKeys.TEXT_PADDING_PX
        if (oldConfig.bottomPaddingPx != newConfig.bottomPaddingPx) repopulateKeys += DanmakuConfigKeys.BOTTOM_PADDING_PX
        if (oldConfig.speedLevel != newConfig.speedLevel) staticUpdateKeys += DanmakuConfigKeys.SPEED_LEVEL
        if (oldConfig.durationMultiplier != newConfig.durationMultiplier) staticUpdateKeys += DanmakuConfigKeys.DURATION_MULTIPLIER
        if (oldConfig.safeSeparation != newConfig.safeSeparation) staticUpdateKeys += DanmakuConfigKeys.SAFE_SEPARATION
        if (oldConfig.area != newConfig.area) repopulateKeys += DanmakuConfigKeys.AREA
        if (oldConfig.laneDensity != newConfig.laneDensity) repopulateKeys += DanmakuConfigKeys.LANE_DENSITY
        if (oldConfig.allowScroll != newConfig.allowScroll) repopulateKeys += DanmakuConfigKeys.ALLOW_SCROLL
        if (oldConfig.allowTop != newConfig.allowTop) repopulateKeys += DanmakuConfigKeys.ALLOW_TOP
        if (oldConfig.allowBottom != newConfig.allowBottom) repopulateKeys += DanmakuConfigKeys.ALLOW_BOTTOM
        if (oldConfig.minLevel != newConfig.minLevel) repopulateKeys += DanmakuConfigKeys.MIN_LEVEL
        if (oldConfig.vodMinLevel != newConfig.vodMinLevel) repopulateKeys += DanmakuConfigKeys.VOD_MIN_LEVEL
        if (oldConfig.liveMinLevel != newConfig.liveMinLevel) repopulateKeys += DanmakuConfigKeys.LIVE_MIN_LEVEL

        return DanmakuConfigDiff(
            oldConfig = oldConfig,
            newConfig = newConfig,
            affectsLayout = repopulateKeys.isNotEmpty(),
            updateOnlyKeys = updateOnlyKeys,
            staticUpdateKeys = staticUpdateKeys,
            repopulateKeys = repopulateKeys,
        )
    }
}
