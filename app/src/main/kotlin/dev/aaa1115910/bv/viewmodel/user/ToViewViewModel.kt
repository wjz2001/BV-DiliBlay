package dev.aaa1115910.bv.viewmodel.user

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.user.ToViewItem
import dev.aaa1115910.biliapi.http.entity.AuthFailureException
import dev.aaa1115910.biliapi.repositories.ToViewRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.repository.UserRepository
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import dev.aaa1115910.bv.util.formatHourMinSec
import dev.aaa1115910.bv.util.swapListSkipEqual
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.common.LoadState
import dev.aaa1115910.bv.viewmodel.common.LoadState.Idle
import dev.aaa1115910.bv.viewmodel.common.canAutoLoad
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class ToViewViewModel(
    private val userRepository: UserRepository,
    private val toViewRepository: ToViewRepository
) : ViewModel() {
    private val _uiEffect = MutableSharedFlow<UiEffect>()
    val uiEvent = _uiEffect.asSharedFlow()

    companion object {
        private val logger = KotlinLogging.logger { }

        private const val SNAPSHOT_REFRESH_PAGE_TIMEOUT_MS = 8_000L
        private const val SNAPSHOT_REFRESH_TOTAL_TIMEOUT_MS = 45_000L
        private const val SNAPSHOT_REFRESH_MAX_PAGES = 10
    }

    private data class FullSnapshotResult(
        val cards: List<VideoCardData>,
        val cursor: Long,
        val noMore: Boolean
    )

    var histories = mutableStateListOf<VideoCardData>()
    var noMore by mutableStateOf(false)

    private var cursor = 0L
    private var updating = false

    private var updateJob: Job? = null

    var initialLoadState by mutableStateOf(Idle)
        private set

    var activationRefreshState by mutableStateOf(Idle)
        private set

    var lastFailureWasAuth by mutableStateOf(false)
        private set

    val retryLoadState: LoadState
        get() = when (activationRefreshState) {
            LoadState.Loading,
            LoadState.Error -> activationRefreshState

            Idle,
            LoadState.Success -> initialLoadState
        }

    @Volatile
    private var requestGeneration = 0L
    private val maxItems = 200

    private val snapshotRefreshMinIntervalMs = 1_200L
    private var lastSnapshotRefreshMs = 0L

    private var loadedAccountSessionKey = currentAccountSessionKey()

    private fun currentAccountSessionKey(): String {
        return if (!userRepository.isLogin) {
            "logout"
        } else {
            "${userRepository.uid}:${userRepository.uidCkMd5}:${userRepository.sessData}"
        }
    }

    private fun resetDataState(
        targetAccountSessionKey: String = currentAccountSessionKey()
    ) {
        requestGeneration++
        updateJob?.cancel()
        histories.clear()
        cursor = 0L
        noMore = false
        updating = false
        initialLoadState = Idle
        activationRefreshState = Idle
        lastFailureWasAuth = false
        lastSnapshotRefreshMs = 0L
        loadedAccountSessionKey = targetAccountSessionKey
    }

    private fun ensureAccountStateFresh() {
        val currentAccountSessionKey = currentAccountSessionKey()
        if (loadedAccountSessionKey == currentAccountSessionKey) return

        logger.fInfo { "Reset toview state because account session changed" }
        resetDataState(currentAccountSessionKey)
    }

    fun update(
        showErrorToast: Boolean = true,
        allowAuthFailureLogout: Boolean = true
    ) {
        if (updateJob?.isActive == true) return

        val expectedGeneration = requestGeneration
        updateJob = viewModelScope.launch(Dispatchers.IO) {
            updateToView(
                expectedGeneration = expectedGeneration,
                showErrorToast = showErrorToast,
                allowAuthFailureLogout = allowAuthFailureLogout
            )
        }
    }

    fun ensureLoaded(showErrorToast: Boolean = true) {
        ensureAccountStateFresh()
        if (!initialLoadState.canAutoLoad()) return
        initialLoadState = LoadState.Loading
        update(
            showErrorToast = showErrorToast,
            allowAuthFailureLogout = true
        )
    }

    fun warmUp() {
        ensureAccountStateFresh()
        if (!initialLoadState.canAutoLoad()) return
        initialLoadState = LoadState.Loading
        update(
            showErrorToast = false,
            allowAuthFailureLogout = false
        )
    }

    fun reloadAll() {
        resetDataState()
        initialLoadState = LoadState.Loading
        update()
    }

    fun addToView(aid: Long, bvid: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                toViewRepository.addToView(
                    aid = aid,
                    bvid = bvid,
                    preferApiType = Prefs.apiType
                )
                _uiEffect.emit(UiEffect.ShowToast("添加到稍后再看"))
            } catch (_: CancellationException) {
                logger.fInfo { "Add toview canceled" }
            } catch (t: Throwable) {
                logger.fWarn { "Add toview failed: ${t.stackTraceToString()}" }
                when (t) {
                    is AuthFailureException -> {
                        _uiEffect.emit(
                            UiEffect.ShowToast(BVApp.context.getString(R.string.exception_auth_failure))
                        )
                        if (!BuildConfig.DEBUG) userRepository.logout()
                    }

                    else -> {
                        _uiEffect.emit(UiEffect.ShowToast("添加到稍后再看失败"))
                    }
                }
            }
        }
    }

    fun delToView(aid: Long, viewed: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                toViewRepository.delToView(
                    viewed = viewed,
                    aid = aid,
                    preferApiType = Prefs.apiType
                )
                withContext(Dispatchers.Main) {
                    histories.removeAll { it.avid == aid }
                }
                _uiEffect.emit(UiEffect.ShowToast("删除稍后再看"))
            } catch (_: CancellationException) {
                logger.fInfo { "Delete toview canceled" }
            } catch (t: Throwable) {
                logger.fWarn { "Delete toview failed: ${t.stackTraceToString()}" }
                when (t) {
                    is AuthFailureException -> {
                        _uiEffect.emit(
                            UiEffect.ShowToast(BVApp.context.getString(R.string.exception_auth_failure))
                        )
                        if (!BuildConfig.DEBUG) userRepository.logout()
                    }

                    else -> {
                        _uiEffect.emit(UiEffect.ShowToast("删除稍后再看失败"))
                    }
                }
            }
        }
    }

    fun clearData() {
        resetDataState()
    }

    fun refreshSnapshotIncrementally(showErrorToast: Boolean = true) {
        ensureAccountStateFresh()

        val now = SystemClock.uptimeMillis()
        if (now - lastSnapshotRefreshMs < snapshotRefreshMinIntervalMs) return
        lastSnapshotRefreshMs = now

        requestGeneration++
        updateJob?.cancel()
        updating = false

        activationRefreshState = LoadState.Loading
        if (histories.isEmpty()) {
            initialLoadState = LoadState.Loading
        }

        val expectedGeneration = requestGeneration
        updateJob = viewModelScope.launch(Dispatchers.IO) {
            refreshToViewSnapshot(
                expectedGeneration = expectedGeneration,
                showErrorToast = showErrorToast
            )
        }
    }

    private fun buildToViewCards(
        items: List<ToViewItem>,
        context: Context = BVApp.context
    ): List<VideoCardData> {
        return items.mapNotNull { toViewItem ->
            if (
                dev.aaa1115910.bv.block.BlockManager.isPageEnabled(dev.aaa1115910.bv.block.BlockPage.ToView) &&
                dev.aaa1115910.bv.block.BlockManager.isBlocked(toViewItem.mid)
            ) {
                null
            } else {
                VideoCardData(
                    avid = toViewItem.oid,
                    cid = toViewItem.cid.takeIf { it > 0L },
                    epId = toViewItem.epid?.takeIf { it > 0 },
                    jumpToSeason = (toViewItem.epid ?: 0) > 0 || (toViewItem.seasonId ?: 0) > 0,
                    title = toViewItem.title,
                    cover = toViewItem.cover,
                    upName = toViewItem.author,
                    upMid = toViewItem.mid,
                    timeString = if (toViewItem.progress == -1) {
                        context.getString(R.string.play_time_finish)
                    } else {
                        context.getString(
                            R.string.play_time_history,
                            (toViewItem.progress * 1000L).formatHourMinSec(),
                            (toViewItem.duration * 1000L).formatHourMinSec()
                        )
                    }
                )
            }
        }
    }

    private fun throwIfStale(expectedGeneration: Long) {
        if (expectedGeneration != requestGeneration) {
            throw CancellationException(
                "Stale ToView request: expected=$expectedGeneration actual=$requestGeneration"
            )
        }
    }

    private suspend fun rebuildFullSnapshot(
        expectedGeneration: Long,
        context: Context = BVApp.context
    ): FullSnapshotResult = withTimeout(SNAPSHOT_REFRESH_TOTAL_TIMEOUT_MS) {
        val visibleCards = ArrayList<VideoCardData>(maxItems)
        val visitedCursors = HashSet<Long>()
        val seenAids = HashSet<Long>(maxItems * 2)
        var nextCursor = 0L
        var page = 1

        do {
            if (!visitedCursors.add(nextCursor)) {
                throw IllegalStateException(
                    "Repeated toview cursor during snapshot refresh: $nextCursor"
                )
            }
            if (page > SNAPSHOT_REFRESH_MAX_PAGES) {
                throw IllegalStateException(
                    "ToView snapshot refresh exceeded $SNAPSHOT_REFRESH_MAX_PAGES pages"
                )
            }

            val requestCursor = nextCursor
            val data = withTimeout(SNAPSHOT_REFRESH_PAGE_TIMEOUT_MS) {
                toViewRepository.getToView(
                    cursor = requestCursor,
                    preferApiType = Prefs.apiType
                )
            }
            throwIfStale(expectedGeneration)

            val pageCards = buildToViewCards(data.data, context)
            for (card in pageCards) {
                if (!seenAids.add(card.avid)) continue
                if (visibleCards.size >= maxItems) break
                visibleCards.add(card)
            }

            val incomingCursor = data.cursor
            logger.fInfo {
                "Refresh toview full snapshot page=$page, keptSize=${visibleCards.size}, cursor=$incomingCursor"
            }

            if (incomingCursor == requestCursor && incomingCursor != 0L) {
                throw IllegalStateException(
                    "ToView cursor did not advance during snapshot refresh: $incomingCursor"
                )
            }

            nextCursor = incomingCursor
            page++
        } while (nextCursor != 0L)

        FullSnapshotResult(
            cards = visibleCards,
            cursor = 0L,
            noMore = true
        )
    }

    private suspend fun updateToView(
        expectedGeneration: Long,
        showErrorToast: Boolean,
        allowAuthFailureLogout: Boolean,
        context: Context = BVApp.context
    ) {
        if (expectedGeneration != requestGeneration) return
        if (updating || noMore) return

        logger.fInfo { "Updating toview with params [cursor=$cursor, apiType=${Prefs.apiType}]" }
        updating = true
        lastFailureWasAuth = false
        try {
            val data = toViewRepository.getToView(
                cursor = cursor,
                preferApiType = Prefs.apiType
            )
            throwIfStale(expectedGeneration)

             /*
            上游原始写法，保留参考，不使用。
             现在改为 buildToViewCards() + 主线程批量 addAll，
             这样可以统一字段转换、做去重，并减少主线程切换次数。

             data.data.forEach { toViewItem ->
                 histories.addWithMainContext(
                     VideoCardData(
                         avid = toViewItem.oid,
                         title = toViewItem.title,
                         cover = toViewItem.cover,
                         upName = toViewItem.author,
                         upMid = toViewItem.mid,
                         timeString = if (toViewItem.progress == -1) {
                             context.getString(R.string.play_time_finish)
                         } else {
                             context.getString(
                                 R.string.play_time_history,
                                 (toViewItem.progress * 1000L).formatHourMinSec(),
                                 (toViewItem.duration * 1000L).formatHourMinSec()
                             )
                         }
                     )
                 )
             }
              */

            val cards = buildToViewCards(data.data, context)
            throwIfStale(expectedGeneration)

            withContext(Dispatchers.Main) {
                throwIfStale(expectedGeneration)

                val existingAids = HashSet<Long>(histories.size + cards.size)
                histories.forEach { existingAids.add(it.avid) }

                val distinctCards = ArrayList<VideoCardData>(cards.size)
                cards.forEach { card ->
                    if (existingAids.add(card.avid)) {
                        distinctCards.add(card)
                    }
                }

                histories.addAll(distinctCards)
            }

            cursor = data.cursor
            logger.fInfo { "Update toview cursor: [cursor=$cursor]" }
            logger.fInfo { "Update toview success" }
            withContext(Dispatchers.Main) {
                throwIfStale(expectedGeneration)
                lastFailureWasAuth = false
                if (initialLoadState == LoadState.Loading) {
                    initialLoadState = LoadState.Success
                }
                noMore = cursor == 0L
            }
            if (cursor == 0L) {
                logger.fInfo { "No more toview" }
            }
        } catch (_: CancellationException) {
            logger.fInfo { "Update toview canceled" }
        } catch (t: Throwable) {
            logger.fWarn { "Update toview failed: ${t.stackTraceToString()}" }
            when (t) {
                is AuthFailureException -> {
                    if (expectedGeneration != requestGeneration) return
                    withContext(Dispatchers.Main) {
                        if (expectedGeneration != requestGeneration) return@withContext
                        lastFailureWasAuth = true
                        if (initialLoadState == LoadState.Loading) {
                            initialLoadState = LoadState.Error
                        }
                        if (showErrorToast) {
                            BVApp.context.getString(R.string.exception_auth_failure)
                                .toast(BVApp.context)
                        }
                        logger.fInfo { "User auth failure" }
                        if (allowAuthFailureLogout && !BuildConfig.DEBUG) {
                            userRepository.logout()
                        }
                    }
                }

                else -> {
                    if (expectedGeneration != requestGeneration) return
                    withContext(Dispatchers.Main) {
                        if (expectedGeneration != requestGeneration) return@withContext
                        lastFailureWasAuth = false
                        if (initialLoadState == LoadState.Loading) {
                            initialLoadState = LoadState.Error
                        }
                        if (showErrorToast) {
                            "加载稍后再看失败: ${t.localizedMessage}".toast(BVApp.context)
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

    private suspend fun refreshToViewSnapshot(
        expectedGeneration: Long,
        showErrorToast: Boolean,
        context: Context = BVApp.context
    ) {
        if (expectedGeneration != requestGeneration) return
        if (updating) return

        logger.fInfo { "Refreshing toview full snapshot with params [apiType=${Prefs.apiType}]" }
        updating = true
        lastFailureWasAuth = false
        try {
            val snapshot = rebuildFullSnapshot(
                expectedGeneration = expectedGeneration,
                context = context
            )

            withContext(Dispatchers.Main) {
                throwIfStale(expectedGeneration)
                histories.swapListSkipEqual(snapshot.cards)
                cursor = snapshot.cursor
                activationRefreshState = LoadState.Success
                lastFailureWasAuth = false
                if (initialLoadState == LoadState.Loading) {
                    initialLoadState = LoadState.Success
                }
                noMore = snapshot.noMore
            }

            logger.fInfo {
                "Refresh toview full snapshot success: size=${snapshot.cards.size}, cursor=$cursor, noMore=${snapshot.noMore}"
            }
        } catch (_: CancellationException) {
            logger.fInfo { "Refresh toview full snapshot canceled" }
        } catch (t: Throwable) {
            logger.fWarn { "Refresh toview full snapshot failed: ${t.stackTraceToString()}" }
            when (t) {
                is AuthFailureException -> {
                    if (expectedGeneration != requestGeneration) return
                    withContext(Dispatchers.Main) {
                        if (expectedGeneration != requestGeneration) return@withContext
                        lastFailureWasAuth = true
                        activationRefreshState = LoadState.Error
                        if (initialLoadState == LoadState.Loading) {
                            initialLoadState = LoadState.Error
                        }
                        if (showErrorToast) {
                            BVApp.context.getString(R.string.exception_auth_failure)
                                .toast(BVApp.context)
                        }
                        logger.fInfo { "User auth failure" }
                        if (!BuildConfig.DEBUG) userRepository.logout()
                    }
                }

                else -> {
                    if (expectedGeneration != requestGeneration) return
                    withContext(Dispatchers.Main) {
                        if (expectedGeneration != requestGeneration) return@withContext
                        lastFailureWasAuth = false
                        activationRefreshState = LoadState.Error
                        if (initialLoadState == LoadState.Loading) {
                            initialLoadState = LoadState.Error
                        }
                        if (showErrorToast) {
                            "加载稍后再看失败: ${t.localizedMessage}".toast(BVApp.context)
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
}