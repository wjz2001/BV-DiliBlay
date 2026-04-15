package dev.aaa1115910.bv.component.controllers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.ui.theme.AppRed
import dev.aaa1115910.bv.ui.theme.AppRedDark
import dev.aaa1115910.bv.ui.theme.AppRedLight
import dev.aaa1115910.bv.ui.theme.AppRedLighter
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.ThemeMode

@Composable
fun VideoProgressSeek(
    modifier: Modifier = Modifier,
    duration: Long,
    position: Long,
    bufferedPercentage: Int,
    isPersistentSeek: Boolean
) {
    val trackWidthDp = if (isPersistentSeek) 4.dp else 8.dp
    val progress = if (duration > 0) {
        (position / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val bufferedProgress = (bufferedPercentage / 100f).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(trackWidthDp)
    ) {
        val trackWidthPx = trackWidthDp.toPx()
        val playedEndX = size.width * progress
        val bufferedEndX = size.width * bufferedProgress

        fun drawSegment(startX: Float, endX: Float, color: Color) {
            if (endX <= startX) return
            drawLine(
                color = color,
                start = Offset(startX, center.y),
                end = Offset(endX, center.y),
                strokeWidth = trackWidthPx,
                cap = StrokeCap.Butt
            )
        }

        if (isPersistentSeek || bufferedProgress >= 1f) {
            drawSegment(0f, playedEndX, AppRed)
            drawSegment(playedEndX, size.width, AppRedLighter)
            return@Canvas
        }

        val bufferedVisibleEndX = bufferedEndX.coerceAtLeast(playedEndX)
        drawSegment(0f, playedEndX, AppRedDark)
        drawSegment(playedEndX, bufferedVisibleEndX, AppRedLight)
        drawSegment(bufferedVisibleEndX, size.width, AppRedLighter)
    }

}


@Preview(device = "id:tv_1080p")
@Composable
private fun SeekPreview() {
    BVTheme(themeMode = ThemeMode.DARK) {
        VideoProgressSeek(
            duration = 1000,
            position = 300,
            bufferedPercentage = 50,
            isPersistentSeek = true
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SeekWithThumbPreview(@PreviewParameter(ProgressProvider::class) data: Triple<Long, Long, Int>) {
    BVTheme(themeMode = ThemeMode.LIGHT) {
        VideoProgressSeek(
            duration = data.first,
            position = data.second,
            bufferedPercentage = data.third,
            isPersistentSeek = false
        )
    }
}

private class ProgressProvider : PreviewParameterProvider<Triple<Long, Long, Int>> {
    override val values = sequenceOf(
        Triple(1234_000L, 0L, 3),
        Triple(1234_000L, 234_000L, 24),
        Triple(1234_000L, 555_000L, 57),
        Triple(1234_000L, 1234_000L, 100)
    )
}
