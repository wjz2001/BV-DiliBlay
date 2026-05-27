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
import dev.aaa1115910.bv.wjzfocus.down
import dev.aaa1115910.bv.wjzfocus.left
import dev.aaa1115910.bv.wjzfocus.right
import dev.aaa1115910.bv.wjzfocus.up
import dev.aaa1115910.bv.wjzfocus.wjzFocus
import dev.aaa1115910.bv.wjzfocus.wjzFocusRouter
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
        modifier = modifier.wjzFocus(
            id = "user",
            layer = WjzFocusLayer.TopNav,
            fallback = true,
            enabled = focusEnabled,
            exits = {
                left move "main/content/right-entry"
                right move "main/content/left-entry"
                cancel(up)
                cancel(down)
            },
            onExit = wjzFocusRouter(layer = WjzFocusLayer.TopNav) { target ->
                when (target) {
                    "main/content/left-entry" -> topNavContentEntry(
                        target = MainTopNavContentEntryTarget.LeftEntry,
                        entryId = "main/content/left-entry",
                        onContentEntryRequested = onContentEntryRequested
                    )

                    "main/content/right-entry" -> topNavContentEntry(
                        target = MainTopNavContentEntryTarget.RightEntry,
                        entryId = "main/content/right-entry",
                        onContentEntryRequested = onContentEntryRequested
                    )

                    else -> reject()
                }
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

private fun topNavContentEntry(
    target: MainTopNavContentEntryTarget,
    entryId: String,
    onContentEntryRequested: (MainTopNavContentEntryTarget) -> Boolean
): WjzFocusEntryResolution {
    return if (onContentEntryRequested(target)) {
        WjzFocusEntryResolution.Pending(
            entryId = WjzFocusEntryId(entryId),
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
