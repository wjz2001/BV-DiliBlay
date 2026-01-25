package dev.aaa1115910.bv.component.controllers

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import dev.aaa1115910.biliapi.entity.video.VideoShot
import dev.aaa1115910.bv.util.SpriteFrame
import dev.aaa1115910.bv.util.getSpriteFrame
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun VideoShot(
    modifier: Modifier = Modifier,
    videoShot: VideoShot,
    position: Long,
    duration: Long,
    coercedOffset: Dp = 0.dp
) {
    val view = LocalView.current
    val density = LocalDensity.current

    var spriteFrame by remember { mutableStateOf<SpriteFrame?>(null) }
    var imageWidth by remember { mutableStateOf(0.dp) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val screenWidth = maxWidth

        // 计算偏移量：仅在数值真正变化时触发重组
        val coercedImageOffset by remember(position, imageWidth, screenWidth, duration) {
            derivedStateOf {
                if (duration <= 0L || imageWidth <= 0.dp) return@derivedStateOf 0.dp
                val progress = position.toFloat() / duration.toFloat()
                val imageOffset = (-imageWidth / 2) + (screenWidth * progress)
                imageOffset.coerceIn(coercedOffset, screenWidth - imageWidth - coercedOffset)
            }
        }

        // 异步获取帧数据：带轻微防抖，防止瞬间滑动产生过多任务
        LaunchedEffect(position) {
            if (view.isInEditMode) return@LaunchedEffect
            delay(16) // 约 60fps 的刷新间隔
            spriteFrame = videoShot.getSpriteFrame(position.toInt() / 1000)
        }

        spriteFrame?.let { frame ->
            VideoShotImage(
                modifier = Modifier
                    .offset(x = coercedImageOffset)
                    .onSizeChanged {
                        imageWidth = with(density) { it.width.toDp() }
                    },
                spriteFrame = frame
            )
        }
    }
}

@Composable
fun VideoShotImage(
    modifier: Modifier = Modifier,
    spriteFrame: SpriteFrame
) {
    val view = LocalView.current

    // 计算纵横比：确保预览框比例正确
    val aspectRatio = spriteFrame.srcRect.width.toFloat() / spriteFrame.srcRect.height

    Spacer(
        modifier = modifier
            .height(100.dp)
            .aspectRatio(aspectRatio)
            .shadow(4.dp, MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .drawWithCache {
                onDrawBehind {
                    // 直接绘制大图的局部区域到画布，零像素拷贝
                    drawImage(
                        image = spriteFrame.spriteSheet,
                        srcOffset = spriteFrame.srcRect.topLeft,
                        srcSize = spriteFrame.srcRect.size,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        filterQuality = FilterQuality.Low
                    )

                    if (view.isInEditMode) {
                        drawLine(Color.White, Offset(center.x, 0f), Offset(center.y, size.height), 2f)
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
                position = data.second,
                duration = data.first
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