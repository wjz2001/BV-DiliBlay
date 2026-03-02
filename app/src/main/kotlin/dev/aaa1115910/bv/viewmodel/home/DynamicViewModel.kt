package dev.aaa1115910.bv.viewmodel.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.user.DynamicVideo
import dev.aaa1115910.biliapi.http.entity.AuthFailureException
import dev.aaa1115910.biliapi.repositories.UserRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import dev.aaa1115910.bv.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import dev.aaa1115910.bv.repository.UserRepository as BvUserRepository
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class DynamicViewModel(
    private val bvUserRepository: BvUserRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger {}

    val dynamicList = mutableStateListOf<DynamicVideo>()

    private var currentPage = 0
    var loading by mutableStateOf(false)
        private set

    var hasMore by mutableStateOf(true)
        private set

    // 历史翻页主游标
    private var historyOffset: String? = null

    // “刷新基线”：只在 page=1 才会有值；历史页经常为空，所以不能被空值覆盖
    private var updateBaseline: String? = null

    // 刷新最多补齐多少页新增：太大刷新会变慢
    private val maxRefreshPages: Int = 4

    // 防并发刷新（菜单键连按、或焦点频繁触发）
    private val refreshMutex = Mutex()

    val isLogin get() = bvUserRepository.isLogin

    enum class LoadMode {
        More,        // 触底翻页：按 historyOffset 往后拉历史
        RefreshNew   // 增量刷新：只拉最新页，prepend 新增项
    }

    // single-flight：同一时间只允许一个请求在途
    private var loadJob: Job? = null

    // generation：刷新时 +1。旧请求晚到也不会再回写列表/游标
    @Volatile
    private var generation: Long = 0L

    // 大列表下去重别每次 O(n) 扫描：维护一个 aidSet（只在主线程读写）
    private val aidSet = HashSet<Long>(4096)

    // 防止“焦点移到 TopNav”高频触发刷新造成请求抖动
    private var lastRefreshMs: Long = 0L

    // 新增内容后通知 UI 瞬移到顶部（一次性事件）
    private val _scrollToTopEvent = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val scrollToTopEvent: SharedFlow<Unit> = _scrollToTopEvent.asSharedFlow()

    suspend fun loadMore(mode: LoadMode = LoadMode.More, showNoUpdateToast: Boolean = false) {
        if (!bvUserRepository.isLogin) return

        when (mode) {
            LoadMode.RefreshNew -> refreshNewInternal(showNoUpdateToast)
            LoadMode.More -> loadMoreInternal()
        }
    }

    private suspend fun setLoading(value: Boolean, expectedGen: Long) {
        withContext(Dispatchers.Main) {
            if (expectedGen != generation) return@withContext
            loading = value
        }
    }

    private suspend fun refreshNewInternal(showNoUpdateToast: Boolean) {
        refreshMutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastRefreshMs < 1500) return
            lastRefreshMs = now

            // 只取消，不等待：减少刷新起步等待
            loadJob?.cancel()
            loadJob = null

            // 让旧请求结果失效（即使网络层取消不及时）
            generation++
            val expectedGen = generation

            val knownAidsSnapshot: HashSet<Long> = withContext(Dispatchers.Main) { HashSet(aidSet) }
            val seenAids = HashSet<Long>(knownAidsSnapshot.size + 256).apply { addAll(knownAidsSnapshot) }
            val hadItemsBefore = withContext(Dispatchers.Main) { dynamicList.isNotEmpty() }

            val baselineSeed = updateBaseline.orEmpty()

            setLoading(true, expectedGen)
            try {
                // 只拉 page=1，立即展示
                val firstData = userRepository.getDynamicVideos(
                    page = 1,
                    offset = "",
                    updateBaseline = baselineSeed,
                    preferApiType = Prefs.apiType
                )
                if (!coroutineContext.isActive || expectedGen != generation) return

                val firstFiltered = dev.aaa1115910.bv.block.BlockManager.filterList(
                    page = dev.aaa1115910.bv.block.BlockPage.Dynamics,
                    list = firstData.videos
                ) { it.authorMid }

                val firstNewItems = ArrayList<DynamicVideo>(firstFiltered.size)
                var firstOverlapCount = 0
                for (item in firstFiltered) {
                    if (knownAidsSnapshot.contains(item.aid)) firstOverlapCount++
                    if (seenAids.add(item.aid)) firstNewItems.add(item)
                }

                val firstBaseline = firstData.updateBaseline.takeIf { it.isNotBlank() }
                val firstOffset = firstData.historyOffset
                val firstHasMore = firstData.hasMore

                withContext(Dispatchers.Main) {
                    if (expectedGen != generation) return@withContext

                    if (firstNewItems.isNotEmpty()) {
                        for (item in firstNewItems) aidSet.add(item.aid)
                        dynamicList.addAll(0, firstNewItems)
                        // 第一阶段有新增：立即滚顶
                        _scrollToTopEvent.tryEmit(Unit)
                    }

                    if (!firstBaseline.isNullOrBlank()) {
                        updateBaseline = firstBaseline
                    }

                    // 首次加载时初始化历史翻页链
                    if (currentPage == 0 && historyOffset == null) {
                        currentPage = 1
                        historyOffset = firstOffset
                        hasMore = firstHasMore
                    }
                }

                // 立刻结束 loading，首屏反馈不被后台补齐阻塞
                setLoading(false, expectedGen)

                // 后台补齐 page=2..N
                val shouldCatchUp = firstHasMore && maxRefreshPages > 1
                if (shouldCatchUp) {
                    val baselineForCatchUp = firstBaseline ?: baselineSeed

                    val job = viewModelScope.launch(Dispatchers.IO) {
                        var totalAdded = firstNewItems.size
                        var cursorOffset = firstOffset
                        val stopByOverlap = (firstNewItems.isEmpty() && firstOverlapCount > 0)

                        try {
                            if (!stopByOverlap) {
                                for (page in 2..maxRefreshPages) {
                                    if (!isActive || expectedGen != generation) return@launch
                                    if (cursorOffset.isBlank()) break

                                    val data = userRepository.getDynamicVideos(
                                        page = page,
                                        offset = cursorOffset,
                                        updateBaseline = baselineForCatchUp,
                                        preferApiType = Prefs.apiType
                                    )
                                    if (!isActive || expectedGen != generation) return@launch

                                    val filtered = dev.aaa1115910.bv.block.BlockManager.filterList(
                                        page = dev.aaa1115910.bv.block.BlockPage.Dynamics,
                                        list = data.videos
                                    ) { it.authorMid }

                                    val pageNewItems = ArrayList<DynamicVideo>(filtered.size)
                                    var pageOverlapCount = 0
                                    for (item in filtered) {
                                        if (knownAidsSnapshot.contains(item.aid)) pageOverlapCount++
                                        if (seenAids.add(item.aid)) pageNewItems.add(item)
                                    }

                                    if (pageNewItems.isNotEmpty()) {
                                        withContext(Dispatchers.Main) {
                                            if (expectedGen != generation) return@withContext
                                            for (item in pageNewItems) aidSet.add(item.aid)
                                            dynamicList.addAll(0, pageNewItems)
                                        }
                                        totalAdded += pageNewItems.size
                                    }

                                    cursorOffset = data.historyOffset

                                    // 命中重叠边界：结束补齐
                                    if (pageNewItems.isEmpty() && pageOverlapCount > 0) break
                                    if (!data.hasMore) break
                                }
                            }
                            
                            if (showNoUpdateToast && hadItemsBefore) {
                                withContext(Dispatchers.Main) {
                                    if (expectedGen != generation) return@withContext
                                    if (totalAdded > 0) {
                                        "更新了 ${totalAdded} 条".toast(BVApp.context)
                                    } else {
                                        "没有更新的了".toast(BVApp.context)
                                    }
                                }
                            }
                        } catch (_: CancellationException) {
                            logger.fInfo { "Refresh catch-up canceled" }
                        } catch (e: Exception) {
                            if (expectedGen != generation) return@launch
                            logger.fWarn { "Refresh catch-up failed: ${e.stackTraceToString()}" }
                        }
                    }

                    loadJob = job
                    job.invokeOnCompletion {
                        if (loadJob === job) loadJob = null
                    }
                } else {
                    // 没有后台补齐时，直接给出提示
                    if (showNoUpdateToast && hadItemsBefore) {
                        withContext(Dispatchers.Main) {
                            if (expectedGen != generation) return@withContext
                            if (firstNewItems.isNotEmpty()) {
                                "更新了 ${firstNewItems.size} 条".toast(BVApp.context)
                            } else {
                                "没有更新的了".toast(BVApp.context)
                            }
                        }
                    }
                }
            } catch (_: CancellationException) {
                logger.fInfo { "Refresh new canceled" }
            } catch (e: Exception) {
                if (expectedGen != generation) return
                logger.fWarn { "Refresh new failed: ${e.stackTraceToString()}" }
                withContext(Dispatchers.Main) {
                    "刷新动态失败: ${e.localizedMessage}".toast(BVApp.context)
                }
            } finally {
                // 阶段1失败或提前 return 时兜底关 loading
                setLoading(false, expectedGen)
            }
        }
    }

    private fun loadMoreInternal() {
        if (!hasMore) return

        // 有在途请求就不再发新请求（避免并发 append / 重复 aid）
        if (loadJob?.isActive == true) return

        val expectedGen = generation
        val nextPage = currentPage + 1
        val offset = historyOffset.orEmpty()

        loadJob = viewModelScope.launch(Dispatchers.IO) {
            loadHistoryPage(expectedGen, nextPage, offset)
        }.also { job ->
            job.invokeOnCompletion {
                if (loadJob === job) loadJob = null
            }
        }
    }

    private suspend fun loadHistoryPage(expectedGen: Long, page: Int, offset: String) {
        if (!hasMore || !bvUserRepository.isLogin) return
        if (expectedGen != generation) return

        setLoading(true, expectedGen)
        try {
            logger.fInfo { "Load dynamic page: $page, offset=$historyOffset" }

            val data = userRepository.getDynamicVideos(
                page = page,
                offset = offset,
                updateBaseline = updateBaseline.orEmpty(),
                preferApiType = Prefs.apiType
            )

            if (!coroutineContext.isActive || expectedGen != generation) return

            val filtered = dev.aaa1115910.bv.block.BlockManager.filterList(
                page = dev.aaa1115910.bv.block.BlockPage.Dynamics,
                list = data.videos
            ) { it.authorMid }

            withContext(Dispatchers.Main) {
                if (expectedGen != generation) return@withContext

                // append 前按 aid 去重（解决重复行）
                val distinct = ArrayList<DynamicVideo>(filtered.size)
                for (item in filtered) {
                    if (aidSet.add(item.aid)) distinct.add(item)
                }
                if (distinct.isNotEmpty()) dynamicList.addAll(distinct)

                currentPage = page
                historyOffset = data.historyOffset
                hasMore = data.hasMore

                // 不要用“空 baseline”覆盖已有 baseline（历史页 baseline 常为空）
                if (data.updateBaseline.isNotBlank()) updateBaseline = data.updateBaseline
            }

            logger.fInfo { "Loaded page=$page size=${data.videos.size}" }
        } catch (_: CancellationException) {
            logger.fInfo { "Load dynamic canceled" }
        } catch (e: Exception) {
            if (expectedGen != generation) return

            logger.fWarn { "Load dynamic failed: ${e.stackTraceToString()}" }

            when (e) {
                is AuthFailureException -> {
                    withContext(Dispatchers.Main) {
                        BVApp.context.getString(R.string.exception_auth_failure)
                            .toast(BVApp.context)
                    }
                    if (!BuildConfig.DEBUG) bvUserRepository.logout()
                }

                else -> {
                    withContext(Dispatchers.Main) {
                        "加载动态失败: ${e.localizedMessage}".toast(BVApp.context)
                    }
                }
            }
        } finally {
            setLoading(false, expectedGen)
        }
    }

    fun clearData() {
        // 强制全量重建时才用（刷新入口不再调用 clearData）
        generation++
        loadJob?.cancel()
        loadJob = null

        dynamicList.clear()
        aidSet.clear()
        currentPage = 0
        loading = false
        hasMore = true
        historyOffset = null
        updateBaseline = null
    }
}