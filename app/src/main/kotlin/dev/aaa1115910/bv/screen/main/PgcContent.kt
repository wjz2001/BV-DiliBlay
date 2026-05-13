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
import dev.aaa1115910.bv.screen.main.common.MainContentFocusTarget
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

@Composable
fun PgcContent(
    navFocusRequester: FocusRequester,
    drawerFocusRequester: FocusRequester,
    topBarLeadingContent: @Composable () -> Unit,
    pendingDrawerEntryRequest: MainContentEntryRequest? = null,
    onDrawerEntryConsumed: (Long) -> Unit = {},
    onDefaultFocusReady: (() -> Unit)? = null,
    active: Boolean = true,
    pgcContentViewModel: PgcContentViewModel = koinViewModel()
) {
    val focusedTab = pgcContentViewModel.focusedTab
    val activeTab = pgcContentViewModel.activeTab
    val contentEntryFocusRequester = remember { FocusRequester() }
    var topNavReadyTab by remember { mutableStateOf<PgcTopNavItem?>(null) }
    var contentReadyTab by remember { mutableStateOf<PgcTopNavItem?>(null) }
    var previousActiveTab by remember { mutableStateOf<PgcTopNavItem?>(null) }

    val desiredDrawerEntryTab = remember(pendingDrawerEntryRequest?.id) {
        when (pendingDrawerEntryRequest?.target) {
            MainContentFocusTarget.LeftEntry -> PgcTopNavItem.entries.firstOrNull()
            MainContentFocusTarget.RightEntry -> PgcTopNavItem.entries.lastOrNull()
            null -> null
        }
    }

    LaunchedEffect(pendingDrawerEntryRequest?.id, desiredDrawerEntryTab, active) {
        if (!active) return@LaunchedEffect
        val desired = desiredDrawerEntryTab ?: return@LaunchedEffect
        if (topNavReadyTab != desired) {
            topNavReadyTab = null
        }
    }

    LaunchedEffect(
        pendingDrawerEntryRequest?.id,
        desiredDrawerEntryTab,
        activeTab,
        focusedTab,
        active
    ) {
        if (!active) return@LaunchedEffect
        val desired = desiredDrawerEntryTab ?: return@LaunchedEffect
        if (activeTab != desired || focusedTab != desired) {
            pgcContentViewModel.onTabClicked(desired)
        }
    }

    LaunchedEffect(
        pendingDrawerEntryRequest?.id,
        desiredDrawerEntryTab,
        activeTab,
        focusedTab,
        topNavReadyTab,
        active
    ) {
        if (!active) return@LaunchedEffect
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

    val handleDefaultFocusReady: (Any) -> Unit = handleDefaultFocusReady@{ readyKey ->
        if (!active) return@handleDefaultFocusReady
        topNavReadyTab = readyKey as? PgcTopNavItem
        onDefaultFocusReady?.invoke()
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
            TopNav(
                modifier = Modifier.padding(end = 80.dp),
                leadingContent = topBarLeadingContent,
                items = PgcTopNavItem.entries,
                selectedItem = focusedTab,
                entryFocusItem = desiredDrawerEntryTab,
                defaultFocusRequester = navFocusRequester,
                onDefaultFocusReady = handleDefaultFocusReady,
                contentFocusRequester = contentEntryFocusRequester,
                contentFocusReadyKey = contentReadyTab,
                onLeftBoundaryExit = { drawerFocusRequester.requestFocus() },
                onRightBoundaryExit = { drawerFocusRequester.requestFocus() },
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
                }
            )
        }
    ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                .onPreviewKeyEvent {
                    if (it.key == Key.Menu) {
                        if (it.type == KeyEventType.KeyDown) return@onPreviewKeyEvent true
                        pgcContentViewModel.requestUserRefresh(activeTab)
                        navFocusRequester.requestFocus()
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
                                contentEntryFocusRequester = contentEntryFocusRequester,
                                tabFocusRequester = navFocusRequester,
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
    contentEntryFocusRequester: FocusRequester,
    tabFocusRequester: FocusRequester,
    onContentEntryReady: () -> Unit,
    active: Boolean
) {
    val activeContentEntryFocusRequester = contentEntryFocusRequester.takeIf { active }
    val activeTabFocusRequester = tabFocusRequester.takeIf { active }

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
