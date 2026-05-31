package dev.aaa1115910.bv.component.controllers.playermenu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.component.controllers.PlayerMenuNavEntryId
import dev.aaa1115910.bv.component.controllers.PlayerMenuPictureItemsEntryId
import dev.aaa1115910.bv.component.controllers.LocalMenuFocusStateData
import dev.aaa1115910.bv.component.controllers.MenuFocusState
import dev.aaa1115910.bv.component.controllers.VideoPlayerPictureMenuItem
import dev.aaa1115910.bv.component.controllers.playermenu.component.MenuListItem
import dev.aaa1115910.bv.component.controllers.playermenu.component.RadioMenuList
import dev.aaa1115910.bv.component.controllers.playermenu.component.VideoTransformMenuList
import dev.aaa1115910.bv.entity.Audio
import dev.aaa1115910.bv.entity.Resolution
import dev.aaa1115910.bv.entity.VideoAspectRatio
import dev.aaa1115910.bv.entity.VideoCodec
import dev.aaa1115910.bv.entity.VideoFlip
import dev.aaa1115910.bv.entity.VideoRotation
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntrySurface
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.defaultEntry
import dev.aaa1115910.bv.wjzfocus.left
import dev.aaa1115910.bv.wjzfocus.right
import dev.aaa1115910.bv.wjzfocus.wjzDisabledFocus

