package dev.aaa1115910.bv.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.wjzfocus.WjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.WjzFocusDefaultTarget
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntrySurface
import dev.aaa1115910.bv.wjzfocus.WjzFocusExitsBuilder
import dev.aaa1115910.bv.wjzfocus.WjzFocusItemKey
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusRestorableTarget
import dev.aaa1115910.bv.wjzfocus.WjzFocusRequestResult
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusSubmitIntent
import dev.aaa1115910.bv.wjzfocus.WjzFocusTargetEntry
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusTopologyRegionRef
import dev.aaa1115910.bv.wjzfocus.WjzFocusHostExits
import dev.aaa1115910.bv.wjzfocus.down
import dev.aaa1115910.bv.wjzfocus.isFocusedBy
import dev.aaa1115910.bv.wjzfocus.left
import dev.aaa1115910.bv.wjzfocus.right
import dev.aaa1115910.bv.wjzfocus.resolve
import dev.aaa1115910.bv.wjzfocus.up
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.wjzfocus.wjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.wjzFocusRememberTopologyRegion
import dev.aaa1115910.bv.wjzfocus.wjzFocusRestorerHost
import dev.aaa1115910.bv.wjzfocus.wjzFocusSingleListRestorerComponent
import dev.aaa1115910.bv.wjzfocus.wjzLazyFocusSingleListRestorerComponent

private fun gridEntryComponentIdFor(nodeIdPrefix: String): String {
    val sanitized = nodeIdPrefix
        .map { char ->
            when (char) {
                '/', '\\', '|' -> '-'
                else -> char
            }
        }
        .joinToString("")
        .ifBlank { "grid" }
    return "tv-grid-$sanitized-${nodeIdPrefix.hashCode()}"
}

class BvLazyFocusItemTarget(
    val modifier: Modifier,
    private val nodeId: WjzFocusNodeId,
    private val localId: WjzFocusLocalId,
    val layer: WjzFocusLayer,
    val scopeId: WjzFocusScopeId?,
    val itemKey: WjzFocusItemKey,
    val onFocusChanged: (Boolean) -> Unit = {},
    private val restoreTarget: WjzFocusRestorableTarget
) {
    val key: String
        get() = nodeId.value

    fun activate(coordinator: WjzFocusCoordinator): Boolean {
        val target = WjzFocusDefaultTarget(
            nodeId = nodeId,
            layer = layer,
            scopeId = scopeId
        )
        if (target.isFocusedBy(coordinator)) return true
        return coordinator.submitNodeFocusIntent(
            nodeId = nodeId,
            layer = layer,
            scopeId = scopeId,
            intent = WjzFocusSubmitIntent.ExternalEntry(
                dedupeKey = "activate:$key",
                enqueueUntilLayerActive = false,
                enqueueIfMissing = false
            )
        ) == WjzFocusRequestResult.Focused
    }

    fun restoreFocus(coordinator: WjzFocusCoordinator) {
        restoreTarget.restoreFocus(coordinator)
    }

    @Composable
    fun focusableModifier(
        enabled: Boolean = true,
        onFocusChanged: (Boolean) -> Unit = {}
    ): Modifier {
        return Modifier.wjzFocusExits(
            localId = localId,
            layer = layer,
            enabled = enabled,
            onFocusChanged = { hasFocus ->
                this.onFocusChanged(hasFocus)
                onFocusChanged(hasFocus)
            }
        )
    }
}

@Composable
fun Modifier.wjzLazyFocusItemTarget(
    target: BvLazyFocusItemTarget,
    enabled: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {}
): Modifier {
    return this.then(
        target.focusableModifier(
            enabled = enabled,
            onFocusChanged = onFocusChanged
        )
    )
}

