package dev.aaa1115910.bv.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 一个封装了 TV 焦点定轴逻辑的 LazyVerticalGrid。
 *
 * 1. 改成按 item 中心点与 pivot 对齐；
 * 2. 增加安全区（safe zone），在安全区内不滚动；
 * 3. 增加死区（hysteresis），避免只差几像素也触发滚动。
 *
 * 这样可以明显减少“每次上下切焦点都强行校正位置”带来的抖动感。
 *
 * @param pivotFraction 焦点 Item 的中心点在屏幕上的目标停留位置比例 (0.0 - 1.0)。
 * 默认 0.3f，保持与旧行为接近。
 * @param topSafeFraction 顶部安全区比例。Item 完全处于安全区内时，不触发滚动。
 * @param bottomSafeFraction 底部安全区比例。Item 完全处于安全区内时，不触发滚动。
 * @param hysteresis 滚动死区。只差很少像素时不滚，减少抖动。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvLazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),

    // 针对“一屏两行多卡片”的默认值
    pivotFraction: Float = 0.42f,
    topSafeFraction: Float = 0.12f,
    bottomSafeFraction: Float = 0.88f,
    hysteresis: Dp = 32.dp,

    content: LazyGridScope.() -> Unit
) {
    val hysteresisPx = with(LocalDensity.current) { hysteresis.toPx() }

    val bringIntoViewSpec = remember(
        pivotFraction,
        topSafeFraction,
        bottomSafeFraction,
        hysteresisPx
    ) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float
            ): Float {
                if (size <= 0f || containerSize <= 0f) return 0f

                val itemStart = offset
                val itemEnd = offset + size

                val topSafe = containerSize * topSafeFraction
                val bottomSafe = containerSize * bottomSafeFraction

                // 完全在安全区内：不滚
                if (
                    itemStart >= topSafe - hysteresisPx &&
                    itemEnd <= bottomSafe + hysteresisPx
                ) {
                    return 0f
                }

                // 改成按 item 中心点对齐 pivot
                val itemCenter = itemStart + size * 0.5f
                val targetCenter = containerSize * pivotFraction
                val delta = itemCenter - targetCenter

                // 死区：避免只差一点点也滚
                return if (abs(delta) <= hysteresisPx) 0f else delta
            }
        }
    }

    CompositionLocalProvider(
        LocalBringIntoViewSpec provides bringIntoViewSpec
    ) {
        LazyVerticalGrid(
            columns = columns,
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            content = content
        )
    }
}