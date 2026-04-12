package dev.aaa1115910.bv.component.controllers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.ui.theme.AppRed
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

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(trackWidthDp)
    ) {
        val trackWidthPx = trackWidthDp.toPx()

        drawLine(
            // inactiveTrackColor
            color = AppRedLighter,
            start = Offset(trackWidthPx / 2, center.y),
            end = Offset(size.width - trackWidthPx / 2, center.y),
            strokeWidth = trackWidthPx,
            cap = StrokeCap.Square
        )
        if (!isPersistentSeek) {
            drawLine(
                // disabledActiveTrackColor
                color = AppRedLight,
                start = Offset(trackWidthPx / 2, center.y),
                end = Offset(trackWidthPx / 2 + size.width * bufferedPercentage / 100, center.y),
                strokeWidth = trackWidthPx,
                cap = StrokeCap.Square
            )
        }
        drawLine(
            // activeTrackColor
            color = AppRed,
            start = Offset(trackWidthPx / 2, center.y),
            end = Offset(trackWidthPx / 2 + size.width * progress, center.y),
            strokeWidth = trackWidthPx,
            cap = StrokeCap.Square
        )
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
