package dev.aaa1115910.bv.screen.main

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import dev.aaa1115910.bv.wjzfocus.WjzFocusComponentId
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.component.TopNav
import dev.aaa1115910.bv.component.TopNavItem
import dev.aaa1115910.bv.screen.main.common.MainContentEntryRequest
import dev.aaa1115910.bv.screen.main.common.mainContentEntryAdapter

private enum class LiveTopNavItem : TopNavItem {
    Live;

    override fun getDisplayName(context: Context): String {
        return "直播"
    }
}

private val LiveTopNavScopeId = WjzFocusScopeId("main/live/top-nav")
private val LiveTopNavComponentId = WjzFocusComponentId("liveTopNav")

@Composable
fun LiveContent(
    topBarLeadingContent: @Composable () -> Unit,
    entryRequest: MainContentEntryRequest? = null,
    onEntryRequestReady: (Long) -> Unit = {},
    onEntryRequestConsumed: (Long) -> Unit = {},
    onEntryRequestRejected: (Long) -> Unit = {},
    onDefaultFocusReady: (() -> Unit)? = null,
    active: Boolean = true
) {
    val entryAdapter = mainContentEntryAdapter(
        entryRequest = entryRequest,
        active = active,
        onDefaultFocusReady = onDefaultFocusReady,
        onEntryRequestReady = onEntryRequestReady,
        onEntryRequestConsumed = onEntryRequestConsumed,
        onEntryRequestRejected = onEntryRequestRejected
    )
    val entryFocusRequest = entryAdapter.topNavEntryFocusRequest

    Box(modifier = Modifier.fillMaxSize()) {
        key(entryFocusRequest?.id) {
            TopNav(
                leadingContent = topBarLeadingContent,
                items = LiveTopNavItem.entries,
                selectedItem = LiveTopNavItem.Live,
                entryFocusTarget = entryAdapter.topNavEntryFocusTarget,
                onDefaultFocusReady = { entryAdapter.onDefaultFocusReady(entryFocusRequest) },
                onEntryFocusResolution = { resolution ->
                    entryAdapter.onTopNavEntryFocusResolution(entryFocusRequest, resolution)
                },
                onEntryFocusConsumed = { consumed ->
                    entryAdapter.onTopNavEntryFocusConsumed(entryFocusRequest, consumed)
                },
                focusScopeId = LiveTopNavScopeId,
                focusComponentId = LiveTopNavComponentId,
                focusLayer = WjzFocusLayer.Content,
                backFocusEnabled = active
            )
        }
    }
}
