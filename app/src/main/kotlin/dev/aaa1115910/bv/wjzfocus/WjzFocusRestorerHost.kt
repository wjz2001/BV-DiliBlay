package dev.aaa1115910.bv.wjzfocus

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.filter

/** 可提交到 WjzFocusCoordinator 的恢复目标语义。 */
interface WjzFocusRestorableTarget {
    fun restoreFocus(coordinator: WjzFocusCoordinator)
}

/** layer/scope 最近焦点恢复目标。 */
class WjzFocusLayerRestoreTarget internal constructor(
    private val layer: WjzFocusLayer,
    private val scopeId: WjzFocusScopeId?
) : WjzFocusRestorableTarget {
    override fun restoreFocus(coordinator: WjzFocusCoordinator) {
        coordinator.enqueueRestoreLayer(
            layer = layer,
            scopeId = scopeId
        )
    }
}

/** 普通焦点组内可恢复目标。 */
class WjzFocusGroupRestoreTarget internal constructor(
    private val nodeId: WjzFocusNodeId,
    internal val layer: WjzFocusLayer,
    internal val scopeId: WjzFocusScopeId?,
    internal val restorerId: String,
    internal val listId: String,
    internal val fallbackNodeId: WjzFocusNodeId?
) : WjzFocusRestorableTarget {
    override fun restoreFocus(coordinator: WjzFocusCoordinator) {
        coordinator.enqueueGroupRestore(
            nodeId = nodeId,
            layer = layer,
            scopeId = scopeId,
            restorerId = restorerId,
            listId = listId,
            fallbackNodeId = fallbackNodeId
        )
    }
}

/** Lazy 列表内可恢复目标。 */
class WjzLazyFocusRestoreTarget internal constructor(
    private val nodeId: WjzFocusNodeId,
    private val itemKey: WjzFocusItemKey,
    private val layer: WjzFocusLayer,
    private val scopeId: WjzFocusScopeId?,
    internal val restorerId: String,
    internal val listId: String
) : WjzFocusRestorableTarget {
    override fun restoreFocus(coordinator: WjzFocusCoordinator) {
        coordinator.enqueueLazyRestore(
            nodeId = nodeId,
            itemKey = itemKey,
            layer = layer,
            scopeId = scopeId,
            restorerId = restorerId,
            listId = listId
        )
    }
}

/** 公开 entry 的内容兜底恢复目标。 */
class WjzFocusEntryRestoreTarget internal constructor(
    private val entryId: WjzFocusEntryId,
    private val dedupeKey: Any
) {
    fun restoreContentFocus(coordinator: WjzFocusCoordinator) {
        coordinator.submitEntryFocusIntent(
            entryId = entryId,
            intent = WjzFocusSubmitIntent.ContentFallback(dedupeKey = dedupeKey)
        )
    }
}

class WjzFocusGroupRestorerComponent internal constructor(
    componentId: String,
    private val layer: WjzFocusLayer,
    private val scopeId: WjzFocusScopeId?,
    private val restorerId: String = "$componentId/restorer",
    private val listId: String = "$componentId/list"
) {

    fun target(
        nodeId: WjzFocusNodeId,
        fallbackNodeId: WjzFocusNodeId? = null
    ): WjzFocusGroupRestoreTarget {
        return WjzFocusGroupRestoreTarget(
            nodeId = nodeId,
            layer = layer,
            scopeId = scopeId,
            restorerId = restorerId,
            listId = listId,
            fallbackNodeId = fallbackNodeId
        )
    }

    @Composable
    fun Modifier.restorerHost(
        enabled: Boolean = true,
        fallbackNodeId: WjzFocusNodeId? = null
    ): Modifier {
        return wjzFocusGroupRestorerHost(
            enabled = enabled,
            layer = layer,
            scopeId = scopeId,
            restorerId = restorerId,
            listId = listId,
            fallbackNodeId = fallbackNodeId
        )
    }
}

