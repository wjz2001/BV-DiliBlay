package dev.aaa1115910.bv.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.kuaishou.akdanmaku.ui.DanmakuPlayer
import com.kuaishou.akdanmaku.ui.DanmakuView

@Composable
fun DanmakuPlayerCompose(
    modifier: Modifier = Modifier,
    danmakuPlayer: DanmakuPlayer?,
    onPlayerBoundStateChanged: (DanmakuPlayer?) -> Unit = {}
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            DanmakuView(context)
        },
        update = { danmakuView ->
            when {
                danmakuPlayer == null -> {
                    danmakuView.danmakuPlayer = null
                    onPlayerBoundStateChanged(null)
                }

                danmakuView.danmakuPlayer !== danmakuPlayer -> {
                    danmakuView.danmakuPlayer = null
                    danmakuPlayer.bindView(danmakuView)
                    onPlayerBoundStateChanged(danmakuPlayer)
                }

                else -> {
                    onPlayerBoundStateChanged(danmakuPlayer)
                }
            }
        },
        onRelease = { danmakuView ->
            danmakuView.danmakuPlayer = null
            onPlayerBoundStateChanged(null)
        }
    )
}