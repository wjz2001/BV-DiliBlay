package dev.aaa1115910.bv.wjzfocus

import android.annotation.SuppressLint
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/** 普通 Scroll 容器的滚动方向。 */
enum class WjzFocusScrollOrientation {
    Vertical,
    Horizontal
}

/**
 * 带 WjzFocus 注册、最近焦点恢复、内部路由和焦点可见性滚动的 Column。
 *
 * 与 LazyColumn 不同，普通 ScrollColumn 的 item 会全部组合，因此 resolver 只返回
 * [WjzFocusRouteResult.Target] 或 [WjzFocusRouteResult.Missing]，不会产生 Lazy 的 ScrollTo
 * 恢复路径；滚动只由 [Modifier.wjzFocusScrollHost] 在焦点变化后按真实 bounds 保证当前节点可见。
 */
@Composable
fun <T> WjzFocusScrollColumn(
    items: List<T>,
    key: (T) -> String,
    listId: String,
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    enabled: Boolean = true,
    reverseScrolling: Boolean = false,
    flingBehavior: FlingBehavior? = null,
    wrap: Boolean = false,
    scrollPadding: PaddingValues = PaddingValues(0.dp),
    animatedScrollToFocused: Boolean = true,
    fallbackNodeId: WjzFocusNodeId? = null,
    customResolver: WjzFocusRouteResolver? = null,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    itemContent: @Composable (ColumnScope.(item: T, focusModifier: Modifier) -> Unit)
) {
    val model = rememberWjzScrollFocusModel(
        items = items,
        key = key,
        listId = listId,
        layer = layer,
        scopeId = scopeId
    )
    val resolver = customResolver ?: remember(model.targets, wrap) {
        wjzScrollColumnRouteResolver(
            entries = model.targets,
            wrap = wrap
        )
    }
    val containerModifier = modifier
        .wjzScrollFocusRestorerHost(
            model = model,
            fallbackNodeId = fallbackNodeId
        )
        .wjzFocusScrollHost(
            state = state,
            orientation = WjzFocusScrollOrientation.Vertical,
            layer = model.layer,
            scopeId = model.scopeId,
            scrollPadding = scrollPadding,
            animatedScrollToFocused = animatedScrollToFocused,
            reverseScrolling = reverseScrolling,
            enabled = enabled && model.items.isNotEmpty()
        )
        .verticalScroll(
            state = state,
            enabled = enabled,
            flingBehavior = flingBehavior,
            reverseScrolling = reverseScrolling
        )

    WjzScrollFocusManagementHosts(
        model = model,
        topologyRegion = topologyRegion
    )

    Column(
        modifier = containerModifier,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = {
            val columnScope = this
            items.forEachIndexed { index, item ->
                val focusItem = model.itemAt(index)
                columnScope.itemContent(
                    item,
                    Modifier.wjzScrollItemFocus(
                        item = focusItem,
                        resolver = resolver
                    )
                )
            }
        }
    )
}

/**
 * 带 WjzFocus 注册、最近焦点恢复、内部路由和焦点可见性滚动的 Row。
 *
 * 与 LazyRow 不同，普通 ScrollRow 已经拥有全部 item 的真实节点和 bounds；默认 resolver
 * 使用一维 index 拓扑，边界方向返回 Missing 交给 exits/Host exits，不会请求 Lazy ScrollTo。
 */
