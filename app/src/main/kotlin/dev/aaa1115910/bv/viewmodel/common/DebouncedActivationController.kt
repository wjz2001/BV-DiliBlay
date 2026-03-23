package dev.aaa1115910.bv.viewmodel.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DebouncedActivationController<T>(
    initial: T,
    private val scope: CoroutineScope,
) {
    var focused by mutableStateOf(initial)
        private set

    var active by mutableStateOf(initial)
        private set

    private var debounceJob: Job? = null

    fun onFocused(target: T) {
        if (focused == target) return
        focused = target
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(600)
            if (focused == target && active != target) active = target
        }
    }

    fun onClicked(target: T) {
        debounceJob?.cancel()
        if (focused == target && active == target) return
        focused = target
        active = target
    }

    fun cancel() {
        debounceJob?.cancel()
        debounceJob = null
    }
}