package dev.aaa1115910.bv.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryId
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusRestoreStrategy
import dev.aaa1115910.bv.wjzfocus.up
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.component.TopNav
import dev.aaa1115910.bv.component.UgcTopNavItem
import dev.aaa1115910.bv.component.PersistLazyGridViewportEffect
import dev.aaa1115910.bv.component.rememberRestoredLazyGridState
import dev.aaa1115910.bv.entity.state.GridViewportState
import dev.aaa1115910.bv.screen.main.ugc.UgcRegionScaffold
import dev.aaa1115910.bv.screen.main.ugc.UgcScaffoldState
import dev.aaa1115910.bv.screen.main.common.MainContentEntryRequest
import dev.aaa1115910.bv.screen.main.common.mainContentEntryAdapter
import dev.aaa1115910.bv.screen.main.runtime.ContentRuntimeState
import dev.aaa1115910.bv.screen.main.runtime.runtimeContainerInputEnabled
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.ugc.UgcViewModel
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import org.koin.androidx.compose.koinViewModel

private val UgcTopNavNodeId = WjzFocusNodeId("main/ugc/top-nav")
private val UgcTopNavEntryId = WjzFocusEntryId.parse(
    "bv_tab_row_${UgcTopNavNodeId.value.hashCode()}"
)

