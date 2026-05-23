package dev.aaa1115910.bv.wjzfocus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester

/** 外部入口请求是否主动切换 active layer。 */
enum class WjzFocusLayerActivation {
    ActivateLayer,
    KeepLayer
}

val activateLayer: WjzFocusLayerActivation = WjzFocusLayerActivation.ActivateLayer
val keepLayer: WjzFocusLayerActivation = WjzFocusLayerActivation.KeepLayer

private val WjzFocusLayerActivation.shouldActivateLayer: Boolean
    get() = when (this) {
        WjzFocusLayerActivation.ActivateLayer -> true
        WjzFocusLayerActivation.KeepLayer -> false
    }

/**
 * 按组合生命周期安装默认 WjzFocus Debug Overlay。
 *
 * 调用方不需要手动 clear；离开当前 composition 时会自动清理 registry，避免全局 registry
 * 长期持有 overlay content。
 */
@Composable
fun WjzFocusDebugOverlayEffect(enabled: Boolean) {
    DisposableEffect(enabled) {
        WjzFocusDebugOverlayRegistry.installDefault(enabled)
        onDispose {
            WjzFocusDebugOverlayRegistry.clear()
        }
    }
}

/** 使用当前 scope 的 local id 注册 Content layer 焦点节点；特殊 layer 显式传入 [layer]。 */
@Composable
fun Modifier.wjzFocusLocal(
    localId: WjzFocusLocalId,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    strategy: WjzFocusRestoreStrategy = WjzFocusRestoreStrategy.RequestFocus,
    fallback: Boolean = false,
    globalFallback: Boolean = false,
    requestPolicy: WjzFocusRequestPolicy = WjzFocusAllowAllRequests,
    enabled: Boolean = true,
    attachFocusTarget: Boolean = true,
    exits: WjzFocusExitsBuilder.() -> Unit = {},
    onFocused: () -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {}
): Modifier {
    return wjzFocusExits(
        localId = localId,
        layer = layer,
        strategy = strategy,
        fallback = fallback,
        globalFallback = globalFallback,
        requestPolicy = requestPolicy,
        enabled = enabled,
        attachFocusTarget = attachFocusTarget,
        exits = exits,
        onFocused = onFocused,
        onFocusChanged = onFocusChanged
    )
}

/** 使用当前 scope 的 local id 注册 Content layer 焦点节点，并显式复用调用方 requester。 */
@Composable
fun Modifier.wjzFocusLocal(
    localId: WjzFocusLocalId,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    strategy: WjzFocusRestoreStrategy = WjzFocusRestoreStrategy.RequestFocus,
    fallback: Boolean = false,
    globalFallback: Boolean = false,
    requestPolicy: WjzFocusRequestPolicy = WjzFocusAllowAllRequests,
    enabled: Boolean = true,
    attachFocusTarget: Boolean = true,
    requester: FocusRequester,
    exits: WjzFocusExitsBuilder.() -> Unit = {},
    onFocused: () -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {}
): Modifier {
    return wjzFocusExits(
        localId = localId,
        layer = layer,
        strategy = strategy,
        fallback = fallback,
        globalFallback = globalFallback,
        requestPolicy = requestPolicy,
        enabled = enabled,
        attachFocusTarget = attachFocusTarget,
        requester = requester,
        exits = exits,
        onFocused = onFocused,
        onFocusChanged = onFocusChanged
    )
}

/**
 * 构建 [WjzFocusHost] 的 Host 级 exits 列表。
 *
 * Host exits 表达整个 scope/模块在某个方向离开时的兜底出口，优先级低于当前持焦节点自己的
 * router 和节点级 exits。业务代码应优先使用这个语法糖，而不是直接依赖
 * [WjzFocusHostExitsBuilder] 的内部存储结构。
 *
 * 示例：
 *
 * ```kotlin
 * WjzFocusHost(
 *     exits = wjzFocusHostExits {
 *         up move SearchInputKeywordEntryId
 *     }
 * ) {
 *     ...
 * }
 * ```
 */
