package dev.aaa1115910.bv.component.controllers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import dev.aaa1115910.biliapi.entity.video.VideoShot
import dev.aaa1115910.bv.entity.VideoFlip
import dev.aaa1115910.bv.entity.VideoRotation
import dev.aaa1115910.bv.ui.theme.AppWhite
import dev.aaa1115910.bv.util.SpriteFrame
import dev.aaa1115910.bv.util.VideoShotImageCache
import dev.aaa1115910.bv.util.getSpriteFrame
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun VideoShot(
    modifier: Modifier = Modifier,
    videoShot: VideoShot,
    imageCache: VideoShotImageCache,
    position: Long,
    duration: Long,
    coercedOffset: Dp = 0.dp,
    videoRotation: VideoRotation? = null,
    videoFlip: VideoFlip? = null
) {
    val view = LocalView.current
    var spriteFrame by remember { mutableStateOf<SpriteFrame?>(null) }

    Box(modifier = modifier.fillMaxWidth()) {
        LaunchedEffect(position) {
            if (view.isInEditMode) return@LaunchedEffect
            delay(16)
            spriteFrame = videoShot.getSpriteFrame(position.toInt() / 1000, imageCache)
        }

        spriteFrame?.let { frame ->
            VideoShotImage(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)

                        val containerWidthPx = constraints.maxWidth
                        val imageWidthPx = placeable.width
                        val coercedOffsetPx = coercedOffset.roundToPx()

                        val xPosition = if (duration <= 0L) {
                            0
                        } else {
                            val progress = position.toDouble() / duration.toDouble()
                            val rawOffset = (-imageWidthPx / 2.0) + (containerWidthPx * progress)

                            val minOffset = coercedOffsetPx.toDouble()
                            val maxOffset =
                                (containerWidthPx - imageWidthPx - coercedOffsetPx).toDouble()

                            rawOffset.coerceIn(minOffset, maxOffset).toInt()
                        }

                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(x = xPosition, y = 0)
                        }
                    },
                spriteFrame = frame,
                videoRotation = videoRotation,
                videoFlip = videoFlip
            )
        }
    }
}

@Composable
fun VideoShotImage(
    modifier: Modifier = Modifier,
    spriteFrame: SpriteFrame,
    videoRotation: VideoRotation? = null,
    videoFlip: VideoFlip? = null
) {
    val view = LocalView.current

    val baseAspectRatio = spriteFrame.srcRect.width.toFloat() / spriteFrame.srcRect.height
    val isQuarterTurn = videoRotation?.isQuarterTurn == true
    val aspectRatio = if (isQuarterTurn) 1f / baseAspectRatio else baseAspectRatio

    val rotationDegrees = -(videoRotation?.effectDegreesCounterClockwise ?: 0f)
    val flipScaleX = videoFlip?.scaleX ?: 1f
    val flipScaleY = videoFlip?.scaleY ?: 1f

    Spacer(
        modifier = modifier
            .height(100.dp)
            .aspectRatio(aspectRatio)
            .shadow(4.dp, MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .drawWithCache {
                val dstWidth = if (isQuarterTurn) size.height.roundToInt() else size.width.roundToInt()
                val dstHeight = if (isQuarterTurn) size.width.roundToInt() else size.height.roundToInt()

                onDrawBehind {
                    withTransform({
                        translate(left = center.x, top = center.y)
                        rotate(rotationDegrees)
                        scale(scaleX = flipScaleX, scaleY = flipScaleY)
                        translate(
                            left = -dstWidth / 2f,
                            top = -dstHeight / 2f
                        )
                    }) {
                        drawImage(
                            image = spriteFrame.spriteSheet,
                            srcOffset = spriteFrame.srcRect.topLeft,
                            srcSize = spriteFrame.srcRect.size,
                            dstSize = IntSize(dstWidth, dstHeight),
                            filterQuality = FilterQuality.Low
                        )
                    }

                    if (view.isInEditMode) {
                        drawLine(
                            AppWhite,
                            Offset(center.x, 0f),
                            Offset(center.x, size.height),
                            2f
                        )
                    }
                }
            }
    )
}

@Preview(device = "id:tv_1080p")
@Composable
private fun VideoShotPreview(@PreviewParameter(VideoShotProgressProvider::class) data: Pair<Long, Long>) {
    MaterialTheme {
        Column {
            VideoShot(
                videoShot = VideoShot(
                    times = emptyList(),
                    imageCountX = 0,
                    imageCountY = 0,
                    imageWidth = 0,
                    imageHeight = 0,
                    images = emptyList()
                ),
                imageCache = VideoShotImageCache(),
                position = data.second,
                duration = data.first,
                videoRotation = null,
                videoFlip = null
            )
            VideoProgressSeek(
                duration = data.first,
                position = data.second,
                bufferedPercentage = 1,
                isPersistentSeek = false
            )
        }
    }
}

private class VideoShotProgressProvider : PreviewParameterProvider<Pair<Long, Long>> {
    override val values = sequenceOf(
        Pair(1234_000L, 0L),
        Pair(1234_000L, 234_000L),
        Pair(1234_000L, 555_000L),
        Pair(1234_000L, 1234_000L)
    )
}