class WjzLazyFocusRestorerComponent internal constructor(
    componentId: String,
    private val layer: WjzFocusLayer,
    private val scopeId: WjzFocusScopeId?,
    private val restoreTimeoutMillis: Long,
    private val restorerId: String = "$componentId/restorer",
    private val listId: String = "$componentId/list"
) {

    fun target(
        nodeId: WjzFocusNodeId,
        itemKey: WjzFocusItemKey
    ): WjzLazyFocusRestoreTarget {
        return WjzLazyFocusRestoreTarget(
            nodeId = nodeId,
            itemKey = itemKey,
            layer = layer,
            scopeId = scopeId,
            restorerId = restorerId,
            listId = listId
        )
    }

    @Composable
    fun InstallRestorerHost(
        scrollToItem: suspend (WjzFocusItemKey) -> Unit,
        isItemVisible: (WjzFocusItemKey) -> Boolean
    ) {
        WjzLazyFocusRestorerHost(
            layer = layer,
            scopeId = scopeId,
            restorerId = restorerId,
            listId = listId,
            restoreTimeoutMillis = restoreTimeoutMillis,
            scrollToItem = scrollToItem,
            isItemVisible = isItemVisible
        )
    }
}

fun wjzFocusGroupRestorerComponent(
    componentId: String,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null
): WjzFocusGroupRestorerComponent {
    return WjzFocusGroupRestorerComponent(
        componentId = componentId,
        layer = layer,
        scopeId = scopeId
    )
}

fun wjzFocusSingleListRestorerComponent(
    componentId: String,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null
): WjzFocusGroupRestorerComponent {
    return WjzFocusGroupRestorerComponent(
        componentId = componentId,
        layer = layer,
        scopeId = scopeId,
        restorerId = componentId,
        listId = componentId
    )
}

fun wjzLazyFocusRestorerComponent(
    componentId: String,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    restoreTimeoutMillis: Long = WjzLazyFocusDefaultRestoreTimeoutMillis
): WjzLazyFocusRestorerComponent {
    return WjzLazyFocusRestorerComponent(
        componentId = componentId,
        layer = layer,
        scopeId = scopeId,
        restoreTimeoutMillis = restoreTimeoutMillis
    )
}

fun wjzLazyFocusSingleListRestorerComponent(
    componentId: String,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    restoreTimeoutMillis: Long = WjzLazyFocusDefaultRestoreTimeoutMillis
): WjzLazyFocusRestorerComponent {
    return WjzLazyFocusRestorerComponent(
        componentId = componentId,
        layer = layer,
        scopeId = scopeId,
        restoreTimeoutMillis = restoreTimeoutMillis,
        restorerId = componentId,
        listId = componentId
    )
}

fun wjzFocusEntryRestoreTarget(
    entryId: WjzFocusEntryId,
    dedupeKey: Any = entryId
): WjzFocusEntryRestoreTarget {
    return WjzFocusEntryRestoreTarget(
        entryId = entryId,
        dedupeKey = dedupeKey
    )
}

fun wjzFocusLayerRestoreTarget(
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null
): WjzFocusLayerRestoreTarget {
    return WjzFocusLayerRestoreTarget(
        layer = layer,
        scopeId = scopeId
    )
}

/**
 * 监听某个普通焦点组 restorer 、list 的定向 pending 门铃，并触发恢复消费。
 */
@Composable
private fun WjzFocusGroupRestoreConsumer(
    layer: WjzFocusLayer,
    scopeId: WjzFocusScopeId? = null,
    restorerId: String,
    listId: String
) {
    val coordinator = LocalWjzFocusCoordinator.current ?: return

    LaunchedEffect(coordinator, layer, scopeId, restorerId, listId) {
        coordinator.pendingGroupIntents(
            layer = layer,
            scopeId = scopeId,
            restorerId = restorerId,
            listId = listId
        ).forEach { intent ->
            coordinator.consumeGroupRestore(intent)
        }

        coordinator.pendingWakeupSignals
            .filter { wakeup ->
                wakeup.kind == WjzFocusPendingWakeupKind.Group &&
                        wakeup.layer == layer &&
                        wakeup.scopeId == scopeId &&
                        wakeup.restorerId == restorerId &&
                        wakeup.listId == listId
            }
            .collect {
                coordinator.pendingGroupIntents(
                    layer = layer,
                    scopeId = scopeId,
                    restorerId = restorerId,
                    listId = listId
                ).forEach { intent ->
                    coordinator.consumeGroupRestore(intent)
                }
            }
    }
}

