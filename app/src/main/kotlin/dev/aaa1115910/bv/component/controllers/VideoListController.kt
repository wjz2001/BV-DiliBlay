package dev.aaa1115910.bv.component.controllers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.resolve
import dev.aaa1115910.bv.wjzfocus.wjzDisabledFocus
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.wjzfocus.wjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.wjzFocusSingleListRestorerComponent
import dev.aaa1115910.bv.entity.VideoListItem
import dev.aaa1115910.bv.ui.theme.AppWhite
import androidx.tv.material3.ListItemDefaults
import dev.aaa1115910.bv.ui.theme.AppBlack
import dev.aaa1115910.bv.ui.theme.AppGray
import dev.aaa1115910.bv.ui.theme.C

import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun videoListParentFocusLocalId(cid: Long): WjzFocusLocalId =
    wjzFocusLocalId("video-list", "parent", cid)

private fun videoListChildFocusLocalId(parentCid: Long, childCid: Long): WjzFocusLocalId =
    wjzFocusLocalId("video-list", "parent", parentCid, "child", childCid)

private const val VideoListRestorerId = "video-list/restorer"

@Composable
fun VideoListController(
    modifier: Modifier = Modifier,
    show: Boolean,
    active: Boolean = show,
    currentAid: Long,
    currentCid: Long,
    videoList: List<VideoListItem>,
    onPlayNewVideo: (VideoListItem) -> Unit,
    onEnsureUgcPagesLoaded: (aid: Long) -> Unit,
) {
    val listState = rememberLazyListState()
    val focusCoordinator = LocalWjzFocusCoordinator.current
    val focusScopeId = LocalWjzFocusScopeId.current
    val listRestorer = remember(focusScopeId) {
        wjzFocusSingleListRestorerComponent(
            componentId = VideoListRestorerId,
            layer = WjzFocusLayer.Player,
            scopeId = focusScopeId
        )
    }

    fun restoreListFocus(nodeId: WjzFocusNodeId) {
        focusCoordinator?.let { coordinator ->
            listRestorer.target(nodeId = nodeId).restoreFocus(coordinator)
        }
    }

    val scope = rememberCoroutineScope()
    var ensureParentVisibleJob by remember { mutableStateOf<Job?>(null) }
    var focusedParentCid by remember { mutableStateOf<Long?>(null) }

    var didInitialPosition by remember { mutableStateOf(false) }
    var pendingFocusCid by remember { mutableStateOf<Long?>(null) }
    var pendingPrefetchAid by remember { mutableStateOf<Long?>(null) }

    // 仅在“打开时”标记一次“待聚焦 cid”：
    // 列表打开后不再因为 currentCid 的变化抢焦点
    LaunchedEffect(show, active) {
        if (show && active) {
            pendingFocusCid = currentCid
        } else {
            pendingFocusCid = null
            if (!show) {
                pendingPrefetchAid = null
                didInitialPosition = false
            }
        }
    }

    // 推导 pinnedParent：
    // 优先用 currentAid 锁定“当前父项”（即使当前 pages 还没加载）
    // 其次再用 cid 在父项/子项里兜底
    val pinnedParent = remember(videoList, currentAid, currentCid) {
        videoList.firstOrNull { it.aid == currentAid }
            ?: videoList.firstOrNull { it.cid == currentCid }
            ?: videoList.firstOrNull { it.ugcPages?.any { p -> p.cid == currentCid } == true }
    }

    /**
     * 打开时：
     * - 预取 pinnedParent 的子项数据（番剧集跳过）
     * - 初次定位到当前播放项
     */
    LaunchedEffect(show, active, videoList.size) {
        if (!show) return@LaunchedEffect
        if (didInitialPosition) return@LaunchedEffect
        if (videoList.isEmpty()) return@LaunchedEffect

        // 兜底：UGC 多P常见场景 videoList 只有 1 个父项，但 ugcPages 尚未加载且 currentCid 是子项
        val probableParent = pinnedParent ?: videoList.singleOrNull()

        if (probableParent != null && probableParent.epid == null) {
            onEnsureUgcPagesLoaded(probableParent.aid)
        }

        val targetIndex = videoList.indexOfFirst { v ->
            v.aid == currentAid ||
                    v.cid == currentCid ||
                    (v.ugcPages?.any { p -> p.cid == currentCid } == true)
        }
        if (targetIndex != -1) {
            listState.scrollToItem(targetIndex)
        }

        didInitialPosition = true
    }

    LaunchedEffect(show, active, focusedParentCid, videoList.size) {
        if (!show) return@LaunchedEffect
        val cid = focusedParentCid ?: return@LaunchedEffect

        val index = videoList.indexOfFirst { it.cid == cid }
        if (index < 0) return@LaunchedEffect

        // 等一帧拿稳定的 layoutInfo
        kotlinx.coroutines.android.awaitFrame()
        // 焦点已经变了就别滚旧的
        if (focusedParentCid != cid) return@LaunchedEffect

        val layout = listState.layoutInfo
        val viewportStart = layout.viewportStartOffset
        val viewportEnd = layout.viewportEndOffset

        val info = layout.visibleItemsInfo.firstOrNull { it.index == index }
        val itemStart = info?.offset
        val itemEnd = info?.let { it.offset + it.size }

        // visibleItemsInfo 里“有”不代表在屏幕内，必须用 offset/size 与 viewport 相交判断
        val actuallyVisible =
            (itemStart != null && itemEnd != null && itemEnd > viewportStart && itemStart < viewportEnd)

        if (!actuallyVisible) {
            // 两段式瞬移：先拉进来，再做 offset 微调（尾部更稳）
            listState.scrollToItem(index)
            kotlinx.coroutines.android.awaitFrame()
            if (focusedParentCid != cid) return@LaunchedEffect
            listState.scrollToItem(index, scrollOffset = -80)
        }
    }

    /**
     * 焦点移动时的“延迟预取”：避免焦点快速上下移动导致请求风暴
     */
    LaunchedEffect(show, pendingPrefetchAid) {
        if (!active) return@LaunchedEffect
        if (!show) return@LaunchedEffect
        val aid = pendingPrefetchAid ?: return@LaunchedEffect

        delay(200)

        if (pendingPrefetchAid == aid) {
            onEnsureUgcPagesLoaded(aid)
        }
    }

    AnimatedVisibility(
        visible = show,
        enter = expandHorizontally(),
        exit = shrinkHorizontally()
    ) {
        Surface(
            modifier = modifier,
            colors = SurfaceDefaults.colors(
                containerColor = C.scrim,
                contentColor = C.onScrim
            )
        ) {
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LazyColumn(
                    modifier = with(listRestorer) { Modifier.restorerHost() },
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 60.dp)
                ) {
                    items(
                        items = videoList,
                        key = { it.cid }
                    ) { video ->

                        val isParentSelected = video.cid == currentCid
                        val isSeasonEpisode = video.epid != null
                        val enableChildrenUi = !isSeasonEpisode

                        // 三态：null=未加载/失败，empty=已加载但单P，notEmpty=多P
                        val pages = if (enableChildrenUi) video.ugcPages else null
                        val pagesLoaded = pages != null
                        val hasSubPages = pages?.isNotEmpty() == true

                        val isCurrentParent = video.aid == currentAid
                        val isChildSelected = enableChildrenUi && (pages?.any { it.cid == currentCid } == true)
                        val isPinned = pinnedParent?.aid == video.aid || isChildSelected || isParentSelected

                        var expanded by remember(video.cid) { mutableStateOf(false) }
                        var didAutoExpand by remember(video.cid) { mutableStateOf(false) }

                        // pinnedParent：只自动展开一次（用于初次定位/初次进入当前组），之后可以手动收起
                        LaunchedEffect(show, isPinned) {
                            if (!active) return@LaunchedEffect
                            if (!show) return@LaunchedEffect
                            if (isPinned && !didAutoExpand) {
                                expanded = true
                                didAutoExpand = true
                            }
                        }

                        LaunchedEffect(pagesLoaded, hasSubPages) {
                            // 确认单P：不需要展开
                            if (pagesLoaded && !hasSubPages) expanded = false
                        }

                        // 组焦点跟踪（保留你原来的折叠策略）
                        var groupHasFocus by remember(video.cid) { mutableStateOf(false) }
                        var collapseToken by remember(video.cid) { mutableIntStateOf(0) }

                        fun scheduleCollapseIfNeeded() {
                            if (isPinned) return
                            collapseToken++
                        }

                        LaunchedEffect(collapseToken) {
                            if (collapseToken > 0) {
                                kotlinx.coroutines.android.awaitFrame()
                                if (!groupHasFocus && !isPinned) expanded = false
                            }
                        }

                        val parentBringIntoViewRequester = remember(video.cid) { androidx.compose.foundation.relocation.BringIntoViewRequester() }

                        Column(
                            modifier = Modifier.animateContentSize()
                        ) {
                            /**
                             * 关键改动：
                             * bringIntoView 不再跟随 onFocusChanged，而是只在“pendingFocusCid 消耗那次”触发。
                             *
                             * 同时做强硬兜底：
                             * 1) 先把对应父项滚到可见位置（按 index）
                             * 2) 再发起焦点定位
                             * 3) 再 bringIntoView
                             */
                            LaunchedEffect(show, pendingFocusCid) {
                                if (!active) return@LaunchedEffect
                                if (!show) return@LaunchedEffect

                                val wantCid = pendingFocusCid ?: return@LaunchedEffect

                                // 只有当“当前要聚焦的 cid”确实属于这个父项（父项自身 cid 或者它的子项 cid）才处理
                                val targetChildLoaded =
                                    enableChildrenUi && (video.ugcPages?.any { it.cid == wantCid } == true)
                                val targetChildOfCurrentParent =
                                    enableChildrenUi && isCurrentParent && video.cid != wantCid
                                val shouldHandleThisGroup =
                                    (video.cid == wantCid) || targetChildLoaded || targetChildOfCurrentParent

                                if (!shouldHandleThisGroup) return@LaunchedEffect

                                if (video.cid == wantCid) {
                                    val scopeId = focusScopeId ?: return@LaunchedEffect
                                    restoreListFocus(
                                        scopeId.resolve(videoListParentFocusLocalId(video.cid))
                                    )
                                    kotlinx.coroutines.android.awaitFrame()
                                    kotlinx.coroutines.coroutineScope {
                                        launch { parentBringIntoViewRequester.bringIntoView() }
                                    }
                                    pendingFocusCid = null
                                    return@LaunchedEffect
                                }

                                if (!targetChildLoaded) {
                                    pendingFocusCid = null
                                    return@LaunchedEffect
                                }

                                expanded = true
                            }

                            DenseListItem(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .wjzFocusExits(
                                        localId = videoListParentFocusLocalId(video.cid),
                                        layer = WjzFocusLayer.Player,
                                        onFocusChanged = { focused ->
                                            if (focused) {
                                        groupHasFocus = true
                                        pendingPrefetchAid = if (enableChildrenUi) video.aid else null

                                            // 仅当父项已经在屏幕外时，才瞬移滚动把它带回可视区。
                                            focusedParentCid = video.cid
                                            ensureParentVisibleJob?.cancel()
                                            ensureParentVisibleJob = scope.launch {
                                                if (!show) return@launch

                                                // 等一帧，确保 listState.layoutInfo 是稳定的
                                                awaitFrame()
                                                if (focusedParentCid != video.cid) return@launch

                                                val index = videoList.indexOfFirst { it.cid == video.cid }
                                                if (index < 0) return@launch

                                                val layout = listState.layoutInfo
                                                val viewportStart = layout.viewportStartOffset
                                                val viewportEnd = layout.viewportEndOffset

                                                val info = layout.visibleItemsInfo.firstOrNull { it.index == index }
                                                val itemStart = info?.offset
                                                val itemEnd = info?.let { it.offset + it.size }

                                                // visibleItemsInfo 里“有”不代表在屏幕内，必须用 offset/size 与 viewport 相交判断
                                                val actuallyVisible =
                                                    (itemStart != null && itemEnd != null && itemEnd > viewportStart && itemStart < viewportEnd)

                                                if (!actuallyVisible) {
                                                    listState.scrollToItem(index, scrollOffset = -80)
                                                    awaitFrame()

                                                    // 只在 off-screen 时才 bringIntoView，避免日常移动焦点时干扰滚动
                                                    if (focusedParentCid == video.cid) {
                                                        parentBringIntoViewRequester.bringIntoView()
                                                    }
                                                }
                                            }
                                            } else {
                                                groupHasFocus = false
                                                if (pendingPrefetchAid == video.aid) pendingPrefetchAid = null
                                                scheduleCollapseIfNeeded()

                                                if (focusedParentCid == video.cid) focusedParentCid = null
                                            }
                                        }
                                    )
                                    .bringIntoViewRequester(parentBringIntoViewRequester),
                                selected = isParentSelected && !isChildSelected,
                                onClick = {
                                    if (!enableChildrenUi) {
                                        if (!isCurrentParent) onPlayNewVideo(video)
                                        return@DenseListItem
                                    }

                                    if (pagesLoaded && !hasSubPages) {
                                        if (!isCurrentParent) onPlayNewVideo(video)
                                        return@DenseListItem
                                    }

                                    if (!pagesLoaded) {
                                        if (!isCurrentParent) {
                                            onPlayNewVideo(video)
                                        } else {
                                            expanded = !expanded
                                            if (expanded) onEnsureUgcPagesLoaded(video.aid)
                                        }
                                        return@DenseListItem
                                    }

                                    expanded = !expanded
                                },
                                headlineContent = {
                                    Text(
                                        text = video.title,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailingContent = {
                                    if (hasSubPages) {
                                        Icon(
                                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = AppWhite.copy(alpha = 0.7f)
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                    contentColor = AppWhite,
                                    selectedContainerColor = AppGray,
                                    selectedContentColor = AppBlack,
                                    focusedContainerColor = AppWhite,
                                    focusedContentColor = AppBlack,
                                    focusedSelectedContainerColor = AppWhite,
                                    focusedSelectedContentColor = AppBlack
                                )
                            )

                            // 子项未加载：仅在“当前父项 + expanded=true”时展示“加载中...”占位（不可聚焦/不可点击）
                            if (expanded && enableChildrenUi && !pagesLoaded && isCurrentParent) {
                                Column(
                                    modifier = Modifier
                                        .padding(start = 16.dp, top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    MenuListItem(
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .wjzDisabledFocus(),
                                        text = "加载中……",
                                        selected = false,
                                        textAlign = TextAlign.Start,
                                        onClick = {}
                                    )
                                }
                            }

                            // 分P子项（仅展开时显示）
                            if (expanded && hasSubPages) {
                                Column(
                                    modifier = Modifier
                                        .padding(start = 16.dp, top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    video.ugcPages?.forEach { page ->
                                        key(page.cid) {
                                            val isPageSelected = page.cid == currentCid

                                            val childBringIntoViewRequester = remember(page.cid) { androidx.compose.foundation.relocation.BringIntoViewRequester() }

                                            /**
                                             * 子项：只在 pendingFocusCid 指向它时请求焦点 + bringIntoView（并消耗 pendingFocusCid）
                                             * 这样 bringIntoView 只发生一次，不会在上下移动焦点时干扰滚动。
                                             */
                                            LaunchedEffect(show, pendingFocusCid, expanded, pagesLoaded) {
                                                if (!active) return@LaunchedEffect
                                                if (!show) return@LaunchedEffect
                                                if (!expanded) return@LaunchedEffect
                                                if (!pagesLoaded) return@LaunchedEffect

                                                val wantCid = pendingFocusCid ?: return@LaunchedEffect
                                                if (wantCid != page.cid) return@LaunchedEffect

                                                val scopeId = focusScopeId ?: return@LaunchedEffect
                                                restoreListFocus(
                                                    scopeId.resolve(
                                                        videoListChildFocusLocalId(video.cid, page.cid)
                                                    )
                                                )
                                                kotlinx.coroutines.android.awaitFrame()

                                                kotlinx.coroutines.coroutineScope {
                                                    launch { childBringIntoViewRequester.bringIntoView() }
                                                }

                                                pendingFocusCid = null
                                            }

                                            MenuListItem(
                                                modifier = Modifier
                                                    .padding(horizontal = 16.dp)
                                                    .wjzFocusExits(
                                                        localId = videoListChildFocusLocalId(video.cid, page.cid),
                                                        layer = WjzFocusLayer.Player,
                                                        onFocusChanged = { focused ->
                                                            if (focused) groupHasFocus = true
                                                        }
                                                    )
                                                    .bringIntoViewRequester(childBringIntoViewRequester),
                                                text = page.title,
                                                selected = isPageSelected,
                                                textAlign = TextAlign.Start,
                                            ) {
                                                if (!isPageSelected) {
                                                    onPlayNewVideo(video.copy(cid = page.cid))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                        }
                    }
                    }
                }
            }
        }
    }

@Composable
fun MenuListItem(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    textAlign: TextAlign = TextAlign.Center,
    onClick: () -> Unit
) {
    DenseListItem(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        headlineContent = {
            Text(
                text = text,
                textAlign = textAlign
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = AppWhite,
            selectedContainerColor = AppGray,
            selectedContentColor = AppBlack,
            focusedContainerColor = AppWhite,
            focusedContentColor = AppBlack,
            focusedSelectedContainerColor = AppWhite,
            focusedSelectedContentColor = AppBlack
        )
    )
}