@Composable
fun <T> WjzFocusScrollRow(
    items: List<T>,
    key: (T) -> String,
    listId: String,
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    enabled: Boolean = true,
    reverseScrolling: Boolean = false,
    flingBehavior: FlingBehavior? = null,
    wrap: Boolean = false,
    scrollPadding: PaddingValues = PaddingValues(0.dp),
    animatedScrollToFocused: Boolean = true,
    fallbackNodeId: WjzFocusNodeId? = null,
    customResolver: WjzFocusRouteResolver? = null,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    itemContent: @Composable (RowScope.(item: T, focusModifier: Modifier) -> Unit)
) {
    val model = rememberWjzScrollFocusModel(
        items = items,
        key = key,
        listId = listId,
        layer = layer,
        scopeId = scopeId
    )
    val resolver = customResolver ?: remember(model.targets, wrap) {
        wjzScrollRowRouteResolver(
            entries = model.targets,
            wrap = wrap
        )
    }
    val containerModifier = modifier
        .wjzScrollFocusRestorerHost(
            model = model,
            fallbackNodeId = fallbackNodeId
        )
        .wjzFocusScrollHost(
            state = state,
            orientation = WjzFocusScrollOrientation.Horizontal,
            layer = model.layer,
            scopeId = model.scopeId,
            scrollPadding = scrollPadding,
            animatedScrollToFocused = animatedScrollToFocused,
            reverseScrolling = reverseScrolling,
            enabled = enabled && model.items.isNotEmpty()
        )
        .horizontalScroll(
            state = state,
            enabled = enabled,
            flingBehavior = flingBehavior,
            reverseScrolling = reverseScrolling
        )

    WjzScrollFocusManagementHosts(
        model = model,
        topologyRegion = topologyRegion
    )

    Row(
        modifier = containerModifier,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content =  {
            val rowScope = this
            items.forEachIndexed { index, item ->
                val focusItem = model.itemAt(index)
                rowScope.itemContent(
                    item,
                    Modifier.wjzScrollItemFocus(
                        item = focusItem,
                        resolver = resolver
                    )
                )
            }
        }
    )
}

/**
 * 普通 ScrollColumn 的默认一维路由 resolver。
 *
 * Scroll item 全部组合，目标存在时直接返回 Target；越界或非纵向方向返回 Missing。
 */
fun wjzScrollColumnRouteResolver(
    entries: List<WjzFocusTargetEntry>,
    wrap: Boolean = false
): WjzFocusRouteResolver {
    return wjzLinearFocusResolver(
        entries = entries,
        direction = vertical,
        wrap = wrap
    )
}

/**
 * 普通 ScrollRow 的默认一维路由 resolver。
 *
 * Scroll item 全部组合，目标存在时直接返回 Target；越界或非横向方向返回 Missing。
 */
fun wjzScrollRowRouteResolver(
    entries: List<WjzFocusTargetEntry>,
    wrap: Boolean = false
): WjzFocusRouteResolver {
    return wjzLinearFocusResolver(
        entries = entries,
        direction = horizontal,
        wrap = wrap
    )
}

/**
 * 给普通 Scroll item 挂内部路由和 WjzFocus 节点注册。
 *
 * Router 会先处理容器内部相邻移动；resolver 返回 Missing 时，方向继续交给节点 exits/Host exits。
 */
@Composable
fun Modifier.wjzScrollItemFocus(
    item: WjzScrollFocusItem,
    resolver: WjzFocusRouteResolver? = null,
    enabled: Boolean = true
): Modifier {
    return wjzScrollItemFocus(
        target = item.target,
        resolver = resolver,
        enabled = enabled
    )
}

/**
 * 给普通 Scroll item 挂内部路由和 WjzFocus 节点注册。
 *
 * Router 会先处理容器内部相邻移动；resolver 返回 Missing 时，方向继续交给节点 exits/Host exits。
 */
@Composable
fun Modifier.wjzScrollItemFocus(
    target: WjzFocusTargetEntry,
    resolver: WjzFocusRouteResolver? = null,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this
    val routedModifier = if (resolver == null) {
        this
    } else {
        wjzFocusRouter(
            currentEntryId = target.id,
            resolver = resolver
        )
    }
    return routedModifier.wjzFocusExits(
        nodeId = target.nodeId,
        layer = target.layer,
        scopeId = target.scopeId
    )
}

/**
 * 用 entryId/nodeId 直接给普通 Scroll item 挂内部路由和节点注册。
 *
 * 该重载适合自定义普通 Scroll 容器复用，完整 nodeId 由调用方负责稳定生成。
 */