@Composable
fun PictureMenuList(
    modifier: Modifier = Modifier,
    availableQualityIds: List<Int>,
    availableAudio: List<Audio>,
    availableVideoCodec: List<VideoCodec>,
    currentResolution: Int?,
    currentVideoCodec: VideoCodec,
    currentVideoAspectRatio: VideoAspectRatio,
    currentVideoRotation: VideoRotation?,
    currentVideoFlip: VideoFlip?,
    currentAudio: Audio,
    onResolutionChange: (Int) -> Unit,
    onCodecChange: (VideoCodec) -> Unit,
    onAspectRatioChange: (VideoAspectRatio) -> Unit,
    onVideoTransformReset: () -> Unit,
    onVideoRotationChange: (VideoRotation?) -> Unit,
    onVideoFlipChange: (VideoFlip?) -> Unit,
    onAudioChange: (Audio) -> Unit,
    onFocusStateChange: (MenuFocusState) -> Unit
) {
    val context = LocalContext.current
    val focusState = LocalMenuFocusStateData.current
    val focusCoordinator = LocalWjzFocusCoordinator.current
    val focusScopeId = LocalWjzFocusScopeId.current
    var selectedPictureMenuItem by remember { mutableStateOf(VideoPlayerPictureMenuItem.Resolution) }
    var pendingParentFocusRestore by remember { mutableStateOf(false) }
    val qualityIdList = remember(availableQualityIds) {
        availableQualityIds
            .sortedByDescending {it}
    }
    val audioList = remember(availableAudio) {
        availableAudio.sortedBy { it.ordinal }
    }
    val selectedItemNodeId = remember(
        selectedPictureMenuItem,
        qualityIdList,
        currentResolution,
        availableVideoCodec,
        currentVideoCodec,
        currentVideoAspectRatio,
        currentVideoRotation,
        currentVideoFlip,
        audioList,
        currentAudio
    ) {
        WjzFocusNodeId(
            when (selectedPictureMenuItem) {
                VideoPlayerPictureMenuItem.Resolution -> {
                    val index = qualityIdList.indexOf(currentResolution)
                        .takeIf { it >= 0 }
                        ?: qualityIdList.indices.firstOrNull()
                        ?: 0
                    "$PlayerMenuPictureFocusIdPrefix/resolution/$index"
                }

                VideoPlayerPictureMenuItem.Rotation -> {
                    val index = when {
                        currentVideoRotation != null -> 1 + currentVideoRotation.ordinal
                        currentVideoFlip != null -> 1 + VideoRotation.entries.size + currentVideoFlip.ordinal
                        else -> 0
                    }
                    "$PlayerMenuPictureFocusIdPrefix/rotation/$index"
                }

                VideoPlayerPictureMenuItem.Codec -> {
                    val index = availableVideoCodec.indexOf(currentVideoCodec)
                        .takeIf { it >= 0 }
                        ?: availableVideoCodec.indices.firstOrNull()
                        ?: 0
                    "$PlayerMenuPictureFocusIdPrefix/codec/$index"
                }

                VideoPlayerPictureMenuItem.AspectRatio -> {
                    val index = VideoAspectRatio.entries.indexOf(currentVideoAspectRatio)
                    "$PlayerMenuPictureFocusIdPrefix/aspect-ratio/$index"
                }

                VideoPlayerPictureMenuItem.Audio -> {
                    val index = audioList.indexOf(currentAudio)
                        .takeIf { it >= 0 }
                        ?: audioList.indices.firstOrNull()
                        ?: 0
                    "$PlayerMenuPictureFocusIdPrefix/audio/$index"
                }
            }
        )
    }

    WjzFocusEntrySurface(
        componentId = PlayerMenuPictureItemsEntryId,
        default = {
            defaultEntry(
                nodeId = selectedItemNodeId,
                layer = WjzFocusLayer.Overlay,
                scopeId = focusScopeId
            )
        }
    )

    LaunchedEffect(pendingParentFocusRestore, focusState.focusState, focusCoordinator, focusScopeId) {
        if (pendingParentFocusRestore && focusState.focusState == MenuFocusState.Menu) {
            focusCoordinator?.restoreActiveLayer(scopeId = focusScopeId)
            pendingParentFocusRestore = false
        }
    }

    fun focusBackToParentMenu() {
        onFocusStateChange(MenuFocusState.Menu)
        pendingParentFocusRestore = true
    }

    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val menuItemsModifier = Modifier
            .width(216.dp)
            .padding(horizontal = 8.dp)
            .wjzDisabledFocus(pendingParentFocusRestore)
        AnimatedVisibility(visible = focusState.focusState != MenuFocusState.MenuNav) {
            when (selectedPictureMenuItem) {
                VideoPlayerPictureMenuItem.Resolution -> RadioMenuList(
                    modifier = menuItemsModifier,
                    focusIdPrefix = "$PlayerMenuPictureFocusIdPrefix/resolution",
                    items = qualityIdList.map { resolutionCode ->
                        runCatching {
                            Resolution.entries.find { it.code == resolutionCode }!!
                                .getShortDisplayName(context)
                        }.getOrDefault("unknown: $resolutionCode")
                    },
                    selected = qualityIdList.indexOf(currentResolution),
                    onSelectedChanged = { onResolutionChange(qualityIdList[it]) },
                    onFocusBackToParent = ::focusBackToParentMenu
                )

                VideoPlayerPictureMenuItem.Rotation -> VideoTransformMenuList(
                    modifier = menuItemsModifier,
                    focusIdPrefix = "$PlayerMenuPictureFocusIdPrefix/rotation",
                    currentVideoRotation = currentVideoRotation,
                    currentVideoFlip = currentVideoFlip,
                    onVideoTransformReset = onVideoTransformReset,
                    onVideoRotationChange = onVideoRotationChange,
                    onVideoFlipChange = onVideoFlipChange,
                    onFocusBackToParent = ::focusBackToParentMenu
                )

                VideoPlayerPictureMenuItem.Codec -> RadioMenuList(
                    modifier = menuItemsModifier,
                    focusIdPrefix = "$PlayerMenuPictureFocusIdPrefix/codec",
                    items = availableVideoCodec.map { it.getDisplayName(context) },
                    selected = availableVideoCodec.indexOf(currentVideoCodec),
                    onSelectedChanged = { onCodecChange(availableVideoCodec[it]) },
                    onFocusBackToParent = ::focusBackToParentMenu
                )

                VideoPlayerPictureMenuItem.AspectRatio -> RadioMenuList(
                    modifier = menuItemsModifier,
                    focusIdPrefix = "$PlayerMenuPictureFocusIdPrefix/aspect-ratio",
                    items = VideoAspectRatio.entries.map { it.getDisplayName(context) },
                    selected = VideoAspectRatio.entries.indexOf(currentVideoAspectRatio),
                    onSelectedChanged = { onAspectRatioChange(VideoAspectRatio.entries[it]) },
                    onFocusBackToParent = ::focusBackToParentMenu
                )

                VideoPlayerPictureMenuItem.Audio -> RadioMenuList(
                    modifier = menuItemsModifier,
                    focusIdPrefix = "$PlayerMenuPictureFocusIdPrefix/audio",
                    items = audioList.map { audio -> audio.getDisplayName(context) },
                    selected = audioList.indexOf(currentAudio),
                    onSelectedChanged = { onAudioChange(audioList[it]) },
                    onFocusBackToParent = ::focusBackToParentMenu
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            itemsIndexed(VideoPlayerPictureMenuItem.entries.toMutableList()) { index, item ->
                val selected = selectedPictureMenuItem == item
                MenuListItem(
                    modifier = Modifier,
                    focusId = "$PlayerMenuPictureFocusIdPrefix/menu/$index",
                    text = item.getDisplayName(context),
                    selected = selected,
                    exits = {
                        left move PlayerMenuPictureItemsEntryId
                        right move PlayerMenuNavEntryId
                    },
                    onClick = {},
                    onFocus = {
                        selectedPictureMenuItem = item
                        onFocusStateChange(MenuFocusState.Menu)
                    },
                )
            }
        }
    }
}
