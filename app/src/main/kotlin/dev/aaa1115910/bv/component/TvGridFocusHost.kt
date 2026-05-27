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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.wjzfocus.WjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.WjzFocusItemKey
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzLazyFocusRestorerHost
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.nodeKey
import dev.aaa1115910.bv.wjzfocus.requestWjzFocusKey
import dev.aaa1115910.bv.wjzfocus.wjzFocus

private fun WjzFocusNodeId.localIdIn(scopeId: WjzFocusScopeId?): String {
    val scopePrefix = scopeId?.value?.let { "$it/" } ?: return value
    return value.removePrefix(scopePrefix)
}

private fun WjzFocusScopeId.scopedNodeId(localId: String): WjzFocusNodeId {
    return WjzFocusNodeId(this.nodeKey(localId))
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
        return coordinator.requestWjzFocusKey(
            key = key,
            layer = layer,
            scopeId = scopeId,
            enqueueIfMissing = enqueueIfMissing
        )
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
        return Modifier.wjzFocus(
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
    var onEntryFocusReady: (() -> Unit)? = null
    var gridState: LazyGridState? = null
    var focusCoordinator: WjzFocusCoordinator? = null

    private fun itemKeyFor(index: Int): WjzFocusItemKey {
        return itemKeys[index]
    }

    private fun visibleItemKeyFor(index: Int): WjzFocusItemKey? {
        return itemKeys.getOrNull(index)
    }

    private fun nodeIdFor(index: Int): WjzFocusNodeId {
        val nodeKey = itemKeyFor(index).value
        return nodeIdFor(WjzFocusNodeId("$nodeIdPrefix/item/$nodeKey"))
    }

    private fun nodeIdFor(nodeId: WjzFocusNodeId): WjzFocusNodeId {
        val scopeId = focusScopeId ?: WjzFocusScopeId(nodeIdPrefix)
        return scopeId.scopedNodeId(nodeId.localIdIn(scopeId))
    }

    private fun emptyNodeId(): WjzFocusNodeId {
        return nodeIdFor(WjzFocusNodeId("$nodeIdPrefix/empty"))
    }

    private fun lazyRestorerId(): String {
        return "$nodeIdPrefix/lazy-restorer"
    }

    private fun indexOfKey(key: WjzFocusItemKey): Int? {
        return itemKeys.indexOf(key).takeIf { it >= 0 }
    }

    private fun sameColumnTarget(index: Int, rowOffset: Int): Int? {
        val target = index + columnCount * rowOffset
        return target.takeIf { it >= 0 && it < itemCount }
    }

    private fun moveFocusTo(index: Int) {
        if (index !in 0 until itemCount) return
        val targetKey = visibleItemKeyFor(index) ?: return
        focusCoordinator?.enqueueLazyRestore(
            nodeId = nodeIdFor(index),
            itemKey = targetKey,
            layer = focusLayer,
            scopeId = focusScopeId,
            restorerId = lazyRestorerId(),
            listId = lazyRestorerId()
        )
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

        focusCoordinator?.enqueueRequestFocus(
            nodeId = emptyNodeId(),
            layer = focusLayer,
            scopeId = focusScopeId
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

        val rowStart = (index / columnCount) * columnCount
        val rowEnd = minOf(rowStart + columnCount - 1, itemCount - 1)
        val upTarget = sameColumnTarget(index, -1)
        val downTarget = sameColumnTarget(index, 1)
        val nodeId = nodeIdFor(index)
        val scopeId = focusScopeId ?: WjzFocusScopeId(nodeIdPrefix)
        val localId = nodeId.localIdIn(scopeId)
        val itemKey = itemKeyFor(index)
        val restorerId = lazyRestorerId()
        val itemFocusChanged: (Boolean) -> Unit = { hasFocus ->
            if (hasFocus) {
                lastFocusedIndex = index
                lastFocusedKey = visibleItemKeyFor(index)
            }
        }

        var modifier = Modifier
            .wjzFocus(
                id = localId,
                layer = focusLayer,
                onFocusChanged = itemFocusChanged
            )
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (event.key) {
                    Key.DirectionLeft -> {
                        if (!enableHorizontalLinks || rowStart == rowEnd) {
                            return@onPreviewKeyEvent false
                        }
                        val target = if (index == rowStart) rowEnd else index - 1
                        moveFocusTo(target)
                        true
                    }

                    Key.DirectionRight -> {
                        if (!enableHorizontalLinks || rowStart == rowEnd) {
                            return@onPreviewKeyEvent false
                        }
                        val target = if (index == rowEnd) rowStart else index + 1
                        moveFocusTo(target)
                        true
                    }

                    Key.DirectionUp -> {
                        val target = upTarget ?: return@onPreviewKeyEvent false
                        moveFocusTo(target)
                        true
                    }

                    Key.DirectionDown -> {
                        val target = downTarget ?: return@onPreviewKeyEvent false
                        moveFocusTo(target)
                        true
                    }

                    else -> false
                }
            }
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
            val emptyLocalId = WjzFocusNodeId("$nodeIdPrefix/empty").localIdIn(resolvedFocusScopeId)
            modifier
                .wjzFocus(
                    id = emptyLocalId,
                    layer = focusLayer,
                    fallback = true
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
