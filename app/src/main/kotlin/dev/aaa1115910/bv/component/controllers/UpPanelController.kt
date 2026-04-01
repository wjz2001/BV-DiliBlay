@file:Suppress("DEPRECATION")

package dev.aaa1115910.bv.component.controllers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Toc
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.aaa1115910.bv.component.ifElse
import dev.aaa1115910.bv.entity.VideoListItem
import dev.aaa1115910.bv.ui.state.PlayerChapter
import dev.aaa1115910.bv.ui.state.PlayerUiState

private enum class UpPanelTab(
    val title: String,
    val icon: ImageVector
) {
    Chapter("选择章节", Icons.Rounded.Toc),
    Video("选择视频", Icons.Rounded.VideoLibrary),
    Collection("选择合集", Icons.Rounded.Subscriptions)
}

private enum class UpPanelFocusState { Nav, Content }

@Composable
fun UpPanelController(
    modifier: Modifier = Modifier,
    show: Boolean,
    uiState: PlayerUiState,
    currentTimeMs: Long,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onGoTime: (timeMs: Long) -> Unit,
    onPlay: () -> Unit,
    onPlayNewVideo: (VideoListItem) -> Unit,
    onEnsureUgcPagesLoaded: (aid: Long) -> Unit,
) {
    val chaptersEnabled = uiState.availableChapters.isNotEmpty()
    val collectionsEnabled = (uiState.ugcSeason?.sections?.size ?: 0) > 1 // 严格按 sections.size

    var selectedTab by remember { mutableStateOf(UpPanelTab.Video) }
    var focusState by remember { mutableStateOf(UpPanelFocusState.Nav) }
    var forceNavExpandedOnOpen by remember { mutableStateOf(false) }

    // 默认焦点落点：导航“选择视频”
    val navVideoItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(show) {
        if (show) {
            selectedTab = UpPanelTab.Video
            focusState = UpPanelFocusState.Nav
            forceNavExpandedOnOpen = true
            // 等一帧再请求焦点，避免初次 composition 未稳定
            kotlinx.coroutines.android.awaitFrame()
            navVideoItemFocusRequester.requestFocus()
        } else {
            forceNavExpandedOnOpen = false
        }
    }

    // 当前 tab 不可用时，强制回到 Video
    LaunchedEffect(show, chaptersEnabled, collectionsEnabled) {
        if (!show) return@LaunchedEffect
        if (selectedTab == UpPanelTab.Chapter && !chaptersEnabled) selectedTab = UpPanelTab.Video
        if (selectedTab == UpPanelTab.Collection && !collectionsEnabled) selectedTab = UpPanelTab.Video
    }

    val chapterBuilt = buildChapterParents(
        chapters = uiState.availableChapters,
        currentTimeMs = currentTimeMs
    )
    val collectionBuilt = buildCollectionParents(
        uiState = uiState
    )
    val videoPageProgress = buildVideoPageProgress(
        videoList = uiState.availableVideoList,
        currentAid = uiState.aid,
        currentCid = uiState.cid
    )

    val chapterProgressText = formatProgressText(
        selectedIndex = chapterBuilt.selectedIndex,
        total = chapterBuilt.parents.size
    )
    val videoProgressText = videoPageProgress?.let {
        formatProgressText(
            selectedIndex = it.selectedIndex,
            total = it.total
        )
    }
    val collectionProgressText = formatProgressText(
        selectedIndex = collectionBuilt.selectedIndex,
        total = collectionBuilt.totalCount
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        AnimatedVisibility(
            visible = show,
            enter = expandHorizontally(),
            exit = shrinkHorizontally()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .onPreviewKeyEvent { event ->
                        if (!forceNavExpandedOnOpen) return@onPreviewKeyEvent false
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (event.key != Key.DirectionUp && event.key != Key.Unknown) {
                            forceNavExpandedOnOpen = false
                        }
                        false
                    },
                colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.5f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    // 左侧导航
                    // 左侧导航
                    UpPanelNavList(
                        modifier = Modifier
                            .focusGroup()
                            .onFocusChanged { fs ->
                                if (fs.hasFocus) focusState = UpPanelFocusState.Nav
                            }
                            .onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyUp) {
                                    // 直接判断，避免创建 List 对象
                                    return@onPreviewKeyEvent it.key != Key.Enter && it.key != Key.DirectionCenter
                                }
                                false
                            },
                        selectedTab = selectedTab,
                        isExpanded = forceNavExpandedOnOpen || focusState == UpPanelFocusState.Nav,
                        chaptersEnabled = chaptersEnabled,
                        collectionsEnabled = collectionsEnabled,
                        chapterProgressText = chapterProgressText,
                        videoProgressText = videoProgressText,
                        collectionProgressText = collectionProgressText,
                        navVideoItemFocusRequester = navVideoItemFocusRequester,
                        onSelectedChanged = { selectedTab = it }
                    )

                    // 右侧内容区：按 Left 回到导航
                    Box(
                        modifier = Modifier
                            .focusGroup()
                            .onFocusChanged { fs ->
                                if (fs.hasFocus) focusState = UpPanelFocusState.Content
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val contentActive = (focusState == UpPanelFocusState.Content)

                        when (selectedTab) {
                            UpPanelTab.Video -> {
                                // 不改 VideoListController.kt 的宽度实现；在这里用 modifier 约束整体宽度，避免填满剩余空间
                                VideoListController(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(300.dp),
                                    show = show,
                                    active = contentActive,
                                    currentAid = uiState.aid,
                                    currentCid = uiState.cid,
                                    videoList = uiState.availableVideoList,
                                    onEnsureUgcPagesLoaded = onEnsureUgcPagesLoaded,
                                    onPlayNewVideo = {
                                        onPlayNewVideo(it)
                                        onDismiss()
                                    }
                                )
                            }

                            UpPanelTab.Chapter -> {
                                if (!chaptersEnabled) return@Box

                                CollectionListController(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .widthIn(min = 300.dp) ,
                                    show = show,
                                    active = contentActive,
                                    parents = chapterBuilt.parents,
                                    selectedParentKey = chapterBuilt.selectedParentKey,
                                    pinnedParentKey = chapterBuilt.selectedParentKey,
                                    enterFocusKey = chapterBuilt.selectedParentKey,
                                    selectedChildKey = null,
                                    autoExpandPinnedParent = false,
                                    onEnsureChildrenLoaded = {},
                                    onParentClick = { parent ->
                                        val chapter = parent.extra as? PlayerChapter ?: return@CollectionListController
                                        onGoTime(chapter.fromSec * 1000L)
                                        if (!isPlaying) onPlay()
                                        onDismiss()
                                    },
                                    onChildClick = { _, _ -> }
                                )
                            }

                            UpPanelTab.Collection -> {
                                if (!collectionsEnabled) return@Box

                                CollectionListController(
                                    modifier = Modifier.fillMaxHeight(),
                                    show = show,
                                    active = contentActive,
                                    parents = collectionBuilt.parents,
                                    selectedParentKey = collectionBuilt.selectedParentKey,
                                    selectedChildKey = collectionBuilt.selectedChildKey,
                                    pinnedParentKey = collectionBuilt.selectedParentKey,
                                    onEnsureChildrenLoaded = {},
                                    onParentClick = { /* 父项点击展开/折叠由组件内部处理 */ },
                                    onChildClick = { _, child ->
                                        val target = child.extra as? VideoListItem ?: return@CollectionListController
                                        onPlayNewVideo(target)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpPanelNavItem(
    modifier: Modifier = Modifier,
    title: String,
    progressText: String?,
    icon: ImageVector,
    expanded: Boolean = true,
    expandedWidth: Dp,
    selected: Boolean,
    onFocus: () -> Unit = {},
    onClick: () -> Unit
) {
    val itemWidth by animateDpAsState(
        targetValue = if (expanded) expandedWidth else 66.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "UpPanelNavItem width [$title]"
    )

    DenseListItem(
        modifier = modifier
            .width(itemWidth)
            .onFocusChanged { if (it.hasFocus) onFocus() },
        selected = selected,
        onClick = onClick,
        headlineContent = {
            Box {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedVisibility(
                        visible = expanded,
                        label = "UpPanelNavItem text [$title]",
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                text = if (progressText != null) "$title$progressText" else title,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    AnimatedVisibility(
                        visible = !expanded,
                        label = "UpPanelNavItem icon [$title]",
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Icon(
                            modifier = Modifier.size(32.dp),
                            imageVector = icon,
                            contentDescription = null
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.4f),
        )
    )
}

@Composable
private fun UpPanelNavList(
    modifier: Modifier = Modifier,
    selectedTab: UpPanelTab,
    isExpanded: Boolean,
    chaptersEnabled: Boolean,
    collectionsEnabled: Boolean,
    chapterProgressText: String?,
    videoProgressText: String?,
    collectionProgressText: String?,
    navVideoItemFocusRequester: FocusRequester,
    onSelectedChanged: (UpPanelTab) -> Unit,
) {
    val listFocusRequester = remember { FocusRequester() }

    // 判断是否至少存在一个进度文本
    val hasAnyProgress = chapterProgressText != null ||
            videoProgressText != null ||
            collectionProgressText != null

    LaunchedEffect(Unit) {
        listFocusRequester.requestFocus()
    }

    LazyColumn(
        modifier = modifier
            .focusRequester(listFocusRequester)
            .focusRestorer(navVideoItemFocusRequester),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        itemsIndexed(UpPanelTab.entries) { _, tab ->
            val enabled = when (tab) {
                UpPanelTab.Chapter -> chaptersEnabled
                UpPanelTab.Video -> true
                UpPanelTab.Collection -> collectionsEnabled
            }

            val progressText = when (tab) {
                UpPanelTab.Chapter -> chapterProgressText
                UpPanelTab.Video -> videoProgressText
                UpPanelTab.Collection -> collectionProgressText
            }

            UpPanelNavItem(
                modifier = Modifier
                    .ifElse(tab == UpPanelTab.Video, Modifier.focusRequester(navVideoItemFocusRequester))
                    .focusProperties { canFocus = enabled }
                    .alpha(if (enabled) 1f else 0.45f),
                title = tab.title,
                progressText = progressText,
                icon = tab.icon,
                expanded = isExpanded,
                expandedWidth = if (hasAnyProgress) 260.dp else 200.dp,
                selected = selectedTab == tab,
                onClick = {},
                onFocus = { if (enabled) onSelectedChanged(tab) }
            )
        }
    }
}

private data class BuiltChapterParents(
    val parents: List<CollectionParentItem>,
    val selectedParentKey: Long?,
    val selectedIndex: Int?
)

private fun buildChapterParents(
    chapters: List<PlayerChapter>,
    currentTimeMs: Long
): BuiltChapterParents {
    val currentSec = (currentTimeMs / 1000L).toInt()

    val exactIndex = chapters.indexOfFirst { ch ->
        val from = ch.fromSec
        val to = ch.toSec
        if (to > 0) currentSec in from until to else currentSec >= from
    }

    val fallbackIndex = chapters.indexOfLast { ch ->
        currentSec >= ch.fromSec
    }

    val selectedIndex = when {
        exactIndex >= 0 -> exactIndex
        fallbackIndex >= 0 -> fallbackIndex
        else -> null
    }

    return BuiltChapterParents(
        parents = chapters.mapIndexed { index, ch ->
            CollectionParentItem(
                key = index.toLong(),
                title = ch.content,
                children = emptyList(),
                extra = ch
            )
        },
        selectedParentKey = selectedIndex?.toLong(),
        selectedIndex = selectedIndex
    )
}

private data class BuiltCollectionParents(
    val parents: List<CollectionParentItem>,
    val selectedParentKey: Long?,
    val selectedChildKey: Long?,
    val selectedIndex: Int?,
    val totalCount: Int
)

private fun buildCollectionParents(uiState: PlayerUiState): BuiltCollectionParents {
    val sections = uiState.ugcSeason?.sections.orEmpty()
    val currentAid = uiState.aid
    val currentCid = uiState.cid

    val parents = sections.mapIndexed { sectionIndex, section ->
        CollectionParentItem(
            key = sectionIndex.toLong(),
            title = section.title,
            children = section.episodes.map { ep ->
                val video = VideoListItem(
                    aid = ep.aid,
                    cid = ep.cid,
                    epid = ep.epid,
                    seasonId = uiState.seasonId.takeIf { it != 0 },
                    title = ep.title,
                    ugcPages = null
                )
                CollectionChildItem(
                    key = ep.aid,
                    title = ep.title,
                    extra = video
                )
            }
        )
    }

    val selectedParentIndex = sections.indexOfFirst { section ->
        section.episodes.any { ep -> ep.aid == currentAid || ep.cid == currentCid }
    }.takeIf { it >= 0 }

    val flatEpisodes = sections.flatMap { it.episodes }
    val selectedEpisodeIndex = flatEpisodes.indexOfFirst { ep ->
        ep.aid == currentAid || ep.cid == currentCid
    }.takeIf { it >= 0 }

    val selectedChildKey = flatEpisodes.firstOrNull { ep ->
        ep.aid == currentAid || ep.cid == currentCid
    }?.aid ?: currentAid.takeIf { it != 0L }

    return BuiltCollectionParents(
        parents = parents,
        selectedParentKey = selectedParentIndex?.toLong(),
        selectedChildKey = selectedChildKey,
        selectedIndex = selectedEpisodeIndex,
        totalCount = flatEpisodes.size
    )
}

private data class VideoPageProgress(
    val total: Int,
    val selectedIndex: Int?
)

private fun buildVideoPageProgress(
    videoList: List<VideoListItem>,
    currentAid: Long,
    currentCid: Long
): VideoPageProgress? {
    if (videoList.isEmpty()) return null

    val topLevelIndex = videoList.indexOfFirst { video ->
        video.aid == currentAid || video.cid == currentCid
    }

    if (videoList.size > 1 && topLevelIndex >= 0) {
        return VideoPageProgress(
            total = videoList.size,
            selectedIndex = topLevelIndex
        )
    }

    val parent = videoList.firstOrNull { it.aid == currentAid }
        ?: videoList.firstOrNull { it.cid == currentCid }
        ?: videoList.firstOrNull { video ->
            video.ugcPages?.any { page -> page.cid == currentCid } == true
        }

    val pages = parent?.ugcPages ?: return null
    val selectedIndex = pages.indexOfFirst { page -> page.cid == currentCid }
        .takeIf { it >= 0 }

    return VideoPageProgress(
        total = pages.size,
        selectedIndex = selectedIndex
    )
}

private fun formatProgressText(
    selectedIndex: Int?,
    total: Int
): String? {
    if (total <= 1) return null
    val current = selectedIndex?.plus(1) ?: return null
    return "（$current/$total）"
}