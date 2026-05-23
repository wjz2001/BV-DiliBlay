package dev.aaa1115910.bv.wjzfocus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.invalidateFocusProperties
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusPropertiesModifierNode
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.InspectorInfo
import dev.aaa1115910.bv.util.isKeyDown

/**
 * 只监听当前节点焦点变化，不注册新的焦点目标。
 *
 * 这是 WjzFocus 体系内所有“只观察焦点状态，不注册焦点节点”场景的统一底层封装。
 */
fun Modifier.wjzObserveFocusChanged(
    onChanged: (Boolean) -> Unit
): Modifier {
    return this.then(WjzObserveFocusChangedElement(onChanged))
}

/**
 * 只监听当前节点焦点变化的 node element。
 */
private data class WjzObserveFocusChangedElement(
    val onChanged: (Boolean) -> Unit
) : ModifierNodeElement<WjzObserveFocusChangedNode>() {
    override fun create(): WjzObserveFocusChangedNode {
        return WjzObserveFocusChangedNode(onChanged)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "wjzObserveFocusChanged"
    }

    override fun update(node: WjzObserveFocusChangedNode) {
        node.onChanged = onChanged
    }

    override fun hashCode(): Int = onChanged.hashCode()
    override fun equals(other: Any?): Boolean {
        return other is WjzObserveFocusChangedElement && other.onChanged == onChanged
    }
}

private class WjzObserveFocusChangedNode(
    var onChanged: (Boolean) -> Unit
) : Modifier.Node(), FocusEventModifierNode {
    override fun onFocusEvent(focusState: FocusState) {
        onChanged(focusState.hasFocus)
    }
}

/**
 * 把当前 Composable 注册为焦点节点，并可选声明节点级出口。
 *
 * 这版实现不再依赖 Composable modifier factory，而是通过 Modifier.Node 承载注册、
 * 布局完成、焦点事件和 exits 处理。
 *
 * 需要特别注意 [requester] 的稳定性：底层允许更新 requester，但普通业务代码应优先使用
 * [Modifier.wjzFocusExits] 的 Composable 便捷版，让语法糖负责 remember。高阶组件如果调用本函数，
 * 应自行保证同一个 UI 节点在重组之间尽量复用同一个 requester。
 *
 * [exits] 属于动态行为，不属于节点身份。出口列表变化只会刷新焦点属性，不会注销节点、
 * 不会改变 generation。这样可以避免状态驱动的出口变化把正在等待的 pending 请求误判为过期。
 *
 * @param attachFocusTarget 为 true 时由 WjzFocus 在当前 modifier 链上创建 focusTarget；
 * 为 false 时只安装注册、requester 和 focus properties，适合 Tab 等自身已经创建 focusTarget 的组件。
 */
fun Modifier.wjzFocusableExits(
    nodeId: WjzFocusNodeId,
    layer: WjzFocusLayer,
    scopeId: WjzFocusScopeId? = null,
    strategy: WjzFocusRestoreStrategy = WjzFocusRestoreStrategy.RequestFocus,
    fallback: Boolean = false,
    globalFallback: Boolean = false,
    requestPolicy: WjzFocusRequestPolicy = WjzFocusAllowAllRequests,
    enabled: Boolean = true,
    attachFocusTarget: Boolean = true,
    requester: FocusRequester = FocusRequester(),
    exits: List<WjzFocusNodeExit> = emptyList(),
    directionHandlers: List<WjzFocusDirectionHandler> = emptyList(),
    onFocusChanged: (Boolean) -> Unit = {}
): Modifier {
    if (!enabled) return this

    val base = this
        .then(
            WjzFocusRegistrationElement(
                nodeId = nodeId,
                layer = layer,
                scopeId = scopeId,
                strategy = strategy,
                fallback = fallback,
                globalFallback = globalFallback,
                requestPolicy = requestPolicy,
                requester = requester,
                exits = exits,
                directionHandlers = directionHandlers,
                onFocusChanged = onFocusChanged
            )
        )
        .focusRequester(requester)

    return if (attachFocusTarget) {
        base.focusTarget()
    } else {
        base
    }
}

