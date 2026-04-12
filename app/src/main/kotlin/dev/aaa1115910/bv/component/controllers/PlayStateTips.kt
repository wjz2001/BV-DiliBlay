package dev.aaa1115910.bv.component.controllers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.C


@Composable
fun PlayStateTips(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isError: Boolean,
    errorMessage: String? = null
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (!isPlaying && !isBuffering && !isError) {
            PauseIcon(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            )
        }
        if (isBuffering && !isError) {
            BufferingTip(
                modifier = Modifier
                    .align(Alignment.Center),
                speed = ""
            )
        }
        if (isError) {
            PlayErrorTip(
                modifier = Modifier.align(Alignment.Center),
                errorMessage = errorMessage
            )
        }
    }
}

@Composable
fun PauseIcon(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        colors = SurfaceDefaults.colors(
            containerColor = C.scrim,
            contentColor = C.onScrim
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(
            modifier = Modifier
                .padding(12.dp, 4.dp)
                .size(50.dp),
            imageVector = Icons.Rounded.Pause,
            contentDescription = null,
            tint = C.onScrim
        )
    }
}

@Composable
fun BufferingTip(
    modifier: Modifier = Modifier,
    speed: String
) {
    Surface(
        modifier = modifier,
        colors = SurfaceDefaults.colors(
            containerColor = C.scrim,
            contentColor = C.onScrim
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(36.dp)
                    .padding(8.dp),
                color = C.onScrim,
                strokeWidth = 2.dp
            )
            Text(
                modifier = Modifier,
                text = "缓冲中...$speed",
                fontSize = 24.sp
            )
        }
    }
}

@Composable
fun PlayErrorTip(
    modifier: Modifier = Modifier,
    errorMessage: String?
) {
    Surface(
        modifier = modifier,
        colors = SurfaceDefaults.colors(
            containerColor = C.scrim,
            contentColor = C.onScrim
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp, 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "播放器正在抽风",
                style = MaterialTheme.typography.titleLarge
            )
            Text(text = " _(:з」∠)_")
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "错误信息：${errorMessage ?: "未知错误"}")
        }
    }
}

@Preview
@Composable
private fun PauseIconPreview() {
    BVTheme {
        Box(modifier = Modifier.padding(10.dp)) {
            PauseIcon()
        }
    }
}

@Preview
@Composable
private fun BufferingTipPreview() {
    BVTheme {
        BufferingTip(
            modifier = Modifier.padding(10.dp),
            speed = ""
        )
    }
}

@Preview
@Composable
private fun PlayErrorTipPreview() {
    BVTheme {
        PlayErrorTip(errorMessage = "This is a test error.")
    }
}
