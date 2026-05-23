package dev.aaa1115910.bv.wjzfocus

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed as lazyListItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as lazyGridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 带 WjzFocus 注册、最近焦点恢复和内部路由的 LazyColumn 便捷入口。
 *
 * 这是便捷 API，不承担 Foundation 后续所有参数的强同步义务；当官方 Lazy 新参数先于 wrapper
 * 暴露时，直接使用 [rememberWjzLazyFocusModel]、[Modifier.wjzFocusLazyListHost]、
 * [Modifier.wjzLazyFocusItem] 组合即可。
 * 最小官方组合可参考：
 * `val model = rememberWjzLazyFocusModel(items, key, listId)`
 * `LazyColumn(modifier = Modifier.wjzFocusLazyListHost(model, state), state = state) { ... }`
 *
 * 默认 resolver 使用索引拓扑：上/下按 index 前后移动，并支持 [reverseLayout]。
 * 如果目标 item 尚未组合，会通过 [WjzLazyFocusRouteResult.ScrollTo] 先滚动到对应 key，
 * 再等待 item 注册后恢复焦点。需要几何或业务邻接规则时传入 [customResolver]。
 */
@Composable
fun <T> WjzFocusLazyColumn(
    items: List<T>,
    key: (T) -> String,
    listId: String,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    wrap: Boolean = false,
    restoreTimeoutMillis: Long = WjzLazyFocusDefaultRestoreTimeoutMillis,
    customResolver: WjzLazyFocusRouteResolver? = null,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    itemContent: @Composable LazyItemScope.(item: T, focusModifier: Modifier) -> Unit
) {
    val model = rememberWjzLazyFocusModel(
        items = items,
        key = key,
        listId = listId,
        layer = layer,
        scopeId = scopeId,
        restoreTimeoutMillis = restoreTimeoutMillis
    )
    val resolver = customResolver ?: remember(reverseLayout, wrap) {
        wjzLazyColumnRouteResolver(
            reversedLayout = reverseLayout,
            wrap = wrap
        )
    }

    LazyColumn(
        modifier = modifier
            .wjzFocusLazyListHost(
                model = model,
                state = state,
                isVertical = true,
                enabled = model.items.isNotEmpty(),
                topologyRegion = topologyRegion
            ),
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        overscrollEffect = overscrollEffect
    ) {
        lazyListItemsIndexed(
            items = items,
            key = { _, item -> key(item) }
        ) { index, item ->
            val itemModel = model.itemAt(index)
            itemContent(
                item,
                Modifier.wjzLazyFocusItem(
                    item = itemModel,
                    model = model,
                    resolver = resolver
                )
            )
        }
    }
}

/**
 * 带 WjzFocus 注册、最近焦点恢复和内部路由的 LazyRow 便捷入口。
 *
 * 这是便捷 API，不承担 Foundation 后续所有参数的强同步义务；需要直接对接官方 Lazy 新参数时，
 * 使用 [rememberWjzLazyFocusModel]、[Modifier.wjzFocusLazyListHost]、
 * [Modifier.wjzLazyFocusItem]。
 *
 * 默认 resolver 使用索引拓扑：左/右按 index 前后移动，并支持 [reverseLayout]。
 * 内部路由只处理 Lazy 自身的相邻项；边界方向返回 Missing 后交给节点 exits 或 Host exits。
 */
@Composable
fun <T> WjzFocusLazyRow(
    items: List<T>,
    key: (T) -> String,
    listId: String,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal =
        if (!reverseLayout) Arrangement.Start else Arrangement.End,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    wrap: Boolean = false,
    restoreTimeoutMillis: Long = WjzLazyFocusDefaultRestoreTimeoutMillis,
    customResolver: WjzLazyFocusRouteResolver? = null,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    itemContent: @Composable LazyItemScope.(item: T, focusModifier: Modifier) -> Unit
) {
    val model = rememberWjzLazyFocusModel(
        items = items,
        key = key,
        listId = listId,
        layer = layer,
        scopeId = scopeId,
        restoreTimeoutMillis = restoreTimeoutMillis
    )
    val resolver = customResolver ?: remember(reverseLayout, wrap) {
        wjzLazyRowRouteResolver(
            reversedLayout = reverseLayout,
            wrap = wrap
        )
    }

    LazyRow(
        modifier = modifier
            .wjzFocusLazyListHost(
                model = model,
                state = state,
                isVertical = false,
                enabled = model.items.isNotEmpty(),
                topologyRegion = topologyRegion
            ),
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        overscrollEffect = overscrollEffect
    ) {
        lazyListItemsIndexed(
            items = items,
            key = { _, item -> key(item) }
        ) { index, item ->
            val itemModel = model.itemAt(index)
            itemContent(
                item,
                Modifier.wjzLazyFocusItem(
                    item = itemModel,
                    model = model,
                    resolver = resolver
                )
            )
        }
    }
}

