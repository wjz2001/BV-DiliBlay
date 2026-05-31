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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
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
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntrySurface
import dev.aaa1115910.bv.wjzfocus.WjzFocusHost
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusRestoreStrategy
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusSourceToken
import dev.aaa1115910.bv.wjzfocus.WjzFocusTransitionGuard
import dev.aaa1115910.bv.wjzfocus.defaultEntry
import dev.aaa1115910.bv.wjzfocus.entry
import dev.aaa1115910.bv.wjzfocus.left
import dev.aaa1115910.bv.wjzfocus.up
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.component.rememberBlackoutSwitchTransitionState
import dev.aaa1115910.bv.wjzfocus.rememberWjzFocusCoordinator
import dev.aaa1115910.bv.repository.UserRepository
import dev.aaa1115910.bv.screen.main.HomeContent
import dev.aaa1115910.bv.screen.main.LeftNaviItem
import dev.aaa1115910.bv.screen.main.LiveContent
import dev.aaa1115910.bv.screen.main.MainDrawerBlock
import dev.aaa1115910.bv.screen.main.MainDrawerContentEntryTarget
import dev.aaa1115910.bv.screen.main.MainDrawerEntryRequest
import dev.aaa1115910.bv.screen.main.MainDrawerEntryTarget
import dev.aaa1115910.bv.screen.main.MainTopNavBlock
import dev.aaa1115910.bv.screen.main.MainTopNavEntryRequest
import dev.aaa1115910.bv.screen.main.MainTopNavEntryTarget
import dev.aaa1115910.bv.screen.main.MainTopNavContentEntryTarget
import dev.aaa1115910.bv.screen.main.PgcContent
import dev.aaa1115910.bv.screen.main.UgcContent
import dev.aaa1115910.bv.screen.main.leftNaviItemFocusNodeId
import dev.aaa1115910.bv.screen.main.common.MainContentEntryId
import dev.aaa1115910.bv.screen.main.common.MainContentEntryRequest
import dev.aaa1115910.bv.screen.main.common.MainContentEntryState
import dev.aaa1115910.bv.screen.main.common.MainContentEntryTarget
import dev.aaa1115910.bv.screen.main.common.MainContentEntryTransition
import dev.aaa1115910.bv.screen.main.common.MainContentFocusComponentId
import dev.aaa1115910.bv.screen.main.common.MainContentLeftEntryId
import dev.aaa1115910.bv.screen.main.common.MainContentTopEntryId
import dev.aaa1115910.bv.screen.main.common.MainDrawerFocusComponentId
import dev.aaa1115910.bv.screen.main.common.MainDrawerRightEntryId
import dev.aaa1115910.bv.screen.main.common.MainTopNavDefaultEntryId
import dev.aaa1115910.bv.screen.main.common.MainTopNavFocusComponentId
import dev.aaa1115910.bv.screen.main.runtime.runtimeContainerInputEnabled
import dev.aaa1115910.bv.screen.main.toMainContentEntryTarget
import dev.aaa1115910.bv.component.TvAlertDialog
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.ui.theme.ThemeMode
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.UserViewModel
import dev.aaa1115910.bv.viewmodel.main.HomeContentViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private val MainContentEntryNodeId = WjzFocusNodeId("main/content/entry")
private const val MainFirstLaunchDialogComponentId = "mainFirstLaunchDialog"
private val MainFirstLaunchDialogScopeId = WjzFocusScopeId("main/first-launch-dialog")
private val MainFirstLaunchDialogContainerNodeId = WjzFocusNodeId("main/first-launch-dialog/container")
private val MainFirstLaunchDialogTextNodeId = WjzFocusNodeId("main/first-launch-dialog/text")
private val MainDrawerScopeId = WjzFocusScopeId("drawer")
private val MainFocusScopeId = WjzFocusScopeId("main")
private val MainTopNavScopeId = WjzFocusScopeId("topNav")
private val MainTopNavDefaultNodeId = WjzFocusNodeId("${MainTopNavFocusComponentId}/default")

