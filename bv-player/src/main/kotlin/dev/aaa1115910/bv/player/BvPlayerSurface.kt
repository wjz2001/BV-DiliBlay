package dev.aaa1115910.bv.player

import androidx.annotation.OptIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW

@OptIn(UnstableApi::class)
@androidx.compose.runtime.Composable
fun BvPlayerSurface(
    player: Player?,
    modifier: Modifier = Modifier
) {
    ContentFrame(
        player = player,
        modifier = modifier,
        surfaceType = SURFACE_TYPE_SURFACE_VIEW,
        contentScale = ContentScale.Fit,
        keepContentOnReset = true
    )
}
