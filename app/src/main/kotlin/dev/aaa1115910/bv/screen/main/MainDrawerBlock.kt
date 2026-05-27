package dev.aaa1115910.bv.screen.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryId
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryResolution
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.down
import dev.aaa1115910.bv.wjzfocus.left
import dev.aaa1115910.bv.wjzfocus.right
import dev.aaa1115910.bv.wjzfocus.up
import dev.aaa1115910.bv.wjzfocus.wjzFocus
import dev.aaa1115910.bv.wjzfocus.wjzFocusRouter

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
            itemModifier.wjzFocus(
                id = "item/${item.name}",
                layer = WjzFocusLayer.Drawer,
                fallback = item == selectedItem,
                exits = {
                    left move "main/content/right-entry"
                    right move "main/content/left-entry"
                    if (item == LeftNaviItem.Home) {
                        up move "main/drawer/user"
                    }
                    if (item == LeftNaviItem.PGC) {
                        down move "main/drawer/settings"
                    }
                },
                onExit = wjzFocusRouter(layer = WjzFocusLayer.Drawer) { target ->
                    when (target) {
                        "main/drawer/user" -> drawerReady(LeftNaviUserNodeId, focusScopeId)
                        "main/drawer/current-item" -> drawerReady(currentItem.drawerContentNodeId(), focusScopeId)
                        "main/drawer/item/${LeftNaviItem.PGC.name}" -> drawerReady(LeftNaviItem.PGC.drawerContentNodeId(), focusScopeId)
                        "main/drawer/settings" -> drawerReady(LeftNaviSettingsNodeId, focusScopeId)
                        "main/content/left-entry" -> drawerContentEntry(
                            target = MainDrawerContentEntryTarget.LeftEntry,
                            entryId = "main/content/left-entry",
                            currentItem = currentItem,
                            onContentEntryRequested = onContentEntryRequested
                        )
                        "main/content/right-entry" -> drawerContentEntry(
                            target = MainDrawerContentEntryTarget.RightEntry,
                            entryId = "main/content/right-entry",
                            currentItem = currentItem,
                            onContentEntryRequested = onContentEntryRequested
                        )
                        else -> reject()
                    }
                },
                onFocusChanged = onFocusChanged
            )
        },
        settingsFocusModifier = { settingsModifier, onFocusChanged ->
            settingsModifier.wjzFocus(
                id = "settings",
                layer = WjzFocusLayer.Drawer,
                exits = {
                    up move "main/drawer/item/${LeftNaviItem.PGC.name}"
                    down move "main/drawer/user"
                    left move "main/content/right-entry"
                    right move "main/content/left-entry"
                },
                onExit = wjzFocusRouter(layer = WjzFocusLayer.Drawer) { target ->
                    when (target) {
                        "main/drawer/user" -> drawerReady(LeftNaviUserNodeId, focusScopeId)
                        "main/drawer/current-item" -> drawerReady(currentItem.drawerContentNodeId(), focusScopeId)
                        "main/drawer/item/${LeftNaviItem.PGC.name}" -> drawerReady(LeftNaviItem.PGC.drawerContentNodeId(), focusScopeId)
                        "main/drawer/settings" -> drawerReady(LeftNaviSettingsNodeId, focusScopeId)
                        "main/content/left-entry" -> drawerContentEntry(
                            target = MainDrawerContentEntryTarget.LeftEntry,
                            entryId = "main/content/left-entry",
                            currentItem = currentItem,
                            onContentEntryRequested = onContentEntryRequested
                        )
                        "main/content/right-entry" -> drawerContentEntry(
                            target = MainDrawerContentEntryTarget.RightEntry,
                            entryId = "main/content/right-entry",
                            currentItem = currentItem,
                            onContentEntryRequested = onContentEntryRequested
                        )
                        else -> reject()
                    }
                },
                onFocusChanged = onFocusChanged
            )
        },
        userContent = {
            LeftNaviUserButton(
                Modifier.wjzFocus(
                    id = "user",
                    layer = WjzFocusLayer.Drawer,
                    exits = {
                        up move "main/drawer/settings"
                        down move "main/drawer/current-item"
                        cancel(left)
                        cancel(right)
                    },
                    onExit = wjzFocusRouter(layer = WjzFocusLayer.Drawer) { target ->
                        when (target) {
                            "main/drawer/user" -> drawerReady(LeftNaviUserNodeId, focusScopeId)
                            "main/drawer/current-item" -> drawerReady(currentItem.drawerContentNodeId(), focusScopeId)
                            "main/drawer/item/${LeftNaviItem.PGC.name}" -> drawerReady(LeftNaviItem.PGC.drawerContentNodeId(), focusScopeId)
                            "main/drawer/settings" -> drawerReady(LeftNaviSettingsNodeId, focusScopeId)
                            "main/content/left-entry" -> drawerContentEntry(
                                target = MainDrawerContentEntryTarget.LeftEntry,
                                entryId = "main/content/left-entry",
                                currentItem = currentItem,
                                onContentEntryRequested = onContentEntryRequested
                            )
                            "main/content/right-entry" -> drawerContentEntry(
                                target = MainDrawerContentEntryTarget.RightEntry,
                                entryId = "main/content/right-entry",
                                currentItem = currentItem,
                                onContentEntryRequested = onContentEntryRequested
                            )
                            else -> reject()
                        }
                    },
                    onFocusChanged = onUserFocusChanged
                ),
                expanded = true,
                colorAnimationEnabled = userColorAnimationEnabled,
                isLogin = userIsLogin,
                avatar = userAvatar,
                username = username,
                isFocused = userIsFocused,
                onFocusChanged = {},
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

private fun drawerContentEntry(
    target: MainDrawerContentEntryTarget,
    entryId: String,
    currentItem: LeftNaviItem,
    onContentEntryRequested: (LeftNaviItem, MainDrawerContentEntryTarget) -> Boolean
): WjzFocusEntryResolution {
    return if (onContentEntryRequested(currentItem, target)) {
        WjzFocusEntryResolution.Pending(
            entryId = WjzFocusEntryId(entryId),
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