private data class PendingContentFocus(
    val item: LeftNaviItem,
    val transition: MainContentEntryTransition
) {
    val request: MainContentEntryRequest
        get() = MainContentEntryRequest(
            id = transition.requestId,
            target = transition.target
        )
}

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
    allowFirstLaunchMainDialog: Boolean = true,
    userViewModel: UserViewModel = koinViewModel(),
    userRepository: UserRepository = koinInject()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val logger = KotlinLogging.logger("MainScreen")
    val focusCoordinator = rememberWjzFocusCoordinator()
    var lastPressBack: Long by remember { mutableLongStateOf(0L) }

    val initialDrawerItem = LeftNaviItem.Home
    var focusedDrawerItem by remember { mutableStateOf(initialDrawerItem) }
    var activeDrawerItem by remember { mutableStateOf(initialDrawerItem) }
    var lastActiveDrawerItem by remember { mutableStateOf<LeftNaviItem?>(null) }

    var drawerEntryRequestSerial by remember { mutableLongStateOf(0L) }
    var pendingDrawerEntryRequest by remember { mutableStateOf<MainDrawerEntryRequest?>(null) }
    var topNavEntryRequestSerial by remember { mutableLongStateOf(0L) }
    var pendingTopNavEntryRequest by remember { mutableStateOf<MainTopNavEntryRequest?>(null) }
    var contentEntryRequestSerial by remember { mutableLongStateOf(0L) }
    var pendingContentFocus by remember { mutableStateOf<PendingContentFocus?>(null) }
    val pendingContentItem = pendingContentFocus
        ?.takeIf {
            it.transition.state == MainContentEntryState.Pending ||
                    it.transition.state == MainContentEntryState.Ready
        }
        ?.item
    val currentContentItem = pendingContentItem ?: activeDrawerItem

    // 状态控制
    var leftNaviExpanded by remember { mutableStateOf(false) }
    var drawerSourceToken by remember { mutableStateOf<WjzFocusSourceToken?>(null) }
    var showFirstLaunchMainDialog by remember { mutableStateOf(Prefs.showFirstLaunchMainDialog) }
    var userIsFocused by remember { mutableStateOf(false) }
    var userButtonColorAnimationEnabled by remember { mutableStateOf(true) }
    var leftNaviExpandedObserved by remember { mutableStateOf(false) }
    var drawerSlideRunning by remember { mutableStateOf(false) }
    var mainContentPushRunning by remember { mutableStateOf(false) }
    val contentSwitchTransitionState = rememberBlackoutSwitchTransitionState()

    // 记录抽屉动态宽度（精确用于撞击计算）
    var drawerWidthPx by remember { mutableFloatStateOf(0f) }

    // 抽屉动画：迅捷、干脆
    LaunchedEffect(leftNaviExpanded) {
        if (leftNaviExpandedObserved) {
            drawerSlideRunning = true
            mainContentPushRunning = true
        } else {
            leftNaviExpandedObserved = true
        }
    }

    val drawerSlideProgress by animateFloatAsState(
        targetValue = if (leftNaviExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "drawer_slide",
        finishedListener = { drawerSlideRunning = false }
    )

    // 主内容区被撞飞动画：带有Q弹缓冲效果（阻尼较低，刚度较低，会被抽屉短暂覆盖后弹开）
    val mainContentPushProgress by animateFloatAsState(
        targetValue = if (leftNaviExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.25f, // 更弹
            stiffness = 30f
        ),
        label = "main_content_push",
        finishedListener = { mainContentPushRunning = false }
    )
    val focusTransitionLocked = drawerSlideRunning ||
            mainContentPushRunning ||
            contentSwitchTransitionState.running

    fun newDrawerEntryRequest(target: MainDrawerEntryTarget): MainDrawerEntryRequest {
        drawerEntryRequestSerial += 1
        return MainDrawerEntryRequest(
            id = drawerEntryRequestSerial,
            target = target
        )
    }

    fun newContentEntryRequest(target: MainContentEntryTarget): MainContentEntryRequest {
        contentEntryRequestSerial += 1
        return MainContentEntryRequest(
            id = contentEntryRequestSerial,
            target = target
        )
    }

    fun newTopNavEntryRequest(target: MainTopNavEntryTarget): MainTopNavEntryRequest {
        topNavEntryRequestSerial += 1
        return MainTopNavEntryRequest(
            id = topNavEntryRequestSerial,
            target = target
        )
    }

    fun newPendingContentFocus(
        item: LeftNaviItem,
        target: MainContentEntryTarget
    ): PendingContentFocus {
        val request = newContentEntryRequest(target)
        return PendingContentFocus(
            item = item,
            transition = MainContentEntryTransition(
                requestId = request.id,
                target = request.target,
                state = MainContentEntryState.Pending
            )
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

    fun isContentItem(item: LeftNaviItem): Boolean {
        return item == LeftNaviItem.Home ||
                item == LeftNaviItem.Live ||
                item == LeftNaviItem.UGC ||
                item == LeftNaviItem.PGC
    }

    fun collapseLeftNavi(
        requestFallback: Boolean = true,
        restoreSource: Boolean = true
    ) {
        if (!leftNaviExpanded) return
        leftNaviExpanded = false
        userButtonColorAnimationEnabled = true
        val restored = if (restoreSource) {
            focusCoordinator.restoreSourceLayer(
                expectedActiveLayer = WjzFocusLayer.Drawer,
                token = drawerSourceToken
            )
        } else {
            false
        }
        drawerSourceToken = null
        if (!restored && requestFallback) {
            focusCoordinator.switchLayer(WjzFocusLayer.Content)
            focusCoordinator.requestEntryFocus(MainContentLeftEntryId)
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

    fun requestContentEntry(
        item: LeftNaviItem,
        target: MainDrawerContentEntryTarget
    ): Boolean {
        if (!isContentItem(item)) return false
        val entryTarget =
            when (target) {
                MainDrawerContentEntryTarget.LeftEntry -> MainContentEntryTarget.LeftEntry
                MainDrawerContentEntryTarget.RightEntry -> MainContentEntryTarget.LeftEntry
            }
        pendingContentFocus = newPendingContentFocus(item, entryTarget)
        if (leftNaviExpanded) {
            collapseLeftNavi(requestFallback = false, restoreSource = true)
        }
        focusCoordinator.switchLayer(WjzFocusLayer.Content)
        return true
    }

    fun requestTopNavContentEntry(target: MainTopNavContentEntryTarget): Boolean {
        if (!isContentItem(activeDrawerItem)) return false
        pendingContentFocus = newPendingContentFocus(
            item = activeDrawerItem,
            target = target.toMainContentEntryTarget()
        )
        focusCoordinator.switchLayer(WjzFocusLayer.Content)
        return true
    }

    fun contentEntryRequestFor(item: LeftNaviItem, active: Boolean): MainContentEntryRequest? {
        if (!active) return null
        return pendingContentFocus
            ?.takeIf { pending ->
                pending.item == item &&
                        (
                                pending.transition.state == MainContentEntryState.Pending ||
                                        pending.transition.state == MainContentEntryState.Ready
                                )
            }
            ?.request
    }

    fun markContentEntryRequestReady(item: LeftNaviItem, requestId: Long) {
        val pending = pendingContentFocus ?: return
        if (
            pending.item == item &&
            pending.transition.requestId == requestId &&
            pending.transition.state == MainContentEntryState.Pending
        ) {
            pendingContentFocus = pending.copy(
                transition = pending.transition.copy(state = MainContentEntryState.Ready)
            )
        }
    }

    fun consumeContentEntryRequest(item: LeftNaviItem, requestId: Long) {
        val pending = pendingContentFocus ?: return
        if (
            pending.item == item &&
            pending.transition.requestId == requestId &&
            pending.transition.state == MainContentEntryState.Ready
        ) {
            pendingContentFocus = pending.copy(
                transition = pending.transition.copy(state = MainContentEntryState.Consumed)
            )
            activateDrawerItem(item)
            pendingContentFocus = null
        }
    }

    fun rejectContentEntryRequest(item: LeftNaviItem, requestId: Long) {
        val pending = pendingContentFocus ?: return
        if (pending.item == item && pending.transition.requestId == requestId) {
            pendingContentFocus = pending.copy(
                transition = pending.transition.copy(state = MainContentEntryState.Rejected)
            )
            pendingContentFocus = null
        }
    }

    fun expandLeftNavi() {
        if (leftNaviExpanded) return
        userButtonColorAnimationEnabled = false
        drawerSourceToken = focusCoordinator.activateLayer(
            layer = WjzFocusLayer.Drawer,
            recordSource = true
        )
        leftNaviExpanded = true
        pendingDrawerEntryRequest = newDrawerEntryRequest(MainDrawerEntryTarget.CurrentItem)
    }

    fun openUserPage() {
        if (userViewModel.isLogin) {
            context.startActivity(Intent(context, UserSwitchActivity::class.java))
        } else {
            context.startActivity(Intent(context, LoginActivity::class.java))
        }
    }

    BackHandler {
        if (leftNaviExpanded) {
            collapseLeftNavi()
        } else if (focusCoordinator.restoreSourceLayer()) {
            return@BackHandler
        } else {
            handleBack()
        }
    }

    WjzFocusHost(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        coordinator = focusCoordinator,
        layer = WjzFocusLayer.Content,
        scopeId = MainFocusScopeId
    ) {
        WjzFocusTransitionGuard(locked = focusTransitionLocked)

        Box(modifier = Modifier.fillMaxSize()) {
            val topBarLeadingContent: @Composable () -> Unit = {
                WjzFocusEntrySurface(
                    componentId = MainTopNavFocusComponentId,
                    default = {
                        defaultEntry(
                            nodeId = MainTopNavDefaultNodeId,
                            layer = WjzFocusLayer.TopNav,
                            scopeId = MainTopNavScopeId
                        )
                    },
                    entries = {}
                )
                WjzFocusHost(
                    modifier = Modifier
                        .zIndex(1f)
                        .graphicsLayer {
                            translationX = -drawerWidthPx * mainContentPushProgress -
                                    size.width * drawerSlideProgress
                            alpha = 1f - drawerSlideProgress
                        },
                    coordinator = focusCoordinator,
                    layer = WjzFocusLayer.TopNav,
                    scopeId = MainTopNavScopeId
                ) {
                    MainTopNavBlock(
                        modifier = Modifier,
                        userColorAnimationEnabled = userButtonColorAnimationEnabled,
                        userIsLogin = userViewModel.isLogin,
                        userAvatar = userViewModel.face,
                        username = userViewModel.username,
                        entryRequest = pendingTopNavEntryRequest,
                        onEntryRequestConsumed = { requestId ->
                            if (pendingTopNavEntryRequest?.id == requestId) {
                                pendingTopNavEntryRequest = null
                            }
                        },
                        focusEnabled = !leftNaviExpanded,
                        userIsFocused = userIsFocused,
                        onUserFocusChanged = { userIsFocused = it },
                        onExpandDrawer = { expandLeftNavi() },
                        onOpenUser = { openUserPage() },
                        onContentEntryRequested = ::requestTopNavContentEntry
                    )
                }
            }

            WjzFocusEntrySurface(
                componentId = MainContentFocusComponentId,
                default = {
                    defaultEntry(
                        nodeId = MainContentEntryNodeId,
                        layer = WjzFocusLayer.Content,
                        scopeId = MainFocusScopeId
                    )
                },
                entries = {
                    entry(MainContentEntryId.localEntryValue) {
                        defaultEntry(
                            nodeId = MainContentEntryNodeId,
                            layer = WjzFocusLayer.Content,
                            scopeId = MainFocusScopeId
                        )
                    }
                    entry(MainContentTopEntryId.localEntryValue) {
                        defaultEntry(
                            nodeId = MainContentEntryNodeId,
                            layer = WjzFocusLayer.Content,
                            scopeId = MainFocusScopeId
                        )
                    }
                    entry(MainContentLeftEntryId.localEntryValue) {
                        defaultEntry(
                            nodeId = MainContentEntryNodeId,
                            layer = WjzFocusLayer.Content,
                            scopeId = MainFocusScopeId
                        )
                    }
                }
            )
            Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(0f)
                        .wjzFocusExits(
                            id = MainContentEntryNodeId.value.removePrefix("${MainFocusScopeId.value}/"),
                            layer = WjzFocusLayer.Content,
                            strategy = WjzFocusRestoreStrategy.Container,
                            enabled = !leftNaviExpanded,
                            exits = {
                                up move MainTopNavDefaultEntryId
                                left move MainDrawerRightEntryId
                            }
                        )
                        .graphicsLayer {
                            translationX = drawerWidthPx * mainContentPushProgress
                        }
                ) {
                    BlackoutSwitch(
                        targetState = currentContentItem,
                        fadeInMillis = fade,
                        fadeOutMillis = fade,
                        transitionState = contentSwitchTransitionState
                    ) { currentItem ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            val mountedDrawerItems = listOfNotNull(
                                lastActiveDrawerItem,
                                activeDrawerItem,
                                pendingContentItem,
                                currentItem
                            ).distinct()
                            mountedDrawerItems.forEach { item ->
                                val activeContent = item == currentItem

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .zIndex(if (activeContent) 1f else 0f)
                                        .graphicsLayer { alpha = if (activeContent) 1f else 0f }
                                        .runtimeContainerInputEnabled(activeContent)
                                ) {
                                    val contentEntryRequest = contentEntryRequestFor(item, activeContent)
                                    when (item) {
                                        LeftNaviItem.Live -> LiveContent(
                                            topBarLeadingContent = topBarLeadingContent,
                                            entryRequest = contentEntryRequest,
                                            onEntryRequestReady = { requestId ->
                                                markContentEntryRequestReady(item, requestId)
                                            },
                                            onEntryRequestConsumed = { requestId ->
                                                consumeContentEntryRequest(item, requestId)
                                            },
                                            onEntryRequestRejected = { requestId ->
                                                rejectContentEntryRequest(item, requestId)
                                            },
                                            active = activeContent
                                        )

                                        LeftNaviItem.Home -> {
                                            val homeContentViewModel: HomeContentViewModel =
                                                koinViewModel<HomeContentViewModel>()
                                            HomeContent(
                                                topBarLeadingContent = topBarLeadingContent,
                                                entryRequest = contentEntryRequest,
                                                onEntryRequestReady = { requestId ->
                                                    markContentEntryRequestReady(item, requestId)
                                                },
                                                onEntryRequestConsumed = { requestId ->
                                                    consumeContentEntryRequest(item, requestId)
                                                },
                                                onEntryRequestRejected = { requestId ->
                                                    rejectContentEntryRequest(item, requestId)
                                                },
                                                homeContentViewModel = homeContentViewModel,
                                                userViewModel = userViewModel,
                                                active = activeContent
                                            )
                                        }

                                        LeftNaviItem.UGC -> UgcContent(
                                            topBarLeadingContent = topBarLeadingContent,
                                            entryRequest = contentEntryRequest,
                                            onEntryRequestReady = { requestId ->
                                                markContentEntryRequestReady(item, requestId)
                                            },
                                            onEntryRequestConsumed = { requestId ->
                                                consumeContentEntryRequest(item, requestId)
                                            },
                                            onEntryRequestRejected = { requestId ->
                                                rejectContentEntryRequest(item, requestId)
                                            },
                                            active = activeContent
                                        )

                                        LeftNaviItem.PGC -> PgcContent(
                                            topBarLeadingContent = topBarLeadingContent,
                                            entryRequest = contentEntryRequest,
                                            onEntryRequestReady = { requestId ->
                                                markContentEntryRequestReady(item, requestId)
                                            },
                                            onEntryRequestConsumed = { requestId ->
                                                consumeContentEntryRequest(item, requestId)
                                            },
                                            onEntryRequestRejected = { requestId ->
                                                rejectContentEntryRequest(item, requestId)
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

            val isDarkTheme = rememberIsDarkFromPrefs()
            WjzFocusEntrySurface(
                componentId = MainDrawerFocusComponentId,
                default = {
                    defaultEntry(
                        nodeId = leftNaviItemFocusNodeId(activeDrawerItem),
                        layer = WjzFocusLayer.Drawer,
                        scopeId = MainDrawerScopeId
                    )
                },
                entries = {
                    entry(MainDrawerRightEntryId.localEntryValue) {
                        defaultEntry(
                            nodeId = leftNaviItemFocusNodeId(activeDrawerItem),
                            layer = WjzFocusLayer.Drawer,
                            scopeId = MainDrawerScopeId
                        )
                    }
                }
            )
            WjzFocusHost(
                    modifier = Modifier
                        .fillMaxHeight()
                        .zIndex(2f)
                        .onSizeChanged { drawerWidthPx = it.width.toFloat() }
                        .graphicsLayer {
                            translationX = -size.width * (1f - drawerSlideProgress)
                            alpha = if (drawerSlideProgress == 0f) 0f else 1f
                        }
                        .shadow(elevation = 16.dp)
                        .background(C.background)
                        .drawWithContent {
                            drawContent()

                            val shadowWidth = 36.dp.toPx()
                            val shadowAlpha = if (isDarkTheme) 0.55f else 0.06f
                            val outlineColor = if (isDarkTheme) {
                                Color.White.copy(alpha = 0.12f)
                            } else {
                                Color.Black.copy(alpha = 0.08f)
                            }

                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Black.copy(alpha = shadowAlpha), Color.Transparent),
                                    startX = size.width,
                                    endX = size.width + shadowWidth
                                ),
                                topLeft = Offset(size.width, 0f),
                                size = Size(shadowWidth, size.height)
                            )

                            drawLine(
                                color = outlineColor,
                                start = Offset(size.width, 0f),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        },
                    coordinator = focusCoordinator,
                    layer = WjzFocusLayer.Drawer,
                    scopeId = MainDrawerScopeId
            ) {
                MainDrawerBlock(
                        modifier = Modifier,
                        selectedItem = activeDrawerItem,
                        currentItem = focusedDrawerItem,
                        entryRequest = pendingDrawerEntryRequest,
                        onEntryRequestConsumed = { requestId ->
                            if (pendingDrawerEntryRequest?.id == requestId) {
                                pendingDrawerEntryRequest = null
                            }
                        },
                        onItemActivated = { activateDrawerItem(it) },
                        onItemFocused = { focusDrawerItem(it) },
                        onOpenSettings = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                        onContentEntryRequested = ::requestContentEntry,
                        userColorAnimationEnabled = userButtonColorAnimationEnabled,
                        userIsLogin = userViewModel.isLogin,
                        userAvatar = userViewModel.face,
                        username = userViewModel.username,
                        userIsFocused = userIsFocused,
                        onUserFocusChanged = { userIsFocused = it },
                        onCollapse = { collapseLeftNavi() },
                        onOpenUser = { openUserPage() }
                )
            }
        }

        if (allowFirstLaunchMainDialog && showFirstLaunchMainDialog) {
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
                TvAlertDialog(
                    onDismissRequest = closeFirstLaunchMainDialog,
                    sourceScopeId = MainFocusScopeId,
                    dialogScopeId = MainFirstLaunchDialogScopeId,
                    containerNodeId = MainFirstLaunchDialogContainerNodeId,
                    title = {
                        Text(text = "温馨提示")
                    },
                    text = {
                        WjzFocusEntrySurface(
                            componentId = MainFirstLaunchDialogComponentId,
                            default = {
                                defaultEntry(
                                    nodeId = MainFirstLaunchDialogTextNodeId,
                                    layer = WjzFocusLayer.Dialog,
                                    scopeId = MainFirstLaunchDialogScopeId
                                )
                            }
                        )
                        Text(
                            modifier = Modifier.wjzFocusExits(
                                id = MainFirstLaunchDialogTextNodeId.value
                                    .removePrefix("${MainFirstLaunchDialogScopeId.value}/"),
                                layer = WjzFocusLayer.Dialog
                            ),
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
        LeftNaviItem.Live -> "直播"
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
