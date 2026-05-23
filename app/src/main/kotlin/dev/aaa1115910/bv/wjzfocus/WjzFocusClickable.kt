package dev.aaa1115910.bv.wjzfocus

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import dev.aaa1115910.bv.util.isConfirmKey
import kotlinx.coroutines.launch

/**
 * TV 高频组合：焦点注册 + 确认键点击。
 *
 * 该重载适合高阶组件已经持有稳定 [requester] 的场景。[id] 是完整 node id，
 * 不会自动读取当前 scope。
 *
 * [interactionSource] 只反映遥控器确认键的 press/release，focused 状态仍由 WjzFocus 提供。
 */
fun Modifier.wjzClickableFocus(
    id: String,
    requester: FocusRequester,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    strategy: WjzFocusRestoreStrategy = WjzFocusRestoreStrategy.RequestFocus,
    fallback: Boolean = false,
    globalFallback: Boolean = false,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    exits: WjzFocusExitsBuilder.() -> Unit = {},
    onFocused: () -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {}
): Modifier {
    return then(WjzClickableFocusElement(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        interactionSource = interactionSource
    )).wjzFocusExits(
        id = id,
        layer = layer,
        strategy = strategy,
        fallback = fallback,
        globalFallback = globalFallback,
        enabled = enabled,
        requester = requester,
        exits = exits,
        onFocused = onFocused,
        onFocusChanged = onFocusChanged
    )
}

/** 保存点击回调和 enabled 状态的 Modifier element。 */
private data class WjzClickableFocusElement(
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)?,
    val enabled: Boolean,
    val interactionSource: MutableInteractionSource?
) : ModifierNodeElement<WjzClickableFocusNode>() {
    override fun create(): WjzClickableFocusNode {
        return WjzClickableFocusNode(
            onClick = onClick,
            onLongClick = onLongClick,
            enabled = enabled,
            interactionSource = interactionSource
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "wjzClickableFocus"
        properties["enabled"] = enabled
        properties["hasLongClick"] = onLongClick != null
        properties["hasInteractionSource"] = interactionSource != null
    }

    override fun update(node: WjzClickableFocusNode) {
        node.update(
            onClick = onClick,
            onLongClick = onLongClick,
            enabled = enabled,
            interactionSource = interactionSource
        )
    }
}

/**
 * 处理遥控器确认键的 Modifier.Node。
 *
 * KeyDown/KeyUp 成对消费，普通点击只在 KeyUp 触发；长按只在第一次 long-press down 触发一次。
 */
private class WjzClickableFocusNode(
    var onClick: () -> Unit,
    var onLongClick: (() -> Unit)?,
    var enabled: Boolean,
    var interactionSource: MutableInteractionSource?
) : Modifier.Node(), KeyInputModifierNode {
    private var pressActive = false
    private var longPressTriggered = false
    private var pressInteraction: PressInteraction.Press? = null

    fun update(
        onClick: () -> Unit,
        onLongClick: (() -> Unit)?,
        enabled: Boolean,
        interactionSource: MutableInteractionSource?
    ) {
        if ((this.enabled && !enabled) || this.interactionSource !== interactionSource) {
            cancelPress()
        }
        this.onClick = onClick
        this.onLongClick = onLongClick
        this.enabled = enabled
        this.interactionSource = interactionSource
    }

    override fun onDetach() {
        longPressTriggered = false
        cancelPress()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!enabled || !event.isConfirmKey()) {
            return false
        }

        return when (event.type) {
            KeyEventType.KeyDown -> {
                if (!pressActive) {
                    pressActive = true
                    emitPress()
                }
                if (event.nativeKeyEvent.isLongPress && !longPressTriggered) {
                    longPressTriggered = true
                    onLongClick?.invoke()
                }
                true
            }

            KeyEventType.KeyUp -> {
                if (!pressActive) {
                    return false
                }

                val click = !longPressTriggered
                if (longPressTriggered) {
                    longPressTriggered = false
                }
                releasePress()
                if (click) {
                    onClick()
                }
                true
            }

            else -> true
        }
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        return false
    }

    private fun emitPress() {
        val source = interactionSource ?: return
        if (pressInteraction != null) return

        val press = PressInteraction.Press(Offset.Zero)
        pressInteraction = press
        coroutineScope.launch {
            source.emit(press)
        }
    }

    private fun releasePress() {
        val source = interactionSource
        val press = pressInteraction
        pressActive = false
        pressInteraction = null
        if (source == null || press == null) {
            return
        }

        coroutineScope.launch {
            source.emit(PressInteraction.Release(press))
        }
    }

    private fun cancelPress() {
        val source = interactionSource
        val press = pressInteraction
        pressActive = false
        pressInteraction = null
        longPressTriggered = false
        if (source == null || press == null) return

        coroutineScope.launch {
            source.emit(PressInteraction.Cancel(press))
        }
    }
}

