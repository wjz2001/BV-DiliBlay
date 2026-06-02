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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.aaa1115910.bv.wjzfocus.WjzFocusDefaultTarget
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntrySurface
import dev.aaa1115910.bv.wjzfocus.WjzFocusExitsBuilder
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusItemKey
import dev.aaa1115910.bv.wjzfocus.WjzFocusLocalEntryId
import dev.aaa1115910.bv.component.BvLazyFocusItemTarget
import dev.aaa1115910.bv.wjzfocus.WjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.WjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusTargetEntry
import dev.aaa1115910.bv.wjzfocus.WjzLazyFocusRestorerHost
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.left
import dev.aaa1115910.bv.wjzfocus.resolve
import dev.aaa1115910.bv.wjzfocus.right
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.wjzfocus.wjzFocusGroup
import dev.aaa1115910.bv.wjzfocus.wjzFocusLocalId
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.ui.theme.C
import kotlinx.collections.immutable.ImmutableList

private fun videosRowEntryComponentIdFor(nodeIdPrefix: String): String {
    val sanitized = nodeIdPrefix
        .map { char ->
            when (char) {
                '/', '\\', '|' -> '-'
                else -> char
            }
        }
        .joinToString("")
        .ifBlank { "videos-row" }
    return "videos-row-$sanitized-${nodeIdPrefix.hashCode()}"
}

private class RowWrapController {
    var lastFocusedIndex by mutableIntStateOf(0)
    var lastFocusedKey by mutableStateOf<WjzFocusItemKey?>(null)
    var itemCount: Int = 0
    var itemKeys: List<WjzFocusItemKey> = emptyList()
    var nodeIdPrefix: String = "videos-row"
    var entryComponentId: String = "videos-row"
    var focusLayer: WjzFocusLayer = WjzFocusLayer.Content
    var focusScopeId: WjzFocusScopeId? = null
    var listState: LazyListState? = null
    var focusCoordinator: WjzFocusCoordinator? = null
    var leadingSlotOffset: Int = 0
    var enableHorizontalWrap: Boolean = true
    var onItemFocusChanged: (WjzFocusItemKey, Boolean) -> Unit = { _, _ -> }

    private fun itemKeyFor(index: Int): WjzFocusItemKey {
        return itemKeys[index]
    }

    private fun itemEntryIdFor(index: Int): String {
        return "item-${itemKeyFor(index).value}"
    }

    private fun itemEntryPathFor(index: Int): String {
        return "$entryComponentId/${itemEntryIdFor(index)}"
    }

    private fun itemLocalIdFor(index: Int): WjzFocusLocalId {
        return wjzFocusLocalId("item", itemKeyFor(index).value)
    }

    private fun resolveNodeId(localId: WjzFocusLocalId): WjzFocusNodeId {
        val scopeId = requireNotNull(focusScopeId) {
            "VideosRow focusScopeId is required to resolve local id '${localId.value}'"
        }
        return scopeId.resolve(localId)
    }

    private fun nodeIdFor(index: Int): WjzFocusNodeId {
        return resolveNodeId(itemLocalIdFor(index))
    }

    private fun leadingLocalId(): WjzFocusLocalId {
        return wjzFocusLocalId("leading")
    }

    private fun leadingNodeId(): WjzFocusNodeId {
        return resolveNodeId(leadingLocalId())
    }

    fun leadingEntryId(): WjzFocusLocalEntryId {
        return WjzFocusLocalEntryId("leading")
    }

    private fun leadingEntryPath(): String {
        return "$entryComponentId/${leadingEntryId().value}"
    }

    private fun lazyRestorerId(): String {
        return "$nodeIdPrefix/lazy-restorer"
    }

    private fun indexOfKey(key: WjzFocusItemKey): Int? {
        return itemKeys.indexOf(key).takeIf { it >= 0 }
    }

    private fun itemEntryTargetFor(index: Int): WjzFocusTargetEntry {
        return WjzFocusTargetEntry(
            id = itemEntryIdFor(index),
            nodeId = nodeIdFor(index),
            layer = focusLayer,
            scopeId = focusScopeId
        )
    }

    private fun adjacentEntryPathFor(
        index: Int,
        direction: FocusDirection
    ): String? {
        val targetIndex = when (direction) {
            FocusDirection.Left -> when {
                index > 0 -> index - 1
                enableHorizontalWrap && itemCount > 1 -> itemCount - 1
                else -> null
            }
            FocusDirection.Right -> when {
                index < itemCount - 1 -> index + 1
                enableHorizontalWrap && itemCount > 1 -> 0
                else -> null
            }
            else -> null
        }
        return targetIndex?.let { itemEntryPathFor(it) }
    }

    private fun WjzFocusExitsBuilder.applyItemExits(index: Int) {
        val leftTarget = if (index == 0 && leadingSlotOffset > 0) {
            leadingEntryPath()
        } else {
            adjacentEntryPathFor(index, FocusDirection.Left)
        }
        if (leftTarget != null) {
            left move leftTarget
        } else {
            cancel(left)
        }

        val rightTarget = adjacentEntryPathFor(index, FocusDirection.Right)
        if (rightTarget != null) {
            right move rightTarget
        } else {
            cancel(right)
        }
    }

    fun syncLinearFocusController() {
    }

    fun hasEntryTargets(): Boolean {
        return itemCount > 0
    }

