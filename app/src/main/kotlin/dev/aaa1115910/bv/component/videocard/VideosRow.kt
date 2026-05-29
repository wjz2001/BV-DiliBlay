package dev.aaa1115910.bv.component.videocard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusItemKey
import dev.aaa1115910.bv.component.BvLazyFocusItemTarget
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzLazyFocusRestorerHost
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.wjzFocus
import dev.aaa1115910.bv.wjzfocus.wjzFocusGroup
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.ui.theme.C
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

private class RowWrapController {
    var lastFocusedIndex by mutableIntStateOf(0)
    var lastFocusedKey by mutableStateOf<WjzFocusItemKey?>(null)
    var itemCount: Int = 0
    var itemKeys: List<WjzFocusItemKey> = emptyList()
    var nodeIdPrefix: String = "videos-row"
    var focusLayer: WjzFocusLayer = WjzFocusLayer.Content
    var focusScopeId: WjzFocusScopeId? = null
    var listState: LazyListState? = null
    var leadingSlotOffset: Int = 0
    var onItemFocusChanged: (WjzFocusItemKey, Boolean) -> Unit = { _, _ -> }

    private fun itemKeyFor(index: Int): WjzFocusItemKey {
        return itemKeys[index]
    }

    private fun itemFocusIdFor(index: Int): String {
        return "item/${itemKeyFor(index)}"
    }

    private fun nodeIdFor(index: Int): WjzFocusNodeId {
        val itemFocusId = itemFocusIdFor(index)
        return WjzFocusNodeId("${focusScopeId?.value ?: nodeIdPrefix}/$itemFocusId")
    }

    private fun indexOfKey(key: WjzFocusItemKey): Int? {
        return itemKeys.indexOf(key).takeIf { it >= 0 }
    }

    suspend fun scrollToKey(key: WjzFocusItemKey) {
        val index = indexOfKey(key) ?: return
        val state = listState ?: return
        state.scrollToItem(index + leadingSlotOffset)
    }

    fun isKeyVisible(key: WjzFocusItemKey): Boolean {
        val index = indexOfKey(key) ?: return false
        val state = listState ?: return false
        return state.layoutInfo.visibleItemsInfo.any { it.index == index + leadingSlotOffset }
    }

    fun itemKeyAt(index: Int): WjzFocusItemKey {
        return itemKeyFor(index)
    }

    fun nodeIdAt(index: Int): WjzFocusNodeId {
        return nodeIdFor(index)
    }

    fun restoreTargetKey(previousItemKeys: List<WjzFocusItemKey>): WjzFocusItemKey? {
        val focusedKey = lastFocusedKey ?: return null
        if (itemKeys.isEmpty()) return null
        if (focusedKey in itemKeys) return focusedKey

        val previousIndex = previousItemKeys.indexOf(focusedKey)
            .takeIf { it >= 0 }
            ?: lastFocusedIndex
        return itemKeys[previousIndex.coerceIn(0, itemKeys.lastIndex)]
    }

    @Composable
    fun Modifier.modifierFor(index: Int): Modifier {
        return targetFor(index)?.let { this.then(it.modifier) } ?: this
    }

    @Composable
    fun targetFor(index: Int): BvLazyFocusItemTarget? {
        if (index !in 0 until itemCount) return null

        val nodeId = nodeIdFor(index)
        val itemFocusId = itemFocusIdFor(index)
        val itemKey = itemKeyFor(index)
        val restorerId = "$nodeIdPrefix/lazy-restorer"
        val itemFocusChanged: (Boolean) -> Unit = { hasFocus ->
            if (hasFocus) {
                lastFocusedIndex = index
                lastFocusedKey = itemKey
            }
            onItemFocusChanged(itemKey, hasFocus)
        }

        val modifier = Modifier
            .wjzFocus(
                id = itemFocusId,
                layer = focusLayer,
                onFocusChanged = itemFocusChanged
            )
        return BvLazyFocusItemTarget(
            modifier = modifier,
            nodeId = nodeId,
            layer = focusLayer,
            scopeId = focusScopeId,
            itemKey = itemKey,
            restorerId = restorerId,
            listId = restorerId,
            onFocusChanged = itemFocusChanged
        )
    }
}

private fun videoCardKey(item: VideoCardData): WjzFocusItemKey {
    return WjzFocusItemKey("${item.avid}:${item.cid ?: -1L}:${item.epId ?: -1}")
}

