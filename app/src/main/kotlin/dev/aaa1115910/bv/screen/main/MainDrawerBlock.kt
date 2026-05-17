package dev.aaa1115910.bv.screen.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import dev.aaa1115910.bv.component.wjzfocus.WjzFocusEntryId
import dev.aaa1115910.bv.component.wjzfocus.WjzFocusEntryResolution
import dev.aaa1115910.bv.component.wjzfocus.WjzFocusExitRequest
import dev.aaa1115910.bv.component.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.component.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.component.wjzfocus.WjzFocusNodeExit
import dev.aaa1115910.bv.component.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.component.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.component.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.component.wjzfocus.wjzFocusable

internal data class MainDrawerEntryRequest(
    val id: Long,
    val target: MainDrawerEntryTarget
)

internal enum class MainDrawerEntryTarget {
    User,
    CurrentItem
}

internal enum class MainDrawerContentEntryTarget {
    LeftEntry,
    RightEntry
}

@Composable
internal fun MainDrawerBlock(
    modifier: Modifier = Modifier,
    selectedItem: LeftNaviItem,
    currentItem: LeftNaviItem,
    entryRequest: MainDrawerEntryRequest? = null,
    onEntryRequestConsumed: (Long) -> Unit = {},
    userColorAnimationEnabled: Boolean,
    userIsLogin: Boolean,
    userAvatar: String,
    username: String,
    userIsFocused: Boolean,
    onUserFocusChanged: (Boolean) -> Unit,
    onCollapse: () -> Unit,
    onOpenUser: () -> Unit,
    onItemFocused: (LeftNaviItem) -> Unit,
    onItemActivated: (LeftNaviItem) -> Unit,
    onContentEntryRequested: (LeftNaviItem, MainDrawerContentEntryTarget) -> Boolean,
    onOpenSettings: () -> Unit
) {
    val focusCoordinator = LocalWjzFocusCoordinator.current
    val focusScopeId = LocalWjzFocusScopeId.current

    LaunchedEffect(entryRequest, focusCoordinator, focusScopeId, currentItem) {
        val request = entryRequest ?: return@LaunchedEffect
        val nodeId = when (request.target) {
            MainDrawerEntryTarget.User -> LeftNaviUserNodeId
            MainDrawerEntryTarget.CurrentItem -> currentItem.drawerContentNodeId()
        }
        if (focusCoordinator?.enqueueRequestFocus(
                nodeId = nodeId,
                layer = WjzFocusLayer.Drawer,
                scopeId = focusScopeId
            ) == true
        ) {
            onEntryRequestConsumed(request.id)
        }
    }

    LeftNaviContent(
        modifier = modifier,
        selectedItem = selectedItem,
        onItemActivated = onItemActivated,
        onItemFocused = onItemFocused,
        onOpenSettings = onOpenSettings,
        drawerItemFocusModifier = { item, itemModifier, onFocusChanged ->
            itemModifier.wjzFocusable(
                nodeId = item.drawerContentNodeId(),
                layer = WjzFocusLayer.Drawer,
                scopeId = focusScopeId,
                fallback = item == selectedItem,
                exits = drawerItemExits(item),
                onExit = { request ->
                    resolveDrawerExit(
                        request = request,
                        focusScopeId = focusScopeId,
                        currentItem = currentItem,
                        onContentEntryRequested = onContentEntryRequested
                    )
                },
                onFocusChanged = onFocusChanged
            )
        },
        settingsFocusModifier = { settingsModifier, onFocusChanged ->
            settingsModifier.wjzFocusable(
                nodeId = LeftNaviSettingsNodeId,
                layer = WjzFocusLayer.Drawer,
                scopeId = focusScopeId,
                exits = settingsExits(),
                onExit = { request ->
                    resolveDrawerExit(
                        request = request,
                        focusScopeId = focusScopeId,
                        currentItem = currentItem,
                        onContentEntryRequested = onContentEntryRequested
                    )
                },
                onFocusChanged = onFocusChanged
            )
        },
        userContent = {
            LeftNaviUserButton(
                Modifier.wjzFocusable(
                    nodeId = LeftNaviUserNodeId,
                    layer = WjzFocusLayer.Drawer,
                    scopeId = focusScopeId,
                    exits = userExits(),
                    onExit = { request ->
                        resolveDrawerExit(
                            request = request,
                            focusScopeId = focusScopeId,
                            currentItem = currentItem,
                            onContentEntryRequested = onContentEntryRequested
                        )
                    }
                ),
                expanded = true,
                colorAnimationEnabled = userColorAnimationEnabled,
                isLogin = userIsLogin,
                avatar = userAvatar,
                username = username,
                isFocused = userIsFocused,
                onFocusChanged = onUserFocusChanged,
                onConfirmLongPress = {
                    onOpenUser()
                    true
                },
                onConfirm = onCollapse,
                onClick = onCollapse
            )
        }
    )
}

