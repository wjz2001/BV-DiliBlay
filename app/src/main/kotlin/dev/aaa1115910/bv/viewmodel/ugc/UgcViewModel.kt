package dev.aaa1115910.bv.viewmodel.ugc

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.aaa1115910.biliapi.entity.ugc.region.UgcFeedPage
import dev.aaa1115910.biliapi.repositories.UgcRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.component.UgcTopNavItem
import dev.aaa1115910.bv.screen.main.ugc.UgcScaffoldState
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class UgcViewModel(private val ugcRepository: UgcRepository) : ViewModel() {
    private val logger = KotlinLogging.logger("UgcViewModel")

    private val _ugcScaffoldStateMap = mutableMapOf<UgcTopNavItem, UgcScaffoldState>()
    val ugcScaffoldStateMap: Map<UgcTopNavItem, UgcScaffoldState> get() = _ugcScaffoldStateMap

    var selectedTab by mutableStateOf(UgcTopNavItem.Douga)

    fun addUgcScaffoldState(item: UgcTopNavItem, state: UgcScaffoldState) {
        _ugcScaffoldStateMap[item] = state
        launchWithIO { initUgcRegionData(item) }
    }

    fun reloadAll(item: UgcTopNavItem) {
        _ugcScaffoldStateMap[item]?.let { state ->
            logger.fInfo { "reload all ${state.ugcType} data" }
            state.nextPage = UgcFeedPage()
            state.hasMore = true
            state.ugcItems.clear()

            if (!state.updating) {
                launchWithIO { initUgcRegionData(item) }
            } else {
                Log.d("UgcViewModel", "正在更新中")
            }
        }
    }

    fun loadMoreData(item: UgcTopNavItem) {
        launchWithIO { loadData(item, isInit = false) }
    }

    fun updateViewport(item: UgcTopNavItem, index: Int, offset: Int) {
        _ugcScaffoldStateMap[item]?.let { state ->
            state.firstVisibleItemIndex = index
            state.firstVisibleItemScrollOffset = offset
        }
    }

    private suspend fun initUgcRegionData(item: UgcTopNavItem) {
        _ugcScaffoldStateMap[item]?.let {
            loadData(item, isInit = true)
        }
    }

    private suspend fun loadData(item: UgcTopNavItem, isInit: Boolean) {
        val state = _ugcScaffoldStateMap[item] ?: return
        if (!state.hasMore || state.updating) return

        state.updating = true
        try {
            if (isInit) {
                logger.fInfo { "load ugc ${state.ugcType} region data" }
                val feedData = ugcRepository.getRegionFeedRcmd(state.ugcType, state.nextPage)

                state.ugcItems.clear()
                state.ugcItems.addAll(feedData.items)
                state.nextPage = feedData.nextPage
                state.hasMore = true
            } else {
                logger.fInfo { "load more ${state.ugcType} region data" }
                val feedData = ugcRepository.getRegionFeedRcmd(state.ugcType, state.nextPage)
                state.ugcItems.addAll(feedData.items)
                state.nextPage = feedData.nextPage
                state.hasMore = feedData.items.isNotEmpty()
            }
        } catch (e: Exception) {
            logger.fInfo { "load ${state.ugcType} data failed: ${e.stackTraceToString()}" }
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