@Composable
fun UgcContent(
    topBarLeadingContent: @Composable () -> Unit,
    entryRequest: MainContentEntryRequest? = null,
    onEntryRequestReady: (Long) -> Unit = {},
    onEntryRequestConsumed: (Long) -> Unit = {},
    onEntryRequestRejected: (Long) -> Unit = {},
    onDefaultFocusReady: (() -> Unit)? = null,
    ugcViewModel: UgcViewModel = koinViewModel(),
    toViewViewModel: ToViewViewModel = koinViewModel(),
    active: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val focusedTab = ugcViewModel.focusedTab
    val activeTab = ugcViewModel.activeTab
    val ugcScaffoldStateMap by ugcViewModel.ugcScaffoldStateMap.collectAsStateWithLifecycle()
    val ugcTopNavItems = UgcTopNavItem.entries
    var contentReadyTab by remember { mutableStateOf<UgcTopNavItem?>(null) }
    var previousActiveTab by remember { mutableStateOf<UgcTopNavItem?>(null) }
    val entryAdapter = mainContentEntryAdapter(
        entryRequest = entryRequest,
        active = active,
        onDefaultFocusReady = onDefaultFocusReady,
        onEntryRequestReady = onEntryRequestReady,
        onEntryRequestConsumed = onEntryRequestConsumed,
        onEntryRequestRejected = onEntryRequestRejected
    )
    val entryFocusRequest = entryAdapter.topNavEntryFocusRequest

    val handleDefaultFocusReady: (Any) -> Unit = handleDefaultFocusReady@{ readyKey ->
        if (!active) return@handleDefaultFocusReady
        entryAdapter.onDefaultFocusReady(entryFocusRequest)
    }

    LaunchedEffect(active, activeTab) {
        if (!active) {
            ugcViewModel.updateRuntimeState(activeTab, ContentRuntimeState.Frozen)
            return@LaunchedEffect
        }
        contentReadyTab = null
        previousActiveTab
            ?.takeIf { it != activeTab }
            ?.let { ugcViewModel.updateRuntimeState(it, ContentRuntimeState.Frozen) }
        previousActiveTab = activeTab

        ugcViewModel.ensureTabState(activeTab)
        when (ugcViewModel.runtimeStateOf(activeTab)) {
            ContentRuntimeState.Active,
            ContentRuntimeState.Frozen -> ugcViewModel.updateRuntimeState(
                activeTab,
                ContentRuntimeState.Active
            )

            else -> {
                ugcViewModel.updateRuntimeState(activeTab, ContentRuntimeState.Shell)
                withFrameNanos { }
                ugcViewModel.updateRuntimeState(activeTab, ContentRuntimeState.Active)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ugcViewModel.freezeAll()
        }
    }

    LaunchedEffect(lifecycleOwner, active) {
        if (!active) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            toViewViewModel.uiEvent.collect { event ->
                when (event) {
                    is UiEffect.ShowToast -> {
                        event.message.toast(context)
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier,
        topBar = {
            key(entryFocusRequest?.id) {
                TopNav(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    leadingContent = topBarLeadingContent,
                    items = ugcTopNavItems,
                    selectedItem = focusedTab,
                    entryFocusTarget = entryAdapter.topNavEntryFocusTarget,
                    onDefaultFocusReady = handleDefaultFocusReady,
                    onEntryFocusResolution = { resolution ->
                        entryAdapter.onTopNavEntryFocusResolution(entryFocusRequest, resolution)
                    },
                    onEntryFocusConsumed = { consumed ->
                        entryAdapter.onTopNavEntryFocusConsumed(entryFocusRequest, consumed)
                    },
                    contentFocusEnabled = true,
                    contentFocusReadyKey = contentReadyTab,
                    focusNodeId = UgcTopNavNodeId,
                    onContentFocusRequested = { nav ->
                        val target = nav as UgcTopNavItem
                        if (target != activeTab) {
                            ugcViewModel.onTabClicked(target)
                        }
                    },
                    onSelectedChanged = { nav ->
                        ugcViewModel.onTabFocused(nav as UgcTopNavItem)
                    },
                    onClick = { nav ->
                        val target = nav as UgcTopNavItem
                        val shouldRefresh = target == activeTab
                        ugcViewModel.onTabClicked(target)
                        if (shouldRefresh) {
                            ugcViewModel.requestUserRefresh(target)
                        }
                    },
                    focusLayer = WjzFocusLayer.Content,
                    backFocusEnabled = active
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .wjzFocusExits(
                    id = "main/ugc/content",
                    layer = WjzFocusLayer.Content,
                    strategy = WjzFocusRestoreStrategy.Container,
                    enabled = active,
                    exits = {
                        up move UgcTopNavEntryId
                    }
                ),
        ) {
            val mountedTabs = ugcTopNavItems.filter { tab ->
                val runtimeState = ugcViewModel.runtimeStateOf(tab)
                runtimeState == ContentRuntimeState.Active ||
                        runtimeState == ContentRuntimeState.Frozen ||
                        tab == activeTab
            }
            mountedTabs.forEach { tab ->
                val runtimeState = ugcViewModel.runtimeStateOf(tab)
                val visible = tab == activeTab
                val tabActive = active && visible && runtimeState == ContentRuntimeState.Active

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (visible) 1f else 0f)
                        .graphicsLayer {
                            alpha = if (visible) 1f else 0f
                        }
                        .runtimeContainerInputEnabled(tabActive)
                ) {
                    if (visible &&
                        (runtimeState == ContentRuntimeState.NotCreated ||
                                runtimeState == ContentRuntimeState.Shell)
                    ) {
                        UgcTabShell()
                    }

                    if (runtimeState == ContentRuntimeState.Active ||
                        runtimeState == ContentRuntimeState.Frozen
                    ) {
                        key(tab) {
                            UgcActiveTabContent(
                                screen = tab,
                                ugcScaffoldStateMap = ugcScaffoldStateMap,
                                ugcViewModel = ugcViewModel,
                                toViewViewModel = toViewViewModel,
                                onContentEntryReady = {
                                    if (tabActive) contentReadyTab = tab
                                },
                                active = tabActive
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun UgcTabShell() {
    Box(modifier = Modifier.fillMaxSize())
}

@Composable
private fun UgcActiveTabContent(
    screen: UgcTopNavItem,
    ugcScaffoldStateMap: Map<UgcTopNavItem, UgcScaffoldState>,
    ugcViewModel: UgcViewModel,
    toViewViewModel: ToViewViewModel,
    onContentEntryReady: () -> Unit,
    active: Boolean
) {
    val context = LocalContext.current
    val screenState = ugcScaffoldStateMap[screen]
    val refreshSerial = ugcViewModel.refreshSerialOf(screen)

    if (screenState != null) {
        val gridState = rememberRestoredLazyGridState(
            GridViewportState(
                index = screenState.firstVisibleItemIndex,
                scrollOffset = screenState.firstVisibleItemScrollOffset
            )
        )

        LaunchedEffect(screen, refreshSerial, active) {
            if (!active) return@LaunchedEffect
            if (ugcViewModel.consumeRefreshSerial(screen, refreshSerial)) {
                gridState.scrollToItem(0)
                ugcViewModel.reloadAll(screen)
            }
        }

        PersistLazyGridViewportEffect(gridState) { index, offset ->
            ugcViewModel.updateViewport(screen, index, offset)
        }

        UgcRegionScaffold(
            state = screenState,
            gridState = gridState,
            active = active,
            onContentEntryReady = onContentEntryReady,
            onLoadMore = { ugcViewModel.loadMoreData(screen) },
            onAddWatchLater = { aid ->
                toViewViewModel.addToView(aid)
            },
            onGoToUpPage = { mid, upName ->
                UpInfoActivity.actionStart(context, mid, upName)
            }
        )
    }
}
