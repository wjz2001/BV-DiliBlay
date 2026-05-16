package dev.aaa1115910.bv.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp

typealias TabPosition = DpRect

enum class IndicatorAlignment {
    Start,
    Center,
    End
}

sealed class AnimatedUnderlineIndicatorWidth {
    data class Fixed(val value: Dp) : AnimatedUnderlineIndicatorWidth()
    object MatchTabWidth : AnimatedUnderlineIndicatorWidth()
    data class WidthFraction(val value: Float) : AnimatedUnderlineIndicatorWidth()
}

@Composable
internal fun AnimatedUnderlineIndicator(
    selectedIndex: Int,
    tabPositions: List<TabPosition>,
    color: Color,
    width: AnimatedUnderlineIndicatorWidth,
    height: Dp,
    offsetAnimationSpec: AnimationSpec<Dp>,
    widthAnimationSpec: AnimationSpec<Dp>,
    rounded: Boolean,
    alignment: IndicatorAlignment,
    verticalOffset: Dp,
    modifier: Modifier,
    visible: Boolean
) {
    if (!visible) return

    val tabPosition = tabPositions.getOrNull(selectedIndex) ?: return
    val tabWidth = tabPosition.right - tabPosition.left
    val targetWidth = when (width) {
        is AnimatedUnderlineIndicatorWidth.Fixed -> width.value
        AnimatedUnderlineIndicatorWidth.MatchTabWidth -> tabWidth
        is AnimatedUnderlineIndicatorWidth.WidthFraction -> tabWidth * width.value
    }
    val targetOffset = when (alignment) {
        IndicatorAlignment.Start -> tabPosition.left
        IndicatorAlignment.Center -> tabPosition.left + (tabWidth - targetWidth) / 2
        IndicatorAlignment.End -> tabPosition.right - targetWidth
    }
    val animatedOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = offsetAnimationSpec,
        label = "underlineIndicatorOffset"
    )
    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = widthAnimationSpec,
        label = "underlineIndicatorWidth"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Spacer(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = animatedOffset, y = verticalOffset)
                .width(animatedWidth)
                .height(height)
                .background(
                    color = color,
                    shape = if (rounded) RoundedCornerShape(percent = 50) else RectangleShape
                )
        )
    }
}

@Composable
internal fun SettingsBottomIndicator(
    modifier: Modifier,
    animatedSelected: Boolean,
    fixedSelected: Boolean,
    color: Color
): Modifier = modifier.animatedUnderlineIndicator(
    animatedSelected = animatedSelected,
    fixedSelected = fixedSelected,
    color = color,
    width = AnimatedUnderlineIndicatorWidth.WidthFraction(1f),
    height = 1.dp,
    enterAnimationSpec = tween(240),
    exitAnimationSpec = tween(160),
    rounded = false,
    alignment = IndicatorAlignment.Center,
    verticalOffset = 0.dp,
    visible = true
)

@Composable
internal fun Modifier.sidebarFocusUnderlineIndicator(
    animatedSelected: Boolean,
    fixedSelected: Boolean = false,
    color: Color
): Modifier = this.animatedUnderlineIndicator(
    animatedSelected = animatedSelected,
    fixedSelected = fixedSelected,
    color = color,
    width = AnimatedUnderlineIndicatorWidth.WidthFraction(0.76f),
    height = 3.dp,
    enterAnimationSpec = tween(220),
    exitAnimationSpec = tween(140),
    rounded = true,
    alignment = IndicatorAlignment.Center,
    verticalOffset = 0.dp,
    visible = true
)

private fun Modifier.animatedUnderlineIndicator(
    animatedSelected: Boolean,
    fixedSelected: Boolean,
    color: Color,
    width: AnimatedUnderlineIndicatorWidth,
    height: Dp,
    enterAnimationSpec: AnimationSpec<Float>,
    exitAnimationSpec: AnimationSpec<Float>,
    rounded: Boolean,
    alignment: IndicatorAlignment,
    verticalOffset: Dp,
    visible: Boolean
): Modifier = composed {
    val progress = remember { Animatable(if (animatedSelected || fixedSelected) 1f else 0f) }

    LaunchedEffect(animatedSelected, fixedSelected, visible) {
        when {
            !visible -> progress.snapTo(0f)
            fixedSelected -> progress.snapTo(1f)
            animatedSelected -> progress.animateTo(1f, animationSpec = enterAnimationSpec)
            else -> progress.animateTo(0f, animationSpec = exitAnimationSpec)
        }
    }

    drawWithContent {
        drawContent()
        if (progress.value <= 0f) return@drawWithContent

        val heightPx = height.toPx()
        val parentWidth = size.width
        val targetWidth = when (width) {
            is AnimatedUnderlineIndicatorWidth.Fixed -> width.value.toPx()
            AnimatedUnderlineIndicatorWidth.MatchTabWidth -> parentWidth
            is AnimatedUnderlineIndicatorWidth.WidthFraction -> parentWidth * width.value
        }
        val indicatorWidth = targetWidth * progress.value
        val x = when (alignment) {
            IndicatorAlignment.Start -> 0f
            IndicatorAlignment.Center -> (parentWidth - indicatorWidth) / 2f
            IndicatorAlignment.End -> parentWidth - indicatorWidth
        }
        val topLeft = Offset(
            x = x,
            y = size.height - heightPx + verticalOffset.toPx()
        )
        val indicatorSize = Size(width = indicatorWidth, height = heightPx)

        if (rounded) {
            val radius = heightPx / 2f
            drawRoundRect(
                color = color,
                topLeft = topLeft,
                size = indicatorSize,
                cornerRadius = CornerRadius(radius, radius)
            )
        } else {
            drawRect(
                color = color,
                topLeft = topLeft,
                size = indicatorSize
            )
        }
    }
}
