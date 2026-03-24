package dev.aaa1115910.bv.viewmodel.user

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.http.entity.AuthFailureException
import dev.aaa1115910.biliapi.repositories.HistoryRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.repository.UserRepository
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import dev.aaa1115910.bv.util.formatHourMinSec
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.common.LoadState
import dev.aaa1115910.bv.viewmodel.common.canAutoLoad
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

@KoinViewModel
class HistoryViewModel(
    private val userRepository: UserRepository,
    private val historyRepository: HistoryRepository
) : ViewModel() {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    var histories = mutableStateListOf<VideoCardData>()
    var noMore by mutableStateOf(false)

    // 历史页搜索状态（Home/Personal 共用同一个 VM）
    var rawQuery by mutableStateOf("")
    var debouncedQuery by mutableStateOf("")
    var showSearchDialog by mutableStateOf(false)

    // 自动补页（仅搜索态使用）
    var autoLoadEnabled by mutableStateOf(false)
        private set
    var isAutoLoading by mutableStateOf(false)
        private set

    private var queryDebounceJob: Job? = null
    private var autoLoadJob: Job? = null

    private var cursor = 0L
    private var updating = false

    private var updateJob: Job? = null

    var initialLoadState by mutableStateOf(LoadState.Idle)
        private set

    var lastFailureWasAuth by mutableStateOf(false)
        private set

    @Volatile private var requestGeneration = 0L

    fun update(showErrorToast: Boolean = true) {
        if (updateJob?.isActive == true) return
        val expectedGeneration = requestGeneration
        updateJob = viewModelScope.launch(Dispatchers.IO) {
            updateHistories(
                expectedGeneration = expectedGeneration,
                showErrorToast = showErrorToast
            )
        }
    }

    fun ensureLoaded(showErrorToast: Boolean = true) {
        if (!initialLoadState.canAutoLoad()) return
        initialLoadState = LoadState.Loading
        update(showErrorToast = showErrorToast)
    }

    fun reloadAll(showErrorToast: Boolean = true) {
        val shouldResumeAutoLoad = debouncedQuery.trim().isNotBlank()

        requestGeneration++
        updateJob?.cancel()
        lastFailureWasAuth = false
        stopAutoLoad()
        histories.clear()
        cursor = 0
        noMore = false
        updating = false
        initialLoadState = LoadState.Loading
        update(showErrorToast = showErrorToast)

        if (shouldResumeAutoLoad) {
            startAutoLoad()
        }
    }

    fun clearData() {
        requestGeneration++
        updateJob?.cancel()
        lastFailureWasAuth = false
        stopAutoLoad()
        histories.clear()
        cursor = 0
        noMore = false
        updating = false
        initialLoadState = LoadState.Idle
    }

    private suspend fun updateHistories(
        expectedGeneration: Long,
        context: Context = BVApp.context,
        showErrorToast: Boolean = true
    ) {
        if (expectedGeneration != requestGeneration) return
        if (updating || noMore) return

        logger.fInfo { "Updating histories with params [cursor=$cursor, apiType=${Prefs.apiType}]" }
        updating = true
        try {
            lastFailureWasAuth = false
            val data = historyRepository.getHistories(
                cursor = cursor,
                preferApiType = Prefs.apiType
            )

            if (expectedGeneration != requestGeneration) return

            val appended = buildList {
                data.data.forEach { historyItem ->
                    if (
                        dev.aaa1115910.bv.block.BlockManager.isPageEnabled(dev.aaa1115910.bv.block.BlockPage.History) &&
                        dev.aaa1115910.bv.block.BlockManager.isBlocked(historyItem.mid)
                    ) {
                        return@forEach
                    }

                    add(
                        VideoCardData(
                            avid = historyItem.oid,
                            cid = historyItem.cid.takeIf { it > 0L },
                            epId = historyItem.epid?.takeIf { it > 0 },
                            jumpToSeason = (historyItem.epid ?: 0) > 0 || (historyItem.seasonId ?: 0) > 0,
                            title = historyItem.title,
                            cover = historyItem.cover,
                            upName = historyItem.author,
                            upMid = historyItem.mid,
                            timeString = if (historyItem.progress == -1) {
                                context.getString(R.string.play_time_finish)
                            } else {
                                context.getString(
                                    R.string.play_time_history,
                                    (historyItem.progress * 1000L).formatHourMinSec(),
                                    (historyItem.duration * 1000L).formatHourMinSec()
                                )
                            }
                        )
                    )
                }
            }

            if (expectedGeneration != requestGeneration) return

            withContext(Dispatchers.Main) {
                if (expectedGeneration != requestGeneration) return@withContext
                histories.addAll(appended)
            }

            cursor = data.cursor
            logger.fInfo { "Update history cursor: [cursor=$cursor]" }
            logger.fInfo { "Update histories success" }
            withContext(Dispatchers.Main) {
                if (expectedGeneration != requestGeneration) return@withContext
                if (initialLoadState == LoadState.Loading) {
                    lastFailureWasAuth = false
                    initialLoadState = LoadState.Success
                }
                if (cursor == 0L) {
                    noMore = true
                }
            }
            if (cursor == 0L) {
                logger.fInfo { "No more history" }
            }
        } catch (_: CancellationException) {
            logger.fInfo { "Update histories canceled" }
        } catch (t: Throwable) {
            logger.fWarn { "Update histories failed: ${t.stackTraceToString()}" }
            if (expectedGeneration != requestGeneration) return

            when (t) {
                is AuthFailureException -> {
                    withContext(Dispatchers.Main) {
                        if (expectedGeneration != requestGeneration) return@withContext
                        lastFailureWasAuth = true
                        if (initialLoadState == LoadState.Loading) {
                            initialLoadState = LoadState.Error
                        }
                        if (showErrorToast) {
                            BVApp.context.getString(R.string.exception_auth_failure).toast(BVApp.context)
                        }
                        if (autoLoadEnabled || isAutoLoading) {
                            stopAutoLoad()
                        }
                        logger.fInfo { "User auth failure" }
                        if (!BuildConfig.DEBUG) userRepository.logout()
                    }
                }
                else -> {
                    withContext(Dispatchers.Main) {
                        if (expectedGeneration != requestGeneration) return@withContext
                        lastFailureWasAuth = false
                        if (initialLoadState == LoadState.Loading) {
                            initialLoadState = LoadState.Error
                        }
                        if (showErrorToast) {
                            "加载历史记录失败: ${t.localizedMessage}".toast(BVApp.context)
                        }
                        if (autoLoadEnabled || isAutoLoading) {
                            stopAutoLoad()
                        }
                    }
                }
            }
        } finally {
            if (expectedGeneration == requestGeneration) {
                updating = false
            }
        }
    }

    fun openSearchDialog() {
        showSearchDialog = true
    }

    fun closeSearchDialog(apply: Boolean) {
        if (apply) onSearchAction()
        showSearchDialog = false
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
        queryDebounceJob?.cancel()
        queryDebounceJob = null
        debouncedQuery = rawQuery
        if (debouncedQuery.trim().isBlank()) {
            stopAutoLoad()
        } else {
            resumeAutoLoad()
        }
    }

    fun clearSearch() {
        queryDebounceJob?.cancel()
        queryDebounceJob = null
        stopAutoLoad()
        rawQuery = ""
        debouncedQuery = ""
        showSearchDialog = false
    }

    fun startAutoLoad() {
        if (debouncedQuery.trim().isBlank()) return
        autoLoadEnabled = true
        if (autoLoadJob?.isActive == true) return

        autoLoadJob = viewModelScope.launch(Dispatchers.Default) {
            isAutoLoading = true
            while (isActive && !noMore) {
                while (isActive && !autoLoadEnabled) {
                    delay(100)
                }
                if (!isActive || noMore) break

                // 自动补页统一走 update()，不要绕过 updateJob 直接打 updateHistories()
                update(showErrorToast = false)
                updateJob?.join()

                if (!isActive || noMore || !autoLoadEnabled) break
                delay(Random.nextLong(500L, 2000L))
            }
            isAutoLoading = false
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

    override fun onCleared() {
        updateJob?.cancel()
        queryDebounceJob?.cancel()
        autoLoadJob?.cancel()
        super.onCleared()
    }
}