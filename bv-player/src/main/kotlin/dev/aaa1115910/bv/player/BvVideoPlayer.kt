package dev.aaa1115910.bv.player

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.aaa1115910.bv.player.impl.exo.ExoMediaPlayer

@OptIn(UnstableApi::class)
@Composable
fun BvVideoPlayer(
    modifier: Modifier = Modifier,
    videoPlayer: AbstractVideoPlayer?,
) {
    if (videoPlayer is ExoMediaPlayer) {
        val lifecycleOwner = LocalLifecycleOwner.current
        var playerView: PlayerView? by remember { mutableStateOf(null) }

        // 控制当前是否允许把 Player 重新挂到 PlayerView 上
        // 避免 onPause/onStop 已经解绑后，又因为一次重组被 update 重新绑回去
        var shouldAttachPlayer by remember(lifecycleOwner) {
            mutableStateOf(
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            )
        }

        // UI 离开时只解绑 View <-> ExoPlayer，不要 release 播放器实例
        // 尽量在 onPause 就先解绑 surface，保证早于 Activity.onStop 的 release
        DisposableEffect(lifecycleOwner, videoPlayer) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE,
                    Lifecycle.Event.ON_STOP -> {
                        shouldAttachPlayer = false
                        runCatching { playerView?.player = null }
                    }

                    Lifecycle.Event.ON_START,
                    Lifecycle.Event.ON_RESUME -> {
                        shouldAttachPlayer = true
                        val target = videoPlayer.mPlayer
                        if (playerView?.player !== target) {
                            runCatching { playerView?.player = target }
                        }
                    }

                    else -> Unit
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                shouldAttachPlayer = false
                runCatching { playerView?.player = null }
                playerView = null
            }
        }

        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    useController = false
                    player = if (shouldAttachPlayer) videoPlayer.mPlayer else null
                    playerView = this
                }
            },
            update = { view ->
                if (playerView !== view) {
                    playerView = view
                }

                // 当 ViewModel 里 initPlayer() 重建了新的 mPlayer 后，这里必须重新绑定
                val targetPlayer = if (shouldAttachPlayer) videoPlayer.mPlayer else null
                if (view.player !== targetPlayer) {
                    view.player = targetPlayer
                }
            },
            onRelease = { view ->
                // AndroidView 真正释放时做最终解绑
                runCatching { view.player = null }
                if (playerView === view) {
                    playerView = null
                }
            }
        )
    }
}