/**
 * 把已有 [FocusRequester] 注册为焦点节点，但不声明节点级出口。
 */
fun Modifier.wjzFocusNode(
    nodeId: WjzFocusNodeId,
    requester: FocusRequester = FocusRequester(),
    layer: WjzFocusLayer,
    scopeId: WjzFocusScopeId? = null,
    strategy: WjzFocusRestoreStrategy = WjzFocusRestoreStrategy.RequestFocus,
    fallback: Boolean = false,
    globalFallback: Boolean = false,
    requestPolicy: WjzFocusRequestPolicy = WjzFocusAllowAllRequests,
    enabled: Boolean = true,
    attachFocusTarget: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {}
): Modifier {
    return wjzFocusableExits(
        nodeId = nodeId,
        layer = layer,
        scopeId = scopeId,
        strategy = strategy,
        fallback = fallback,
        globalFallback = globalFallback,
        requestPolicy = requestPolicy,
        enabled = enabled,
        attachFocusTarget = attachFocusTarget,
        requester = requester,
        exits = emptyList(),
        directionHandlers = emptyList(),
        onFocusChanged = onFocusChanged
    )
}

/**
 * 创建 remembered 原生焦点 requester，供少数需要显式持有 requester 的位置使用。
 */
@Composable
fun rememberWjzFocusRequester(): FocusRequester {
    return remember { FocusRequester() }
}

/**
 * 节点注册、布局完成和焦点回调都由这个元素承载。
 *
 * Element 只负责把最新参数传给 Node。真正需要长期保存的运行时状态，例如 generation、
 * placed、上一次注册的 identity，都保存在 [WjzFocusRegistrationNode] 中。
 */
