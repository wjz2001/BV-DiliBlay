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
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusTargetEntry
import dev.aaa1115910.bv.wjzfocus.WjzFocusTargetResolution
import dev.aaa1115910.bv.wjzfocus.WjzGridFocusController
import dev.aaa1115910.bv.wjzfocus.WjzLazyFocusRestorerHost
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.down
import dev.aaa1115910.bv.wjzfocus.left
import dev.aaa1115910.bv.wjzfocus.right
import dev.aaa1115910.bv.wjzfocus.up
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.wjzfocus.wjzFocusRestorerHost

private fun WjzFocusNodeId.localIdIn(scopeId: WjzFocusScopeId?): String {
    val scopePrefix = scopeId?.value?.let { "$it/" } ?: return value
    return value.removePrefix(scopePrefix)
}

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
    val layer: WjzFocusLayer,
    val scopeId: WjzFocusScopeId?,
    val itemKey: WjzFocusItemKey,
    val restorerId: String,
    val listId: String,
    val onFocusChanged: (Boolean) -> Unit = {}
) {
    val key: String
        get() = nodeId.value

    fun copy(
        modifier: Modifier = this.modifier,
        layer: WjzFocusLayer = this.layer,
        scopeId: WjzFocusScopeId? = this.scopeId,
        itemKey: WjzFocusItemKey = this.itemKey,
        restorerId: String = this.restorerId,
        listId: String = this.listId,
        onFocusChanged: (Boolean) -> Unit = this.onFocusChanged
    ): BvLazyFocusItemTarget {
        return BvLazyFocusItemTarget(
            modifier = modifier,
            nodeId = nodeId,
            layer = layer,
            scopeId = scopeId,
            itemKey = itemKey,
            restorerId = restorerId,
            listId = listId,
            onFocusChanged = onFocusChanged
        )
    }

    fun activate(
        coordinator: WjzFocusCoordinator,
        enqueueIfMissing: Boolean = true
    ): Boolean {
        coordinator.enqueueLazyRestore(
            nodeId = nodeId,
            itemKey = itemKey,
            layer = layer,
            scopeId = scopeId,
            restorerId = restorerId,
            listId = listId
        )
        return coordinator.hasFocus(nodeId)
    }

    fun enqueueLazyRestore(coordinator: WjzFocusCoordinator) {
        coordinator.enqueueLazyRestore(
            nodeId = nodeId,
            itemKey = itemKey,
            layer = layer,
            scopeId = scopeId,
            restorerId = restorerId,
            listId = listId
        )
    }

    @Composable
    fun focusableModifier(
        enabled: Boolean = true,
        onFocusChanged: (Boolean) -> Unit = {}
    ): Modifier {
        val localId = nodeId.localIdIn(scopeId)
        return Modifier.wjzFocusExits(
            id = localId,
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
    return target.focusableModifier(
        enabled = enabled,
        onFocusChanged = onFocusChanged
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
    private var gridControllerHorizontalWrap: Boolean? = null
    private var gridFocusController = WjzGridFocusController()

    private fun itemKeyFor(index: Int): WjzFocusItemKey {
        return itemKeys[index]
    }

    private fun visibleItemKeyFor(index: Int): WjzFocusItemKey? {
        return itemKeys.getOrNull(index)
    }

    private fun nodeIdFor(index: Int): WjzFocusNodeId {
        return WjzFocusNodeId("$nodeIdPrefix/item/${itemKeyFor(index).value}")
    }

    private fun emptyNodeId(): WjzFocusNodeId {
        return WjzFocusNodeId("$nodeIdPrefix/empty")
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

    private fun syncGridFocusController() {
        if (gridControllerHorizontalWrap != enableHorizontalLinks) {
            gridFocusController = WjzGridFocusController(
                horizontalWrap = enableHorizontalLinks
            )
            gridControllerHorizontalWrap = enableHorizontalLinks
        }
        gridFocusController.updateRows(entryRows())
    }

    private fun readyEntryTargetFor(
        index: Int,
        direction: FocusDirection
    ): WjzFocusTargetEntry? {
        val currentEntryId = itemEntryIdFor(index)
        return when (val resolution = gridFocusController.resolve(currentEntryId, direction)) {
            is WjzFocusTargetResolution.Ready -> resolution.target
            else -> null
        }
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
                focusCoordinator?.hasFocus(nodeIdFor(previousIndex)) == true
        val changed = this.itemCount != itemCount || this.itemKeys != itemKeys

        this.itemCount = itemCount
        this.itemKeys = itemKeys
        syncGridFocusController()

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

        focusCoordinator?.enqueueGroupRestore(
            nodeId = emptyNodeId(),
            layer = focusLayer,
            scopeId = focusScopeId,
            restorerId = "$nodeIdPrefix/empty-restorer",
            listId = "$nodeIdPrefix/empty-restorer"
        )
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
        focusCoordinator?.enqueueLazyRestore(
            nodeId = nodeIdFor(index),
            itemKey = key,
            layer = focusLayer,
            scopeId = focusScopeId,
            restorerId = lazyRestorerId(),
            listId = lazyRestorerId()
        )
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

        val nodeId = nodeIdFor(index)
        val scopeId = focusScopeId ?: WjzFocusScopeId(nodeIdPrefix)
        val itemKey = itemKeyFor(index)
        val restorerId = lazyRestorerId()
        val itemFocusChanged: (Boolean) -> Unit = { hasFocus ->
            if (hasFocus) {
                lastFocusedIndex = index
                lastFocusedKey = visibleItemKeyFor(index)
            }
        }

        var modifier = Modifier
            .wjzFocusExits(
                id = nodeId.value,
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
            layer = focusLayer,
            scopeId = scopeId,
            itemKey = itemKey,
            restorerId = restorerId,
            listId = restorerId,
            onFocusChanged = itemFocusChanged
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
    content: LazyGridScope.() -> Unit
) {
    val focusCoordinator = LocalWjzFocusCoordinator.current
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
                    layer = focusLayer,
                    scopeId = resolvedFocusScopeId,
                    restorerId = "$nodeIdPrefix/empty-restorer",
                    listId = "$nodeIdPrefix/empty-restorer"
                )
                .wjzFocusExits(
                    id = "$nodeIdPrefix/empty",
                    layer = focusLayer
                )
                .onGloballyPositioned {
                    onEntryFocusReady?.invoke()
                }
        } else {
            modifier
        }

        WjzLazyFocusRestorerHost(
            layer = focusLayer,
            scopeId = resolvedFocusScopeId,
            restorerId = "$nodeIdPrefix/lazy-restorer",
            listId = "$nodeIdPrefix/lazy-restorer",
            scrollToItem = { key -> focusController.scrollToKey(key) },
            isItemVisible = { key -> focusController.isKeyVisible(key) }
        )

        WjzFocusEntrySurface(
            componentId = focusController.entryComponentId,
            default = { focusController.defaultEntryTarget() },
            entries = {
                focusController.entryTargets().forEach { target ->
                    entry(target.id, target)
                }
            }
        )

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
