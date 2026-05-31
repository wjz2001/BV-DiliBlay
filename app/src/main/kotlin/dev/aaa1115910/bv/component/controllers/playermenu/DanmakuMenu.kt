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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.component.controllers.DanmakuType
import dev.aaa1115910.bv.component.controllers.LocalMenuFocusStateData
import dev.aaa1115910.bv.component.controllers.MenuFocusState
import dev.aaa1115910.bv.component.controllers.PlayerMenuNavEntryId
import dev.aaa1115910.bv.component.controllers.VideoPlayerDanmakuMenuItem
import dev.aaa1115910.bv.component.controllers.playermenu.component.CheckBoxMenuList
import dev.aaa1115910.bv.component.controllers.playermenu.component.MenuListItem
import dev.aaa1115910.bv.component.controllers.playermenu.component.StepLessMenuItem
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntrySurface
import dev.aaa1115910.bv.wjzfocus.WjzFocusExitsBuilder
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.defaultEntry
import dev.aaa1115910.bv.wjzfocus.left
import dev.aaa1115910.bv.wjzfocus.right
import java.text.NumberFormat
import kotlin.math.roundToInt

private const val PlayerMenuDanmakuMenuEntryId = "playerMenuDanmakuMenu"
private const val PlayerMenuDanmakuItemsEntryId = "playerMenuDanmakuItems"