fun wjzFocusHostExits(
    exits: WjzFocusHostExitsBuilder.() -> Unit
): List<WjzFocusHostExit> {
    return WjzFocusHostExitsBuilder().apply(exits).exits
}

/**
 * 构建一个只允许指定公开 entry 进入目标节点的请求策略。
 *
 * 这个策略只检查 [WjzFocusRequestSource.Entry]，因此它会拒绝所有非 entry 来源，例如：
 *
 * - 直接 node 请求；
 * - router 内部请求；
 * - restore/fallback 恢复请求；
 * - 没有携带 entry 信息的低层请求。
 *
 * 适用场景是“这个节点必须从某个明确入口进入”，例如搜索输入框只允许软键盘向上通过`searchInput/keyword` 进入，而不接受普通恢复或其它组件绕行请求。
 *
 * 注意：该策略只过滤 WjzFocus coordinator 主导的请求。没有被 WjzFocus router/exits 消费的 Compose 原生几何焦点搜索可能不会经过 coordinator，
 * 因此不属于该策略的拦截范围。
 */
fun wjzFocusAllowOnlyEntries(
    vararg entryIds: WjzFocusEntryId
): WjzFocusRequestPolicy {
    val allowedEntries = entryIds.toSet()
    return WjzFocusRequestPolicy { source ->
        source is WjzFocusRequestSource.Entry && source.entryId in allowedEntries
    }
}

/**
 * 构建一个拒绝指定公开 entry，允许其它来源进入目标节点的请求策略。
 *
 * 这个策略只把 [entryIds] 视为黑名单：当来源是 [WjzFocusRequestSource.Entry] 且 entry 命中黑名单时，
 * 本次请求会被拒绝；其它 entry、直接 node 请求、router 请求、restore/fallback 等来源都会被允许。
 *
 * 适用场景是“某个公开入口不应该进入当前节点，但其它路径保持原行为”。如果需要严格限制为只能从少数入口进入，请使用 [wjzFocusAllowOnlyEntries]。
 *
 * 注意：该策略只过滤 WjzFocus coordinator 主导的请求。没有被 WjzFocus router/exits 消费的Compose 原生几何焦点搜索可能不会经过 coordinator，
 * 因此不属于该策略的拦截范围。
 */
fun wjzFocusDenyEntries(
    vararg entryIds: WjzFocusEntryId
): WjzFocusRequestPolicy {
    val deniedEntries = entryIds.toSet()
    return WjzFocusRequestPolicy { source ->
        source !is WjzFocusRequestSource.Entry || source.entryId !in deniedEntries
    }
}

/**
 * 构建一个按自定义条件放行请求的策略。
 *
 * [predicate] 返回 true 表示允许请求继续执行，返回 false 表示拒绝请求。它适合 entry 白名单/黑名单无法表达的细粒度场景，
 * 例如同时允许某个 entry 和某类 fallback，或者基于 [WjzFocusSubmitIntent] 判断请求来源。
 *
 *
 * 优先使用 [wjzFocusAllowOnlyEntries] 和 [wjzFocusDenyEntries] 表达常见 entry 过滤，
 * 只有需要同时检查多种 [WjzFocusRequestSource] 时再使用这个通用入口。
 *
 *
 * 注意：该策略只过滤 WjzFocus coordinator 主导的请求。
 * 没有被 WjzFocus router/exits 消费的Compose 原生几何焦点搜索可能不会经过 coordinator，因此不属于该策略的拦截范围。
 */
fun wjzFocusAllowOnly(
    predicate: (WjzFocusRequestSource) -> Boolean
): WjzFocusRequestPolicy {
    return WjzFocusRequestPolicy(predicate)
}