/**
 * 监听某个 Lazy restorer 、list 的定向 pending 门铃，并触发恢复消费。
 */
@Composable
private fun WjzLazyFocusRestoreConsumer(
    layer: WjzFocusLayer,
    scopeId: WjzFocusScopeId? = null,
    restorerId: String,
    listId: String
) {
    val coordinator = LocalWjzFocusCoordinator.current ?: return

    LaunchedEffect(coordinator, layer, scopeId, restorerId, listId) {
        coordinator.pendingLazyIntents(
            layer = layer,
            scopeId = scopeId,
            restorerId = restorerId,
            listId = listId
        ).forEach { intent ->
            coordinator.consumeLazyRestore(intent)
        }

        coordinator.pendingWakeupSignals
            .filter { wakeup ->
                wakeup.kind == WjzFocusPendingWakeupKind.Lazy &&
                        wakeup.layer == layer &&
                        wakeup.scopeId == scopeId &&
                        wakeup.restorerId == restorerId &&
                        wakeup.listId == listId
            }
            .collect {
                coordinator.pendingLazyIntents(
                    layer = layer,
                    scopeId = scopeId,
                    restorerId = restorerId,
                    listId = listId
                ).forEach { intent ->
                    coordinator.consumeLazyRestore(intent)
                }
            }
    }
}

/**
 * 普通焦点组恢复实现 Host。
 *
 * 业务/UI 代码不要直接调用本函数，而是使用统一入口 [Modifier.wjzFocusRestorerHost]。
 */
@SuppressLint("ComposableModifierFactory")
@Composable
fun Modifier.wjzFocusGroupRestorerHost(
    enabled: Boolean = true,
    layer: WjzFocusLayer,
    scopeId: WjzFocusScopeId? = null,
    restorerId: String,
    listId: String,
    fallbackNodeId: WjzFocusNodeId? = null
): Modifier {
    if (!enabled) return this

    val coordinator = LocalWjzFocusCoordinator.current ?: return this
    val resolvedScopeId = scopeId ?: LocalWjzFocusScopeId.current
    val restorer = remember(coordinator, layer, resolvedScopeId, restorerId, listId, fallbackNodeId) {
        WjzFocusGroupRestorer { target ->
            coordinator.restoreGroupTarget(
                target.copy(
                    fallbackNodeId = target.fallbackNodeId ?: fallbackNodeId
                )
            )
        }
    }

    DisposableEffect(coordinator, layer, resolvedScopeId, restorerId, listId, restorer) {
        coordinator.registerGroupRestorer(
            layer = layer,
            scopeId = resolvedScopeId,
            restorerId = restorerId,
            listId = listId,
            restorer = restorer
        )
        onDispose {
            coordinator.unregisterGroupRestorer(
                layer = layer,
                scopeId = resolvedScopeId,
                restorerId = restorerId,
                listId = listId,
                restorer = restorer
            )
        }
    }

    WjzFocusGroupRestoreConsumer(
        layer = layer,
        scopeId = resolvedScopeId,
        restorerId = restorerId,
        listId = listId
    )

    return this.wjzFocusGroup()
}

/**
 * Lazy 列表焦点恢复实现 Host。
 *
 * 业务/UI 代码优先使用统一入口 [Modifier.wjzFocusRestorerHost]，不要直接调用本函数。
 */
