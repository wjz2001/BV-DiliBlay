package dev.aaa1115910.bv.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.settings.SettingsActivity
import dev.aaa1115910.bv.activities.user.LoginActivity
import dev.aaa1115910.bv.activities.user.UserSwitchActivity
import dev.aaa1115910.bv.component.BlackoutSwitch
import dev.aaa1115910.bv.component.MainChromeDefaults
import dev.aaa1115910.bv.repository.UserRepository
import dev.aaa1115910.bv.screen.main.FollowContent
import dev.aaa1115910.bv.screen.main.HomeContent
import dev.aaa1115910.bv.screen.main.LeftNaviContent
import dev.aaa1115910.bv.screen.main.LeftNaviItem
import dev.aaa1115910.bv.screen.main.LeftNaviUserButton
import dev.aaa1115910.bv.screen.main.PgcContent
import dev.aaa1115910.bv.screen.main.UgcContent
import dev.aaa1115910.bv.screen.main.common.MainContentEntryRequest
import dev.aaa1115910.bv.screen.main.common.MainContentFocusTarget
import dev.aaa1115910.bv.screen.main.common.MainDrawerPreloadHost
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.ui.theme.ThemeMode
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fException
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.requestFocus
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.UserViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.math.roundToInt

private data class PendingContentFocus(
    val id: Long,
    val item: LeftNaviItem,
    val entryTarget: MainContentFocusTarget? = null
)

