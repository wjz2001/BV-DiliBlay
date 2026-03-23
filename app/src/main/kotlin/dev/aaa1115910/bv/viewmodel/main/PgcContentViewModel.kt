package dev.aaa1115910.bv.viewmodel.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.bv.viewmodel.common.DebouncedActivationController
import dev.aaa1115910.bv.component.PgcTopNavItem
import dev.aaa1115910.bv.entity.state.ListViewportState
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class PgcContentViewModel : ViewModel() {
    private val tabActivation = DebouncedActivationController(
        initial = PgcTopNavItem.Anime,
        scope = viewModelScope,
    )

    val focusedTab get() = tabActivation.focused
    val activeTab get() = tabActivation.active

    fun onTabFocused(target: PgcTopNavItem) = tabActivation.onFocused(target)
    fun onTabClicked(target: PgcTopNavItem) = tabActivation.onClicked(target)

    override fun onCleared() {
        tabActivation.cancel()
        super.onCleared()
    }

    private val viewportMap = mutableStateMapOf<PgcTopNavItem, ListViewportState>()

    fun viewportOf(tab: PgcTopNavItem): ListViewportState {
        return viewportMap[tab] ?: ListViewportState()
    }

    fun updateViewport(tab: PgcTopNavItem, index: Int, offset: Int) {
        viewportMap[tab] = ListViewportState(
            index = index,
            scrollOffset = offset
        )
    }
}