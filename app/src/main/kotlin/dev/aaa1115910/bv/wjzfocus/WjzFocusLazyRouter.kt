package dev.aaa1115910.bv.wjzfocus

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.platform.InspectorInfo

/**
 * Lazy 容器的内部路由 resolver。
 *
 * Lazy 与普通 Grid 最大差异是目标 item 可能尚未组合，因此 resolver 除了直接返回可见目标，
 * 还可以返回 [WjzLazyFocusRouteResult.ScrollTo] 交给 restorer 先滚动、再在目标 item 注册后恢复焦点。
 */
fun interface WjzLazyFocusRouteResolver {
    /** 根据当前 Lazy 快照、当前 entry 和方向计算下一步动作。 */
    fun resolve(
        context: WjzLazyFocusRouteContext,
        currentEntryId: String,
        direction: FocusDirection
    ): WjzLazyFocusRouteResult
}

/**
 * Lazy 内部路由结果。
 *
 * - [Target]：目标已经可见并注册，可以立即请求焦点。
 * - [ScrollTo]：目标存在但未注册，先滚动到 key，待注册后由 Lazy restorer 恢复。
 * - [Missing]：当前方向没有内部目标，交回节点 exits/Host exits 处理边界。
 */
sealed interface WjzLazyFocusRouteResult {
    /** 立即请求一个已经可见、可注册的真实目标。 */
    data class Target(
        val target: WjzFocusResolvedTarget
    ) : WjzLazyFocusRouteResult

    /** 滚动到指定 entry 对应的 item，然后等待注册恢复。 */
    data class ScrollTo(
        val entryId: String
    ) : WjzLazyFocusRouteResult

    /** 不处理该方向。 */
    data object Missing : WjzLazyFocusRouteResult
}

/**
 * Lazy resolver 的只读快照上下文。
 *
 * 所有函数都来自本帧 item/key/visibility 状态，resolver 不需要也不应该维护自己的 controller。
 * 自定义 resolver 可以用它查询索引、entry、业务 key、可见几何信息，并把结果转成 Target/ScrollTo。
 */
data class WjzLazyFocusRouteContext(
    val itemCount: Int,
    val indexByEntryId: (String) -> Int?,
    val entryIdByIndex: (Int) -> String?,
    val itemKeyByIndex: (Int) -> WjzFocusItemKey?,
    val visibleEntryIds: Set<String>,
    val targetByEntryId: (String) -> WjzFocusResolvedTarget?,
    val targetNodeIdByEntryId: (String) -> WjzFocusNodeId?,
    val visibleItems: List<WjzFocusVisibleItem>
)

/**
 * 给 Lazy item 挂内部路由。
 *
 * 该 router 必须位于 item 的焦点注册 modifier 之前，这样方向键先尝试内部移动；只有
 * [WjzLazyFocusRouteResult.Missing] 才会继续交给节点 exits 和 Host exits。
 */
fun Modifier.wjzLazyFocusRouter(
    currentEntryId: String,
    model: WjzLazyFocusModel,
    restorerId: String,
    listId: String,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    resolver: WjzLazyFocusRouteResolver
): Modifier {
    return this.then(
        WjzLazyFocusRouterElement(
            currentEntryId = currentEntryId,
            model = model,
            restorerId = restorerId,
            listId = listId,
            layer = layer,
            scopeId = scopeId,
            resolver = resolver
        )
    )
}

