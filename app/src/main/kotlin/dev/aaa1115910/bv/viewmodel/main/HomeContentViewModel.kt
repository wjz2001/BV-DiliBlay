package dev.aaa1115910.bv.viewmodel.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.aaa1115910.bv.component.HomeTopNavItem
import dev.aaa1115910.bv.entity.state.GridViewportState
import dev.aaa1115910.bv.util.Prefs
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class HomeContentViewModel : ViewModel() {
    var initialized by mutableStateOf(false)
    var selectedTab by mutableStateOf(Prefs.firstHomeTopNavItem)

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