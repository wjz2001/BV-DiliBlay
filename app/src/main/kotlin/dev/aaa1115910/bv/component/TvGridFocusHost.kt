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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.component.wjzfocus.WjzFocusCoordinator
import dev.aaa1115910.bv.component.wjzfocus.WjzFocusItemKey
import dev.aaa1115910.bv.component.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.component.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.component.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.component.wjzfocus.WjzLazyFocusRestorerHost
import dev.aaa1115910.bv.component.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.component.wjzfocus.wjzFocusable

data class BvLazyFocusItemTarget(
    val modifier: Modifier,
    val nodeId: WjzFocusNodeId,
    val requester: FocusRequester,
    val layer: WjzFocusLayer,
    val scopeId: WjzFocusScopeId?,
    val itemKey: WjzFocusItemKey,
    val restorerId: String,
    val listId: String
)

private class TvGridFocusController(
    private val columnCount: Int
) {
    private val requesters = mutableMapOf<Int, FocusRequester>()
    private val emptyRequester = FocusRequester()

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
    var entryFocusRequester: FocusRequester? = null
    var upFocusRequester: FocusRequester? = null
    var enableHorizontalLinks: Boolean = true
    var onEntryFocusReady: (() -> Unit)? = null
    var gridState: LazyGridState? = null
    var focusCoordinator: WjzFocusCoordinator? = null

    private fun requesterFor(index: Int): FocusRequester {
        if (index == 0) {
            entryFocusRequester?.let { return it }
        }
        return requesters.getOrPut(index) { FocusRequester() }
    }

    private fun itemKeyFor(index: Int): WjzFocusItemKey {
        return itemKeys[index]
    }

    private fun visibleItemKeyFor(index: Int): WjzFocusItemKey? {
        return itemKeys.getOrNull(index)
    }

    private fun nodeIdFor(index: Int): WjzFocusNodeId {
        val nodeKey = itemKeyFor(index).value
        return WjzFocusNodeId("$nodeIdPrefix/item/$nodeKey")
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

    fun emptyFocusRequester(): FocusRequester {
        return emptyRequester
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
            nodeId = WjzFocusNodeId("$nodeIdPrefix/empty"),
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
        val requester = requesterFor(index)
        val nodeId = nodeIdFor(index)
        val itemKey = itemKeyFor(index)
        val restorerId = lazyRestorerId()

        var modifier = Modifier
            .wjzFocusable(
                nodeId = nodeId,
                layer = focusLayer,
                scopeId = focusScopeId,
                requester = requester,
                onFocusChanged = { hasFocus ->
                    if (hasFocus) {
                        lastFocusedIndex = index
                        lastFocusedKey = visibleItemKeyFor(index)
                    }
                }
            )
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (event.key) {
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
            .focusProperties {
                if (enableHorizontalLinks && rowStart != rowEnd) {
                    left = if (index == rowStart) {
                        requesterFor(rowEnd)
                    } else {
                        requesterFor(index - 1)
                    }

                    right = if (index == rowEnd) {
                        requesterFor(rowStart)
                    } else {
                        requesterFor(index + 1)
                    }
                }
                if (rowStart == 0) {
                    up = upFocusRequester ?: FocusRequester.Default
                } else {
                    upTarget?.let { up = requesterFor(it) }
                }
                downTarget?.let { down = requesterFor(it) }
            }
        if (index == 0) {
            modifier = modifier.onGloballyPositioned {
                onEntryFocusReady?.invoke()
            }
        }
        return BvLazyFocusItemTarget(
            modifier = modifier,
            nodeId = nodeId,
            requester = requester,
            layer = focusLayer,
            scopeId = focusScopeId,
            itemKey = itemKey,
            restorerId = restorerId,
            listId = restorerId
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
    entryFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onEntryFocusReady: (() -> Unit)? = null,
    focusItemCount: Int = 0,
    focusColumnCount: Int = 4,
    content: LazyGridScope.() -> Unit
) {
    val focusCoordinator = LocalWjzFocusCoordinator.current
    require(focusItemCount == 0 || itemKeys.size == focusItemCount) {
        "itemKeys size must match focusItemCount"
    }
    val focusController = remember(focusColumnCount) {
        TvGridFocusController(focusColumnCount)
    }.apply {
        enabled = focusItemCount > 0 &&
                focusColumnCount > 0 &&
                (enableRowHorizontalWrap || entryFocusRequester != null)
        this.nodeIdPrefix = nodeIdPrefix
        this.focusLayer = focusLayer
        this.focusScopeId = focusScopeId
        this.entryFocusRequester = entryFocusRequester
        this.upFocusRequester = upFocusRequester
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
        LocalTvGridFocusController provides focusController
    ) {
        val gridModifier = if (focusItemCount <= 0) {
            modifier
                .wjzFocusable(
                    nodeId = WjzFocusNodeId("$nodeIdPrefix/empty"),
                    layer = focusLayer,
                    scopeId = focusScopeId,
                    fallback = true,
                    requester = focusController.emptyFocusRequester()
                )
                .focusProperties {
                    up = upFocusRequester ?: FocusRequester.Default
                }
                .onGloballyPositioned {
                    onEntryFocusReady?.invoke()
                }
        } else {
            modifier
        }

        WjzLazyFocusRestorerHost(
            layer = focusLayer,
            scopeId = focusScopeId,
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
