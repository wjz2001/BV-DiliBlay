package dev.aaa1115910.bv.viewmodel.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.bv.viewmodel.common.DebouncedActivationController
import dev.aaa1115910.bv.component.PgcTopNavItem
import dev.aaa1115910.bv.entity.state.ListViewportState
import dev.aaa1115910.bv.viewmodel.common.KeyedRuntimeAwareViewModel
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class PgcContentViewModel : KeyedRuntimeAwareViewModel<PgcTopNavItem>() {
    private val tabActivation = DebouncedActivationController(
        initial = PgcTopNavItem.Anime,
        scope = viewModelScope,
    )

    val focusedTab get() = tabActivation.focused
    val activeTab get() = tabActivation.active

    fun onTabFocused(target: PgcTopNavItem) = tabActivation.onFocused(target)
    fun onTabClicked(target: PgcTopNavItem) = tabActivation.onClicked(target)

    override val activeRuntimeKey: PgcTopNavItem
        get() = activeTab

    private var refreshSerialMap by mutableStateOf<Map<PgcTopNavItem, Long>>(emptyMap())

    fun requestUserRefresh(tab: PgcTopNavItem) {
        refreshSerialMap = refreshSerialMap + (tab to ((refreshSerialMap[tab] ?: 0L) + 1L))
    }

    fun refreshSerialOf(tab: PgcTopNavItem): Long {
        return refreshSerialMap[tab] ?: 0L
    }

    override fun onCleared() {
        tabActivation.cancel()
        super.onCleared()
    }

    private val viewportMap = MutableStateFlow(persistentHashMapOf<PgcTopNavItem, ListViewportState>())

    fun viewportOf(tab: PgcTopNavItem): ListViewportState {
        return viewportMap.value[tab] ?: ListViewportState()
    }

    fun updateViewport(tab: PgcTopNavItem, index: Int, offset: Int) {
        viewportMap.update {
            it.put(
                tab,
                ListViewportState(
                    index = index,
                    scrollOffset = offset
                )
            )
        }
    }
}
