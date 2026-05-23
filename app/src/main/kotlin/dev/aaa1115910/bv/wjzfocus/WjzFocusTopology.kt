package dev.aaa1115910.bv.wjzfocus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusDirection
import kotlin.jvm.JvmName

/**
 * WjzFocus 的静态拓扑草图。
 *
 * 当前阶段只记录 region、target 和边界 exits，不接入 coordinator，也不驱动真实焦点移动。
 *
 * Topology 可以嵌套声明。子 topology 会记录创建时组合树上最近的父 topology，并在读取 region 时解析合并父 topology：
 *
 * - 当前 topology 未声明该 region 时，直接回溯读取父 topology 的 region；
 * - 当前 topology 声明了该 region 时，只覆盖自己声明过的方向；
 * - 当前 topology 未声明的方向，从父 region 继承；
 * - [WjzFocusTopologyRegion.initialTarget] 在子 region 未声明时继承父值。
 *
 * 这样页面可以在外层声明完整的跨组件边界，内层只针对局部场景覆盖少数方向，
 * 不需要为了覆盖一个方向而重复声明其它方向。
 */
@Stable
class WjzFocusTopologyState internal constructor(
    /**
     * 创建当前 state 时组合树上的父 topology。
     *
     * 这里保存的是 state 引用，不是 snapshot。父 topology 后续声明或移除 region 时，
     * 子 topology 的解析结果会在读取 [region]、[nodeExitsFor]、[hostExitsFor] 时自然反映出来。
     */
    private val parent: WjzFocusTopologyState? = null
) {
    private val regions = mutableStateMapOf<WjzFocusTopologyRegionId, WjzFocusTopologyRegion>()

    /**
     * 当前 topology 的已解析快照。
     *
     * 快照包含父 topology 中可见的 region，以及当前 topology 的局部覆盖结果。
     * 如果父子 topology 声明了同一个 region id，快照里只保留合并后的 region。
     */
    val snapshot: WjzFocusTopologySnapshot
        get() {
            val parentRegions = parent?.snapshot?.regions.orEmpty()
                .associateBy { region -> region.id }
            val resolvedRegions = parentRegions + regions.keys.associateWith { id -> region(id) }
                .mapValues { (_, region) -> requireNotNull(region) }
            return WjzFocusTopologySnapshot(regions = resolvedRegions.values.toList())
        }

    /**
     * 解析一个 region。
     *
     * 解析规则是“当前优先，缺省则继承父级”：
     *
     * - 当前 topology 没有这个 region：返回父 topology 的解析结果；
     * - 父 topology 没有这个 region：返回当前 topology 的原始声明；
     * - 父子 topology 都有这个 region：返回按方向合并后的 region。
     */
    fun region(id: WjzFocusTopologyRegionId): WjzFocusTopologyRegion? {
        val currentRegion = regions[id]
        val parentRegion = parent?.region(id)
        return when {
            currentRegion == null -> parentRegion
            parentRegion == null -> currentRegion
            else -> parentRegion.mergeChild(currentRegion)
        }
    }

    fun nodeExitsFor(regionId: String): List<WjzFocusNodeExit> {
        return nodeExitsFor(WjzFocusTopologyRegionId(regionId))
    }

    fun nodeExitsFor(regionId: WjzFocusTopologyRegionId): List<WjzFocusNodeExit> {
        val region = region(regionId) ?: return emptyList()
        return region.exits.mapNotNull { exit ->
            exit.asNodeExit()
        } + region.boundaries.mapNotNull { boundary ->
            boundary.asNodeExit()
        }
    }

    fun hostExitsFor(regionId: String): List<WjzFocusHostExit> {
        return hostExitsFor(WjzFocusTopologyRegionId(regionId))
    }

    fun hostExitsFor(regionId: WjzFocusTopologyRegionId): List<WjzFocusHostExit> {
        val region = region(regionId) ?: return emptyList()
        return region.exits.mapNotNull { exit ->
            exit.asHostExit()
        } + region.boundaries.mapNotNull { boundary ->
            boundary.asHostExit()
        }
    }

    fun initialTargetFor(regionId: String): WjzFocusBoundaryTarget? {
        return initialTargetFor(WjzFocusTopologyRegionId(regionId))
    }

    fun initialTargetFor(regionId: WjzFocusTopologyRegionId): WjzFocusBoundaryTarget? {
        return region(regionId)?.initialTarget
    }

    internal fun updateRegion(region: WjzFocusTopologyRegion) {
        regions[region.id] = region
    }

    internal fun removeRegion(id: WjzFocusTopologyRegionId) {
        regions.remove(id)
    }
}

