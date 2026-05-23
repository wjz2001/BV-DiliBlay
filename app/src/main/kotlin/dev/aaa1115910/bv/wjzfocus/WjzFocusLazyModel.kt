package dev.aaa1115910.bv.wjzfocus

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.collectLatest

/**
 * 构建 Lazy 焦点模型。
 *
 * 这是 Lazy wrapper 复用的底层 API。高层 `WjzFocusLazy*` wrapper 只是便捷入口，
 * 不承诺跟 Foundation 后续新增参数做强同步；当业务需要直接使用 Compose 官方 Lazy 的新参数、
 * 自定义 `content` DSL 或混合多个 item 类型时，优先手动组合本文件里的 model、Host 和
 * [Modifier.wjzLazyFocusItem]。
 *
 * 最小组合示例：
 * `val model = rememberWjzLazyFocusModel(items, key, listId)`
 * `LazyColumn(modifier = Modifier.wjzFocusLazyListHost(model, state), state = state) { ... }`
 * `itemContent(item, Modifier.wjzLazyFocusItem(item = model.itemAt(index), model = model, resolver = resolver))`
 */
@Composable
fun <T> rememberWjzLazyFocusModel(
    items: List<T>,
    key: (T) -> String,
    listId: String,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    restoreTimeoutMillis: Long = WjzLazyFocusDefaultRestoreTimeoutMillis
): WjzLazyFocusModel {
    val resolvedScopeId = scopeId ?: LocalWjzFocusScopeId.current
    // model 固定完整数据集里的 entry/key/node 映射；可见性由 route context 另行按帧提供。
    return remember(items, key, listId, layer, resolvedScopeId, restoreTimeoutMillis) {
        val itemModels = buildLazyFocusItems(
            items = items,
            key = key,
            listId = listId,
            layer = layer,
            scopeId = resolvedScopeId
        )
        WjzLazyFocusModel(
            listId = listId,
            layer = layer,
            scopeId = resolvedScopeId,
            restorerId = "$listId/restorer",
            restoreTimeoutMillis = restoreTimeoutMillis,
            items = itemModels,
            targets = itemModels.map { item -> item.target }
        )
    }
}

/**
 * 安装 Lazy 焦点管理 Host。
 *
 * wrapper 是便捷入口；直接使用官方 Lazy 或自定义 content 时，调用该 Host 负责公开入口、
 * 不可见目标滚动和滚动后的焦点恢复；LazyList 会尽量让焦点项主轴居中。若 wrapper 对 Foundation 新参数跟进滞后，
 * 直接搭配 [Modifier.wjzFocusLazyListHost] / [Modifier.wjzFocusLazyGridHost] /
 * [Modifier.wjzFocusLazyStaggeredHost] 接官方 Lazy 即可。
 */
@Composable
fun WjzLazyFocusManagementHosts(
    model: WjzLazyFocusModel,
    stateScrollToItem: suspend (Int) -> Unit,
    isItemVisible: (Int) -> Boolean,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone
) {
    val topology = wjzFocusRememberTopologyRegion(topologyRegion)

    // Entry host 暴露公开入口；restorer host 负责不可见目标滚动后恢复。
    if (model.items.isNotEmpty()) {
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
    }

    if (topology.isBound) {
        WjzFocusHostExits(
            token = requireNotNull(topology.hostExitToken("lazy-topology", model.listId)),
            scopeId = model.scopeId,
            exits = topology.hostExits
        )
    }

    WjzLazyFocusRestorerHost(
        layer = model.layer,
        scopeId = model.scopeId,
        restorerId = model.restorerId,
        listId = model.listId,
        restoreTimeoutMillis = model.restoreTimeoutMillis,
        scrollToItem = { itemKey ->
            model.indexByItemKey(itemKey)?.let { index -> stateScrollToItem(index) }
        },
        isItemVisible = { itemKey ->
            model.indexByItemKey(itemKey)?.let(isItemVisible) ?: false
        }
    )
}

/**
 * 给官方 LazyList 安装 WjzFocus host 组合。
 *
 * 它会固定安装最近焦点恢复边界、Lazy restorer/entries host，以及焦点项滚动主轴居中恢复。
 * `WjzFocusLazyColumn/Row` 内部直接调用它；若 wrapper 对官方参数跟进滞后，可直接把它挂到
 * `LazyColumn` / `LazyRow` 的 `modifier` 上继续使用官方 API。
 */
