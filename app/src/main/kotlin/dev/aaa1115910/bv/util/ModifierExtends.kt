package dev.aaa1115910.bv.util
import dev.aaa1115910.bv.ui.theme.C
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.material3.ShapeDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import dev.aaa1115910.biliapi.entity.danmaku.DanmakuMaskFrame
import dev.aaa1115910.biliapi.entity.danmaku.DanmakuMobMaskFrame
import dev.aaa1115910.biliapi.entity.danmaku.DanmakuWebMaskFrame


/**
 * 获取到焦点时显示边框
 */
fun Modifier.focusedBorder(
    shape: Shape = ShapeDefaults.Large,
    animate: Boolean = false
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "infinite border color transition")
    var hasFocus by remember { mutableStateOf(false) }
    val selectedBorder = C.selectedBorder

    val animateColor by infiniteTransition.animateColor(
        initialValue = selectedBorder,
        targetValue = selectedBorder.copy(alpha = 0.1f),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "focused border animate color"
    )
    val borderColor = if (hasFocus) {
        if (animate) animateColor else selectedBorder
    } else Color.Transparent

    onFocusChanged { hasFocus = it.hasFocus }
        .border(
            width = 3.dp,
            color = borderColor,
            shape = shape
        )
}

/**
 * 在没有获取到焦点的时候缩小，以便在获取到焦点的时候“放大”
 */
fun Modifier.focusedScale(
    scale: Float = 0.9f
): Modifier = composed {
    var hasFocus by remember { mutableStateOf(false) }
    val scaleValue by animateFloatAsState(
        targetValue = if (hasFocus) 1f else scale,
        label = "focused scale"
    )

    onFocusChanged { hasFocus = it.hasFocus }
        .scale(scaleValue)
}

fun Modifier.bitmapMask(
    bitmap: Bitmap,
    videoAspectRatio: Float, // 视频的宽高比 (例如 1920/1080 ≈ 1.77, 21/9 ≈ 2.33)
): Modifier = composed {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    drawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            drawContent()

            val screenWidth = size.width
            val screenHeight = size.height
            val screenAspectRatio = screenWidth / screenHeight

            val dstWidth: Float
            val dstHeight: Float
            val offsetX: Float
            val offsetY: Float

            if (videoAspectRatio > screenAspectRatio) {
                dstWidth = screenWidth
                dstHeight = dstWidth / videoAspectRatio

                offsetX = 0f
                offsetY = (screenHeight - dstHeight) / 2f
            } else {
                dstHeight = screenHeight
                dstWidth = dstHeight * videoAspectRatio

                offsetY = 0f
                offsetX = (screenWidth - dstWidth) / 2f
            }

            drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                dstSize = IntSize(dstWidth.toInt(), dstHeight.toInt()),
                blendMode = BlendMode.DstIn
            )

            canvas.restore()
        }
    }
}

fun Modifier.danmakuWebMask(
    frame: DanmakuWebMaskFrame,
    aspectRatio: Float,
): Modifier = composed {
    // remember(frame) 保证 SVG 解析和 Bitmap 创建只在帧变化时执行一次
    val bitmap = remember(frame) {
        val svgObj = runCatching { SVG.getFromString(frame.svg) }.getOrNull()
            ?: return@remember null

        val svgWidth = svgObj.documentWidth.toInt().coerceAtLeast(1)
        val svgHeight = svgObj.documentHeight.toInt().coerceAtLeast(1)

        val bmp = Bitmap.createBitmap(svgWidth, svgHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        svgObj.renderToCanvas(canvas)
        bmp
    } ?: return@composed this

    bitmapMask(bitmap, aspectRatio)
}

fun Modifier.danmakuMobMask(
    frame: DanmakuMobMaskFrame,
    aspectRatio: Float,
): Modifier = composed {
    // remember(frame) 保证像素解码和 Bitmap 创建只在帧变化时执行一次，
    // 避免每次 recompose 都重建 Bitmap（对长视频是显著的内存和 CPU 压力）
    val bitmap = remember(frame) {
        val width = frame.width
        val height = frame.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // 1bpp 连续 bit 流，MSB first
        val pixels = IntArray(width * height) { i ->
            val byteIndex = i / 8
            val bitOffset = 7 - (i % 8)
            val bit = (frame.image[byteIndex].toInt() shr bitOffset) and 1
            if (bit == 1) android.graphics.Color.TRANSPARENT else android.graphics.Color.BLACK
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        bmp
    }

    bitmapMask(bitmap, aspectRatio)
}

fun Modifier.danmakuMask(
    frame: DanmakuMaskFrame?,
    aspectRatio: Float,
): Modifier = composed {
    if (frame == null) return@composed this

    when (frame) {
        is DanmakuWebMaskFrame -> danmakuWebMask(frame, aspectRatio)
        is DanmakuMobMaskFrame -> danmakuMobMask(frame, aspectRatio)
    }
}