@Composable
fun rememberWjzFocusTopologyState(
    parent: WjzFocusTopologyState? = LocalWjzFocusTopology.current
): WjzFocusTopologyState {
    return remember(parent) { WjzFocusTopologyState(parent) }
}

val LocalWjzFocusTopology = compositionLocalOf<WjzFocusTopologyState?> { null }

val LocalWjzFocusTopologyState = LocalWjzFocusTopology

/**
 * 声明一个 WjzFocus topology 作用域。
 *
 * 默认情况下会创建一个新的 [WjzFocusTopologyState]，并把当前组合树上的 [LocalWjzFocusTopology] 记录为 parent，
 * 因此嵌套 topology 可以继承外层 region。
 *
 * 如果调用方显式传入 [state]，则沿用该 state 自己创建时记录的 parent。
 * 这通常用于外部持有 state 做调试或快照读取。
 */
@Composable
fun WjzFocusTopology(
    state: WjzFocusTopologyState? = null,
    content: @Composable WjzFocusTopologyScope.() -> Unit
) {
    val resolvedState = state ?: rememberWjzFocusTopologyState()
    val scope = remember(resolvedState) { WjzFocusTopologyScope(resolvedState) }
    CompositionLocalProvider(LocalWjzFocusTopology provides resolvedState) {
        scope.content()
    }
}

class WjzFocusTopologyScope internal constructor(
    private val state: WjzFocusTopologyState
) {
    fun region(
        id: String,
        scopeId: WjzFocusScopeId? = null,
        layer: WjzFocusLayer = WjzFocusLayer.Content,
        targets: List<WjzFocusTopologyTarget> = emptyList(),
        exits: List<WjzFocusTopologyExit> = emptyList()
    ): WjzFocusRegionScope {
        return region(
            id = WjzFocusTopologyRegionId(id),
            scopeId = scopeId,
            layer = layer,
            targets = targets,
            exits = exits
        )
    }

    fun region(
        id: WjzFocusTopologyRegionId,
        scopeId: WjzFocusScopeId? = null,
        layer: WjzFocusLayer = WjzFocusLayer.Content,
        targets: List<WjzFocusTopologyTarget> = emptyList(),
        exits: List<WjzFocusTopologyExit> = emptyList()
    ): WjzFocusRegionScope {
        val region = WjzFocusTopologyRegion(
            id = id,
            scopeId = scopeId,
            layer = layer,
            targets = targets,
            exits = exits
        )
        state.updateRegion(region)
        return WjzFocusRegionScope(state, region)
    }

    fun region(
        id: String,
        scopeId: WjzFocusScopeId? = null,
        layer: WjzFocusLayer = WjzFocusLayer.Content,
        targets: List<WjzFocusTopologyTarget> = emptyList(),
        exits: List<WjzFocusTopologyExit> = emptyList(),
        block: WjzFocusRegionScope.() -> Unit
    ) {
        region(
            id = id,
            scopeId = scopeId,
            layer = layer,
            targets = targets,
            exits = exits
        ).block()
    }

    fun region(
        id: WjzFocusTopologyRegionId,
        scopeId: WjzFocusScopeId? = null,
        layer: WjzFocusLayer = WjzFocusLayer.Content,
        targets: List<WjzFocusTopologyTarget> = emptyList(),
        exits: List<WjzFocusTopologyExit> = emptyList(),
        block: WjzFocusRegionScope.() -> Unit
    ) {
        region(
            id = id,
            scopeId = scopeId,
            layer = layer,
            targets = targets,
            exits = exits
        ).block()
    }

    fun removeRegion(id: String) {
        removeRegion(WjzFocusTopologyRegionId(id))
    }

    fun removeRegion(id: WjzFocusTopologyRegionId) {
        state.removeRegion(id)
    }
}

