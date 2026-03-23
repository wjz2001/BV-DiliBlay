package dev.aaa1115910.bv.viewmodel.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import dev.aaa1115910.bv.component.PersonalTopNavItem
import dev.aaa1115910.bv.viewmodel.common.DebouncedActivationController
import dev.aaa1115910.bv.entity.state.GridViewportState
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class PersonalContentViewModel : ViewModel() {
    var initialized by mutableStateOf(false)
    private val tabActivation = DebouncedActivationController(
        initial = PersonalTopNavItem.ToView,
        scope = viewModelScope,
    )

    val focusedTab get() = tabActivation.focused
    val activeTab get() = tabActivation.active

    fun onTabFocused(target: PersonalTopNavItem) = tabActivation.onFocused(target)
    fun onTabClicked(target: PersonalTopNavItem) = tabActivation.onClicked(target)

    override fun onCleared() {
        tabActivation.cancel()
        super.onCleared()
    }

    private val viewportMap = mutableStateMapOf<PersonalTopNavItem, GridViewportState>()

    fun viewportOf(tab: PersonalTopNavItem): GridViewportState {
        return viewportMap[tab] ?: GridViewportState()
    }

    fun updateViewport(tab: PersonalTopNavItem, index: Int, offset: Int) {
        viewportMap[tab] = GridViewportState(
            index = index,
            scrollOffset = offset
        )
    }
}