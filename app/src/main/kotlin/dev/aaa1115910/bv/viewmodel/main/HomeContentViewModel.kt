package dev.aaa1115910.bv.viewmodel.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.bv.viewmodel.common.DebouncedActivationController
import dev.aaa1115910.bv.component.HomeTopNavItem
import dev.aaa1115910.bv.entity.state.GridViewportState
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.viewmodel.common.KeyedRuntimeAwareViewModel
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class HomeContentViewModel : KeyedRuntimeAwareViewModel<HomeTopNavItem>() {
    private val tabActivation = DebouncedActivationController(
        initial = Prefs.firstHomeTopNavItem,
        scope = viewModelScope,
    )

    val focusedTab get() = tabActivation.focused
    val activeTab get() = tabActivation.active

    fun onTabFocused(target: HomeTopNavItem) = tabActivation.onFocused(target)
    fun onTabClicked(target: HomeTopNavItem) = tabActivation.onClicked(target)

    var isHistorySearching by mutableStateOf(false)
        private set

    fun updateHistorySearching(searching: Boolean) {
        isHistorySearching = searching
    }

    override val activeRuntimeKey: HomeTopNavItem
        get() = activeTab

    private var activationSerialMap by mutableStateOf<Map<HomeTopNavItem, Long>>(emptyMap())
    private var refreshSerialMap by mutableStateOf<Map<HomeTopNavItem, Long>>(emptyMap())
    private var consumedRefreshSerialMap by mutableStateOf<Map<HomeTopNavItem, Long>>(emptyMap())
    private var longPressSerialMap by mutableStateOf<Map<HomeTopNavItem, Long>>(emptyMap())
    private var consumedLongPressSerialMap by mutableStateOf<Map<HomeTopNavItem, Long>>(emptyMap())

    fun markContentReady(tab: HomeTopNavItem) {
        activationSerialMap = activationSerialMap + (tab to ((activationSerialMap[tab] ?: 0L) + 1L))
    }

    fun activationSerialOf(tab: HomeTopNavItem): Long {
        return activationSerialMap[tab] ?: 0L
    }

    fun requestUserRefresh(tab: HomeTopNavItem) {
        refreshSerialMap = refreshSerialMap + (tab to ((refreshSerialMap[tab] ?: 0L) + 1L))
    }

    fun refreshSerialOf(tab: HomeTopNavItem): Long {
        return refreshSerialMap[tab] ?: 0L
    }

    fun consumeRefreshSerial(tab: HomeTopNavItem, serial: Long): Boolean {
        if (serial == 0L || serial <= (consumedRefreshSerialMap[tab] ?: 0L)) return false
        consumedRefreshSerialMap = consumedRefreshSerialMap + (tab to serial)
        return true
    }

    fun requestTopNavLongPress(tab: HomeTopNavItem) {
        longPressSerialMap = longPressSerialMap + (tab to ((longPressSerialMap[tab] ?: 0L) + 1L))
    }

    fun longPressSerialOf(tab: HomeTopNavItem): Long {
        return longPressSerialMap[tab] ?: 0L
    }

    fun consumeLongPressSerial(tab: HomeTopNavItem, serial: Long): Boolean {
        if (serial == 0L || serial <= (consumedLongPressSerialMap[tab] ?: 0L)) return false
        consumedLongPressSerialMap = consumedLongPressSerialMap + (tab to serial)
        return true
    }

    override fun onCleared() {
        tabActivation.cancel()
        super.onCleared()
    }

    private val _viewportMap = MutableStateFlow(persistentHashMapOf<HomeTopNavItem, GridViewportState>())
    val viewportMap: StateFlow<ImmutableMap<HomeTopNavItem, GridViewportState>> = _viewportMap.asStateFlow()

    fun updateViewport(tab: HomeTopNavItem, index: Int, offset: Int) {
        _viewportMap.update {
            it.put(
                tab,
                GridViewportState(
                    index = index,
                    scrollOffset = offset
                )
            )
        }
    }
}
