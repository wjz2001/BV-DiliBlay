package dev.aaa1115910.bv.component

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.TabRowScope
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.pgc.PgcType
import dev.aaa1115910.biliapi.entity.ugc.UgcTypeV2
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.util.getDisplayName

@Composable
fun TopNav(
    modifier: Modifier = Modifier,
    items: List<TopNavItem>,
    isLargePadding: Boolean,
    selectedItem: TopNavItem? = null,
    defaultFocusRequester: FocusRequester? = null,
    onDefaultFocusReady: (() -> Unit)? = null,
    isHistorySearching: Boolean = false,
    onTabConfirmLongPress: ((TopNavItem) -> Boolean)? = null,
    onLeftBoundaryExit: (() -> Unit)? = null,
    onRightBoundaryExit: (() -> Unit)? = null,
    onSelectedChanged: (TopNavItem) -> Unit = {},
    onClick: (TopNavItem) -> Unit = {}
) {
    val internalFocusRequester = remember { FocusRequester() }
    val entryFocusRequester = defaultFocusRequester ?: internalFocusRequester

    var selectedTabIndex by remember(items) { mutableIntStateOf(0) }

    val focusTargetIndex = selectedItem
        ?.let(items::indexOf)
        ?.takeIf { it >= 0 }
        ?: 0

    var defaultFocusReadyNotified by remember(focusTargetIndex) { mutableStateOf(false) }
    val verticalPadding by animateDpAsState(
        targetValue = if (isLargePadding) 12.dp else 6.dp,
        label = "top nav vertical padding"
    )

    LaunchedEffect(items, selectedItem) {
        val selectedIndex = selectedItem
            ?.let(items::indexOf)
            ?.takeIf { it >= 0 }
            ?: return@LaunchedEffect

        if (selectedTabIndex != selectedIndex) {
            selectedTabIndex = selectedIndex
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp, verticalPadding),
        horizontalArrangement = Arrangement.Center
    ) {
        TabRow(
            modifier = Modifier.focusRestorer(entryFocusRequester),
            selectedTabIndex = selectedTabIndex,
            separator = { Spacer(modifier = Modifier.width(12.dp)) },
            indicator = { tabPositions, doesTabRowHaveFocus ->
                tabPositions.getOrNull(selectedTabIndex)?.let { currentTabPosition ->
                    TabRowDefaults.PillIndicator(
                        currentTabPosition = currentTabPosition,
                        doesTabRowHaveFocus = doesTabRowHaveFocus,
                        activeColor = MaterialTheme.colorScheme.primary,
                        inactiveColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
            },
        ) {
            items.forEachIndexed { index, tab ->
                val isHistoryTab = tab is HomeTopNavItem && tab == HomeTopNavItem.History

                NavItemTab(
                    modifier = Modifier.ifElse(
                        index == focusTargetIndex,
                        Modifier
                            .focusRequester(entryFocusRequester)
                            .onGloballyPositioned {
                                if (!defaultFocusReadyNotified) {
                                    defaultFocusReadyNotified = true
                                    onDefaultFocusReady?.invoke()
                                }
                            }
                    ),
                    topNavItem = tab,
                    selected = index == selectedTabIndex,
                    showHistorySearchIcon = isHistoryTab && isHistorySearching,
                    onTabConfirmLongPress = onTabConfirmLongPress?.let { callback ->
                        { callback(tab) }
                    },
                    onLeftBoundaryExit = onLeftBoundaryExit.takeIf { index == 0 },
                    onRightBoundaryExit = onRightBoundaryExit.takeIf { index == items.lastIndex },
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

@Composable
private fun TabRowScope.NavItemTab(
    modifier: Modifier = Modifier,
    topNavItem: TopNavItem,
    selected: Boolean,
    showHistorySearchIcon: Boolean = false,
    onTabConfirmLongPress: (() -> Boolean)? = null,
    onLeftBoundaryExit: (() -> Unit)? = null,
    onRightBoundaryExit: (() -> Unit)? = null,
    onClick: () -> Unit,
    onFocus: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tabLabelFontSize = MaterialTheme.typography.labelLarge.fontSize
    val filterIconSizeDp = with(density) { tabLabelFontSize.toDp() }

    var confirmLongPressTriggered by remember(topNavItem) { mutableStateOf(false) }

    Tab(
        colors = TabDefaults.pillIndicatorTabColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            inactiveContentColor = MaterialTheme.colorScheme.onSurface,
            selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
            focusedSelectedContentColor = MaterialTheme.colorScheme.onPrimary
        ),
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
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
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
            Text(
                modifier = Modifier
                    .height(32.dp)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                text = topNavItem.getDisplayName(context),
                color = LocalContentColor.current,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
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
    FollowingSeason(6, "我追的番");

    companion object {
        fun fromCode(code: Int): HomeTopNavItem {
            return HomeTopNavItem.entries.find { it.code == code } ?: Dynamics
        }
    }

    override fun getDisplayName(context: Context): String {
        return displayName
    }
}

enum class UgcTopNavItem(val ugcTypeV2: UgcTypeV2) : TopNavItem {
    Douga(UgcTypeV2.Douga),
    Game(UgcTypeV2.Game),
    Kichiku(UgcTypeV2.Kichiku),
    Music(UgcTypeV2.Music),
    Dance(UgcTypeV2.Dance),
    Cinephile(UgcTypeV2.Cinephile),
    Ent(UgcTypeV2.Ent),
    Knowledge(UgcTypeV2.Knowledge),
    Tech(UgcTypeV2.Tech),
    Information(UgcTypeV2.Information),
    Food(UgcTypeV2.Food),
    Life(UgcTypeV2.LifeJoy),
    Car(UgcTypeV2.Car),
    Fashion(UgcTypeV2.Fashion),
    Sports(UgcTypeV2.Sports),
    Animal(UgcTypeV2.Animal);

    override fun getDisplayName(context: Context): String {
        return ugcTypeV2.getDisplayName(context)
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
