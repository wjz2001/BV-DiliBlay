package dev.aaa1115910.bv.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusComponentId
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntrySurface
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusTargetEntry
import dev.aaa1115910.bv.wjzfocus.WjzFocusTopologyRegionRef
import dev.aaa1115910.bv.wjzfocus.horizontal
import dev.aaa1115910.bv.wjzfocus.left
import dev.aaa1115910.bv.wjzfocus.resolve
import dev.aaa1115910.bv.wjzfocus.right
import dev.aaa1115910.bv.wjzfocus.vertical
import dev.aaa1115910.bv.wjzfocus.wjzFocusEncodeItemEntryId
import dev.aaa1115910.bv.wjzfocus.wjzFocusItemNodeId
import dev.aaa1115910.bv.wjzfocus.wjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.wjzFocusRememberTopologyRegion
import dev.aaa1115910.bv.wjzfocus.wjzFocusRouter
import dev.aaa1115910.bv.wjzfocus.wjzLinearFocusResolver
import dev.aaa1115910.bv.wjzfocus.wjzTextFieldFocus

enum class WjzMediaListControlAction {
    SortAsc,
    SortDesc,
    PlayAsc,
    PlayDesc,
    PlayRandom
}

private val WjzMediaListControlBarComponentId =
    WjzFocusComponentId("wjz_media_list_control_bar")
private val WjzMediaListControlBarActionsComponentId =
    WjzFocusComponentId("wjz_media_list_control_bar_actions")
private val WjzMediaListControlBarSearchLocalId =
    wjzFocusLocalId("media-list-control-bar", "search")

@Composable
fun WjzMediaListControlBar(
    modifier: Modifier = Modifier,
    showSortDesc: Boolean = true,
    showPlayAsc: Boolean = true,
    showPlayDesc: Boolean = true,
    showPlayRandom: Boolean = true,
    showSearch: Boolean = true,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    focusLayer: WjzFocusLayer = WjzFocusLayer.TopNav,
    selectedAction: WjzMediaListControlAction,
    searchText: String,
    onActionSelected: (WjzMediaListControlAction) -> Unit,
    onActionClick: (WjzMediaListControlAction) -> Unit = onActionSelected,
    onSearchTextChange: (String) -> Unit,
    onSearchAction: () -> Unit = {}
) {
    val actions = remember(showSortDesc, showPlayAsc, showPlayDesc, showPlayRandom) {
        buildList {
            add(WjzMediaListControlAction.SortAsc)
            if (showSortDesc) add(WjzMediaListControlAction.SortDesc)
            if (showPlayAsc) add(WjzMediaListControlAction.PlayAsc)
            if (showPlayDesc) add(WjzMediaListControlAction.PlayDesc)
            if (showPlayRandom) add(WjzMediaListControlAction.PlayRandom)
        }
    }
    val selectedItem = selectedAction.takeIf { it in actions } ?: WjzMediaListControlAction.SortAsc
    val focusScopeId = LocalWjzFocusScopeId.current
    val topology = wjzFocusRememberTopologyRegion(topologyRegion)
    val actionTargets = remember(actions, focusScopeId, focusLayer) {
        actions.map { action ->
            action.toFocusTargetEntry(
                scopeId = focusScopeId,
                layer = focusLayer
            )
        }
    }
    val searchTarget = remember(focusScopeId, focusLayer) {
        WjzFocusTargetEntry(
            id = WjzMediaListControlBarSearchLocalId.value,
            nodeId = WjzMediaListControlBarSearchLocalId.toNodeId(focusScopeId),
            layer = focusLayer,
            scopeId = focusScopeId
        )
    }
    val focusTargets = remember(actionTargets, searchTarget, showSearch) {
        if (showSearch) actionTargets + searchTarget else actionTargets
    }
    val focusResolver = remember(focusTargets) {
        wjzLinearFocusResolver(
            entries = focusTargets,
            direction = horizontal,
            wrap = true
        )
    }
    val searchInteractionSource = remember { MutableInteractionSource() }
    val searchFocused by searchInteractionSource.collectIsFocusedAsState()
    val searchActiveColor = C.primary
    val searchInactiveColor = C.onSurfaceVariant
    val searchColor = if (searchFocused) searchActiveColor else searchInactiveColor

    WjzFocusEntrySurface(
        componentId = WjzMediaListControlBarComponentId.value,
        default = {
            topology.resolveInitialTarget(
                componentId = WjzMediaListControlBarComponentId.value,
                targets = focusTargets
            ) {
                actionTargets.first()
            }
        },
        entries = {
            actionTargets.forEach { target ->
                entry(target.id) { target }
            }
            if (showSearch) {
                entry(searchTarget.id) { searchTarget }
            }
        }
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = MainTopTabDefaults.TabRowHorizontalPadding,
                vertical = 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BvPillTabRow(
            items = actions,
            selectedItem = selectedItem,
            entryFocusItem = selectedItem,
            itemKey = { it.name },
            itemText = { it.label },
            itemIcon = { action, iconSize ->
                WjzMediaListControlActionIcon(
                    action = action,
                    modifier = Modifier.size(iconSize)
                )
            },
            iconMode = BvTabIconMode.FocusedIconText,
            onSelectedChanged = onActionSelected,
            onClick = onActionClick,
            tabItemExits = { index, _ ->
                {
                    addAll(topology.nodeExits.filter { it.direction in vertical.directions })
                    if (showSearch && index == actions.lastIndex) {
                        right move "${WjzMediaListControlBarComponentId.value}/${searchTarget.id}"
                    }
                    if (showSearch && index == 0) {
                        left move "${WjzMediaListControlBarComponentId.value}/${searchTarget.id}"
                    }
                }
            },
            topologyRegion = WjzFocusTopologyRegionRef.Standalone,
            wrap = !showSearch,
            focusScopeId = focusScopeId,
            focusComponentId = WjzMediaListControlBarActionsComponentId,
            focusLayer = focusLayer
        )

        Spacer(modifier = Modifier.weight(1f))

        if (showSearch) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(id = R.drawable.search),
                contentDescription = null,
                tint = searchColor
            )

            Spacer(modifier = Modifier.width(8.dp))

            TextField(
                modifier = Modifier
                    .wjzFocusRouter(
                        currentEntryId = searchTarget.id,
                        resolver = focusResolver
                    )
                    .width(360.dp)
                    .height(MainTopTabDefaults.TabContentHeight)
                    .drawWithContent {
                        drawContent()
                        val stroke = 2.dp.toPx()
                        val y = size.height - stroke / 2f
                        drawLine(
                            color = searchColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = stroke
                        )
                    }
                    .wjzTextFieldFocus(
                        nodeId = searchTarget.nodeId,
                        scopeId = searchTarget.scopeId,
                        layer = searchTarget.layer,
                        exits = {
                            addAll(topology.nodeExits.filter { it.direction in vertical.directions })
                        }
                    ),
                value = searchText,
                onValueChange = onSearchTextChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 20.sp, lineHeight = 24.sp),
                shape = RectangleShape,
                interactionSource = searchInteractionSource,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = C.onSurface,
                    unfocusedTextColor = C.onSurface,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    cursorColor = searchActiveColor,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearchAction() }
                )
            )
        }
    }
}

