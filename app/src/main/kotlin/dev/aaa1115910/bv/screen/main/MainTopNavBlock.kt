package dev.aaa1115910.bv.screen.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryId
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryResolution
import dev.aaa1115910.bv.wjzfocus.WjzFocusExitRequest
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeExit
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.wjzFocusable
import dev.aaa1115910.bv.screen.main.common.MainContentEntryTarget

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

private val MainTopNavUserNodeId = WjzFocusNodeId("main/top-nav/user")
private val MainTopNavScopeId = WjzFocusScopeId("main/top-nav")

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

    LaunchedEffect(entryRequest, focusCoordinator) {
        val request = entryRequest ?: return@LaunchedEffect
        val nodeId = when (request.target) {
            MainTopNavEntryTarget.User -> MainTopNavUserNodeId
        }
        if (focusCoordinator?.enqueueRequestFocus(
                nodeId = nodeId,
                layer = WjzFocusLayer.TopNav,
                scopeId = MainTopNavScopeId
            ) == true
        ) {
            onEntryRequestConsumed(request.id)
        }
    }

    LeftNaviUserButton(
        modifier = modifier.wjzFocusable(
            nodeId = MainTopNavUserNodeId,
            layer = WjzFocusLayer.TopNav,
            scopeId = MainTopNavScopeId,
            fallback = true,
            enabled = focusEnabled,
            exits = topNavUserExits(),
            onExit = { request ->
                resolveTopNavUserExit(
                    request = request,
                    onContentEntryRequested = onContentEntryRequested
                )
            }
        ),
        expanded = false,
        colorAnimationEnabled = userColorAnimationEnabled,
        isLogin = userIsLogin,
        avatar = userAvatar,
        username = username,
        isFocused = userIsFocused,
        onFocusChanged = { hasFocus ->
            onUserFocusChanged(hasFocus)
        },
        onConfirmLongPress = {
            onOpenUser()
            true
        },
        onConfirm = onExpandDrawer,
        onClick = onExpandDrawer
    )
}

private enum class MainTopNavFocusEntry(
    val entryId: WjzFocusEntryId
) {
    ContentLeftEntry(WjzFocusEntryId("main/content/left-entry")),
    ContentRightEntry(WjzFocusEntryId("main/content/right-entry"))
}

private fun topNavUserExits(): List<WjzFocusNodeExit> {
    return listOf(
        WjzFocusNodeExit(FocusDirection.Left, MainTopNavFocusEntry.ContentRightEntry.entryId),
        WjzFocusNodeExit(FocusDirection.Right, MainTopNavFocusEntry.ContentLeftEntry.entryId),
        WjzFocusNodeExit.cancel(FocusDirection.Up),
        WjzFocusNodeExit.cancel(FocusDirection.Down)
    )
}

private fun resolveTopNavUserExit(
    request: WjzFocusExitRequest,
    onContentEntryRequested: (MainTopNavContentEntryTarget) -> Boolean
): WjzFocusEntryResolution {
    return when (request.targetEntryId) {
        MainTopNavFocusEntry.ContentLeftEntry.entryId -> topNavContentEntry(
            request = request,
            target = MainTopNavContentEntryTarget.LeftEntry,
            onContentEntryRequested = onContentEntryRequested
        )

        MainTopNavFocusEntry.ContentRightEntry.entryId -> topNavContentEntry(
            request = request,
            target = MainTopNavContentEntryTarget.RightEntry,
            onContentEntryRequested = onContentEntryRequested
        )

        else -> WjzFocusEntryResolution.Reject
    }
}

private fun topNavContentEntry(
    request: WjzFocusExitRequest,
    target: MainTopNavContentEntryTarget,
    onContentEntryRequested: (MainTopNavContentEntryTarget) -> Boolean
): WjzFocusEntryResolution {
    return if (onContentEntryRequested(target)) {
        WjzFocusEntryResolution.Pending(
            entryId = request.targetEntryId,
            layer = WjzFocusLayer.Content,
            scopeId = null
        )
    } else {
        WjzFocusEntryResolution.Reject
    }
}

internal fun MainTopNavContentEntryTarget.toMainContentEntryTarget(): MainContentEntryTarget {
    return when (this) {
        MainTopNavContentEntryTarget.LeftEntry -> MainContentEntryTarget.LeftEntry
        MainTopNavContentEntryTarget.RightEntry -> MainContentEntryTarget.RightEntry
    }
}