@SuppressLint("ComposableModifierFactory")
@Composable
fun Modifier.wjzFocusLazyListHost(
    model: WjzLazyFocusModel,
    state: LazyListState,
    fallbackNodeId: WjzFocusNodeId? = null,
    animatedScrollToFocused: Boolean = true,
    isVertical: Boolean = true,
    enabled: Boolean = true,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone
): Modifier {
    return wjzFocusLazyListHost(
        model = model,
        state = state,
        fallbackNodeId = fallbackNodeId,
        animatedScrollToFocused = animatedScrollToFocused,
        isVertical = isVertical,
        enabled = enabled,
        restoreTimeoutMillis = model.restoreTimeoutMillis,
        topologyRegion = topologyRegion
    )
}

/**
 * 给官方 LazyGrid 安装 WjzFocus host 组合。
 *
 * 面向 `LazyVerticalGrid` / `LazyHorizontalGrid`，仍只做焦点项滚动可见性恢复和最近焦点恢复。
 */
@SuppressLint("ComposableModifierFactory")
@Composable
fun Modifier.wjzFocusLazyGridHost(
    model: WjzLazyFocusModel,
    state: LazyGridState,
    fallbackNodeId: WjzFocusNodeId? = null,
    animatedScrollToFocused: Boolean = true,
    enabled: Boolean = true,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone
): Modifier {
    return wjzFocusLazyGridHost(
        model = model,
        state = state,
        fallbackNodeId = fallbackNodeId,
        animatedScrollToFocused = animatedScrollToFocused,
        enabled = enabled,
        restoreTimeoutMillis = model.restoreTimeoutMillis,
        topologyRegion = topologyRegion
    )
}

/**
 * 给官方 LazyStaggeredGrid 安装 WjzFocus host 组合。
 *
 * 它会安装公开入口、Lazy restorer 和最近焦点恢复边界。默认不额外做“当前焦点项滚动可见性恢复”，
 * 因为 Staggered 容器的布局/移动语义不稳定；需要内部方向移动时，仍应显式传入 custom resolver。
 */
@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("ComposableModifierFactory")
@Composable
fun Modifier.wjzFocusLazyStaggeredHost(
    model: WjzLazyFocusModel,
    state: LazyStaggeredGridState,
    fallbackNodeId: WjzFocusNodeId? = null,
    enabled: Boolean = true,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone
): Modifier {
    return wjzFocusLazyStaggeredHost(
        model = model,
        state = state,
        fallbackNodeId = fallbackNodeId,
        enabled = enabled,
        restoreTimeoutMillis = model.restoreTimeoutMillis,
        topologyRegion = topologyRegion
    )
}

/**
 * 给 Lazy item 追加 WjzFocus 注册和可选内部路由。
 *
 * wrapper 会自动调用它；底层使用时需要把它放到具体 item 的 modifier 上。当 [resolver] 为 null 时
 * 只注册和恢复，不接管 Lazy 内部方向移动。
 */
@Composable
fun Modifier.wjzLazyFocusItem(
    item: WjzLazyFocusItem,
    model: WjzLazyFocusModel,
    resolver: WjzLazyFocusRouteResolver?
): Modifier {
    val focusModifier = wjzFocusExits(
        nodeId = item.nodeId,
        scopeId = model.scopeId,
        layer = model.layer
    )
    // Router 必须在焦点注册之前，让方向键先尝试 Lazy 内部路由，再交给 exits 边界。
    return if (resolver == null) {
        focusModifier
    } else {
        wjzLazyFocusRouter(
            currentEntryId = item.entryId,
            model = model,
            restorerId = model.restorerId,
            listId = model.listId,
            layer = model.layer,
            scopeId = model.scopeId,
            resolver = resolver
        ).then(focusModifier)
    }
}

/**
 * 按当前可见 item 构建 Lazy resolver 的只读上下文。
 */
fun wjzLazyFocusRouteContext(
    model: WjzLazyFocusModel,
    visibleItems: List<WjzFocusVisibleItem>
): WjzLazyFocusRouteContext {
    return model.routeContext(visibleItems)
}

