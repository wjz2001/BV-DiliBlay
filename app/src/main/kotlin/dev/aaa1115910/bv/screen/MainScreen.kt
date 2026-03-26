package dev.aaa1115910.bv.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.rememberDrawerState
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.settings.SettingsActivity
import dev.aaa1115910.bv.activities.user.FollowActivity
import dev.aaa1115910.bv.activities.user.LoginActivity
import dev.aaa1115910.bv.activities.user.UserSwitchActivity
import dev.aaa1115910.bv.component.UserPanel
import dev.aaa1115910.bv.component.BlackoutSwitch
import dev.aaa1115910.bv.screen.main.HomeContent
import dev.aaa1115910.bv.screen.main.LeftNaviContent
import dev.aaa1115910.bv.screen.main.LeftNaviItem
import dev.aaa1115910.bv.screen.main.PersonalContent
import dev.aaa1115910.bv.screen.main.PgcContent
import dev.aaa1115910.bv.screen.main.UgcContent
import dev.aaa1115910.bv.screen.search.SearchInputScreen
import dev.aaa1115910.bv.screen.main.common.MainDrawerPreloadHost
import dev.aaa1115910.bv.util.fException
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.requestFocus
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.repository.UserRepository
import dev.aaa1115910.bv.viewmodel.UserViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.getKoin

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    userViewModel: UserViewModel = koinViewModel(),
    userRepository: UserRepository = getKoin().get()
) {
    val context = LocalContext.current
    val logger = KotlinLogging.logger("MainScreen")
    var showUserPanel by remember { mutableStateOf(false) }
    var lastPressBack: Long by remember { mutableLongStateOf(0L) }

    val initialDrawerItem = LeftNaviItem.Home
    var requestedDrawerItem by remember { mutableStateOf(initialDrawerItem) }
    val preloadedDrawerItems = remember { mutableStateMapOf<LeftNaviItem, Boolean>() }
    val personalPreloadSessionKey = if (!userRepository.isLogin) {
        "logout"
    } else {
        "${userRepository.uid}:${userRepository.uidCkMd5}:${userRepository.sessData}"
    }

    val scope = rememberCoroutineScope()

    val personalFocusRequester = remember { FocusRequester() }
    val mainFocusRequester = remember { FocusRequester() }
    val ugcFocusRequester = remember { FocusRequester() }
    val pgcFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }

    var pendingFocusTargetItem by remember { mutableStateOf<LeftNaviItem?>(initialDrawerItem) }
    var currentReadyItem by remember { mutableStateOf<LeftNaviItem?>(null) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val fade = 0

    val handleBack = {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPressBack < 1000 * 3) {
            logger.fInfo { "Exiting bug video" }
            (context as Activity).finish()
        } else {
            lastPressBack = currentTime
            R.string.home_press_back_again_to_exit.toast(context)
        }
    }

    val requestFocusForItem: (LeftNaviItem) -> Unit = { item ->
        runCatching {
            when (item) {
                LeftNaviItem.Home -> mainFocusRequester.requestFocus(scope)
                LeftNaviItem.UGC -> ugcFocusRequester.requestFocus(scope)
                LeftNaviItem.PGC -> pgcFocusRequester.requestFocus(scope)
                LeftNaviItem.Search -> searchFocusRequester.requestFocus(scope)
                LeftNaviItem.Personal -> personalFocusRequester.requestFocus(scope)
                else -> {}
            }
        }.onFailure {
            logger.fException(it) { "request focus to content failed: $item" }
        }
    }

    val onContentDefaultFocusReady: (LeftNaviItem) -> Unit = { item ->
        if (requestedDrawerItem == item) {
            currentReadyItem = item
            if (pendingFocusTargetItem == item) {
                requestFocusForItem(item)
                pendingFocusTargetItem = null
            }
        }
    }

    val onFocusToContent: () -> Unit = {
        val target = requestedDrawerItem
        if (currentReadyItem == target) {
            requestFocusForItem(target)
        } else {
            pendingFocusTargetItem = target
        }
    }

    val onLeftNaviItemPreload: (LeftNaviItem) -> Unit = { item ->
        when (item) {
            LeftNaviItem.Home,
            LeftNaviItem.User,
            LeftNaviItem.Settings -> Unit

            LeftNaviItem.Personal -> {
                if (userViewModel.isLogin) {
                    preloadedDrawerItems[item] = true
                }
            }

            LeftNaviItem.Search,
            LeftNaviItem.UGC,
            LeftNaviItem.PGC -> {
                preloadedDrawerItems[item] = true
            }
        }
    }

    LaunchedEffect(requestedDrawerItem) {
        currentReadyItem = null
    }

    LaunchedEffect(personalPreloadSessionKey) {
        preloadedDrawerItems.remove(LeftNaviItem.Personal)
    }

    BackHandler {
        handleBack()
    }

    MainDrawerPreloadHost(
        preloadSearch = preloadedDrawerItems[LeftNaviItem.Search] == true,
        preloadPersonal = userViewModel.isLogin &&
                preloadedDrawerItems[LeftNaviItem.Personal] == true,
        preloadUgc = preloadedDrawerItems[LeftNaviItem.UGC] == true,
        preloadPgc = preloadedDrawerItems[LeftNaviItem.PGC] == true
    )

    NavigationDrawer(
        modifier = modifier,
        drawerContent = {
            LeftNaviContent(
                isLogin = userViewModel.isLogin,
                avatar = userViewModel.face,
                onLeftNaviItemChanged = { requestedDrawerItem = it },
                onLeftNaviItemPreload = onLeftNaviItemPreload,
                onOpenSettings = {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                },
                onFocusToContent = onFocusToContent,
                onShowUserPanel = {
                    showUserPanel = true
                },
                onLogin = {
                    context.startActivity(Intent(context, LoginActivity::class.java))
                }
            )
        },
        drawerState = drawerState
    ) {
        Box(modifier = Modifier) {
            BlackoutSwitch(
                targetState = requestedDrawerItem,
                fadeInMillis = fade,
                fadeOutMillis = fade
            ) { currentItem ->
                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentItem) {
                        LeftNaviItem.Search -> SearchInputScreen(
                            defaultFocusRequester = searchFocusRequester,
                            onDefaultFocusReady = {
                                onContentDefaultFocusReady(LeftNaviItem.Search)
                            }
                        )

                        LeftNaviItem.Personal -> PersonalContent(
                            navFocusRequester = personalFocusRequester,
                            onDefaultFocusReady = {
                                onContentDefaultFocusReady(LeftNaviItem.Personal)
                            }
                        )

                        LeftNaviItem.Home -> HomeContent(
                            navFocusRequester = mainFocusRequester,
                            onDefaultFocusReady = {
                                onContentDefaultFocusReady(LeftNaviItem.Home)
                            }
                        )

                        LeftNaviItem.UGC -> UgcContent(
                            navFocusRequester = ugcFocusRequester,
                            onDefaultFocusReady = {
                                onContentDefaultFocusReady(LeftNaviItem.UGC)
                            }
                        )

                        LeftNaviItem.PGC -> PgcContent(
                            navFocusRequester = pgcFocusRequester,
                            onDefaultFocusReady = {
                                onContentDefaultFocusReady(LeftNaviItem.PGC)
                            }
                        )

                        else -> Unit
                    }
                }
            }

            AnimatedVisibility(
                visible = showUserPanel,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    UserPanel(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(12.dp),
                        username = userViewModel.username,
                        face = userViewModel.face,
                        level = userViewModel.responseData?.level ?: 0,
                        currentExp = userViewModel.responseData?.levelExp?.currentExp ?: 0,
                        nextLevelExp = with(userViewModel.responseData?.levelExp?.nextExp) {
                            if (this == null) {
                                1
                            } else if (this <= 0) {
                                userViewModel.responseData?.levelExp?.currentExp ?: 1
                            } else {
                                (userViewModel.responseData?.levelExp?.currentExp ?: 1) +
                                        (userViewModel.responseData?.levelExp?.nextExp ?: 0)
                            }
                        },
                        onHide = { showUserPanel = false },
                        onGoUserSwitch = {
                            context.startActivity(Intent(context, UserSwitchActivity::class.java))
                        },
                        onGoFollowingUp = {
                            context.startActivity(Intent(context, FollowActivity::class.java))
                        },
                    )
                }
            }
        }
    }
}
