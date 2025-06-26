package dev.aaa1115910.bv.screen.main

import androidx.activity.compose.BackHandler
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
import dev.aaa1115910.biliapi.entity.ugc.UgcType
import dev.aaa1115910.bv.component.TopNav
import dev.aaa1115910.bv.component.UgcTopNavItem
import dev.aaa1115910.bv.screen.main.ugc.UgcRegionContent
import dev.aaa1115910.bv.screen.main.ugc.rememberUgcScaffoldState
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.requestFocus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun UgcContent(
    modifier: Modifier = Modifier,
    navFocusRequester: FocusRequester
) {
    val scope = rememberCoroutineScope()
    val logger = KotlinLogging.logger("UgcContent")

    var selectedTab by remember { mutableStateOf(UgcTopNavItem.Douga) }
    var focusOnContent by remember { mutableStateOf(false) }

    val ugcStateMap = mapOf(
        UgcTopNavItem.Douga to rememberUgcScaffoldState(ugcType = UgcType.Douga),
        UgcTopNavItem.Game to rememberUgcScaffoldState(ugcType = UgcType.Game),
        UgcTopNavItem.Kichiku to rememberUgcScaffoldState(ugcType = UgcType.Kichiku),
        UgcTopNavItem.Music to rememberUgcScaffoldState(ugcType = UgcType.Music),
        UgcTopNavItem.Dance to rememberUgcScaffoldState(ugcType = UgcType.Dance),
        UgcTopNavItem.Cinephile to rememberUgcScaffoldState(ugcType = UgcType.Cinephile),
        UgcTopNavItem.Ent to rememberUgcScaffoldState(ugcType = UgcType.Ent),
        UgcTopNavItem.Knowledge to rememberUgcScaffoldState(ugcType = UgcType.Knowledge),
        UgcTopNavItem.Tech to rememberUgcScaffoldState(ugcType = UgcType.Tech),
        UgcTopNavItem.Information to rememberUgcScaffoldState(ugcType = UgcType.Information),
        UgcTopNavItem.Food to rememberUgcScaffoldState(ugcType = UgcType.Food),
        UgcTopNavItem.Life to rememberUgcScaffoldState(ugcType = UgcType.Life),
        UgcTopNavItem.Car to rememberUgcScaffoldState(ugcType = UgcType.Car),
        UgcTopNavItem.Fashion to rememberUgcScaffoldState(ugcType = UgcType.Fashion),
        UgcTopNavItem.Sports to rememberUgcScaffoldState(ugcType = UgcType.Sports),
        UgcTopNavItem.Animal to rememberUgcScaffoldState(ugcType = UgcType.Animal)
    )


    //启动时刷新数据
    LaunchedEffect(Unit) {

    }

    BackHandler(focusOnContent) {
        logger.fInfo { "onFocusBackToNav" }
        navFocusRequester.requestFocus(scope)
        // scroll to top
        scope.launch(Dispatchers.Main) {
            ugcStateMap[selectedTab]!!.lazyListState.animateScrollToItem(0)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopNav(
                modifier = Modifier
                    .focusRequester(navFocusRequester),
                items = UgcTopNavItem.entries,
                isLargePadding = !focusOnContent,
                onSelectedChanged = { nav ->
                    selectedTab = nav as UgcTopNavItem
                },
                onClick = { nav ->
                    ugcStateMap[nav]!!.reloadAll()
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .onFocusChanged { focusOnContent = it.hasFocus }
        ) {
            AnimatedContent(
                targetState = selectedTab,
                label = "ugc animated content",
                transitionSpec = {
                    val coefficient = 10
                    if (targetState.ordinal < initialState.ordinal) {
                        fadeIn() + slideInHorizontally { -it / coefficient } togetherWith
                                fadeOut() + slideOutHorizontally { it / coefficient }
                    } else {
                        fadeIn() + slideInHorizontally { it / coefficient } togetherWith
                                fadeOut() + slideOutHorizontally { -it / coefficient }
                    }
                }
            ) { screen ->
                UgcRegionContent(state = ugcStateMap[screen]!!)
            }
        }
    }
}