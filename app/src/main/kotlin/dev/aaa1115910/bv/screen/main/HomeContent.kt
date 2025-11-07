package dev.aaa1115910.bv.screen.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.component.HomeTopNavItem
import dev.aaa1115910.bv.component.TopNav
import dev.aaa1115910.bv.screen.main.home.DynamicsScreen
import dev.aaa1115910.bv.screen.main.home.PopularScreen
import dev.aaa1115910.bv.screen.main.home.RecommendScreen
import dev.aaa1115910.bv.screen.user.FavoriteScreen
import dev.aaa1115910.bv.screen.user.FollowingSeasonScreen
import dev.aaa1115910.bv.screen.user.HistoryScreen
import dev.aaa1115910.bv.screen.user.ToViewScreen
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.viewmodel.UserViewModel
import dev.aaa1115910.bv.viewmodel.home.DynamicViewModel
import dev.aaa1115910.bv.viewmodel.home.PopularViewModel
import dev.aaa1115910.bv.viewmodel.home.RecommendViewModel
import dev.aaa1115910.bv.viewmodel.user.FavoriteViewModel
import dev.aaa1115910.bv.viewmodel.user.FollowingSeasonViewModel
import dev.aaa1115910.bv.viewmodel.user.HistoryViewModel
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeContent(
    navFocusRequester: FocusRequester,
    recommendViewModel: RecommendViewModel = koinViewModel(),
    popularViewModel: PopularViewModel = koinViewModel(),
    dynamicViewModel: DynamicViewModel = koinViewModel(),
    userViewModel: UserViewModel = koinViewModel(),
    favouriteViewModel: FavoriteViewModel = koinViewModel(),
    historyViewModel: HistoryViewModel = koinViewModel(),
    toViewViewModel: ToViewViewModel = koinViewModel(),
    followingSeasonViewModel: FollowingSeasonViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    val logger = KotlinLogging.logger("HomeContent")

    val firstTab = remember { Prefs.firstHomeTopNavItem }
    var selectedTab by remember { mutableStateOf(firstTab) }
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

    //启动时刷新数据
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            recommendViewModel.loadMore()
        }
        scope.launch(Dispatchers.IO) {
            popularViewModel.loadMore()
        }
        scope.launch(Dispatchers.IO) {
            dynamicViewModel.loadMore()
        }
        scope.launch(Dispatchers.IO) {
            userViewModel.updateUserInfo()
        }
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

    //监听登录变化
    LaunchedEffect(userViewModel.isLogin) {
        if (userViewModel.isLogin) {
            //login
            userViewModel.updateUserInfo()
        } else {
            //logout
            userViewModel.clearUserInfo()
        }
    }

    Scaffold(
        topBar = {
            TopNav(
                modifier = Modifier
                    .focusRequester(navFocusRequester),
                items = reorderedItems,
                isLargePadding = !focusOnContent,
                onSelectedChanged = { nav ->
                    selectedTab = nav as HomeTopNavItem
                    when (nav) {
                        HomeTopNavItem.Recommend -> {}
                        HomeTopNavItem.Popular -> {}
                        HomeTopNavItem.Dynamics -> {
                            /*
                            if (!dynamicViewModel.loading && dynamicViewModel.isLogin && dynamicViewModel.dynamicList.isEmpty()) {
                                scope.launch(Dispatchers.IO) { dynamicViewModel.loadMore() }
                            }
                             */
                        }
                        HomeTopNavItem.ToView -> {
                            toViewViewModel.clearData()
                            toViewViewModel.update()
                        }
                        HomeTopNavItem.History -> {
                            historyViewModel.clearData()
                            historyViewModel.update()
                        }
                        HomeTopNavItem.Favorite -> {
                            favouriteViewModel.clearData()
                            favouriteViewModel.updateFoldersInfo()
                            favouriteViewModel.updateFolderItems(force = true)
                        }
                        HomeTopNavItem.FollowingSeason -> {
                            followingSeasonViewModel.clearData()
                            followingSeasonViewModel.loadMore()
                        }
                    }
                },
                onClick = { nav ->
                    when (nav) {
                        HomeTopNavItem.Recommend -> {
                            logger.fInfo { "clear recommend data" }
                            recommendViewModel.clearData()
                            logger.fInfo { "reload recommend data" }
                            scope.launch(Dispatchers.IO) { recommendViewModel.loadMore() }
                        }

                        HomeTopNavItem.Popular -> {
                            logger.fInfo { "clear popular data" }
                            popularViewModel.clearData()
                            logger.fInfo { "reload popular data" }
                            scope.launch(Dispatchers.IO) { popularViewModel.loadMore() }
                        }

                        HomeTopNavItem.Dynamics -> {
                            dynamicViewModel.clearData()
                            scope.launch(Dispatchers.IO) { dynamicViewModel.loadMore() }
                        }
                        HomeTopNavItem.ToView -> {
                            toViewViewModel.clearData()
                            scope.launch(Dispatchers.IO) { toViewViewModel.update() }
                        }
                        HomeTopNavItem.History -> {
                            historyViewModel.clearData()
                            scope.launch(Dispatchers.IO) { historyViewModel.update() }
                        }
                        HomeTopNavItem.Favorite -> {
                            favouriteViewModel.clearData()
                            scope.launch(Dispatchers.IO) {
                                favouriteViewModel.updateFoldersInfo()
                                favouriteViewModel.updateFolderItems(force = true)
                            }
                        }
                        HomeTopNavItem.FollowingSeason -> {
                            followingSeasonViewModel.clearData()
                            scope.launch(Dispatchers.IO) { followingSeasonViewModel.loadMore() }
                        }
                    }
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
                        when (selectedTab) {
                            HomeTopNavItem.Recommend -> {
                                recommendViewModel.clearData()
                                scope.launch(Dispatchers.IO) { recommendViewModel.loadMore() }
                            }

                            HomeTopNavItem.Popular -> {
                                popularViewModel.clearData()
                                scope.launch(Dispatchers.IO) { popularViewModel.loadMore() }
                            }

                            HomeTopNavItem.Dynamics -> {
                                dynamicViewModel.clearData()
                                scope.launch(Dispatchers.IO) { dynamicViewModel.loadMore() }
                            }
                            HomeTopNavItem.ToView -> {
                                toViewViewModel.clearData()
                                scope.launch(Dispatchers.IO) { toViewViewModel.update() }
                            }
                            HomeTopNavItem.History -> {
                                historyViewModel.clearData()
                                scope.launch(Dispatchers.IO) { historyViewModel.update() }
                            }
                            HomeTopNavItem.Favorite -> {
                                favouriteViewModel.clearData()
                                scope.launch(Dispatchers.IO) {
                                    favouriteViewModel.updateFoldersInfo()
                                    favouriteViewModel.updateFolderItems(force = true)
                                }
                            }
                            HomeTopNavItem.FollowingSeason -> {
                                followingSeasonViewModel.clearData()
                                scope.launch(Dispatchers.IO) { followingSeasonViewModel.loadMore() }
                            }
                        }
                        navFocusRequester.requestFocus()
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                },
        ) {
            AnimatedContent(
                targetState = selectedTab,
                label = "home animated content",
                transitionSpec = {
                    val coefficient = 10
                    if (reorderedItems.indexOf(targetState) < reorderedItems.indexOf(initialState)) {
                        fadeIn() + slideInHorizontally { -it / coefficient } togetherWith
                                fadeOut() + slideOutHorizontally { it / coefficient }
                    } else {
                        fadeIn() + slideInHorizontally { it / coefficient } togetherWith
                                fadeOut() + slideOutHorizontally { -it / coefficient }
                    }
                }
            ) { screen ->
                when (screen) {
                    HomeTopNavItem.Recommend -> RecommendScreen()
                    HomeTopNavItem.Popular -> PopularScreen()
                    HomeTopNavItem.Dynamics -> DynamicsScreen()
                    HomeTopNavItem.ToView -> ToViewScreen()
                    HomeTopNavItem.History -> HistoryScreen()
                    HomeTopNavItem.Favorite -> FavoriteScreen(onBack = backToTabRow)
                    HomeTopNavItem.FollowingSeason -> FollowingSeasonScreen()
                }
            }
        }
    }
}
