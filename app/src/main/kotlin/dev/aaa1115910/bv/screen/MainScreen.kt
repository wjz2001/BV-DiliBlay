package dev.aaa1115910.bv.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material3.VerticalDivider
import androidx.tv.material3.MaterialTheme
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.settings.SettingsActivity
import dev.aaa1115910.bv.activities.user.LoginActivity
import dev.aaa1115910.bv.activities.user.UserSwitchActivity
import dev.aaa1115910.bv.component.BlackoutSwitch
import dev.aaa1115910.bv.repository.UserRepository
import dev.aaa1115910.bv.screen.main.FollowContent
import dev.aaa1115910.bv.screen.main.HomeContent
import dev.aaa1115910.bv.screen.main.LeftNaviContent
import dev.aaa1115910.bv.screen.main.LeftNaviItem
import dev.aaa1115910.bv.screen.main.PgcContent
import dev.aaa1115910.bv.screen.main.UgcContent
import dev.aaa1115910.bv.screen.main.common.MainContentEntryRequest
import dev.aaa1115910.bv.screen.main.common.MainContentFocusTarget
import dev.aaa1115910.bv.screen.main.common.MainDrawerPreloadHost
import dev.aaa1115910.bv.screen.search.MainDrawerSearchInputScreen
import dev.aaa1115910.bv.screen.search.SearchRightEntryToken
import dev.aaa1115910.bv.ui.theme.AppWhite
import dev.aaa1115910.bv.util.fException
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.requestFocus
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.UserViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private data class PendingContentFocus(
    val id: Long,
    val item: LeftNaviItem,
    val entryTarget: MainContentFocusTarget? = null
)

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    userViewModel: UserViewModel = koinViewModel(),
    userRepository: UserRepository = koinInject()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val logger = KotlinLogging.logger("MainScreen")
    var lastPressBack: Long by remember { mutableLongStateOf(0L) }

    val initialDrawerItem = LeftNaviItem.Home
    var requestedDrawerItem by remember { mutableStateOf(initialDrawerItem) }
    val preloadedDrawerItems = remember { mutableStateMapOf<LeftNaviItem, Boolean>() }
    val followPreloadSessionKey = if (!userRepository.isLogin) {
        "logout"
    } else {
        "${userRepository.uid}:${userRepository.uidCkMd5}:${userRepository.sessData}"
    }

    val scope = rememberCoroutineScope()

    val followFocusRequester = remember { FocusRequester() }
    val mainFocusRequester = remember { FocusRequester() }
    val ugcFocusRequester = remember { FocusRequester() }
    val pgcFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }

    val searchDrawerFocusRequester = remember { FocusRequester() }
    val homeDrawerFocusRequester = remember { FocusRequester() }
    val followDrawerFocusRequester = remember { FocusRequester() }
    val ugcDrawerFocusRequester = remember { FocusRequester() }
    val pgcDrawerFocusRequester = remember { FocusRequester() }
    val searchRightEntryFocusRequester = remember { FocusRequester() }

    var contentFocusRequestSerial by remember { mutableLongStateOf(0L) }
    var pendingContentFocus by remember {
        mutableStateOf<PendingContentFocus?>(
            PendingContentFocus(
                id = 0L,
                item = initialDrawerItem
            )
        )
    }
    var currentReadyItem by remember { mutableStateOf<LeftNaviItem?>(null) }
    var searchCurrentRightEntryToken by remember { mutableStateOf<SearchRightEntryToken?>(null) }
    var searchRightEntryReadyToken by remember { mutableStateOf<SearchRightEntryToken?>(null) }

    fun newPendingContentFocus(
        item: LeftNaviItem,
        entryTarget: MainContentFocusTarget?
    ): PendingContentFocus {
        contentFocusRequestSerial += 1
        return PendingContentFocus(
            id = contentFocusRequestSerial,
            item = item,
            entryTarget = entryTarget
        )
    }

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

    val requestFocusForContent: (LeftNaviItem, MainContentFocusTarget?) -> Unit =
        { item, entryTarget ->
            runCatching {
                when (item) {
                    LeftNaviItem.Home -> mainFocusRequester.requestFocus(scope)
                    LeftNaviItem.UGC -> ugcFocusRequester.requestFocus(scope)
                    LeftNaviItem.PGC -> pgcFocusRequester.requestFocus(scope)
                    LeftNaviItem.Follow -> followFocusRequester.requestFocus(scope)
                    LeftNaviItem.Search -> {
                        val requester = when {
                            entryTarget == MainContentFocusTarget.RightEntry &&
                                    searchCurrentRightEntryToken != null &&
                                    searchCurrentRightEntryToken == searchRightEntryReadyToken -> {
                                searchRightEntryFocusRequester
                            }

                            else -> searchFocusRequester
                        }
                        requester.requestFocus(scope)
                    }

                    else -> Unit
                }
            }.onFailure {
                logger.fException(it) { "request focus to content failed: $item / $entryTarget" }
            }
        }

    val onContentDefaultFocusReady: (LeftNaviItem) -> Unit = { item ->
        if (requestedDrawerItem == item) {
            currentReadyItem = item

            val pending = pendingContentFocus
            if (pending != null && pending.item == item) {
                when {
                    pending.entryTarget == null -> {
                        requestFocusForContent(item, null)
                        pendingContentFocus = null
                    }

                    item == LeftNaviItem.Search &&
                            pending.entryTarget == MainContentFocusTarget.LeftEntry -> {
                        requestFocusForContent(item, MainContentFocusTarget.LeftEntry)
                        pendingContentFocus = null
                    }

                    item == LeftNaviItem.Search &&
                            pending.entryTarget == MainContentFocusTarget.RightEntry &&
                            searchCurrentRightEntryToken == null -> {
                        requestFocusForContent(item, null)
                        pendingContentFocus = null
                    }

                    item == LeftNaviItem.Search &&
                            pending.entryTarget == MainContentFocusTarget.RightEntry &&
                            searchCurrentRightEntryToken != null &&
                            searchCurrentRightEntryToken == searchRightEntryReadyToken -> {
                        requestFocusForContent(item, MainContentFocusTarget.RightEntry)
                        pendingContentFocus = null
                    }
                }
            }
        }
    }

    val onSearchCurrentRightEntryTokenChanged: (SearchRightEntryToken?) -> Unit = { token ->
        searchCurrentRightEntryToken = token

        val pending = pendingContentFocus
        if (requestedDrawerItem == LeftNaviItem.Search &&
            currentReadyItem == LeftNaviItem.Search &&
            pending?.item == LeftNaviItem.Search &&
            pending.entryTarget == MainContentFocusTarget.RightEntry
        ) {
            if (token == null) {
                requestFocusForContent(LeftNaviItem.Search, null)
                pendingContentFocus = null
            } else if (token == searchRightEntryReadyToken) {
                requestFocusForContent(LeftNaviItem.Search, MainContentFocusTarget.RightEntry)
                pendingContentFocus = null
            }
        }
    }

    val onSearchRightEntryReady: (SearchRightEntryToken) -> Unit = { token ->
        searchRightEntryReadyToken = token

        val pending = pendingContentFocus
        if (requestedDrawerItem == LeftNaviItem.Search &&
            currentReadyItem == LeftNaviItem.Search &&
            pending?.item == LeftNaviItem.Search &&
            pending.entryTarget == MainContentFocusTarget.RightEntry &&
            searchCurrentRightEntryToken == token
        ) {
            requestFocusForContent(LeftNaviItem.Search, MainContentFocusTarget.RightEntry)
            pendingContentFocus = null
        }
    }

    val onFocusToContent: (MainContentFocusTarget) -> Unit = { entryTarget ->
        val item = requestedDrawerItem

        when (item) {
            LeftNaviItem.Search -> {
                if (currentReadyItem == LeftNaviItem.Search) {
                    when (entryTarget) {
                        MainContentFocusTarget.LeftEntry -> {
                            requestFocusForContent(
                                LeftNaviItem.Search,
                                MainContentFocusTarget.LeftEntry
                            )
                        }

                        MainContentFocusTarget.RightEntry -> {
                            when (searchCurrentRightEntryToken) {
                                null -> {
                                    requestFocusForContent(LeftNaviItem.Search, null)
                                }
                                searchRightEntryReadyToken -> {
                                    requestFocusForContent(
                                        LeftNaviItem.Search,
                                        MainContentFocusTarget.RightEntry
                                    )
                                }
                                else -> {
                                    pendingContentFocus = newPendingContentFocus(
                                        item = LeftNaviItem.Search,
                                        entryTarget = MainContentFocusTarget.RightEntry
                                    )
                                }
                            }
                        }
                    }
                } else {
                    pendingContentFocus = newPendingContentFocus(
                        item = LeftNaviItem.Search,
                        entryTarget = entryTarget
                    )
                }
            }

            LeftNaviItem.Home,
            LeftNaviItem.Follow,
            LeftNaviItem.UGC,
            LeftNaviItem.PGC -> {
                pendingContentFocus = newPendingContentFocus(
                    item = item,
                    entryTarget = entryTarget
                )
            }

            else -> Unit
        }
    }

    val onLeftNaviItemPreload: (LeftNaviItem) -> Unit = { item ->
        when (item) {
            LeftNaviItem.Home,
            LeftNaviItem.User,
            LeftNaviItem.Settings -> Unit

            LeftNaviItem.Follow -> {
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
        searchCurrentRightEntryToken = null
        searchRightEntryReadyToken = null
        pendingContentFocus = pendingContentFocus?.takeIf { it.item == requestedDrawerItem }
    }

    LaunchedEffect(followPreloadSessionKey) {
        preloadedDrawerItems.remove(LeftNaviItem.Follow)
    }

    BackHandler {
        handleBack()
    }

    MainDrawerPreloadHost(
        preloadSearch = preloadedDrawerItems[LeftNaviItem.Search] == true,
        preloadFollow = userViewModel.isLogin &&
                preloadedDrawerItems[LeftNaviItem.Follow] == true,
        preloadUgc = preloadedDrawerItems[LeftNaviItem.UGC] == true,
        preloadPgc = preloadedDrawerItems[LeftNaviItem.PGC] == true
    )

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LeftNaviContent(
            modifier = Modifier.fillMaxHeight(),
            isLogin = userViewModel.isLogin,
            avatar = userViewModel.face,
            selectedItem = requestedDrawerItem,
            searchFocusRequester = searchDrawerFocusRequester,
            homeFocusRequester = homeDrawerFocusRequester,
            followFocusRequester = followDrawerFocusRequester,
            ugcFocusRequester = ugcDrawerFocusRequester,
            pgcFocusRequester = pgcDrawerFocusRequester,
            onLeftNaviItemChanged = { requestedDrawerItem = it },
            onLeftNaviItemPreload = onLeftNaviItemPreload,
            onOpenSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
            onFocusToContent = onFocusToContent,
            onOpenUserSwitch = { context.startActivity(Intent(context, UserSwitchActivity::class.java)) },
            onLogin = { context.startActivity(Intent(context, LoginActivity::class.java)) }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            BlackoutSwitch(
                targetState = requestedDrawerItem,
                fadeInMillis = fade,
                fadeOutMillis = fade
            ) { currentItem ->
                val drawerEntryRequest = pendingContentFocus
                    ?.takeIf { it.item == currentItem && it.entryTarget != null }
                    ?.let { request ->
                        MainContentEntryRequest(
                            id = request.id,
                            target = request.entryTarget!!
                        )
                    }
                val consumeDrawerEntryRequest: (Long) -> Unit = { requestId ->
                    if (pendingContentFocus?.id == requestId) {
                        pendingContentFocus = null
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentItem) {
                        LeftNaviItem.Search -> MainDrawerSearchInputScreen(
                            defaultFocusRequester = searchFocusRequester,
                            drawerFocusRequester = searchDrawerFocusRequester,
                            rightEntryFocusRequester = searchRightEntryFocusRequester,
                            onDefaultFocusReady = {
                                onContentDefaultFocusReady(LeftNaviItem.Search)
                            },
                            onCurrentRightEntryTokenChanged = onSearchCurrentRightEntryTokenChanged,
                            onRightEntryFocusReady = onSearchRightEntryReady
                        )

                        LeftNaviItem.Follow -> FollowContent(
                            navFocusRequester = followFocusRequester,
                            drawerFocusRequester = followDrawerFocusRequester,
                            pendingDrawerEntryRequest = drawerEntryRequest,
                            onDrawerEntryConsumed = consumeDrawerEntryRequest,
                            onDefaultFocusReady = {
                                onContentDefaultFocusReady(LeftNaviItem.Follow)
                            }
                        )

                        LeftNaviItem.Home -> HomeContent(
                            navFocusRequester = mainFocusRequester,
                            drawerFocusRequester = homeDrawerFocusRequester,
                            pendingDrawerEntryRequest = drawerEntryRequest,
                            onDrawerEntryConsumed = consumeDrawerEntryRequest,
                            onDefaultFocusReady = {
                                onContentDefaultFocusReady(LeftNaviItem.Home)
                            }
                        )

                        LeftNaviItem.UGC -> UgcContent(
                            navFocusRequester = ugcFocusRequester,
                            drawerFocusRequester = ugcDrawerFocusRequester,
                            pendingDrawerEntryRequest = drawerEntryRequest,
                            onDrawerEntryConsumed = consumeDrawerEntryRequest,
                            onDefaultFocusReady = {
                                onContentDefaultFocusReady(LeftNaviItem.UGC)
                            }
                        )

                        LeftNaviItem.PGC -> PgcContent(
                            navFocusRequester = pgcFocusRequester,
                            drawerFocusRequester = pgcDrawerFocusRequester,
                            pendingDrawerEntryRequest = drawerEntryRequest,
                            onDrawerEntryConsumed = consumeDrawerEntryRequest,
                            onDefaultFocusReady = {
                                onContentDefaultFocusReady(LeftNaviItem.PGC)
                            }
                        )

                        else -> Unit
                    }
                }
            }
        }
    }
}