/**
 * 带 WjzFocus 注册、恢复和内部路由的 LazyVerticalGrid 便捷入口。
 *
 * 这是便捷 API，不承担 Foundation 后续所有参数的强同步义务；当官方 grid 参数先更新时，
 * 使用 [rememberWjzLazyFocusModel]、[Modifier.wjzFocusLazyGridHost]、
 * [Modifier.wjzLazyFocusItem] 直接挂到官方 Grid。
 *
 * 默认按索引拓扑计算行列，列数由 [columns] 参照 Compose 的 [GridCells.Fixed]、
 * [GridCells.Adaptive] 规则在当前约束下计算。特殊 span 布局会破坏纯索引拓扑，应传入
 * [customResolver]，例如使用 `near` 邻接表或几何 nearest 工具。
 */
@Composable
fun <T> WjzFocusLazyVerticalGrid(
    items: List<T>,
    key: (T) -> String,
    gridId: String,
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    horizontalWrap: Boolean = false,
    verticalWrap: Boolean = false,
    restoreTimeoutMillis: Long = WjzLazyFocusDefaultRestoreTimeoutMillis,
    customResolver: WjzLazyFocusRouteResolver? = null,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    itemContent: @Composable LazyGridItemScope.(item: T, focusModifier: Modifier) -> Unit
) {
    val model = rememberWjzLazyFocusModel(
        items = items,
        key = key,
        listId = gridId,
        layer = layer,
        scopeId = scopeId,
        restoreTimeoutMillis = restoreTimeoutMillis
    )
    BoxWithConstraints {
        val resolverColumns = wjzGridSlotCount(
            cells = columns,
            availableSize = maxWidth,
            spacing = horizontalArrangement.spacing,
            beforePadding = contentPadding.calculateLeftPadding(LocalLayoutDirection.current),
            afterPadding = contentPadding.calculateRightPadding(LocalLayoutDirection.current)
        )
        val resolver = customResolver ?: remember(resolverColumns, horizontalWrap, verticalWrap) {
            wjzLazyGridRouteResolver(
                columns = resolverColumns,
                horizontalWrap = horizontalWrap,
                verticalWrap = verticalWrap
            )
        }

        LazyVerticalGrid(
            columns = columns,
            modifier = modifier
                .wjzFocusLazyGridHost(
                    model = model,
                    state = state,
                    enabled = model.items.isNotEmpty(),
                    topologyRegion = topologyRegion
                ),
            state = state,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            flingBehavior = flingBehavior,
            userScrollEnabled = userScrollEnabled,
            overscrollEffect = overscrollEffect
        ) {
            lazyGridItemsIndexed(
                items = items,
                key = { _, item -> key(item) }
            ) { index, item ->
                val itemModel = model.itemAt(index)
                itemContent(
                    item,
                    Modifier.wjzLazyFocusItem(
                        item = itemModel,
                        model = model,
                        resolver = resolver
                    )
                )
            }
        }
    }
}

/**
 * 带 WjzFocus 注册、恢复和内部路由的 LazyHorizontalGrid 便捷入口。
 *
 * 这是便捷 API，不承担 Foundation 后续所有参数的强同步义务；需要直接对接官方 Grid 新参数时，
 * 使用 [rememberWjzLazyFocusModel]、[Modifier.wjzFocusLazyGridHost]、
 * [Modifier.wjzLazyFocusItem]。
 *
 * 默认按索引拓扑计算行列，行数由 [rows] 参照 Compose GridCells 规则在当前约束下计算。
 * 当业务布局不再等价于规则网格时，应使用 [customResolver] 覆盖内部移动。
 */
