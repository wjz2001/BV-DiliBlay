package dev.aaa1115910.bv.wjzfocus

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.platform.InspectorInfo

internal val WjzFocusRouterTraverseKey = Any()

internal interface WjzFocusRouterNodeContract {
    fun handle(direction: FocusDirection, coordinator: WjzFocusCoordinator?): Boolean
}

/**
 * 普通非 Lazy 容器的内部路由 resolver。
 *
 * Resolver 只拿当前 entry id 和方向，返回目标节点或缺失。它不接收 component/scope 信息，
 * 因为内部相对移动只关心本帧拓扑快照，不应该把外部入口命名规则带进来。
 */
fun interface WjzFocusRouteResolver {
    /** 根据当前内部 entry 和方向计算下一跳。 */
    fun resolve(
        currentEntryId: String,
        direction: FocusDirection
    ): WjzFocusRouteResult
}

/**
 * 内部路由的解析结果。
 *
 * 这里故意只有 [Target] 和 [Missing]：`Target` 表示请求真实节点，`Missing` 表示当前 router
 * 不处理该方向，让后续节点 exits/Host exits 接管边界行为。不存在 `default`，因为 default 是外部
 * entry 解析语义，不属于内部相邻移动。
 */
sealed interface WjzFocusRouteResult {
    /** 当前方向命中内部目标节点。 */
    data class Target(
        val target: WjzFocusResolvedTarget
    ) : WjzFocusRouteResult

    /** 当前方向没有内部目标，交回边界协议处理。 */
    data object Missing : WjzFocusRouteResult
}

/**
 * 挂在节点路由入口上的方向处理器。
 *
 * `wjzFocusableExits` 在处理方向键时会先尝试 router handler，再尝试节点本地 handler；
 * 如果任一 handler 消费方向，后续 exits 不再执行。
 */
fun interface WjzFocusDirectionHandler {
    /** 返回 true 表示方向已被消费。 */
    fun handle(
        direction: FocusDirection,
        coordinator: WjzFocusCoordinator?
    ): Boolean
}

/**
 * 为当前节点挂接内部路由。
 *
 * [currentEntryId] 是 resolver 快照里的内部 id，不是完整 node id，也不需要 componentId。
 * resolver 应该由高层容器基于本帧 entries/rows 构建，避免 controller 通过 SideEffect 延迟同步时
 * item 注册拿到旧拓扑。
 */
fun Modifier.wjzFocusRouter(
    currentEntryId: String,
    resolver: WjzFocusRouteResolver
): Modifier {
    return this.then(
        WjzFocusRouterElement(
            currentEntryId = currentEntryId,
            resolver = resolver
        )
    )
}

private data class WjzFocusRouterElement(
    val currentEntryId: String,
    val resolver: WjzFocusRouteResolver
) : ModifierNodeElement<WjzFocusRouterNode>() {
    override fun create(): WjzFocusRouterNode {
        return WjzFocusRouterNode(
            currentEntryId = currentEntryId,
            resolver = resolver
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "wjzFocusRouter"
        properties["currentEntryId"] = currentEntryId
    }

    override fun update(node: WjzFocusRouterNode) {
        node.currentEntryId = currentEntryId
        node.resolver = resolver
    }
}

private class WjzFocusRouterNode(
    var currentEntryId: String,
    var resolver: WjzFocusRouteResolver
) : Modifier.Node(),
    TraversableNode,
    WjzFocusRouterNodeContract {

    override val traverseKey: Any
        get() = WjzFocusRouterTraverseKey

    override fun handle(
        direction: FocusDirection,
        coordinator: WjzFocusCoordinator?
    ): Boolean {
        // Handler 每次读取 node 上的最新字段，避免 modifier element update 后仍闭包旧 resolver
        val result = resolver.resolve(currentEntryId, direction)
        val consumed = result.consume(coordinator)
        return consumed
    }
}

private fun WjzFocusRouteResult.consume(
    coordinator: WjzFocusCoordinator?
): Boolean {
    if (this !is WjzFocusRouteResult.Target || coordinator == null) return false
    // 内部路由直接请求 resolver 返回的真实节点，不经由公开 entry，也不拼 componentId。
    val requestResult = coordinator.requestFocusDetailed(
        nodeId = target.nodeId,
        layer = target.layer,
        scopeId = target.scopeId
    )
    return when (requestResult) {
        WjzFocusRequestResult.Focused,
        WjzFocusRequestResult.Enqueued -> true
        WjzFocusRequestResult.Dropped,
        WjzFocusRequestResult.Failed -> false
    }
}
