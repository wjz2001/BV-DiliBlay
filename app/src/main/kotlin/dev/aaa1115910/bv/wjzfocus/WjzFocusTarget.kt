package dev.aaa1115910.bv.wjzfocus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

private const val WjzFocusDefaultTargetId = "__default_target__"

/**
 * 公开 entry 最终解析出的真实焦点目标。
 *
 * 目标必须包含完整 [nodeId]，以及可选的 [scopeId] 和 [layer]。entry 解析只负责把
 * “组件间通信 id”转成这个真实目标；组件内部的相邻移动不应该通过 entry 解析绕行。
 */
sealed interface WjzFocusResolvedTarget {
    val nodeId: WjzFocusNodeId
    val layer: WjzFocusLayer
    val scopeId: WjzFocusScopeId?
}

/**
 * 默认入口返回的目标。
 *
 * 它没有自己的公开 entry 名称，注册到 [WjzFocusEntriesHost] 时会被包装成内部默认 target id。
 */
data class WjzFocusDefaultTarget(
    override val nodeId: WjzFocusNodeId,
    override val layer: WjzFocusLayer = WjzFocusLayer.Content,
    override val scopeId: WjzFocusScopeId? = null
) : WjzFocusResolvedTarget

/** 构造默认入口目标。 */
fun defaultEntry(
    nodeId: WjzFocusNodeId,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null
): WjzFocusDefaultTarget {
    return WjzFocusDefaultTarget(
        nodeId = nodeId,
        layer = layer,
        scopeId = scopeId
    )
}

/** 用字符串 node key 构造默认入口目标。 */
fun defaultEntry(
    nodeKey: String,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null
): WjzFocusDefaultTarget {
    return defaultEntry(
        nodeId = WjzFocusNodeId(nodeKey),
        layer = layer,
        scopeId = scopeId
    )
}

/**
 * 具名 entry 对应的真实目标。
 *
 * [id] 是 component 内部的 local entry id，不包含 componentId，也不能使用 `default`。
 */
data class WjzFocusTargetEntry(
    val id: String,
    override val nodeId: WjzFocusNodeId,
    override val layer: WjzFocusLayer = WjzFocusLayer.Content,
    override val scopeId: WjzFocusScopeId? = null
) : WjzFocusResolvedTarget {
    init {
        validateEntryId(id)
    }
}

/** 构造具名 entry 目标。 */
fun entry(
    id: String,
    nodeId: WjzFocusNodeId,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null
): WjzFocusTargetEntry {
    return WjzFocusTargetEntry(
        id = id,
        nodeId = nodeId,
        layer = layer,
        scopeId = scopeId
    )
}

/** 用字符串 node key 构造具名 entry 目标。 */
fun entry(
    id: String,
    nodeKey: String,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null
): WjzFocusTargetEntry {
    return entry(
        id = id,
        nodeId = WjzFocusNodeId(nodeKey),
        layer = layer,
        scopeId = scopeId
    )
}

/** entries DSL 中暂存 local entry id 的句柄，用于 `entry("x") move target` 写法。 */
class WjzFocusEntriesHostEntryHandle internal constructor(
    internal val id: WjzFocusLocalEntryId
)

/**
 * [WjzFocusEntriesHost] 的 entries DSL 构建器。
 *
 * 这里暴露的是组件对外的公开入口，不是组件内部 item 的相邻关系。
 */
class WjzFocusEntriesHostEntriesBuilder internal constructor() {
    internal val entries = linkedMapOf<WjzFocusLocalEntryId, () -> WjzFocusResolvedTarget?>()

    /** 创建一个具名 entry 句柄，供 infix `move` 使用。 */
    fun entry(id: String): WjzFocusEntriesHostEntryHandle {
        return WjzFocusEntriesHostEntryHandle(WjzFocusLocalEntryId(id))
    }

    /** 创建一个强类型具名 entry 句柄。 */
    fun entry(id: WjzFocusLocalEntryId): WjzFocusEntriesHostEntryHandle {
        return WjzFocusEntriesHostEntryHandle(id)
    }