@Composable
fun <T> WjzFocusLazyHorizontalGrid(
    items: List<T>,
    key: (T) -> String,
    gridId: String,
    rows: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal =
        if (!reverseLayout) Arrangement.Start else Arrangement.End,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    horizontalWrap: Boolean = false,
    verticalWrap: Boolean = false,
    restoreTimeoutMillis: Long = WjzLazyFocusDefaultRestoreTimeoutMillis,
    customResolver: WjzLazyFocusRouteResolver? = null,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    itemContent: @Composable LazyGridItemScope.(item: T, focusModifier: Modifier) -> Unit
) {
    val model = rememberWjzLazyFocusModel(
        items = items,
        key = key,
        listId = gridId,
        layer = layer,
        scopeId = scopeId,
        restoreTimeoutMillis = restoreTimeoutMillis
    )
    BoxWithConstraints {
        val resolverRows = wjzGridSlotCount(
            cells = rows,
            availableSize = maxHeight,
            spacing = verticalArrangement.spacing,
            beforePadding = contentPadding.calculateTopPadding(),
            afterPadding = contentPadding.calculateBottomPadding()
        )
        val resolver = customResolver ?: remember(resolverRows, horizontalWrap, verticalWrap) {
            wjzLazyHorizontalGridRouteResolver(
                rows = resolverRows,
                horizontalWrap = horizontalWrap,
                verticalWrap = verticalWrap
            )
        }

        LazyHorizontalGrid(
            rows = rows,
            modifier = modifier
                .wjzFocusLazyGridHost(
                    model = model,
                    state = state,
                    enabled = model.items.isNotEmpty(),
                    topologyRegion = topologyRegion
                ),
            state = state,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = verticalArrangement,
            flingBehavior = flingBehavior,
            userScrollEnabled = userScrollEnabled,
            overscrollEffect = overscrollEffect
        ) {
            lazyGridItemsIndexed(
                items = items,
                key = { _, item -> key(item) }
            ) { index, item ->
                val itemModel = model.itemAt(index)
                itemContent(
                    item,
                    Modifier.wjzLazyFocusItem(
                        item = itemModel,
                        model = model,
                        resolver = resolver
                    )
                )
            }
        }
    }
}

@Composable
private fun wjzGridSlotCount(
    cells: GridCells,
    availableSize: Dp,
    spacing: Dp,
    beforePadding: Dp,
    afterPadding: Dp
): Int {
    val density = LocalDensity.current
    return with(density) {
        val contentSize = (availableSize - beforePadding - afterPadding).roundToPx()
            .coerceAtLeast(0)
        // 与 Compose foundation 1.11 LazyGrid 的 slot 计算保持一致：
        // content constraints + cross-axis arrangement spacing -> GridCells.
        with(cells) {
            calculateCrossAxisCellSizes(
                availableSize = contentSize,
                spacing = spacing.roundToPx()
            ).size
        }
    }.coerceAtLeast(1)
}

private fun androidx.compose.foundation.lazy.LazyListLayoutInfo.listItemBounds(
    offset: Int,
    size: Int,
    isVertical: Boolean
): Rect {
    return if (isVertical) {
        Rect(
            left = 0f,
            top = offset.toFloat(),
            right = viewportSize.width.toFloat(),
            bottom = (offset + size).toFloat()
        )
    } else {
        Rect(
            left = offset.toFloat(),
            top = 0f,
            right = (offset + size).toFloat(),
            bottom = viewportSize.height.toFloat()
        )
    }
}

internal fun Modifier.wjzTrackLazyListVisibleItems(
    model: WjzLazyFocusModel,
    state: LazyListState,
    isVertical: Boolean,
    enabled: Boolean
): Modifier = then(
    WjzLazyListVisibleItemsElement(
        model = model,
        state = state,
        isVertical = isVertical,
        enabled = enabled
    )
)

