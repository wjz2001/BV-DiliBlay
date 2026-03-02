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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowScope
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
    isHistorySearching: Boolean = false,
    onHistoryTabDirectionUp: (isLongPress: Boolean) -> Unit = {},
    onSelectedChanged: (TopNavItem) -> Unit = {},
    onClick: (TopNavItem) -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }

    var selectedNav by remember { mutableStateOf(items.first()) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val verticalPadding by animateDpAsState(
        targetValue = if (isLargePadding) 12.dp else 6.dp,
        label = "top nav vertical padding"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp, verticalPadding),
        horizontalArrangement = Arrangement.Center
    ) {
        TabRow(
            modifier = Modifier
                .focusRestorer(focusRequester),
            selectedTabIndex = selectedTabIndex,
            separator = { Spacer(modifier = Modifier.width(12.dp)) },
        ) {
            items.forEachIndexed { index, tab ->
                val isHistoryTab =
                    (tab is HomeTopNavItem && tab == HomeTopNavItem.History) ||
                            (tab is PersonalTopNavItem && tab == PersonalTopNavItem.History)

                NavItemTab(
                    modifier = Modifier
                        .ifElse(index == 0, Modifier.focusRequester(focusRequester)),
                    topNavItem = tab,
                    selected = index == selectedTabIndex,
                    showHistorySearchIcon = isHistoryTab && isHistorySearching,
                    onHistoryTabDirectionUp = if (isHistoryTab) onHistoryTabDirectionUp else null,
                    onFocus = {
                        selectedNav = tab
                        selectedTabIndex = index
                        onSelectedChanged(tab)
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
    onHistoryTabDirectionUp: ((isLongPress: Boolean) -> Unit)? = null,
    onClick: () -> Unit,
    onFocus: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tabLabelFontSize = MaterialTheme.typography.labelLarge.fontSize
    val filterIconSizeDp = with(density) { tabLabelFontSize.toDp() }

    var directionUpPressedOnThisTab by remember(topNavItem) { mutableStateOf(false) }
    var directionUpLongPressTriggered by remember(topNavItem) { mutableStateOf(false) }
    var confirmLongPressTriggered by remember(topNavItem) { mutableStateOf(false) }

    Tab(
        modifier = if (onHistoryTabDirectionUp != null) {
            modifier.onPreviewKeyEvent { event ->
                val isDirectionUp = event.key == Key.DirectionUp
                val isConfirmKey =
                    event.key == Key.DirectionCenter ||
                            event.key == Key.Enter ||
                            event.key == Key.Spacebar

                if (!isDirectionUp && !isConfirmKey) return@onPreviewKeyEvent false

                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (isDirectionUp) {
                            // 记录：这一轮上键按下确实发生在当前 Tab
                            directionUpPressedOnThisTab = true
                        }

                        if (event.nativeKeyEvent.isLongPress) {
                            if (isDirectionUp) {
                                if (!directionUpLongPressTriggered) {
                                    directionUpLongPressTriggered = true
                                    onHistoryTabDirectionUp(true)
                                }
                            } else {
                                if (!confirmLongPressTriggered) {
                                    confirmLongPressTriggered = true
                                    onHistoryTabDirectionUp(true)
                                }
                            }
                            return@onPreviewKeyEvent true
                        }

                        if (isDirectionUp) {
                            // 上键短按延迟到 KeyUp 决定，避免与长按冲突
                            return@onPreviewKeyEvent true
                        }
                        false
                    }

                    KeyEventType.KeyUp -> {
                        if (isDirectionUp) {
                            // 没有匹配到本 Tab 的 KeyDown，说明多半是“回焦点”带来的 KeyUp，忽略
                            if (!directionUpPressedOnThisTab) {
                                return@onPreviewKeyEvent true
                            }

                            directionUpPressedOnThisTab = false

                            if (directionUpLongPressTriggered) {
                                directionUpLongPressTriggered = false
                                true
                            } else {
                                onHistoryTabDirectionUp(false)
                                true
                            }
                        } else {
                            if (confirmLongPressTriggered) {
                                confirmLongPressTriggered = false
                                true
                            } else {
                                false
                            }
                        }
                    }

                    else -> false
                }
            }
        } else {
            modifier
        },
        selected = selected,
        onFocus = {
            // 焦点切入时清理按键状态，避免跨焦点污染
            directionUpPressedOnThisTab = false
            directionUpLongPressTriggered = false
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

    companion object{
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

enum class PersonalTopNavItem : TopNavItem {
    ToView,
    History,
    Favorite,
    FollowingSeason;

    override fun getDisplayName(context: Context): String {
        return when (this) {
            ToView -> "稍后再看"
            History -> "历史"
            Favorite -> "收藏"
            FollowingSeason -> "我追的番"
        }
    }
}

enum class SearchTypeTopNavItem: TopNavItem {
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