@Composable
fun Modifier.wjzScrollItemFocus(
    entryId: String,
    nodeId: WjzFocusNodeId,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    resolver: WjzFocusRouteResolver? = null,
    enabled: Boolean = true
): Modifier {
    val resolvedScopeId = scopeId ?: LocalWjzFocusScopeId.current
    return wjzScrollItemFocus(
        target = WjzFocusTargetEntry(
            id = entryId,
            nodeId = nodeId,
            layer = layer,
            scopeId = resolvedScopeId
        ),
        resolver = resolver,
        enabled = enabled
    )
}

/**
 * 普通 Scroll 容器的焦点可见性 Host。
 *
 * 它不会注册 item，也不会参与内部路由；只监听 coordinator 的严格 leaf 焦点快照，用当前 focused node
 * 与 host 的 root bounds 计算最小滚动 delta，然后调用 [ScrollState.scrollTo] 或
 * [ScrollState.animateScrollTo]。Lazy 容器需要 ScrollTo key 并等待 item 组合，普通 Scroll
 * item 已全组合，因此这里只做 bounds 驱动的主轴居中修正。
 */
@SuppressLint("ComposableModifierFactory")
@Composable
fun Modifier.wjzFocusScrollHost(
    state: ScrollState,
    orientation: WjzFocusScrollOrientation,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    scrollPadding: PaddingValues = PaddingValues(0.dp),
    animatedScrollToFocused: Boolean = true,
    reverseScrolling: Boolean = false,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this

    val coordinator = LocalWjzFocusCoordinator.current ?: return this
    val resolvedScopeId = scopeId ?: LocalWjzFocusScopeId.current
    val hostBoundsState = remember { WjzFocusScrollHostBoundsState() }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val paddingPx = remember(scrollPadding, density, layoutDirection) {
        with(density) {
            WjzFocusScrollPaddingPx(
                left = scrollPadding.calculateLeftPadding(layoutDirection).roundToPx().toFloat(),
                top = scrollPadding.calculateTopPadding().roundToPx().toFloat(),
                right = scrollPadding.calculateRightPadding(layoutDirection).roundToPx().toFloat(),
                bottom = scrollPadding.calculateBottomPadding().roundToPx().toFloat()
            )
        }
    }

    LaunchedEffect(
        coordinator,
        state,
        orientation,
        layer,
        resolvedScopeId,
        paddingPx,
        animatedScrollToFocused,
        reverseScrolling
    ) {
        snapshotFlow {
            coordinator.focusedLeafSnapshot(layer, resolvedScopeId) to hostBoundsState.bounds
        }.collectLatest { (snapshot, hostBounds) ->
            val focusedNodeId = snapshot.nodeId ?: return@collectLatest
            hostBounds ?: return@collectLatest
            val nodeBounds = coordinator.nodeBounds(focusedNodeId) ?: return@collectLatest
            state.scrollNodeIntoView(
                hostBounds = hostBounds,
                nodeBounds = nodeBounds,
                orientation = orientation,
                padding = paddingPx,
                animated = animatedScrollToFocused,
                reverseScrolling = reverseScrolling
            )
        }
    }

    return this.then(WjzFocusScrollHostBoundsElement(hostBoundsState))
}

/**
 * 安装普通 Scroll 的公开入口 Host。
 *
 * 普通 Scroll 不需要 Lazy 的滚动后恢复消费；最近焦点恢复边界仍由
 * [Modifier.wjzScrollFocusRestorerHost] 挂到真实滚动容器上。
 */
@Composable
fun WjzScrollFocusManagementHosts(
    model: WjzScrollFocusModel,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone
) {
    if (model.items.isEmpty()) return
    val topology = wjzFocusRememberTopologyRegion(topologyRegion)

    WjzFocusEntriesHost(
        componentId = model.listId,
        default = {
            topology.resolveInitialTarget(
                componentId = model.listId,
                targets = model.targets
            ) {
                model.items.first().target
            }
        },
        entries = {
            model.items.forEach { item ->
                entry(item.entryId) { item.target }
            }
        }
    )

    if (topology.isBound) {
        WjzFocusHostExits(
            token = requireNotNull(topology.hostExitToken("scroll-topology", model.listId)),
            scopeId = model.scopeId,
            exits = topology.hostExits
        )
    }
}

