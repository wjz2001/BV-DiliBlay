package dev.aaa1115910.bv.wjzfocus

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import dev.aaa1115910.bv.util.isConfirmKey

/**
 * 单行 TextField 的 WjzFocus 接入。
 *
 * 这个 modifier 只面向单行 TextField：DPadCenter/Enter 不消费，交给输入框自身处理；
 * Left/Right 不消费，交给输入框移动光标；Up/Down 不在 KeyInput 中拦截，交给 WjzFocus exits/onExit
 * 处理离开输入框的方向导航；Back/Escape 在当前输入框有焦点时，如果传入 [backEntryId] 则请求该
 * WjzFocus entry，否则尝试通过 WjzFocus 恢复当前 scope 内非当前输入框节点；失败时仍消费按键，
 * 避免误触发页面返回。
 */
@Composable
fun Modifier.wjzTextFieldFocus(
    nodeId: WjzFocusNodeId,
    scopeId: WjzFocusScopeId? = null,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    fallback: Boolean = false,
    requestPolicy: WjzFocusRequestPolicy = WjzFocusAllowAllRequests,
    enabled: Boolean = true,
    backEntryId: WjzFocusEntryId? = null,
    exits: WjzFocusExitsBuilder.() -> Unit = {},
    onFocused: () -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {}
): Modifier {
    val requester = rememberWjzFocusRequester()
    return then(
        WjzTextFieldFocusElement(
            nodeId = nodeId,
            scopeId = scopeId,
            layer = layer,
            enabled = enabled,
            backEntryId = backEntryId
        )
    ).wjzFocusExits(
        nodeId = nodeId,
        scopeId = scopeId,
        layer = layer,
        fallback = fallback,
        requestPolicy = requestPolicy,
        enabled = enabled,
        requester = requester,
        exits = exits,
        onFocused = onFocused,
        onFocusChanged = onFocusChanged
    )
}

/**
 * 当前 WjzFocus scope 内单行 TextField 的 WjzFocus 接入。
 *
 * 这个 modifier 只面向单行 TextField：DPadCenter/Enter 不消费，交给输入框自身处理；
 * Left/Right 不消费，交给输入框移动光标；Up/Down 不在 KeyInput 中拦截，交给 WjzFocus exits/onExit
 * 处理离开输入框的方向导航；Back/Escape 在当前输入框有焦点时，如果传入 [backEntryId] 则请求该
 * WjzFocus entry，否则尝试通过 WjzFocus 恢复当前 scope 内非当前输入框节点；失败时仍消费按键，
 * 避免误触发页面返回。
 */
@Composable
fun Modifier.wjzTextFieldFocus(
    localId: WjzFocusLocalId,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    fallback: Boolean = false,
    requestPolicy: WjzFocusRequestPolicy = WjzFocusAllowAllRequests,
    enabled: Boolean = true,
    backEntryId: WjzFocusEntryId? = null,
    exits: WjzFocusExitsBuilder.() -> Unit = {},
    onFocused: () -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {}
): Modifier {
    val scopeId = LocalWjzFocusScopeId.current
    require(scopeId != null) {
        "wjz text field focus local id '${localId.value}' requires LocalWjzFocusScopeId.current"
    }
    return wjzTextFieldFocus(
        nodeId = scopeId.resolve(localId),
        scopeId = scopeId,
        layer = layer,
        fallback = fallback,
        requestPolicy = requestPolicy,
        enabled = enabled,
        backEntryId = backEntryId,
        exits = exits,
        onFocused = onFocused,
        onFocusChanged = onFocusChanged
    )
}

/**
 * TextField focus bridge 的 modifier element。
 *
 * Element 只保存最新参数并同步到 node；真实焦点注册仍由后续 [wjzFocusExits] 完成。
 * 这里单独放一个 key input node，是为了把 Back/Escape 的输入态退出逻辑限制在 TextField 接入点内。
 */
private data class WjzTextFieldFocusElement(
    val nodeId: WjzFocusNodeId,
    val scopeId: WjzFocusScopeId?,
    val layer: WjzFocusLayer,
    val enabled: Boolean,
    val backEntryId: WjzFocusEntryId?
) : ModifierNodeElement<WjzTextFieldFocusNode>() {
    override fun create(): WjzTextFieldFocusNode {
        return WjzTextFieldFocusNode(
            nodeId = nodeId,
            scopeId = scopeId,
            layer = layer,
            enabled = enabled,
            backEntryId = backEntryId
        )
    }

    override fun update(node: WjzTextFieldFocusNode) {
        node.nodeId = nodeId
        node.scopeId = scopeId
        node.layer = layer
        node.enabled = enabled
        node.backEntryId = backEntryId
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "wjzTextFieldFocus"
        properties["nodeId"] = nodeId.value
        properties["scopeId"] = scopeId?.value
        properties["layer"] = layer
        properties["enabled"] = enabled
        properties["backEntryId"] = backEntryId?.value
    }
}

/**
 * 单行 TextField 的按键桥接 node。
 *
 * Center/Enter 和 Left/Right 都交给输入框自身处理，避免破坏输入法提交和光标移动。
 * Up/Down 也不在 key node 中消费，让 WjzFocus 的 onExit/exits 负责方向离开。
 * Back/Escape 只有在当前 TextField 节点确实持焦时才消费，并按 back entry 或同 scope 恢复处理。
 */
private class WjzTextFieldFocusNode(
    var nodeId: WjzFocusNodeId,
    var scopeId: WjzFocusScopeId?,
    var layer: WjzFocusLayer,
    var enabled: Boolean,
    var backEntryId: WjzFocusEntryId?
) : Modifier.Node(), KeyInputModifierNode, CompositionLocalConsumerModifierNode {
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!enabled) return false

        return when (event.key) {
            // 确认键属于输入框自身语义，例如提交、打开输入法候选或由 Material 组件处理。
            Key.DirectionCenter,
            Key.Enter,
            Key.NumPadEnter -> false

            // 单行输入框的左右方向优先用于移动光标；上下方向交给 WjzFocus exits 离开输入框。
            Key.DirectionLeft,
            Key.DirectionRight,
            Key.DirectionUp,
            Key.DirectionDown -> false

            Key.Back,
            Key.Escape -> event.type == KeyEventType.KeyDown && handleBackOrEscape()

            else -> {
                if (event.isConfirmKey()) return false

                false
            }
        }
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        return false
    }

    private fun handleBackOrEscape(): Boolean {
        val coordinator = currentValueOf(LocalWjzFocusCoordinator) ?: return false
        if (!coordinator.hasFocus(nodeId)) return false
        if (coordinator.activeLayer != layer) return false

        backEntryId?.let { entryId ->
            coordinator.submitExternalEntryFocus(
                entryId = entryId,
                layerActivation = activateLayer,
                dedupeKey = "text-field-back-entry"
            )
            return true
        }

        val resolvedScopeId = scopeId ?: currentValueOf(LocalWjzFocusScopeId)
        // 排除当前 TextField，避免普通 scope 恢复优先命中自己，导致 Back 看起来没有效果。
        coordinator.restoreActiveLayerExcludingNode(
            excludedNodeId = nodeId,
            scopeId = resolvedScopeId
        )
        // 即使当前 scope 没有可恢复节点，也消费 Back/Escape，避免 TextField 输入态把返回键泄漏给 Activity/Dialog。
        return true
    }
}
