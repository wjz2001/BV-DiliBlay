package dev.aaa1115910.bv.component.controllers2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.ui.theme.BVTheme

@Composable
fun VideoProgressSeek(
    modifier: Modifier = Modifier,
    duration: Long,
    position: Long,
    bufferedPercentage: Int,
) {
    Seek(
        modifier = modifier,
        duration = duration,
        position = position,
        bufferedPercentage = bufferedPercentage,
    )
}

@Composable
private fun Seek(
    modifier: Modifier = Modifier,
    duration: Long,
    position: Long,
    bufferedPercentage: Int,
    colors: SliderColors = SliderDefaults.colors(),
) {

    val trackWidth = 12f
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(trackWidth.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        drawLine(
            color = colors.inactiveTrackColor,
            start = Offset(trackWidth / 2, center.y),
            end = Offset(size.width - trackWidth / 2, center.y),
            strokeWidth = trackWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = colors.disabledActiveTrackColor,
            start = Offset(trackWidth / 2, center.y),
            end = Offset(size.width * bufferedPercentage / 100, center.y),
            strokeWidth = trackWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = colors.activeTrackColor,
            start = Offset(trackWidth / 2, center.y),
            end = Offset(size.width * (position / duration.toFloat()), center.y),
            strokeWidth = trackWidth,
            cap = StrokeCap.Round
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SeekPreview() {
    BVTheme {
        Seek(
            duration = 1000,
            position = 300,
            bufferedPercentage = 50
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SeekWithThumbPreview(@PreviewParameter(ProgressProvider::class) data: Triple<Long, Long, Int>) {
    BVTheme {
        Seek(
            duration = data.first,
            position = data.second,
            bufferedPercentage = data.third,
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