/**
 * 使用完整 String node id 的便捷版本，requester 由内部 remember。
 *
 * [interactionSource] 只反映遥控器确认键的 press/release，focused 状态仍由 WjzFocus 提供。
 */
@Composable
fun Modifier.wjzClickableFocus(
    id: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    strategy: WjzFocusRestoreStrategy = WjzFocusRestoreStrategy.RequestFocus,
    fallback: Boolean = false,
    globalFallback: Boolean = false,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    exits: WjzFocusExitsBuilder.() -> Unit = {},
    onFocused: () -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {}
): Modifier {
    val requester = remember { FocusRequester() }
    return wjzClickableFocus(
        id = id,
        requester = requester,
        onClick = onClick,
        onLongClick = onLongClick,
        layer = layer,
        strategy = strategy,
        fallback = fallback,
        globalFallback = globalFallback,
        enabled = enabled,
        interactionSource = interactionSource,
        exits = exits,
        onFocused = onFocused,
        onFocusChanged = onFocusChanged
    )
}

/**
 * 使用完整 [WjzFocusNodeId] 的便捷版本，requester 由内部 remember。
 *
 * [scopeId] 只用于恢复和边界筛选，不参与 nodeId 拼接。
 *
 * [interactionSource] 只反映遥控器确认键的 press/release，focused 状态仍由 WjzFocus 提供。
 */
@Composable
fun Modifier.wjzClickableFocus(
    nodeId: WjzFocusNodeId,
    scopeId: WjzFocusScopeId? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    strategy: WjzFocusRestoreStrategy = WjzFocusRestoreStrategy.RequestFocus,
    fallback: Boolean = false,
    globalFallback: Boolean = false,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    exits: WjzFocusExitsBuilder.() -> Unit = {},
    onFocused: () -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {}
): Modifier {
    val requester = remember { FocusRequester() }
    return then(WjzClickableFocusElement(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        interactionSource = interactionSource
    )).wjzFocusExits(
        nodeId = nodeId,
        scopeId = scopeId,
        layer = layer,
        strategy = strategy,
        fallback = fallback,
        globalFallback = globalFallback,
        enabled = enabled,
        requester = requester,
        exits = exits,
        onFocused = onFocused,
        onFocusChanged = onFocusChanged
    )
}

/**
 * 使用当前 scope 内相对 [WjzFocusLocalId] 的便捷版本。
 *
 * 该重载会通过 [WjzFocusScopeId.resolve] 拼成 `scope|local` 完整 node id。
 *
 * [interactionSource] 只反映遥控器确认键的 press/release，focused 状态仍由 WjzFocus 提供。
 */
@Composable
fun Modifier.wjzClickableFocus(
    localId: WjzFocusLocalId,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    strategy: WjzFocusRestoreStrategy = WjzFocusRestoreStrategy.RequestFocus,
    fallback: Boolean = false,
    globalFallback: Boolean = false,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    exits: WjzFocusExitsBuilder.() -> Unit = {},
    onFocused: () -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {}
): Modifier {
    val requester = remember { FocusRequester() }
    return then(WjzClickableFocusElement(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        interactionSource = interactionSource
    )).wjzFocusExits(
        localId = localId,
        layer = layer,
        strategy = strategy,
        fallback = fallback,
        globalFallback = globalFallback,
        enabled = enabled,
        requester = requester,
        exits = exits,
        onFocused = onFocused,
        onFocusChanged = onFocusChanged
    )
}
