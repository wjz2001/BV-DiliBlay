package dev.aaa1115910.bv.component

import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
    val latestOnPlayerBoundStateChanged = rememberUpdatedState(onPlayerBoundStateChanged)
    val bindCoordinator = remember {
        DanmakuViewBindCoordinator(
            onPlayerReady = { player ->
                latestOnPlayerBoundStateChanged.value(player)
            }
        )
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            DanmakuView(context).also { danmakuView ->
                bindCoordinator.attachToView(danmakuView)
            }
        },
        update = { danmakuView ->
            bindCoordinator.attachToView(danmakuView)
            bindCoordinator.onPlayerReady = { player ->
                latestOnPlayerBoundStateChanged.value(player)
            }
            bindCoordinator.bind(danmakuPlayer)
        },
        onRelease = { danmakuView ->
            bindCoordinator.clearReadyState()
            danmakuView.danmakuPlayer = null
            latestOnPlayerBoundStateChanged.value(null)
        }
    )
}

private class DanmakuViewBindCoordinator(
    var onPlayerReady: (DanmakuPlayer?) -> Unit
) {
    private var danmakuView: DanmakuView? = null
    private var currentPlayer: DanmakuPlayer? = null
    private var lastReadyPlayer: DanmakuPlayer? = null

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            notifyIfReady()
        }

        override fun onViewDetachedFromWindow(v: View) = Unit
    }

    private val layoutChangeListener =
        View.OnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            if (right > left && bottom > top) {
                notifyIfReady()
            }
        }

    fun attachToView(view: DanmakuView) {
        if (danmakuView === view) return

        danmakuView?.removeOnAttachStateChangeListener(attachListener)
        danmakuView?.removeOnLayoutChangeListener(layoutChangeListener)

        danmakuView = view
        view.addOnAttachStateChangeListener(attachListener)
        view.addOnLayoutChangeListener(layoutChangeListener)
    }

    fun bind(player: DanmakuPlayer?) {
        val view = danmakuView ?: return

        currentPlayer = player

        when {
            player == null -> {
                clearReadyState()
                view.danmakuPlayer = null
                onPlayerReady(null)
            }

            view.danmakuPlayer !== player -> {
                clearReadyState()
                view.danmakuPlayer = null
                player.bindView(view)
                notifyIfReady()
            }

            else -> {
                notifyIfReady()
            }
        }
    }

    fun clearReadyState() {
        lastReadyPlayer = null
    }

    private fun notifyIfReady() {
        val view = danmakuView ?: return
        val player = currentPlayer ?: return

        if (view.danmakuPlayer !== player) return
        if (!view.isAttachedToWindow) return
        if (view.width <= 0 || view.height <= 0) return
        if (lastReadyPlayer === player) return

        lastReadyPlayer = player
        onPlayerReady(player)
    }
}