package dev.aaa1115910.bv.wjzdanmaku.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.aaa1115910.biliapi.entity.danmaku.DanmakuMask
import dev.aaa1115910.bv.wjzdanmaku.DanmakuHostRuntime
import kotlinx.coroutines.flow.Flow

@Composable
fun DanmakuSurface(
    currentTime: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    config: DanmakuConfig,
    mask: DanmakuMask?,
    sourceMode: DanmakuSourceMode,
    modifier: Modifier = Modifier,
    maskEnabled: Boolean = mask != null,
    videoAspectRatio: Float? = null,
    commandFlow: Flow<DanmakuHostCommand>? = null,
    onSessionEvent: (DanmakuSessionEvent) -> Unit = {},
) {
    DanmakuHostRuntime(
        currentTime = currentTime,
        isPlaying = isPlaying,
        playbackSpeed = playbackSpeed,
        config = config,
        mask = mask,
        sourceMode = sourceMode,
        modifier = modifier,
        maskEnabled = maskEnabled,
        videoAspectRatio = videoAspectRatio,
        commandFlow = commandFlow,
        onSessionEvent = onSessionEvent,
    )
}
