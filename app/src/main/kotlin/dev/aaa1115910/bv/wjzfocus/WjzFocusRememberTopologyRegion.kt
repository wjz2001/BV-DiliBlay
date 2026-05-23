package dev.aaa1115910.bv.wjzfocus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

sealed interface WjzFocusTopologyRegionRef {
    data object Standalone : WjzFocusTopologyRegionRef

    data class Bound(
        val id: WjzFocusTopologyRegionId
    ) : WjzFocusTopologyRegionRef
}

fun wjzFocusTopologyRegion(id: String): WjzFocusTopologyRegionRef {
    return WjzFocusTopologyRegionRef.Bound(WjzFocusTopologyRegionId(id))
}

fun WjzFocusTopologyRegionRef.enabledIf(enabled: Boolean): WjzFocusTopologyRegionRef {
    return if (enabled) this else WjzFocusTopologyRegionRef.Standalone
}

@Stable
class WjzFocusTopologyRegionBinding internal constructor(
    private val topology: WjzFocusTopologyState?,
    private val ref: WjzFocusTopologyRegionRef
) {
    val isBound: Boolean
        get() = ref is WjzFocusTopologyRegionRef.Bound

    val isStandalone: Boolean
        get() = ref is WjzFocusTopologyRegionRef.Standalone

    val regionId: WjzFocusTopologyRegionId?
        get() = (ref as? WjzFocusTopologyRegionRef.Bound)?.id

    val region: WjzFocusTopologyRegion?
        get() {
            val id = regionId ?: return null
            val state = requireTopology(id)
            return state.region(id)
                ?: error("WjzFocus topology region '${id.value}' is not declared")
        }

    val nodeExits: List<WjzFocusNodeExit>
        get() {
            val id = regionId ?: return emptyList()
            return requireTopology(id).nodeExitsFor(id)
        }

    val hostExits: List<WjzFocusHostExit>
        get() {
            val id = regionId ?: return emptyList()
            return requireTopology(id).hostExitsFor(id)
        }

    val initialTarget: WjzFocusBoundaryTarget?
        get() {
            val id = regionId ?: return null
            return requireTopology(id).initialTargetFor(id)
        }

    fun resolveInitialTarget(
        componentId: String,
        targets: List<WjzFocusTargetEntry>,
        fallback: () -> WjzFocusResolvedTarget
    ): WjzFocusResolvedTarget {
        return initialTarget.resolveTopologyInitialTarget(
            componentId = componentId,
            targets = targets,
            fallback = fallback
        )
    }

    fun hostExitToken(prefix: String, ownerKey: Any): Any? {
        val id = regionId ?: return null
        return "$prefix-$ownerKey-${id.value}"
    }

    private fun requireTopology(id: WjzFocusTopologyRegionId): WjzFocusTopologyState {
        return topology
            ?: error("WjzFocus topology region '${id.value}' requires WjzFocusTopology provider")
    }
}

@Composable
fun wjzFocusRememberTopologyRegion(
    ref: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone
): WjzFocusTopologyRegionBinding {
    val topology = LocalWjzFocusTopology.current
    return remember(topology, ref) {
        WjzFocusTopologyRegionBinding(topology, ref)
    }
}
