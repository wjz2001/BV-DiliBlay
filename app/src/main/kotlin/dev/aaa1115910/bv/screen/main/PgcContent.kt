package dev.aaa1115910.bv.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.component.PgcTopNavItem
import dev.aaa1115910.bv.component.TopNav
import dev.aaa1115910.bv.component.PersistLazyListViewportEffect
import dev.aaa1115910.bv.wjzfocus.rememberWjzFocusRequester
import dev.aaa1115910.bv.component.rememberRestoredLazyListState
import dev.aaa1115910.bv.screen.main.pgc.AnimeContent
import dev.aaa1115910.bv.screen.main.pgc.DocumentaryContent
import dev.aaa1115910.bv.screen.main.pgc.GuoChuangContent
import dev.aaa1115910.bv.screen.main.pgc.MovieContent
import dev.aaa1115910.bv.screen.main.pgc.TvContent
import dev.aaa1115910.bv.screen.main.pgc.VarietyContent
import dev.aaa1115910.bv.screen.main.common.MainContentEntryRequest
import dev.aaa1115910.bv.screen.main.common.mainContentEntryAdapter
import dev.aaa1115910.bv.screen.main.runtime.ContentRuntimeState
import dev.aaa1115910.bv.screen.main.runtime.runtimeContainerInputEnabled
import dev.aaa1115910.bv.viewmodel.main.PgcContentViewModel
import dev.aaa1115910.bv.viewmodel.pgc.PgcAnimeViewModel
import dev.aaa1115910.bv.viewmodel.pgc.PgcDocumentaryViewModel
import dev.aaa1115910.bv.viewmodel.pgc.PgcGuoChuangViewModel
import dev.aaa1115910.bv.viewmodel.pgc.PgcMovieViewModel
import dev.aaa1115910.bv.viewmodel.pgc.PgcTvViewModel
import dev.aaa1115910.bv.viewmodel.pgc.PgcVarietyViewModel
import dev.aaa1115910.bv.viewmodel.pgc.PgcViewModel
import org.koin.androidx.compose.koinViewModel

private val PgcTopNavNodeId = WjzFocusNodeId("main/pgc/top-nav")

