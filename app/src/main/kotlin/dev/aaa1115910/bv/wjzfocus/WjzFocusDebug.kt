package dev.aaa1115910.bv.wjzfocus

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.collect

enum class WjzFocusLogLevel {
    Off,
    Info,
    Verbose
}

object WjzFocusDebugConfig {
    var logLevel by mutableStateOf(WjzFocusLogLevel.Off)
}

internal object WjzFocusLogger {
    private val logger = KotlinLogging.logger("WjzFocusCoordinator")

    fun info(message: () -> String) {
        if (WjzFocusDebugConfig.logLevel >= WjzFocusLogLevel.Info) {
            logger.fInfo(message)
        }
    }

    fun verbose(message: () -> String) {
        if (WjzFocusDebugConfig.logLevel >= WjzFocusLogLevel.Verbose) {
            logger.fInfo { "VERBOSE ${message()}" }
        }
    }

    fun warn(message: () -> String) {
        if (WjzFocusDebugConfig.logLevel >= WjzFocusLogLevel.Info) {
            logger.fWarn(message)
        }
    }
}

data class WjzFocusDebugSnapshot(
    val activeLayer: WjzFocusLayer,
    val registeredNodes: List<WjzFocusDebugNode>,
    val focusedByLayerScope: List<WjzFocusDebugLayerScopeNode>,
    val focusedLeafByLayerScope: List<WjzFocusDebugLayerScopeNode>,
    val recentFocus: List<WjzFocusDebugRecentLayer>,
    val sourceStack: List<WjzFocusDebugSource>,
    val pendingRequests: List<WjzFocusDebugPendingRequest>,
    val lockCount: Int,
    val lockedDirection: WjzFocusDebugLockedDirection?,
    val disabledRegionCount: Int
)

data class WjzFocusDebugNode(
    val nodeId: String,
    val layer: WjzFocusLayer,
    val scopeId: String?,
    val kind: String,
    val mounted: Boolean,
    val placed: Boolean,
    val hasFocus: Boolean,
    val routingReady: Boolean,
    val generation: Int
)

data class WjzFocusDebugLayerScopeNode(
    val layer: WjzFocusLayer,
    val scopeId: String?,
    val nodeId: String?,
    val version: Int
)

data class WjzFocusDebugRecentLayer(
    val layer: WjzFocusLayer,
    val nodes: List<WjzFocusDebugRecentNode>
)

data class WjzFocusDebugRecentNode(
    val nodeId: String,
    val scopeId: String?
)

data class WjzFocusDebugSource(
    val layer: WjzFocusLayer,
    val scopeId: String?,
    val nodeId: String?,
    val token: Long
)

data class WjzFocusDebugPendingRequest(
    val intent: String,
    val layer: WjzFocusLayer,
    val scopeId: String?,
    val nodeId: String?,
    val deadlineUptimeMillis: Long,
    val submitDedupeKey: String?
)

data class WjzFocusDebugLockedDirection(
    val direction: String,
    val layer: WjzFocusLayer,
    val deadlineUptimeMillis: Long
)

object WjzFocusDebugOverlayRegistry {
    var content by mutableStateOf<(@Composable (WjzFocusCoordinator) -> Unit)?>(null)

    fun installDefault(enabled: Boolean) {
        content = if (enabled) {
            { coordinator -> WjzFocusDebugOverlay(coordinator) }
        } else {
            null
        }
    }

    fun clear() {
        content = null
    }
}

@Composable
fun WjzFocusDebugOverlaySlot(coordinator: WjzFocusCoordinator?) {
    if (coordinator == null) return
    WjzFocusDebugOverlayRegistry.content?.invoke(coordinator)
}

@Composable
fun WjzFocusDebugOverlay(coordinator: WjzFocusCoordinator) {
    var snapshot by remember(coordinator) { mutableStateOf(coordinator.debugSnapshot()) }

    LaunchedEffect(coordinator) {
        snapshotFlow { coordinator.debugSnapshot() }
            .collect { snapshot = it }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "WjzFocus Debug",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = snapshot.overlayText(),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun WjzFocusDebugSnapshot.overlayText(): String {
    val now = SystemClock.uptimeMillis()
    val focusedLeaf = focusedLeafByLayerScope
        .lastOrNull { it.nodeId != null }
        ?.let { "${it.layer}/${it.scopeId ?: "-"} ${it.nodeId}" }
        ?: "-"
    val recentText = recentFocus
        .takeLast(4)
        .joinToString("\n") { layer ->
            val nodes = layer.nodes
                .takeLast(3)
                .joinToString(" > ") { node -> "${node.scopeId ?: "-"}/${node.nodeId}" }
                .ifBlank { "-" }
            "${layer.layer}: $nodes"
        }
        .ifBlank { "-" }
    val sourceText = sourceStack
        .takeLast(5)
        .joinToString("\n") { source ->
            "#${source.token} ${source.layer}/${source.scopeId ?: "-"} ${source.nodeId ?: "-"}"
        }
        .ifBlank { "-" }
    val pendingText = pendingRequests
        .takeLast(5)
        .joinToString("\n") { request ->
            val ttl = (request.deadlineUptimeMillis - now).coerceAtLeast(0L)
            "${request.layer}/${request.scopeId ?: "-"} ${request.nodeId ?: request.intent} ${ttl}ms"
        }
        .ifBlank { "-" }
    val lockedDirectionText = lockedDirection?.let { direction ->
        val ttl = (direction.deadlineUptimeMillis - now).coerceAtLeast(0L)
        "${direction.direction}@${direction.layer} ${ttl}ms"
    } ?: "-"

    return buildString {
        appendLine("active layer: $activeLayer")
        appendLine("focused leaf: $focusedLeaf")
        appendLine("registered: ${registeredNodes.size}")
        appendLine("locks: $lockCount, locked direction: $lockedDirectionText")
        appendLine("disabled regions: $disabledRegionCount")
        appendLine("recent:")
        appendLine(recentText)
        appendLine("source stack:")
        appendLine(sourceText)
        appendLine("pending:")
        append(pendingText)
    }
}
