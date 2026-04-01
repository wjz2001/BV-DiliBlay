package dev.aaa1115910.bv.component.controllers.playermenu.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.component.ifElse
import dev.aaa1115910.bv.entity.VideoFlip
import dev.aaa1115910.bv.entity.VideoRotation
import dev.aaa1115910.bv.entity.VideoTransformNormal

private sealed interface VideoTransformMenuAction {
    data object Normal : VideoTransformMenuAction
    data class Rotate(val rotation: VideoRotation) : VideoTransformMenuAction
    data class Flip(val flip: VideoFlip) : VideoTransformMenuAction
}

@Composable
fun VideoTransformMenuList(
    modifier: Modifier = Modifier,
    currentVideoRotation: VideoRotation?,
    currentVideoFlip: VideoFlip?,
    onVideoTransformReset: () -> Unit,
    onVideoRotationChange: (VideoRotation?) -> Unit,
    onVideoFlipChange: (VideoFlip?) -> Unit,
    onFocusBackToParent: () -> Unit
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val actions = buildList {
        add(VideoTransformMenuAction.Normal)
        addAll(VideoRotation.entries.map { VideoTransformMenuAction.Rotate(it) })
        addAll(VideoFlip.entries.map { VideoTransformMenuAction.Flip(it) })
    }

    LazyColumn(
        modifier = modifier
            .onPreviewKeyEvent {
                println(it)
                if (it.type == KeyEventType.KeyUp) {
                    if (listOf(Key.Enter, Key.DirectionCenter).contains(it.key)) {
                        return@onPreviewKeyEvent false
                    }
                    return@onPreviewKeyEvent true
                }
                val result = it.key == Key.DirectionRight
                if (result) onFocusBackToParent()
                result
            }
            .focusRestorer(focusRequester),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 120.dp, horizontal = 8.dp)
    ) {
        itemsIndexed(actions) { index, action ->
            val text = when (action) {
                VideoTransformMenuAction.Normal -> {
                    VideoTransformNormal.Normal.getDisplayName(context)
                }

                is VideoTransformMenuAction.Rotate -> action.rotation.getDisplayName(context)
                is VideoTransformMenuAction.Flip -> action.flip.getDisplayName(context)
            }

            val selected = when (action) {
                VideoTransformMenuAction.Normal -> {
                    currentVideoRotation == null && currentVideoFlip == null
                }

                is VideoTransformMenuAction.Rotate -> currentVideoRotation == action.rotation
                is VideoTransformMenuAction.Flip -> currentVideoFlip == action.flip
            }

            MenuListItem(
                modifier = Modifier
                    .width(200.dp)
                    .ifElse(
                        when (action) {
                            VideoTransformMenuAction.Normal ->
                                currentVideoRotation == null && currentVideoFlip == null

                            is VideoTransformMenuAction.Rotate ->
                                currentVideoRotation == action.rotation

                            is VideoTransformMenuAction.Flip ->
                                currentVideoFlip == action.flip
                        },
                        Modifier.focusRequester(focusRequester)
                    ),
                text = text,
                selected = selected,
                onClick = {
                    when (action) {
                        VideoTransformMenuAction.Normal -> onVideoTransformReset()

                        is VideoTransformMenuAction.Rotate -> {
                            onVideoRotationChange(
                                if (currentVideoRotation == action.rotation) null else action.rotation
                            )
                        }

                        is VideoTransformMenuAction.Flip -> {
                            onVideoFlipChange(
                                if (currentVideoFlip == action.flip) null else action.flip
                            )
                        }
                    }
                }
            )
        }
    }
}