private class TvGridFocusController(
    private val columnCount: Int
) {
    var lastFocusedIndex by mutableIntStateOf(0)
    private var lastFocusedKey by mutableStateOf<WjzFocusItemKey?>(null)
    private var pendingRestoreKey by mutableStateOf<WjzFocusItemKey?>(null)
    private var pendingEmptyRestoreVersion by mutableIntStateOf(0)
    var enabled: Boolean = true
    var itemCount: Int = 0
    var itemKeys: List<WjzFocusItemKey> = emptyList()
    var nodeIdPrefix: String = "tv-grid"
    var focusLayer: WjzFocusLayer = WjzFocusLayer.Content
    var focusScopeId: WjzFocusScopeId? = null
    var enableHorizontalLinks: Boolean = true
    var entryComponentId: String = "tv-grid"
    var onEntryFocusReady: (() -> Unit)? = null
    var gridState: LazyGridState? = null
    var focusCoordinator: WjzFocusCoordinator? = null

    private fun itemKeyFor(index: Int): WjzFocusItemKey {
        return itemKeys[index]
    }

    private fun visibleItemKeyFor(index: Int): WjzFocusItemKey? {
        return itemKeys.getOrNull(index)
    }

    private fun resolvedScopeId(): WjzFocusScopeId {
        return focusScopeId ?: WjzFocusScopeId(nodeIdPrefix)
    }

    private fun itemLocalIdFor(index: Int): WjzFocusLocalId {
        return wjzFocusLocalId("item", itemKeyFor(index).value)
    }

    private fun nodeIdFor(index: Int): WjzFocusNodeId {
        return resolvedScopeId().resolve(itemLocalIdFor(index))
    }

    fun emptyLocalId(): WjzFocusLocalId {
        return wjzFocusLocalId("empty")
    }

    fun emptyNodeId(): WjzFocusNodeId {
        return resolvedScopeId().resolve(emptyLocalId())
    }

    private fun itemEntryIdFor(index: Int): String {
        return "item-$index"
    }

    private fun itemEntryPathFor(entryId: String): String {
        return "$entryComponentId/$entryId"
    }

    private fun lazyRestorerId(): String {
        return "$nodeIdPrefix/lazy-restorer"
    }

    fun lazyRestorerComponent() =
        wjzLazyFocusSingleListRestorerComponent(
            componentId = lazyRestorerId(),
            layer = focusLayer,
            scopeId = focusScopeId
        )

    fun emptyRestorerComponent() =
        wjzFocusSingleListRestorerComponent(
            componentId = "$nodeIdPrefix/empty-restorer",
            layer = focusLayer,
            scopeId = focusScopeId
        )

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

    private fun itemFocusTargetFor(index: Int): WjzFocusDefaultTarget {
        return WjzFocusDefaultTarget(
            nodeId = nodeIdFor(index),
            layer = focusLayer,
            scopeId = focusScopeId
        )
    }

    fun hasEntryTargets(): Boolean {
        return enabled && itemCount > 0 && columnCount > 0
    }

    fun defaultEntryTarget(): WjzFocusDefaultTarget {
        if (!hasEntryTargets()) {
            return WjzFocusDefaultTarget(
                nodeId = emptyNodeId(),
                layer = focusLayer,
                scopeId = focusScopeId
            )
        }

        val index = lastFocusedIndex.coerceIn(0, itemCount - 1)
        return WjzFocusDefaultTarget(
            nodeId = nodeIdFor(index),
            layer = focusLayer,
            scopeId = focusScopeId
        )
    }

    fun entryTargets(): List<WjzFocusTargetEntry> {
        if (!hasEntryTargets()) return emptyList()
        return (0 until itemCount).map { index -> itemEntryTargetFor(index) }
    }

    private fun entryRows(): List<List<WjzFocusTargetEntry>> {
        if (!hasEntryTargets()) return emptyList()
        return (0 until itemCount)
            .chunked(columnCount)
            .map { row -> row.map { index -> itemEntryTargetFor(index) } }
    }

    private fun readyEntryTargetFor(
        index: Int,
        direction: FocusDirection
    ): WjzFocusTargetEntry? {
        if (!hasEntryTargets() || index !in 0 until itemCount) return null

        val rowIndex = index / columnCount
        val columnIndex = index % columnCount
        val rowStart = rowIndex * columnCount
        val rowSize = (itemCount - rowStart).coerceAtMost(columnCount)
        val targetIndex = when (direction) {
            FocusDirection.Left -> when {
                columnIndex > 0 -> index - 1
                enableHorizontalLinks && rowSize > 0 -> rowStart + rowSize - 1
                else -> index
            }
            FocusDirection.Right -> when {
                columnIndex < rowSize - 1 -> index + 1
                enableHorizontalLinks && rowSize > 0 -> rowStart
                else -> index
            }
            FocusDirection.Up -> (index - columnCount).takeIf { it >= 0 }
            FocusDirection.Down -> (index + columnCount).takeIf { it < itemCount }
            else -> null
        }
        return targetIndex?.let { itemEntryTargetFor(it) }
    }

    private fun WjzFocusExitsBuilder.applyItemExits(index: Int) {
        val leftTarget = readyEntryTargetFor(index, FocusDirection.Left)
        if (leftTarget != null) {
            left move itemEntryPathFor(leftTarget.id)
        } else {
            cancel(left)
        }

        val rightTarget = readyEntryTargetFor(index, FocusDirection.Right)
        if (rightTarget != null) {
            right move itemEntryPathFor(rightTarget.id)
        } else {
            cancel(right)
        }

        val upTarget = readyEntryTargetFor(index, FocusDirection.Up)
        if (upTarget != null) {
            up move itemEntryPathFor(upTarget.id)
        } else {
            cancel(up)
        }

        val downTarget = readyEntryTargetFor(index, FocusDirection.Down)
        if (downTarget != null) {
            down move itemEntryPathFor(downTarget.id)
        } else {
            cancel(down)
        }
    }

    suspend fun scrollToKey(key: WjzFocusItemKey) {
        val index = indexOfKey(key) ?: return
        val state = gridState ?: return
        state.scrollToItem(index)
        withFrameNanos { }
    }

    fun isKeyVisible(key: WjzFocusItemKey): Boolean {
        val state = gridState ?: return false
        return state.layoutInfo.visibleItemsInfo.any { item ->
            visibleItemKeyFor(item.index)?.let { it == key } == true
        }
    }

    fun updateItems(
        itemCount: Int,
        itemKeys: List<WjzFocusItemKey>
    ) {
        val previousIndex = lastFocusedIndex
        val previousKey = lastFocusedKey
        val hadFocusedItem = this.itemCount > 0 &&
                previousIndex in this.itemKeys.indices &&
                focusCoordinator?.let { coordinator ->
                    itemFocusTargetFor(previousIndex).isFocusedBy(coordinator)
                } == true
        val changed = this.itemCount != itemCount || this.itemKeys != itemKeys

        this.itemCount = itemCount
        this.itemKeys = itemKeys

        if (!changed) return
        if (itemCount <= 0) {
            lastFocusedIndex = 0
            lastFocusedKey = null
            pendingRestoreKey = null
            if (hadFocusedItem) {
                pendingEmptyRestoreVersion += 1
            }
            return
        }

        val restoredIndex = previousKey?.let { indexOfKey(it) }
            ?: previousIndex.coerceIn(0, itemCount - 1)
        lastFocusedIndex = restoredIndex
        val restoredKey = visibleItemKeyFor(restoredIndex)
        lastFocusedKey = restoredKey
        if (hadFocusedItem) {
            pendingRestoreKey = restoredKey
        }
    }

    fun pendingRestoreKey(): WjzFocusItemKey? {
        return pendingRestoreKey
    }

    fun pendingEmptyRestoreVersion(): Int {
        return pendingEmptyRestoreVersion
    }

    fun restorePendingEmpty(version: Int) {
        if (version <= 0 || pendingEmptyRestoreVersion != version) return

        focusCoordinator?.let { coordinator ->
            emptyRestorerComponent()
                .target(nodeId = emptyNodeId())
                .restoreFocus(coordinator)
        }
        if (pendingEmptyRestoreVersion == version) {
            pendingEmptyRestoreVersion = 0
        }
    }

    fun restorePendingKey(key: WjzFocusItemKey) {
        val index = indexOfKey(key) ?: run {
            if (pendingRestoreKey == key) {
                pendingRestoreKey = null
            }
            return
        }
        focusCoordinator?.let { coordinator ->
            lazyRestorerComponent()
                .target(
                    nodeId = nodeIdFor(index),
                    itemKey = key
                )
                .restoreFocus(coordinator)
        }
        if (pendingRestoreKey == key) {
            pendingRestoreKey = null
        }
    }

    @Composable
    fun Modifier.modifierFor(index: Int): Modifier {
        return targetFor(index)?.let { this.then(it.modifier) } ?: this
    }

    @Composable
    fun targetFor(index: Int): BvLazyFocusItemTarget? {
        if (!enabled || itemCount <= 0 || index < 0 || index >= itemCount) return null

        val localId = itemLocalIdFor(index)
        val nodeId = nodeIdFor(index)
        val scopeId = resolvedScopeId()
        val itemKey = itemKeyFor(index)
        val restoreTarget = lazyRestorerComponent().target(
            nodeId = nodeId,
            itemKey = itemKey
        )
        val itemFocusChanged: (Boolean) -> Unit = { hasFocus ->
            if (hasFocus) {
                lastFocusedIndex = index
                lastFocusedKey = visibleItemKeyFor(index)
            }
        }

        var modifier = Modifier
            .wjzFocusExits(
                localId = localId,
                layer = focusLayer,
                exits = {
                    applyItemExits(index)
                },
                onFocusChanged = itemFocusChanged
            )
        if (index == 0) {
            modifier = modifier.onGloballyPositioned {
                onEntryFocusReady?.invoke()
            }
        }
        return BvLazyFocusItemTarget(
            modifier = modifier,
            nodeId = nodeId,
            localId = localId,
            layer = focusLayer,
            scopeId = scopeId,
            itemKey = itemKey,
            onFocusChanged = itemFocusChanged,
            restoreTarget = restoreTarget
        )
    }
}