/**
 * Lazy 焦点模型。`items` 描述完整数据集，visibility 由每帧的 route context 提供。
 */
data class WjzLazyFocusModel(
    val listId: String,
    val layer: WjzFocusLayer,
    val scopeId: WjzFocusScopeId?,
    val restorerId: String,
    val restoreTimeoutMillis: Long,
    val items: List<WjzLazyFocusItem>,
    val targets: List<WjzFocusTargetEntry>
) {
    @Volatile
    private var visibleItems: List<WjzFocusVisibleItem> = emptyList()

    private val indexByEntryId = items
        .mapIndexed { index, item -> item.entryId to index }
        .toMap()
    private val indexByNodeId = items
        .mapIndexed { index, item -> item.nodeId to index }
        .toMap()
    private val indexByItemKey = items
        .mapIndexed { index, item -> item.itemKey to index }
        .toMap()
    private val entryIdByIndex = items.map { item -> item.entryId }
    private val itemKeyByIndex = items.map { item -> item.itemKey }
    private val targetByEntryId = items.associate { item -> item.entryId to item.target }

    fun itemAt(index: Int): WjzLazyFocusItem = items[index]
    fun indexByEntryId(entryId: String): Int? = indexByEntryId[entryId]
    fun indexByNodeId(nodeId: WjzFocusNodeId): Int? = indexByNodeId[nodeId]
    fun entryIdByIndex(index: Int): String? = entryIdByIndex.getOrNull(index)
    fun itemKeyByIndex(index: Int): WjzFocusItemKey? = itemKeyByIndex.getOrNull(index)
    fun indexByItemKey(itemKey: WjzFocusItemKey): Int? = indexByItemKey[itemKey]
    fun targetByEntryId(entryId: String): WjzFocusTargetEntry? = targetByEntryId[entryId]

    internal fun updateVisibleItems(items: List<WjzFocusVisibleItem>) {
        visibleItems = items
    }

    internal fun visibleItemsSnapshot(): List<WjzFocusVisibleItem> = visibleItems

    internal fun routeContextSnapshot(): WjzLazyFocusRouteContext {
        return routeContext(visibleItemsSnapshot())
    }

    internal fun routeContext(visibleItems: List<WjzFocusVisibleItem>): WjzLazyFocusRouteContext {
        val visibleEntryIds = visibleItems.mapTo(linkedSetOf()) { item -> item.entryId }
        // RouteContext 是 resolver 的本帧只读视图，避免 resolver 自己缓存 Lazy 拓扑。
        return WjzLazyFocusRouteContext(
            itemCount = items.size,
            indexByEntryId = ::indexByEntryId,
            entryIdByIndex = ::entryIdByIndex,
            itemKeyByIndex = ::itemKeyByIndex,
            visibleEntryIds = visibleEntryIds,
            targetByEntryId = ::targetByEntryId,
            targetNodeIdByEntryId = { entryId -> targetByEntryId(entryId)?.nodeId },
            visibleItems = visibleItems
        )
    }
}

/**
 * 单个 Lazy item 的 WjzFocus 注册信息。
 */
data class WjzLazyFocusItem(
    val entryId: String,
    val itemKey: WjzFocusItemKey,
    val nodeId: WjzFocusNodeId,
    val target: WjzFocusTargetEntry
)

