package dev.aaa1115910.bv.viewmodel.user

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.user.SpaceVideoPage
import dev.aaa1115910.biliapi.repositories.UserRepository
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.formatHourMinSec
import dev.aaa1115910.bv.util.toWanString
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel
import kotlin.random.Random

@KoinViewModel
class UpInfoViewModel(
    private val userRepository: UserRepository
) : ViewModel() {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    var upName by mutableStateOf("")
    var upMid by mutableLongStateOf(0L)
    var spaceVideos = mutableStateListOf<VideoCardData>()

    // Web: page.count; App: count。取不到时为 null
    var totalCount by mutableStateOf<Int?>(null)
        private set

    // 输入：raw 立即更新；debounced 900ms 后才更新（用于真正过滤）
    var rawQuery by mutableStateOf("")
        private set
    var debouncedQuery by mutableStateOf("")
        private set

    // 自动加载控制：仅搜索态开启
    var autoLoadEnabled by mutableStateOf(false)
        private set

    // 顶部展示用
    var isAutoLoading by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set

    private var page = SpaceVideoPage()
    @Volatile private var requestGeneration = 0L
    val noMore get() = !page.hasNext

    // 防止并发请求、并做按 avid 去重
    private val requestMutex = Mutex()
    private val loadedAvids = mutableSetOf<Long>()

    private var queryDebounceJob: Job? = null
    private var autoLoadJob: Job? = null

    // 进入页面时冻结一次，避免 Web/App 混用导致分页游标混乱
    private var preferApiType: ApiType = Prefs.apiType

    fun reset(mid: Long, name: String) {
        requestGeneration++

        queryDebounceJob?.cancel()
        autoLoadJob?.cancel()
        queryDebounceJob = null
        autoLoadJob = null

        upMid = mid
        upName = name

        preferApiType = Prefs.apiType

        rawQuery = ""
        debouncedQuery = ""
        autoLoadEnabled = false

        isAutoLoading = false
        loading = false
        totalCount = null

        page = SpaceVideoPage()
        spaceVideos.clear()
        loadedAvids.clear()
    }

    fun onQueryChange(newText: String) {
        rawQuery = newText
        pauseAutoLoad()

        queryDebounceJob?.cancel()
        queryDebounceJob = viewModelScope.launch {
            delay(900)
            debouncedQuery = rawQuery
            if (debouncedQuery.trim().isBlank()) {
                stopAutoLoad()
            } else {
                resumeAutoLoad()
            }
        }
    }

    fun onSearchAction() {
        // 用户按“搜索”视为输入完成：立即应用过滤并根据关键词切换加载模式
        queryDebounceJob?.cancel()
        queryDebounceJob = null
        debouncedQuery = rawQuery
        if (debouncedQuery.trim().isBlank()) {
            stopAutoLoad()
        } else {
            resumeAutoLoad()
        }
    }

    fun stopAutoLoad() {
        autoLoadEnabled = false
        autoLoadJob?.cancel()
        autoLoadJob = null
        isAutoLoading = false
    }

    private fun pauseAutoLoad() {
        autoLoadEnabled = false
    }

    private fun resumeAutoLoad() {
        autoLoadEnabled = true
        startAutoLoad()
    }

    fun startAutoLoad() {
        if (!autoLoadEnabled) return
        if (autoLoadJob?.isActive == true) return

        val expectedGeneration = requestGeneration
        autoLoadJob = viewModelScope.launch(Dispatchers.Default) {
            isAutoLoading = true

            var backoffMs = 0L
            while (isActive && !noMore && expectedGeneration == requestGeneration) {
                while (isActive && !autoLoadEnabled) {
                    delay(100)
                }
                if (!isActive || noMore || expectedGeneration != requestGeneration) break

                val ok = loadNextPage(expectedGeneration)
                if (noMore || expectedGeneration != requestGeneration) break

                if (ok) {
                    backoffMs = 0L
                    delay(Random.nextLong(100L, 200L))
                } else {
                    backoffMs = if (backoffMs == 0L) 5_000L else (backoffMs * 2).coerceAtMost(60_000L)
                    delay(backoffMs)
                }
            }

            if (expectedGeneration == requestGeneration) {
                isAutoLoading = false
            }
        }
    }

    fun update() {
        val expectedGeneration = requestGeneration
        viewModelScope.launch(Dispatchers.Default) {
            loadNextPage(expectedGeneration)
        }
    }

    suspend fun loadNextPage(
        expectedGeneration: Long = requestGeneration
    ): Boolean = requestMutex.withLock {
        if (expectedGeneration != requestGeneration) return false
        if (loading || noMore) return false

        loading = true
        return try {
            logger.fInfo { "Updating up [mid=$upMid] space videos from page $page (api=$preferApiType)" }

            val spaceVideoData = withContext(Dispatchers.IO) {
                userRepository.getSpaceVideos(
                    mid = upMid,
                    page = page,
                    preferApiType = preferApiType
                )
            }

            if (expectedGeneration != requestGeneration) return false

            spaceVideoData.totalCount?.takeIf { it > 0 }?.let { totalCount = it }

            val newCards = spaceVideoData.videos.map { spaceVideoItem ->
                VideoCardData(
                    avid = spaceVideoItem.aid,
                    title = spaceVideoItem.title,
                    // TODO 这里在改造 app 端接口时，没找到在空间内显示为合集样式封面的UP,没法进一步测试接口
                    cover = spaceVideoItem.cover,
                    upName = spaceVideoItem.author,
                    upMid = upMid,
                    playString = spaceVideoItem.play.takeIf { it != -1 }.toWanString(),
                    danmakuString = spaceVideoItem.danmaku.takeIf { it != -1 }.toWanString(),
                    timeString = (spaceVideoItem.duration * 1000L).formatHourMinSec(),
                    pubTime = spaceVideoItem.pubTime
                )
            }

            withContext(Dispatchers.Main) {
                if (expectedGeneration != requestGeneration) return@withContext
                newCards.forEach { card ->
                    if (loadedAvids.add(card.avid)) {
                        spaceVideos.add(card)
                    }
                }
            }

            if (expectedGeneration != requestGeneration) return false
            page = spaceVideoData.page
            true
        } catch (t: Throwable) {
            logger.fInfo { "Update up space videos failed: ${t.stackTraceToString()}" }
            false
        } finally {
            if (expectedGeneration == requestGeneration) {
                loading = false
            }
        }
    }
}