@Composable
fun rememberIsDarkFromPrefs(): Boolean {
    val themeModeOrdinal by Prefs.themeModeFlow.collectAsState(Prefs.themeMode.ordinal)
    val themeMode = remember(themeModeOrdinal) { ThemeMode.fromOrdinal(themeModeOrdinal) }
    return themeMode == ThemeMode.DARK
}

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

    val homeDrawerFocusRequester = remember { FocusRequester() }
    val followDrawerFocusRequester = remember { FocusRequester() }
    val ugcDrawerFocusRequester = remember { FocusRequester() }
    val pgcDrawerFocusRequester = remember { FocusRequester() }
    val userFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }

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
    var leftNaviExpanded by remember { mutableStateOf(false) }
    var drawerX by remember { mutableFloatStateOf(0f) }
    var showContentScrim by remember { mutableStateOf(false) }
    var showLeftNaviContent by remember { mutableStateOf(false) }
    var userIsFocused by remember { mutableStateOf(false) }
    var userLongPressTriggered by remember { mutableStateOf(false) }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (showContentScrim) 0.35f else 0f,
        animationSpec = tween(durationMillis = 80),
        label = "left navi scrim alpha"
    )

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
                }
            }
        }
    }

    val onFocusToContent: (MainContentFocusTarget) -> Unit = { entryTarget ->
        when (val item = requestedDrawerItem) {
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

            LeftNaviItem.UGC,
            LeftNaviItem.PGC -> {
                preloadedDrawerItems[item] = true
            }
        }
    }

    LaunchedEffect(requestedDrawerItem) {
        currentReadyItem = null
        pendingContentFocus = pendingContentFocus?.takeIf { it.item == requestedDrawerItem }
    }

    fun currentDrawerFocusRequester(): FocusRequester {
        return when (requestedDrawerItem) {
            LeftNaviItem.Home -> homeDrawerFocusRequester
            LeftNaviItem.Follow -> followDrawerFocusRequester
            LeftNaviItem.UGC -> ugcDrawerFocusRequester
            LeftNaviItem.PGC -> pgcDrawerFocusRequester
            else -> homeDrawerFocusRequester
        }
    }

    fun expandLeftNavi() {
        if (leftNaviExpanded) return
        leftNaviExpanded = true
        showContentScrim = true
        scope.launch {
            delay(90)
            if (leftNaviExpanded) {
                showLeftNaviContent = true
            }
        }
    }

    fun collapseLeftNavi() {
        if (!leftNaviExpanded) return
        leftNaviExpanded = false
        showLeftNaviContent = false
        showContentScrim = false
        userFocusRequester.requestFocus(scope)
    }

    LaunchedEffect(showLeftNaviContent, requestedDrawerItem) {
        if (showLeftNaviContent) {
            currentDrawerFocusRequester().requestFocus()
            delay(16)
            currentDrawerFocusRequester().requestFocus()
        }
    }

    LaunchedEffect(followPreloadSessionKey) {
        preloadedDrawerItems.remove(LeftNaviItem.Follow)
    }

    BackHandler {
        if (leftNaviExpanded) {
            collapseLeftNavi()
        } else {
            handleBack()
        }
    }

    MainDrawerPreloadHost(
        preloadFollow = userViewModel.isLogin &&
                preloadedDrawerItems[LeftNaviItem.Follow] == true,
        preloadUgc = preloadedDrawerItems[LeftNaviItem.UGC] == true,
        preloadPgc = preloadedDrawerItems[LeftNaviItem.PGC] == true
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                        LeftNaviItem.Follow -> FollowContent(
                            navFocusRequester = followFocusRequester,
                            drawerFocusRequester = if (leftNaviExpanded) followDrawerFocusRequester else userFocusRequester,
                            pendingDrawerEntryRequest = drawerEntryRequest,
                            onDrawerEntryConsumed = consumeDrawerEntryRequest,
                            onDefaultFocusReady = {
                                onContentDefaultFocusReady(LeftNaviItem.Follow)
                            }
                        )
                        LeftNaviItem.Home -> HomeContent(
                            navFocusRequester = mainFocusRequester,
                            drawerFocusRequester = if (leftNaviExpanded) homeDrawerFocusRequester else userFocusRequester,
                            pendingDrawerEntryRequest = drawerEntryRequest,
                            onDrawerEntryConsumed = consumeDrawerEntryRequest,
                            onDefaultFocusReady = {
                                onContentDefaultFocusReady(LeftNaviItem.Home)
                            }
                        )
                        LeftNaviItem.UGC -> UgcContent(
                            navFocusRequester = ugcFocusRequester,
                            drawerFocusRequester = if (leftNaviExpanded) ugcDrawerFocusRequester else userFocusRequester,
                            pendingDrawerEntryRequest = drawerEntryRequest,
                            onDrawerEntryConsumed = consumeDrawerEntryRequest,
                            onDefaultFocusReady = {
                                onContentDefaultFocusReady(LeftNaviItem.UGC)
                            }
                        )
                        LeftNaviItem.PGC -> PgcContent(
                            navFocusRequester = pgcFocusRequester,
                            drawerFocusRequester = if (leftNaviExpanded) pgcDrawerFocusRequester else userFocusRequester,
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

        if (showContentScrim || scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .zIndex(1f)
            )
        }

        val isDarkTheme = rememberIsDarkFromPrefs()
        AnimatedVisibility(
            visible = showLeftNaviContent,
            modifier = Modifier.zIndex(2f),
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth }, // 从最左侧外开始滑入
                animationSpec = spring(
                    dampingRatio = 0.32f,
                    stiffness = Spring.StiffnessLow
                )
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth },
                animationSpec = tween(durationMillis = 250)
            )
        ) {
            LeftNaviContent(
                modifier = Modifier
                    .fillMaxHeight()
                    // 实时追踪侧边栏到底滑到了哪里
                    .onGloballyPositioned { coordinates ->
                        drawerX = coordinates.positionInRoot().x
                    }
                    .layout { measurable, constraints ->
                        val overshoot = maxOf(0f, drawerX).roundToInt()
                        val placeable = measurable.measure(
                            constraints.copy(
                                maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + overshoot else constraints.maxWidth,
                                minWidth = constraints.minWidth + overshoot
                            )
                        )
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(-overshoot, 0)
                        }
                    }
                    // 浅色模式原生阴影很明显，深色模式原生阴影看不见，两者保留共存
                    .shadow(elevation = 16.dp)
                    .background(C.background)
                    .drawWithContent {
                        drawContent()

                        val shadowWidth = 36.dp.toPx()
                        // 浅色模式降低阴影浓度（避免显得脏），深色模式保持浓郁（提升立体感）
                        val shadowAlpha = if (isDarkTheme) 0.55f else 0.06f
                        // 深色模式用白线做边缘反光，浅色模式用极淡的黑线勾勒物理轮廓
                        val outlineColor = if (isDarkTheme) {
                            Color.White.copy(alpha = 0.12f)
                        } else {
                            Color.Black.copy(alpha = 0.08f)
                        }

                        // 绘制深邃环境阴影
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Black.copy(alpha = shadowAlpha), Color.Transparent),
                                startX = size.width,
                                endX = size.width + shadowWidth
                            ),
                            topLeft = Offset(size.width, 0f),
                            size = Size(shadowWidth, size.height)
                        )

                        // 绘制切开层次的 1dp 细线
                        drawLine(
                            color = outlineColor,
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    },
                selectedItem = requestedDrawerItem,
                homeFocusRequester = homeDrawerFocusRequester,
                followFocusRequester = followDrawerFocusRequester,
                ugcFocusRequester = ugcDrawerFocusRequester,
                pgcFocusRequester = pgcDrawerFocusRequester,
                onLeftNaviItemChanged = { requestedDrawerItem = it },
                onLeftNaviItemPreload = onLeftNaviItemPreload,
                onOpenSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                onFocusToContent = {},
                userFocusRequester = userFocusRequester,
                settingsFocusRequester = settingsFocusRequester
            )
        }

        fun openUserPage() {
            if (userViewModel.isLogin) {
                context.startActivity(Intent(context, UserSwitchActivity::class.java))
            } else {
                context.startActivity(Intent(context, LoginActivity::class.java))
            }
        }

        LeftNaviUserButton(
            modifier = Modifier
                .align(Alignment.TopStart)
                // 按钮依然无缝追踪 drawerX
                .offset {
                    val overShootX = maxOf(0f, drawerX).roundToInt()
                    IntOffset(x = overShootX, y = 0)
                }
                .zIndex(3f),
            isLogin = userViewModel.isLogin,
            avatar = userViewModel.face,
            userName = userViewModel.username,
            focusRequester = userFocusRequester,
            isFocused = userIsFocused,
            onFocusChanged = {
                userIsFocused = it
                if (!it) userLongPressTriggered = false
            },
            onPreviewKeyEvent = { keyEvent ->
                val isConfirmKey = keyEvent.key == Key.DirectionCenter ||
                        keyEvent.key == Key.Enter ||
                        keyEvent.key == Key.Spacebar

                when {
                    isConfirmKey &&
                            keyEvent.type == KeyEventType.KeyDown &&
                            keyEvent.nativeKeyEvent.isLongPress -> {
                        if (!userLongPressTriggered) {
                            userLongPressTriggered = true
                            openUserPage()
                        }
                        true
                    }

                    isConfirmKey &&
                            keyEvent.type == KeyEventType.KeyUp -> {
                        if (userLongPressTriggered) {
                            // 这是长按后的抬起：不要再触发短按逻辑
                            userLongPressTriggered = false
                        } else {
                            // 短按：切换抽屉
                            if (leftNaviExpanded) collapseLeftNavi() else expandLeftNavi()
                        }
                        true
                    }

                    leftNaviExpanded && keyEvent.type == KeyEventType.KeyDown &&
                            keyEvent.key == Key.DirectionDown -> {
                        currentDrawerFocusRequester().requestFocus(scope)
                        true
                    }

                    leftNaviExpanded && keyEvent.type == KeyEventType.KeyDown &&
                            keyEvent.key == Key.DirectionUp -> {
                        settingsFocusRequester.requestFocus(scope)
                        true
                    }

                    leftNaviExpanded &&
                            (keyEvent.key == Key.DirectionLeft || keyEvent.key == Key.DirectionRight) -> true

                    !leftNaviExpanded && keyEvent.type == KeyEventType.KeyDown &&
                            keyEvent.key == Key.DirectionRight -> {
                        onFocusToContent(MainContentFocusTarget.LeftEntry)
                        true
                    }

                    !leftNaviExpanded && keyEvent.type == KeyEventType.KeyDown &&
                            keyEvent.key == Key.DirectionLeft -> {
                        onFocusToContent(MainContentFocusTarget.RightEntry)
                        true
                    }

                    !leftNaviExpanded &&
                            (keyEvent.key == Key.DirectionUp || keyEvent.key == Key.DirectionDown) -> true

                    else -> false
                }
            },
            onClick = { if (leftNaviExpanded) collapseLeftNavi() else expandLeftNavi() }
        )
    }
}