private fun <T> buildLazyFocusItems(
    items: List<T>,
    key: (T) -> String,
    listId: String,
    layer: WjzFocusLayer,
    scopeId: WjzFocusScopeId?
): List<WjzLazyFocusItem> {
    val usedKeys = linkedSetOf<String>()
    return items.map { item ->
        val itemKey = key(item)
        require(usedKeys.add(itemKey)) {
            "duplicate wjz focus item key '$itemKey' in '$listId'"
        }
        // 业务 key 继续保存在 itemKey 中用于 Lazy 恢复；entryId 是内部路由和公开 entry 使用的安全 id。
        val entryId = wjzFocusEncodeItemEntryId(itemKey)
        val localId = WjzFocusLocalId(wjzFocusItemNodeId(listId = listId, itemEntryId = entryId))
        // 有 scope 时使用 scope|local 生成完整 node id；无 scope 时保留旧完整 node id 形态。
        val nodeId = scopeId?.resolve(localId) ?: WjzFocusNodeId(localId.value)
        WjzLazyFocusItem(
            entryId = entryId,
            itemKey = WjzFocusItemKey(itemKey),
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

@SuppressLint("ComposableModifierFactory")
@Composable
internal fun Modifier.wjzFocusLazyListHost(
    model: WjzLazyFocusModel,
    state: LazyListState,
    fallbackNodeId: WjzFocusNodeId?,
    animatedScrollToFocused: Boolean,
    isVertical: Boolean,
    enabled: Boolean,
    restoreTimeoutMillis: Long,
    topologyRegion: WjzFocusTopologyRegionRef
): Modifier {
    return wjzTrackLazyListVisibleItems(
        model = model,
        state = state,
        isVertical = isVertical,
        enabled = enabled && model.items.isNotEmpty()
    ).wjzFocusLazyHost(
        model = model,
        fallbackNodeId = fallbackNodeId,
        enabled = enabled,
        restoreTimeoutMillis = restoreTimeoutMillis,
        scrollToItemForRestore = { index -> state.scrollToItem(index) },
        isItemVisible = { index -> state.layoutInfo.visibleItemsInfo.any { it.index == index } },
        topologyRegion = topologyRegion
    ) {
        InstallLazyFocusedItemVisibilityHost(
            model = model,
            animatedScrollToFocused = animatedScrollToFocused,
            isItemVisible = { index -> state.layoutInfo.visibleItemsInfo.any { it.index == index } },
            scrollToFocusedItem = { index, animated ->
                val scrollOffset = state.centerOffsetFor(
                    index = index,
                    itemCount = model.items.size
                )
                if (animated) {
                    state.animateScrollToItem(index, scrollOffset = scrollOffset)
                } else {
                    state.scrollToItem(index, scrollOffset = scrollOffset)
                }
            }
        )
    }
}

/**
 * 计算 LazyList focused item 的近似居中 offset。
 *
 * 首尾 item 不强行居中，直接返回 0 交给 Compose 贴近边界，避免列表开头/结尾处
 * 因不可达的居中目标反复回弹。中间 item 只根据当前可见 item size 和 viewport size
 * 做近似计算；如果目标尚不可见，返回 0 先滚到目标，后续焦点变化再自然修正。
 */
private fun LazyListState.centerOffsetFor(
    index: Int,
    itemCount: Int
): Int {
    if (index <= 0 || index >= itemCount - 1) return 0
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return 0
    val viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    return -(viewportSize / 2 - item.size / 2)
}

/**
 * LazyGrid 的底层 Host 组合入口。
 *
 * Grid 这里暂不做主轴居中 offset，因为 vertical/horizontal grid 的主轴尺寸和 spacing
 * 需要额外参数才能可靠计算；当前只保证目标可见和最近焦点恢复，避免为了近似居中引入复杂状态。
 */
@SuppressLint("ComposableModifierFactory")
@Composable
internal fun Modifier.wjzFocusLazyGridHost(
    model: WjzLazyFocusModel,
    state: LazyGridState,
    fallbackNodeId: WjzFocusNodeId?,
    animatedScrollToFocused: Boolean,
    enabled: Boolean,
    restoreTimeoutMillis: Long,
    topologyRegion: WjzFocusTopologyRegionRef
): Modifier {
    return wjzTrackLazyGridVisibleItems(
        model = model,
        state = state,
        enabled = enabled && model.items.isNotEmpty()
    ).wjzFocusLazyHost(
        model = model,
        fallbackNodeId = fallbackNodeId,
        enabled = enabled,
        restoreTimeoutMillis = restoreTimeoutMillis,
        scrollToItemForRestore = { index -> state.scrollToItem(index) },
        isItemVisible = { index -> state.layoutInfo.visibleItemsInfo.any { it.index == index } },
        topologyRegion = topologyRegion
    ) {
        InstallLazyFocusedItemVisibilityHost(
            model = model,
            animatedScrollToFocused = animatedScrollToFocused,
            isItemVisible = { index -> state.layoutInfo.visibleItemsInfo.any { it.index == index } },
            scrollToFocusedItem = { index, animated ->
                if (animated) {
                    state.animateScrollToItem(index)
                } else {
                    state.scrollToItem(index)
                }
            }
        )
    }
}

/**
 * StaggeredGrid 的底层 Host 组合入口。
 *
 * Staggered 默认只负责注册、恢复和滚动到 key；内部方向移动必须由调用方显式传入
 * custom resolver。这里不安装 focused item 居中恢复，避免瀑布流高度不一致时产生不可预期跳动。
 */
@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("ComposableModifierFactory")
@Composable
internal fun Modifier.wjzFocusLazyStaggeredHost(
    model: WjzLazyFocusModel,
    state: LazyStaggeredGridState,
    fallbackNodeId: WjzFocusNodeId?,
    enabled: Boolean,
    restoreTimeoutMillis: Long,
    topologyRegion: WjzFocusTopologyRegionRef
): Modifier {
    return wjzTrackLazyStaggeredVisibleItems(
        model = model,
        state = state,
        enabled = enabled && model.items.isNotEmpty()
    ).wjzFocusLazyHost(
        model = model,
        fallbackNodeId = fallbackNodeId,
        enabled = enabled,
        restoreTimeoutMillis = restoreTimeoutMillis,
        scrollToItemForRestore = { index -> state.scrollToItem(index) },
        isItemVisible = { index -> state.layoutInfo.visibleItemsInfo.any { it.index == index } },
        topologyRegion = topologyRegion
    )
}

/**
 * Lazy host 共用安装逻辑。
 *
 * 该函数集中安装 entries host、restorer host 和可选的 focused item 可见性恢复。
 * List/Grid/Staggered 的差异只体现在滚动到 index 和可见性判断两个函数参数里。
 */
@SuppressLint("ComposableModifierFactory")
@Composable
private fun Modifier.wjzFocusLazyHost(
    model: WjzLazyFocusModel,
    fallbackNodeId: WjzFocusNodeId?,
    enabled: Boolean,
    restoreTimeoutMillis: Long,
    scrollToItemForRestore: suspend (Int) -> Unit,
    isItemVisible: (Int) -> Boolean,
    topologyRegion: WjzFocusTopologyRegionRef,
    installFocusedItemVisibilityRestore: @Composable () -> Unit = {}
): Modifier {
    if (!enabled || model.items.isEmpty()) return this

    WjzLazyFocusManagementHosts(
        model = model.copy(restoreTimeoutMillis = restoreTimeoutMillis),
        stateScrollToItem = scrollToItemForRestore,
        isItemVisible = isItemVisible,
        topologyRegion = topologyRegion
    )
    installFocusedItemVisibilityRestore()

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
 * 监听当前 scope 的 focused item leaf，并在目标 item 不可见时请求 Lazy 容器滚动到该 index。
 *
 * `snapshotFlow` 读取的是严格 `(layer, scopeId)` 的 focused leaf snapshot，因此不会被其他 scope
 * 的焦点变化广播唤醒。把 index 和 visible 状态打包成一个 data class，是为了让 item
 * 从不可见变成可见时也能停止重复滚动。
 */
@Composable
private fun InstallLazyFocusedItemVisibilityHost(
    model: WjzLazyFocusModel,
    animatedScrollToFocused: Boolean,
    isItemVisible: (Int) -> Boolean,
    scrollToFocusedItem: suspend (Int, Boolean) -> Unit
) {
    val coordinator = LocalWjzFocusCoordinator.current ?: return

    LaunchedEffect(
        coordinator,
        model.listId,
        model.layer,
        model.scopeId,
        animatedScrollToFocused
    ) {
        snapshotFlow {
            val index = coordinator
                .focusedLeafSnapshot(model.layer, model.scopeId)
                .nodeId
                ?.let(model::indexByNodeId)
            FocusedLazyItemVisibility(
                index = index,
                isVisible = index?.let(isItemVisible) ?: false
            )
        }.collectLatest { focusedItem ->
            val index = focusedItem.index ?: return@collectLatest
            scrollToFocusedItem(index, animatedScrollToFocused)
        }
    }
}

/**
 * Lazy focused item 的可见性快照。
 *
 * [index] 为 null 表示当前 scope 没有落在本 model 内的 focused node；
 * [isVisible] 表示该 index 当前是否已经出现在 Lazy layoutInfo 中。
 */
private data class FocusedLazyItemVisibility(
    val index: Int?,
    val isVisible: Boolean
)
