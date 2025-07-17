package dev.aaa1115910.bv.viewmodel.ugc

import androidx.lifecycle.ViewModel
import dev.aaa1115910.biliapi.entity.ugc.region.UgcRegionPage
import dev.aaa1115910.biliapi.repositories.UgcRepository
import dev.aaa1115910.bv.component.UgcTopNavItem
import dev.aaa1115910.bv.screen.main.ugc.UgcScaffoldState
import dev.aaa1115910.bv.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.bv.BVApp
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class UgcViewModel(private val ugcRepository: UgcRepository) : ViewModel() {
    private val _ugcScaffoldStateMap = mutableMapOf<UgcTopNavItem, UgcScaffoldState>()
    val ugcScaffoldStateMap: Map<UgcTopNavItem, UgcScaffoldState> get() = _ugcScaffoldStateMap

    fun addUgcScaffoldState(item: UgcTopNavItem, state: UgcScaffoldState) {
        _ugcScaffoldStateMap[item] = state
        launchWithIO { initUgcRegionData(item) }
    }

    fun reloadAll(item: UgcTopNavItem) {
        _ugcScaffoldStateMap[item]?.let { state ->
            if (!state.updating) {
                state.nextPage = UgcRegionPage()
                state.hasMore = true
                state.ugcItems.clear()
                launchWithIO { initUgcRegionData(item) }
            }
        }
    }

    fun loadMoreData(item: UgcTopNavItem) {
        launchWithIO { loadData(item, isInit = false) }
    }

    private suspend fun initUgcRegionData(item: UgcTopNavItem) {
        _ugcScaffoldStateMap[item]?.let {
            loadData(item, isInit = true)
        }
    }

    private suspend fun loadData(item: UgcTopNavItem, isInit: Boolean) {
        val state = _ugcScaffoldStateMap[item] ?: return
        if (!state.hasMore) return

        state.updating = true
        try {
            if (isInit) {
                val data = ugcRepository.getRegionData(state.ugcType)
                state.ugcItems.clear()
                state.ugcItems.addAll(data.items)
                state.nextPage = data.next
                state.hasMore = true
            } else {
                val data = ugcRepository.getRegionMoreData(state.ugcType)
                state.ugcItems.addAll(data.items)
                state.nextPage = data.next
                state.hasMore = data.items.isNotEmpty()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                val msg = if (isInit) "加载 ${state.ugcType} 数据失败" else "加载 ${state.ugcType} 更多推荐失败"
                "$msg: ${e.message}".toast(BVApp.context)
            }
        } finally {
            state.updating = false
        }
    }

    private fun launchWithIO(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            block()
        }
    }
}