private data class WjzFocusRegistrationElement(
    val nodeId: WjzFocusNodeId,
    val layer: WjzFocusLayer,
    val scopeId: WjzFocusScopeId?,
    val strategy: WjzFocusRestoreStrategy,
    val fallback: Boolean,
    val globalFallback: Boolean,
    val requestPolicy: WjzFocusRequestPolicy,
    val requester: FocusRequester,
    val exits: List<WjzFocusNodeExit>,
    val directionHandlers: List<WjzFocusDirectionHandler>,
    val onFocusChanged: (Boolean) -> Unit
) : ModifierNodeElement<WjzFocusRegistrationNode>() {
    override fun create(): WjzFocusRegistrationNode {
        return WjzFocusRegistrationNode(
            nodeId = nodeId,
            layer = layer,
            scopeId = scopeId,
            strategy = strategy,
            fallback = fallback,
            globalFallback = globalFallback,
            requestPolicy = requestPolicy,
            requester = requester,
            exits = exits,
            directionHandlers = directionHandlers,
            onFocusChanged = onFocusChanged
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "wjzFocusableExits"
        properties["nodeId"] = nodeId.value
        properties["layer"] = layer
        properties["scopeId"] = scopeId?.value
        properties["strategy"] = strategy
        properties["fallback"] = fallback
        properties["globalFallback"] = globalFallback
        properties["requestPolicy"] = requestPolicy
        properties["enabled"] = true
        properties["exits"] = exits.size
        properties["directionHandlers"] = directionHandlers.size
    }

    override fun update(node: WjzFocusRegistrationNode) {
        node.update(
            nodeId = nodeId,
            layer = layer,
            scopeId = scopeId,
            strategy = strategy,
            fallback = fallback,
            globalFallback = globalFallback,
            requestPolicy = requestPolicy,
            requester = requester,
            exits = exits,
            directionHandlers = directionHandlers,
            onFocusChanged = onFocusChanged
        )
    }
}

private class WjzFocusRegistrationNode(
    nodeId: WjzFocusNodeId,
    layer: WjzFocusLayer,
    scopeId: WjzFocusScopeId?,
    strategy: WjzFocusRestoreStrategy,
    fallback: Boolean,
    globalFallback: Boolean,
    requestPolicy: WjzFocusRequestPolicy,
    private var requester: FocusRequester,
    private var exits: List<WjzFocusNodeExit>,
    private var directionHandlers: List<WjzFocusDirectionHandler>,
    onFocusChanged: (Boolean) -> Unit
) : Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    LayoutAwareModifierNode,
    FocusEventModifierNode,
    FocusPropertiesModifierNode,
    ObserverModifierNode,
    KeyInputModifierNode {

    /**
     * 节点身份 key。
     *
     * 只有这些字段改变时，才需要注销旧节点并重新注册，从而生成新的 generation。
     * requestPolicy 和语义身份绑定；它决定节点是否允许 WjzFocus 请求落焦，变化时需要重新注册节点。
     * requester、exits、directionHandlers、onFocusChanged 都是动态行为，不能放进 identity，否则业务状态变化
     * 会导致过度 unregister/register，使 pending request 的 generation 校验失效。
     */
    private data class RegistrationIdentityKey(
        val nodeId: WjzFocusNodeId,
        val layer: WjzFocusLayer,
        val scopeId: WjzFocusScopeId?,
        val strategy: WjzFocusRestoreStrategy,
        val fallback: Boolean,
        val globalFallback: Boolean,
        val requestPolicy: WjzFocusRequestPolicy
    )

    private var identityKey = RegistrationIdentityKey(
        nodeId = nodeId,
        layer = layer,
        scopeId = scopeId,
        strategy = strategy,
        fallback = fallback,
        globalFallback = globalFallback,
        requestPolicy = requestPolicy
    )
    private var coordinator: WjzFocusCoordinator? = null
    private var generation: Int = 0
    private var placed = false
    private var lastBounds: Rect? = null
    private var lastDisabledFocusContext = WjzDisabledFocusContext()
    private var onFocusChangedCallback: (Boolean) -> Unit = onFocusChanged
    private val routerDirectionHandler = WjzFocusDirectionHandler { direction, currentCoordinator ->
        handleAncestorRouters(direction, currentCoordinator)
    }

    override fun onAttach() {
        // Modifier.Node 可能被复用。重新 attach 时必须先把 placed 清回 false，
        // 等真正 onPlaced 后再通知 coordinator 该节点可 request。
        placed = false
        syncRegistration(force = true)
    }

    override fun onDetach() {
        // detach 时清理注册和 placed 状态，避免复用节点带着旧布局状态提前参与 requestFocus。
        unregister()
        placed = false
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        // mounted 只表示注册完成；placed 表示布局已经发生。只有两者都满足时，
        // coordinator 才会把该节点视为 requestable。
        placed = true
        val oldBounds = lastBounds
        val newBounds = coordinates.boundsInRoot()
        lastBounds = newBounds
        val currentCoordinator = coordinator
        if (generation != 0) {
            currentCoordinator?.markPlaced(
                identityKey.nodeId,
                generation,
                newBounds,
                lastDisabledFocusContext
            )
            if (oldBounds != newBounds && currentCoordinator != null) {
                invalidateFocusProperties()
            }
        }
    }

    override fun onFocusEvent(focusState: FocusState) {
        onFocusChangedCallback(focusState.hasFocus)
        coordinator?.updateFocus(identityKey.nodeId, focusState.hasFocus)
    }

    override fun onObservedReadsChanged() {
        syncRegistration(force = false)
    }

    override fun applyFocusProperties(focusProperties: FocusProperties) {
        val currentCoordinator = currentCoordinator()
        val currentDisabledFocusContext = currentDisabledFocusContext()
        val currentDirectionHandlers = listOf(routerDirectionHandler) + directionHandlers
        val currentExits = exits
        val disabledByRegion = if (currentCoordinator != null && generation != 0) {
            currentCoordinator.shouldApplyDisabledFocusProperties(
                nodeId = identityKey.nodeId,
                generation = generation,
                bounds = lastBounds,
                disabledFocusContext = currentDisabledFocusContext
            )
        } else {
            false
        }
        if (currentCoordinator != null && generation != 0) {
            currentCoordinator.updateFocusRouting(
                nodeId = identityKey.nodeId,
                generation = generation,
                directionHandlers = currentDirectionHandlers,
                exits = currentExits
            )
        }
        if (disabledByRegion) {
            focusProperties.canFocus = false
        }
        if (currentDirectionHandlers.isEmpty() && currentExits.isEmpty()) return

        focusProperties.onExit = {
            val direction = requestedFocusDirection
            if (handleDirectionalExit(direction, currentCoordinator)) {
                cancelFocusChange()
            }
        }
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean = false

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!event.isKeyDown()) return false
        val direction = event.wjzFocusDirection() ?: return false
        val currentCoordinator = coordinator ?: return false
        return handleDirectionalExit(direction, currentCoordinator)
    }

    private fun handleDirectionalExit(
        direction: FocusDirection,
        currentCoordinator: WjzFocusCoordinator?
    ): Boolean {
        val handledByRouter = handleAncestorRouters(
            direction = direction,
            currentCoordinator = currentCoordinator
        )
        if (handledByRouter) {
            return true
        }

        val handledByLocalHandler = directionHandlers.any { handler ->
            handler.handle(direction, currentCoordinator)
        }
        if (handledByLocalHandler) {
            return true
        }

        val exit = exits.firstOrNull { it.direction == direction }
        return exit != null && exit.consume(currentCoordinator)
    }

    private fun handleAncestorRouters(
        direction: FocusDirection,
        currentCoordinator: WjzFocusCoordinator?
    ): Boolean {
        if (!isAttached) return false

        var handled = false
        traverseAncestors(WjzFocusRouterTraverseKey) { traversableNode ->
            val routerNode = traversableNode as? WjzFocusRouterNodeContract ?: return@traverseAncestors true
            val consumed = routerNode.handle(direction, currentCoordinator)
            handled = consumed
            !consumed // true=继续找，false=停止遍历
        }
        return handled
    }

    fun update(
        nodeId: WjzFocusNodeId,
        layer: WjzFocusLayer,
        scopeId: WjzFocusScopeId?,
        strategy: WjzFocusRestoreStrategy,
        fallback: Boolean,
        globalFallback: Boolean,
        requestPolicy: WjzFocusRequestPolicy,
        requester: FocusRequester,
        exits: List<WjzFocusNodeExit>,
        directionHandlers: List<WjzFocusDirectionHandler>,
        onFocusChanged: (Boolean) -> Unit
    ) {
        val newIdentity = RegistrationIdentityKey(
            nodeId = nodeId,
            layer = layer,
            scopeId = scopeId,
            strategy = strategy,
            fallback = fallback,
            globalFallback = globalFallback,
            requestPolicy = requestPolicy
        )
        val identityChanged = newIdentity != identityKey
        val exitsChanged = this.exits != exits
        val directionHandlersChanged = this.directionHandlers != directionHandlers
        val requesterChanged = this.requester != requester
        onFocusChangedCallback = onFocusChanged

        if (identityChanged) {
            // 身份变化意味着旧节点已经不是同一个语义目标，必须重新注册并生成新 generation。
            unregister()
            identityKey = newIdentity
            this.requester = requester
            this.exits = exits
            this.directionHandlers = directionHandlers
            syncRegistration(force = true)
            invalidateFocusProperties()
        } else {
            // 行为变化只更新 Node 内部状态，不触碰 generation。
            this.requester = requester
            this.exits = exits
            this.directionHandlers = directionHandlers
            if (requesterChanged && generation != 0) {
                coordinator?.updateRequester(
                    nodeId = identityKey.nodeId,
                    generation = generation,
                    requester = requester
                )
            }
            if (exitsChanged || directionHandlersChanged) {
                // 完整 routing 必须包含节点自己的 router 入口和本地 handlers；
                // router 是否真的命中祖先由消费当下现场遍历确认，这里先让旧入口失效并触发重算。
                coordinator?.invalidateFocusRouting(
                    nodeId = identityKey.nodeId,
                    generation = generation
                )
                invalidateFocusProperties()
            }
            syncRegistration(force = false)
        }
    }

    private fun currentCoordinator(): WjzFocusCoordinator? {
        return currentValueOf(LocalWjzFocusCoordinator)
    }

    private fun currentScopeId(): WjzFocusScopeId? {
        return currentValueOf(LocalWjzFocusScopeId)
    }

    private fun currentDisabledFocusContext(): WjzDisabledFocusContext {
        return currentValueOf(LocalWjzDisabledFocusContext)
    }

    private fun syncRegistration(force: Boolean) {
        observeReads {
            syncRegistration(
                force = force,
                currentCoordinator = currentCoordinator(),
                currentScopeId = currentScopeId(),
                currentDisabledFocusContext = currentDisabledFocusContext()
            )
        }
    }

    private fun syncRegistration(
        force: Boolean,
        currentCoordinator: WjzFocusCoordinator?,
        currentScopeId: WjzFocusScopeId?,
        currentDisabledFocusContext: WjzDisabledFocusContext
    ) {
        if (currentCoordinator == null) {
            unregister()
            return
        }

        val disabledFocusContextChanged = currentDisabledFocusContext != lastDisabledFocusContext
        lastDisabledFocusContext = currentDisabledFocusContext
        val resolvedScopeId = identityKey.scopeId ?: currentScopeId
        val currentKey = identityKey.copy(scopeId = resolvedScopeId)
        val coordinatorChanged = currentCoordinator !== coordinator
        val registrationChanged = force || coordinatorChanged || currentKey != lastRegisteredKey
        if (!registrationChanged) {
            if (disabledFocusContextChanged && generation != 0) {
                currentCoordinator.updateDisabledFocusContext(
                    nodeId = identityKey.nodeId,
                    generation = generation,
                    disabledFocusContext = currentDisabledFocusContext
                )
                invalidateFocusProperties()
            }
            if (placed && generation != 0) {
                currentCoordinator.markPlaced(
                    identityKey.nodeId,
                    generation,
                    lastBounds,
                    currentDisabledFocusContext
                )
            }
            return
        }

        unregister()
        generation = currentCoordinator.register(
            WjzFocusNode(
                id = identityKey.nodeId,
                layer = identityKey.layer,
                requester = requester,
                strategy = identityKey.strategy,
                scopeId = resolvedScopeId,
                fallback = identityKey.fallback,
                globalFallback = identityKey.globalFallback,
                requestPolicy = identityKey.requestPolicy,
                directionHandlers = directionHandlers,
                exits = exits
            )
        )
        currentCoordinator.registerFocusPropertiesInvalidator(
            nodeId = identityKey.nodeId,
            generation = generation
        ) {
            if (isAttached) {
                invalidateFocusProperties()
            }
        }
        lastRegisteredKey = currentKey
        coordinator = currentCoordinator
        if (placed) {
            currentCoordinator.markPlaced(
                identityKey.nodeId,
                generation,
                lastBounds,
                currentDisabledFocusContext
            )
        }
    }

    private fun unregister() {
        val registeredCoordinator = coordinator
        if (registeredCoordinator != null && generation != 0) {
            registeredCoordinator.unregisterFocusPropertiesInvalidator(identityKey.nodeId, generation)
            registeredCoordinator.unregister(identityKey.nodeId, generation)
        }
        generation = 0
        lastRegisteredKey = null
        coordinator = null
    }

    private var lastRegisteredKey: RegistrationIdentityKey? = null
}

