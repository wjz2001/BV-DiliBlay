package dev.aaa1115910.bv.viewmodel.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.rank.PopularVideoPage
import dev.aaa1115910.biliapi.entity.ugc.UgcItem
import dev.aaa1115910.biliapi.repositories.RecommendVideoRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fError
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
class PopularViewModel(
    private val recommendVideoRepository: RecommendVideoRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger {}

    private var nextPage = PopularVideoPage()
    var refreshing by mutableStateOf(false)
    var loading by mutableStateOf(false)

    var popularVideoList by mutableStateOf<List<UgcItem>>(emptyList())
        private set

    var initialLoadState by mutableStateOf(LoadState.Idle)
        private set
    @Volatile private var requestVersion = 0L
    private val requestMutex = Mutex()
    private val maxItems = 480

    suspend fun loadMore(
        beforeAppendData: () -> Unit = {}
    ) {
        val expectedVersion = requestVersion
        requestMutex.withLock {
            if (expectedVersion != requestVersion) return
            if (loading) return
            loadData(
                beforeAppendData = beforeAppendData,
                expectedVersion = expectedVersion
            )
        }
    }

    private suspend fun loadData(
        beforeAppendData: () -> Unit,
        expectedVersion: Long
    ) {
        if (expectedVersion != requestVersion) return

        withContext(Dispatchers.Main) { loading = true }

        try {
            val popularVideoData = recommendVideoRepository.getPopularVideos(
                page = nextPage,
                preferApiType = Prefs.apiType
            )

            if (expectedVersion != requestVersion) return

            val filtered = dev.aaa1115910.bv.block.BlockManager.filterList(
                page = dev.aaa1115910.bv.block.BlockPage.Popular,
                list = popularVideoData.list
            ) { it.authorMid }

            withContext(Dispatchers.Main) {
                if (expectedVersion != requestVersion) return@withContext

                beforeAppendData()
                nextPage = popularVideoData.nextPage
                popularVideoList = (popularVideoList + filtered).take(maxItems)

                if (initialLoadState == LoadState.Loading) {
                    initialLoadState = LoadState.Success
                }
            }
        } catch (t: Throwable) {
            logger.fError { "Load popular video list failed: ${t.stackTraceToString()}" }
            withContext(Dispatchers.Main) {
                if (expectedVersion == requestVersion && popularVideoList.isEmpty()) {
                    initialLoadState = LoadState.Error
                }
                "加载热门视频失败: ${t.localizedMessage}".toast(BVApp.context)
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
        popularVideoList = emptyList()
        resetPage()
        loading = false
        initialLoadState = LoadState.Idle
    }

    fun ensureLoaded() {
        if (!initialLoadState.canAutoLoad()) return
        initialLoadState = LoadState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            loadMore()
        }
    }

    fun reloadAll() {
        requestVersion++
        popularVideoList = emptyList()
        resetPage()
        loading = false
        initialLoadState = LoadState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            loadMore()
        }
    }

    fun resetPage() {
        nextPage = PopularVideoPage()
        refreshing = true
    }
}