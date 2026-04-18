package dev.aaa1115910.bv.viewmodel.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.rank.PopularVideoPage
import dev.aaa1115910.biliapi.entity.ugc.UgcItem
import dev.aaa1115910.biliapi.http.entity.AuthFailureException
import dev.aaa1115910.biliapi.repositories.RecommendVideoRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.repository.UserRepository
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fError
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.common.LoadState
import dev.aaa1115910.bv.viewmodel.common.canAutoLoad
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Job
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class PopularViewModel(
    private val recommendVideoRepository: RecommendVideoRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger {}

    private var nextPage = PopularVideoPage()
    var refreshing by mutableStateOf(false)
    var loading by mutableStateOf(false)

    var popularVideoList by mutableStateOf<List<UgcItem>>(emptyList())
        private set

    var initialLoadState by mutableStateOf(LoadState.Idle)
        private set

    var lastFailureWasAuth by mutableStateOf(false)
        private set

    @Volatile private var requestVersion = 0L
    private val requestMutex = Mutex()
    private var loadJob: Job? = null

    fun loadMore(
        beforeAppendData: () -> Unit = {},
        showErrorToast: Boolean = true
    ) {
        if (loadJob?.isActive == true) return

        val expectedVersion = requestVersion
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            requestMutex.withLock {
                if (expectedVersion != requestVersion) return@withLock
                if (loading) return@withLock
                loadData(
                    beforeAppendData = beforeAppendData,
                    expectedVersion = expectedVersion,
                    showErrorToast = showErrorToast
                )
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (loadJob === job) {
                    loadJob = null
                }
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
            lastFailureWasAuth = false
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
                popularVideoList = popularVideoList + filtered
                lastFailureWasAuth = false
                initialLoadState = LoadState.Success
            }
        } catch (_: CancellationException) {
            logger.fInfo { "Load popular video list canceled" }
        } catch (t: Throwable) {
            logger.fError { "Load popular video list failed: ${t.stackTraceToString()}" }

            when (t) {
                is AuthFailureException -> {
                    if (expectedVersion != requestVersion) return
                    withContext(Dispatchers.Main) {
                        if (expectedVersion != requestVersion) return@withContext
                        lastFailureWasAuth = true
                        if (initialLoadState == LoadState.Loading) {
                            initialLoadState = LoadState.Error
                        }
                        if (showErrorToast) {
                            BVApp.context.getString(R.string.exception_auth_failure).toast(BVApp.context)
                        }
                        if (!BuildConfig.DEBUG) userRepository.logout()
                    }
                }

                else -> {
                    if (expectedVersion != requestVersion) return
                    withContext(Dispatchers.Main) {
                        if (expectedVersion != requestVersion) return@withContext
                        lastFailureWasAuth = false
                        if (initialLoadState == LoadState.Loading) {
                            initialLoadState = LoadState.Error
                        }
                        if (showErrorToast) {
                            "加载热门视频失败: ${t.localizedMessage}".toast(BVApp.context)
                        }
                    }
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
        loadJob?.cancel()
        loadJob = null
        popularVideoList = emptyList()
        resetPage()
        loading = false
        initialLoadState = LoadState.Idle
        lastFailureWasAuth = false
    }

    fun ensureLoaded(showErrorToast: Boolean = true) {
        if (!initialLoadState.canAutoLoad()) return
        initialLoadState = LoadState.Loading
        loadMore(showErrorToast = showErrorToast)
    }

    fun reloadAll(showErrorToast: Boolean = true) {
        requestVersion++
        loadJob?.cancel()
        loadJob = null
        popularVideoList = emptyList()
        resetPage()
        loading = false
        initialLoadState = LoadState.Loading
        lastFailureWasAuth = false
        loadMore(showErrorToast = showErrorToast)
    }

    fun resetPage() {
        nextPage = PopularVideoPage()
        refreshing = true
    }
}