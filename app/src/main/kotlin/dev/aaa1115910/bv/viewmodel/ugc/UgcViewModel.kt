package dev.aaa1115910.bv.viewmodel.ugc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.ugc.region.UgcFeedPage
import dev.aaa1115910.biliapi.repositories.UgcRepository
import dev.aaa1115910.bv.component.UgcTopNavItem
import dev.aaa1115910.bv.screen.main.runtime.ContentRuntimeState
import dev.aaa1115910.bv.screen.main.ugc.UgcScaffoldState
import dev.aaa1115910.bv.viewmodel.common.DebouncedActivationController
import dev.aaa1115910.bv.viewmodel.common.KeyedRuntimeAwareViewModel
import dev.aaa1115910.bv.viewmodel.common.LoadState
import dev.aaa1115910.bv.viewmodel.common.canAutoLoad
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class UgcViewModel(private val ugcRepository: UgcRepository) : KeyedRuntimeAwareViewModel<UgcTopNavItem>() {
    private val _ugcScaffoldStateMap = MutableStateFlow(persistentHashMapOf<UgcTopNavItem, UgcScaffoldState>())
    val ugcScaffoldStateMap: StateFlow<ImmutableMap<UgcTopNavItem, UgcScaffoldState>> =
        _ugcScaffoldStateMap.asStateFlow()

    private fun updateState(
        item: UgcTopNavItem,
        transform: (UgcScaffoldState) -> UgcScaffoldState
    ) {
        _ugcScaffoldStateMap.update {
            val old = it[item] ?: return
            it.put(item, transform(old))
        }
    }

    private val tabActivation = DebouncedActivationController(
        initial = UgcTopNavItem.Douga,
        scope = viewModelScope,
    )

    val focusedTab get() = tabActivation.focused
    val activeTab get() = tabActivation.active

    private val tabLoadStateMap = MutableStateFlow(persistentHashMapOf<UgcTopNavItem, LoadState>())
    private val requestGenerationMap = mutableMapOf<UgcTopNavItem, Long>()
    private val requestMutexMap = mutableMapOf<UgcTopNavItem, kotlinx.coroutines.sync.Mutex>()
    private val requestJobMap = mutableMapOf<UgcTopNavItem, Job>()
    private var refreshSerialMap by mutableStateOf<Map<UgcTopNavItem, Long>>(emptyMap())
    private var consumedRefreshSerialMap by mutableStateOf<Map<UgcTopNavItem, Long>>(emptyMap())
    private var preloadJob: Job? = null
    private val recentTabs = LinkedHashSet<UgcTopNavItem>()
    private val maxItemsPerTab = 480

    private fun loadStateOf(item: UgcTopNavItem): LoadState =
        tabLoadStateMap.value[item] ?: LoadState.Idle

    private fun setLoadState(item: UgcTopNavItem, state: LoadState) {
        tabLoadStateMap.update { it.put(item, state) }
    }

    private fun markTabUsed(item: UgcTopNavItem) {
        recentTabs.remove(item)
        recentTabs.add(item)
    }

    fun onTabFocused(target: UgcTopNavItem) = tabActivation.onFocused(target)
    fun onTabClicked(target: UgcTopNavItem) = tabActivation.onClicked(target)

    override val activeRuntimeKey: UgcTopNavItem
        get() = activeTab

    override fun onRuntimeActive(key: UgcTopNavItem) {
        ensureTabState(key)
        ensureLoaded(key)
        trimInactiveData(except = key)
        cancelPreload()
        preloadJob = viewModelScope.launch {
            delay(1500)
            if (activeTab == key && runtimeStateOf(key) == ContentRuntimeState.Active) {
                val target = key.nextOrNull() ?: return@launch
                ensureTabState(target)
                startEnsureLoaded(target)
                trimInactiveData(except = key)
            }
        }
    }

    override fun onRuntimeFrozen(key: UgcTopNavItem) {
        cancelPreload()
        cancelLoad(key)
    }

    override fun onRuntimeDisposed(key: UgcTopNavItem) {
        cancelPreload()
        cancelLoad(key)
    }

    fun requestUserRefresh(tab: UgcTopNavItem) {
        refreshSerialMap = refreshSerialMap + (tab to ((refreshSerialMap[tab] ?: 0L) + 1L))
    }

    fun refreshSerialOf(tab: UgcTopNavItem): Long {
        return refreshSerialMap[tab] ?: 0L
    }

    fun consumeRefreshSerial(tab: UgcTopNavItem, serial: Long): Boolean {
        if (serial == 0L || serial <= (consumedRefreshSerialMap[tab] ?: 0L)) return false
        consumedRefreshSerialMap = consumedRefreshSerialMap + (tab to serial)
        return true
    }

    fun freezeAll() {
        freezeAllRuntimeKeys()
        cancelPreload()
        requestJobMap.values.forEach { it.cancel() }
        requestJobMap.clear()
    }

    private fun generationOf(item: UgcTopNavItem): Long = requestGenerationMap[item] ?: 0L
    private fun bumpGeneration(item: UgcTopNavItem): Long {
        val next = generationOf(item) + 1L
        requestGenerationMap[item] = next
        return next
    }
    private fun mutexOf(item: UgcTopNavItem): kotlinx.coroutines.sync.Mutex =
        requestMutexMap.getOrPut(item) { kotlinx.coroutines.sync.Mutex() }

    fun addUgcScaffoldState(item: UgcTopNavItem, state: UgcScaffoldState) {
        _ugcScaffoldStateMap.update { it.put(item, state) }
    }

    fun ensureTabState(item: UgcTopNavItem) {
        if (!_ugcScaffoldStateMap.value.containsKey(item)) {
            addUgcScaffoldState(
                item,
                UgcScaffoldState(ugcType = item.ugcType)
            )
        }
    }

    fun warmUp(item: UgcTopNavItem) {
        ensureTabState(item)
        ensureLoaded(item)
        trimInactiveData(except = item)
    }

    fun ensureLoaded(item: UgcTopNavItem) {
        startEnsureLoaded(item)
    }

    fun preloadOne(item: UgcTopNavItem?) {
        val target = item ?: return
        if (runtimeStateOf(activeTab) != ContentRuntimeState.Active) return

        cancelPreload()
        ensureTabState(target)
        preloadJob = startEnsureLoaded(target)
        trimInactiveData(except = activeTab)
    }

    fun cancelPreload() {
        preloadJob?.cancel()
        preloadJob = null
    }

    private fun startEnsureLoaded(item: UgcTopNavItem): Job? {
        if (!_ugcScaffoldStateMap.value.containsKey(item)) return null
        markTabUsed(item)
        if (!loadStateOf(item).canAutoLoad()) return null
        setLoadState(item, LoadState.Loading)
        return launchLoad(item) {
            loadData(item, isInit = true, expectedGeneration = generationOf(item))
        }
    }

    fun reloadAll(item: UgcTopNavItem) {
        val state = _ugcScaffoldStateMap.value[item] ?: return
        markTabUsed(item)
        val newGen = bumpGeneration(item)

        _ugcScaffoldStateMap.update {
            it.put(
                item,
                state.copy(
                    ugcItems = emptyList(),
                    nextPage = UgcFeedPage(),
                    hasMore = true,
                    updating = false
                )
            )
        }
        setLoadState(item, LoadState.Loading)

        launchLoad(item) { loadData(item, isInit = true, expectedGeneration = newGen) }
    }

    fun loadMoreData(item: UgcTopNavItem) {
        if (!_ugcScaffoldStateMap.value.containsKey(item)) return
        if (loadStateOf(item) != LoadState.Success) return
        launchLoad(item) { loadData(item, isInit = false, expectedGeneration = generationOf(item)) }
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
            val state = _ugcScaffoldStateMap.value[item] ?: return
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
            } catch (t: CancellationException) {
                updateState(item) { it.copy(updating = false) }
                if (isInit && expectedGeneration == generationOf(item)) {
                    setLoadState(item, LoadState.Idle)
                }
                throw t
            } catch (t: Throwable) {
                if (expectedGeneration != generationOf(item)) return
                updateState(item) { it.copy(updating = false) }
                val current = _ugcScaffoldStateMap.value[item]
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
            _ugcScaffoldStateMap.update { it.remove(evict) }
            requestGenerationMap.remove(evict)
            requestMutexMap.remove(evict)
            cancelLoad(evict)
            tabLoadStateMap.update { it.remove(evict) }
        }
    }

    override fun onCleared() {
        tabActivation.cancel()
        cancelPreload()
        requestJobMap.values.forEach { it.cancel() }
        requestJobMap.clear()
        super.onCleared()
    }

    private fun cancelLoad(item: UgcTopNavItem) {
        requestJobMap.remove(item)?.cancel()
    }

    private fun launchLoad(item: UgcTopNavItem, block: suspend () -> Unit): Job {
        cancelLoad(item)
        val job = viewModelScope.launch(Dispatchers.IO) {
            block()
        }
        requestJobMap[item] = job
        job.invokeOnCompletion {
            viewModelScope.launch {
                if (requestJobMap[item] == job) {
                    requestJobMap.remove(item)
                }
            }
        }
        return job
    }

    private fun UgcTopNavItem.nextOrNull(): UgcTopNavItem? {
        return UgcTopNavItem.entries.getOrNull(ordinal + 1)
    }
}
