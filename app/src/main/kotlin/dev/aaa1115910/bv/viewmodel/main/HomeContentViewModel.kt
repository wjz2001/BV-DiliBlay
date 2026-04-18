package dev.aaa1115910.bv.viewmodel.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.bv.viewmodel.common.DebouncedActivationController
import dev.aaa1115910.bv.component.HomeTopNavItem
import dev.aaa1115910.bv.entity.state.GridViewportState
import dev.aaa1115910.bv.util.Prefs
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class HomeContentViewModel : ViewModel() {
    var initialized by mutableStateOf(false)
    private val tabActivation = DebouncedActivationController(
        initial = Prefs.firstHomeTopNavItem,
        scope = viewModelScope,
    )

    val focusedTab get() = tabActivation.focused
    val activeTab get() = tabActivation.active

    fun onTabFocused(target: HomeTopNavItem) = tabActivation.onFocused(target)
    fun onTabClicked(target: HomeTopNavItem) = tabActivation.onClicked(target)

    override fun onCleared() {
        tabActivation.cancel()
        super.onCleared()
    }

    private val viewportMap = mutableStateMapOf<HomeTopNavItem, GridViewportState>()

    fun viewportOf(tab: HomeTopNavItem): GridViewportState {
        return viewportMap[tab] ?: GridViewportState()
    }

    fun updateViewport(tab: HomeTopNavItem, index: Int, offset: Int) {
        viewportMap[tab] = GridViewportState(
            index = index,
            scrollOffset = offset
        )
    }
}