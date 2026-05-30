package dev.aaa1115910.bv.component.videocard

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.formatHourMinSec
import dev.aaa1115910.bv.wjzfocus.wjzObserveFocusChanged

enum class VideoPartButtonStyle {
    Muji,
    Poetry
}

@Composable
fun VideoPartButton(
    modifier: Modifier = Modifier,
    index: Int,
    title: String,
    duration: Int,
    played: Int = 0,
    style: VideoPartButtonStyle = VideoPartButtonStyle.Muji,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val durationText = remember(duration) { (duration * 1000L).formatHourMinSec() }

    Surface(
        modifier = modifier
            .height(96.dp)
            .graphicsLayer {
                val pressedScale = if (isPressed) 0.9f else 1f
                scaleX = pressedScale
                scaleY = pressedScale
            }
            .wjzObserveFocusChanged { isFocused = it },
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
                    style = style,
                    progress = if (played < 0) {
                        1f
                    } else if (duration > 0) {
                        (played / duration.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                )
        ) {
            when (style) {
                VideoPartButtonStyle.Muji -> VideoPartButtonMujiContent(
                    index = index,
                    title = title,
                    durationText = durationText
                )
                VideoPartButtonStyle.Poetry -> VideoPartButtonPoetryContent(
                    index = index,
                    title = title,
                    durationText = durationText
                )
            }
        }
    }
}

private fun Modifier.videoPartButtonBackground(
    isActive: Boolean,
    isPressed: Boolean,
    style: VideoPartButtonStyle,
    progress: Float
): Modifier = drawWithCache {
    val backgroundBrush = when (style) {
        VideoPartButtonStyle.Muji -> Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFF5F5F0),
                Color(0xFFF8F8F4)
            )
        )
        VideoPartButtonStyle.Poetry -> Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFCFBF9),
                Color(0xFFF9F7F3)
            )
        )
    }
    val activeBackgroundColor = when (style) {
        VideoPartButtonStyle.Muji -> if (isPressed) Color(0xFFE4E6D9) else Color(0xFFECEDE4)
        VideoPartButtonStyle.Poetry -> if (isPressed) Color(0xFFF0EBE1) else Color(0xFFF5F1EA)
    }
    val progressColor = when (style) {
        VideoPartButtonStyle.Muji -> Color(0xFFE4E6D9)
        VideoPartButtonStyle.Poetry -> Color(0xFFF0EBE1)
    }

    onDrawWithContent {
        if (isActive) {
            drawRect(color = activeBackgroundColor)
        } else {
            drawRect(brush = backgroundBrush)
        }
        drawRect(
            color = progressColor.copy(alpha = 0.48f),
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
private fun VideoPartButtonMujiContent(
    index: Int,
    title: String,
    durationText: String
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 18.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.width(58.dp),
            text = "P$index",
            color = Color(0xFF333333),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            text = title,
            color = Color(0xFF333333),
            fontSize = 28.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .background(Color(0xFFE4E6D9), shape = RectangleShape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = durationText,
                color = Color(0xFF6B705C),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun VideoPartButtonPoetryContent(
    index: Int,
    title: String,
    durationText: String
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 18.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xFFF0EBE1), shape = RectangleShape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            durationText.forEach { char ->
                Text(
                    text = char.toString(),
                    fontSize = 16.sp,
                    color = Color(0xFF9E9485),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.width(22.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 10.dp)
        ) {
            Text(
                text = "P$index",
                color = Color(0xFF9E9485),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
            Text(
                text = title,
                color = Color(0xFF333333),
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
