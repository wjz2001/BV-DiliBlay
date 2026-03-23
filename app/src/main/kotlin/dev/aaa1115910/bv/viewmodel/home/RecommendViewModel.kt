package dev.aaa1115910.bv.viewmodel.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.home.RecommendPage
import dev.aaa1115910.biliapi.entity.ugc.UgcItem
import dev.aaa1115910.biliapi.repositories.RecommendVideoRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fError
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.common.LoadState
import dev.aaa1115910.bv.viewmodel.common.canAutoLoad
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class RecommendViewModel(
    private val recommendVideoRepository: RecommendVideoRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger {}
    var recommendVideoList by mutableStateOf<List<UgcItem>>(emptyList())
        private set

    private var nextPage = RecommendPage()
    var refreshing by mutableStateOf(true)
    var loading by mutableStateOf(false)

    var initialLoadState by mutableStateOf(LoadState.Idle)
        private set
    @Volatile private var requestVersion = 0L
    private val requestMutex = Mutex()
    private val maxItems = 480

    suspend fun loadMore(
        beforeAppendData: () -> Unit = {},
        showErrorToast: Boolean = true
    ) {
        val expectedVersion = requestVersion
        requestMutex.withLock {
            if (expectedVersion != requestVersion) return
            if (loading) return

            var loadCount = 0
            val maxLoadMoreCount = 3

            if (recommendVideoList.isEmpty()) {
                while (recommendVideoList.size < 24 && loadCount < maxLoadMoreCount) {
                    val callback = if (loadCount == 0) beforeAppendData else ({})
                    loadData(
                        beforeAppendData = callback,
                        expectedVersion = expectedVersion,
                        showErrorToast = showErrorToast
                    )
                    if (expectedVersion != requestVersion) return
                    if (loadCount != 0) logger.fInfo { "Load more recommend videos because items too less" }
                    loadCount++
                }
            } else {
                loadData(
                    beforeAppendData = beforeAppendData,
                    expectedVersion = expectedVersion,
                    showErrorToast = showErrorToast
                )
            }
        }
    }

    private suspend fun loadData(
        beforeAppendData: () -> Unit,
        expectedVersion: Long,
        showErrorToast: Boolean
    ) {
        if (expectedVersion != requestVersion) return

        withContext(Dispatchers.Main) { loading = true }

        try {
            val recommendData = recommendVideoRepository.getRecommendVideos(
                page = nextPage,
                preferApiType = Prefs.apiType
            )

            if (expectedVersion != requestVersion) return

            val filtered = dev.aaa1115910.bv.block.BlockManager.filterList(
                page = dev.aaa1115910.bv.block.BlockPage.Recommend,
                list = recommendData.items
            ) { it.authorMid }

            withContext(Dispatchers.Main) {
                if (expectedVersion != requestVersion) return@withContext

                beforeAppendData()
                nextPage = recommendData.nextPage
                recommendVideoList = (recommendVideoList + filtered).take(maxItems)

                if (initialLoadState == LoadState.Loading) {
                    initialLoadState = LoadState.Success
                }
            }
        } catch (t: Throwable) {
            logger.fError { "Load recommend video list failed: ${t.stackTraceToString()}" }
            withContext(Dispatchers.Main) {
                if (expectedVersion == requestVersion && recommendVideoList.isEmpty()) {
                    initialLoadState = LoadState.Error
                }
                if (showErrorToast) {
                    "加载推荐视频失败: ${t.localizedMessage}".toast(BVApp.context)
                }
            }
        } finally {
            withContext(Dispatchers.Main) {
                if (expectedVersion == requestVersion) {
                    loading = false
                }
            }
        }
    }

    fun clearData() {
        requestVersion++
        recommendVideoList = emptyList()
        resetPage()
        loading = false
        initialLoadState = LoadState.Idle
    }

    fun ensureLoaded(showErrorToast: Boolean = true) {
        if (!initialLoadState.canAutoLoad()) return
        initialLoadState = LoadState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            loadMore(showErrorToast = showErrorToast)
        }
    }

    fun reloadAll(showErrorToast: Boolean = true) {
        requestVersion++
        recommendVideoList = emptyList()
        resetPage()
        loading = false
        initialLoadState = LoadState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            loadMore(showErrorToast = showErrorToast)
        }
    }

    fun resetPage() {
        nextPage = RecommendPage()
        refreshing = true
    }
}