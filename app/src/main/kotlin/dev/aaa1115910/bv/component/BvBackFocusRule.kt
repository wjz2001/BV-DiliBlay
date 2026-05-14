package dev.aaa1115910.bv.component

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

enum class BvBackFocusTarget {
    TopNav,
    TabRow
}

@Stable
class BvBackFocusRegistry {
    private val targets = mutableMapOf<BvBackFocusTarget, BvBackFocusEntry>()
    private var targetCount by mutableIntStateOf(0)

    fun register(target: BvBackFocusTarget, requester: FocusRequester) {
        targets[target] = BvBackFocusEntry(requester = requester)
        targetCount = targets.size
    }

    fun unregister(target: BvBackFocusTarget, requester: FocusRequester) {
        if (targets[target]?.requester == requester) {
            targets.remove(target)
            targetCount = targets.size
        }
    }

    fun updateFocus(target: BvBackFocusTarget, hasFocus: Boolean) {
        targets[target]?.hasFocus = hasFocus
    }

    fun hasTargets(): Boolean {
        return targetCount > 0
    }

    fun handleBack(): Boolean {
        val tabRow = targets[BvBackFocusTarget.TabRow]
        if (tabRow != null && !tabRow.hasFocus) {
            return tabRow.requestFocus()
        }

        val topNav = targets[BvBackFocusTarget.TopNav]
        if (topNav != null && !topNav.hasFocus) {
            return topNav.requestFocus()
        }

        return false
    }

    private fun BvBackFocusEntry.requestFocus(): Boolean {
        return runCatching { requester.requestFocus() }.getOrDefault(false)
    }
}

private data class BvBackFocusEntry(
    val requester: FocusRequester,
    var hasFocus: Boolean = false
)

val LocalBvBackFocusRegistry = compositionLocalOf<BvBackFocusRegistry?> { null }

@Composable
fun rememberBvBackFocusRegistry(): BvBackFocusRegistry {
    return remember { BvBackFocusRegistry() }
}

@Composable
fun BvBackFocusHost(
    onUnhandledBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val registry = rememberBvBackFocusRegistry()

    BackHandler(enabled = registry.hasTargets()) {
        if (!registry.handleBack()) {
            onUnhandledBack()
        }
    }

    CompositionLocalProvider(LocalBvBackFocusRegistry provides registry) {
        content()
    }
}

@Composable
fun RegisterBvBackFocusTarget(
    target: BvBackFocusTarget?,
    requester: FocusRequester,
    enabled: Boolean
) {
    val registry = LocalBvBackFocusRegistry.current

    DisposableEffect(registry, target, requester, enabled) {
        if (registry == null || target == null || !enabled) {
            return@DisposableEffect onDispose { }
        }

        registry.register(target, requester)
        onDispose {
            registry.unregister(target, requester)
        }
    }
}
