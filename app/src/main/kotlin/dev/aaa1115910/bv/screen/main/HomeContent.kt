package dev.aaa1115910.bv.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PsychologyAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.wjzfocus.WjzFocusComponentId
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.component.HomeTopNavItem
import dev.aaa1115910.bv.wjzfocus.LocalWjzDisabledFocusContext
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.WjzDisabledFocusContext
import dev.aaa1115910.bv.wjzfocus.WjzFocusRestoreStrategy
import dev.aaa1115910.bv.wjzfocus.WjzFocusSubmitIntent
import dev.aaa1115910.bv.wjzfocus.WjzFocusTopologyRegionRef
import dev.aaa1115910.bv.wjzfocus.WjzFocusBoundaryTarget
import dev.aaa1115910.bv.wjzfocus.WjzFocusTopology
import dev.aaa1115910.bv.wjzfocus.defaultEntry
import dev.aaa1115910.bv.wjzfocus.down
import dev.aaa1115910.bv.wjzfocus.enabledIf
import dev.aaa1115910.bv.wjzfocus.left
import dev.aaa1115910.bv.wjzfocus.right
import dev.aaa1115910.bv.wjzfocus.up
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.component.PersistLazyGridViewportEffect
import dev.aaa1115910.bv.component.TopNav
import dev.aaa1115910.bv.component.TopNavLeadingIcon
import dev.aaa1115910.bv.component.TopNavItem
import dev.aaa1115910.bv.component.rememberRestoredLazyGridState
import dev.aaa1115910.bv.entity.state.GridViewportState
import dev.aaa1115910.bv.screen.main.home.DynamicsScreen
import dev.aaa1115910.bv.screen.main.home.PopularScreen
import dev.aaa1115910.bv.screen.main.home.RecommendScreen
import dev.aaa1115910.bv.screen.main.runtime.ContentRuntimeState
import dev.aaa1115910.bv.screen.main.runtime.runtimeContainerInputEnabled
import dev.aaa1115910.bv.screen.search.SearchInputScreen
import dev.aaa1115910.bv.screen.search.SearchInputDefaultEntryId
import dev.aaa1115910.bv.screen.search.SearchResultScreen
import dev.aaa1115910.bv.screen.main.home.FollowScreen
import dev.aaa1115910.bv.screen.main.home.FavoriteScreen
import dev.aaa1115910.bv.screen.main.home.FollowingSeasonScreen
import dev.aaa1115910.bv.screen.main.home.HistoryScreen
import dev.aaa1115910.bv.screen.main.home.MyClassroomScreen
import dev.aaa1115910.bv.screen.main.home.SubscribedCollectionScreen
import dev.aaa1115910.bv.screen.main.home.ToViewScreen
import dev.aaa1115910.bv.screen.main.common.MainContentEntryRequest
import dev.aaa1115910.bv.screen.main.common.MainContentTopEntryId
import dev.aaa1115910.bv.screen.main.common.MainTopNavDefaultEntryId
import dev.aaa1115910.bv.screen.main.common.mainContentEntryAdapter
import dev.aaa1115910.bv.screen.main.common.toTopNavFocusRequest
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.viewmodel.UserViewModel
import dev.aaa1115910.bv.viewmodel.main.HomeContentViewModel
import dev.aaa1115910.bv.viewmodel.search.SearchInputViewModel
import dev.aaa1115910.bv.viewmodel.search.SearchResultViewModel
import dev.aaa1115910.bv.viewmodel.user.FavoriteViewModel
import dev.aaa1115910.bv.viewmodel.user.FollowViewModel
import dev.aaa1115910.bv.viewmodel.user.FollowingSeasonViewModel
import dev.aaa1115910.bv.viewmodel.user.HistoryViewModel
import dev.aaa1115910.bv.viewmodel.user.SubscribedCollectionViewModel
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import org.koin.androidx.compose.koinViewModel

private enum class HomeSearchPage {
    Input,
    Result
}

private val HomeTopNavScopeId = WjzFocusScopeId("main/home/top-nav")
private val HomeTopNavComponentId = WjzFocusComponentId("homeTopNav")
private val HomeTopNavEntryId = HomeTopNavComponentId.defaultEntry()
private val HomeContentNodeId = WjzFocusNodeId("main/home/content")

