package dev.aaa1115910.bv.screen.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import dev.aaa1115910.bv.screen.main.common.MainContentEntryTarget
import dev.aaa1115910.bv.screen.main.common.MainContentTopEntryId
import dev.aaa1115910.bv.screen.main.common.MainTopNavDefaultEntryId
import dev.aaa1115910.bv.screen.main.common.MainTopNavFocusComponentId
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusRequestResult
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.down
import dev.aaa1115910.bv.wjzfocus.left
import dev.aaa1115910.bv.wjzfocus.right
import dev.aaa1115910.bv.wjzfocus.up
import dev.aaa1115910.bv.wjzfocus.WjzFocusLocalEntrySurface
import dev.aaa1115910.bv.wjzfocus.WjzFocusTopologyRegionRef
import dev.aaa1115910.bv.wjzfocus.submitExternalEntryFocus
import dev.aaa1115910.bv.wjzfocus.wjzFocusLocal
import dev.aaa1115910.bv.wjzfocus.wjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.wjzFocusRememberTopologyRegion

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

internal val MainTopNavDefaultLocalId = wjzFocusLocalId("default")

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
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    onUserFocusChanged: (Boolean) -> Unit,
    onExpandDrawer: () -> Unit,
    onOpenUser: () -> Unit,
    onContentEntryRequested: (MainTopNavContentEntryTarget) -> Boolean
) {
    val focusCoordinator = LocalWjzFocusCoordinator.current
    val topology = wjzFocusRememberTopologyRegion(topologyRegion)

    LaunchedEffect(entryRequest, focusCoordinator) {
        val request = entryRequest ?: return@LaunchedEffect
        val entryId = when (request.target) {
            MainTopNavEntryTarget.User -> MainTopNavDefaultEntryId
        }
        val result = focusCoordinator?.submitExternalEntryFocus(
            entryId = entryId,
            dedupeKey = request.id
        )
        if (result == WjzFocusRequestResult.Focused || result == WjzFocusRequestResult.Enqueued) {
            onEntryRequestConsumed(request.id)
        }
    }

    WjzFocusLocalEntrySurface(
        componentId = MainTopNavFocusComponentId,
        defaultLocalId = MainTopNavDefaultLocalId,
        layer = WjzFocusLayer.TopNav
    )

    LeftNaviUserButton(
        modifier = modifier.wjzFocusLocal(
            localId = MainTopNavDefaultLocalId,
            layer = WjzFocusLayer.TopNav,
            enabled = focusEnabled,
            exits = {
                if (topology.isStandalone) {
                    down move MainContentTopEntryId
                    right move MainContentTopEntryId
                    cancel(left)
                    cancel(up)
                } else {
                    addAll(topology.nodeExits)
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

internal fun MainTopNavContentEntryTarget.toMainContentEntryTarget(): MainContentEntryTarget {
    return when (this) {
        MainTopNavContentEntryTarget.LeftEntry -> MainContentEntryTarget.LeftEntry
        MainTopNavContentEntryTarget.RightEntry -> MainContentEntryTarget.TopEntry
    }
}
