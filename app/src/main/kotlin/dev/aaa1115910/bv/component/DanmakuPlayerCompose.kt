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
    danmakuPlayer: DanmakuPlayer?
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            DanmakuView(context)
        },
        update = { danmakuView ->
            danmakuPlayer?.bindView(danmakuView)
        },
        onRelease = { danmakuView ->
            if (danmakuView.danmakuPlayer === danmakuPlayer) {
                danmakuView.danmakuPlayer = null
            }
        }
    )
}