@Composable
fun WjzLazyFocusRestorerHost(
    layer: WjzFocusLayer,
    scopeId: WjzFocusScopeId? = null,
    restorerId: String,
    listId: String,
    enabled: Boolean = true,
    restoreTimeoutMillis: Long = WjzLazyFocusDefaultRestoreTimeoutMillis,
    scrollToItem: suspend (WjzFocusItemKey) -> Unit,
    isItemVisible: (WjzFocusItemKey) -> Boolean
) {
    if (!enabled) return

    val coordinator = LocalWjzFocusCoordinator.current ?: return
    val resolvedScopeId = scopeId ?: LocalWjzFocusScopeId.current
    val currentScrollToItem = rememberUpdatedState(scrollToItem)
    val currentIsItemVisible = rememberUpdatedState(isItemVisible)
    val restorer = remember(coordinator, layer, resolvedScopeId, restorerId, listId, restoreTimeoutMillis) {
        WjzLazyFocusRestorer { target ->
            coordinator.restoreLazyTarget(
                target = target,
                scrollToItem = { itemKey -> currentScrollToItem.value(itemKey) },
                isItemVisible = { itemKey -> currentIsItemVisible.value(itemKey) },
                restoreTimeoutMillis = restoreTimeoutMillis
            )
        }
    }

    DisposableEffect(coordinator, layer, resolvedScopeId, restorerId, listId, restorer) {
        coordinator.registerLazyRestorer(
            layer = layer,
            scopeId = resolvedScopeId,
            restorerId = restorerId,
            listId = listId,
            restorer = restorer
        )
        onDispose {
            coordinator.unregisterLazyRestorer(
                layer = layer,
                scopeId = resolvedScopeId,
                restorerId = restorerId,
                listId = listId,
                restorer = restorer
            )
        }
    }

    WjzLazyFocusRestoreConsumer(
        layer = layer,
        scopeId = resolvedScopeId,
        restorerId = restorerId,
        listId = listId
    )
}

/**
 * 普通焦点组最近焦点恢复入口。
 *
 * 该 modifier 会注册 group restorer，并在组重新进入时优先恢复最近节点；如果最近节点不可用，
 * 可选回退到 [fallbackNodeId]。它不负责 Lazy 滚动，也不声明内部方向路由。
 */
@SuppressLint("ComposableModifierFactory")
@Composable
fun Modifier.wjzFocusRestorerHost(
    enabled: Boolean = true,
    layer: WjzFocusLayer,
    scopeId: WjzFocusScopeId? = null,
    restorerId: String,
    listId: String,
    fallbackNodeId: WjzFocusNodeId? = null
): Modifier {
    return wjzFocusGroupRestorerHost(
        enabled = enabled,
        layer = layer,
        scopeId = scopeId,
        restorerId = restorerId,
        listId = listId,
        fallbackNodeId = fallbackNodeId
    )
}

/** 普通焦点组最近焦点恢复入口，使用 target 隐藏内部 restorer/list 身份。 */
@SuppressLint("ComposableModifierFactory")
@Composable
fun Modifier.wjzFocusRestorerHost(
    target: WjzFocusGroupRestoreTarget,
    enabled: Boolean = true
): Modifier {
    return wjzFocusGroupRestorerHost(
        enabled = enabled,
        layer = target.layer,
        scopeId = target.scopeId,
        restorerId = target.restorerId,
        listId = target.listId,
        fallbackNodeId = target.fallbackNodeId
    )
}

/**
 * Lazy 焦点恢复入口。
 *
 * 该 modifier 注册 Lazy restorer；当 router 返回 [WjzLazyFocusRouteResult.ScrollTo] 时，
 * coordinator 会调用 [scrollToItem]，等待 [isItemVisible] 成立并在目标节点注册后请求焦点。
 */
@SuppressLint("ComposableModifierFactory")
@Composable
fun Modifier.wjzFocusRestorerHost(
    enabled: Boolean = true,
    layer: WjzFocusLayer,
    scopeId: WjzFocusScopeId? = null,
    restorerId: String,
    listId: String,
    restoreTimeoutMillis: Long = WjzLazyFocusDefaultRestoreTimeoutMillis,
    scrollToItem: suspend (WjzFocusItemKey) -> Unit,
    isItemVisible: (WjzFocusItemKey) -> Boolean
): Modifier {
    if (!enabled) return this

    WjzLazyFocusRestorerHost(
        layer = layer,
        scopeId = scopeId,
        restorerId = restorerId,
        listId = listId,
        enabled = true,
        restoreTimeoutMillis = restoreTimeoutMillis,
        scrollToItem = scrollToItem,
        isItemVisible = isItemVisible
    )

    return this.wjzFocusGroup()
}
