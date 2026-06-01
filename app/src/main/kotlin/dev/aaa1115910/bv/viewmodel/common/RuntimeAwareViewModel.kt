package dev.aaa1115910.bv.viewmodel.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.bv.screen.main.runtime.ContentRuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object PlayerGlobalFreeze {
    private val _frozen = MutableStateFlow(false)
    val frozen = _frozen.asStateFlow()

    val isFrozen: Boolean
        get() = _frozen.value

    fun enter() {
        _frozen.value = true
    }

    fun exit() {
        _frozen.value = false
    }
}

suspend fun awaitPlayerGlobalUnfrozen() {
    if (!PlayerGlobalFreeze.isFrozen) return
    PlayerGlobalFreeze.frozen.first { frozen -> !frozen }
}

abstract class RuntimeAwareViewModel : ViewModel() {
    var runtimeState by mutableStateOf(ContentRuntimeState.NotCreated)
        private set

    val runtimeActive: Boolean
        get() = runtimeState == ContentRuntimeState.Active && !PlayerGlobalFreeze.isFrozen

    init {
        viewModelScope.launch {
            PlayerGlobalFreeze.frozen.collect { frozen ->
                if (runtimeState != ContentRuntimeState.Active) return@collect
                if (frozen) {
                    onRuntimeFrozen()
                } else {
                    onRuntimeActive()
                }
            }
        }
    }

    fun updateRuntimeState(state: ContentRuntimeState) {
        if (runtimeState == state) return
        runtimeState = state
        when (state) {
            ContentRuntimeState.Shell -> onRuntimeShell()
            ContentRuntimeState.Active -> {
                if (PlayerGlobalFreeze.isFrozen) onRuntimeFrozen() else onRuntimeActive()
            }
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

    init {
        viewModelScope.launch {
            PlayerGlobalFreeze.frozen.collect { frozen ->
                if (frozen) {
                    runtimeStateMap
                        .filterValues { it == ContentRuntimeState.Active }
                        .keys
                        .forEach { key -> onRuntimeFrozen(key) }
                } else {
                    if (runtimeStateMap.values.none { it == ContentRuntimeState.Active }) return@collect
                    val key = activeRuntimeKey ?: return@collect
                    if (runtimeStateOf(key) == ContentRuntimeState.Active) {
                        onRuntimeActive(key)
                    }
                }
            }
        }
    }

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
                if (PlayerGlobalFreeze.isFrozen) onRuntimeFrozen(key) else onRuntimeActive(key)
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