private fun WjzMediaListControlAction.toFocusTargetEntry(
    scopeId: WjzFocusScopeId?,
    layer: WjzFocusLayer
): WjzFocusTargetEntry {
    val entryId = wjzFocusEncodeItemEntryId(name)
    val localId = WjzFocusLocalId(
        wjzFocusItemNodeId(
            listId = WjzMediaListControlBarActionsComponentId.value,
            itemEntryId = entryId
        )
    )
    return WjzFocusTargetEntry(
        id = entryId,
        nodeId = localId.toNodeId(scopeId),
        layer = layer,
        scopeId = scopeId
    )
}

private fun WjzFocusLocalId.toNodeId(
    scopeId: WjzFocusScopeId?
): WjzFocusNodeId {
    return scopeId?.resolve(this) ?: WjzFocusNodeId(value)
}

private val WjzMediaListControlAction.label: String
    get() = when (this) {
        WjzMediaListControlAction.SortAsc -> "正向排序"
        WjzMediaListControlAction.SortDesc -> "逆向排序"
        WjzMediaListControlAction.PlayAsc -> "正向播放"
        WjzMediaListControlAction.PlayDesc -> "逆向播放"
        WjzMediaListControlAction.PlayRandom -> "随机播放"
    }

@Composable
private fun WjzMediaListControlActionIcon(
    action: WjzMediaListControlAction,
    modifier: Modifier = Modifier
) {
    when (action) {
        WjzMediaListControlAction.SortAsc -> {
            Icon(
                modifier = modifier,
                imageVector = Icons.Rounded.ArrowDropUp,
                contentDescription = null
            )
        }

        WjzMediaListControlAction.SortDesc -> {
            Icon(
                modifier = modifier,
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null
            )
        }

        WjzMediaListControlAction.PlayAsc -> {
            Icon(
                modifier = modifier,
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null
            )
        }

        WjzMediaListControlAction.PlayDesc -> {
            Icon(
                modifier = modifier.graphicsLayer(scaleX = -1f),
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null
            )
        }

        WjzMediaListControlAction.PlayRandom -> {
            Icon(
                modifier = modifier,
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = null
            )
        }
    }
}
