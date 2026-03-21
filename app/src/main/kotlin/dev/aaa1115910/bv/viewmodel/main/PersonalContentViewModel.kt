package dev.aaa1115910.bv.viewmodel.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.aaa1115910.bv.component.PersonalTopNavItem
import dev.aaa1115910.bv.entity.state.GridViewportState
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class PersonalContentViewModel : ViewModel() {
    var initialized by mutableStateOf(false)
    var selectedTab by mutableStateOf(PersonalTopNavItem.ToView)

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