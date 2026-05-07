package dev.aaa1115910.bv.component

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowScope
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.pgc.PgcType
import dev.aaa1115910.biliapi.entity.ugc.UgcType
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.util.getDisplayName

@Composable
fun TopNav(
    modifier: Modifier = Modifier,
    items: List<TopNavItem>,
    selectedItem: TopNavItem? = null,
    entryFocusItem: TopNavItem? = null,
    defaultFocusRequester: FocusRequester? = null,
    onDefaultFocusReady: (() -> Unit)? = null,
    isHistorySearching: Boolean = false,
    focusedLeadingIcon: ((TopNavItem) -> TopNavLeadingIcon?)? = null,
    onTabConfirmLongPress: ((TopNavItem) -> Boolean)? = null,
    contentFocusRequester: FocusRequester? = null,
    onLeftBoundaryExit: (() -> Unit)? = null,
    onRightBoundaryExit: (() -> Unit)? = null,
    onSelectedChanged: (TopNavItem) -> Unit = {},
    onClick: (TopNavItem) -> Unit = {}
) {
    val internalFocusRequester = remember { FocusRequester() }
    val entryFocusRequester = defaultFocusRequester ?: internalFocusRequester
    val tabFocusRequesters = remember(items) { List(items.size) { FocusRequester() } }

    var selectedTabIndex by remember(items) { mutableIntStateOf(0) }

    val focusTargetIndex = (entryFocusItem ?: selectedItem)
        ?.let(items::indexOf)
        ?.takeIf { it >= 0 }
        ?: 0
    var contentFocusRequestToken by remember { mutableIntStateOf(0) }

    var defaultFocusReadyNotified by remember(focusTargetIndex) { mutableStateOf(false) }
    LaunchedEffect(items, selectedItem) {
        val selectedIndex = selectedItem
            ?.let(items::indexOf)
            ?.takeIf { it >= 0 }
            ?: return@LaunchedEffect

        if (selectedTabIndex != selectedIndex) {
            selectedTabIndex = selectedIndex
        }
    }

    LaunchedEffect(contentFocusRequestToken) {
        if (contentFocusRequestToken == 0) return@LaunchedEffect
        val requester = contentFocusRequester ?: return@LaunchedEffect
        repeat(3) {
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }

    LaunchedEffect(entryFocusItem, focusTargetIndex) {
        if (entryFocusItem == null) return@LaunchedEffect
        repeat(3) {
            withFrameNanos { }
            runCatching { entryFocusRequester.requestFocus() }
        }
    }

    MainTopBarContainer(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TabRow(
                modifier = Modifier,
                selectedTabIndex = selectedTabIndex,
                separator = { MainTopTabSeparator() },
                indicator = mainTopTabIndicator(selectedTabIndex)
            ) {
                items.forEachIndexed { index, tab ->
                    val isHistoryTab = tab is HomeTopNavItem && tab == HomeTopNavItem.History
                    fun requesterFor(targetIndex: Int): FocusRequester {
                        return if (targetIndex == focusTargetIndex) {
                            entryFocusRequester
                        } else {
                            tabFocusRequesters[targetIndex]
                        }
                    }

                    NavItemTab(
                        modifier = Modifier
                            .focusRequester(requesterFor(index))
                            .focusProperties {
                                left = if (index == 0) {
                                    FocusRequester.Default
                                } else {
                                    requesterFor(index - 1)
                                }
                                right = if (index == items.lastIndex) {
                                    FocusRequester.Default
                                } else {
                                    requesterFor(index + 1)
                                }
                                up = FocusRequester.Cancel
                                down = contentFocusRequester ?: FocusRequester.Default
                            }
                            .ifElse(
                                index == focusTargetIndex,
                                Modifier.onGloballyPositioned {
                                    if (!defaultFocusReadyNotified) {
                                        defaultFocusReadyNotified = true
                                        onDefaultFocusReady?.invoke()
                                    }
                                }
                            ),
                        topNavItem = tab,
                        selected = index == selectedTabIndex,
                        showHistorySearchIcon = isHistoryTab && isHistorySearching,
                        focusedLeadingIcon = focusedLeadingIcon?.invoke(tab),
                        onTabConfirmLongPress = onTabConfirmLongPress?.let { callback ->
                            { callback(tab) }
                        },
                        onLeftBoundaryExit = onLeftBoundaryExit.takeIf { index == 0 },
                        onRightBoundaryExit = onRightBoundaryExit.takeIf { index == items.lastIndex },
                        onDownToContent = contentFocusRequester?.let {
                            {
                                onClick(tab)
                                contentFocusRequestToken++
                                true
                            }
                        },
                        onFocus = {
                            if (selectedTabIndex != index) {
                                selectedTabIndex = index
                                onSelectedChanged(tab)
                            }
                        },
                        onClick = { onClick(tab) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TabRowScope.NavItemTab(
    modifier: Modifier = Modifier,
    topNavItem: TopNavItem,
    selected: Boolean,
    showHistorySearchIcon: Boolean = false,
    focusedLeadingIcon: TopNavLeadingIcon? = null,
    onTabConfirmLongPress: (() -> Boolean)? = null,
    onLeftBoundaryExit: (() -> Unit)? = null,
    onRightBoundaryExit: (() -> Unit)? = null,
    onDownToContent: (() -> Boolean)? = null,
    onClick: () -> Unit,
    onFocus: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tabLabelFontSize = MaterialTheme.typography.labelLarge.fontSize
    val filterIconSizeDp = with(density) { tabLabelFontSize.toDp() }

    var confirmLongPressTriggered by remember(topNavItem) { mutableStateOf(false) }

    Tab(
        colors = mainTopTabColors(),
        modifier = modifier.onPreviewKeyEvent { event ->
            val isDirectionLeft = event.key == Key.DirectionLeft
            val isDirectionRight = event.key == Key.DirectionRight
            val isConfirmKey =
                event.key == Key.DirectionCenter ||
                        event.key == Key.Enter ||
                        event.key == Key.Spacebar

            if (event.type == KeyEventType.KeyDown) {
                if (isDirectionLeft && onLeftBoundaryExit != null) {
                    onLeftBoundaryExit()
                    return@onPreviewKeyEvent true
                }

                if (isDirectionRight && onRightBoundaryExit != null) {
                    onRightBoundaryExit()
                    return@onPreviewKeyEvent true
                }

                if (event.key == Key.DirectionDown && onDownToContent != null) {
                    return@onPreviewKeyEvent onDownToContent()
                }
            }

            if (!isConfirmKey) return@onPreviewKeyEvent false

            when (event.type) {
                KeyEventType.KeyDown -> {
                    if (event.nativeKeyEvent.isLongPress) {
                        if (onTabConfirmLongPress == null) {
                            return@onPreviewKeyEvent false
                        }
                        if (!confirmLongPressTriggered) {
                            confirmLongPressTriggered = onTabConfirmLongPress()
                        }
                        return@onPreviewKeyEvent confirmLongPressTriggered
                    }
                    false
                }

                KeyEventType.KeyUp -> {
                    if (confirmLongPressTriggered) {
                        confirmLongPressTriggered = false
                        true
                    } else {
                        false
                    }
                }

                else -> false
            }
        },
        selected = selected,
        onFocus = {
            // 焦点切入时清理按键状态，避免跨焦点污染
            confirmLongPressTriggered = false
            onFocus()
        },
        onClick = onClick
    ) {
        if (showHistorySearchIcon) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MainTopTabDefaults.TabContentHeight),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.padding(MainTopTabDefaults.TabContentPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier.size(filterIconSizeDp),
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = topNavItem.getDisplayName(context),
                        color = LocalContentColor.current,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .height(MainTopTabDefaults.TabContentHeight)
                    .padding(MainTopTabDefaults.TabContentPadding)
                    .wrapContentWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedVisibility(
                        visible = selected && focusedLeadingIcon != null,
                        enter = expandHorizontally() + fadeIn(),
                        exit = shrinkHorizontally() + fadeOut()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LeadingIcon(
                                icon = focusedLeadingIcon ?: TopNavLeadingIcon.Vector(Icons.Rounded.FilterList),
                                iconSizeDp = filterIconSizeDp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    }
                    Text(
                        text = topNavItem.getDisplayName(context),
                        color = LocalContentColor.current,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun LeadingIcon(
    icon: TopNavLeadingIcon,
    iconSizeDp: androidx.compose.ui.unit.Dp
) {
    when (icon) {
        is TopNavLeadingIcon.Vector -> Icon(
            modifier = Modifier.size(iconSizeDp),
            imageVector = icon.imageVector,
            contentDescription = null
        )

        is TopNavLeadingIcon.DrawableRes -> Icon(
            modifier = Modifier.size(iconSizeDp),
            painter = painterResource(icon.resId),
            contentDescription = null
        )
    }
}

sealed interface TopNavLeadingIcon {
    data class Vector(val imageVector: ImageVector) : TopNavLeadingIcon
    data class DrawableRes(val resId: Int) : TopNavLeadingIcon
}

interface TopNavItem {
    fun getDisplayName(context: Context = BVApp.context): String
}

enum class HomeTopNavItem(val code: Int, private val displayName: String) : TopNavItem {
    Dynamics(0, "动态"),
    History(1, "历史"),
    Favorite(2, "收藏"),
    ToView(3, "稍后再看"),
    Recommend(4, "推荐"),
    Popular(5, "热门"),
    FollowingSeason(6, "我追的番"),
    Search(7, "搜索"),
    MyClassroom(8, "我的课堂");

    companion object {
        fun fromCode(code: Int): HomeTopNavItem {
            return HomeTopNavItem.entries.find { it.code == code } ?: Dynamics
        }
    }

    override fun getDisplayName(context: Context): String {
        return displayName
    }
}

enum class UgcTopNavItem(val ugcType: UgcType) : TopNavItem {
    Douga(UgcType.Douga),
    Game(UgcType.Game),
    Kichiku(UgcType.Kichiku),
    Music(UgcType.Music),
    Dance(UgcType.Dance),
    Cinephile(UgcType.Cinephile),
    Ent(UgcType.Ent),
    Knowledge(UgcType.Knowledge),
    Tech(UgcType.Tech),
    Information(UgcType.Information),
    Food(UgcType.Food),
    Life(UgcType.LifeJoy),
    Car(UgcType.Car),
    Fashion(UgcType.Fashion),
    Sports(UgcType.Sports),
    Animal(UgcType.Animal);

    override fun getDisplayName(context: Context): String {
        return ugcType.getDisplayName(context)
    }
}

enum class PgcTopNavItem(private val pgcType: PgcType) : TopNavItem {
    Anime(PgcType.Anime),
    GuoChuang(PgcType.GuoChuang),
    Movie(PgcType.Movie),
    Documentary(PgcType.Documentary),
    Tv(PgcType.Tv),
    Variety(PgcType.Variety);

    override fun getDisplayName(context: Context): String {
        return pgcType.getDisplayName(context)
    }
}

enum class SearchTypeTopNavItem : TopNavItem {
    Video,
    MediaBangumi,
    MediaFt,
    BiliUser;

    override fun getDisplayName(context: Context): String {
        return when (this) {
            Video -> "视频"
            MediaBangumi -> "番剧"
            MediaFt -> "影视"
            BiliUser -> "用户"
        }
    }
}