private data class WjzLazyListVisibleItemsElement(
    val model: WjzLazyFocusModel,
    val state: LazyListState,
    val isVertical: Boolean,
    val enabled: Boolean
) : ModifierNodeElement<WjzLazyListVisibleItemsNode>() {
    override fun create(): WjzLazyListVisibleItemsNode {
        return WjzLazyListVisibleItemsNode(
            model = model,
            state = state,
            isVertical = isVertical,
            enabled = enabled
        )
    }

    override fun update(node: WjzLazyListVisibleItemsNode) {
        node.update(
            model = model,
            state = state,
            isVertical = isVertical,
            enabled = enabled
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "wjzTrackLazyListVisibleItems"
        properties["enabled"] = enabled
        properties["isVertical"] = isVertical
    }
}

private class WjzLazyListVisibleItemsNode(
    private var model: WjzLazyFocusModel,
    private var state: LazyListState,
    private var isVertical: Boolean,
    private var enabled: Boolean
) : Modifier.Node() {
    private var job: Job? = null

    override fun onAttach() {
        restartJob()
    }

    override fun onDetach() {
        job?.cancel()
        job = null
        model.updateVisibleItems(emptyList())
    }

    fun update(
        model: WjzLazyFocusModel,
        state: LazyListState,
        isVertical: Boolean,
        enabled: Boolean
    ) {
        val restart =
            this.model !== model ||
                    this.state !== state ||
                    this.isVertical != isVertical ||
                    this.enabled != enabled

        this.model = model
        this.state = state
        this.isVertical = isVertical
        this.enabled = enabled

        if (restart && isAttached) restartJob()
    }

    private fun restartJob() {
        job?.cancel()
        job = null

        if (!enabled) {
            model.updateVisibleItems(emptyList())
            return
        }

        job = coroutineScope.launch {
            snapshotFlow { buildVisibleItems() }
                .collectLatest { visibleItems -> model.updateVisibleItems(visibleItems) }
        }
    }

    private fun buildVisibleItems(): List<WjzFocusVisibleItem> {
        val layoutInfo = state.layoutInfo
        return layoutInfo.visibleItemsInfo.mapNotNull { itemInfo ->
            val entryId = model.entryIdByIndex(itemInfo.index) ?: return@mapNotNull null
            val itemKey = model.itemKeyByIndex(itemInfo.index) ?: return@mapNotNull null
            WjzFocusVisibleItem(
                entryId = entryId,
                itemKey = itemKey,
                index = itemInfo.index,
                bounds = layoutInfo.listItemBounds(
                    offset = itemInfo.offset,
                    size = itemInfo.size,
                    isVertical = isVertical
                )
            )
        }
    }
}

internal fun Modifier.wjzTrackLazyGridVisibleItems(
    model: WjzLazyFocusModel,
    state: LazyGridState,
    enabled: Boolean
): Modifier = then(
    WjzLazyGridVisibleItemsElement(
        model = model,
        state = state,
        enabled = enabled
    )
)

private data class WjzLazyGridVisibleItemsElement(
    val model: WjzLazyFocusModel,
    val state: LazyGridState,
    val enabled: Boolean
) : ModifierNodeElement<WjzLazyGridVisibleItemsNode>() {
    override fun create(): WjzLazyGridVisibleItemsNode {
        return WjzLazyGridVisibleItemsNode(
            model = model,
            state = state,
            enabled = enabled
        )
    }

    override fun update(node: WjzLazyGridVisibleItemsNode) {
        node.update(
            model = model,
            state = state,
            enabled = enabled
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "wjzTrackLazyGridVisibleItems"
        properties["enabled"] = enabled
    }
}

private class WjzLazyGridVisibleItemsNode(
    private var model: WjzLazyFocusModel,
    private var state: LazyGridState,
    private var enabled: Boolean
) : Modifier.Node() {
    private var job: Job? = null

    override fun onAttach() {
        restartJob()
    }

    override fun onDetach() {
        job?.cancel()
        job = null
        model.updateVisibleItems(emptyList())
    }

    fun update(
        model: WjzLazyFocusModel,
        state: LazyGridState,
        enabled: Boolean
    ) {
        val restart =
            this.model !== model ||
                    this.state !== state ||
                    this.enabled != enabled

        this.model = model
        this.state = state
        this.enabled = enabled

        if (restart && isAttached) restartJob()
    }

    private fun restartJob() {
        job?.cancel()
        job = null

        if (!enabled) {
            model.updateVisibleItems(emptyList())
            return
        }

        job = coroutineScope.launch {
            snapshotFlow { buildVisibleItems() }
                .collectLatest { visibleItems -> model.updateVisibleItems(visibleItems) }
        }
    }

    private fun buildVisibleItems(): List<WjzFocusVisibleItem> {
        return state.layoutInfo.visibleItemsInfo.mapNotNull { itemInfo ->
            val entryId = model.entryIdByIndex(itemInfo.index) ?: return@mapNotNull null
            val itemKey = model.itemKeyByIndex(itemInfo.index) ?: return@mapNotNull null
            WjzFocusVisibleItem(
                entryId = entryId,
                itemKey = itemKey,
                index = itemInfo.index,
                bounds = Rect(
                    left = itemInfo.offset.x.toFloat(),
                    top = itemInfo.offset.y.toFloat(),
                    right = (itemInfo.offset.x + itemInfo.size.width).toFloat(),
                    bottom = (itemInfo.offset.y + itemInfo.size.height).toFloat()
                )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.wjzTrackLazyStaggeredVisibleItems(
    model: WjzLazyFocusModel,
    state: LazyStaggeredGridState,
    enabled: Boolean
): Modifier = then(
    WjzLazyStaggeredVisibleItemsElement(
        model = model,
        state = state,
        enabled = enabled
    )
)

@OptIn(ExperimentalFoundationApi::class)
private data class WjzLazyStaggeredVisibleItemsElement(
    val model: WjzLazyFocusModel,
    val state: LazyStaggeredGridState,
    val enabled: Boolean
) : ModifierNodeElement<WjzLazyStaggeredVisibleItemsNode>() {
    override fun create(): WjzLazyStaggeredVisibleItemsNode {
        return WjzLazyStaggeredVisibleItemsNode(
            model = model,
            state = state,
            enabled = enabled
        )
    }

    override fun update(node: WjzLazyStaggeredVisibleItemsNode) {
        node.update(
            model = model,
            state = state,
            enabled = enabled
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "wjzTrackLazyStaggeredVisibleItems"
        properties["enabled"] = enabled
    }
}

@OptIn(ExperimentalFoundationApi::class)
private class WjzLazyStaggeredVisibleItemsNode(
    private var model: WjzLazyFocusModel,
    private var state: LazyStaggeredGridState,
    private var enabled: Boolean
) : Modifier.Node() {
    private var job: Job? = null

    override fun onAttach() {
        restartJob()
    }

    override fun onDetach() {
        job?.cancel()
        job = null
        model.updateVisibleItems(emptyList())
    }

    fun update(
        model: WjzLazyFocusModel,
        state: LazyStaggeredGridState,
        enabled: Boolean
    ) {
        val restart =
            this.model !== model ||
                    this.state !== state ||
                    this.enabled != enabled

        this.model = model
        this.state = state
        this.enabled = enabled

        if (restart && isAttached) restartJob()
    }

    private fun restartJob() {
        job?.cancel()
        job = null

        if (!enabled) {
            model.updateVisibleItems(emptyList())
            return
        }

        job = coroutineScope.launch {
            snapshotFlow { buildVisibleItems() }
                .collectLatest { visibleItems -> model.updateVisibleItems(visibleItems) }
        }
    }

    private fun buildVisibleItems(): List<WjzFocusVisibleItem> {
        return state.layoutInfo.visibleItemsInfo.mapNotNull { itemInfo ->
            val entryId = model.entryIdByIndex(itemInfo.index) ?: return@mapNotNull null
            val itemKey = model.itemKeyByIndex(itemInfo.index) ?: return@mapNotNull null
            WjzFocusVisibleItem(
                entryId = entryId,
                itemKey = itemKey,
                index = itemInfo.index,
                bounds = Rect(
                    left = itemInfo.offset.x.toFloat(),
                    top = itemInfo.offset.y.toFloat(),
                    right = (itemInfo.offset.x + itemInfo.size.width).toFloat(),
                    bottom = (itemInfo.offset.y + itemInfo.size.height).toFloat()
                )
            )
        }
    }
}