class WjzFocusRegionScope internal constructor(
    private val state: WjzFocusTopologyState,
    private var region: WjzFocusTopologyRegion
) {
    fun onLeft(target: WjzFocusBoundaryTarget) {
        setBoundary(FocusDirection.Left, target)
    }

    fun onRight(target: WjzFocusBoundaryTarget) {
        setBoundary(FocusDirection.Right, target)
    }

    fun onUp(target: WjzFocusBoundaryTarget) {
        setBoundary(FocusDirection.Up, target)
    }

    fun onDown(target: WjzFocusBoundaryTarget) {
        setBoundary(FocusDirection.Down, target)
    }

    fun initialEntry(target: WjzFocusBoundaryTarget) {
        update(region.copy(initialTarget = target))
    }

    private fun setBoundary(
        direction: FocusDirection,
        target: WjzFocusBoundaryTarget
    ) {
        update(
            region.copy(
                boundaries = region.boundaries
                    .filterNot { boundary -> boundary.direction == direction } +
                        WjzFocusTopologyBoundary(direction, target)
            )
        )
    }

    private fun update(nextRegion: WjzFocusTopologyRegion) {
        region = nextRegion
        state.updateRegion(nextRegion)
    }
}

@JvmInline
value class WjzFocusTopologyRegionId(
    val value: String
) {
    init {
        require(value.isNotBlank()) { "wjz focus topology region id must not be blank" }
    }
}

data class WjzFocusTopologySnapshot(
    val regions: List<WjzFocusTopologyRegion>
)

data class WjzFocusTopologyRegion(
    val id: WjzFocusTopologyRegionId,
    val scopeId: WjzFocusScopeId?,
    val layer: WjzFocusLayer,
    val targets: List<WjzFocusTopologyTarget>,
    val exits: List<WjzFocusTopologyExit>,
    val boundaries: List<WjzFocusTopologyBoundary> = emptyList(),
    val initialTarget: WjzFocusBoundaryTarget? = null
)

/**
 * 把子 region 覆盖到父 region 上。
 *
 * 覆盖粒度是方向，而不是整个 region：
 *
 * - 子 region 的 [WjzFocusTopologyRegion.exits] 或 [WjzFocusTopologyRegion.boundaries] 只要声明了某个 direction，
 *   就认为该 direction 由子 region 接管；
 * - 父 region 中相同 direction 的 exits/boundaries 会被移除；
 * - 父 region 中其它 direction 的 exits/boundaries 会继续保留；
 * - 子 region 未设置 [WjzFocusTopologyRegion.initialTarget] 时继承父级 initialTarget；
 * - 子 region 未提供 [WjzFocusTopologyRegion.scopeId] 或 targets 时继承父级对应值。
 *
 * 这个函数只负责静态 topology 合并，不直接触发焦点移动。
 */
private fun WjzFocusTopologyRegion.mergeChild(
    child: WjzFocusTopologyRegion
): WjzFocusTopologyRegion {
    // 子级任意一种边界声明命中某方向，就覆盖父级该方向的所有 topology 出口。
    val childDirections = (
        child.exits.map { exit -> exit.direction } +
            child.boundaries.map { boundary -> boundary.direction }
        ).toSet()
    return child.copy(
        scopeId = child.scopeId ?: scopeId,
        targets = child.targets.ifEmpty { targets },
        exits = exits.filterNot { exit -> exit.direction in childDirections } + child.exits,
        boundaries = boundaries.filterNot { boundary -> boundary.direction in childDirections } +
            child.boundaries,
        initialTarget = child.initialTarget ?: initialTarget
    )
}

data class WjzFocusTopologyBoundary(
    val direction: FocusDirection,
    val target: WjzFocusBoundaryTarget
)

sealed interface WjzFocusBoundaryTarget {
    data class Region(
        val id: String
    ) : WjzFocusBoundaryTarget

    data class Entry(
        val entryId: WjzFocusEntryId
    ) : WjzFocusBoundaryTarget

    data object Wrap : WjzFocusBoundaryTarget

    data object Cancel : WjzFocusBoundaryTarget

    data object Internal : WjzFocusBoundaryTarget
}

sealed interface WjzFocusTopologyTarget {
    val nodeId: WjzFocusNodeId
    val layer: WjzFocusLayer
    val scopeId: WjzFocusScopeId?

    data class Node(
        override val nodeId: WjzFocusNodeId,
        override val layer: WjzFocusLayer = WjzFocusLayer.Content,
        override val scopeId: WjzFocusScopeId? = null
    ) : WjzFocusTopologyTarget
}

