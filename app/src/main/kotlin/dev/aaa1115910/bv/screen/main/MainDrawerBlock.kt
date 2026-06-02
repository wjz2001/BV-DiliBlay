package dev.aaa1115910.bv.screen.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import dev.aaa1115910.bv.wjzfocus.WjzFocusDefaultTarget
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntrySurface
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.down
import dev.aaa1115910.bv.wjzfocus.left
import dev.aaa1115910.bv.wjzfocus.right
import dev.aaa1115910.bv.wjzfocus.target
import dev.aaa1115910.bv.wjzfocus.up
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.wjzfocus.wjzFocusLocalId
import dev.aaa1115910.bv.screen.main.common.MainContentLeftEntryId
import dev.aaa1115910.bv.screen.main.common.MainDrawerFocusComponentId
import dev.aaa1115910.bv.screen.main.common.MainDrawerRightEntryId

internal val MainDrawerSettingsLocalId = wjzFocusLocalId("settings")
internal val MainDrawerUserLocalId = wjzFocusLocalId("user")

internal data class MainDrawerEntryRequest(
    val id: Long,
    val target: MainDrawerEntryTarget
)

internal enum class MainDrawerEntryTarget {
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
    val drawerScopeId = requireNotNull(focusScopeId) {
        "MainDrawerBlock requires LocalWjzFocusScopeId.current"
    }

    LaunchedEffect(entryRequest, focusCoordinator, focusScopeId, currentItem) {
        val request = entryRequest ?: return@LaunchedEffect
        val entryId = when (request.target) {
            MainDrawerEntryTarget.CurrentItem -> MainDrawerRightEntryId
        }
        if (focusCoordinator?.requestEntryFocus(entryId) == true) {
            onEntryRequestConsumed(request.id)
        }
    }

    WjzFocusEntrySurface(
        componentId = MainDrawerFocusComponentId,
        default = {
            drawerScopeId.mainDrawerItemTarget(currentItem)
        },
        entries = {
            "right" move drawerScopeId.mainDrawerItemTarget(currentItem)
        }
    )

    LeftNaviContent(
        modifier = modifier,
        selectedItem = selectedItem,
        onItemActivated = onItemActivated,
        onItemFocused = onItemFocused,
        onOpenSettings = onOpenSettings,
        drawerItemFocusModifier = { item, itemModifier, onFocusChanged ->
            itemModifier.wjzFocusExits(
                localId = mainDrawerItemLocalId(item),
                layer = WjzFocusLayer.Drawer,
                exits = {
                    right move MainContentLeftEntryId
                    cancel(left)
                    if (item == LeftNaviItem.Home) {
                        up move MainDrawerRightEntryId
                    }
                    if (item == LeftNaviItem.PGC) {
                        down move MainDrawerRightEntryId
                    }
                },
                onFocusChanged = onFocusChanged
            )
        },
        settingsFocusModifier = { settingsModifier, onFocusChanged ->
            settingsModifier.wjzFocusExits(
                localId = MainDrawerSettingsLocalId,
                layer = WjzFocusLayer.Drawer,
                exits = {
                    up move MainDrawerRightEntryId
                    down move MainDrawerRightEntryId
                    right move MainContentLeftEntryId
                    cancel(left)
                },
                onFocusChanged = onFocusChanged
            )
        },
        userContent = {
            LeftNaviUserButton(
                Modifier.wjzFocusExits(
                    localId = MainDrawerUserLocalId,
                    layer = WjzFocusLayer.Drawer,
                    exits = {
                        up move MainDrawerRightEntryId
                        down move MainDrawerRightEntryId
                        right move MainContentLeftEntryId
                        cancel(left)
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

internal fun WjzFocusScopeId.mainDrawerItemTarget(item: LeftNaviItem): WjzFocusDefaultTarget {
    return target(mainDrawerItemLocalId(item)).copy(layer = WjzFocusLayer.Drawer)
}

internal fun mainDrawerItemLocalId(item: LeftNaviItem): WjzFocusLocalId {
    return when (item) {
        LeftNaviItem.Home,
        LeftNaviItem.Live,
        LeftNaviItem.UGC,
        LeftNaviItem.PGC -> wjzFocusLocalId("item", item.name)

        else -> wjzFocusLocalId("item", LeftNaviItem.Home.name)
    }
}
