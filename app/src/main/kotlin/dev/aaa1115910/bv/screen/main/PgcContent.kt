package dev.aaa1115910.bv.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.aaa1115910.bv.wjzfocus.WjzFocusComponentId
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusRestoreStrategy
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusTopologyRegionRef
import dev.aaa1115910.bv.wjzfocus.LocalWjzDisabledFocusContext
import dev.aaa1115910.bv.wjzfocus.WjzDisabledFocusContext
import dev.aaa1115910.bv.wjzfocus.defaultEntry
import dev.aaa1115910.bv.wjzfocus.up
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.wjzfocus.wjzFocusRememberTopologyRegion
import dev.aaa1115910.bv.component.PgcTopNavItem
import dev.aaa1115910.bv.component.TopNav
import dev.aaa1115910.bv.component.PersistLazyListViewportEffect
import dev.aaa1115910.bv.component.rememberRestoredLazyListState
import dev.aaa1115910.bv.screen.main.pgc.AnimeContent
import dev.aaa1115910.bv.screen.main.pgc.DocumentaryContent
import dev.aaa1115910.bv.screen.main.pgc.GuoChuangContent
import dev.aaa1115910.bv.screen.main.pgc.MovieContent
import dev.aaa1115910.bv.screen.main.pgc.TvContent
import dev.aaa1115910.bv.screen.main.pgc.VarietyContent
import dev.aaa1115910.bv.screen.main.common.MainContentEntryRequest
import dev.aaa1115910.bv.screen.main.common.MainTopNavDefaultEntryId
import dev.aaa1115910.bv.screen.main.common.mainContentEntryAdapter
import dev.aaa1115910.bv.screen.main.common.toTopNavFocusRequest
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

private val PgcTopNavScopeId = WjzFocusScopeId("main/pgc/top-nav")
private val PgcTopNavComponentId = WjzFocusComponentId("pgcTopNav")
private val PgcTopNavEntryId = PgcTopNavComponentId.defaultEntry()
private val PgcContentNodeId = WjzFocusNodeId("main/pgc/content")

@Composable
fun PgcContent(
    topBarLeadingContent: @Composable () -> Unit,
    topNavTopologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
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
    var contentReadyTab by remember { mutableStateOf<PgcTopNavItem?>(null) }
    var previousActiveTab by remember { mutableStateOf<PgcTopNavItem?>(null) }
    val entryAdapter = mainContentEntryAdapter(
        entryRequest = entryRequest,
        active = active,
        onDefaultFocusReady = onDefaultFocusReady,
        onEntryRequestReady = onEntryRequestReady,
        onEntryRequestConsumed = onEntryRequestConsumed,
        onEntryRequestRejected = onEntryRequestRejected
    )
    val entryFocusRequest = entryAdapter.topNavEntryFocusRequest
    val topology = wjzFocusRememberTopologyRegion(topologyRegion)
    val topologyContentNodeExits = topology.nodeExits
        .filterNot { exit -> exit.direction in up.directions }

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
                    entryFocusRequest = entryFocusRequest?.toTopNavFocusRequest(),
                    entryFocusTarget = entryAdapter.topNavEntryFocusTarget,
                    initialFocusEnabled = active && entryRequest == null,
                    leadingContentEntryId = MainTopNavDefaultEntryId,
                    topologyRegion = topNavTopologyRegion,
                    onDefaultFocusReady = handleDefaultFocusReady,
                    onEntryFocusResolution = { resolution ->
                        entryAdapter.onTopNavEntryFocusResolution(entryFocusRequest, resolution)
                    },
                    onEntryFocusConsumed = { consumed ->
                        entryAdapter.onTopNavEntryFocusConsumed(entryFocusRequest, consumed)
                    },
                    contentFocusEnabled = true,
                    contentFocusReadyKey = contentReadyTab,
                    focusScopeId = PgcTopNavScopeId,
                    focusComponentId = PgcTopNavComponentId,
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
                .wjzFocusExits(
                    nodeId = PgcContentNodeId,
                    layer = WjzFocusLayer.Content,
                    strategy = WjzFocusRestoreStrategy.Container,
                    enabled = active,
                    exits = {
                        up move PgcTopNavEntryId
                        addAll(topologyContentNodeExits)
                    }
                )
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
                val contentZIndex = if (visible) 1f else 0f

                CompositionLocalProvider(
                    LocalWjzDisabledFocusContext provides WjzDisabledFocusContext(
                        group = tab,
                        zIndex = contentZIndex
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(contentZIndex)
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
}

@Composable
private fun PgcTabShell() {
    Box(modifier = Modifier.fillMaxSize())
}

@Composable
private fun PgcActiveTabContent(
    tab: PgcTopNavItem,
    pgcContentViewModel: PgcContentViewModel,
    onContentEntryReady: () -> Unit,
    active: Boolean
) {
    when (tab) {
        PgcTopNavItem.Anime -> {
            val pgcViewModel: PgcAnimeViewModel = koinViewModel<PgcAnimeViewModel>()
            PgcTabContent(
                tab = tab,
                pgcContentViewModel = pgcContentViewModel,
                pgcViewModel = pgcViewModel,
                onContentEntryReady = onContentEntryReady,
                active = active
            ) { lazyListState ->
                AnimeContent(
                    lazyListState = lazyListState,
                    pgcViewModel = pgcViewModel,
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
                onContentEntryReady = onContentEntryReady,
                active = active
            ) { lazyListState ->
                GuoChuangContent(
                    lazyListState = lazyListState,
                    pgcViewModel = pgcViewModel,
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
                onContentEntryReady = onContentEntryReady,
                active = active
            ) { lazyListState ->
                MovieContent(
                    lazyListState = lazyListState,
                    pgcViewModel = pgcViewModel,
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
                onContentEntryReady = onContentEntryReady,
                active = active
            ) { lazyListState ->
                DocumentaryContent(
                    lazyListState = lazyListState,
                    pgcViewModel = pgcViewModel,
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
                onContentEntryReady = onContentEntryReady,
                active = active
            ) { lazyListState ->
                TvContent(
                    lazyListState = lazyListState,
                    pgcViewModel = pgcViewModel,
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
                onContentEntryReady = onContentEntryReady,
                active = active
            ) { lazyListState ->
                VarietyContent(
                    lazyListState = lazyListState,
                    pgcViewModel = pgcViewModel,
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
    onContentEntryReady: () -> Unit,
    active: Boolean,
    content: @Composable (LazyListState) -> Unit
) {
    val lazyListState = rememberRestoredLazyListState(pgcContentViewModel.viewportOf(tab))
    val refreshSerial = pgcContentViewModel.refreshSerialOf(tab)
    var consumedRefreshSerial by remember(tab) { mutableLongStateOf(0L) }

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
