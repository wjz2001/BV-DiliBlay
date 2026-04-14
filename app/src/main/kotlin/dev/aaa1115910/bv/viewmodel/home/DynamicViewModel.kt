package dev.aaa1115910.bv.viewmodel.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import dev.aaa1115910.bv.viewmodel.common.LoadState
import dev.aaa1115910.bv.viewmodel.common.canAutoLoad
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    companion object {
        // 复用 SmallVideoCard 的占位数据主键
        const val REFRESH_PLACEHOLDER_AID = -1L

        // 后续页拉取：单页超时 + 总超时，避免占位长时间不消失
        private const val CATCH_UP_PAGE_TIMEOUT_MS = 8_000L
        private const val CATCH_UP_TOTAL_TIMEOUT_MS = 45_000L
    }

    // token 递增：UI 通过“token 变大”触发滚顶，避免 SharedFlow 丢事件
    var scrollToTopToken by mutableLongStateOf(0L)
        private set

    fun requestScrollToTop() {
        scrollToTopToken += 1
    }

    // RefreshNew 进行中：屏蔽触底 loadMore
    private var isRefreshingNew = false

    // 占位卡位置（插在“最新 1 页新增”之后）
    private var refreshPlaceholderIndex = -1

    var initialLoadState by mutableStateOf(LoadState.Idle)
        private set

    var lastFailureWasAuth by mutableStateOf(false)
        private set

    private val maxItems = 600

    private fun buildRefreshPlaceholder(): DynamicVideo = DynamicVideo(
        aid = REFRESH_PLACEHOLDER_AID,
        bvid = null,
        cid = 0,
        epid = null,
        seasonId = null,
        title = "正在同步最新动态……",
        cover = "",
        author = "",
        authorMid = 0L,
        duration = 0,
        play = -1,
        danmaku = -1,
        pubTime = null
    )

    // 仅在主线程调用
    private fun removeRefreshPlaceholderLocked(): Int {
        val previousSize = dynamicList.size
        // removeAll 会遍历一遍列表，把所有满足条件的都删掉，内部处理了指针
        dynamicList.removeAll { it.aid == REFRESH_PLACEHOLDER_AID }
        val removed = previousSize - dynamicList.size
        refreshPlaceholderIndex = -1
        return removed
    }

    private suspend fun showRefreshPlaceholder(expectedGen: Long, insertIndex: Int) {
        withContext(Dispatchers.Main) {
            if (expectedGen != generation) return@withContext
            val removed = removeRefreshPlaceholderLocked()

            val safeIndex = insertIndex.coerceIn(0, dynamicList.size)
            dynamicList.add(safeIndex, buildRefreshPlaceholder())
            refreshPlaceholderIndex = safeIndex

            logger.fInfo {
                "Show refresh placeholder: gen=$expectedGen, index=$safeIndex, removedBefore=$removed"
            }
        }
    }

    private suspend fun hideRefreshPlaceholder(expectedGen: Long) {
        withContext(Dispatchers.Main) {
            if (expectedGen != generation) return@withContext
            val removed = removeRefreshPlaceholderLocked()
            logger.fInfo { "Hide refresh placeholder: gen=$expectedGen, removed=$removed" }
        }
    }

    suspend fun loadMore(
        mode: LoadMode = LoadMode.More,
        showNoUpdateToast: Boolean = false,
        showErrorToast: Boolean = true
    ) {
        if (!bvUserRepository.isLogin) {
            withContext(Dispatchers.Main) {
                if (initialLoadState == LoadState.Loading) {
                    initialLoadState = LoadState.Idle
                }
                loading = false
            }
            return
        }

        when (mode) {
            LoadMode.RefreshNew -> refreshNewInternal(
                showNoUpdateToast = showNoUpdateToast,
                showErrorToast = showErrorToast
            )

            LoadMode.More -> loadMoreInternal(
                showErrorToast = showErrorToast
            )
        }
    }

    private suspend fun setLoading(value: Boolean, expectedGen: Long) {
        withContext(Dispatchers.Main) {
            if (expectedGen != generation) return@withContext
            loading = value
        }
    }

    private suspend fun refreshNewInternal(
        showNoUpdateToast: Boolean,
        showErrorToast: Boolean
    ) {
        refreshMutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastRefreshMs < 1500) {
                withContext(Dispatchers.Main) {
                    if (initialLoadState == LoadState.Loading) {
                        initialLoadState = if (dynamicList.isNotEmpty()) {
                            LoadState.Success
                        } else {
                            LoadState.Idle
                        }
                    }
                }
                return
            }
            lastRefreshMs = now

            // 取消可能在进行的“触底翻页”请求
            loadJob?.cancel()
            loadJob = null

            // 让旧请求结果失效（即使网络层取消不及时）
            generation++
            val expectedGen = generation

            logger.fInfo { "RefreshNew start: gen=$expectedGen" }

            isRefreshingNew = true
            val hadItemsBefore = withContext(Dispatchers.Main) { dynamicList.isNotEmpty() }
            var totalAdded = 0
            // 只有首次建链时，刷新结果才允许覆盖 currentPage/historyOffset/hasMore
            val initializingPagingState = currentPage == 0 && historyOffset == null

            // 清理可能残留的占位
            withContext(Dispatchers.Main) {
                if (expectedGen != generation) return@withContext
                val removed = removeRefreshPlaceholderLocked()
                if (removed > 0) {
                    logger.fWarn { "RefreshNew pre-clean removed stale placeholders: $removed" }
                }
            }

            setLoading(true, expectedGen)
            try {
                lastFailureWasAuth = false
                val baselineSeed = updateBaseline.orEmpty()

                // 只拉最新 1 页并立即落地
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

                val firstPageNewItems = ArrayList<DynamicVideo>(firstFiltered.size)
                var firstPageOverlap = false

                withContext(Dispatchers.Main) {
                    if (expectedGen != generation) return@withContext

                    for (item in firstFiltered) {
                        if (aidSet.contains(item.aid)) firstPageOverlap = true
                        if (aidSet.add(item.aid)) firstPageNewItems.add(item)
                    }

                    if (firstPageNewItems.isNotEmpty()) {
                        dynamicList.addAll(0, firstPageNewItems)
                        trimDynamicDataLocked()
                        totalAdded += firstPageNewItems.size

                        // 第一页落地后立即滚顶
                        scrollToTopToken += 1
                    }

                    // 只在非空时更新 baseline
                    if (firstData.updateBaseline.isNotBlank()) {
                        updateBaseline = firstData.updateBaseline
                    }

                    // 只有首次初始化分页链时，才允许第一页刷新覆盖分页前沿，已经存在旧前沿时，刷新只能 prepend 新内容，不能把旧前沿回退到第一页。
                    if (initializingPagingState) {
                        currentPage = 1
                        historyOffset = firstData.historyOffset
                        hasMore = firstData.hasMore
                    }
                }

                // 第一页完成后先关全局 loading，后续用占位卡提示
                setLoading(false, expectedGen)

                withContext(Dispatchers.Main) {
                    if (expectedGen != generation) return@withContext
                    if (initialLoadState == LoadState.Loading) {
                        lastFailureWasAuth = false
                        initialLoadState = LoadState.Success
                    }
                }

                // 第一页无新增且命中重叠，可直接判定无更新
                if (firstPageNewItems.isEmpty() && firstPageOverlap) {
                    if (showNoUpdateToast && hadItemsBefore) {
                        withContext(Dispatchers.Main) {
                            if (expectedGen != generation) return@withContext
                            "没有更新的了".toast(BVApp.context)
                        }
                    }
                    logger.fInfo { "RefreshNew end early(no update): gen=$expectedGen" }
                    return
                }

                // 若还有后续，显示占位卡并在后台拉取，最终一次性插入
                val baselineForCatchUp = updateBaseline.orEmpty()
                var cursorOffset = firstData.historyOffset
                var cursorHasMore = firstData.hasMore
                var page = 2
                var lastConsumedPage = 1

                val pendingItems = ArrayList<DynamicVideo>()
                val pendingAidSet = HashSet<Long>(512)
                var reachNoMore = false
                var catchUpTimedOut = false

                if (firstData.hasMore && cursorOffset.isNotBlank()) {
                    // 占位卡放在“第一页新增内容”之后
                    showRefreshPlaceholder(
                        expectedGen = expectedGen,
                        insertIndex = firstPageNewItems.size
                    )

                    logger.fInfo {
                        "Refresh catch-up start: gen=$expectedGen, startPage=$page, offset=$cursorOffset"
                    }

                    try {
                        withTimeout(CATCH_UP_TOTAL_TIMEOUT_MS) {
                            while (coroutineContext.isActive && expectedGen == generation) {
                                if (cursorOffset.isBlank()) {
                                    reachNoMore = true
                                    break
                                }

                                val data = withTimeout(CATCH_UP_PAGE_TIMEOUT_MS) {
                                    userRepository.getDynamicVideos(
                                        page = page,
                                        offset = cursorOffset,
                                        updateBaseline = baselineForCatchUp,
                                        preferApiType = Prefs.apiType
                                    )
                                }
                                if (!coroutineContext.isActive || expectedGen != generation) return@withTimeout

                                lastConsumedPage = page

                                val filtered = dev.aaa1115910.bv.block.BlockManager.filterList(
                                    page = dev.aaa1115910.bv.block.BlockPage.Dynamics,
                                    list = data.videos
                                ) { it.authorMid }

                                var pageOverlap = false
                                var pageAdded = 0

                                for (item in filtered) {
                                    val alreadyInList = aidSet.contains(item.aid)
                                    if (alreadyInList) {
                                        pageOverlap = true
                                        continue
                                    }
                                    if (pendingAidSet.add(item.aid)) {
                                        pendingItems.add(item)
                                        pageAdded++
                                    }
                                }

                                cursorOffset = data.historyOffset
                                cursorHasMore = data.hasMore

                                // 主条件：hasMore=false/offset 为空
                                if (!data.hasMore || cursorOffset.isBlank()) {
                                    reachNoMore = true
                                    break
                                }

                                // 提前结束条件：本页无新增 && 命中重叠
                                if (pageAdded == 0 && pageOverlap) break

                                page++
                            }
                        }
                    } catch (_: TimeoutCancellationException) {
                        catchUpTimedOut = true
                        logger.fWarn {
                            "Refresh catch-up timeout: gen=$expectedGen, page=$page, offset=$cursorOffset"
                        }
                    }

                    withContext(Dispatchers.Main) {
                        if (expectedGen != generation) return@withContext

                        // 先移除占位，再一次性插入后续新数据（避免中途重排抖动）
                        removeRefreshPlaceholderLocked()

                        if (pendingItems.isNotEmpty()) {
                            val committed = ArrayList<DynamicVideo>(pendingItems.size)
                            for (item in pendingItems) {
                                if (aidSet.add(item.aid)) committed.add(item)
                            }

                            if (committed.isNotEmpty()) {
                                val insertAt = firstPageNewItems.size.coerceIn(0, dynamicList.size)
                                dynamicList.addAll(insertAt, committed)
                                trimDynamicDataLocked()
                                totalAdded += committed.size
                            }
                        }

                        // 非首次刷新时，旧分页前沿必须保留，只有首次建链时，才用 catch-up 的最终位置初始化正式分页状态。
                        if (initializingPagingState) {
                            currentPage = lastConsumedPage
                            historyOffset = cursorOffset
                            hasMore = if (reachNoMore) false else cursorHasMore
                        } else if (reachNoMore) {
                            hasMore = false
                        }
                    }

                    if (catchUpTimedOut && showNoUpdateToast && hadItemsBefore) {
                        withContext(Dispatchers.Main) {
                            if (expectedGen != generation) return@withContext
                            "更新超时，已展示已获取内容".toast(BVApp.context)
                        }
                    }
                }

                if (showNoUpdateToast && hadItemsBefore) {
                    withContext(Dispatchers.Main) {
                        if (expectedGen != generation) return@withContext
                        if (totalAdded > 0) {
                            "更新了 $totalAdded 条".toast(BVApp.context)
                        } else {
                            "没有更新的了".toast(BVApp.context)
                        }
                    }
                }

                logger.fInfo { "RefreshNew end: gen=$expectedGen, totalAdded=$totalAdded" }
            } catch (_: CancellationException) {
                logger.fInfo { "Refresh new canceled: gen=$expectedGen" }
            } catch (e: AuthFailureException) {
                if (expectedGen != generation) return
                logger.fWarn { "Refresh new auth failure: ${e.stackTraceToString()}" }
                withContext(Dispatchers.Main) {
                    if (expectedGen != generation) return@withContext
                    lastFailureWasAuth = true
                    if (initialLoadState == LoadState.Loading) {
                        initialLoadState = LoadState.Error
                    }
                    if (showErrorToast) {
                        BVApp.context.getString(R.string.exception_auth_failure)
                            .toast(BVApp.context)
                    }
                    if (!BuildConfig.DEBUG) bvUserRepository.logout()
                }
            } catch (e: Exception) {
                if (expectedGen != generation) return
                logger.fWarn { "Refresh new failed: ${e.stackTraceToString()}" }
                withContext(Dispatchers.Main) {
                    if (expectedGen != generation) return@withContext
                    lastFailureWasAuth = false
                    if (initialLoadState == LoadState.Loading) {
                        initialLoadState = LoadState.Error
                    }
                    if (showErrorToast) {
                        "刷新动态失败: ${e.localizedMessage}".toast(BVApp.context)
                    }
                }
            } finally {
                // 即使协程处于取消态，也必须完成占位清理和状态收口
                withContext(NonCancellable) {
                    hideRefreshPlaceholder(expectedGen)
                    isRefreshingNew = false
                    setLoading(false, expectedGen)
                }
            }
        }
    }

    private fun loadMoreInternal(showErrorToast: Boolean) {
        if (!hasMore) return
        if (isRefreshingNew) return

        // 有在途请求就不再发新请求（避免并发 append / 重复 aid）
        if (loadJob?.isActive == true) return

        val expectedGen = generation
        val nextPage = currentPage + 1
        val offset = historyOffset.orEmpty()

        loadJob = viewModelScope.launch(Dispatchers.IO) {
            loadHistoryPage(expectedGen, nextPage, offset, showErrorToast)
        }.also { job ->
            job.invokeOnCompletion {
                if (loadJob === job) loadJob = null
            }
        }
    }

    private suspend fun loadHistoryPage(
        expectedGen: Long,
        page: Int,
        offset: String,
        showErrorToast: Boolean
    ) {
        if (!hasMore || !bvUserRepository.isLogin) return
        if (expectedGen != generation) return

        setLoading(true, expectedGen)
        try {
            withContext(Dispatchers.Main) {
                if (expectedGen != generation) return@withContext
                lastFailureWasAuth = false
            }

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

            withContext(Dispatchers.Main) {
                if (expectedGen != generation) return@withContext
                if (initialLoadState == LoadState.Loading) {
                    lastFailureWasAuth = false
                    initialLoadState = LoadState.Success
                }
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
                        if (expectedGen != generation) return@withContext
                        lastFailureWasAuth = true
                        if (initialLoadState == LoadState.Loading) {
                            initialLoadState = LoadState.Error
                        }
                        if (showErrorToast) {
                            BVApp.context.getString(R.string.exception_auth_failure)
                                .toast(BVApp.context)
                        }
                        if (!BuildConfig.DEBUG) bvUserRepository.logout()
                    }
                }

                else -> {
                    withContext(Dispatchers.Main) {
                        if (expectedGen != generation) return@withContext
                        lastFailureWasAuth = false
                        if (initialLoadState == LoadState.Loading) {
                            initialLoadState = LoadState.Error
                        }
                        if (showErrorToast) {
                            "加载动态失败: ${e.localizedMessage}".toast(BVApp.context)
                        }
                    }
                }
            }
        } finally {
            setLoading(false, expectedGen)
        }
    }

    fun ensureLoaded(showErrorToast: Boolean = true) {
        if (!initialLoadState.canAutoLoad()) return
        initialLoadState = LoadState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            loadMore(
                mode = LoadMode.More,
                showErrorToast = showErrorToast
            )
        }
    }

    fun reloadAll(
        showNoUpdateToast: Boolean = true,
        showErrorToast: Boolean = true
    ) {
        // 防抖必须发生在状态切到 Loading 之前，
        // 否则被拦下的那次刷新会把状态卡在 Loading。
        val now = System.currentTimeMillis()
        if (now - lastRefreshMs < 1500) return

        initialLoadState = LoadState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            loadMore(
                mode = LoadMode.RefreshNew,
                showNoUpdateToast = showNoUpdateToast,
                showErrorToast = showErrorToast
            )
        }
    }

    fun clearData() {
        generation++
        loadJob?.cancel()
        loadJob = null
        lastFailureWasAuth = false
        dynamicList.clear()
        aidSet.clear()
        currentPage = 0
        loading = false
        hasMore = true
        historyOffset = null
        updateBaseline = null
        isRefreshingNew = false
        refreshPlaceholderIndex = -1
        initialLoadState = LoadState.Idle
    }

    private fun trimDynamicDataLocked() {
        if (dynamicList.size <= maxItems) return
        val overflow = dynamicList.size - maxItems
        repeat(overflow) {
            if (dynamicList.isNotEmpty()) {
                dynamicList.removeAt(dynamicList.lastIndex)
            }
        }
        aidSet.clear()
        dynamicList.forEach { aidSet.add(it.aid) }
    }
}