/**
 * 构建一个按自定义条件拒绝请求的策略。
 *
 * [predicate] 返回 true 表示拒绝请求，返回 false 表示允许请求继续执行。它是 [wjzFocusAllowOnly] 的反向表达，
 * 适合“除了某些来源，其它都保持可进入”的场景。
 *
 * 例如，如果要拒绝所有 fallback，但保留 entry、node 和 restore 请求：
 *
 * ```kotlin
 * requestPolicy = wjzFocusDeny { source ->
 *     source is WjzFocusRequestSource.Fallback
 * }
 * ```
 *
 * 注意：该策略只过滤 WjzFocus coordinator 主导的请求。没有被 WjzFocus router/exits 消费的Compose 原生几何焦点搜索可能不会经过 coordinator，
 * 因此不属于该策略的拦截范围。
 */
fun wjzFocusDeny(
    predicate: (WjzFocusRequestSource) -> Boolean
): WjzFocusRequestPolicy {
    return WjzFocusRequestPolicy { source -> !predicate(source) }
}

/**
 * 在当前 scope 下声明一个默认入口，目标由 [defaultLocalId] 解析得到。
 *
 * 默认 layer 是 Content；TopNav、Drawer、Dialog 等特殊 layer 必须显式传入。
 */
@Composable
fun WjzFocusLocalEntrySurface(
    componentId: String,
    defaultLocalId: WjzFocusLocalId,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    entries: WjzFocusEntriesHostEntriesBuilder.() -> Unit = {}
) {
    val scopeId = LocalWjzFocusScopeId.current
    require(scopeId != null) {
        "WjzFocusLocalEntrySurface requires LocalWjzFocusScopeId.current"
    }
    WjzFocusEntrySurface(
        componentId = componentId,
        default = { scopeId.target(defaultLocalId).copy(layer = layer) },
        entries = entries
    )
}

/** 提交通用组件初始首焦请求。 */
fun WjzFocusCoordinator.submitInitialEntryFocus(
    entryId: WjzFocusEntryId,
    dedupeKey: Any
): WjzFocusRequestResult {
    return submitEntryFocusIntent(
        entryId = entryId,
        intent = WjzFocusSubmitIntent.InitialEntry(dedupeKey = dedupeKey)
    )
}

/** 提交外部明确入口请求，用枚举表达是否主动切换 layer，避免裸布尔参数。 */
fun WjzFocusCoordinator.submitExternalEntryFocus(
    entryId: WjzFocusEntryId,
    layerActivation: WjzFocusLayerActivation = WjzFocusLayerActivation.KeepLayer,
    dedupeKey: Any? = null,
    enqueueUntilLayerActive: Boolean = true,
    enqueueIfMissing: Boolean = true
): WjzFocusRequestResult {
    return submitEntryFocusIntent(
        entryId = entryId,
        intent = WjzFocusSubmitIntent.ExternalEntry(
            dedupeKey = dedupeKey,
            activateLayer = layerActivation.shouldActivateLayer,
            enqueueUntilLayerActive = enqueueUntilLayerActive,
            enqueueIfMissing = enqueueIfMissing
        )
    )
}

/** 提交已知节点的外部明确入口请求，用枚举表达是否主动切换 layer。 */
fun WjzFocusCoordinator.submitExternalNodeFocus(
    nodeId: WjzFocusNodeId,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    layerActivation: WjzFocusLayerActivation = WjzFocusLayerActivation.KeepLayer,
    dedupeKey: Any? = null,
    expectedGeneration: Int? = null,
    enqueueUntilLayerActive: Boolean = true,
    enqueueIfMissing: Boolean = true
): WjzFocusRequestResult {
    return submitNodeFocusIntent(
        nodeId = nodeId,
        layer = layer,
        scopeId = scopeId,
        intent = WjzFocusSubmitIntent.ExternalEntry(
            dedupeKey = dedupeKey,
            activateLayer = layerActivation.shouldActivateLayer,
            enqueueUntilLayerActive = enqueueUntilLayerActive,
            enqueueIfMissing = enqueueIfMissing
        ),
        expectedGeneration = expectedGeneration
    )
}

