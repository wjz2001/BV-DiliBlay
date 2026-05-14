package dev.aaa1115910.bv.screen.main

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import dev.aaa1115910.bv.component.TopNav
import dev.aaa1115910.bv.component.TopNavItem
import dev.aaa1115910.bv.screen.main.common.MainContentEntryRequest

private enum class LiveTopNavItem : TopNavItem {
    Live;

    override fun getDisplayName(context: Context): String {
        return "直播"
    }
}

@Composable
fun LiveContent(
    navFocusRequester: FocusRequester,
    drawerFocusRequester: FocusRequester,
    topBarLeadingContent: @Composable () -> Unit,
    pendingDrawerEntryRequest: MainContentEntryRequest? = null,
    onDrawerEntryConsumed: (Long) -> Unit = {},
    onDefaultFocusReady: (() -> Unit)? = null,
    active: Boolean = true
) {
    LaunchedEffect(active) {
        if (active) {
            onDefaultFocusReady?.invoke()
        }
    }

    LaunchedEffect(pendingDrawerEntryRequest?.id, active) {
        if (!active) return@LaunchedEffect
        val request = pendingDrawerEntryRequest ?: return@LaunchedEffect
        navFocusRequester.requestFocus()
        onDrawerEntryConsumed(request.id)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TopNav(
            leadingContent = topBarLeadingContent,
            items = LiveTopNavItem.entries,
            selectedItem = LiveTopNavItem.Live,
            defaultFocusRequester = navFocusRequester,
            onDefaultFocusReady = { onDefaultFocusReady?.invoke() },
            backFocusEnabled = active
        )
    }
}