/**
 * 给普通 Scroll 容器安装最近焦点恢复边界。
 *
 * 自定义官方 Column/Row + scroll 时，通常把它和 [Modifier.wjzFocusScrollHost]、
 * 官方滚动 modifier 挂在同一个容器 modifier 链上；公开入口由 [WjzScrollFocusManagementHosts] 安装。
 */
@SuppressLint("ComposableModifierFactory")
@Composable
fun Modifier.wjzScrollFocusRestorerHost(
    model: WjzScrollFocusModel,
    fallbackNodeId: WjzFocusNodeId? = null,
    enabled: Boolean = true
): Modifier {
    if (!enabled || model.items.isEmpty()) return this

    return wjzFocusRestorerHost(
        enabled = true,
        layer = model.layer,
        scopeId = model.scopeId,
        restorerId = model.restorerId,
        listId = model.listId,
        fallbackNodeId = fallbackNodeId ?: model.items.first().nodeId
    )
}

/**
 * 构建普通 Scroll 焦点模型。
 *
 * 普通 Scroll model 是轻量模型，只保存 item 顺序和 target 列表，不维护 Lazy 式索引字典。
 *
 * 高层 [WjzFocusScrollColumn] / [WjzFocusScrollRow] 会自动调用；需要直接组合官方
 * Column/Row + scroll 时，可手动组合该 model、[WjzScrollFocusManagementHosts]、
 * [Modifier.wjzScrollFocusRestorerHost]、[Modifier.wjzFocusScrollHost] 和
 * [Modifier.wjzScrollItemFocus]。
 */
@Composable
fun <T> rememberWjzScrollFocusModel(
    items: List<T>,
    key: (T) -> String,
    listId: String,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null
): WjzScrollFocusModel {
    val resolvedScopeId = scopeId ?: LocalWjzFocusScopeId.current
    return remember(items, key, listId, layer, resolvedScopeId) {
        val itemModels = buildScrollFocusItems(
            items = items,
            key = key,
            listId = listId,
            layer = layer,
            scopeId = resolvedScopeId
        )
        val targets = itemModels.map { item -> item.target }
        WjzScrollFocusModel(
            listId = listId,
            layer = layer,
            scopeId = resolvedScopeId,
            restorerId = "$listId/restorer",
            items = itemModels,
            targets = targets
        )
    }
}

private fun <T> buildScrollFocusItems(
    items: List<T>,
    key: (T) -> String,
    listId: String,
    layer: WjzFocusLayer,
    scopeId: WjzFocusScopeId?
): List<WjzScrollFocusItem> {
    val usedKeys = linkedSetOf<String>()
    return items.map { item ->
        val itemKey = key(item)
        require(usedKeys.add(itemKey)) {
            "duplicate wjz focus item key '$itemKey' in '$listId'"
        }
        // 普通 Scroll 也统一拆分业务 key 和内部 entry id，避免 target.id 被业务分隔符污染。
        val entryId = wjzFocusEncodeItemEntryId(itemKey)
        val localId = WjzFocusLocalId(wjzFocusItemNodeId(listId = listId, itemEntryId = entryId))
        val nodeId = scopeId?.resolve(localId) ?: WjzFocusNodeId(localId.value)
        WjzScrollFocusItem(
            entryId = entryId,
            nodeId = nodeId,
            target = WjzFocusTargetEntry(
                id = entryId,
                nodeId = nodeId,
                layer = layer,
                scopeId = scopeId
            )
        )
    }
}

