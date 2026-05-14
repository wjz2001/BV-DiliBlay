package dev.aaa1115910.bv.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DividerDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun HorizontalDashedDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.color,

    dashLength: Dp = thickness,                    // 线段沿主轴长度
    gapLength: Dp = thickness,                     // 空白沿主轴长度
    roundCaps: Boolean = false,                    // 每段线两端是否半圆
    phase: Dp = 0.dp,                              // 图案整体沿主轴偏移（可动画）
) {
    val density = LocalDensity.current

    val (strokePx, pathEffect, cap) = remember(
        thickness, dashLength, gapLength, roundCaps, phase, density
    ) {
        with(density) {
            val stroke = thickness.toPx() // 可能为 0（Hairline）
            val strokeForMath = if (stroke == 0f) 1f else stroke

            val dashPx = dashLength.toPx()
            val gapPx = gapLength.toPx()
            val phasePx = phase.toPx()

            val capValue = if (roundCaps) StrokeCap.Round else StrokeCap.Butt

            // Round cap 会让每段 dash 视觉上“变长”（两端各伸出半径）
            // 为了让 dashLength/gapLength 更接近“视觉长度”，做一个补偿：
            val capExtra = if (roundCaps) strokeForMath else 0f
            val on = max(0f, dashPx - capExtra)
            val off = max(0f, gapPx + capExtra)

            val effect =
                if (on == 0f && off == 0f) null
                else PathEffect.dashPathEffect(floatArrayOf(on, off), phasePx)

            Triple(stroke, effect, capValue)
        }
    }

    Canvas(modifier.fillMaxWidth().height(thickness)) {
        val y = strokePx / 2f
        drawLine(
            color = color,
            strokeWidth = strokePx,
            cap = cap,
            pathEffect = pathEffect,
            start = Offset(0f, y),
            end = Offset(size.width, y),
        )
    }
}

@Composable
fun VerticalDashedDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.color,

    dashLength: Dp = thickness,                    // 线段沿主轴长度
    gapLength: Dp = thickness,                     // 空白沿主轴长度
    roundCaps: Boolean = false,                    // 每段线两端是否半圆
    phase: Dp = 0.dp,                              // 图案整体沿主轴偏移（可动画）
) {
    val density = LocalDensity.current

    val (strokePx, pathEffect, cap) = remember(
        thickness, dashLength, gapLength, roundCaps, phase, density
    ) {
        with(density) {
            val stroke = thickness.toPx() // 可能为 0（Hairline）
            val strokeForMath = if (stroke == 0f) 1f else stroke

            val dashPx = dashLength.toPx()
            val gapPx = gapLength.toPx()
            val phasePx = phase.toPx()

            val capValue = if (roundCaps) StrokeCap.Round else StrokeCap.Butt

            // Round cap 会让每段 dash 视觉上“变长”（两端各伸出半径）
            // 为了让 dashLength/gapLength 更接近“视觉长度”，做一个补偿：
            val capExtra = if (roundCaps) strokeForMath else 0f
            val on = max(0f, dashPx - capExtra)
            val off = max(0f, gapPx + capExtra)

            val effect =
                if (on == 0f && off == 0f) null
                else PathEffect.dashPathEffect(floatArrayOf(on, off), phasePx)

            Triple(stroke, effect, capValue)
        }
    }

    Canvas(modifier.fillMaxHeight().width(thickness)) {
        val x = strokePx / 2f
        drawLine(
            color = color,
            strokeWidth = strokePx,
            cap = cap,
            pathEffect = pathEffect,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
        )
    }
}

/**
 * 圆点虚线（横向），圆点直径 = 空白长度（视觉意义）
 */
@Composable
fun HorizontalDottedDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.color,
    phase: Dp = 0.dp,
) {
    // 处理 thickness = Dp.Hairline：它本身“值接近 0dp”，但视觉是 1px。
    // 空白也应是 1px，所以这里把 gapLength 映射成 1px 对应的 dp。
    val gap = if (thickness == Dp.Hairline) {
        with(LocalDensity.current) { (1f / density).dp }
    } else {
        thickness
    }

    HorizontalDashedDivider(
        modifier = modifier,
        thickness = thickness,
        color = color,
        dashLength = 0.dp,      // 让每段 dash 退化成“点”（靠 round cap）
        gapLength = gap,        // 空白=点直径
        roundCaps = true,
        phase = phase,
    )
}

/**
 * 圆点虚线（竖向），圆点直径 = 空白长度（视觉意义）
 */
@Composable
fun VerticalDottedDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.color,
    phase: Dp = 0.dp,
) {
    val gap = if (thickness == Dp.Hairline) {
        with(LocalDensity.current) { (1f / density).dp }
    } else {
        thickness
    }

    VerticalDashedDivider(
        modifier = modifier,
        thickness = thickness,
        color = color,
        dashLength = 0.dp,
        gapLength = gap,
        roundCaps = true,
        phase = phase,
    )
}

/** 小工具：缓存并返回 */
private data class DashParams(
    val strokeWidthPx: Float,
    val center: Float,
    val effect: PathEffect?,
    val cap: StrokeCap,
)