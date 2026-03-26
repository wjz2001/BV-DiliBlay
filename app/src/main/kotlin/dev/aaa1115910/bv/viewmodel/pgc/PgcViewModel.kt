package dev.aaa1115910.bv.viewmodel.pgc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.CarouselData
import dev.aaa1115910.biliapi.entity.pgc.PgcFeedData
import dev.aaa1115910.biliapi.entity.pgc.PgcItem
import dev.aaa1115910.biliapi.entity.pgc.PgcType
import dev.aaa1115910.biliapi.repositories.PgcRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.util.addWithMainContext
import dev.aaa1115910.bv.util.addAllWithMainContext
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.common.LoadState
import dev.aaa1115910.bv.viewmodel.common.canAutoLoad
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PgcWarmUpOptions(
    val showCarouselErrorToast: Boolean = false
)

abstract class PgcViewModel(
    open val pgcRepository: PgcRepository,
    val pgcType: PgcType,
) : ViewModel() {
    private val logger = KotlinLogging.logger("PgcViewModel[$pgcType]")

    /**
     * 轮播图
     */
    var carouselItems by mutableStateOf<List<CarouselData.CarouselItem>>(emptyList())
        private set
    var feedItems by mutableStateOf<List<FeedListItem>>(emptyList())
        private set
    private val restSubItems = mutableListOf<PgcItem>()

    private var inFlightCount by mutableIntStateOf(0)
    val updating get() = inFlightCount > 0
    var hasNext by mutableStateOf(true)
    private var cursor = 0

    var initialLoadState by mutableStateOf(LoadState.Idle)
        private set
    @Volatile
    private var requestGeneration = 0L
    private var loadAllJob: Job? = null
    private val loadMutex = Mutex()
    private val maxFeedBlocks = 240

    private val feedUpdateMutex = Mutex()

    private var feedRequestSeq = 0L
    private var inFlightFeedToken: Long? = null

    private suspend fun beginLoading(expectedGeneration: Long) {
        withContext(Dispatchers.Main) {
            if (expectedGeneration != requestGeneration) return@withContext
            inFlightCount += 1
        }
    }

    private suspend fun endLoading(expectedGeneration: Long) {
        withContext(Dispatchers.Main) {
            if (expectedGeneration != requestGeneration) return@withContext
            inFlightCount = (inFlightCount - 1).coerceAtLeast(0)
        }
    }

    fun ensureLoaded() {
        if (!initialLoadState.canAutoLoad()) return
        initialLoadState = LoadState.Loading
        launchLoadAll(expectedGeneration = requestGeneration)
    }

    fun warmUp(options: PgcWarmUpOptions = PgcWarmUpOptions()) {
        if (!initialLoadState.canAutoLoad()) return
        initialLoadState = LoadState.Loading
        launchLoadAll(
            expectedGeneration = requestGeneration,
            warmUpOptions = options
        )
    }

    fun loadMore() {
        if (!hasNext) return
        val expectedGeneration = requestGeneration
        viewModelScope.launch(Dispatchers.IO) {
            updateFeed(expectedGeneration)
        }
    }

    fun reloadAll() {
        logger.fInfo { "Reload all $pgcType data" }
        requestGeneration++
        clearAll()
        initialLoadState = LoadState.Loading
        launchLoadAll(expectedGeneration = requestGeneration)
    }

    private fun launchLoadAll(
        expectedGeneration: Long,
        warmUpOptions: PgcWarmUpOptions = PgcWarmUpOptions(showCarouselErrorToast = true)
    ) {
        loadAllJob?.cancel()
        loadAllJob = viewModelScope.launch(Dispatchers.IO) {
            loadMutex.withLock {
                if (expectedGeneration != requestGeneration) return@withLock
                updateCarousel(
                    expectedGeneration = expectedGeneration,
                    showErrorToast = warmUpOptions.showCarouselErrorToast
                )
                updateFeed(expectedGeneration)
            }
        }
    }

    // 更新轮播图
    private suspend fun updateCarousel(
        expectedGeneration: Long,
        showErrorToast: Boolean
    ) {
        if (expectedGeneration != requestGeneration) return
        logger.fInfo { "Updating $pgcType carousel" }

        beginLoading(expectedGeneration)
        try {
            runCatching {
                val carouselData = pgcRepository.getCarousel(pgcType)
                if (expectedGeneration != requestGeneration) return@runCatching
                logger.fInfo { "Find $pgcType carousels, size: ${carouselData.items.size}" }
                withContext(Dispatchers.Main) {
                    if (expectedGeneration != requestGeneration) return@withContext
                    carouselItems = carouselData.items
                    if (initialLoadState == LoadState.Loading) {
                        initialLoadState = LoadState.Success
                    }
                }
            }.onFailure {
                if (expectedGeneration != requestGeneration) return@onFailure
                logger.fInfo { "Update $pgcType carousel failed: ${it.stackTraceToString()}" }
                withContext(Dispatchers.Main) {
                    if (expectedGeneration != requestGeneration) return@withContext
                    if (carouselItems.isEmpty() && feedItems.isEmpty()) {
                        initialLoadState = LoadState.Error
                        if (showErrorToast) {
                            "加载 $pgcType 轮播图失败: ${it.message}".toast(BVApp.context)
                        }
                    }
                }
            }
        } finally {
            endLoading(expectedGeneration)
        }
    }

    private suspend fun updateFeed(expectedGeneration: Long) {
        val requestCursor: Int
        val token: Long

        // 第一段锁：只做占位和快照
        feedUpdateMutex.withLock {
            if (expectedGeneration != requestGeneration) return
            if (!hasNext) return
            if (inFlightFeedToken != null) return

            requestCursor = cursor
            token = ++feedRequestSeq
            inFlightFeedToken = token
        }

        beginLoading(expectedGeneration)
        logger.fInfo { "Update $pgcType feed" }

        try {
            // 锁外网络请求
            val pgcFeedData = pgcRepository.getFeed(
                pgcType = pgcType,
                cursor = requestCursor
            )

            // 第二段锁：只做提交
            feedUpdateMutex.withLock {
                if (expectedGeneration != requestGeneration) return@withLock
                if (inFlightFeedToken != token) return@withLock
                if (cursor != requestCursor) return@withLock

                // 提交分页游标
                cursor = pgcFeedData.cursor
                hasNext = pgcFeedData.hasNext

                // 组装并提交 feed block（沿用你现在的拆块逻辑）
                val epList = ArrayList<PgcItem>(restSubItems.size + pgcFeedData.items.size)
                epList.addAll(restSubItems)
                epList.addAll(pgcFeedData.items)

                val newBlocks = ArrayList<FeedListItem>()
                restSubItems.clear()
                epList.chunked(5).forEach { chunk ->
                    if (chunk.size == 5) {
                        newBlocks.add(
                            FeedListItem(
                                type = FeedListType.Ep,
                                items = chunk
                            )
                        )
                    } else {
                        restSubItems.addAll(chunk)
                    }
                }

                pgcFeedData.ranks.forEach { rank ->
                    newBlocks.add(
                        FeedListItem(
                            type = FeedListType.Rank,
                            rank = rank
                        )
                    )
                }

                feedItems = (feedItems + newBlocks).takeLast(maxFeedBlocks)

                if (initialLoadState == LoadState.Loading) {
                    initialLoadState = LoadState.Success
                }
            }
        } catch (t: Throwable) {
            if (expectedGeneration != requestGeneration) return
            logger.fInfo { "Update $pgcType feeds failed: ${t.stackTraceToString()}" }

            withContext(Dispatchers.Main) {
                if (expectedGeneration != requestGeneration) return@withContext
                if (feedItems.isEmpty() && carouselItems.isEmpty()) {
                    initialLoadState = LoadState.Error
                }
            }
        } finally {
            feedUpdateMutex.withLock {
                if (inFlightFeedToken == token) {
                    inFlightFeedToken = null
                }
            }
            endLoading(expectedGeneration)
        }
    }

    private suspend fun updateFeedItems(
        data: PgcFeedData,
        expectedGeneration: Long
    ) {
        if (expectedGeneration != requestGeneration) return

        logger.fInfo { "update $pgcType feed items: [items: ${data.items.size}, ranks: ${data.ranks.size}]" }

        val epList = ArrayList<PgcItem>(restSubItems.size + data.items.size)
        epList.addAll(restSubItems)
        epList.addAll(data.items)

        val newBlocks = ArrayList<FeedListItem>()
        restSubItems.clear()
        epList.chunked(5).forEach { chunkedVCardList ->
            if (chunkedVCardList.size == 5) {
                newBlocks.add(
                    FeedListItem(
                        type = FeedListType.Ep,
                        items = chunkedVCardList
                    )
                )
            } else {
                restSubItems.addAll(chunkedVCardList)
            }
        }

        data.ranks.forEach { rank ->
            newBlocks.add(
                FeedListItem(
                    type = FeedListType.Rank,
                    rank = rank
                )
            )
        }

        withContext(Dispatchers.Main) {
            if (expectedGeneration != requestGeneration) return@withContext
            feedItems = (feedItems + newBlocks).takeLast(maxFeedBlocks)
        }
    }

    /**
     * 清理所有数据
     */
    fun clearAll() {
        logger.fInfo { "Clear all data" }
        carouselItems = emptyList()
        feedItems = emptyList()
        restSubItems.clear()
        cursor = 0
        hasNext = true
        inFlightCount = 0
        initialLoadState = LoadState.Idle
        inFlightFeedToken = null
    }

    data class FeedListItem(
        val type: FeedListType,
        val items: List<PgcItem>? = emptyList(),
        val rank: PgcFeedData.FeedRank? = null
    )

    enum class FeedListType {
        Ep, Rank
    }
}