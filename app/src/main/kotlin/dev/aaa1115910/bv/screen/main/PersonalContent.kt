package dev.aaa1115910.bv.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.component.PersistLazyGridViewportEffect
import dev.aaa1115910.bv.component.PersonalTopNavItem
import dev.aaa1115910.bv.component.TopNav
import dev.aaa1115910.bv.component.rememberRestoredLazyGridState
import dev.aaa1115910.bv.screen.main.common.UnifiedTabActivationEffects
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.screen.main.common.ActivationBehavior
import dev.aaa1115910.bv.screen.main.common.TabActivationGuard
import dev.aaa1115910.bv.screen.main.common.MainContentEntryRequest
import dev.aaa1115910.bv.screen.main.common.MainContentFocusTarget
import dev.aaa1115910.bv.screen.user.FavoriteScreen
import dev.aaa1115910.bv.screen.user.FollowingSeasonScreen
import dev.aaa1115910.bv.screen.user.HistoryScreen
import dev.aaa1115910.bv.screen.user.ToViewScreen
import androidx.compose.runtime.LaunchedEffect
import dev.aaa1115910.bv.viewmodel.UserViewModel
import dev.aaa1115910.bv.viewmodel.common.LoadState
import dev.aaa1115910.bv.viewmodel.main.PersonalContentViewModel
import dev.aaa1115910.bv.viewmodel.user.FavoriteViewModel
import dev.aaa1115910.bv.viewmodel.user.FollowingSeasonViewModel
import dev.aaa1115910.bv.viewmodel.user.HistoryViewModel
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun PersonalContent(
    navFocusRequester: FocusRequester,
    drawerFocusRequester: FocusRequester,
    pendingDrawerEntryRequest: MainContentEntryRequest? = null,
    onDrawerEntryConsumed: (Long) -> Unit = {},
    onDefaultFocusReady: (() -> Unit)? = null,
    personalContentViewModel: PersonalContentViewModel = koinViewModel(),
    userViewModel: UserViewModel = koinViewModel(),
    favouriteViewModel: FavoriteViewModel = koinViewModel(),
    historyViewModel: HistoryViewModel = koinViewModel(),
    toViewViewModel: ToViewViewModel = koinViewModel(),
    followingSeasonViewModel: FollowingSeasonViewModel = koinViewModel()
) {
    val focusedTab = personalContentViewModel.focusedTab
    val activeTab = personalContentViewModel.activeTab
    var focusOnContent by remember { mutableStateOf(false) }
    val composeScope = rememberCoroutineScope()
    // 1. 在这里定义 backToTabRow 函数
    // 它会使用从外部传入的 navFocusRequester 来请求焦点
    val backToTabRow: () -> Unit = {
        navFocusRequester.requestFocus()
    }

    val toViewGridState = rememberRestoredLazyGridState(personalContentViewModel.viewportOf(PersonalTopNavItem.ToView))
    val historyGridState = rememberRestoredLazyGridState(personalContentViewModel.viewportOf(PersonalTopNavItem.History))
    val favoriteGridState = rememberRestoredLazyGridState(personalContentViewModel.viewportOf(PersonalTopNavItem.Favorite))
    val followingSeasonGridState = rememberRestoredLazyGridState(personalContentViewModel.viewportOf(PersonalTopNavItem.FollowingSeason))
    val activationGuard = remember { TabActivationGuard<PersonalTopNavItem>() }
    var topNavReadyTab by remember { mutableStateOf<PersonalTopNavItem?>(null) }

    val desiredDrawerEntryTab = remember(pendingDrawerEntryRequest?.id) {
        when (pendingDrawerEntryRequest?.target) {
            MainContentFocusTarget.LeftEntry -> PersonalTopNavItem.entries.firstOrNull()
            MainContentFocusTarget.RightEntry -> PersonalTopNavItem.entries.lastOrNull()
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
            personalContentViewModel.onTabClicked(desired)
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

    PersistLazyGridViewportEffect(toViewGridState) { index, offset -> personalContentViewModel.updateViewport(PersonalTopNavItem.ToView, index, offset) }
    PersistLazyGridViewportEffect(historyGridState) { index, offset -> personalContentViewModel.updateViewport(PersonalTopNavItem.History, index, offset) }
    PersistLazyGridViewportEffect(favoriteGridState) { index, offset -> personalContentViewModel.updateViewport(PersonalTopNavItem.Favorite, index, offset) }
    PersistLazyGridViewportEffect(followingSeasonGridState) { index, offset -> personalContentViewModel.updateViewport(PersonalTopNavItem.FollowingSeason, index, offset) }

    fun personalActivationBehaviorOf(tab: PersonalTopNavItem): ActivationBehavior = when (tab) {
        PersonalTopNavItem.ToView -> ActivationBehavior.KeepPosition
        PersonalTopNavItem.History,
        PersonalTopNavItem.Favorite,
        PersonalTopNavItem.FollowingSeason -> ActivationBehavior.RefreshAndScrollTop
    }

    fun personalGridStateOf(tab: PersonalTopNavItem): LazyGridState = when (tab) {
        PersonalTopNavItem.ToView -> toViewGridState
        PersonalTopNavItem.History -> historyGridState
        PersonalTopNavItem.Favorite -> favoriteGridState
        PersonalTopNavItem.FollowingSeason -> followingSeasonGridState
    }

    fun personalRetryStateOf(tab: PersonalTopNavItem): LoadState = when (tab) {
        PersonalTopNavItem.ToView -> toViewViewModel.retryLoadState
        PersonalTopNavItem.History -> historyViewModel.initialLoadState
        PersonalTopNavItem.Favorite -> favouriteViewModel.initialLoadState
        PersonalTopNavItem.FollowingSeason -> followingSeasonViewModel.initialLoadState
    }

    fun personalShouldRetry(tab: PersonalTopNavItem, state: LoadState?): Boolean {
        if (state != LoadState.Error) return false

        return when (tab) {
            PersonalTopNavItem.ToView -> !toViewViewModel.lastFailureWasAuth
            PersonalTopNavItem.History -> !historyViewModel.lastFailureWasAuth
            PersonalTopNavItem.Favorite -> !favouriteViewModel.lastFailureWasAuth
            PersonalTopNavItem.FollowingSeason -> !followingSeasonViewModel.lastFailureWasAuth
        }
    }

    fun ensurePersonalTabLoadedSilent(tab: PersonalTopNavItem) {
        when (tab) {
            PersonalTopNavItem.ToView -> toViewViewModel.ensureLoaded(showErrorToast = false)
            PersonalTopNavItem.History -> historyViewModel.ensureLoaded(showErrorToast = false)
            PersonalTopNavItem.Favorite -> favouriteViewModel.ensureLoaded()
            PersonalTopNavItem.FollowingSeason -> followingSeasonViewModel.ensureLoaded()
        }
    }

    fun refreshPageDataSilent(tab: PersonalTopNavItem) {
        when (tab) {
            PersonalTopNavItem.ToView -> toViewViewModel.refreshSnapshotIncrementally(showErrorToast = false)
            PersonalTopNavItem.History -> historyViewModel.reloadAll(showErrorToast = false)
            PersonalTopNavItem.Favorite -> favouriteViewModel.reloadAll()
            PersonalTopNavItem.FollowingSeason -> followingSeasonViewModel.reloadAll()
        }
    }

    fun personalShouldScrollTopOnUserRefresh(tab: PersonalTopNavItem): Boolean = when (tab) {
        PersonalTopNavItem.ToView -> true
        else -> personalActivationBehaviorOf(tab) == ActivationBehavior.RefreshAndScrollTop
    }

    fun refreshPersonalTabByUser(tab: PersonalTopNavItem) {
        activationGuard.markClickRefresh(tab)

        if (personalShouldScrollTopOnUserRefresh(tab)) {
            composeScope.launch {
                personalGridStateOf(tab).scrollToItem(0)
                refreshPageDataSilent(tab)
            }
        } else {
            refreshPageDataSilent(tab)
        }
    }

    fun retryPageDataSilent(tab: PersonalTopNavItem) {
        refreshPageDataSilent(tab)
    }

    fun toastPersonalFinalFailure(tab: PersonalTopNavItem) {
        val message = when (tab) {
            PersonalTopNavItem.ToView ->
                if (toViewViewModel.lastFailureWasAuth) {
                    BVApp.context.getString(R.string.exception_auth_failure)
                } else {
                    "加载稍后再看失败"
                }

            PersonalTopNavItem.History ->
                if (historyViewModel.lastFailureWasAuth) {
                    BVApp.context.getString(R.string.exception_auth_failure)
                } else {
                    "加载历史记录失败"
                }

            PersonalTopNavItem.Favorite ->
                if (favouriteViewModel.lastFailureWasAuth) {
                    BVApp.context.getString(R.string.exception_auth_failure)
                } else {
                    "加载收藏夹失败"
                }

            PersonalTopNavItem.FollowingSeason ->
                if (followingSeasonViewModel.lastFailureWasAuth) {
                    BVApp.context.getString(R.string.exception_auth_failure)
                } else {
                    "加载追番追剧失败"
                }
        }

        message.toast(BVApp.context)
    }

    UnifiedTabActivationEffects(
        activeTab = activeTab,
        behaviorOf = ::personalActivationBehaviorOf,
        gridStateOf = ::personalGridStateOf,
        guard = activationGuard,
        currentRetryStateOf = ::personalRetryStateOf,
        shouldRetryOf = ::personalShouldRetry,
        onEnsureLoadedSilent = ::ensurePersonalTabLoadedSilent,
        onActivationRefreshSilent = ::refreshPageDataSilent,
        onRetrySilent = ::retryPageDataSilent,
        onFinalFailureToast = ::toastPersonalFinalFailure
    )

    LaunchedEffect(userViewModel.isLogin) {
        if (!userViewModel.isLogin) {
            userViewModel.clearUserInfo()
            historyViewModel.clearData()
            toViewViewModel.clearData()
            favouriteViewModel.clearData()
            followingSeasonViewModel.clearData()
        }
    }

    Scaffold(
        modifier = Modifier,
        topBar = {
            TopNav(
                modifier = Modifier,
                items = PersonalTopNavItem.entries,
                isLargePadding = !focusOnContent,
                selectedItem = focusedTab,
                defaultFocusRequester = navFocusRequester,
                onDefaultFocusReady = handleDefaultFocusReady,
                isHistorySearching = historyViewModel.debouncedQuery.isNotBlank(),
                onHistoryTabDirectionUp = { isLongPress ->
                    if (focusedTab == PersonalTopNavItem.History) {
                        if (isLongPress) historyViewModel.clearSearch() else historyViewModel.openSearchDialog()
                    }
                },
                onLeftBoundaryExit = { drawerFocusRequester.requestFocus() },
                onRightBoundaryExit = { drawerFocusRequester.requestFocus() },
                onSelectedChanged = { nav -> personalContentViewModel.onTabFocused(nav as PersonalTopNavItem) },
                onClick = { nav ->
                    val target = nav as PersonalTopNavItem
                    personalContentViewModel.onTabClicked(target)
                    refreshPersonalTabByUser(target)
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .onFocusChanged { focusOnContent = it.hasFocus }
                .onPreviewKeyEvent {
                    if (it.key == Key.Back) {
                        if (it.type == KeyEventType.KeyUp && focusOnContent) {
                            backToTabRow()
                            return@onPreviewKeyEvent true
                        }
                    }

                    if (it.key == Key.Menu) {
                        if (it.type == KeyEventType.KeyDown) return@onPreviewKeyEvent true
                        refreshPersonalTabByUser(activeTab)
                        navFocusRequester.requestFocus()
                        return@onPreviewKeyEvent true
                    }
                    false
                },
        ) {
            when (activeTab) {
                PersonalTopNavItem.ToView -> ToViewScreen(gridState = toViewGridState)
                PersonalTopNavItem.History -> HistoryScreen(
                    historyViewModel = historyViewModel,
                    gridState = historyGridState
                )
                PersonalTopNavItem.Favorite -> FavoriteScreen(
                    onBack = backToTabRow,
                    lazyGridState = favoriteGridState
                )
                PersonalTopNavItem.FollowingSeason -> FollowingSeasonScreen(
                    lazyGridState = followingSeasonGridState
                )
            }
        }
    }
}