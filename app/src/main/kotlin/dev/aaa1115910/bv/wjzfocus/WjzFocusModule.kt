package dev.aaa1115910.bv.wjzfocus

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 同时声明公开 entry surface 和焦点 Host 的模块封装。
 *
 * 普通业务优先使用该入口把“外部如何进入模块”和“模块边界如何离开”收口到一起。
 * 模块内部移动仍应由子节点的 [Modifier.wjzFocusRouter]、Lazy router 或更高层组件负责；
 * 本组件不维护内部 controller。
 */
@Composable
fun WjzFocusModule(
    moduleId: String,
    default: () -> WjzFocusResolvedTarget,
    modifier: Modifier = Modifier,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    hostExits: WjzFocusHostExitsBuilder.() -> Unit = {},
    entries: WjzFocusEntriesHostEntriesBuilder.() -> Unit = {},
    content: @Composable () -> Unit
) {
    // 先注册公开 entry，让外部请求可以解析到本模块默认目标或具名目标。
    WjzFocusEntriesHost(
        componentId = moduleId,
        default = default,
        entries = entries
    )

    // Host 只负责边界、layer 和 scope，不参与内部相邻项计算。
    WjzFocusHost(
        modifier = modifier,
        layer = layer,
        scopeId = scopeId,
        exits = WjzFocusHostExitsBuilder().apply(hostExits).exits,
        content = content
    )
}

/**
 * [WjzFocusModule] 的完整 node id 便捷重载。
 *
 * [defaultNodeId] 会被包装成 [WjzFocusDefaultTarget]，适合默认入口固定指向某个节点的模块。
 */
@Composable
fun WjzFocusModule(
    moduleId: String,
    defaultNodeId: String,
    modifier: Modifier = Modifier,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    hostExits: WjzFocusHostExitsBuilder.() -> Unit = {},
    entries: WjzFocusEntriesHostEntriesBuilder.() -> Unit = {},
    content: @Composable () -> Unit
) {
    WjzFocusModule(
        moduleId = moduleId,
        default = { defaultEntry(defaultNodeId, layer, scopeId) },
        modifier = modifier,
        layer = layer,
        scopeId = scopeId,
        hostExits = hostExits,
        entries = entries,
        content = content
    )
}

/**
 * 只声明公开 entry surface，不创建 Host 边界。
 *
 * 适用于外层已经有 [WjzFocusHost]，但当前组合块仍需要暴露 component entry 的场景。
 */
@Composable
fun WjzFocusEntrySurface(
    componentId: String,
    default: () -> WjzFocusResolvedTarget,
    entries: WjzFocusEntriesHostEntriesBuilder.() -> Unit = {}
) {
    WjzFocusEntriesHost(
        componentId = componentId,
        default = default,
        entries = entries
    )
}

/** [WjzFocusEntrySurface] 的完整 node id 便捷重载。 */
@Composable
fun WjzFocusEntrySurface(
    componentId: String,
    defaultNodeId: String,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    entries: WjzFocusEntriesHostEntriesBuilder.() -> Unit = {}
) {
    WjzFocusEntrySurface(
        componentId = componentId,
        default = { defaultEntry(defaultNodeId, layer, scopeId) },
        entries = entries
    )
}

