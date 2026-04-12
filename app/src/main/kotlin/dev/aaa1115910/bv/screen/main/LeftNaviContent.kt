package dev.aaa1115910.bv.screen.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import coil.compose.AsyncImage
import dev.aaa1115910.bv.screen.main.common.MainContentFocusTarget
import dev.aaa1115910.bv.ui.theme.AppWhite
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.isDpadDown
import dev.aaa1115910.bv.util.isDpadUp
import dev.aaa1115910.bv.util.isKeyDown
import dev.aaa1115910.bv.util.isKeyUp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LeftNaviContent(
    modifier: Modifier = Modifier,
    isLogin: Boolean = false,
    avatar: String = "",
    selectedItem: LeftNaviItem,
    searchFocusRequester: FocusRequester,
    homeFocusRequester: FocusRequester,
    followFocusRequester: FocusRequester,
    ugcFocusRequester: FocusRequester,
    pgcFocusRequester: FocusRequester,
    onLeftNaviItemChanged: (LeftNaviItem) -> Unit,
    onLeftNaviItemPreload: (LeftNaviItem) -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenUserSwitch: () -> Unit,
    onFocusToContent: (MainContentFocusTarget) -> Unit,
    onLogin: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val userFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }

    var userArmedEntryTarget by remember { mutableStateOf<MainContentFocusTarget?>(null) }
    var settingsArmedEntryTarget by remember { mutableStateOf<MainContentFocusTarget?>(null) }

    val contentItems = listOf(
        LeftNaviItem.Search,
        LeftNaviItem.Home,
        LeftNaviItem.Follow,
        LeftNaviItem.UGC,
        LeftNaviItem.PGC
    )

    val railButtonSize = 44.dp
    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .width(railButtonSize),
        containerColor = Color.Transparent,
    ) {
        var userIsFocused by remember { mutableStateOf(false) }

        // 顶部的用户图标颜色逻辑
        val userIconColor by animateColorAsState(
            targetValue = if (userIsFocused) AppWhite else MaterialTheme.colorScheme.onSurface,
            label = "userIconColor"
        )

        val userBorderColor by animateColorAsState(
            targetValue = if (userIsFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
            label = "userBorderColor"
        )

        NavigationRailItem(
            modifier = Modifier
                .focusRequester(userFocusRequester)
                .onFocusChanged {
                    userIsFocused = it.hasFocus
                    if (!it.hasFocus) {
                        userArmedEntryTarget = null
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    when {
                        keyEvent.isDpadUp() -> {
                            if (keyEvent.isKeyDown()) {
                                settingsFocusRequester.requestFocus()
                                return@onPreviewKeyEvent true
                            }
                            true
                        }

                        keyEvent.key == Key.DirectionRight -> {
                            when {
                                keyEvent.isKeyDown() -> {
                                    userArmedEntryTarget = MainContentFocusTarget.LeftEntry
                                    true
                                }

                                keyEvent.isKeyUp() -> {
                                    val armed = userArmedEntryTarget
                                    userArmedEntryTarget = null
                                    if (armed == MainContentFocusTarget.LeftEntry) {
                                        onFocusToContent(MainContentFocusTarget.LeftEntry)
                                    }
                                    true
                                }

                                else -> false
                            }
                        }

                        keyEvent.key == Key.DirectionLeft -> {
                            when {
                                keyEvent.isKeyDown() -> {
                                    userArmedEntryTarget = MainContentFocusTarget.RightEntry
                                    true
                                }

                                keyEvent.isKeyUp() -> {
                                    val armed = userArmedEntryTarget
                                    userArmedEntryTarget = null
                                    if (armed == MainContentFocusTarget.RightEntry) {
                                        onFocusToContent(MainContentFocusTarget.RightEntry)
                                    }
                                    true
                                }

                                else -> false
                            }
                        }

                        else -> false
                    }
                },
            onClick = {
                if (isLogin) onOpenUserSwitch() else onLogin()
            },
            selected = userIsFocused,
            // 统一下发用户图标的颜色和背景颜色
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = userIconColor,
                unselectedIconColor = userIconColor
            ),
            icon = {
                if (isLogin) {
                    Surface(
                        modifier = Modifier
                            .size(railButtonSize)
                            .border(width = 2.dp, color = userBorderColor, shape = CircleShape)
                            .padding(2.dp),
                        colors = SurfaceDefaults.colors(
                            containerColor = Color.Transparent
                        )
                    ) {
                        AsyncImage(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            model = avatar,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds
                        )
                    }
                } else {
                    Icon(
                        imageVector = LeftNaviItem.User.displayIcon,
                        contentDescription = null
                    )
                }
            }
        )

        // ====== 中间 contentItems：移动高亮块 + 固定选中块 + 左侧指示条（按你的规则） ======

        /**
         * 仅针对中间 contentItems：
         * - selectedItem 如果不在 contentItems（例如你把 selectedItem 设成 User/Settings），就视为“中间没有选中项”
         *   => 左侧指示条跟随 focus 跑
         * - 如果 selectedItem 在 contentItems
         *   => 左侧指示条固定在 selectedItem
         *   => 当 selectedItem != focusedItem 时，额外画一个与移动高亮块同尺寸的“选中块”（颜色不同）
         */
        val selectedContentItem: LeftNaviItem? = selectedItem.takeIf { it in contentItems }
        var focusedContentItem by remember { mutableStateOf<LeftNaviItem?>(null) }

        val barWidth = 4.dp

// 用 root 坐标差值，把 item 的 positionInRoot 转成这个 Box 的本地坐标，方便 draw
        var contentBoxOffsetInRoot by remember { mutableStateOf(Offset.Zero) }

        // 保存每个 item 的 top/height（px，Box 本地坐标系）
        data class ItemBoundsPx(val top: Float, val height: Float)
        val boundsMap = remember { mutableStateMapOf<LeftNaviItem, ItemBoundsPx>() }

// Animatable：只在 draw 阶段读取 value，减少每帧重组
        val focusTop = remember { Animatable(0f) }
        val focusHeight = remember { Animatable(0f) }

        val selectedTop = remember { Animatable(0f) }
        val selectedHeight = remember { Animatable(0f) }

        val barTop = remember { Animatable(0f) }
        val barHeight = remember { Animatable(0f) }

        val animSpec = spring<Float>(stiffness = 700f, dampingRatio = 0.85f)

        val density = androidx.compose.ui.platform.LocalDensity.current
        val barWidthPx = remember(barWidth, density) { with(density) { barWidth.toPx() } }

        // focus 高亮块：跟随 focusedContentItem
        LaunchedEffect(focusedContentItem, boundsMap[focusedContentItem]) {
            val item = focusedContentItem ?: return@LaunchedEffect
            val b = boundsMap[item] ?: return@LaunchedEffect
            launch { focusTop.animateTo(b.top, animSpec) }
            launch { focusHeight.animateTo(b.height, animSpec) }
        }

        // selected 固定块：跟随 selectedContentItem（是否绘制由 draw 时判断 selected != focused）
        LaunchedEffect(selectedContentItem, boundsMap[selectedContentItem]) {
            val item = selectedContentItem ?: return@LaunchedEffect
            val b = boundsMap[item] ?: return@LaunchedEffect
            launch { selectedTop.animateTo(b.top, animSpec) }
            launch { selectedHeight.animateTo(b.height, animSpec) }
        }

        // 左侧指示条：有 selectedContentItem 就固定在它；否则跟随 focus
        LaunchedEffect(
            selectedContentItem,
            focusedContentItem,
            boundsMap[selectedContentItem],
            boundsMap[focusedContentItem]
        ) {
            val anchor = selectedContentItem ?: focusedContentItem ?: return@LaunchedEffect
            val b = boundsMap[anchor] ?: return@LaunchedEffect
            launch { barTop.animateTo(b.top, animSpec) }
            launch { barHeight.animateTo(b.height, animSpec) }
        }

        // 左侧指示条颜色
        val railIndicatorBarColor = MaterialTheme.colorScheme.primary
        // 焦点移动高亮块颜色
        val railFocusHighlightColor = MaterialTheme.colorScheme.primary
        // 选中固定块颜色
        val railSelectedBlockColor = MaterialTheme.colorScheme.secondary

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onGloballyPositioned { coords ->
                    contentBoxOffsetInRoot = coords.positionInRoot()
                }
                .drawWithCache {
                    onDrawWithContent {
                        val hasFocus = focusedContentItem != null
                        val hasSelected = selectedContentItem != null

                        // 选中固定块（只有 selected 存在且 selected != focused 才画）
                        if (hasSelected && selectedContentItem != focusedContentItem) {
                            if (selectedHeight.value > 0.5f) {
                                drawRect(
                                    color = railSelectedBlockColor,
                                    topLeft = Offset(barWidthPx, selectedTop.value),
                                    size = Size(size.width - barWidthPx, selectedHeight.value)
                                )
                            }
                        }

                        // 焦点移动高亮块（永远跟随 focus）
                        if (hasFocus) {
                            if (focusHeight.value > 0.5f) {
                                drawRect(
                                    color = railFocusHighlightColor,
                                    topLeft = Offset(barWidthPx, focusTop.value),
                                    size = Size(size.width - barWidthPx, focusHeight.value)
                                )
                            }
                        }

                        // 内容（NavigationRailItem / Icon）
                        drawContent()

                        // 左侧指示条（有选中固定选中；无选中跟随 focus）
                        val showBar = hasSelected || hasFocus
                        if (showBar && barHeight.value > 0.5f) {
                            drawRect(
                                color = railIndicatorBarColor,
                                topLeft = Offset(0f, barTop.value),
                                size = Size(barWidthPx, barHeight.value)
                            )
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                contentItems.forEach { item ->
                    val itemFocusRequester = when (item) {
                        LeftNaviItem.Search -> searchFocusRequester
                        LeftNaviItem.Home -> homeFocusRequester
                        LeftNaviItem.Follow -> followFocusRequester
                        LeftNaviItem.UGC -> ugcFocusRequester
                        LeftNaviItem.PGC -> pgcFocusRequester
                        else -> error("Unexpected item: $item")
                    }

                    var isFocused by remember(item) { mutableStateOf(false) }
                    var preloadJob by remember(item) { mutableStateOf<Job?>(null) }
                    var armedEntryTarget by remember(item) { mutableStateOf<MainContentFocusTarget?>(null) }

                    val isSelected = item == selectedContentItem
                    val isActivated = isFocused || isSelected

                    val itemIconColor by animateColorAsState(
                        targetValue = when {
                            isFocused -> MaterialTheme.colorScheme.onPrimary
                            isSelected -> MaterialTheme.colorScheme.onSecondary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        label = "itemIconColor"
                    )

                    NavigationRailItem(
                        modifier = Modifier
                            .size(railButtonSize)
                            .onGloballyPositioned { coords ->
                                val top = coords.positionInRoot().y - contentBoxOffsetInRoot.y
                                val h = coords.size.height.toFloat()
                                val old = boundsMap[item]
                                if (
                                    old == null ||
                                    abs(old.top - top) > 0.5f ||
                                    abs(old.height - h) > 0.5f
                                ) {
                                    boundsMap[item] = ItemBoundsPx(top = top, height = h)
                                }
                            }
                            .focusRequester(itemFocusRequester)
                            .onFocusChanged { focusState ->
                                isFocused = focusState.hasFocus

                                if (focusState.hasFocus) {
                                    focusedContentItem = item
                                } else if (focusedContentItem == item) {
                                    focusedContentItem = null
                                }

                                if (!focusState.hasFocus) {
                                    armedEntryTarget = null
                                }

                                preloadJob?.cancel()
                                preloadJob = if (focusState.hasFocus) {
                                    scope.launch {
                                        delay(200)
                                        if (isFocused) onLeftNaviItemPreload(item)
                                    }
                                } else {
                                    null
                                }
                            }
                            .onPreviewKeyEvent { keyEvent ->
                                when (keyEvent.key) {
                                    Key.DirectionRight -> {
                                        when {
                                            keyEvent.isKeyDown() -> {
                                                armedEntryTarget = MainContentFocusTarget.LeftEntry
                                                true
                                            }

                                            keyEvent.isKeyUp() -> {
                                                val armed = armedEntryTarget
                                                armedEntryTarget = null
                                                if (armed == MainContentFocusTarget.LeftEntry) {
                                                    onFocusToContent(MainContentFocusTarget.LeftEntry)
                                                }
                                                true
                                            }

                                            else -> false
                                        }
                                    }

                                    Key.DirectionLeft -> {
                                        when {
                                            keyEvent.isKeyDown() -> {
                                                armedEntryTarget = MainContentFocusTarget.RightEntry
                                                true
                                            }

                                            keyEvent.isKeyUp() -> {
                                                val armed = armedEntryTarget
                                                armedEntryTarget = null
                                                if (armed == MainContentFocusTarget.RightEntry) {
                                                    onFocusToContent(MainContentFocusTarget.RightEntry)
                                                }
                                                true
                                            }

                                            else -> false
                                        }
                                    }

                                    else -> false
                                }
                            },
                        onClick = { onLeftNaviItemChanged(item) },
                        selected = isActivated,
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = itemIconColor,
                            selectedTextColor = itemIconColor,
                            indicatorColor = Color.Transparent, // 关掉组件自带“胶囊”indicator
                            unselectedIconColor = itemIconColor,
                            unselectedTextColor = itemIconColor
                        ),
                        icon = {
                            Box(
                                modifier = Modifier.size(railButtonSize),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = item.displayIcon, contentDescription = null)
                            }
                        }
                    )
                }
            }
        }

        // 底部设置按钮
        var settingsIsFocused by remember { mutableStateOf(false) }
        val settingsIconColor by animateColorAsState(
            targetValue = if (settingsIsFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            label = "settingsIconColor"
        )
        val settingsIndicatorColor by animateColorAsState(
            targetValue = if (settingsIsFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
            label = "settingsIndicatorColor"
        )

        NavigationRailItem(
            modifier = Modifier
                .size(railButtonSize)
                .focusRequester(settingsFocusRequester)
                .onFocusChanged {
                    settingsIsFocused = it.hasFocus
                    if (!it.hasFocus) {
                        settingsArmedEntryTarget = null
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    when {
                        keyEvent.isDpadDown() -> {
                            if (keyEvent.isKeyDown()) {
                                userFocusRequester.requestFocus()
                                return@onPreviewKeyEvent true
                            }
                            true
                        }

                        keyEvent.key == Key.DirectionRight -> {
                            when {
                                keyEvent.isKeyDown() -> {
                                    settingsArmedEntryTarget = MainContentFocusTarget.LeftEntry
                                    true
                                }

                                keyEvent.isKeyUp() -> {
                                    val armed = settingsArmedEntryTarget
                                    settingsArmedEntryTarget = null
                                    if (armed == MainContentFocusTarget.LeftEntry) {
                                        onFocusToContent(MainContentFocusTarget.LeftEntry)
                                    }
                                    true
                                }

                                else -> false
                            }
                        }

                        keyEvent.key == Key.DirectionLeft -> {
                            when {
                                keyEvent.isKeyDown() -> {
                                    settingsArmedEntryTarget = MainContentFocusTarget.RightEntry
                                    true
                                }

                                keyEvent.isKeyUp() -> {
                                    val armed = settingsArmedEntryTarget
                                    settingsArmedEntryTarget = null
                                    if (armed == MainContentFocusTarget.RightEntry) {
                                        onFocusToContent(MainContentFocusTarget.RightEntry)
                                    }
                                    true
                                }

                                else -> false
                            }
                        }

                        else -> false
                    }
                },
            onClick = onOpenSettings,
            selected = settingsIsFocused,
            // 统一下发设置按钮的颜色配置
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = settingsIconColor,
                selectedTextColor = settingsIconColor,
                indicatorColor = settingsIndicatorColor,
                unselectedIconColor = settingsIconColor,
                unselectedTextColor = settingsIconColor
            ),
            icon = {
                Icon(
                    imageVector = LeftNaviItem.Settings.displayIcon,
                    contentDescription = null
                )
            }
        )
    }
}

enum class LeftNaviItem(
    val displayIcon: ImageVector
) {
    User(displayIcon = Icons.Default.AccountCircle),
    Search(displayIcon = Icons.Default.Search),
    Home(displayIcon = Icons.Default.Home),
    Follow(displayIcon = Icons.Default.HowToReg),
    UGC(displayIcon = Icons.Default.OndemandVideo),
    PGC(displayIcon = Icons.Default.Movie),
    Settings(displayIcon = Icons.Default.Settings),
}

fun Modifier.selectionIndicator(color: Color): Modifier {
    return this.drawBehind {
        val strokeWidth = 4.dp.toPx()
        drawRect(
            color = color,
            topLeft = Offset.Zero,
            size = Size(width = strokeWidth, height = size.height)
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun LeftNaviContentPreview() {
    BVTheme {
        LeftNaviContent(
            selectedItem = LeftNaviItem.Home,
            searchFocusRequester = remember { FocusRequester() },
            homeFocusRequester = remember { FocusRequester() },
            followFocusRequester = remember { FocusRequester() },
            ugcFocusRequester = remember { FocusRequester() },
            pgcFocusRequester = remember { FocusRequester() },
            onLeftNaviItemChanged = {},
            onLeftNaviItemPreload = {},
            onOpenSettings = {},
            onOpenUserSwitch = {},
            onFocusToContent = {},
            onLogin = {}
        )
    }
}