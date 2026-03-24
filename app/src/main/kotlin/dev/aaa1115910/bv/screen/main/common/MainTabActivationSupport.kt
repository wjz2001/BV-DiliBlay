package dev.aaa1115910.bv.screen.main.common

import android.os.SystemClock
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.aaa1115910.bv.viewmodel.common.LoadState
import kotlinx.coroutines.delay

enum class ActivationBehavior {
    KeepPosition,
    RefreshAndScrollTop
}

class TabActivationGuard<T>(
    private val dedupWindowMs: Long = 1_200L
) {
    private var lastClickRefreshTab: T? = null
    private var lastClickRefreshAtMs: Long = 0L

    private var retryTab: T? = null
    private var retryCount: Int = 0
    private var exhaustedToastShown = false

    fun markClickRefresh(tab: T, nowMs: Long = SystemClock.uptimeMillis()) {
        lastClickRefreshTab = tab
        lastClickRefreshAtMs = nowMs
        ensureRetryTab(tab)
        retryCount = 0
        exhaustedToastShown = false
    }

    fun markActivated(tab: T) {
        ensureRetryTab(tab)
    }

    fun shouldSkipActivationRefresh(
        tab: T,
        nowMs: Long = SystemClock.uptimeMillis()
    ): Boolean {
        return lastClickRefreshTab == tab &&
                nowMs - lastClickRefreshAtMs <= dedupWindowMs
    }

    private fun ensureRetryTab(tab: T) {
        if (retryTab != tab) {
            retryTab = tab
            retryCount = 0
            exhaustedToastShown = false
        }
    }

    fun markLoadRecovered(tab: T, state: LoadState?) {
        ensureRetryTab(tab)
        if (state == LoadState.Success || state == LoadState.Idle) {
            retryCount = 0
            exhaustedToastShown = false
        }
    }

    fun canRetry(tab: T, state: LoadState?, maxRetries: Int = 3): Boolean {
        ensureRetryTab(tab)
        return state == LoadState.Error && retryCount < maxRetries
    }

    fun nextRetryDelayMs(tab: T): Long {
        ensureRetryTab(tab)
        return when (retryCount) {
            0 -> 1_500L
            1 -> 3_000L
            else -> 5_000L
        }
    }

    fun markRetryConsumed(tab: T) {
        ensureRetryTab(tab)
        retryCount += 1
    }

    fun shouldToastFinalFailure(tab: T, state: LoadState?, maxRetries: Int = 3): Boolean {
        ensureRetryTab(tab)
        return state == LoadState.Error &&
                retryCount >= maxRetries &&
                !exhaustedToastShown
    }

    fun markFinalFailureToastShown(tab: T) {
        ensureRetryTab(tab)
        exhaustedToastShown = true
    }
}

@Composable
fun <T> UnifiedTabActivationEffects(
    activeTab: T,
    behaviorOf: (T) -> ActivationBehavior,
    gridStateOf: (T) -> LazyGridState,
    guard: TabActivationGuard<T>,
    currentRetryStateOf: (T) -> LoadState?,
    shouldRetryOf: (T, LoadState?) -> Boolean = { _, state -> state == LoadState.Error },
    onEnsureLoadedSilent: (T) -> Unit,
    onActivationRefreshSilent: (T) -> Unit,
    onRetrySilent: (T) -> Unit,
    onFinalFailureToast: (T) -> Unit,
    maxRetryCount: Int = 3,
) {
    LaunchedEffect(activeTab) {
        guard.markActivated(activeTab)
        when (behaviorOf(activeTab)) {
            ActivationBehavior.KeepPosition -> {
                onEnsureLoadedSilent(activeTab)
            }

            ActivationBehavior.RefreshAndScrollTop -> {
                gridStateOf(activeTab).scrollToItem(0)
                if (!guard.shouldSkipActivationRefresh(activeTab)) {
                    onActivationRefreshSilent(activeTab)
                }
            }
        }
    }

    val retryState = currentRetryStateOf(activeTab)
    LaunchedEffect(activeTab, retryState) {
        guard.markLoadRecovered(activeTab, retryState)

        if (retryState == LoadState.Error && !shouldRetryOf(activeTab, retryState)) {
            if (guard.shouldToastFinalFailure(activeTab, retryState, maxRetries = 0)) {
                guard.markFinalFailureToastShown(activeTab)
                onFinalFailureToast(activeTab)
            }
            return@LaunchedEffect
        }

        if (guard.canRetry(activeTab, retryState, maxRetryCount) &&
            shouldRetryOf(activeTab, retryState)
        ) {
            delay(guard.nextRetryDelayMs(activeTab))

            val latestState = currentRetryStateOf(activeTab)
            if (!guard.canRetry(activeTab, latestState, maxRetryCount)) return@LaunchedEffect
            if (!shouldRetryOf(activeTab, latestState)) return@LaunchedEffect

            guard.markRetryConsumed(activeTab)
            onRetrySilent(activeTab)
            return@LaunchedEffect
        }

        if (guard.shouldToastFinalFailure(activeTab, retryState, maxRetryCount)) {
            guard.markFinalFailureToastShown(activeTab)
            onFinalFailureToast(activeTab)
        }
    }
}