    /** 直接声明一个具名 entry 到固定目标。 */
    fun entry(
        id: String,
        target: WjzFocusResolvedTarget
    ) {
        entry(id) move target
    }

    /** 直接声明一个具名 entry 到动态目标 provider。 */
    fun entry(
        id: String,
        target: () -> WjzFocusResolvedTarget?
    ) {
        val handle = entry(id)
        entries[handle.id] = target
    }

    /** 直接声明一个强类型具名 entry 到动态目标 provider。 */
    fun entry(
        id: WjzFocusLocalEntryId,
        target: () -> WjzFocusResolvedTarget?
    ) {
        entries[id] = target
    }

    /** 把 entry 句柄绑定到固定目标。 */
    infix fun WjzFocusEntriesHostEntryHandle.move(target: WjzFocusResolvedTarget) {
        entries[id] = { target }
    }

    /** 简写：`"entryId" move target`。 */
    infix fun String.move(target: WjzFocusResolvedTarget) {
        entry(this) move target
    }
}

/**
 * 单个 component 暴露的 entry host 状态。
 *
 * coordinator 通过它解析 [WjzFocusEntryId]。具名 entry 缺失时会 fallback 到 default，
 * 因为跨组件请求通常比组件内部移动更需要稳定兜底。
 */
class WjzFocusEntriesHostState(
    val componentId: WjzFocusComponentId,
    default: () -> WjzFocusResolvedTarget,
    entries: WjzFocusEntriesHostEntriesBuilder.() -> Unit = {}
) {
    private var defaultProvider = default
    private var entryProviders = buildEntryProviders(entries)

    /** 当前 component 的默认公开 entry id。 */
    val defaultEntryId: WjzFocusEntryId
        get() = componentId.defaultEntry()

    /** 更新默认入口 provider，供组合重组后保持最新闭包。 */
    fun updateDefault(default: () -> WjzFocusResolvedTarget) {
        defaultProvider = default
    }

    /** 用固定默认目标更新默认入口。 */
    fun updateDefault(default: WjzFocusDefaultTarget) {
        updateDefault { default }
    }

    /** 重新构建具名 entry providers。 */
    fun updateEntries(entries: WjzFocusEntriesHostEntriesBuilder.() -> Unit) {
        entryProviders = buildEntryProviders(entries)
    }

    /** 从已生成的 target entries 更新具名入口。 */
    fun updateEntries(entries: List<WjzFocusTargetEntry>) {
        entryProviders = entries.associateBy(
            keySelector = { entry -> WjzFocusLocalEntryId(entry.id) },
            valueTransform = { entry -> { entry } }
        )
    }

    /** 解析一个完整公开 entry id。 */
    fun resolve(entryId: WjzFocusEntryId): WjzFocusTargetResolution {
        require(entryId.componentId == componentId) {
            "wjz focus entry '${entryId.value}' does not belong to component '${componentId.value}'"
        }
        if (entryId.localEntryValue == WjzFocusDefaultEntryName) {
            return WjzFocusTargetResolution.Ready(defaultProvider().asTargetEntry(WjzFocusDefaultTargetId))
        }

        val localEntryId = WjzFocusLocalEntryId(entryId.localEntryValue)
        val target = entryProviders[localEntryId]?.invoke()
        return target?.let {
            WjzFocusTargetResolution.Ready(it.asTargetEntry(localEntryId.value))
        } ?: fallbackToDefault(entryId.value)
    }

    private fun buildEntryProviders(
        entries: WjzFocusEntriesHostEntriesBuilder.() -> Unit
    ): Map<WjzFocusLocalEntryId, () -> WjzFocusResolvedTarget?> {
        return WjzFocusEntriesHostEntriesBuilder().apply(entries).entries
    }

    private fun fallbackToDefault(requestedEntryId: String?): WjzFocusTargetResolution {
        return WjzFocusTargetResolution.Fallback(
            requestedEntryId = requestedEntryId,
            target = defaultProvider().asTargetEntry(WjzFocusDefaultTargetId)
        )
    }
}