    fun defaultEntryTarget(): WjzFocusDefaultTarget {
        val index = lastFocusedIndex.coerceIn(0, itemCount - 1)
        return WjzFocusDefaultTarget(
            nodeId = nodeIdFor(index),
            layer = focusLayer,
            scopeId = focusScopeId
        )
    }

    fun leadingEntryTarget(): WjzFocusDefaultTarget {
        return WjzFocusDefaultTarget(
            nodeId = leadingNodeId(),
            layer = focusLayer,
            scopeId = focusScopeId
        )
    }

    fun itemEntryTargets(): List<WjzFocusTargetEntry> {
        return (0 until itemCount).map { index -> itemEntryTargetFor(index) }
    }

    fun resolveItemEntryTarget(index: Int): WjzFocusTargetEntry? {
        if (index !in 0 until itemCount) return null
        val target = itemEntryTargetFor(index)
        val itemKey = itemKeyFor(index)
        if (!isKeyVisible(itemKey)) {
            focusCoordinator?.enqueueLazyRestore(
                nodeId = target.nodeId,
                itemKey = itemKey,
                layer = target.layer,
                scopeId = target.scopeId,
                restorerId = lazyRestorerId(),
                listId = lazyRestorerId()
            )
        }
        return target
    }

    fun firstItemEntryPath(): String? {
        if (itemCount <= 0) return null
        return itemEntryPathFor(0)
    }

    @Composable
    fun leadingModifier(
        onFocusChanged: (Boolean) -> Unit
    ): Modifier {
        return Modifier.wjzFocusExits(
            localId = leadingLocalId(),
            layer = focusLayer,
            exits = {
                val firstItemEntryPath = firstItemEntryPath()
                if (firstItemEntryPath != null) {
                    right move firstItemEntryPath
                } else {
                    cancel(right)
                }
                cancel(left)
            },
            onFocusChanged = onFocusChanged
        )
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

    fun restoreFocusToKey(key: WjzFocusItemKey): Boolean {
        val index = indexOfKey(key) ?: return false
        return restoreFocusToIndex(index)
    }

    private fun restoreFocusToIndex(index: Int): Boolean {
        if (index !in 0 until itemCount) return false
        val targetKey = itemKeyFor(index)
        focusCoordinator?.enqueueLazyRestore(
            nodeId = nodeIdFor(index),
            itemKey = targetKey,
            layer = focusLayer,
            scopeId = focusScopeId,
            restorerId = lazyRestorerId(),
            listId = lazyRestorerId()
        )
        return true
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
        val itemLocalId = itemLocalIdFor(index)
        val itemKey = itemKeyFor(index)
        val restorerId = lazyRestorerId()
        val itemFocusChanged: (Boolean) -> Unit = { hasFocus ->
            if (hasFocus) {
                lastFocusedIndex = index
                lastFocusedKey = itemKey
            }
            onItemFocusChanged(itemKey, hasFocus)
        }

        val modifier = Modifier
            .wjzFocusExits(
                localId = itemLocalId,
                layer = focusLayer,
                exits = {
                    applyItemExits(index)
                },
                onFocusChanged = itemFocusChanged
            )
        return BvLazyFocusItemTarget(
            modifier = modifier,
            nodeId = nodeId,
            localId = itemLocalId,
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
    val focusCoordinator = LocalWjzFocusCoordinator.current

    var focusedItemKey by remember(rowStateKey) {
        mutableStateOf<WjzFocusItemKey?>(null)
    }
    var leadingItemHasFocus by remember(rowStateKey) {
        mutableStateOf(false)
    }
    val hasFocus = focusedItemKey != null || leadingItemHasFocus
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
        entryComponentId = videosRowEntryComponentIdFor(rowStateKey)
        focusLayer = WjzFocusLayer.Content
        focusScopeId = rowFocusScopeId
        this.listState = listState
        this.focusCoordinator = focusCoordinator
        this.leadingSlotOffset = if (leadingItem != null) 1 else 0
        this.enableHorizontalWrap = enableHorizontalWrap
        syncLinearFocusController()
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
                rowWrapController.restoreFocusToKey(targetKey)
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

                if (rowWrapController.hasEntryTargets()) {
                    WjzFocusEntrySurface(
                        componentId = rowWrapController.entryComponentId,
                        default = { rowWrapController.defaultEntryTarget() },
                        entries = {
                            if (leadingItem != null) {
                                val leadingTarget = rowWrapController.leadingEntryTarget()
                                entry(rowWrapController.leadingEntryId()) move leadingTarget
                            }
                            rowWrapController.itemEntryTargets().forEachIndexed { index, target ->
                                entry(target.id) {
                                    rowWrapController.resolveItemEntryTarget(index)
                                }
                            }
                        }
                    )
                }

                LazyRow(
                    state = listState,
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top,
                    contentPadding = PaddingValues(horizontal = 58.dp)
                ) {
                    if (leadingItem != null) {
                        item(key = "${rowStateKey}_leading_item") {
                            CompositionLocalProvider(LocalWjzFocusScopeId provides rowFocusScopeId) {
                                leadingItem(
                                    rowWrapController.leadingModifier { hasFocus ->
                                        leadingItemHasFocus = hasFocus
                                    }
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = videos,
                        key = { _, item -> videoCardKey(item).value }
                    ) { index, videoData ->
                        CompositionLocalProvider(LocalWjzFocusScopeId provides rowFocusScopeId) {
                            val focusTarget = rowWrapController.targetFor(index)

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