@Composable
fun DanmakuMenuList(
    modifier: Modifier = Modifier,
    currentEnabledTypes: List<DanmakuType>,
    currentScale: Float,
    currentOpacity: Float,
    currentRollingDurationFactor: Float,
    currentVodFilterLevel: Int,
    currentLiveFilterLevel: Int,
    currentColorful: Boolean,
    currentArea: Float,
    currentMaskEnabled: Boolean,
    isDanmakuRefreshing: Boolean = false,
    onDanmakuSwitchChange: (List<DanmakuType>) -> Unit,
    onDanmakuSizeChange: (Float) -> Unit,
    onDanmakuOpacityChange: (Float) -> Unit,
    onRollingDurationFactorChange: (Float) -> Unit,
    onVodFilterLevelChange: (Int) -> Unit,
    onLiveFilterLevelChange: (Int) -> Unit,
    onColorfulChange: (Boolean) -> Unit,
    onDanmakuAreaChange: (Float) -> Unit,
    onDanmakuMaskChange: (Boolean) -> Unit,
    onDanmakuRefreshClick: () -> Unit = {},
    onFocusStateChange: (MenuFocusState) -> Unit
) {
    val context = LocalContext.current
    val focusState = LocalMenuFocusStateData.current
    val focusScopeId = LocalWjzFocusScopeId.current
    var selectedDanmakuMenuItem by remember { mutableStateOf(VideoPlayerDanmakuMenuItem.Switch) }
    val selectedMenuNodeId = remember(selectedDanmakuMenuItem) {
        WjzFocusNodeId("$PlayerMenuDanmakuFocusIdPrefix/menu/${selectedDanmakuMenuItem.ordinal}")
    }
    val selectedItemNodeId = remember(
        selectedDanmakuMenuItem,
        currentEnabledTypes,
        currentVodFilterLevel,
        currentScale,
        currentOpacity,
        currentRollingDurationFactor,
        currentArea
    ) {
        WjzFocusNodeId(
            when (selectedDanmakuMenuItem) {
                VideoPlayerDanmakuMenuItem.Switch -> {
                    val index = currentEnabledTypes.firstOrNull()?.ordinal
                        ?.takeIf { it in DanmakuType.entries.indices }
                        ?: DanmakuType.entries.indices.first
                    "$PlayerMenuDanmakuFocusIdPrefix/switch/$index"
                }

                VideoPlayerDanmakuMenuItem.FilterLevel ->
                    "$PlayerMenuDanmakuFocusIdPrefix/filter-level"

                VideoPlayerDanmakuMenuItem.Size ->
                    "$PlayerMenuDanmakuFocusIdPrefix/size"

                VideoPlayerDanmakuMenuItem.Opacity ->
                    "$PlayerMenuDanmakuFocusIdPrefix/opacity"

                VideoPlayerDanmakuMenuItem.Speed ->
                    "$PlayerMenuDanmakuFocusIdPrefix/speed"

                VideoPlayerDanmakuMenuItem.Area ->
                    "$PlayerMenuDanmakuFocusIdPrefix/area"

                else -> "$PlayerMenuDanmakuFocusIdPrefix/menu/${selectedDanmakuMenuItem.ordinal}"
            }
        )
    }

    WjzFocusEntrySurface(
        componentId = PlayerMenuDanmakuMenuEntryId,
        default = {
            defaultEntry(
                nodeId = selectedMenuNodeId,
                layer = WjzFocusLayer.Overlay,
                scopeId = focusScopeId
            )
        }
    )
    WjzFocusEntrySurface(
        componentId = PlayerMenuDanmakuItemsEntryId,
        default = {
            defaultEntry(
                nodeId = selectedItemNodeId,
                layer = WjzFocusLayer.Overlay,
                scopeId = focusScopeId
            )
        }
    )

    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val menuItemsModifier = Modifier
            .width(216.dp)
            .padding(horizontal = 8.dp)
        AnimatedVisibility(visible = focusState.focusState != MenuFocusState.MenuNav) {
            when (selectedDanmakuMenuItem) {
                VideoPlayerDanmakuMenuItem.Switch -> CheckBoxMenuList(
                    modifier = menuItemsModifier,
                    focusIdPrefix = "$PlayerMenuDanmakuFocusIdPrefix/switch",
                    items = DanmakuType.entries.map { it.getDisplayName(context) },
                    selected = currentEnabledTypes.map { it.ordinal },
                    parentFocusEntryId = PlayerMenuDanmakuMenuEntryId,
                    onItemFocused = { onFocusStateChange(MenuFocusState.Items) },
                    onSelectedChanged = { indices ->
                        val newSelection = indices
                            .map { index -> DanmakuType.entries[index] }
                            .toMutableList()

                        val allType = DanmakuType.All
                        val allEntries = DanmakuType.entries

                        val isAllInOld = currentEnabledTypes.contains(allType)
                        val isAllInNew = newSelection.contains(allType)

                        val realItemsCount = allEntries.size - 1

                        when {
                            // ---------------------------------------------------------
                            // 场景 1: 用户直接点击了 [全选] 框
                            // ---------------------------------------------------------

                            // 1.1 从无到有：点击全选 -> 选中所有
                            !isAllInOld && isAllInNew -> {
                                onDanmakuSwitchChange(allEntries)
                            }

                            // 1.2 从有到无：取消全选 -> 清空所有
                            isAllInOld && !isAllInNew -> {
                                onDanmakuSwitchChange(emptyList())
                            }

                            // ---------------------------------------------------------
                            // 场景 2: 用户点击了 [子选项] (触发联动)
                            // ---------------------------------------------------------

                            else -> {
                                // 先计算当前选中了多少个“真实子项”(排除 All)
                                val currentRealItemsCount = newSelection.count { it != allType }

                                if (currentRealItemsCount == realItemsCount) {
                                    // 触发自动 All：
                                    // 如果所有子项都齐了，但列表中没有 All，手动加上 All
                                    if (!newSelection.contains(allType)) {
                                        newSelection.add(allType)
                                    }
                                    onDanmakuSwitchChange(newSelection)
                                } else {
                                    // 触发自动移除 All：
                                    // 如果子项不齐（用户取消了某一项），但列表里还有 All，必须移除 All
                                    if (newSelection.contains(allType)) {
                                        newSelection.remove(allType)
                                    }
                                    onDanmakuSwitchChange(newSelection)
                                }
                            }
                        }
                    },
                    onFocusBackToParent = {
                        onFocusStateChange(MenuFocusState.Menu)
                    }
                )

                VideoPlayerDanmakuMenuItem.FilterLevel -> StepLessMenuItem(
                    modifier = menuItemsModifier,
                    focusId = "$PlayerMenuDanmakuFocusIdPrefix/filter-level",
                    value = currentVodFilterLevel,
                    step = 1,
                    range = 0..10,
                    text = currentVodFilterLevel.toString(),
                    parentFocusEntryId = PlayerMenuDanmakuMenuEntryId,
                    onItemFocused = { onFocusStateChange(MenuFocusState.Items) },
                    onValueChange = onVodFilterLevelChange,
                    onFocusBackToParent = { onFocusStateChange(MenuFocusState.Menu) }
                )

                VideoPlayerDanmakuMenuItem.BlockKeyword -> {}

                VideoPlayerDanmakuMenuItem.Size -> StepLessMenuItem(
                    modifier = menuItemsModifier,
                    focusId = "$PlayerMenuDanmakuFocusIdPrefix/size",
                    value = currentScale,
                    step = 0.01f,
                    range = 0.5f..4f,
                    text = NumberFormat.getPercentInstance()
                        .apply { maximumFractionDigits = 0 }
                        .format(currentScale),
                    parentFocusEntryId = PlayerMenuDanmakuMenuEntryId,
                    onItemFocused = { onFocusStateChange(MenuFocusState.Items) },
                    onValueChange = onDanmakuSizeChange,
                    onFocusBackToParent = { onFocusStateChange(MenuFocusState.Menu) }
                )

                VideoPlayerDanmakuMenuItem.Opacity -> StepLessMenuItem(
                    modifier = menuItemsModifier,
                    focusId = "$PlayerMenuDanmakuFocusIdPrefix/opacity",
                    value = currentOpacity,
                    step = 0.01f,
                    range = 0f..1f,
                    text = NumberFormat.getPercentInstance()
                        .apply { maximumFractionDigits = 0 }
                        .format(currentOpacity),
                    parentFocusEntryId = PlayerMenuDanmakuMenuEntryId,
                    onItemFocused = { onFocusStateChange(MenuFocusState.Items) },
                    onValueChange = onDanmakuOpacityChange,
                    onFocusBackToParent = { onFocusStateChange(MenuFocusState.Menu) }
                )

                VideoPlayerDanmakuMenuItem.Colorful -> {}

                VideoPlayerDanmakuMenuItem.Speed -> StepLessMenuItem(
                    modifier = menuItemsModifier,
                    focusId = "$PlayerMenuDanmakuFocusIdPrefix/speed",
                    value = currentRollingDurationFactor,
                    step = 0.1f,
                    range = 0.2f..1.8f,
                    text = "${((currentRollingDurationFactor * 10).roundToInt() / 10f)}x",
                    parentFocusEntryId = PlayerMenuDanmakuMenuEntryId,
                    onItemFocused = { onFocusStateChange(MenuFocusState.Items) },
                    onValueChange = onRollingDurationFactorChange,
                    onFocusBackToParent = { onFocusStateChange(MenuFocusState.Menu) }
                )

                VideoPlayerDanmakuMenuItem.Area -> StepLessMenuItem(
                    modifier = menuItemsModifier,
                    focusId = "$PlayerMenuDanmakuFocusIdPrefix/area",
                    value = currentArea,
                    step = 0.01f,
                    range = 0f..1f,
                    text = NumberFormat.getPercentInstance()
                        .apply { maximumFractionDigits = 0 }
                        .format(currentArea),
                    parentFocusEntryId = PlayerMenuDanmakuMenuEntryId,
                    onItemFocused = { onFocusStateChange(MenuFocusState.Items) },
                    onValueChange = onDanmakuAreaChange,
                    onFocusBackToParent = { onFocusStateChange(MenuFocusState.Menu) }
                )

                VideoPlayerDanmakuMenuItem.Mask -> {}

                VideoPlayerDanmakuMenuItem.Refresh -> {}
            }
        }
        LazyColumn(
            modifier = Modifier
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            itemsIndexed(VideoPlayerDanmakuMenuItem.entries) { index, item ->
                val exits = danmakuMenuItemExits(item)
                when (item) {
                    VideoPlayerDanmakuMenuItem.Colorful -> MenuListItem(
                        modifier = Modifier,
                        focusId = "$PlayerMenuDanmakuFocusIdPrefix/menu/$index",
                        text = item.getDisplayName(context),
                        selected = currentColorful,
                        exits = exits,
                        onFocus = {
                            selectedDanmakuMenuItem = item
                            onFocusStateChange(MenuFocusState.Menu)
                        },
                        onClick = { onColorfulChange(!currentColorful) },
                    )

                    VideoPlayerDanmakuMenuItem.Mask -> MenuListItem(
                        modifier = Modifier,
                        focusId = "$PlayerMenuDanmakuFocusIdPrefix/menu/$index",
                        text = item.getDisplayName(context),
                        selected = currentMaskEnabled,
                        exits = exits,
                        onFocus = {
                            selectedDanmakuMenuItem = item
                            onFocusStateChange(MenuFocusState.Menu)
                        },
                        onClick = { onDanmakuMaskChange(!currentMaskEnabled) },
                    )

                    VideoPlayerDanmakuMenuItem.Refresh -> MenuListItem(
                        modifier = Modifier,
                        focusId = "$PlayerMenuDanmakuFocusIdPrefix/menu/$index",
                        text = if (isDanmakuRefreshing) {
                            stringResource(R.string.video_player_menu_danmaku_refreshing)
                        } else {
                            item.getDisplayName(context)
                        },
                        selected = isDanmakuRefreshing,
                        exits = exits,
                        onClick = onDanmakuRefreshClick,
                        onFocus = {
                            selectedDanmakuMenuItem = item
                            onFocusStateChange(MenuFocusState.Menu)
                        },
                    )

                    else -> MenuListItem(
                        modifier = Modifier,
                        focusId = "$PlayerMenuDanmakuFocusIdPrefix/menu/$index",
                        text = item.getDisplayName(context),
                        selected = selectedDanmakuMenuItem == item,
                        exits = exits,
                        onClick = {},
                        onFocus = {
                            selectedDanmakuMenuItem = item
                            onFocusStateChange(MenuFocusState.Menu)
                        },
                    )
                }
            }
        }
    }
}

private fun danmakuMenuItemExits(
    item: VideoPlayerDanmakuMenuItem
): WjzFocusExitsBuilder.() -> Unit = {
    if (item.hasDanmakuItems()) {
        left move PlayerMenuDanmakuItemsEntryId
    }
    right move PlayerMenuNavEntryId
}

private fun VideoPlayerDanmakuMenuItem.hasDanmakuItems(): Boolean {
    return when (this) {
        VideoPlayerDanmakuMenuItem.Switch,
        VideoPlayerDanmakuMenuItem.FilterLevel,
        VideoPlayerDanmakuMenuItem.Size,
        VideoPlayerDanmakuMenuItem.Opacity,
        VideoPlayerDanmakuMenuItem.Speed,
        VideoPlayerDanmakuMenuItem.Area -> true

        VideoPlayerDanmakuMenuItem.BlockKeyword,
        VideoPlayerDanmakuMenuItem.Colorful,
        VideoPlayerDanmakuMenuItem.Mask,
        VideoPlayerDanmakuMenuItem.Refresh -> false
    }
}
