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

enum class TvGridBringIntoViewMode {
    /**
     * 保持 TV 定轴感：
     * 只有越过安全区时，才把 item 中心往 pivot 拉。
     */
    Pivot,

    /**
     * 只保证可见：
     * 只要 item 还在安全区内，就完全不滚；
     * 越界时只滚最小距离让它回到安全区。
     */
    KeepVisible
}

/**
 * 一个封装了 TV 焦点滚动行为的 LazyVerticalGrid。
 *
 * 支持两种模式：
 * 1. Pivot：保留 TV 定轴感，但按 item 中心算，并带 safe zone / hysteresis
 * 2. KeepVisible：不追求固定位置，只保证 item 保持在安全区内
 *

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

    // 可选 KeepVisible,Pivot
    mode: TvGridBringIntoViewMode = TvGridBringIntoViewMode.KeepVisible,
    // Pivot 模式用
    pivotFraction: Float = 0.45f,
    // Pivot 与 KeepVisible 模式共用
    topSafeFraction: Float = 0.12f,
    bottomSafeFraction: Float = 0.9f,
    hysteresis: Dp = 36.dp,

    content: LazyGridScope.() -> Unit
) {
    val hysteresisPx = with(LocalDensity.current) { hysteresis.toPx() }

    val bringIntoViewSpec = remember(
        mode,
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

                return when (mode) {
                    TvGridBringIntoViewMode.KeepVisible -> {
                        when {
                            // 顶部越界：最小距离推回 topSafe
                            itemStart < topSafe - hysteresisPx -> {
                                itemStart - topSafe
                            }

                            // 底部越界：最小距离推回 bottomSafe
                            itemEnd > bottomSafe + hysteresisPx -> {
                                itemEnd - bottomSafe
                            }

                            // 在安全区内：不滚
                            else -> 0f
                        }
                    }

                    TvGridBringIntoViewMode.Pivot -> {
                        // 在安全区内：不滚
                        if (
                            itemStart >= topSafe - hysteresisPx &&
                            itemEnd <= bottomSafe + hysteresisPx
                        ) {
                            return 0f
                        }

                        // 越界时，按 item 中心与 pivot 对齐
                        val itemCenter = itemStart + size * 0.5f
                        val targetCenter = containerSize * pivotFraction
                        val delta = itemCenter - targetCenter

                        // 死区：避免只差一点点也滚
                        if (abs(delta) <= hysteresisPx) 0f else delta
                    }
                }
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