@Composable
fun PgcContent(
    topBarLeadingContent: @Composable () -> Unit,
    entryRequest: MainContentEntryRequest? = null,
    onEntryRequestReady: (Long) -> Unit = {},
    onEntryRequestConsumed: (Long) -> Unit = {},
    onEntryRequestRejected: (Long) -> Unit = {},
    onDefaultFocusReady: (() -> Unit)? = null,
    pgcContentViewModel: PgcContentViewModel = koinViewModel(),
    active: Boolean = true
) {
    val focusedTab = pgcContentViewModel.focusedTab
    val activeTab = pgcContentViewModel.activeTab
    val topNavContentFocusRequester = rememberWjzFocusRequester()
    var contentReadyTab by remember { mutableStateOf<PgcTopNavItem?>(null) }
    var previousActiveTab by remember { mutableStateOf<PgcTopNavItem?>(null) }
    val focusCoordinator = LocalWjzFocusCoordinator.current
    val focusScopeId = LocalWjzFocusScopeId.current
    val entryAdapter = mainContentEntryAdapter(
        entryRequest = entryRequest,
        active = active,
        onDefaultFocusReady = onDefaultFocusReady,
        onEntryRequestReady = onEntryRequestReady,
        onEntryRequestConsumed = onEntryRequestConsumed,
        onEntryRequestRejected = onEntryRequestRejected
    )
    val entryFocusRequest = entryAdapter.topNavEntryFocusRequest

    fun requestTopNavFocus(): Boolean {
        val coordinator = focusCoordinator
        if (coordinator != null) {
            return coordinator.enqueueRequestFocus(
                nodeId = PgcTopNavNodeId,
                layer = WjzFocusLayer.Content,
                scopeId = focusScopeId
            )
        }
        return false
    }

    val handleDefaultFocusReady: (Any) -> Unit = handleDefaultFocusReady@{ readyKey ->
        if (!active) return@handleDefaultFocusReady
        entryAdapter.onDefaultFocusReady(entryFocusRequest)
    }

    LaunchedEffect(active, activeTab) {
        if (!active) {
            pgcContentViewModel.updateRuntimeState(activeTab, ContentRuntimeState.Frozen)
            return@LaunchedEffect
        }
        contentReadyTab = null
        previousActiveTab
            ?.takeIf { it != activeTab }
            ?.let { pgcContentViewModel.updateRuntimeState(it, ContentRuntimeState.Frozen) }
        previousActiveTab = activeTab

        when (pgcContentViewModel.runtimeStateOf(activeTab)) {
            ContentRuntimeState.Active,
            ContentRuntimeState.Frozen -> pgcContentViewModel.updateRuntimeState(
                activeTab,
                ContentRuntimeState.Active
            )

            else -> {
                pgcContentViewModel.updateRuntimeState(activeTab, ContentRuntimeState.Shell)
                withFrameNanos { }
                pgcContentViewModel.updateRuntimeState(activeTab, ContentRuntimeState.Active)
            }
        }
    }

    Scaffold(
        modifier = Modifier,
        topBar = {
            key(entryFocusRequest?.id) {
                TopNav(
                    modifier = Modifier.padding(end = 80.dp),
                    leadingContent = topBarLeadingContent,
                    items = PgcTopNavItem.entries,
                    selectedItem = focusedTab,
                    entryFocusTarget = entryAdapter.topNavEntryFocusTarget,
                    onDefaultFocusReady = handleDefaultFocusReady,
                    onEntryFocusResolution = { resolution ->
                        entryAdapter.onTopNavEntryFocusResolution(entryFocusRequest, resolution)
                    },
                    onEntryFocusConsumed = { consumed ->
                        entryAdapter.onTopNavEntryFocusConsumed(entryFocusRequest, consumed)
                    },
                    contentFocusRequester = topNavContentFocusRequester,
                    contentFocusReadyKey = contentReadyTab,
                    focusNodeId = PgcTopNavNodeId,
                    onContentFocusRequested = { nav ->
                        val target = nav as PgcTopNavItem
                        if (target != activeTab) {
                            pgcContentViewModel.onTabClicked(target)
                        }
                    },
                    onSelectedChanged = { nav ->
                        pgcContentViewModel.onTabFocused(nav as PgcTopNavItem)
                    },
                    onClick = { nav ->
                        val target = nav as PgcTopNavItem
                        val shouldReload = target == activeTab
                        pgcContentViewModel.onTabClicked(target)
                        if (shouldReload) {
                            pgcContentViewModel.requestUserRefresh(target)
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
                .onPreviewKeyEvent {
                    if (it.isMenuKey()) {
                        if (it.isKeyDown()) return@onPreviewKeyEvent true
                        pgcContentViewModel.requestUserRefresh(activeTab)
                        requestTopNavFocus()
                        return@onPreviewKeyEvent true
                    }
                    false
                }
        ) {
            val mountedTabs = PgcTopNavItem.entries.filter { tab ->
                val runtimeState = pgcContentViewModel.runtimeStateOf(tab)
                runtimeState == ContentRuntimeState.Active ||
                        runtimeState == ContentRuntimeState.Frozen ||
                        tab == activeTab
            }
            mountedTabs.forEach { tab ->
                val runtimeState = pgcContentViewModel.runtimeStateOf(tab)
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
                        PgcTabShell()
                    }

                    if (runtimeState == ContentRuntimeState.Active ||
                        runtimeState == ContentRuntimeState.Frozen
                    ) {
                        key(tab) {
                            PgcActiveTabContent(
                                tab = tab,
                                pgcContentViewModel = pgcContentViewModel,
                                contentEntryFocusRequester = topNavContentFocusRequester,
                                tabFocusRequester = null,
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
private fun PgcTabShell() {
    Box(modifier = Modifier.fillMaxSize())
}

@Composable
private fun PgcActiveTabContent(
    tab: PgcTopNavItem,
    pgcContentViewModel: PgcContentViewModel,
    contentEntryFocusRequester: FocusRequester?,
    tabFocusRequester: FocusRequester?,
    onContentEntryReady: () -> Unit,
    active: Boolean
) {
    val activeContentEntryFocusRequester = contentEntryFocusRequester?.takeIf { active }
    val activeTabFocusRequester = tabFocusRequester?.takeIf { active }

    when (tab) {
        PgcTopNavItem.Anime -> {
            val pgcViewModel: PgcAnimeViewModel = koinViewModel<PgcAnimeViewModel>()
            PgcTabContent(
                tab = tab,
                pgcContentViewModel = pgcContentViewModel,
                pgcViewModel = pgcViewModel,
                contentEntryFocusRequester = activeContentEntryFocusRequester,
                tabFocusRequester = activeTabFocusRequester,
                onContentEntryReady = onContentEntryReady,
                active = active
            ) { lazyListState ->
                AnimeContent(
                    lazyListState = lazyListState,
                    pgcViewModel = pgcViewModel,
                    contentEntryFocusRequester = activeContentEntryFocusRequester,
                    tabFocusRequester = activeTabFocusRequester,
                    onContentEntryReady = onContentEntryReady,
                    active = active
                )
            }
        }

        PgcTopNavItem.GuoChuang -> {
            val pgcViewModel: PgcGuoChuangViewModel = koinViewModel<PgcGuoChuangViewModel>()
            PgcTabContent(
                tab = tab,
                pgcContentViewModel = pgcContentViewModel,
                pgcViewModel = pgcViewModel,
                contentEntryFocusRequester = activeContentEntryFocusRequester,
                tabFocusRequester = activeTabFocusRequester,
                onContentEntryReady = onContentEntryReady,
                active = active
            ) { lazyListState ->
                GuoChuangContent(
                    lazyListState = lazyListState,
                    pgcViewModel = pgcViewModel,
                    contentEntryFocusRequester = activeContentEntryFocusRequester,
                    tabFocusRequester = activeTabFocusRequester,
                    onContentEntryReady = onContentEntryReady,
                    active = active
                )
            }
        }

        PgcTopNavItem.Movie -> {
            val pgcViewModel: PgcMovieViewModel = koinViewModel<PgcMovieViewModel>()
            PgcTabContent(
                tab = tab,
                pgcContentViewModel = pgcContentViewModel,
                pgcViewModel = pgcViewModel,
                contentEntryFocusRequester = activeContentEntryFocusRequester,
                tabFocusRequester = activeTabFocusRequester,
                onContentEntryReady = onContentEntryReady,
                active = active
            ) { lazyListState ->
                MovieContent(
                    lazyListState = lazyListState,
                    pgcViewModel = pgcViewModel,
                    contentEntryFocusRequester = activeContentEntryFocusRequester,
                    tabFocusRequester = activeTabFocusRequester,
                    onContentEntryReady = onContentEntryReady,
                    active = active
                )
            }
        }

        PgcTopNavItem.Documentary -> {
            val pgcViewModel: PgcDocumentaryViewModel = koinViewModel<PgcDocumentaryViewModel>()
            PgcTabContent(
                tab = tab,
                pgcContentViewModel = pgcContentViewModel,
                pgcViewModel = pgcViewModel,
                contentEntryFocusRequester = activeContentEntryFocusRequester,
                tabFocusRequester = activeTabFocusRequester,
                onContentEntryReady = onContentEntryReady,
                active = active
            ) { lazyListState ->
                DocumentaryContent(
                    lazyListState = lazyListState,
                    pgcViewModel = pgcViewModel,
                    contentEntryFocusRequester = activeContentEntryFocusRequester,
                    tabFocusRequester = activeTabFocusRequester,
                    onContentEntryReady = onContentEntryReady,
                    active = active
                )
            }
        }

        PgcTopNavItem.Tv -> {
            val pgcViewModel: PgcTvViewModel = koinViewModel<PgcTvViewModel>()
            PgcTabContent(
                tab = tab,
                pgcContentViewModel = pgcContentViewModel,
                pgcViewModel = pgcViewModel,
                contentEntryFocusRequester = activeContentEntryFocusRequester,
                tabFocusRequester = activeTabFocusRequester,
                onContentEntryReady = onContentEntryReady,
                active = active
            ) { lazyListState ->
                TvContent(
                    lazyListState = lazyListState,
                    pgcViewModel = pgcViewModel,
                    contentEntryFocusRequester = activeContentEntryFocusRequester,
                    tabFocusRequester = activeTabFocusRequester,
                    onContentEntryReady = onContentEntryReady,
                    active = active
                )
            }
        }

        PgcTopNavItem.Variety -> {
            val pgcViewModel: PgcVarietyViewModel = koinViewModel<PgcVarietyViewModel>()
            PgcTabContent(
                tab = tab,
                pgcContentViewModel = pgcContentViewModel,
                pgcViewModel = pgcViewModel,
                contentEntryFocusRequester = activeContentEntryFocusRequester,
                tabFocusRequester = activeTabFocusRequester,
                onContentEntryReady = onContentEntryReady,
                active = active
            ) { lazyListState ->
                VarietyContent(
                    lazyListState = lazyListState,
                    pgcViewModel = pgcViewModel,
                    contentEntryFocusRequester = activeContentEntryFocusRequester,
                    tabFocusRequester = activeTabFocusRequester,
                    onContentEntryReady = onContentEntryReady,
                    active = active
                )
            }
        }
    }
}

@Composable
private fun PgcTabContent(
    tab: PgcTopNavItem,
    pgcContentViewModel: PgcContentViewModel,
    pgcViewModel: PgcViewModel,
    contentEntryFocusRequester: FocusRequester?,
    tabFocusRequester: FocusRequester?,
    onContentEntryReady: () -> Unit,
    active: Boolean,
    content: @Composable (LazyListState) -> Unit
) {
    val lazyListState = rememberRestoredLazyListState(pgcContentViewModel.viewportOf(tab))
    val refreshSerial = pgcContentViewModel.refreshSerialOf(tab)
    var consumedRefreshSerial by remember(tab) { mutableStateOf(0L) }

    PersistLazyListViewportEffect(lazyListState) { index, offset ->
        pgcContentViewModel.updateViewport(tab, index, offset)
    }

    LaunchedEffect(pgcViewModel, active) {
        pgcViewModel.updateRuntimeState(
            if (active) ContentRuntimeState.Active else ContentRuntimeState.Frozen
        )
    }

    LaunchedEffect(refreshSerial, active) {
        if (!active) return@LaunchedEffect
        if (refreshSerial > consumedRefreshSerial) {
            consumedRefreshSerial = refreshSerial
            pgcViewModel.reloadAll()
        }
    }

    content(lazyListState)
}
