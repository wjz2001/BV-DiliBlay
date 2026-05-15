package dev.aaa1115910.bv.component.videocard

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aaa1115910.bv.component.VerticalDashedDivider
import dev.aaa1115910.bv.ui.theme.C

@Composable
fun VideoPartButton(
    modifier: Modifier = Modifier,
    index: Int,
    title: String,
    duration: Int,
    played: Int = 0,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val surfaceVariant = C.surfaceVariant
    val defaultBackgroundBrush = remember(surfaceVariant) {
        Brush.horizontalGradient(
            colors = listOf(
                surfaceVariant.copy(alpha = 0.9f),
                surfaceVariant.copy(alpha = 1f)
            )
        )
    }
    val progressColor = when {
        isPressed -> C.primaryContainer
        isFocused -> C.secondaryContainer
        else -> C.inverseSurface
    }
    val backgroundColor = when {
        isPressed -> C.secondaryContainer
        isFocused -> C.tertiaryContainer
        else -> Color.Transparent
    }
    val textColor = C.onSurface

    Surface(
        modifier = modifier
            .height(96.dp)
            .graphicsLayer {
                val pressedScale = if (isPressed) 0.9f else 1f
                scaleX = pressedScale
                scaleY = pressedScale
            }
            .onFocusChanged { isFocused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = C.onSurface,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = C.onSurface,
            pressedContainerColor = Color.Transparent,
            pressedContentColor = C.onSurface
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RectangleShape),
        interactionSource = interactionSource,
        onClick = { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .videoPartButtonBackground(
                    isActive = isFocused || isPressed,
                    isPressed = isPressed,
                    backgroundBrush = defaultBackgroundBrush,
                    backgroundColor = backgroundColor,
                    progressColor = progressColor,
                    progress = if (played < 0) {
                        1f
                    } else if (duration > 0) {
                        (played / duration.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 32.dp)
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .padding(start = 14.dp, end = 8.dp, top = 2.dp),
                    text = "P$index",
                    color = textColor,
                    fontSize = LocalTextStyle.current.fontSize * 1.5,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .align(Alignment.BottomStart)
                        .padding(start = 88.dp, end = 8.dp, top = 2.dp, bottom = 6.dp),
                    text = title,
                    color = textColor,
                    fontSize = LocalTextStyle.current.fontSize * 1.5,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isFocused || isPressed) {
                VerticalDivider(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 32.dp)
                        .height(96.dp),
                    color = textColor
                )
            } else {
                VerticalDashedDivider(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 32.dp)
                        .height(96.dp),
                    color = textColor,
                    dashLength = 6.dp,
                    gapLength = 4.dp
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(32.dp)
                    .height(96.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DurationUnitText(
                    modifier = Modifier.weight(1f),
                    duration = duration,
                    unit = "h",
                    fontSize = 22,
                    visible = duration >= 3600,
                    color = textColor
                )
                DurationUnitText(
                    modifier = Modifier.weight(1f),
                    duration = duration,
                    unit = "m",
                    fontSize = 21,
                    visible = duration >= 60,
                    color = textColor
                )
                DurationUnitText(
                    modifier = Modifier.weight(1f),
                    duration = duration,
                    unit = "s",
                    fontSize = 20,
                    visible = true,
                    color = textColor
                )
            }
        }
    }
}

private fun Modifier.videoPartButtonBackground(
    isActive: Boolean,
    isPressed: Boolean,
    backgroundBrush: Brush,
    backgroundColor: Color,
    progressColor: Color,
    progress: Float
): Modifier = drawWithCache {
    onDrawWithContent {
        if (isActive) {
            drawRect(color = backgroundColor)
        } else {
            drawRect(brush = backgroundBrush)
        }
        drawRect(
            color = progressColor,
            size = Size(
                width = size.width * progress,
                height = size.height
            )
        )

        drawContent()

        if (isPressed) {
            drawRect(color = Color.Black.copy(alpha = 0.12f))
        }
    }
}

@Composable
private fun DurationUnitText(
    modifier: Modifier = Modifier,
    duration: Int,
    unit: String,
    fontSize: Int,
    visible: Boolean,
    color: Color
) {
    val value = when (unit.lowercase()) {
        "h" -> duration / 3600
        "m" -> (duration % 3600) / 60
        "s" -> duration % 60
        else -> 0L
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (visible) {
            Text(
                text = value.toString().padStart(2, '0'),
                color = color,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
