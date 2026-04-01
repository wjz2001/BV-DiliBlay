package dev.aaa1115910.bv.player

import android.util.Log
import androidx.annotation.OptIn
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
        var reapplyPostScheduled by remember(videoPlayer) { mutableStateOf(false) }

        // 控制当前是否允许把 Player 重新挂到 PlayerView 上
        // 避免 onPause/onStop 已经解绑后，又因为一次重组被 update 重新绑回去
        var shouldAttachPlayer by remember(lifecycleOwner) {
            mutableStateOf(
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            )
        }

        fun bindPlayerAndRefreshEffects(view: PlayerView) {
            val targetPlayer = if (shouldAttachPlayer) videoPlayer.mPlayer else null
            val didRebind = view.player !== targetPlayer
            val hasPendingReapply = targetPlayer != null && videoPlayer.hasPendingVideoEffectsReapply()
            val shouldPostReapply = targetPlayer != null && (didRebind || hasPendingReapply)

            if (didRebind) {
                Log.d(
                    "BvVideoPlayer",
                    "bindPlayer: target=${targetPlayer != null}, " +
                            "attached=${view.isAttachedToWindow}, size=${view.width}x${view.height}"
                )
                view.player = targetPlayer
            }

            if (shouldPostReapply && !reapplyPostScheduled) {
                reapplyPostScheduled = true

                view.post {
                    reapplyPostScheduled = false

                    if (!view.isAttachedToWindow) return@post

                    val latestTarget = if (shouldAttachPlayer) videoPlayer.mPlayer else null
                    if (view.player !== latestTarget) {
                        view.player = latestTarget
                    }

                    if (latestTarget != null && view.player === latestTarget) {
                        Log.d(
                            "BvVideoPlayer",
                            "reapply effects: didRebind=$didRebind pending=$hasPendingReapply, " +
                                    "attached=${view.isAttachedToWindow}, size=${view.width}x${view.height}"
                        )
                        videoPlayer.reapplyVideoEffectsAfterViewBound()
                    }
                }
            }
        }

        DisposableEffect(lifecycleOwner, videoPlayer) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE,
                    Lifecycle.Event.ON_STOP -> {
                        shouldAttachPlayer = false
                        reapplyPostScheduled = false
                        Log.d("BvVideoPlayer", "detach player on $event")
                        runCatching { playerView?.player = null }
                    }

                    Lifecycle.Event.ON_START,
                    Lifecycle.Event.ON_RESUME -> {
                        shouldAttachPlayer = true
                        Log.d("BvVideoPlayer", "attach player on $event")
                        playerView?.let(::bindPlayerAndRefreshEffects)
                    }

                    else -> Unit
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                shouldAttachPlayer = false
                reapplyPostScheduled = false
                runCatching { playerView?.player = null }
                playerView = null
            }
        }

        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                PlayerView(ctx).apply {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    useController = false
                    setEnableComposeSurfaceSyncWorkaround(true)

                    // 不要在这里直接裸绑后就结束
                    player = null
                    playerView = this

                    bindPlayerAndRefreshEffects(this)
                }
            },
            update = { view ->
                if (playerView !== view) {
                    playerView = view
                }

                // 不裁切画面，只做最大内接显示
                if (view.resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }

                view.setEnableComposeSurfaceSyncWorkaround(true)

                bindPlayerAndRefreshEffects(view)
            },
            onRelease = { view ->
                reapplyPostScheduled = false
                runCatching { view.player = null }
                if (playerView === view) {
                    playerView = null
                }
            }
        )
    }
}