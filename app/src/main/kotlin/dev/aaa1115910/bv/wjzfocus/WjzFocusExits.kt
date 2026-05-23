package dev.aaa1115910.bv.wjzfocus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import dev.aaa1115910.bv.util.fWarn
import io.github.oshai.kotlinlogging.KotlinLogging

private val wjzFocusExitsLogger = KotlinLogging.logger("WjzFocusExits")

/**
 * `exits {}` DSL 的构建器。
 *
 * 节点级 exits 只描述当前节点离开自身边界时跳到哪个公开 entry，或者消费某个方向。
 * 它不负责组件内部相邻 item 的移动；内部移动应使用 router/resolver。
 */
class WjzFocusExitsBuilder {
    internal val exits = mutableListOf<WjzFocusNodeExit>()

    /** 追加已经构建好的节点级出口。 */
    fun addAll(nodeExits: List<WjzFocusNodeExit>) {
        exits.addAll(nodeExits)
    }

    /** 声明一个或多个方向的节点级出口。 */
    infix fun WjzFocusDirections.move(targetEntryId: String) {
        directions.forEach { direction ->
            if (exits.any { it.direction == direction }) {
                wjzFocusExitsLogger.fWarn {
                    "duplicate wjzFocus exit direction: direction=$direction, " +
                            "targetEntryId=$targetEntryId. " +
                            "The first matching direction will win during exit dispatch."
                }
            }
            exits.add(WjzFocusNodeExit(direction, WjzFocusEntryId.parse(targetEntryId)))
        }
    }

    /** 直接接收完整强类型 entry。 */
    infix fun WjzFocusDirections.move(targetEntryId: WjzFocusEntryId) {
        directions.forEach { direction ->
            if (exits.any { it.direction == direction }) {
                wjzFocusExitsLogger.fWarn {
                    "duplicate wjzFocus exit direction: direction=$direction, " +
                            "targetEntryId=${targetEntryId.value}. " +
                            "The first matching direction will win during exit dispatch."
                }
            }
            exits.add(WjzFocusNodeExit(direction, targetEntryId))
        }
    }

    /** 把一个或多个方向直接封闭在当前节点边界内。 */
    fun cancel(directions: WjzFocusDirections = all) {
        directions.directions.forEach { direction ->
            exits.add(WjzFocusNodeExit.cancel(direction))
        }
    }
}

/**
 * Host 级 `exits {}` DSL 的构建器。
 *
 * Host exits 是整个模块边界的兜底出口，只有节点和内部 router 都没有消费方向时才会生效。
 */
class WjzFocusHostExitsBuilder {
    internal val exits = mutableListOf<WjzFocusHostExit>()

    /** 追加已经构建好的 Host 级出口。 */
    fun addAll(hostExits: List<WjzFocusHostExit>) {
        exits.addAll(hostExits)
    }

    /** 声明一个或多个 Host 级方向出口。 */
    infix fun WjzFocusDirections.move(targetEntryId: String) {
        directions.forEach { direction ->
            if (exits.any { it.direction == direction }) {
                wjzFocusExitsLogger.fWarn {
                    "duplicate wjzFocus host exit direction: direction=$direction, " +
                            "targetEntryId=$targetEntryId. " +
                            "The first matching direction will win during host exit dispatch."
                }
            }
            exits.add(WjzFocusHostExit(direction, WjzFocusEntryId.parse(targetEntryId)))
        }
    }

    /** 直接接收完整强类型 entry。 */
    infix fun WjzFocusDirections.move(targetEntryId: WjzFocusEntryId) {
        directions.forEach { direction ->
            if (exits.any { it.direction == direction }) {
                wjzFocusExitsLogger.fWarn {
                    "duplicate wjzFocus host exit direction: direction=$direction, " +
                            "targetEntryId=${targetEntryId.value}. " +
                            "The first matching direction will win during host exit dispatch."
                }
            }
            exits.add(WjzFocusHostExit(direction, targetEntryId))
        }
    }

    /** 把一个或多个方向封闭在当前 Host 边界内。 */
    fun cancel(directions: WjzFocusDirections = all) {
        directions.directions.forEach { direction ->
            exits.add(WjzFocusHostExit.cancel(direction))
        }
    }
}

/**
 * 使用旧 String 完整 node id 注册焦点节点，并显式传入 requester。
 *
 * [id] 不会读取当前 scope，也不会参与 `scope|local` 拼接；需要 scope 内相对路径时使用
 * [WjzFocusLocalId] 重载。
 */
fun Modifier.wjzFocusExits(
    id: String,
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
        nodeId = WjzFocusNodeId(id),
        scopeId = null,
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
 * 使用完整 [WjzFocusNodeId] 注册焦点节点，并显式传入 requester。
 *
 * [scopeId] 只描述节点所属边界和恢复范围，不会改变 [nodeId] 本身。
 */
fun Modifier.wjzFocusExits(
    nodeId: WjzFocusNodeId,
    scopeId: WjzFocusScopeId? = null,
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
    val builtExits = WjzFocusExitsBuilder().apply(exits).exits

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
        exits = builtExits,
        onFocusChanged = { focused ->
            if (focused) onFocused()
            onFocusChanged(focused)
        }
    )
}

/**
 * 使用旧 String 完整 node id 注册焦点节点，并由内部 remember requester。
 */
@Composable
fun Modifier.wjzFocusExits(
    id: String,
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
    val requester = remember { FocusRequester() }
    return wjzFocusExits(
        id = id,
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
 * 使用完整 [WjzFocusNodeId] 注册焦点节点，并由内部 remember requester。
 */
@Composable
fun Modifier.wjzFocusExits(
    nodeId: WjzFocusNodeId,
    scopeId: WjzFocusScopeId? = null,
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
    val requester = remember { FocusRequester() }
    return wjzFocusExits(
        nodeId = nodeId,
        scopeId = scopeId,
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
 * 使用当前 [LocalWjzFocusScopeId] 下的相对 [WjzFocusLocalId] 注册节点，并显式传入 requester。
 *
 * 该重载会通过 [WjzFocusScopeId.resolve] 生成完整 node id，最终格式为 `scope|local`。
 */
@Composable
fun Modifier.wjzFocusExits(
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
    val scopeId = LocalWjzFocusScopeId.current
    require(scopeId != null) {
        "wjz focus local id '${localId.value}' requires LocalWjzFocusScopeId.current"
    }
    return wjzFocusExits(
        nodeId = scopeId.resolve(localId),
        scopeId = scopeId,
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
 * 使用当前 [LocalWjzFocusScopeId] 下的相对 [WjzFocusLocalId] 注册节点，并由内部 remember requester。
 */
@Composable
fun Modifier.wjzFocusExits(
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
    val requester = remember { FocusRequester() }
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
