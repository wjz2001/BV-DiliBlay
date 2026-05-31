package dev.aaa1115910.bv.screen.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntrySurface
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.defaultEntry
import dev.aaa1115910.bv.wjzfocus.down
import dev.aaa1115910.bv.wjzfocus.left
import dev.aaa1115910.bv.wjzfocus.right
import dev.aaa1115910.bv.wjzfocus.up
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.screen.main.common.MainContentEntryTarget
import dev.aaa1115910.bv.screen.main.common.MainContentTopEntryId
import dev.aaa1115910.bv.screen.main.common.MainTopNavFocusComponentId
import dev.aaa1115910.bv.screen.main.common.MainTopNavDefaultEntryId

internal data class MainTopNavEntryRequest(
    val id: Long,
    val target: MainTopNavEntryTarget
)

internal enum class MainTopNavEntryTarget {
    User
}

internal enum class MainTopNavContentEntryTarget {
    LeftEntry,
    RightEntry
}

private val MainTopNavDefaultNodeId = WjzFocusNodeId("${MainTopNavFocusComponentId}/default")

@Composable
internal fun MainTopNavBlock(
    modifier: Modifier = Modifier,
    userColorAnimationEnabled: Boolean,
    userIsLogin: Boolean,
    userAvatar: String,
    username: String,
    userIsFocused: Boolean,
    entryRequest: MainTopNavEntryRequest? = null,
    onEntryRequestConsumed: (Long) -> Unit = {},
    focusEnabled: Boolean,
    onUserFocusChanged: (Boolean) -> Unit,
    onExpandDrawer: () -> Unit,
    onOpenUser: () -> Unit,
    onContentEntryRequested: (MainTopNavContentEntryTarget) -> Boolean
) {
    val focusCoordinator = LocalWjzFocusCoordinator.current
    val focusScopeId = LocalWjzFocusScopeId.current

    LaunchedEffect(entryRequest, focusCoordinator) {
        val request = entryRequest ?: return@LaunchedEffect
        val entryId = when (request.target) {
            MainTopNavEntryTarget.User -> MainTopNavDefaultEntryId
        }
        if (focusCoordinator?.requestEntryFocus(entryId) == true) {
            onEntryRequestConsumed(request.id)
        }
    }

    WjzFocusEntrySurface(
        componentId = MainTopNavFocusComponentId,
        default = {
            defaultEntry(
                nodeId = MainTopNavDefaultNodeId,
                layer = WjzFocusLayer.TopNav,
                scopeId = focusScopeId
            )
        }
    )

    LeftNaviUserButton(
        modifier = modifier.wjzFocusExits(
            id = "default",
            layer = WjzFocusLayer.TopNav,
            enabled = focusEnabled,
            exits = {
                down move MainContentTopEntryId
                right move MainContentTopEntryId
                cancel(left)
                cancel(up)
            },
            onFocusChanged = onUserFocusChanged
        ),
        expanded = false,
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
        onConfirm = onExpandDrawer,
        onClick = onExpandDrawer
    )
}

internal fun MainTopNavContentEntryTarget.toMainContentEntryTarget(): MainContentEntryTarget {
    return when (this) {
        MainTopNavContentEntryTarget.LeftEntry -> MainContentEntryTarget.LeftEntry
        MainTopNavContentEntryTarget.RightEntry -> MainContentEntryTarget.TopEntry
    }
}
