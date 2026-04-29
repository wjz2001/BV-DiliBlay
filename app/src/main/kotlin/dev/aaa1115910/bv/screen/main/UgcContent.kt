package dev.aaa1115910.bv.screen.main

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.component.TopNav
import dev.aaa1115910.bv.component.UgcTopNavItem
import dev.aaa1115910.bv.component.PersistLazyGridViewportEffect
import dev.aaa1115910.bv.component.rememberRestoredLazyGridState
import dev.aaa1115910.bv.entity.state.GridViewportState
import dev.aaa1115910.bv.screen.main.ugc.UgcRegionScaffold
import dev.aaa1115910.bv.screen.main.ugc.UgcScaffoldState
import dev.aaa1115910.bv.screen.main.common.MainContentEntryRequest
import dev.aaa1115910.bv.screen.main.common.MainContentFocusTarget
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.ugc.UgcViewModel
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun UgcContent(
    navFocusRequester: FocusRequester,
    drawerFocusRequester: FocusRequester,
    pendingDrawerEntryRequest: MainContentEntryRequest? = null,
    onDrawerEntryConsumed: (Long) -> Unit = {},
    onDefaultFocusReady: (() -> Unit)? = null,
    ugcViewModel: UgcViewModel = koinViewModel(),
    toViewViewModel: ToViewViewModel = koinViewModel(),
) {
    val context = LocalContext.current

    val focusedTab = ugcViewModel.focusedTab
    val activeTab = ugcViewModel.activeTab
    var focusOnContent by remember { mutableStateOf(false) }
    val ugcTopNavItems = UgcTopNavItem.entries
    var topNavReadyTab by remember { mutableStateOf<UgcTopNavItem?>(null) }

    val desiredDrawerEntryTab = remember(pendingDrawerEntryRequest?.id) {
        when (pendingDrawerEntryRequest?.target) {
            MainContentFocusTarget.LeftEntry -> ugcTopNavItems.firstOrNull()
            MainContentFocusTarget.RightEntry -> ugcTopNavItems.lastOrNull()
            null -> null
        }
    }

    LaunchedEffect(pendingDrawerEntryRequest?.id, desiredDrawerEntryTab) {
        val desired = desiredDrawerEntryTab ?: return@LaunchedEffect
        if (topNavReadyTab != desired) {
            topNavReadyTab = null
        }
    }

    LaunchedEffect(
        pendingDrawerEntryRequest?.id,
        desiredDrawerEntryTab,
        activeTab,
        focusedTab
    ) {
        val desired = desiredDrawerEntryTab ?: return@LaunchedEffect
        if (activeTab != desired || focusedTab != desired) {
            ugcViewModel.onTabClicked(desired)
        }
    }

    LaunchedEffect(
        pendingDrawerEntryRequest?.id,
        desiredDrawerEntryTab,
        activeTab,
        focusedTab,
        topNavReadyTab
    ) {
        val request = pendingDrawerEntryRequest ?: return@LaunchedEffect
        val desired = desiredDrawerEntryTab ?: return@LaunchedEffect

        if (activeTab == desired &&
            focusedTab == desired &&
            topNavReadyTab == desired
        ) {
            navFocusRequester.requestFocus()
            onDrawerEntryConsumed(request.id)
        }
    }

    val handleDefaultFocusReady: () -> Unit = {
        topNavReadyTab = focusedTab
        onDefaultFocusReady?.invoke()
    }

    LaunchedEffect(activeTab) {
        if (activeTab !in ugcViewModel.ugcScaffoldStateMap) {
            ugcViewModel.addUgcScaffoldState(
                activeTab,
                UgcScaffoldState(ugcType = activeTab.ugcType)
            )
        }
        ugcViewModel.ensureLoaded(activeTab)
        ugcViewModel.trimInactiveData(except = activeTab)
    }

    LaunchedEffect(Unit) {
        toViewViewModel.uiEvent.collect { event ->
            when (event) {
                is UiEffect.ShowToast -> {
                    event.message.toast(context)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier,
        topBar = {
            TopNav(
                modifier = Modifier.padding(horizontal = 10.dp),
                items = ugcTopNavItems,
                isLargePadding = !focusOnContent,
                selectedItem = focusedTab,
                defaultFocusRequester = navFocusRequester,
                onDefaultFocusReady = handleDefaultFocusReady,
                onLeftBoundaryExit = { drawerFocusRequester.requestFocus() },
                onRightBoundaryExit = { drawerFocusRequester.requestFocus() },
                onSelectedChanged = { nav ->
                    ugcViewModel.onTabFocused(nav as UgcTopNavItem)
                },
                onClick = { nav ->
                    val target = nav as UgcTopNavItem
                    ugcViewModel.onTabClicked(target)
                    ugcViewModel.reloadAll(target)
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .onFocusChanged { focusOnContent = it.hasFocus }
                .onPreviewKeyEvent {
                    if (it.key == Key.Menu) {
                        if (it.type == KeyEventType.KeyDown) return@onPreviewKeyEvent true
                        ugcViewModel.reloadAll(activeTab)
                        navFocusRequester.requestFocus()
                        return@onPreviewKeyEvent true
                    }
                    false
                },
        ) {
            val screen = activeTab
            val range = (screen.ordinal)..minOf(screen.ordinal + 2, ugcTopNavItems.size - 1)
            for (i in range) {
                val item = ugcTopNavItems[i]
                if (item !in ugcViewModel.ugcScaffoldStateMap) {
                    Log.d("UgcContent", "rememberUgcScaffoldState: $item")
                    ugcViewModel.addUgcScaffoldState(
                        item,
                        UgcScaffoldState(
                            ugcType = item.ugcType
                        )
                    )
                }
            }

            val screenState = ugcViewModel.ugcScaffoldStateMap[screen]
            if (screenState != null) {
                val gridState = rememberRestoredLazyGridState(
                    GridViewportState(
                        index = screenState.firstVisibleItemIndex,
                        scrollOffset = screenState.firstVisibleItemScrollOffset
                    )
                )

                PersistLazyGridViewportEffect(gridState) { index, offset ->
                    ugcViewModel.updateViewport(screen, index, offset)
                }

                UgcRegionScaffold(
                    state = screenState,
                    gridState = gridState,
                    onLoadMore = { ugcViewModel.loadMoreData(screen) },
                    onAddWatchLater = { aid ->
                        toViewViewModel.addToView(aid)
                    },
                    onGoToDetailPage = { aid ->
                        VideoInfoActivity.actionStart(
                            context = context,
                            fromController = true,
                            aid = aid
                        )
                    },
                    onGoToUpPage = { mid, upName ->
                        UpInfoActivity.actionStart(context, mid, upName)
                    }
                )
            }
        }
    }
}