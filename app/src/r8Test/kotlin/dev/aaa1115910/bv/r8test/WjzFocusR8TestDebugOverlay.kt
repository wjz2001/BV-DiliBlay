package dev.aaa1115910.bv.r8test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.wjzfocus.WjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.WjzFocusDebugOverlayRegistry
import dev.aaa1115910.bv.wjzfocus.WjzFocusDebugSnapshot
import kotlinx.coroutines.delay

object WjzFocusR8TestDebugOverlayInstaller {
    @JvmStatic
    fun install() {
        WjzFocusDebugOverlayRegistry.content = { coordinator ->
            WjzFocusR8TestDebugOverlay(coordinator)
        }
    }
}

@Composable
private fun WjzFocusR8TestDebugOverlay(coordinator: WjzFocusCoordinator) {
    if (!Prefs.wjzFocusDebugOverlay) return

    var snapshot by remember { mutableStateOf(coordinator.debugSnapshot()) }

    LaunchedEffect(coordinator) {
        while (true) {
            snapshot = coordinator.debugSnapshot()
            delay(250)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "WjzFocus ${snapshot.activeLayer}",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = snapshot.summaryText(),
                color = Color.White.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun WjzFocusDebugSnapshot.summaryText(): String {
    val focusedLeaf = focusedLeafByLayerScope
        .lastOrNull { it.nodeId != null }
        ?.let { "${it.layer}/${it.scopeId ?: "-"}=${it.nodeId}" }
        ?: "-"
    val pendingText = pendingRequests
        .takeLast(3)
        .joinToString("\n") { request ->
            "${request.layer}/${request.scopeId ?: "-"} ${request.nodeId ?: request.intent}"
        }
        .ifBlank { "-" }
    val sourceText = sourceStack
        .takeLast(3)
        .joinToString("\n") { source ->
            "#${source.token} ${source.layer}/${source.scopeId ?: "-"} ${source.nodeId ?: "-"}"
        }
        .ifBlank { "-" }
    val lockedText = lockedDirection?.let { "${it.direction}@${it.layer}" } ?: "-"

    return buildString {
        appendLine("nodes=${registeredNodes.size} pending=${pendingRequests.size} locks=$lockCount disabled=$disabledRegionCount")
        appendLine("leaf=$focusedLeaf")
        appendLine("locked=$lockedText")
        appendLine("pending:")
        appendLine(pendingText)
        appendLine("source:")
        append(sourceText)
    }
}