private enum class MainDrawerFocusEntry(
    val entryId: WjzFocusEntryId
) {
    User(WjzFocusEntryId("main/drawer/user")),
    CurrentItem(WjzFocusEntryId("main/drawer/current-item")),
    Pgc(WjzFocusEntryId("main/drawer/item/${LeftNaviItem.PGC.name}")),
    Settings(WjzFocusEntryId("main/drawer/settings")),
    ContentLeftEntry(WjzFocusEntryId("main/content/left-entry")),
    ContentRightEntry(WjzFocusEntryId("main/content/right-entry"))
}

private fun userExits(): List<WjzFocusNodeExit> {
    return listOf(
        WjzFocusNodeExit(FocusDirection.Up, MainDrawerFocusEntry.Settings.entryId),
        WjzFocusNodeExit(FocusDirection.Down, MainDrawerFocusEntry.CurrentItem.entryId),
        WjzFocusNodeExit.cancel(FocusDirection.Left),
        WjzFocusNodeExit.cancel(FocusDirection.Right)
    )
}

private fun drawerItemExits(item: LeftNaviItem): List<WjzFocusNodeExit> {
    return buildList {
        add(WjzFocusNodeExit(FocusDirection.Left, MainDrawerFocusEntry.ContentRightEntry.entryId))
        add(WjzFocusNodeExit(FocusDirection.Right, MainDrawerFocusEntry.ContentLeftEntry.entryId))
        if (item == LeftNaviItem.Home) {
            add(WjzFocusNodeExit(FocusDirection.Up, MainDrawerFocusEntry.User.entryId))
        }
        if (item == LeftNaviItem.PGC) {
            add(WjzFocusNodeExit(FocusDirection.Down, MainDrawerFocusEntry.Settings.entryId))
        }
    }
}

private fun settingsExits(): List<WjzFocusNodeExit> {
    return listOf(
        WjzFocusNodeExit(FocusDirection.Up, MainDrawerFocusEntry.Pgc.entryId),
        WjzFocusNodeExit(FocusDirection.Down, MainDrawerFocusEntry.User.entryId),
        WjzFocusNodeExit(FocusDirection.Left, MainDrawerFocusEntry.ContentRightEntry.entryId),
        WjzFocusNodeExit(FocusDirection.Right, MainDrawerFocusEntry.ContentLeftEntry.entryId)
    )
}

private fun resolveDrawerExit(
    request: WjzFocusExitRequest,
    focusScopeId: WjzFocusScopeId?,
    currentItem: LeftNaviItem,
    onContentEntryRequested: (LeftNaviItem, MainDrawerContentEntryTarget) -> Boolean
): WjzFocusEntryResolution {
    return when (request.targetEntryId) {
        MainDrawerFocusEntry.User.entryId -> drawerReady(LeftNaviUserNodeId, focusScopeId)
        MainDrawerFocusEntry.CurrentItem.entryId -> drawerReady(currentItem.drawerContentNodeId(), focusScopeId)
        MainDrawerFocusEntry.Pgc.entryId -> drawerReady(LeftNaviItem.PGC.drawerContentNodeId(), focusScopeId)
        MainDrawerFocusEntry.Settings.entryId -> drawerReady(LeftNaviSettingsNodeId, focusScopeId)
        MainDrawerFocusEntry.ContentLeftEntry.entryId -> drawerContentEntry(
            request = request,
            target = MainDrawerContentEntryTarget.LeftEntry,
            currentItem = currentItem,
            onContentEntryRequested = onContentEntryRequested
        )

        MainDrawerFocusEntry.ContentRightEntry.entryId -> drawerContentEntry(
            request = request,
            target = MainDrawerContentEntryTarget.RightEntry,
            currentItem = currentItem,
            onContentEntryRequested = onContentEntryRequested
        )

        else -> WjzFocusEntryResolution.Reject
    }
}

private fun drawerContentEntry(
    request: WjzFocusExitRequest,
    target: MainDrawerContentEntryTarget,
    currentItem: LeftNaviItem,
    onContentEntryRequested: (LeftNaviItem, MainDrawerContentEntryTarget) -> Boolean
): WjzFocusEntryResolution {
    return if (onContentEntryRequested(currentItem, target)) {
        WjzFocusEntryResolution.Pending(
            entryId = request.targetEntryId,
            layer = WjzFocusLayer.Content,
            scopeId = null
        )
    } else {
        WjzFocusEntryResolution.Reject
    }
}

private fun drawerReady(
    nodeId: WjzFocusNodeId,
    focusScopeId: WjzFocusScopeId?
): WjzFocusEntryResolution {
    return WjzFocusEntryResolution.Ready(
        nodeId = nodeId,
        layer = WjzFocusLayer.Drawer,
        scopeId = focusScopeId
    )
}

private fun LeftNaviItem.drawerContentNodeId(): WjzFocusNodeId {
    return when (this) {
        LeftNaviItem.Home,
        LeftNaviItem.Live,
        LeftNaviItem.UGC,
        LeftNaviItem.PGC -> leftNaviItemFocusNodeId(this)

        else -> leftNaviItemFocusNodeId(LeftNaviItem.Home)
    }
}