@Composable
fun VideosRowCore(
    modifier: Modifier = Modifier,
    header: String,
    fontSize: TextUnit = 14.sp,
    focusedHeaderColor: Color? = null,
    unfocusedHeaderColor: Color? = null,
    videos: ImmutableList<VideoCardData>,
    onVideoClicked: (VideoCardData) -> Unit,
    onAddWatchLater: ((Long) -> Unit)? = null,
    onGoToUpPage: ((Long, String) -> Unit)? = null,
    enableHorizontalWrap: Boolean = true,
    rowStateKey: String,
    leadingItem: (@Composable (Modifier) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    manageRowFocusInternally: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val focusCoordinator = LocalWjzFocusCoordinator.current

    var focusedItemKey by remember(rowStateKey) {
        mutableStateOf<WjzFocusItemKey?>(null)
    }
    val hasFocus = focusedItemKey != null
    val titleColor = if (hasFocus) {
        focusedHeaderColor ?: C.onSurface
    } else {
        unfocusedHeaderColor ?: C.onSurfaceVariant
    }

    val rowFocusScopeId = remember(rowStateKey) {
        WjzFocusScopeId("videos-row/$rowStateKey")
    }
    val itemKeys = remember(videos) {
        videos.map { videoCardKey(it) }
    }

    val rowWrapController = remember { RowWrapController() }.apply {
        itemCount = videos.size
        this.itemKeys = itemKeys
        nodeIdPrefix = rowStateKey
        focusLayer = WjzFocusLayer.Content
        focusScopeId = rowFocusScopeId
        this.listState = listState
        this.leadingSlotOffset = if (leadingItem != null) 1 else 0
    }
    var shouldRestoreFocusedItem by remember(rowStateKey) {
        mutableStateOf(false)
    }
    rowWrapController.onItemFocusChanged = { itemKey, itemHasFocus ->
        if (itemHasFocus) {
            focusedItemKey = itemKey
            shouldRestoreFocusedItem = true
        } else if (itemKey in itemKeys) {
            if (focusedItemKey == itemKey) {
                focusedItemKey = null
            }
            shouldRestoreFocusedItem = false
        }
    }

    var previousItemKeys by remember(rowStateKey) {
        mutableStateOf<List<WjzFocusItemKey>>(emptyList())
    }

    LaunchedEffect(itemKeys) {
        if (previousItemKeys != itemKeys) {
            val targetKey = rowWrapController.restoreTargetKey(previousItemKeys)
            if (
                previousItemKeys.isNotEmpty() &&
                itemKeys.isNotEmpty() &&
                targetKey != null &&
                shouldRestoreFocusedItem
            ) {
                val targetIndex = itemKeys.indexOf(targetKey)
                if (targetIndex >= 0) {
                    focusCoordinator?.enqueueLazyRestore(
                        nodeId = rowWrapController.nodeIdAt(targetIndex),
                        itemKey = targetKey,
                        layer = WjzFocusLayer.Content,
                        scopeId = rowFocusScopeId,
                        restorerId = "$rowStateKey/lazy-restorer",
                        listId = "$rowStateKey/lazy-restorer"
                    )
                }
                shouldRestoreFocusedItem = false
            } else if (
                targetKey == null ||
                itemKeys.isEmpty() ||
                !hasFocus
            ) {
                shouldRestoreFocusedItem = false
            }
        }
        previousItemKeys = itemKeys
    }

    SmallVideoCardHostProviders(
        viewModelKey = rowStateKey,
        onNavigateUp = onGoToUpPage
    ) { cardUiStateFor ->
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = LocalDensity.current.density * 1.25f,
                fontScale = LocalDensity.current.fontScale * 1.25f
            )
        ) {
            var columnModifier = modifier
            if (manageRowFocusInternally) {
                columnModifier = columnModifier
                    .wjzFocusGroup()
            }

            Column(
                modifier = columnModifier
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 62.dp),
                    text = header,
                    fontSize = fontSize,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                WjzLazyFocusRestorerHost(
                    layer = WjzFocusLayer.Content,
                    scopeId = rowFocusScopeId,
                    restorerId = "$rowStateKey/lazy-restorer",
                    listId = "$rowStateKey/lazy-restorer",
                    scrollToItem = { key -> rowWrapController.scrollToKey(key) },
                    isItemVisible = { key -> rowWrapController.isKeyVisible(key) }
                )

                LazyRow(
                    state = listState,
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top,
                    contentPadding = PaddingValues(horizontal = 58.dp)
                ) {
                    if (leadingItem != null) {
                        item(key = "${rowStateKey}_leading_item") {
                            leadingItem(
                                Modifier
                                    .onPreviewKeyEvent { event ->
                                        if (
                                            event.type != KeyEventType.KeyDown ||
                                            event.key != Key.DirectionRight ||
                                            videos.isEmpty()
                                        ) {
                                            return@onPreviewKeyEvent false
                                        }
                                        scope.launch {
                                            val targetKey = rowWrapController.itemKeyAt(0)
                                            focusCoordinator?.enqueueLazyRestore(
                                                nodeId = rowWrapController.nodeIdAt(0),
                                                itemKey = targetKey,
                                                layer = WjzFocusLayer.Content,
                                                scopeId = rowFocusScopeId,
                                                restorerId = "$rowStateKey/lazy-restorer",
                                                listId = "$rowStateKey/lazy-restorer"
                                            )
                                        }
                                        true
                                    }
                            )
                        }
                    }

                    itemsIndexed(
                        items = videos,
                        key = { _, item -> videoCardKey(item).value }
                    ) { index, videoData ->
                        CompositionLocalProvider(LocalWjzFocusScopeId provides rowFocusScopeId) {
                            val lastIndex = videos.lastIndex
                            val focusTarget = rowWrapController.targetFor(index)?.let { target ->
                                target.copy(
                                    modifier = target.modifier
                                        .onPreviewKeyEvent { event ->
                                            if (!enableHorizontalWrap) {
                                                return@onPreviewKeyEvent false
                                            }
                                            if (videos.size <= 1) {
                                                return@onPreviewKeyEvent false
                                            }
                                            if (event.type != KeyEventType.KeyDown) {
                                                return@onPreviewKeyEvent false
                                            }

                                            when {
                                                // 没有 leadingItem 时，第一个视频按左，循环到最后一个视频
                                                event.key == Key.DirectionLeft &&
                                                        index == 0 &&
                                                        leadingItem == null -> {
                                                    scope.launch {
                                                        val targetKey = rowWrapController.itemKeyAt(lastIndex)
                                                        focusCoordinator?.enqueueLazyRestore(
                                                            nodeId = rowWrapController.nodeIdAt(lastIndex),
                                                            itemKey = targetKey,
                                                            layer = WjzFocusLayer.Content,
                                                            scopeId = rowFocusScopeId,
                                                            restorerId = "$rowStateKey/lazy-restorer",
                                                            listId = "$rowStateKey/lazy-restorer"
                                                        )
                                                    }
                                                    true
                                                }

                                                // 最后一个视频按右，循环到第一个视频
                                                event.key == Key.DirectionRight &&
                                                        index == lastIndex -> {
                                                    scope.launch {
                                                        val targetKey = rowWrapController.itemKeyAt(0)
                                                        focusCoordinator?.enqueueLazyRestore(
                                                            nodeId = rowWrapController.nodeIdAt(0),
                                                            itemKey = targetKey,
                                                            layer = WjzFocusLayer.Content,
                                                            scopeId = rowFocusScopeId,
                                                            restorerId = "$rowStateKey/lazy-restorer",
                                                            listId = "$rowStateKey/lazy-restorer"
                                                        )
                                                    }
                                                    true
                                                }

                                                else -> false
                                            }
                                        }
                                )
                            }

                            SmallVideoCard(
                                modifier = Modifier.width(200.dp),
                                focusTarget = focusTarget,
                                uiState = cardUiStateFor(videoData.avid),
                                data = videoData,
                                titleMaxLines = 1,
                                onClick = { onVideoClicked(videoData) },
                                coverDensityMultiplier = 1f,
                                coverFontScaleMultiplier = 1f,
                                infoDensityMultiplier = 1f,
                                infoFontScaleMultiplier = 1f,
                                onAddWatchLater = onAddWatchLater?.let { callback ->
                                    { callback(videoData.avid) }
                                },
                                onGoToUpPage = onGoToUpPage?.let { callback ->
                                    videoData.upMid?.let { mid ->
                                        { callback(mid, videoData.upName) }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideosRow(
    modifier: Modifier = Modifier,
    header: String,
    focusedHeaderColor: Color? = null,
    unfocusedHeaderColor: Color? = null,
    videos: ImmutableList<VideoCardData>,
    onVideoClicked: (VideoCardData) -> Unit,
    onAddWatchLater: ((Long) -> Unit)? = null,
    onGoToUpPage: ((Long, String) -> Unit)? = null,
    enableHorizontalWrap: Boolean = true,
    rowStateKey: String? = null,
    manageRowFocusInternally: Boolean = true,
) {
    val resolvedRowStateKey = rowStateKey ?: remember(header, videos) {
        val firstAid = videos.firstOrNull()?.avid ?: 0L
        "VideosRow:$header:$firstAid:${videos.size}"
    }

    VideosRowCore(
        modifier = modifier,
        header = header,
        focusedHeaderColor = focusedHeaderColor,
        unfocusedHeaderColor = unfocusedHeaderColor,
        videos = videos,
        onVideoClicked = onVideoClicked,
        onAddWatchLater = onAddWatchLater,
        onGoToUpPage = onGoToUpPage,
        enableHorizontalWrap = enableHorizontalWrap,
        rowStateKey = resolvedRowStateKey,
        leadingItem = null,
        manageRowFocusInternally = manageRowFocusInternally
    )
}
