package dev.aaa1115910.bv.player.danmaku.renderer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import dev.aaa1115910.bv.player.danmaku.config.DanmakuConfig
import dev.aaa1115910.bv.player.danmaku.model.DanmakuItem

data class DanmakuTextMeasureKey(
    val text: String,
    val textSizeSp: Float,
    val textSizeScale: Int,
    val textPaddingPx: Int,
    val density: Float,
    val fontScale: Float,
)

data class DanmakuTextMeasureResult(
    val widthPx: Int,
    val heightPx: Int,
    val layoutResult: TextLayoutResult,
)

class DanmakuTextMeasureCache(
    private val maxSize: Int = DEFAULT_MAX_SIZE,
) {
    private val cache = object : LinkedHashMap<DanmakuTextMeasureKey, DanmakuTextMeasureResult>(
        maxSize,
        LOAD_FACTOR,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<DanmakuTextMeasureKey, DanmakuTextMeasureResult>?,
        ): Boolean {
            return size > maxSize.coerceAtLeast(1)
        }
    }

    fun getOrMeasure(
        item: DanmakuItem,
        config: DanmakuConfig,
        density: Density,
        textMeasurer: TextMeasurer,
    ): DanmakuTextMeasureResult {
        val key = item.toMeasureKey(config, density)
        return cache.getOrPut(key) {
            val layoutResult = textMeasurer.measure(
                text = key.text,
                style = key.toTextStyle(),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            val canvasPadding = key.textPaddingPx.coerceAtLeast(0)
            DanmakuTextMeasureResult(
                widthPx = layoutResult.size.width + canvasPadding,
                heightPx = layoutResult.size.height + canvasPadding,
                layoutResult = layoutResult,
            )
        }
    }

    fun clear() {
        cache.clear()
    }

    fun size(): Int = cache.size

    companion object {
        const val DEFAULT_MAX_SIZE = 2_048
        private const val LOAD_FACTOR = 0.75f
    }
}

@Composable
fun rememberDanmakuTextMeasureCache(
    maxSize: Int = DanmakuTextMeasureCache.DEFAULT_MAX_SIZE,
): DanmakuTextMeasureCache {
    return remember(maxSize) {
        DanmakuTextMeasureCache(maxSize = maxSize)
    }
}

private fun DanmakuItem.toMeasureKey(
    config: DanmakuConfig,
    density: Density,
): DanmakuTextMeasureKey {
    return DanmakuTextMeasureKey(
        text = text,
        textSizeSp = config.textSizeSp,
        textSizeScale = config.textSizeScale,
        textPaddingPx = config.textPaddingPx,
        density = density.density,
        fontScale = density.fontScale,
    )
}

private fun DanmakuTextMeasureKey.toTextStyle(): TextStyle {
    val configScale = textSizeScale.coerceIn(25, 400) / 100f
    return TextStyle(
        fontSize = (textSizeSp.coerceAtLeast(MIN_TEXT_SIZE_SP) * configScale).sp,
        fontWeight = FontWeight.Bold,
    )
}

private const val MIN_TEXT_SIZE_SP = 1f
