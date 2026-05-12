package dev.aaa1115910.bv.component

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.tv.material3.Icon
import dev.aaa1115910.biliapi.entity.pgc.PgcType
import dev.aaa1115910.biliapi.entity.ugc.UgcType
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.util.getDisplayName

@Composable
fun TopNav(
    modifier: Modifier = Modifier,
    items: List<TopNavItem>,
    selectedItem: TopNavItem? = null,
    activeItem: TopNavItem? = null,
    autoRefreshItems: Collection<TopNavItem> = emptySet<TopNavItem>(),
    entryFocusItem: TopNavItem? = null,
    defaultFocusRequester: FocusRequester? = null,
    onDefaultFocusReady: ((Any) -> Unit)? = null,
    isHistorySearching: Boolean = false,
    focusedLeadingIcon: ((TopNavItem) -> TopNavLeadingIcon?)? = null,
    onTabConfirmLongPress: ((TopNavItem) -> Boolean)? = null,
    contentFocusRequester: FocusRequester? = null,
    contentFocusReadyKey: Any? = null,
    onLeftBoundaryExit: (() -> Unit)? = null,
    onRightBoundaryExit: (() -> Unit)? = null,
    onContentFocusRequested: (TopNavItem) -> Unit = {},
    onAutoRefreshRequested: (TopNavItem) -> Unit = {},
    onSelectedChanged: (TopNavItem) -> Unit = {},
    onClick: (TopNavItem) -> Unit = {}
) {
    val context = LocalContext.current
    var previousActiveItem by remember { mutableStateOf<TopNavItem?>(null) }

    LaunchedEffect(activeItem, autoRefreshItems) {
        val previous = previousActiveItem
        previousActiveItem = activeItem
        val target = activeItem ?: return@LaunchedEffect
        if (previous != null && previous != target && target in autoRefreshItems) {
            onAutoRefreshRequested(target)
        }
    }

    MainTopBarContainer(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            BvUnderlineTabRow(
                modifier = Modifier,
                items = items,
                selectedItem = selectedItem,
                entryFocusItem = entryFocusItem,
                itemKey = { it },
                itemText = { it.getDisplayName(context) },
                itemIcon = { item, iconSize ->
                    when (val icon = focusedLeadingIcon?.invoke(item)) {
                        is TopNavLeadingIcon.Vector -> Icon(
                            modifier = Modifier.size(iconSize),
                            imageVector = icon.imageVector,
                            contentDescription = null
                        )

                        is TopNavLeadingIcon.DrawableRes -> Icon(
                            modifier = Modifier.size(iconSize),
                            painter = painterResource(icon.resId),
                            contentDescription = null
                        )

                        null -> Unit
                    }
                },
                itemHasIcon = { item -> focusedLeadingIcon?.invoke(item) != null },
                iconMode = BvTabIconMode.FocusedIconText,
                separator = { MainTopTabSeparator() },
                defaultFocusRequester = defaultFocusRequester,
                onDefaultFocusReady = onDefaultFocusReady,
                onSelectedChanged = onSelectedChanged,
                onClick = onClick,
                onLongClick = onTabConfirmLongPress,
                onLeftExit = onLeftBoundaryExit,
                onRightExit = onRightBoundaryExit,
                contentFocusRequester = contentFocusRequester,
                contentFocusReadyKey = contentFocusReadyKey,
                onContentFocusRequested = onContentFocusRequested,
                blockUp = true
            )
        }
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
    Search(0, "搜索"),
    Dynamics(1, "动态"),
    History(2, "历史"),
    Favorite(3, "收藏"),
    SubscribedCollection(4, "订阅合集"),
    MyClassroom(5, "我的课堂"),
    ToView(6, "稍后再看"),
    Recommend(7, "推荐"),
    Popular(8, "热门"),
    FollowingSeason(9, "我追的番");




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