fun WjzFocusResolvedTarget.asTopologyTarget(): WjzFocusTopologyTarget {
    return WjzFocusTopologyTarget.Node(
        nodeId = nodeId,
        layer = layer,
        scopeId = scopeId
    )
}

data class WjzFocusTopologyExit(
    val direction: FocusDirection,
    val target: WjzFocusTopologyExitTarget
)

sealed interface WjzFocusTopologyExitTarget {
    data class Entry(
        val entryId: WjzFocusEntryId
    ) : WjzFocusTopologyExitTarget

    data object Cancel : WjzFocusTopologyExitTarget
}

fun WjzFocusNodeExit.asTopologyExit(): WjzFocusTopologyExit {
    return WjzFocusTopologyExit(
        direction = direction,
        target = target.asTopologyExitTarget()
    )
}

fun WjzFocusHostExit.asTopologyExit(): WjzFocusTopologyExit {
    return WjzFocusTopologyExit(
        direction = direction,
        target = target.asTopologyExitTarget()
    )
}

fun WjzFocusBoundaryTarget?.resolveTopologyInitialTarget(
    componentId: String,
    targets: List<WjzFocusTargetEntry>,
    fallback: () -> WjzFocusResolvedTarget
): WjzFocusResolvedTarget {
    val entryTarget = this as? WjzFocusBoundaryTarget.Entry ?: return fallback()
    if (entryTarget.entryId.componentId.value != componentId) return fallback()
    return targets.firstOrNull { target ->
        target.id == entryTarget.entryId.localEntryValue
    } ?: fallback()
}

@JvmName("wjzFocusNodeExitsAsTopologyExits")
fun List<WjzFocusNodeExit>.asTopologyExits(): List<WjzFocusTopologyExit> {
    return map { it.asTopologyExit() }
}

@JvmName("wjzFocusHostExitsAsTopologyExits")
fun List<WjzFocusHostExit>.asTopologyExits(): List<WjzFocusTopologyExit> {
    return map { it.asTopologyExit() }
}

private fun WjzFocusNodeExitTarget.asTopologyExitTarget(): WjzFocusTopologyExitTarget {
    return when (this) {
        WjzFocusNodeExitTarget.Cancel -> WjzFocusTopologyExitTarget.Cancel
        is WjzFocusNodeExitTarget.Entry -> WjzFocusTopologyExitTarget.Entry(entryId)
    }
}

private fun WjzFocusTopologyExit.asNodeExit(): WjzFocusNodeExit? {
    return when (val resolvedTarget = target) {
        is WjzFocusTopologyExitTarget.Entry -> WjzFocusNodeExit(direction, resolvedTarget.entryId)
        WjzFocusTopologyExitTarget.Cancel -> WjzFocusNodeExit.cancel(direction)
    }
}

private fun WjzFocusTopologyExit.asHostExit(): WjzFocusHostExit? {
    return when (val resolvedTarget = target) {
        is WjzFocusTopologyExitTarget.Entry -> WjzFocusHostExit(direction, resolvedTarget.entryId)
        WjzFocusTopologyExitTarget.Cancel -> WjzFocusHostExit.cancel(direction)
    }
}

private fun WjzFocusTopologyBoundary.asNodeExit(): WjzFocusNodeExit? {
    return when (val resolvedTarget = target) {
        is WjzFocusBoundaryTarget.Entry -> WjzFocusNodeExit(direction, resolvedTarget.entryId)
        WjzFocusBoundaryTarget.Cancel -> WjzFocusNodeExit.cancel(direction)
        is WjzFocusBoundaryTarget.Region,
        WjzFocusBoundaryTarget.Wrap,
        WjzFocusBoundaryTarget.Internal -> null
    }
}

private fun WjzFocusTopologyBoundary.asHostExit(): WjzFocusHostExit? {
    return when (val resolvedTarget = target) {
        is WjzFocusBoundaryTarget.Entry -> WjzFocusHostExit(direction, resolvedTarget.entryId)
        WjzFocusBoundaryTarget.Cancel -> WjzFocusHostExit.cancel(direction)
        is WjzFocusBoundaryTarget.Region,
        WjzFocusBoundaryTarget.Wrap,
        WjzFocusBoundaryTarget.Internal -> null
    }
}