/**
 * 在当前 coordinator 中注册一个公开 entry host。
 *
 * componentId 是外部可寻址组件名；default 是组件默认入口；entries 是额外具名入口。
 * 该 Host 只提供外部入口解析，不创建 UI 边界，通常由 [WjzFocusModule] 或容器组件代为调用。
 */
@Composable
fun WjzFocusEntriesHost(
    componentId: String,
    default: () -> WjzFocusResolvedTarget,
    entries: WjzFocusEntriesHostEntriesBuilder.() -> Unit = {}
) {
    val coordinator = LocalWjzFocusCoordinator.current ?: return
    val parsedComponentId = WjzFocusComponentId(componentId)
    val currentDefault by rememberUpdatedState(default)
    val currentEntries by rememberUpdatedState(entries)
    val state = remember(parsedComponentId, coordinator) {
        WjzFocusEntriesHostState(
            componentId = parsedComponentId,
            default = { currentDefault() },
            entries = currentEntries
        )
    }

    state.updateDefault { currentDefault() }
    state.updateEntries(currentEntries)

    DisposableEffect(coordinator, state) {
        coordinator.registerEntriesHost(state)
        onDispose {
            coordinator.unregisterEntriesHost(state)
        }
    }
}

/**
 * 公开 entry 的解析结果。
 *
 * [Ready] 表示命中目标；[Fallback] 表示请求的具名 entry 缺失但已回退到 default；
 * [Missing] 表示没有任何可请求目标。
 */
sealed interface WjzFocusTargetResolution {
    val target: WjzFocusTargetEntry?

    /** 目标已直接命中。 */
    data class Ready(
        override val target: WjzFocusTargetEntry
    ) : WjzFocusTargetResolution

    /** 请求未命中，但已回退到默认入口。 */
    data class Fallback(
        val requestedEntryId: String?,
        override val target: WjzFocusTargetEntry
    ) : WjzFocusTargetResolution

    /** 没有可用目标。 */
    data class Missing(
        val requestedEntryId: String?
    ) : WjzFocusTargetResolution {
        override val target: WjzFocusTargetEntry? = null
    }
}

/** 判断当前 coordinator 的焦点是否落在该语义目标上。 */
fun WjzFocusResolvedTarget.isFocusedBy(coordinator: WjzFocusCoordinator): Boolean {
    return coordinator.hasFocus(nodeId)
}

/** 把 resolved target 包装成具名 [WjzFocusTargetEntry]。 */
internal fun WjzFocusResolvedTarget.asTargetEntry(id: String): WjzFocusTargetEntry {
    return when (this) {
        is WjzFocusTargetEntry -> copy(id = id)
        is WjzFocusDefaultTarget -> WjzFocusTargetEntry(
            id = id,
            nodeId = nodeId,
            layer = layer,
            scopeId = scopeId
        )
    }
}

/** 校验 component 内部 local entry id。 */
internal fun validateEntryId(
    entryId: String,
    allowDefault: Boolean = false
) {
    require(entryId.isNotBlank()) { "wjz focus entry id must not be blank" }
    require('/' !in entryId && '\\' !in entryId && '|' !in entryId) {
        "wjz focus local entry id must not contain '/', '\\' or '|'"
    }
    require(allowDefault || entryId != WjzFocusDefaultEntryName) {
        "wjz focus entry id '$WjzFocusDefaultEntryName' is reserved"
    }
}

/** 如果当前 target 非空则 Ready，否则执行 fallback。 */
internal fun WjzFocusTargetEntry?.readyOrFallback(
    entryId: String,
    fallback: () -> WjzFocusTargetResolution
): WjzFocusTargetResolution {
    return this?.let { WjzFocusTargetResolution.Ready(it) } ?: fallback()
}

