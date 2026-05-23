package dev.aaa1115910.bv.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.component.MainChromeDefaults
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.C
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun LeftNaviContent(
    modifier: Modifier = Modifier,
    selectedItem: LeftNaviItem,
    onItemActivated: (LeftNaviItem) -> Unit,
    onItemFocused: (LeftNaviItem) -> Unit = {},
    onOpenSettings: () -> Unit,
    drawerItemFocusModifier: @Composable (LeftNaviItem, Modifier, (Boolean) -> Unit) -> Modifier =
        { _, itemModifier, onFocusChanged ->
            itemModifier.onFocusChanged { state -> onFocusChanged(state.hasFocus) }
        },
    settingsFocusModifier: @Composable (Modifier, (Boolean) -> Unit) -> Modifier =
        { settingsModifier, onFocusChanged ->
            settingsModifier.onFocusChanged { state -> onFocusChanged(state.hasFocus) }
        },
    userContent: @Composable () -> Unit = {
        Spacer(modifier = Modifier.height(280.dp))
    }
) {
    val contentItems = listOf(
        LeftNaviItem.Home,
        LeftNaviItem.Live,
        LeftNaviItem.UGC,
        LeftNaviItem.PGC
    )

    val railButtonSize = 72.dp
    val settingsButtonSize = MainChromeDefaults.Size
    val settingsAreaHeight = settingsButtonSize + 40.dp
    val expandedRailWidth = 360.dp

    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .width(expandedRailWidth),
        containerColor = C.background,
    ) {
        userContent()

        // ====== 中间 contentItems：移动高亮块 + 固定选中块 + 左侧指示条 ======

        /**
         * 仅针对中间 contentItems：
         * - selectedItem 如果不在 contentItems，就视为“中间没有选中项”
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
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(top = 36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                contentItems.forEach { item ->
                    val itemText = when (item) {
                        LeftNaviItem.Home -> "首页"
                        LeftNaviItem.Live -> "直播"
                        LeftNaviItem.UGC -> "分区"
                        LeftNaviItem.PGC -> "番剧影视"
                        else -> ""
                    }
                    var isFocused by remember(item) { mutableStateOf(false) }

                    val isSelected = item == selectedContentItem
                    val isActivated = isFocused || isSelected

                    val itemIconColor = when {
                        isFocused -> MaterialTheme.colorScheme.onPrimary
                        isSelected -> MaterialTheme.colorScheme.onSecondary
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    NavigationRailItem(
                        modifier = Modifier
                            .width(expandedRailWidth)
                            .height(railButtonSize)
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
                            .let { itemModifier ->
                                drawerItemFocusModifier(
                                    item,
                                    itemModifier,
                                    { hasFocus ->
                                    isFocused = hasFocus

                                    if (hasFocus) {
                                        focusedContentItem = item
                                        onItemFocused(item)
                                    } else if (focusedContentItem == item) {
                                        focusedContentItem = null
                                    }
                                    }
                                )
                            },
                        onClick = { onItemActivated(item) },
                        selected = isActivated,
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = itemIconColor,
                            selectedTextColor = itemIconColor,
                            indicatorColor = Color.Transparent, // 关掉组件自带“胶囊”indicator
                            unselectedIconColor = itemIconColor,
                            unselectedTextColor = itemIconColor
                        ),
                        icon = {
                            Row(
                                modifier = Modifier
                                    .width(expandedRailWidth)
                                    .height(railButtonSize)
                                    .padding(horizontal = 32.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    modifier = Modifier.size(34.dp),
                                    imageVector = item.displayIcon,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(24.dp))
                                Text(
                                    text = itemText,
                                    color = itemIconColor,
                                    style = MaterialTheme.typography.headlineSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    )
                }
            }
        }

        // 底部设置按钮
        var settingsIsFocused by remember { mutableStateOf(false) }
        val settingsIconColor =
            if (settingsIsFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        val settingsIndicatorColor =
            if (settingsIsFocused) MaterialTheme.colorScheme.primary else Color.Transparent

        Box(
            modifier = Modifier
                .width(expandedRailWidth)
                .height(settingsAreaHeight),
            contentAlignment = Alignment.Center
        ) {
            NavigationRailItem(
                modifier = Modifier
                    .size(settingsButtonSize)
                    .let { settingsModifier ->
                        settingsFocusModifier(settingsModifier) {
                            settingsIsFocused = it
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
}

val LeftNaviUserNodeId = WjzFocusNodeId("main/drawer/user")
val LeftNaviSettingsNodeId = WjzFocusNodeId("main/drawer/settings")

fun leftNaviItemFocusNodeId(item: LeftNaviItem): WjzFocusNodeId {
    return WjzFocusNodeId("main/drawer/item/${item.name}")
}

enum class LeftNaviItem(
    val displayIcon: ImageVector
) {
    User(displayIcon = Icons.Default.AccountCircle),
    Home(displayIcon = Icons.Default.Home),
    Live(displayIcon = Icons.Default.Videocam),
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
            onItemActivated = {},
            onOpenSettings = {},
        )
    }
}
