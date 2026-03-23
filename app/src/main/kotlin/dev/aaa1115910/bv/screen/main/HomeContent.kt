package dev.aaa1115910.bv.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.component.HomeTopNavItem
import dev.aaa1115910.bv.component.PersistLazyGridViewportEffect
import dev.aaa1115910.bv.component.TopNav
import dev.aaa1115910.bv.component.rememberRestoredLazyGridState
import dev.aaa1115910.bv.screen.main.common.ActivationBehavior
import dev.aaa1115910.bv.screen.main.common.TabActivationGuard
import dev.aaa1115910.bv.screen.main.common.UnifiedTabActivationEffects
import dev.aaa1115910.bv.screen.main.home.DynamicsScreen
import dev.aaa1115910.bv.screen.main.home.PopularScreen
import dev.aaa1115910.bv.screen.main.home.RecommendScreen
import dev.aaa1115910.bv.screen.user.FavoriteScreen
import dev.aaa1115910.bv.screen.user.FollowingSeasonScreen
import dev.aaa1115910.bv.screen.user.HistoryScreen
import dev.aaa1115910.bv.screen.user.ToViewScreen
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.viewmodel.UserViewModel
import dev.aaa1115910.bv.viewmodel.common.LoadState
import dev.aaa1115910.bv.viewmodel.home.DynamicViewModel
import dev.aaa1115910.bv.viewmodel.home.PopularViewModel
import dev.aaa1115910.bv.viewmodel.home.RecommendViewModel
import dev.aaa1115910.bv.viewmodel.main.HomeContentViewModel
import dev.aaa1115910.bv.viewmodel.user.FavoriteViewModel
import dev.aaa1115910.bv.viewmodel.user.FollowingSeasonViewModel
import dev.aaa1115910.bv.viewmodel.user.HistoryViewModel
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeContent(
    navFocusRequester: FocusRequester,
    onDefaultFocusReady: (() -> Unit)? = null,
    homeContentViewModel: HomeContentViewModel = koinViewModel(),
    recommendViewModel: RecommendViewModel = koinViewModel(),
    popularViewModel: PopularViewModel = koinViewModel(),
    dynamicViewModel: DynamicViewModel = koinViewModel(),
    userViewModel: UserViewModel = koinViewModel(),
    favouriteViewModel: FavoriteViewModel = koinViewModel(),
    historyViewModel: HistoryViewModel = koinViewModel(),
    toViewViewModel: ToViewViewModel = koinViewModel(),
    followingSeasonViewModel: FollowingSeasonViewModel = koinViewModel()
) {
    val firstTab = remember { Prefs.firstHomeTopNavItem }
    val focusedTab = homeContentViewModel.focusedTab
    val activeTab = homeContentViewModel.activeTab
    var focusOnContent by remember { mutableStateOf(false) }

    // 1. 在这里定义 backToTabRow 函数
    // 它会使用从外部传入的 navFocusRequester 来请求焦点
    val backToTabRow: () -> Unit = {
        navFocusRequester.requestFocus()
    }

    val getReorderedItems: (HomeTopNavItem) -> List<HomeTopNavItem> = { item ->
        val allItems = HomeTopNavItem.entries
        val startIndex = allItems.indexOf(item)
        if (startIndex == -1) emptyList()
        else allItems.drop(startIndex) + allItems.take(startIndex)
    }
    val reorderedItems = remember {
        getReorderedItems(firstTab)
    }

    val recommendGridState = rememberRestoredLazyGridState(homeContentViewModel.viewportOf(HomeTopNavItem.Recommend))
    val popularGridState = rememberRestoredLazyGridState(homeContentViewModel.viewportOf(HomeTopNavItem.Popular))
    val dynamicsGridState = rememberRestoredLazyGridState(homeContentViewModel.viewportOf(HomeTopNavItem.Dynamics))
    val toViewGridState = rememberRestoredLazyGridState(homeContentViewModel.viewportOf(HomeTopNavItem.ToView))
    val historyGridState = rememberRestoredLazyGridState(homeContentViewModel.viewportOf(HomeTopNavItem.History))
    val favoriteGridState = rememberRestoredLazyGridState(homeContentViewModel.viewportOf(HomeTopNavItem.Favorite))
    val followingSeasonGridState = rememberRestoredLazyGridState(homeContentViewModel.viewportOf(HomeTopNavItem.FollowingSeason))
    val activationGuard = remember { TabActivationGuard<HomeTopNavItem>() }

    PersistLazyGridViewportEffect(recommendGridState) { index, offset -> homeContentViewModel.updateViewport(HomeTopNavItem.Recommend, index, offset) }
    PersistLazyGridViewportEffect(popularGridState) { index, offset -> homeContentViewModel.updateViewport(HomeTopNavItem.Popular, index, offset) }
    PersistLazyGridViewportEffect(dynamicsGridState) { index, offset -> homeContentViewModel.updateViewport(HomeTopNavItem.Dynamics, index, offset) }
    PersistLazyGridViewportEffect(toViewGridState) { index, offset -> homeContentViewModel.updateViewport(HomeTopNavItem.ToView, index, offset) }
    PersistLazyGridViewportEffect(historyGridState) { index, offset -> homeContentViewModel.updateViewport(HomeTopNavItem.History, index, offset) }
    PersistLazyGridViewportEffect(favoriteGridState) { index, offset -> homeContentViewModel.updateViewport(HomeTopNavItem.Favorite, index, offset) }
    PersistLazyGridViewportEffect(followingSeasonGridState) { index, offset -> homeContentViewModel.updateViewport(HomeTopNavItem.FollowingSeason, index, offset) }

    fun homeActivationBehaviorOf(tab: HomeTopNavItem): ActivationBehavior = when (tab) {
        HomeTopNavItem.Dynamics,
        HomeTopNavItem.ToView -> ActivationBehavior.KeepPosition
        HomeTopNavItem.Recommend,
        HomeTopNavItem.Popular,
        HomeTopNavItem.History,
        HomeTopNavItem.Favorite,
        HomeTopNavItem.FollowingSeason -> ActivationBehavior.RefreshAndScrollTop
    }

    fun homeGridStateOf(tab: HomeTopNavItem): LazyGridState = when (tab) {
        HomeTopNavItem.Recommend -> recommendGridState
        HomeTopNavItem.Popular -> popularGridState
        HomeTopNavItem.Dynamics -> dynamicsGridState
        HomeTopNavItem.ToView -> toViewGridState
        HomeTopNavItem.History -> historyGridState
        HomeTopNavItem.Favorite -> favoriteGridState
        HomeTopNavItem.FollowingSeason -> followingSeasonGridState
    }

    fun homeRetryStateOf(tab: HomeTopNavItem): LoadState = when (tab) {
        HomeTopNavItem.Recommend -> recommendViewModel.initialLoadState
        HomeTopNavItem.Popular -> popularViewModel.initialLoadState
        HomeTopNavItem.Dynamics -> dynamicViewModel.initialLoadState
        HomeTopNavItem.ToView -> toViewViewModel.initialLoadState
        HomeTopNavItem.History -> historyViewModel.initialLoadState
        HomeTopNavItem.Favorite -> favouriteViewModel.initialLoadState
        HomeTopNavItem.FollowingSeason -> followingSeasonViewModel.initialLoadState
    }

    fun ensureHomeTabLoadedSilent(tab: HomeTopNavItem) {
        when (tab) {
            HomeTopNavItem.Recommend -> recommendViewModel.ensureLoaded(showErrorToast = false)
            HomeTopNavItem.Popular -> popularViewModel.ensureLoaded(showErrorToast = false)
            HomeTopNavItem.Dynamics -> dynamicViewModel.ensureLoaded(showErrorToast = false)
            HomeTopNavItem.ToView -> toViewViewModel.ensureLoaded(showErrorToast = false)
            HomeTopNavItem.History -> historyViewModel.ensureLoaded(showErrorToast = false)
            HomeTopNavItem.Favorite -> favouriteViewModel.ensureLoaded()
            HomeTopNavItem.FollowingSeason -> followingSeasonViewModel.ensureLoaded()
        }
    }

    fun refreshHomeTabSilent(tab: HomeTopNavItem) {
        when (tab) {
            HomeTopNavItem.Recommend -> recommendViewModel.reloadAll(showErrorToast = false)
            HomeTopNavItem.Popular -> popularViewModel.reloadAll(showErrorToast = false)
            HomeTopNavItem.Dynamics -> dynamicViewModel.reloadAll(
                showNoUpdateToast = false,
                showErrorToast = false
            )
            HomeTopNavItem.ToView -> toViewViewModel.refreshSnapshotIncrementally(showErrorToast = false)
            HomeTopNavItem.History -> historyViewModel.reloadAll(showErrorToast = false)
            HomeTopNavItem.Favorite -> favouriteViewModel.reloadAll()
            HomeTopNavItem.FollowingSeason -> followingSeasonViewModel.reloadAll()
        }
    }

    fun toastHomeFinalFailure(tab: HomeTopNavItem) {
        when (tab) {
            HomeTopNavItem.Recommend -> "加载推荐视频失败".toast(BVApp.context)
            HomeTopNavItem.Popular -> "加载热门视频失败".toast(BVApp.context)
            HomeTopNavItem.Dynamics -> "加载动态失败".toast(BVApp.context)
            HomeTopNavItem.ToView -> "加载稍后再看失败".toast(BVApp.context)
            HomeTopNavItem.History -> "加载历史记录失败".toast(BVApp.context)
            HomeTopNavItem.Favorite -> "加载收藏夹失败".toast(BVApp.context)
            HomeTopNavItem.FollowingSeason -> "加载追番追剧失败".toast(BVApp.context)
        }
    }

    UnifiedTabActivationEffects(
        activeTab = activeTab,
        behaviorOf = ::homeActivationBehaviorOf,
        gridStateOf = ::homeGridStateOf,
        guard = activationGuard,
        currentRetryStateOf = ::homeRetryStateOf,
        onEnsureLoadedSilent = ::ensureHomeTabLoadedSilent,
        onActivationRefreshSilent = ::refreshHomeTabSilent,
        onFinalFailureToast = ::toastHomeFinalFailure
    )

    LaunchedEffect(userViewModel.isLogin) {
        if (userViewModel.isLogin) userViewModel.updateUserInfo() else userViewModel.clearUserInfo()
    }

    Scaffold(
        topBar = {
            TopNav(
                modifier = Modifier,
                items = reorderedItems,
                isLargePadding = !focusOnContent,
                selectedItem = focusedTab,
                defaultFocusRequester = navFocusRequester,
                onDefaultFocusReady = onDefaultFocusReady,
                isHistorySearching = historyViewModel.debouncedQuery.isNotBlank(),
                onHistoryTabDirectionUp = { isLongPress ->
                    if (focusedTab == HomeTopNavItem.History) {
                        if (isLongPress) historyViewModel.clearSearch() else historyViewModel.openSearchDialog()
                    }
                },
                onSelectedChanged = { nav -> homeContentViewModel.onTabFocused(nav as HomeTopNavItem) },
                onClick = { nav ->
                    val target = nav as HomeTopNavItem
                    homeContentViewModel.onTabClicked(target)
                    activationGuard.markClickRefresh(target)
                    refreshHomeTabSilent(target)
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .onFocusChanged { focusOnContent = it.hasFocus }
                .onKeyEvent {
                    if (it.key == Key.Back) {
                        // 确保是按键抬起事件，防止重复触发
                        // 同时检查焦点是否确实在内容区域
                        if (it.type == KeyEventType.KeyUp && focusOnContent) {
                            backToTabRow()
                            // 返回 true 表示我们已经处理了这个事件，
                            // 系统不需要再执行默认的返回操作（例如退出页面）
                            return@onKeyEvent true
                        }
                    }

                    if (it.key == Key.Menu) {
                        if (it.type == KeyEventType.KeyDown) return@onKeyEvent true
                        activationGuard.markClickRefresh(activeTab)
                        refreshHomeTabSilent(activeTab)
                        navFocusRequester.requestFocus()
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                },
        ) {
            when (activeTab) {
                HomeTopNavItem.Recommend -> RecommendScreen(gridState = recommendGridState)
                HomeTopNavItem.Popular -> PopularScreen(gridState = popularGridState)
                HomeTopNavItem.Dynamics -> DynamicsScreen(gridState = dynamicsGridState)
                HomeTopNavItem.ToView -> ToViewScreen(gridState = toViewGridState)
                HomeTopNavItem.History -> HistoryScreen(historyViewModel = historyViewModel, gridState = historyGridState)
                HomeTopNavItem.Favorite -> FavoriteScreen(onBack = backToTabRow, lazyGridState = favoriteGridState)
                HomeTopNavItem.FollowingSeason -> FollowingSeasonScreen(lazyGridState = followingSeasonGridState)
            }
        }
    }
}