/** 提交 layer 激活后的公开入口首焦请求。 */
fun WjzFocusCoordinator.submitLayerEntryFocus(
    entryId: WjzFocusEntryId,
    dedupeKey: Any? = null
): WjzFocusRequestResult {
    return submitEntryFocusIntent(
        entryId = entryId,
        intent = WjzFocusSubmitIntent.LayerEntry(dedupeKey = dedupeKey)
    )
}

/** 提交 layer 激活后的已知节点首焦请求。 */
fun WjzFocusCoordinator.submitLayerNodeFocus(
    nodeId: WjzFocusNodeId,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    dedupeKey: Any? = null,
    expectedGeneration: Int? = null
): WjzFocusRequestResult {
    return submitNodeFocusIntent(
        nodeId = nodeId,
        layer = layer,
        scopeId = scopeId,
        intent = WjzFocusSubmitIntent.LayerEntry(dedupeKey = dedupeKey),
        expectedGeneration = expectedGeneration
    )
}

/** 提交内容变化后的公开入口兜底恢复请求。 */
fun WjzFocusCoordinator.submitContentFallbackEntryFocus(
    entryId: WjzFocusEntryId,
    dedupeKey: Any
): WjzFocusRequestResult {
    return submitEntryFocusIntent(
        entryId = entryId,
        intent = WjzFocusSubmitIntent.ContentFallback(dedupeKey = dedupeKey)
    )
}

/** 提交内容变化后的已知节点兜底恢复请求。 */
fun WjzFocusCoordinator.submitContentFallbackNodeFocus(
    nodeId: WjzFocusNodeId,
    dedupeKey: Any,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    expectedGeneration: Int? = null
): WjzFocusRequestResult {
    return submitNodeFocusIntent(
        nodeId = nodeId,
        layer = layer,
        scopeId = scopeId,
        intent = WjzFocusSubmitIntent.ContentFallback(dedupeKey = dedupeKey),
        expectedGeneration = expectedGeneration
    )
}

/** 用 scope-local id 构造默认入口目标；默认落在 Content layer。 */
fun WjzFocusScopeId.localTarget(
    localId: WjzFocusLocalId,
    layer: WjzFocusLayer = WjzFocusLayer.Content
): WjzFocusDefaultTarget {
    return WjzFocusDefaultTarget(
        nodeId = resolve(localId),
        layer = layer,
        scopeId = this
    )
}

/** 用 scope-local id 构造具名 entry 目标；默认落在 Content layer。 */
fun WjzFocusScopeId.localEntry(
    id: String,
    localId: WjzFocusLocalId,
    layer: WjzFocusLayer = WjzFocusLayer.Content
): WjzFocusTargetEntry {
    return entry(
        id = id,
        nodeId = resolve(localId),
        layer = layer,
        scopeId = this
    )
}

/**
 * 使用显式 scope-local 默认目标声明模块。
 *
 * [scopeId] 必须显式传入；本封装需要在创建 Host 前解析 entry target，不能依赖
 * [WjzFocusHost] 未传 scope 时自动生成的默认 scope。
 */
@Composable
fun WjzFocusLocalModule(
    moduleId: String,
    defaultLocalId: WjzFocusLocalId,
    scopeId: WjzFocusScopeId,
    modifier: Modifier = Modifier,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    hostExits: WjzFocusHostExitsBuilder.() -> Unit = {},
    entries: WjzFocusEntriesHostEntriesBuilder.() -> Unit = {},
    content: @Composable () -> Unit
) {
    WjzFocusModule(
        moduleId = moduleId,
        default = { scopeId.localTarget(defaultLocalId, layer) },
        modifier = modifier,
        layer = layer,
        scopeId = scopeId,
        hostExits = hostExits,
        entries = entries,
        content = content
    )
}