@Composable
fun HomeContent(
    topBarLeadingContent: @Composable () -> Unit,
    topNavTopologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    entryRequest: MainContentEntryRequest? = null,
    onEntryRequestReady: (Long) -> Unit = {},
    onEntryRequestConsumed: (Long) -> Unit = {},
    onEntryRequestRejected: (Long) -> Unit = {},
    onDefaultFocusReady: (() -> Unit)? = null,
    homeContentViewModel: HomeContentViewModel,
    userViewModel: UserViewModel,
    active: Boolean = true
){
    val firstTab = remember { Prefs.firstHomeTopNavItem }
    val focusedTab = homeContentViewModel.focusedTab
    val activeTab = homeContentViewModel.activeTab
    var contentReadyTab by remember { mutableStateOf<HomeTopNavItem?>(null) }
    var searchPage by rememberSaveable { mutableStateOf(HomeSearchPage.Input) }
    var searchKeyword by rememberSaveable { mutableStateOf("") }
    var searchEnableProxy by rememberSaveable { mutableStateOf(false) }
    var previousActiveTab by remember { mutableStateOf<HomeTopNavItem?>(null) }
    val focusCoordinator = LocalWjzFocusCoordinator.current
    val entryAdapter = mainContentEntryAdapter(
        entryRequest = entryRequest,
        active = active,
        onDefaultFocusReady = onDefaultFocusReady,
        onEntryRequestReady = onEntryRequestReady,
        onEntryRequestConsumed = onEntryRequestConsumed,
        onEntryRequestRejected = onEntryRequestRejected
    )
    val entryFocusRequest = entryAdapter.topNavEntryFocusRequest

    val backToTopNav: () -> Unit = {
        focusCoordinator?.submitEntryFocusIntent(
            entryId = HomeTopNavEntryId,
            intent = WjzFocusSubmitIntent.ExternalEntry(
                dedupeKey = "home-back-to-top"
            )
        )
    }

    val reorderedItems = remember {
        Prefs.homeTopNavItems.ensureVisibleHomeTabs(firstTab)
    }
    val homeFocusedLeadingIcon: (TopNavItem) -> TopNavLeadingIcon? = remember {
        {
            when (it as? HomeTopNavItem) {
                HomeTopNavItem.Dynamics -> TopNavLeadingIcon.Vector(Icons.Rounded.DynamicFeed)
                HomeTopNavItem.History -> TopNavLeadingIcon.DrawableRes(R.drawable.add_to_list)
                HomeTopNavItem.Favorite -> TopNavLeadingIcon.Vector(Icons.Rounded.Star)
                HomeTopNavItem.ToView -> TopNavLeadingIcon.Vector(Icons.Rounded.Schedule)
                HomeTopNavItem.Recommend -> TopNavLeadingIcon.Vector(Icons.Rounded.ThumbUp)
                HomeTopNavItem.Popular -> TopNavLeadingIcon.Vector(Icons.Rounded.LocalFireDepartment)
                HomeTopNavItem.FollowingSeason -> TopNavLeadingIcon.Vector(Icons.Rounded.LiveTv)
                HomeTopNavItem.Follow -> TopNavLeadingIcon.Vector(Icons.Rounded.HowToReg)
                HomeTopNavItem.Search -> TopNavLeadingIcon.Vector(Icons.Rounded.Search)
                HomeTopNavItem.MyClassroom -> TopNavLeadingIcon.Vector(Icons.Rounded.PsychologyAlt)
                HomeTopNavItem.SubscribedCollection -> TopNavLeadingIcon.Vector(Icons.Rounded.Subscriptions)
                null -> null
            }
        }
    }

    LaunchedEffect(active, activeTab) {
        if (!active) {
            homeContentViewModel.updateRuntimeState(activeTab, ContentRuntimeState.Frozen)
            return@LaunchedEffect
        }
        val previous = previousActiveTab
        previous
            ?.takeIf { it != activeTab }
            ?.let { homeContentViewModel.updateRuntimeState(it, ContentRuntimeState.Frozen) }
        previousActiveTab = activeTab

        when (homeContentViewModel.runtimeStateOf(activeTab)) {
            ContentRuntimeState.Active,
            ContentRuntimeState.Frozen -> homeContentViewModel.updateRuntimeState(
                activeTab,
                ContentRuntimeState.Active
            )

            else -> {
                homeContentViewModel.updateRuntimeState(activeTab, ContentRuntimeState.Shell)
                withFrameNanos { }
                homeContentViewModel.updateRuntimeState(activeTab, ContentRuntimeState.Active)
                withFrameNanos { }
            }
        }
        homeContentViewModel.markContentReady(activeTab)
    }

    LaunchedEffect(userViewModel.isLogin, active) {
        if (!active) return@LaunchedEffect
        if (userViewModel.isLogin) {
            userViewModel.updateUserInfo()
        } else {
            userViewModel.clearUserInfo()
        }
    }

    val handleDefaultFocusReady: (Any) -> Unit = handleDefaultFocusReady@{ readyKey ->
        if (!active) return@handleDefaultFocusReady
        entryAdapter.onDefaultFocusReady(entryFocusRequest)
    }

    fun handleTopNavConfirmLongPress(tab: HomeTopNavItem): Boolean {
        if (activeTab != tab) return false

        return when (tab) {
            HomeTopNavItem.Dynamics,
            HomeTopNavItem.History -> {
                homeContentViewModel.requestTopNavLongPress(tab)
                true
            }

            else -> false
        }
    }

    Scaffold(
        modifier = Modifier,
        topBar = {
            key(entryFocusRequest?.id) {
                WjzFocusTopology {
                    val topNavRegionId = (topNavTopologyRegion as? WjzFocusTopologyRegionRef.Bound)?.id
                    if (topNavRegionId != null) {
                        region(
                            id = topNavRegionId,
                            scopeId = HomeTopNavScopeId,
                            layer = WjzFocusLayer.Content
                        ) {
                            onUp(WjzFocusBoundaryTarget.Cancel)
                            if (activeTab == HomeTopNavItem.Search && searchPage == HomeSearchPage.Input) {
                                onDown(WjzFocusBoundaryTarget.Entry(SearchInputDefaultEntryId))
                            }
                        }
                    }

                    TopNav(
                        modifier = Modifier,
                        leadingContent = topBarLeadingContent,
                        items = reorderedItems,
                        selectedItem = focusedTab,
                        activeItem = activeTab,
                        autoRefreshItems = Prefs.homeAutoRefreshTopNavItems,
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
                        isHistorySearching = homeContentViewModel.isHistorySearching,
                        focusedLeadingIcon = homeFocusedLeadingIcon,
                        onTabConfirmLongPress = { nav -> handleTopNavConfirmLongPress(nav as HomeTopNavItem) },
                        contentFocusEnabled = true,
                        contentFocusReadyKey = contentReadyTab,
                        topNavExits = {
                            left move MainTopNavDefaultEntryId
                            right move MainTopNavDefaultEntryId
                            if (activeTab == HomeTopNavItem.Search && searchPage == HomeSearchPage.Input) {
                                down move SearchInputDefaultEntryId
                            } else {
                                down move MainContentTopEntryId
                            }
                        },
                        focusScopeId = HomeTopNavScopeId,
                        focusComponentId = HomeTopNavComponentId,
                        onContentFocusRequested = { nav ->
                            val target = nav as HomeTopNavItem
                            if (target != activeTab) {
                                homeContentViewModel.onTabClicked(target)
                            }
                        },
                        onAutoRefreshRequested = { nav ->
                            homeContentViewModel.requestUserRefresh(nav as HomeTopNavItem)
                        },
                        onSelectedChanged = { nav -> homeContentViewModel.onTabFocused(nav as HomeTopNavItem) },
                        onClick = { nav ->
                            val target = nav as HomeTopNavItem
                            val shouldRefresh = target == activeTab
                            homeContentViewModel.onTabClicked(target)
                            if (shouldRefresh && target != HomeTopNavItem.Search) {
                                homeContentViewModel.requestUserRefresh(target)
                            }
                        },
                        focusLayer = WjzFocusLayer.Content,
                        backFocusEnabled = active
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .wjzFocusExits(
                    nodeId = HomeContentNodeId,
                    layer = WjzFocusLayer.Content,
                    strategy = WjzFocusRestoreStrategy.Container,
                    enabled = active,
                    exits = {
                        up move HomeTopNavEntryId
                    }
                ),
        ) {
            val mountedTabs = reorderedItems.filter { tab ->
                val runtimeState = homeContentViewModel.runtimeStateOf(tab)
                runtimeState == ContentRuntimeState.Active ||
                        runtimeState == ContentRuntimeState.Frozen ||
                        tab == activeTab
            }
            mountedTabs.forEach { tab ->
                val runtimeState = homeContentViewModel.runtimeStateOf(tab)
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
                            HomeTabShell()
                        }

                        if (runtimeState == ContentRuntimeState.Active ||
                            runtimeState == ContentRuntimeState.Frozen
                        ) {
                            when (tab) {
                                HomeTopNavItem.Search -> {
                                    when (searchPage) {
                                        HomeSearchPage.Input -> {
                                            val searchInputViewModel: SearchInputViewModel =
                                                koinViewModel<SearchInputViewModel>()
                                            SearchInputScreen(
                                                onDefaultFocusReady = {
                                                    handleDefaultFocusReady(tab)
                                                    if (tabActive) contentReadyTab = tab
                                                },
                                                onSearchSubmit = { keyword, enableProxy ->
                                                    searchKeyword = keyword
                                                    searchEnableProxy = enableProxy
                                                    searchPage = HomeSearchPage.Result
                                                },
                                                topologyRegion = topologyRegion,
                                                topNavEntryId = HomeTopNavEntryId,
                                                searchInputViewModel = searchInputViewModel
                                            )
                                        }

                                        HomeSearchPage.Result -> {
                                            val searchResultViewModel: SearchResultViewModel =
                                                koinViewModel<SearchResultViewModel>()
                                            SearchResultScreen(
                                                keyword = searchKeyword,
                                                enableProxy = searchEnableProxy,
                                                onContentEntryReady = {
                                                    if (tabActive) contentReadyTab = tab
                                                },
                                                onBackToInput = { searchPage = HomeSearchPage.Input },
                                                topologyRegion = topologyRegion,
                                                searchResultViewModel = searchResultViewModel
                                            )
                                        }
                                    }
                                }

                                else -> key(tab) {
                                    HomeActiveTabContent(
                                        tab = tab,
                                        homeContentViewModel = homeContentViewModel,
                                        backToTopNav = backToTopNav,
                                        onContentEntryReady = {
                                            if (tabActive) contentReadyTab = tab
                                        },
                                        active = tabActive,
                                        topologyRegion = topologyRegion
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTabShell() {
    Box(modifier = Modifier.fillMaxSize())
}

private fun List<HomeTopNavItem>.ensureVisibleHomeTabs(
    firstTab: HomeTopNavItem
): List<HomeTopNavItem> {
    val validItems = HomeTopNavItem.entries.toSet()
    val orderedItems = filter { it in validItems }.distinct()
    val visibleItems = if (firstTab in orderedItems) {
        orderedItems
    } else {
        listOf(firstTab) + orderedItems
    }
    return visibleItems.ifEmpty { HomeTopNavItem.entries.toList() }
}

@Composable
private fun HomeActiveTabContent(
    tab: HomeTopNavItem,
    homeContentViewModel: HomeContentViewModel,
    backToTopNav: () -> Unit,
    onContentEntryReady: () -> Unit,
    active: Boolean,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone
) {
    val gridState = rememberHomeGridState(tab, homeContentViewModel)
    val activationSerial = homeContentViewModel.activationSerialOf(tab)
    val refreshSerial = homeContentViewModel.refreshSerialOf(tab)
    val longPressSerial = homeContentViewModel.longPressSerialOf(tab)
    var consumedRefreshSerial by remember(tab) { mutableStateOf(0L) }
    var consumedLongPressSerial by remember(tab) { mutableStateOf(0L) }

    LaunchedEffect(tab, refreshSerial) {
        consumedRefreshSerial = if (homeContentViewModel.consumeRefreshSerial(tab, refreshSerial)) {
            refreshSerial
        } else {
            0L
        }
    }

    LaunchedEffect(tab, longPressSerial) {
        consumedLongPressSerial = if (homeContentViewModel.consumeLongPressSerial(tab, longPressSerial)) {
            longPressSerial
        } else {
            0L
        }
    }

    when (tab) {
        HomeTopNavItem.Search -> Unit
        HomeTopNavItem.Recommend -> {
            RecommendScreen(
                gridState = gridState,
                active = active,
                activationSerial = activationSerial,
                refreshSerial = consumedRefreshSerial,
                onContentEntryReady = onContentEntryReady,
                topologyRegion = topologyRegion
            )
        }
        HomeTopNavItem.Popular -> {
            PopularScreen(
                gridState = gridState,
                active = active,
                activationSerial = activationSerial,
                refreshSerial = consumedRefreshSerial,
                onContentEntryReady = onContentEntryReady,
                topologyRegion = topologyRegion
            )
        }
        HomeTopNavItem.Dynamics -> {
            DynamicsScreen(
                gridState = gridState,
                active = active,
                activationSerial = activationSerial,
                refreshSerial = consumedRefreshSerial,
                longPressSerial = consumedLongPressSerial,
                onContentEntryReady = onContentEntryReady,
                topologyRegion = topologyRegion
            )
        }
        HomeTopNavItem.ToView -> {
            val toViewViewModel: ToViewViewModel = koinViewModel<ToViewViewModel>()
            ToViewScreen(
                gridState = gridState,
                active = active,
                activationSerial = activationSerial,
                refreshSerial = consumedRefreshSerial,
                toViewViewModel = toViewViewModel,
                onContentEntryReady = onContentEntryReady,
                onBack = backToTopNav,
                topologyRegion = topologyRegion
            )
        }
        HomeTopNavItem.History -> {
            val historyViewModel: HistoryViewModel = koinViewModel<HistoryViewModel>()
            val toViewViewModel: ToViewViewModel = koinViewModel<ToViewViewModel>()
            HistoryScreen(
                gridState = gridState,
                active = active,
                activationSerial = activationSerial,
                refreshSerial = consumedRefreshSerial,
                longPressSerial = consumedLongPressSerial,
                historyViewModel = historyViewModel,
                toViewViewModel = toViewViewModel,
                onContentEntryReady = onContentEntryReady,
                onSearchStateChanged = homeContentViewModel::updateHistorySearching,
                topologyRegion = topologyRegion
            )
        }
        HomeTopNavItem.Favorite -> {
            val favoriteViewModel: FavoriteViewModel = koinViewModel<FavoriteViewModel>()
            val toViewViewModel: ToViewViewModel = koinViewModel<ToViewViewModel>()
            FavoriteScreen(
                lazyGridState = gridState,
                active = active,
                activationSerial = activationSerial,
                refreshSerial = consumedRefreshSerial,
                favoriteViewModel = favoriteViewModel,
                toViewViewModel = toViewViewModel,
                onContentEntryReady = onContentEntryReady,
                onBack = backToTopNav,
                topologyRegion = topologyRegion
            )
        }
        HomeTopNavItem.Follow -> {
            val followViewModel: FollowViewModel = koinViewModel<FollowViewModel>()
            FollowScreen(
                lazyGridState = gridState,
                active = active,
                activationSerial = activationSerial,
                refreshSerial = consumedRefreshSerial,
                followViewModel = followViewModel,
                onContentEntryReady = onContentEntryReady,
                onBack = backToTopNav,
                topologyRegion = topologyRegion
            )
        }
        HomeTopNavItem.FollowingSeason -> {
            val followingSeasonViewModel: FollowingSeasonViewModel =
                koinViewModel<FollowingSeasonViewModel>()
            FollowingSeasonScreen(
                lazyGridState = gridState,
                active = active,
                activationSerial = activationSerial,
                refreshSerial = consumedRefreshSerial,
                onContentEntryReady = onContentEntryReady,
                followingSeasonViewModel = followingSeasonViewModel,
                topologyRegion = topologyRegion
            )
        }
        HomeTopNavItem.MyClassroom -> MyClassroomScreen(
            gridState = gridState,
            activationSerial = activationSerial,
            refreshSerial = consumedRefreshSerial,
            onContentEntryReady = onContentEntryReady,
            topologyRegion = topologyRegion.enabledIf(active)
        )
        HomeTopNavItem.SubscribedCollection -> {
            val subscribedCollectionViewModel: SubscribedCollectionViewModel =
                koinViewModel<SubscribedCollectionViewModel>()
            val toViewViewModel: ToViewViewModel = koinViewModel<ToViewViewModel>()
            SubscribedCollectionScreen(
                lazyGridState = gridState,
                active = active,
                activationSerial = activationSerial,
                refreshSerial = consumedRefreshSerial,
                favoriteViewModel = subscribedCollectionViewModel,
                toViewViewModel = toViewViewModel,
                onContentEntryReady = onContentEntryReady,
                onBack = backToTopNav,
                topologyRegion = topologyRegion
            )
        }
    }
}

@Composable
private fun rememberHomeGridState(
    tab: HomeTopNavItem,
    homeContentViewModel: HomeContentViewModel
): LazyGridState {
    val viewportMap by homeContentViewModel.viewportMap.collectAsStateWithLifecycle()
    val viewport by remember(tab, viewportMap) {
        derivedStateOf { viewportMap[tab] ?: GridViewportState() }
    }
    val gridState = rememberRestoredLazyGridState(viewport)
    PersistLazyGridViewportEffect(gridState) { index, offset ->
        homeContentViewModel.updateViewport(tab, index, offset)
    }
    return gridState
}