private val LocalTvGridFocusController =
    compositionLocalOf<TvGridFocusController?> { null }

@Composable
fun rememberTvGridFocusModifier(index: Int): Modifier {
    return rememberTvGridFocusTarget(index)?.modifier ?: Modifier
}

@Composable
fun rememberTvGridFocusTarget(index: Int): BvLazyFocusItemTarget? {
    val controller = LocalTvGridFocusController.current ?: return null
    return controller.targetFor(index)
}

@Composable
fun TvGridFocusHost(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
    nodeIdPrefix: String,
    itemKeys: List<WjzFocusItemKey>,
    focusLayer: WjzFocusLayer = WjzFocusLayer.Content,
    focusScopeId: WjzFocusScopeId? = null,
    enableRowHorizontalWrap: Boolean = true,
    onEntryFocusReady: (() -> Unit)? = null,
    focusItemCount: Int = 0,
    focusColumnCount: Int = 4,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    content: LazyGridScope.() -> Unit
) {
    val focusCoordinator = LocalWjzFocusCoordinator.current
    val topology = wjzFocusRememberTopologyRegion(topologyRegion)
    val resolvedFocusScopeId = remember(nodeIdPrefix, focusScopeId) {
        focusScopeId ?: WjzFocusScopeId(nodeIdPrefix)
    }
    require(focusItemCount == 0 || itemKeys.size == focusItemCount) {
        "itemKeys size must match focusItemCount"
    }
    val focusController = remember(focusColumnCount) {
        TvGridFocusController(focusColumnCount)
    }.apply {
        enabled = focusItemCount > 0 && focusColumnCount > 0
        this.nodeIdPrefix = nodeIdPrefix
        this.focusLayer = focusLayer
        this.focusScopeId = resolvedFocusScopeId
        this.enableHorizontalLinks = enableRowHorizontalWrap
        this.entryComponentId = gridEntryComponentIdFor(nodeIdPrefix)
        this.onEntryFocusReady = onEntryFocusReady
        this.gridState = state
        this.focusCoordinator = focusCoordinator
        updateItems(
            itemCount = focusItemCount,
            itemKeys = itemKeys
        )
    }
    LaunchedEffect(focusController.pendingRestoreKey()) {
        focusController.pendingRestoreKey()?.let { key ->
            focusController.restorePendingKey(key)
        }
    }
    LaunchedEffect(focusController.pendingEmptyRestoreVersion()) {
        focusController.restorePendingEmpty(focusController.pendingEmptyRestoreVersion())
    }

    CompositionLocalProvider(
        LocalTvGridFocusController provides focusController,
        LocalWjzFocusScopeId provides resolvedFocusScopeId
    ) {
        val gridModifier = if (focusItemCount <= 0) {
            modifier
                .wjzFocusRestorerHost(
                    target = focusController.emptyRestorerComponent()
                        .target(nodeId = focusController.emptyNodeId())
                )
                .wjzFocusExits(
                    localId = focusController.emptyLocalId(),
                    layer = focusLayer
                )
                .onGloballyPositioned {
                    onEntryFocusReady?.invoke()
                }
        } else {
            modifier
        }

        focusController.lazyRestorerComponent().InstallRestorerHost(
            scrollToItem = { key -> focusController.scrollToKey(key) },
            isItemVisible = { key -> focusController.isKeyVisible(key) }
        )

        WjzFocusEntrySurface(
            componentId = focusController.entryComponentId,
            default = {
                topology.resolveInitialTarget(
                    componentId = focusController.entryComponentId,
                    targets = focusController.entryTargets()
                ) {
                    focusController.defaultEntryTarget()
                }
            },
            entries = {
                focusController.entryTargets().forEach { target ->
                    entry(target.id, target)
                }
            }
        )

        if (topology.isBound) {
            WjzFocusHostExits(
                token = requireNotNull(topology.hostExitToken("tv-grid-topology", nodeIdPrefix)),
                scopeId = resolvedFocusScopeId,
                exits = topology.hostExits
            )
        }

        TvLazyVerticalGrid(
            columns = columns,
            modifier = gridModifier,
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            content = content
        )
    }
}
