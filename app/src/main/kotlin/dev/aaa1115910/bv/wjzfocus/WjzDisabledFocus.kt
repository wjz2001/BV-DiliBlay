package dev.aaa1115910.bv.wjzfocus

import androidx.compose.ui.Modifier
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.InspectorInfo

data class WjzDisabledFocusContext(
    val group: Any? = null,
    val zIndex: Float = 0f
)

val LocalWjzDisabledFocusContext = compositionLocalOf { WjzDisabledFocusContext() }

/**
 * 禁用当前 WjzFocus 节点、子树的参与焦点流转。
 *
 * 这个 Modifier 表达的是“当前 UI 不应该成为焦点目标”，暂时不允许接收焦点，供严格恢复窗口和物理路径判断使用。
 *
 * 它和 `exits { cancel(...) }` 的不同之处：
 *
 * 1. `cancel(...)` 作用于已经获得焦点的 WjzFocus 节点，用来消费离开方向；
 * 2. [wjzDisabledFocus] 作用于 WjzFocus 请求、恢复、fallback 阶段，让当前区域内的已注册 WjzFocus 节点不参与落焦；
 * 3. disabled 状态下不应把该 UI 作为 fallback 或恢复目标。
 *
 * 业务代码不要直接写 Compose 原生禁焦 API，应使用本封装保持 WjzFocus 语义统一。
 *
 * 常见写法：
 *
 * ```kotlin
 * modifier.wjzDisabledFocus()
 * ```
 *
 * 等价于 `modifier.wjzDisabledFocus(true)`，表示无条件禁用当前 UI 的焦点参与。
 *
 * ```kotlin
 * modifier.wjzDisabledFocus(false)
 * ```
 *
 * 表示不禁用焦点，会直接返回原 modifier。它不是“禁用 false 个焦点”，也不是“切换焦点状态”，
 * 只是一个条件开关为 false 的空操作。
 *
 * @param disabled 为 true 时禁用焦点；为 false 时保持原 modifier，不额外声明焦点属性。
 */
fun Modifier.wjzDisabledFocus(disabled: Boolean = true): Modifier {
    if (!disabled) return this

    return this.then(WjzDisabledFocusElement)
}

private data object WjzDisabledFocusElement : ModifierNodeElement<WjzDisabledFocusNode>() {
    override fun create(): WjzDisabledFocusNode {
        return WjzDisabledFocusNode()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "wjzDisabledFocus"
        properties["disabled"] = true
    }

    override fun update(node: WjzDisabledFocusNode) = Unit
}

private class WjzDisabledFocusNode : Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    LayoutAwareModifierNode,
    ObserverModifierNode {

    private val token = Any()
    private var coordinator: WjzFocusCoordinator? = null
    private var lastBounds: Rect? = null

    override fun onAttach() {
        syncRegistration()
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        lastBounds = coordinates.boundsInRoot()
        syncRegistration()
    }

    override fun onObservedReadsChanged() {
        syncRegistration()
    }

    override fun onDetach() {
        unregister()
        lastBounds = null
    }

    private fun syncRegistration() {
        observeReads {
            syncRegistration(
                currentCoordinator = currentValueOf(LocalWjzFocusCoordinator),
                bounds = lastBounds,
                disabledFocusContext = currentValueOf(LocalWjzDisabledFocusContext)
            )
        }
    }

    private fun syncRegistration(
        currentCoordinator: WjzFocusCoordinator?,
        bounds: Rect?,
        disabledFocusContext: WjzDisabledFocusContext
    ) {
        if (currentCoordinator == null || bounds == null) {
            unregister()
            return
        }

        val previousCoordinator = coordinator
        if (previousCoordinator !== currentCoordinator) {
            currentCoordinator.registerDisabledFocusRegion(token, bounds, disabledFocusContext)
            coordinator = currentCoordinator
            previousCoordinator?.unregisterDisabledFocusRegion(
                token = token,
                consumePendingRequests = false
            )
            return
        }
        currentCoordinator.registerDisabledFocusRegion(token, bounds, disabledFocusContext)
    }

    private fun unregister() {
        coordinator?.unregisterDisabledFocusRegion(token)
        coordinator = null
    }
}
