package dev.aaa1115910.bv.component

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.tv.material3.Icon
import dev.aaa1115910.biliapi.entity.pgc.PgcType
import dev.aaa1115910.biliapi.entity.ugc.UgcType
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.util.getDisplayName

@Composable
fun TopNav(
    modifier: Modifier = Modifier,
    leadingContent: @Composable () -> Unit,
    items: List<TopNavItem>,
    selectedItem: TopNavItem? = null,
    activeItem: TopNavItem? = null,
    autoRefreshItems: Collection<TopNavItem> = emptySet<TopNavItem>(),
    entryFocusItem: TopNavItem? = null,
    entryFocusTarget: TopNavEntryFocusTarget = TopNavEntryFocusTarget.DefaultEntry,
    onDefaultFocusReady: ((Any) -> Unit)? = null,
    onEntryFocusReady: ((TopNavEntryFocusReady) -> Unit)? = null,
    onEntryFocusResolution: ((TopNavEntryFocusResolution) -> Unit)? = null,
    onEntryFocusConsumed: ((TopNavEntryFocusConsumed) -> Unit)? = null,
    isHistorySearching: Boolean = false,
    focusedLeadingIcon: ((TopNavItem) -> TopNavLeadingIcon?)? = null,
    onTabConfirmLongPress: ((TopNavItem) -> Boolean)? = null,
    contentFocusEnabled: Boolean = false,
    contentFocusReadyKey: Any? = null,
    onLeftBoundaryExit: (() -> Unit)? = null,
    onRightBoundaryExit: (() -> Unit)? = null,
    focusNodeId: WjzFocusNodeId? = null,
    focusLayer: WjzFocusLayer = WjzFocusLayer.TopNav,
    backFocusEnabled: Boolean = true,
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(MainChromeDefaults.Size),
                contentAlignment = Alignment.Center
            ) {
                leadingContent()
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = MainChromeDefaults.TopNavVerticalPadding),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.Center) {
                    BvUnderlineTabRow(
                        modifier = Modifier,
                        items = items,
                        selectedItem = selectedItem,
                        entryFocusItem = entryFocusItem,
                        entryFocusTarget = entryFocusTarget.toBvTabEntryFocusTarget(),
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
                        onDefaultFocusReady = onDefaultFocusReady,
                        onEntryFocusReady = { ready ->
                            onEntryFocusReady?.invoke(
                                TopNavEntryFocusReady(
                                    target = entryFocusTarget,
                                    item = ready.item,
                                    itemKey = ready.itemKey,
                                    nodeId = ready.nodeId
                                )
                            )
                        },
                        onEntryFocusResolution = { resolution ->
                            onEntryFocusResolution?.invoke(
                                resolution.toTopNavEntryFocusResolution(entryFocusTarget)
                            )
                        },
                        onEntryFocusConsumed = { consumed ->
                            onEntryFocusConsumed?.invoke(
                                TopNavEntryFocusConsumed(
                                    target = entryFocusTarget,
                                    item = consumed.item,
                                    itemKey = consumed.itemKey,
                                    nodeId = consumed.nodeId
                                )
                            )
                        },
                        onSelectedChanged = onSelectedChanged,
                        onClick = onClick,
                        onLongClick = onTabConfirmLongPress,
                        onLeftExit = onLeftBoundaryExit,
                        onRightExit = onRightBoundaryExit,
                        contentFocusEnabled = contentFocusEnabled,
                        contentFocusReadyKey = contentFocusReadyKey,
                        onContentFocusRequested = onContentFocusRequested,
                        focusNodeId = focusNodeId,
                        focusLayer = focusLayer,
                        backFocusEnabled = backFocusEnabled,
                        autoRequestEntryFocus = backFocusEnabled,
                        blockUp = true
                    )
                }
            }

            Spacer(modifier = Modifier.size(MainChromeDefaults.Size))
        }
    }
}

enum class TopNavEntryFocusTarget {
    DefaultEntry,
    LeftEntry,
    RightEntry
}

data class TopNavEntryFocusReady(
    val target: TopNavEntryFocusTarget,
    val item: TopNavItem,
    val itemKey: Any,
    val nodeId: WjzFocusNodeId
)

data class TopNavEntryFocusConsumed(
    val target: TopNavEntryFocusTarget,
    val item: TopNavItem,
    val itemKey: Any,
    val nodeId: WjzFocusNodeId
)

sealed interface TopNavEntryFocusResolution {
    data class Ready(
        val ready: TopNavEntryFocusReady
    ) : TopNavEntryFocusResolution

    data class Pending(
        val target: TopNavEntryFocusTarget
    ) : TopNavEntryFocusResolution

    data class Reject(
        val target: TopNavEntryFocusTarget
    ) : TopNavEntryFocusResolution
}

fun resolveTopNavEntryFocus(
    items: List<TopNavItem>,
    selectedItem: TopNavItem?,
    entryFocusItem: TopNavItem?,
    entryFocusTarget: TopNavEntryFocusTarget = TopNavEntryFocusTarget.DefaultEntry,
    focusNodeId: WjzFocusNodeId? = null
): TopNavEntryFocusResolution {
    return when (val resolution = resolveBvTabEntryFocus(
        items = items,
        selectedItem = selectedItem,
        entryFocusItem = entryFocusItem,
        entryFocusTarget = entryFocusTarget.toBvTabEntryFocusTarget(),
        itemKey = { it },
        focusNodeId = focusNodeId
    )) {
        is BvTabEntryFocusResolution.Ready -> TopNavEntryFocusResolution.Ready(
            TopNavEntryFocusReady(
                target = entryFocusTarget,
                item = resolution.item,
                itemKey = resolution.itemKey,
                nodeId = resolution.nodeId
            )
        )

        is BvTabEntryFocusResolution.Pending -> TopNavEntryFocusResolution.Pending(
            entryFocusTarget
        )

        is BvTabEntryFocusResolution.Reject -> TopNavEntryFocusResolution.Reject(
            entryFocusTarget
        )
    }
}

private fun BvTabEntryFocusResolution<TopNavItem>.toTopNavEntryFocusResolution(
    entryFocusTarget: TopNavEntryFocusTarget
): TopNavEntryFocusResolution {
    return when (this) {
        is BvTabEntryFocusResolution.Ready -> TopNavEntryFocusResolution.Ready(
            TopNavEntryFocusReady(
                target = entryFocusTarget,
                item = item,
                itemKey = itemKey,
                nodeId = nodeId
            )
        )

        is BvTabEntryFocusResolution.Pending -> TopNavEntryFocusResolution.Pending(
            entryFocusTarget
        )

        is BvTabEntryFocusResolution.Reject -> TopNavEntryFocusResolution.Reject(
            entryFocusTarget
        )
    }
}

private fun TopNavEntryFocusTarget.toBvTabEntryFocusTarget(): BvTabEntryFocusTarget {
    return when (this) {
        TopNavEntryFocusTarget.DefaultEntry -> BvTabEntryFocusTarget.DefaultEntry
        TopNavEntryFocusTarget.LeftEntry -> BvTabEntryFocusTarget.DefaultEntry
        TopNavEntryFocusTarget.RightEntry -> BvTabEntryFocusTarget.DefaultEntry
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
    FollowingSeason(9, "我追的番"),
    Follow(10, "我的关注");




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
