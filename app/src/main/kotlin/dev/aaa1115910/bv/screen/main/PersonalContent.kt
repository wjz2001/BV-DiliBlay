package dev.aaa1115910.bv.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import dev.aaa1115910.bv.component.PersistLazyGridViewportEffect
import dev.aaa1115910.bv.component.rememberRestoredLazyGridState
import dev.aaa1115910.bv.component.PersonalTopNavItem
import dev.aaa1115910.bv.component.TopNav
import dev.aaa1115910.bv.screen.user.FavoriteScreen
import dev.aaa1115910.bv.screen.user.FollowingSeasonScreen
import dev.aaa1115910.bv.screen.user.HistoryScreen
import dev.aaa1115910.bv.screen.user.ToViewScreen
import dev.aaa1115910.bv.viewmodel.main.PersonalContentViewModel
import dev.aaa1115910.bv.viewmodel.user.FavoriteViewModel
import dev.aaa1115910.bv.viewmodel.user.FollowingSeasonViewModel
import dev.aaa1115910.bv.viewmodel.user.HistoryViewModel
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun PersonalContent(
    navFocusRequester: FocusRequester,
    onDefaultFocusReady: (() -> Unit)? = null,
    personalContentViewModel: PersonalContentViewModel = koinViewModel(),
    favouriteViewModel: FavoriteViewModel = koinViewModel(),
    historyViewModel: HistoryViewModel = koinViewModel(),
    toViewViewModel: ToViewViewModel = koinViewModel(),
    followingSeasonViewModel: FollowingSeasonViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()

    val selectedTab = personalContentViewModel.selectedTab
    var focusOnContent by remember { mutableStateOf(false) }
    // 1. 在这里定义 backToTabRow 函数
    // 它会使用从外部传入的 navFocusRequester 来请求焦点
    val backToTabRow: () -> Unit = {
        navFocusRequester.requestFocus()
    }

    val toViewGridState = rememberRestoredLazyGridState(
        personalContentViewModel.viewportOf(PersonalTopNavItem.ToView)
    )
    val historyGridState = rememberRestoredLazyGridState(
        personalContentViewModel.viewportOf(PersonalTopNavItem.History)
    )
    val favoriteGridState = rememberRestoredLazyGridState(
        personalContentViewModel.viewportOf(PersonalTopNavItem.Favorite)
    )
    val followingSeasonGridState = rememberRestoredLazyGridState(
        personalContentViewModel.viewportOf(PersonalTopNavItem.FollowingSeason)
    )

    PersistLazyGridViewportEffect(toViewGridState) { index, offset ->
        personalContentViewModel.updateViewport(PersonalTopNavItem.ToView, index, offset)
    }
    PersistLazyGridViewportEffect(historyGridState) { index, offset ->
        personalContentViewModel.updateViewport(PersonalTopNavItem.History, index, offset)
    }
    PersistLazyGridViewportEffect(favoriteGridState) { index, offset ->
        personalContentViewModel.updateViewport(PersonalTopNavItem.Favorite, index, offset)
    }
    PersistLazyGridViewportEffect(followingSeasonGridState) { index, offset ->
        personalContentViewModel.updateViewport(PersonalTopNavItem.FollowingSeason, index, offset)
    }

    fun refreshPageData(nav: PersonalTopNavItem) {
        when (nav) {
            PersonalTopNavItem.ToView -> {
                toViewViewModel.clearData()
                toViewViewModel.update()
            }

            PersonalTopNavItem.History -> {
                historyViewModel.clearData()
                historyViewModel.update()
            }

            PersonalTopNavItem.Favorite -> {
                favouriteViewModel.clearData()
                favouriteViewModel.updateFoldersInfo()
                favouriteViewModel.updateFolderItems(force = true)
            }

            PersonalTopNavItem.FollowingSeason -> {
                followingSeasonViewModel.clearData()
                followingSeasonViewModel.loadMore()
            }
        }
    }

    //启动时刷新数据，仅一次
    LaunchedEffect(Unit) {
        if (!personalContentViewModel.initialized) {
            personalContentViewModel.initialized = true

            scope.launch(Dispatchers.IO) {
                favouriteViewModel.updateFolderItems()
            }
            scope.launch(Dispatchers.IO) {
                historyViewModel.update()
            }
            scope.launch(Dispatchers.IO) {
                toViewViewModel.update()
            }
            scope.launch(Dispatchers.IO) {
                followingSeasonViewModel.loadMore()
            }
        }
    }


    Scaffold(
        topBar = {
            TopNav(
                modifier = Modifier,
                items = PersonalTopNavItem.entries,
                isLargePadding = !focusOnContent,
                selectedItem = selectedTab,
                defaultFocusRequester = navFocusRequester,
                onDefaultFocusReady = onDefaultFocusReady,
                isHistorySearching = historyViewModel.debouncedQuery.isNotBlank(),
                onHistoryTabDirectionUp = { isLongPress ->
                    if (selectedTab == PersonalTopNavItem.History) {
                        if (isLongPress) {
                            historyViewModel.clearSearch()
                        } else {
                            historyViewModel.openSearchDialog()
                        }
                    }
                },
                onSelectedChanged = { nav ->
                    personalContentViewModel.selectedTab = nav as PersonalTopNavItem
                },
                onClick = { nav ->
                    refreshPageData(nav as PersonalTopNavItem)
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
                        refreshPageData(selectedTab)
                        navFocusRequester.requestFocus()
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                },
        ) {

                when (selectedTab) {
                    PersonalTopNavItem.ToView -> {
                        ToViewScreen(gridState = toViewGridState)
                    }

                    PersonalTopNavItem.History -> {
                        HistoryScreen(
                            historyViewModel = historyViewModel,
                            gridState = historyGridState
                        )
                    }

                    PersonalTopNavItem.Favorite -> {
                        FavoriteScreen(
                            onBack = backToTabRow,
                            lazyGridState = favoriteGridState
                        )
                    }

                    PersonalTopNavItem.FollowingSeason -> {
                        FollowingSeasonScreen(lazyGridState = followingSeasonGridState)
                    }
                }
        }
    }
}