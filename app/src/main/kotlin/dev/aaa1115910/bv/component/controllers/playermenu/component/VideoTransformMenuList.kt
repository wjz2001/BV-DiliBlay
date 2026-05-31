package dev.aaa1115910.bv.component.controllers.playermenu.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.component.controllers.PlayerMenuMainEntryId
import dev.aaa1115910.bv.component.controllers.playermenu.playerMenuFocusNodeId
import dev.aaa1115910.bv.entity.VideoFlip
import dev.aaa1115910.bv.entity.VideoRotation
import dev.aaa1115910.bv.entity.VideoTransformNormal
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.right
import dev.aaa1115910.bv.wjzfocus.wjzFocusRestorerHost

private sealed interface VideoTransformMenuAction {
    data object Normal : VideoTransformMenuAction
    data class Rotate(val rotation: VideoRotation) : VideoTransformMenuAction
    data class Flip(val flip: VideoFlip) : VideoTransformMenuAction
}

@Composable
fun VideoTransformMenuList(
    modifier: Modifier = Modifier,
    focusIdPrefix: String,
    currentVideoRotation: VideoRotation?,
    currentVideoFlip: VideoFlip?,
    onVideoTransformReset: () -> Unit,
    onVideoRotationChange: (VideoRotation?) -> Unit,
    onVideoFlipChange: (VideoFlip?) -> Unit,
    parentFocusEntryId: String = PlayerMenuMainEntryId,
    onItemFocused: () -> Unit = {},
    onFocusBackToParent: () -> Unit
) {
    val context = LocalContext.current
    val focusScopeId = LocalWjzFocusScopeId.current
    val actions = buildList {
        add(VideoTransformMenuAction.Normal)
        addAll(VideoRotation.entries.map { VideoTransformMenuAction.Rotate(it) })
        addAll(VideoFlip.entries.map { VideoTransformMenuAction.Flip(it) })
    }
    val selectedIndex = actions.indexOfFirst { action ->
        when (action) {
            VideoTransformMenuAction.Normal -> currentVideoRotation == null && currentVideoFlip == null
            is VideoTransformMenuAction.Rotate -> currentVideoRotation == action.rotation
            is VideoTransformMenuAction.Flip -> currentVideoFlip == action.flip
        }
    }.takeIf { it >= 0 } ?: 0
    val fallbackFocusId = "$focusIdPrefix/$selectedIndex"

    LazyColumn(
        modifier = modifier
            .wjzFocusRestorerHost(
                layer = WjzFocusLayer.Overlay,
                scopeId = focusScopeId,
                restorerId = "$focusIdPrefix/restorer",
                listId = "$focusIdPrefix/list",
                fallbackNodeId = playerMenuFocusNodeId(focusScopeId, fallbackFocusId)
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 120.dp, horizontal = 8.dp)
    ) {
        itemsIndexed(
            items = actions,
            key = { index, action -> "$focusIdPrefix/$index/${action::class.simpleName}" }
        ) { index, action ->
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
                    .width(200.dp),
                focusId = "$focusIdPrefix/$index",
                text = text,
                selected = selected,
                exits = {
                    right move parentFocusEntryId
                },
                onFocus = onItemFocused,
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
