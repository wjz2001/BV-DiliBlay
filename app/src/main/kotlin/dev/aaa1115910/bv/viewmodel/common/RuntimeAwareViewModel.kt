package dev.aaa1115910.bv.viewmodel.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.aaa1115910.bv.screen.main.runtime.ContentRuntimeState

abstract class RuntimeAwareViewModel : ViewModel() {
    var runtimeState by mutableStateOf(ContentRuntimeState.NotCreated)
        private set

    val runtimeActive: Boolean
        get() = runtimeState == ContentRuntimeState.Active

    fun updateRuntimeState(state: ContentRuntimeState) {
        if (runtimeState == state) return
        runtimeState = state
        when (state) {
            ContentRuntimeState.Shell -> onRuntimeShell()
            ContentRuntimeState.Active -> onRuntimeActive()
            ContentRuntimeState.Frozen -> onRuntimeFrozen()
            ContentRuntimeState.Disposed -> onRuntimeDisposed()
            ContentRuntimeState.NotCreated -> Unit
        }
    }

    protected open fun onRuntimeShell() = Unit
    protected open fun onRuntimeActive() = Unit
    protected open fun onRuntimeFrozen() = Unit
    protected open fun onRuntimeDisposed() = Unit
}

abstract class KeyedRuntimeAwareViewModel<K>(
    private val maxRetainedRuntimeKeys: Int = 3
) : ViewModel() {
    private var runtimeStateMap by mutableStateOf<Map<K, ContentRuntimeState>>(emptyMap())
    private val runtimeStateOrder = mutableListOf<K>()

    protected abstract val activeRuntimeKey: K?

    fun runtimeStateOf(key: K): ContentRuntimeState {
        return runtimeStateMap[key] ?: ContentRuntimeState.NotCreated
    }

    fun updateRuntimeState(key: K, state: ContentRuntimeState) {
        if (runtimeStateMap[key] == state) return
        runtimeStateMap = runtimeStateMap + (key to state)
        when (state) {
            ContentRuntimeState.Shell -> onRuntimeShell(key)
            ContentRuntimeState.Active -> {
                markRuntimeKeyUsed(key)
                trimRuntimeStates()
                onRuntimeActive(key)
            }

            ContentRuntimeState.Frozen -> {
                markRuntimeKeyUsed(key)
                trimRuntimeStates()
                onRuntimeFrozen(key)
            }

            ContentRuntimeState.Disposed -> {
                runtimeStateOrder.remove(key)
                onRuntimeDisposed(key)
            }

            ContentRuntimeState.NotCreated -> Unit
        }
    }

    protected fun freezeAllRuntimeKeys() {
        runtimeStateMap.keys.forEach { key ->
            updateRuntimeState(key, ContentRuntimeState.Frozen)
        }
    }

    private fun markRuntimeKeyUsed(key: K) {
        runtimeStateOrder.remove(key)
        runtimeStateOrder.add(key)
    }

    private fun trimRuntimeStates() {
        while (runtimeStateOrder.size > maxRetainedRuntimeKeys) {
            val evictKey = runtimeStateOrder.firstOrNull {
                it != activeRuntimeKey && runtimeStateMap[it] == ContentRuntimeState.Frozen
            } ?: break
            runtimeStateOrder.remove(evictKey)
            runtimeStateMap = runtimeStateMap + (evictKey to ContentRuntimeState.Disposed)
            onRuntimeDisposed(evictKey)
        }
    }

    protected open fun onRuntimeShell(key: K) = Unit
    protected open fun onRuntimeActive(key: K) = Unit
    protected open fun onRuntimeFrozen(key: K) = Unit
    protected open fun onRuntimeDisposed(key: K) = Unit
}