private suspend fun ScrollState.scrollNodeIntoView(
    hostBounds: Rect,
    nodeBounds: Rect,
    orientation: WjzFocusScrollOrientation,
    padding: WjzFocusScrollPaddingPx,
    animated: Boolean,
    reverseScrolling: Boolean
) {
    val delta = when (orientation) {
        WjzFocusScrollOrientation.Vertical -> nodeBounds.verticalScrollDeltaIn(
            hostBounds = hostBounds,
            padding = padding
        )
        WjzFocusScrollOrientation.Horizontal -> nodeBounds.horizontalScrollDeltaIn(
            hostBounds = hostBounds,
            padding = padding
        )
    }
    if (kotlin.math.abs(delta) < 1f) return

    // Compose ScrollNode 在 reverseScrolling=false 时用 -scroll 偏移内容，
    // reverseScrolling=true 时用 scroll - maxScroll 偏移内容；同样的 bounds delta
    // 需要转成相反的 ScrollState.value delta 才能把节点推回可视区域。
    val scrollDelta = if (reverseScrolling) -delta else delta
    val target = (value + scrollDelta.roundToInt()).coerceIn(0, maxValue)
    if (target == value) return
    if (animated) {
        animateScrollTo(target)
    } else {
        scrollTo(target)
    }
}

private fun Rect.verticalScrollDeltaIn(
    hostBounds: Rect,
    padding: WjzFocusScrollPaddingPx
): Float {
    val viewportTop = hostBounds.top + padding.top
    val viewportBottom = hostBounds.bottom - padding.bottom
    val viewportCenter = (viewportTop + viewportBottom) / 2f
    val nodeCenter = (top + bottom) / 2f
    return nodeCenter - viewportCenter
}

private fun Rect.horizontalScrollDeltaIn(
    hostBounds: Rect,
    padding: WjzFocusScrollPaddingPx
): Float {
    val viewportLeft = hostBounds.left + padding.left
    val viewportRight = hostBounds.right - padding.right
    val viewportCenter = (viewportLeft + viewportRight) / 2f
    val nodeCenter = (left + right) / 2f
    return nodeCenter - viewportCenter
}

private class WjzFocusScrollHostBoundsState {
    var bounds: Rect? by mutableStateOf(null)
}

private data class WjzFocusScrollHostBoundsElement(
    val state: WjzFocusScrollHostBoundsState
) : ModifierNodeElement<WjzFocusScrollHostBoundsNode>() {
    override fun create(): WjzFocusScrollHostBoundsNode {
        return WjzFocusScrollHostBoundsNode(state)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "wjzFocusScrollHost"
    }

    override fun update(node: WjzFocusScrollHostBoundsNode) {
        node.state = state
    }
}

private class WjzFocusScrollHostBoundsNode(
    var state: WjzFocusScrollHostBoundsState
) : Modifier.Node(),
    LayoutAwareModifierNode {
    override fun onPlaced(coordinates: LayoutCoordinates) {
        state.bounds = coordinates.boundsInRoot()
    }

    override fun onDetach() {
        state.bounds = null
    }
}

/**
 * 普通 Scroll 可视区域的内边距像素值。
 *
 * 外部 API 使用 [PaddingValues]，这里在组合阶段按 density/layoutDirection 转成像素，
 * 避免每次 focused node 变化时重复做单位换算。
 */
private data class WjzFocusScrollPaddingPx(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * 普通 Scroll 的轻量焦点模型。
 *
 * 普通 Scroll item 已经全部组合，因此 model 只保留稳定 item 顺序和 resolver 需要的 target 列表。
 * 它不维护 Lazy 式索引字典；内部一维移动直接基于 [targets] 的顺序计算，最近焦点恢复则使用
 * [items] 中保存的 nodeId 和 target。
 */
data class WjzScrollFocusModel(
    val listId: String,
    val layer: WjzFocusLayer,
    val scopeId: WjzFocusScopeId?,
    val restorerId: String,
    val items: List<WjzScrollFocusItem>,
    val targets: List<WjzFocusTargetEntry>
) {
    /** 按组合顺序取得 item 的焦点注册信息。 */
    fun itemAt(index: Int): WjzScrollFocusItem = items[index]
}

/**
 * 普通 Scroll 单个 item 的焦点元数据。
 *
 * [entryId] 是由业务 key 编码得到的内部安全 entry id；
 * [nodeId] 是 coordinator 请求焦点的完整节点 id；
 * [target] 是 entries host 和 resolver 返回的统一目标。
 */
data class WjzScrollFocusItem(
    val entryId: String,
    val nodeId: WjzFocusNodeId,
    val target: WjzFocusTargetEntry
)
