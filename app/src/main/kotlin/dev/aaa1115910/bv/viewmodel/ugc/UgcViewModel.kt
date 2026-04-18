package dev.aaa1115910.bv.viewmodel.ugc

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import dev.aaa1115910.biliapi.entity.ugc.region.UgcFeedPage
import dev.aaa1115910.biliapi.repositories.UgcRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.component.UgcTopNavItem
import dev.aaa1115910.bv.screen.main.ugc.UgcScaffoldState
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.common.DebouncedActivationController
import dev.aaa1115910.bv.viewmodel.common.LoadState
import dev.aaa1115910.bv.viewmodel.common.canAutoLoad
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class UgcViewModel(private val ugcRepository: UgcRepository) : ViewModel() {
    private val logger = KotlinLogging.logger("UgcViewModel")

    private val _ugcScaffoldStateMap = mutableStateMapOf<UgcTopNavItem, UgcScaffoldState>()
    val ugcScaffoldStateMap: Map<UgcTopNavItem, UgcScaffoldState> get() = _ugcScaffoldStateMap

    private fun updateState(
        item: UgcTopNavItem,
        transform: (UgcScaffoldState) -> UgcScaffoldState
    ) {
        val old = _ugcScaffoldStateMap[item] ?: return
        _ugcScaffoldStateMap[item] = transform(old)
    }

    private val tabActivation = DebouncedActivationController(
        initial = UgcTopNavItem.Douga,
        scope = viewModelScope,
    )

    val focusedTab get() = tabActivation.focused
    val activeTab get() = tabActivation.active

    private val tabLoadStateMap = mutableStateMapOf<UgcTopNavItem, LoadState>()
    private val requestGenerationMap = mutableMapOf<UgcTopNavItem, Long>()
    private val requestMutexMap = mutableMapOf<UgcTopNavItem, kotlinx.coroutines.sync.Mutex>()
    private val recentTabs = LinkedHashSet<UgcTopNavItem>()
    private val maxItemsPerTab = 480

    private fun loadStateOf(item: UgcTopNavItem): LoadState =
        tabLoadStateMap[item] ?: LoadState.Idle

    private fun setLoadState(item: UgcTopNavItem, state: LoadState) {
        tabLoadStateMap[item] = state
    }

    private fun markTabUsed(item: UgcTopNavItem) {
        recentTabs.remove(item)
        recentTabs.add(item)
    }

    fun onTabFocused(target: UgcTopNavItem) = tabActivation.onFocused(target)
    fun onTabClicked(target: UgcTopNavItem) = tabActivation.onClicked(target)

    private fun generationOf(item: UgcTopNavItem): Long = requestGenerationMap[item] ?: 0L
    private fun bumpGeneration(item: UgcTopNavItem): Long {
        val next = generationOf(item) + 1L
        requestGenerationMap[item] = next
        return next
    }
    private fun mutexOf(item: UgcTopNavItem): kotlinx.coroutines.sync.Mutex =
        requestMutexMap.getOrPut(item) { kotlinx.coroutines.sync.Mutex() }

    fun addUgcScaffoldState(item: UgcTopNavItem, state: UgcScaffoldState) {
        _ugcScaffoldStateMap[item] = state
    }

    fun warmUp(item: UgcTopNavItem) {
        if (!_ugcScaffoldStateMap.containsKey(item)) {
            addUgcScaffoldState(
                item,
                UgcScaffoldState(ugcType = item.ugcTypeV2)
            )
        }
        ensureLoaded(item)
        trimInactiveData(except = item)
    }

    fun ensureLoaded(item: UgcTopNavItem) {
        if (!_ugcScaffoldStateMap.containsKey(item)) return
        markTabUsed(item)
        if (!loadStateOf(item).canAutoLoad()) return
        setLoadState(item, LoadState.Loading)
        launchWithIO { loadData(item, isInit = true, expectedGeneration = generationOf(item)) }
    }

    fun reloadAll(item: UgcTopNavItem) {
        val state = _ugcScaffoldStateMap[item] ?: return
        markTabUsed(item)
        val newGen = bumpGeneration(item)

        _ugcScaffoldStateMap[item] = state.copy(
            ugcItems = emptyList(),
            nextPage = UgcFeedPage(),
            hasMore = true,
            updating = false
        )
        setLoadState(item, LoadState.Loading)

        launchWithIO { loadData(item, isInit = true, expectedGeneration = newGen) }
    }

    fun loadMoreData(item: UgcTopNavItem) {
        if (!_ugcScaffoldStateMap.containsKey(item)) return
        if (loadStateOf(item) != LoadState.Success) return
        launchWithIO { loadData(item, isInit = false, expectedGeneration = generationOf(item)) }
    }

    fun updateViewport(item: UgcTopNavItem, index: Int, offset: Int) {
        updateState(item) { state ->
            state.copy(
                firstVisibleItemIndex = index,
                firstVisibleItemScrollOffset = offset
            )
        }
    }

    private suspend fun loadData(item: UgcTopNavItem, isInit: Boolean, expectedGeneration: Long) {
        mutexOf(item).withLock {
            val state = _ugcScaffoldStateMap[item] ?: return
            if (expectedGeneration != generationOf(item)) return
            if (!state.hasMore || state.updating) return

            updateState(item) { it.copy(updating = true) }
            try {
                val feedData = ugcRepository.getRegionFeedRcmd(state.ugcType, state.nextPage)
                if (expectedGeneration != generationOf(item)) return

                updateState(item) { current ->
                    if (expectedGeneration != generationOf(item)) {
                        current
                    } else {
                        val mergedItems = if (isInit) {
                            feedData.items.take(maxItemsPerTab)
                        } else {
                            current.ugcItems + feedData.items
                        }

                        current.copy(
                            ugcItems = mergedItems,
                            nextPage = feedData.nextPage,
                            hasMore = feedData.items.isNotEmpty(),
                            updating = false
                        )
                    }
                }

                if (isInit && expectedGeneration == generationOf(item)) {
                    setLoadState(item, LoadState.Success)
                }
            } catch (t: Throwable) {
                if (expectedGeneration != generationOf(item)) return
                updateState(item) { it.copy(updating = false) }
                val current = _ugcScaffoldStateMap[item]
                if (isInit && current != null && current.ugcItems.isEmpty()) {
                    setLoadState(item, LoadState.Error)
                }
            }
        }
    }

    fun trimInactiveData(except: UgcTopNavItem, keepCount: Int = 3) {
        markTabUsed(except)

        while (recentTabs.size > keepCount) {
            val evict = recentTabs.firstOrNull() ?: break
            if (evict == except) {
                recentTabs.remove(evict)
                recentTabs.add(evict)
                continue
            }

            recentTabs.remove(evict)
            _ugcScaffoldStateMap.remove(evict)
            requestGenerationMap.remove(evict)
            requestMutexMap.remove(evict)
            tabLoadStateMap.remove(evict)
        }
    }

    override fun onCleared() {
        tabActivation.cancel()
        super.onCleared()
    }

    private fun launchWithIO(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            block()
        }
    }
}
