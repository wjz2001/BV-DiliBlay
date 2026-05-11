package dev.aaa1115910.bv.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
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
import dev.aaa1115910.bv.screen.main.LeftNaviUserButton
import dev.aaa1115910.bv.screen.main.PgcContent
import dev.aaa1115910.bv.screen.main.UgcContent
import dev.aaa1115910.bv.screen.main.common.MainContentEntryRequest
import dev.aaa1115910.bv.screen.main.common.MainContentFocusTarget
import dev.aaa1115910.bv.screen.main.runtime.runtimeContainerInputEnabled
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.ui.theme.ThemeMode
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fException
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.requestFocus
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.UserViewModel
import dev.aaa1115910.bv.viewmodel.main.HomeContentViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private data class PendingContentFocus(
    val id: Long,
    val item: LeftNaviItem,
    val entryTarget: MainContentFocusTarget? = null
)

@Composable
fun rememberIsDarkFromPrefs(): Boolean {
    val themeModeOrdinal by Prefs.themeModeFlow.collectAsStateWithLifecycle(
        initialValue = Prefs.themeMode.ordinal
    )
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
    var focusedDrawerItem by remember { mutableStateOf(initialDrawerItem) }
    var activeDrawerItem by remember { mutableStateOf(initialDrawerItem) }
    var lastActiveDrawerItem by remember { mutableStateOf<LeftNaviItem?>(null) }

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

    // 状态控制
    var leftNaviExpanded by remember { mutableStateOf(false) }
    var showCollapsedUserButton by remember { mutableStateOf(true) }
    var showFirstLaunchMainDialog by remember { mutableStateOf(Prefs.showFirstLaunchMainDialog) }
    var userIsFocused by remember { mutableStateOf(false) }
    var userLongPressTriggered by remember { mutableStateOf(false) }
    var userButtonColorAnimationEnabled by remember { mutableStateOf(true) }

    // 记录抽屉动态宽度（精确用于撞击计算）
    var drawerWidthPx by remember { mutableFloatStateOf(0f) }

    // 抽屉动画：迅捷、干脆
    val drawerSlideProgress by animateFloatAsState(
        targetValue = if (leftNaviExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "drawer_slide"
    )

    // 主内容区被撞飞动画：带有Q弹缓冲效果（阻尼较低，刚度较低，会被抽屉短暂覆盖后弹开）
    val mainContentPushProgress by animateFloatAsState(
        targetValue = if (leftNaviExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.25f, // 更弹
            stiffness = 30f
        ),
        label = "main_content_push"
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
        if (activeDrawerItem == item) {
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

    fun isContentItem(item: LeftNaviItem): Boolean {
        return item == LeftNaviItem.Home ||
                item == LeftNaviItem.Follow ||
                item == LeftNaviItem.UGC ||
                item == LeftNaviItem.PGC
    }

    fun requestDefaultFocusForActiveContent() {
        val item = activeDrawerItem
        if (!isContentItem(item)) {
            userFocusRequester.requestFocus()
            return
        }

        pendingContentFocus = newPendingContentFocus(
            item = item,
            entryTarget = null
        )
        requestFocusForContent(item, null)
        if (currentReadyItem == item) {
            pendingContentFocus = null
        }
    }

    val onFocusToContent: (MainContentFocusTarget) -> Unit = { entryTarget ->
        val resolvedItem = if (leftNaviExpanded) focusedDrawerItem else activeDrawerItem
        logger.fInfo {
            "onFocusToContent: active=$activeDrawerItem, focused=$focusedDrawerItem, resolved=$resolvedItem, target=$entryTarget"
        }
        if (isContentItem(resolvedItem)) {
                if (activeDrawerItem != resolvedItem) {
                    lastActiveDrawerItem = activeDrawerItem
                }
                focusedDrawerItem = resolvedItem
                activeDrawerItem = resolvedItem
                pendingContentFocus = newPendingContentFocus(
                    item = resolvedItem,
                    entryTarget = entryTarget
                )
                logger.fInfo {
                    "new pending content focus: item=$resolvedItem, target=$entryTarget, pendingId=${pendingContentFocus?.id}"
                }
        }
    }

    fun focusDrawerItem(item: LeftNaviItem) {
        focusedDrawerItem = item
    }

    fun activateDrawerItem(item: LeftNaviItem) {
        if (activeDrawerItem != item) {
            lastActiveDrawerItem = activeDrawerItem
        }
        focusedDrawerItem = item
        activeDrawerItem = item
    }

    LaunchedEffect(activeDrawerItem) {
        currentReadyItem = null
        pendingContentFocus = pendingContentFocus?.takeIf { it.item == activeDrawerItem }
    }

    fun currentDrawerFocusRequester(): FocusRequester {
        return when (focusedDrawerItem) {
            LeftNaviItem.Home -> homeDrawerFocusRequester
            LeftNaviItem.Follow -> followDrawerFocusRequester
            LeftNaviItem.UGC -> ugcDrawerFocusRequester
            LeftNaviItem.PGC -> pgcDrawerFocusRequester
            else -> homeDrawerFocusRequester
        }
    }

    fun expandLeftNavi() {
        if (leftNaviExpanded) return
        userButtonColorAnimationEnabled = false
        showCollapsedUserButton = false
        leftNaviExpanded = true
    }

    fun collapseLeftNavi() {
        if (!leftNaviExpanded) return
        leftNaviExpanded = false
        userButtonColorAnimationEnabled = true
        scope.launch {
            delay(360) // 等待动画完成再恢复焦点和头像按钮
            if (!leftNaviExpanded) {
                showCollapsedUserButton = true
                requestDefaultFocusForActiveContent()
            }
        }
    }

    fun openUserPage() {
        if (userViewModel.isLogin) {
            context.startActivity(Intent(context, UserSwitchActivity::class.java))
        } else {
            context.startActivity(Intent(context, LoginActivity::class.java))
        }
    }

    val onUserButtonPreviewKeyEvent: (androidx.compose.ui.input.key.KeyEvent) -> Boolean =
        { keyEvent ->
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

                isConfirmKey && keyEvent.type == KeyEventType.KeyUp -> {
                    if (userLongPressTriggered) {
                        userLongPressTriggered = false
                    } else {
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
        }

    LaunchedEffect(leftNaviExpanded, focusedDrawerItem) {
        if (leftNaviExpanded) {
            // 给抽屉一点时间上屏，然后请求焦点
            delay(16)
            currentDrawerFocusRequester().requestFocus()
            delay(16)
            currentDrawerFocusRequester().requestFocus()
        }
    }

    BackHandler {
        if (leftNaviExpanded) {
            collapseLeftNavi()
        } else {
            handleBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ========== 主页内容区域 ==========
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .graphicsLayer {
                    // 仅在 Draw 阶段读取 mainContentPushProgress，计算方式：将主页面向右侧"撞飞"，最大距离等于抽屉的确切像素宽度
                    translationX = drawerWidthPx * mainContentPushProgress
                }
        ) {
            BlackoutSwitch(
                targetState = activeDrawerItem,
                fadeInMillis = fade,
                fadeOutMillis = fade
            ) { currentItem ->
                val consumeDrawerEntryRequest: (Long) -> Unit = { requestId ->
                    if (pendingContentFocus?.id == requestId) {
                        pendingContentFocus = null
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    val mountedDrawerItems = listOfNotNull(lastActiveDrawerItem, currentItem).distinct()
                    mountedDrawerItems.forEach { item ->
                        val activeContent = item == currentItem
                        val drawerEntryRequest = pendingContentFocus
                            ?.takeIf {
                                activeContent && it.item == item && it.entryTarget != null
                            }
                            ?.let { request ->
                                MainContentEntryRequest(
                                    id = request.id,
                                    target = request.entryTarget!!
                                )
                            }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (activeContent) 1f else 0f)
                                .graphicsLayer { alpha = if (activeContent) 1f else 0f }
                                .runtimeContainerInputEnabled(activeContent)
                        ) {
                            when (item) {
                                LeftNaviItem.Follow -> FollowContent(
                                    navFocusRequester = followFocusRequester,
                                    drawerFocusRequester = if (leftNaviExpanded) followDrawerFocusRequester else userFocusRequester,
                                    pendingDrawerEntryRequest = drawerEntryRequest,
                                    onDrawerEntryConsumed = consumeDrawerEntryRequest,
                                    onDefaultFocusReady = {
                                        onContentDefaultFocusReady(LeftNaviItem.Follow)
                                    },
                                    active = activeContent
                                )

                                LeftNaviItem.Home -> {
                                    val homeContentViewModel: HomeContentViewModel =
                                        koinViewModel<HomeContentViewModel>()
                                    HomeContent(
                                        navFocusRequester = mainFocusRequester,
                                        drawerFocusRequester = if (leftNaviExpanded) homeDrawerFocusRequester else userFocusRequester,
                                        pendingDrawerEntryRequest = drawerEntryRequest,
                                        onDrawerEntryConsumed = consumeDrawerEntryRequest,
                                        onDefaultFocusReady = {
                                            onContentDefaultFocusReady(LeftNaviItem.Home)
                                        },
                                        homeContentViewModel = homeContentViewModel,
                                        userViewModel = userViewModel,
                                        active = activeContent
                                    )
                                }

                                LeftNaviItem.UGC -> UgcContent(
                                    navFocusRequester = ugcFocusRequester,
                                    drawerFocusRequester = if (leftNaviExpanded) ugcDrawerFocusRequester else userFocusRequester,
                                    pendingDrawerEntryRequest = drawerEntryRequest,
                                    onDrawerEntryConsumed = consumeDrawerEntryRequest,
                                    onDefaultFocusReady = {
                                        onContentDefaultFocusReady(LeftNaviItem.UGC)
                                    },
                                    active = activeContent
                                )

                                LeftNaviItem.PGC -> PgcContent(
                                    navFocusRequester = pgcFocusRequester,
                                    drawerFocusRequester = if (leftNaviExpanded) pgcDrawerFocusRequester else userFocusRequester,
                                    pendingDrawerEntryRequest = drawerEntryRequest,
                                    onDrawerEntryConsumed = consumeDrawerEntryRequest,
                                    onDefaultFocusReady = {
                                        onContentDefaultFocusReady(LeftNaviItem.PGC)
                                    },
                                    active = activeContent
                                )

                                else -> MainContentShell(item)
                            }
                        }
                    }
                }
            }
        }

        // ========== 抽屉区域 ==========
        val isDarkTheme = rememberIsDarkFromPrefs()
        LeftNaviContent(
            modifier = Modifier
                .fillMaxHeight()
                .zIndex(2f)
                .onSizeChanged { drawerWidthPx = it.width.toFloat() } // 测量确切宽度给主页做撞击计算
                .graphicsLayer {
                    // 仅在 Draw 阶段读取 drawerSlideProgress
                    translationX = -size.width * (1f - drawerSlideProgress)

                    // 当抽屉完全在屏幕外时，跳过绘制以节省性能
                    alpha = if (drawerSlideProgress == 0f) 0f else 1f
                }
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
            selectedItem = activeDrawerItem,
            homeFocusRequester = homeDrawerFocusRequester,
            followFocusRequester = followDrawerFocusRequester,
            ugcFocusRequester = ugcDrawerFocusRequester,
            pgcFocusRequester = pgcDrawerFocusRequester,
            onLeftNaviItemChanged = { activateDrawerItem(it) },
            onLeftNaviItemFocused = { focusDrawerItem(it) },
            onOpenSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
            onFocusToContent = onFocusToContent,
            userFocusRequester = userFocusRequester,
            settingsFocusRequester = settingsFocusRequester,
            userContent = {
                LeftNaviUserButton(
                    expanded = true,
                    colorAnimationEnabled = userButtonColorAnimationEnabled,
                    isLogin = userViewModel.isLogin,
                    avatar = userViewModel.face,
                    username = userViewModel.username,
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
                            isConfirmKey && keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.nativeKeyEvent.isLongPress -> {
                                if (!userLongPressTriggered) {
                                    userLongPressTriggered = true
                                    openUserPage()
                                }
                                true
                            }

                            isConfirmKey && keyEvent.type == KeyEventType.KeyUp -> {
                                if (!userLongPressTriggered) {
                                    collapseLeftNavi()
                                }
                                userLongPressTriggered = false
                                true
                            }

                            keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyDown -> {
                                currentDrawerFocusRequester().requestFocus(scope)
                                true
                            }

                            keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown -> {
                                settingsFocusRequester.requestFocus(scope)
                                true
                            }

                            keyEvent.key == Key.DirectionLeft || keyEvent.key == Key.DirectionRight -> true

                            else -> false
                        }
                    },
                    onClick = { collapseLeftNavi() }
                )
            }
        )

        // ========== 悬浮的初始小头像 ==========
        if (showCollapsedUserButton) {
            LeftNaviUserButton(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(3f),
                expanded = false,
                colorAnimationEnabled = userButtonColorAnimationEnabled,
                isLogin = userViewModel.isLogin,
                avatar = userViewModel.face,
                username = userViewModel.username,
                focusRequester = userFocusRequester,
                isFocused = userIsFocused,
                onFocusChanged = {
                    userIsFocused = it
                    if (!it) userLongPressTriggered = false
                },
                onPreviewKeyEvent = onUserButtonPreviewKeyEvent,
                onClick = { expandLeftNavi() }
            )
        }

        if (showFirstLaunchMainDialog) {
            val closeFirstLaunchMainDialog = {
                showFirstLaunchMainDialog = false
                Prefs.showFirstLaunchMainDialog = false
            }

            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = LocalDensity.current.density * 1.5f,
                    fontScale = LocalDensity.current.fontScale * 1.5f
                )
            ) {
                AlertDialog(
                    onDismissRequest = closeFirstLaunchMainDialog,
                    title = {
                        Text(text = "温馨提示")
                    },
                    text = {
                        Text(
                            text = """
                            1.点击左上角头像按钮有惊喜（如果你已经登录了的话）；
                            2.常来设置这里看看；
                            3.某些地方长按会有不一样的东西出现；
                            4.■■■■亡■■■■■■■■否■■■■要■■■■■。
                        """.trimIndent()
                        )
                    },
                    confirmButton = {}
                )
            }
        }
    }
}

@Composable
fun MainContentShell(item: LeftNaviItem) {
    val title = when (item) {
        LeftNaviItem.Home -> "首页"
        LeftNaviItem.Follow -> "我的关注"
        LeftNaviItem.UGC -> "分区"
        LeftNaviItem.PGC -> "番剧影视"
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineLarge
        )
    }
}