private data class WjzLazyFocusRouterElement(
    val currentEntryId: String,
    val model: WjzLazyFocusModel,
    val restorerId: String,
    val listId: String,
    val layer: WjzFocusLayer,
    val scopeId: WjzFocusScopeId?,
    val resolver: WjzLazyFocusRouteResolver
) : ModifierNodeElement<WjzLazyFocusRouterNode>() {
    override fun create(): WjzLazyFocusRouterNode {
        return WjzLazyFocusRouterNode(
            currentEntryId = currentEntryId,
            model = model,
            restorerId = restorerId,
            listId = listId,
            layer = layer,
            scopeId = scopeId,
            resolver = resolver
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "wjzLazyFocusRouter"
        properties["currentEntryId"] = currentEntryId
        properties["restorerId"] = restorerId
        properties["listId"] = listId
        properties["layer"] = layer
        properties["scopeId"] = scopeId
    }

    override fun update(node: WjzLazyFocusRouterNode) {
        node.currentEntryId = currentEntryId
        node.model = model
        node.restorerId = restorerId
        node.listId = listId
        node.layer = layer
        node.scopeId = scopeId
        node.resolver = resolver
    }
}

private class WjzLazyFocusRouterNode(
    var currentEntryId: String,
    var model: WjzLazyFocusModel,
    var restorerId: String,
    var listId: String,
    var layer: WjzFocusLayer,
    var scopeId: WjzFocusScopeId?,
    var resolver: WjzLazyFocusRouteResolver
) : Modifier.Node(),
    TraversableNode,
    WjzFocusRouterNodeContract {

    override val traverseKey: Any
        get() = WjzFocusRouterTraverseKey

    override fun handle(
        direction: FocusDirection,
        coordinator: WjzFocusCoordinator?
    ): Boolean {
        val context = model.routeContextSnapshot()
        return resolver.resolve(
            context = context,
            currentEntryId = currentEntryId,
            direction = direction
        ).consume(
            context = context,
            coordinator = coordinator,
            restorerId = restorerId,
            listId = listId,
            layer = layer,
            scopeId = scopeId
        )
    }
}

private fun WjzLazyFocusRouteResult.consume(
    context: WjzLazyFocusRouteContext,
    coordinator: WjzFocusCoordinator?,
    restorerId: String,
    listId: String,
    layer: WjzFocusLayer,
    scopeId: WjzFocusScopeId?
): Boolean {
    if (coordinator == null) return false
    // Target 和 ScrollTo 都表示方向已被 Lazy 内部协议接管；Missing 才交还边界。
    return when (this) {
        is WjzLazyFocusRouteResult.Target -> requestTargetFocus(coordinator)
        is WjzLazyFocusRouteResult.ScrollTo -> enqueueLazyRestore(
            context = context,
            coordinator = coordinator,
            restorerId = restorerId,
            listId = listId,
            layer = layer,
            scopeId = scopeId
        )
        WjzLazyFocusRouteResult.Missing -> false
    }
}

private fun WjzLazyFocusRouteResult.Target.requestTargetFocus(
    coordinator: WjzFocusCoordinator
): Boolean {
    // Lazy router 已解析到具体节点，属于底层路由链路，直接执行底层请求。
    return when (
        coordinator.requestFocusDetailed(
            nodeId = target.nodeId,
            layer = target.layer,
            scopeId = target.scopeId
        )
    ) {
        WjzFocusRequestResult.Focused,
        WjzFocusRequestResult.Enqueued -> true
        WjzFocusRequestResult.Dropped,
        WjzFocusRequestResult.Failed -> false
    }
}

private fun WjzLazyFocusRouteResult.ScrollTo.enqueueLazyRestore(
    context: WjzLazyFocusRouteContext,
    coordinator: WjzFocusCoordinator,
    restorerId: String,
    listId: String,
    layer: WjzFocusLayer,
    scopeId: WjzFocusScopeId?
): Boolean {
    val targetIndex = context.indexByEntryId(entryId) ?: return false
    val itemKey = context.itemKeyByIndex(targetIndex) ?: return false
    val nodeId = context.targetNodeIdByEntryId(entryId) ?: return false
    // 先记录目标 node/key，再由具体 Lazy restorer 完成滚动并等待 item 注册。
    coordinator.enqueueLazyRestore(
        nodeId = nodeId,
        itemKey = itemKey,
        layer = layer,
        scopeId = scopeId,
        restorerId = restorerId,
        listId = listId
    )
    return true
}
