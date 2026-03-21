package dev.aaa1115910.bv.viewmodel.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.aaa1115910.bv.component.PgcTopNavItem
import dev.aaa1115910.bv.entity.state.ListViewportState
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class PgcContentViewModel : ViewModel() {
    var selectedTab by mutableStateOf(PgcTopNavItem.Anime)

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