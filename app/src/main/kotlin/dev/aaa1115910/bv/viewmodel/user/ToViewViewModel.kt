package dev.aaa1115910.bv.viewmodel.user

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.http.entity.AuthFailureException
import dev.aaa1115910.biliapi.repositories.ToViewRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.repository.UserRepository
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.addWithMainContext
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import dev.aaa1115910.bv.util.formatHourMinSec
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.common.LoadState
import dev.aaa1115910.bv.viewmodel.common.canAutoLoad
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    }

    var histories = mutableStateListOf<VideoCardData>()
    var noMore by mutableStateOf(false)

    private var cursor = 0L
    private var updating = false

    private var updateJob: Job? = null

    var initialLoadState by mutableStateOf(LoadState.Idle)
        private set
    @Volatile private var requestGeneration = 0L
    private val maxItems = 600

    fun update() {
        if (updateJob?.isActive == true) return
        val expectedGeneration = requestGeneration
        updateJob = viewModelScope.launch(Dispatchers.IO) {
            updateToView(expectedGeneration = expectedGeneration)
        }
    }

    fun ensureLoaded() {
        if (!initialLoadState.canAutoLoad()) return
        initialLoadState = LoadState.Loading
        update()
    }

    fun reloadAll() {
        requestGeneration++
        updateJob?.cancel()
        histories.clear()
        cursor = 0
        noMore = false
        updating = false
        initialLoadState = LoadState.Loading
        update()
    }

    fun clearData() {
        requestGeneration++
        updateJob?.cancel()
        histories.clear()
        cursor = 0
        noMore = false
        updating = false
        initialLoadState = LoadState.Idle
    }

    private suspend fun updateToView(
        expectedGeneration: Long,
        context: Context = BVApp.context
    ) {
        if (expectedGeneration != requestGeneration) return
        if (updating || noMore) return

        logger.fInfo { "Updating histories with params [cursor=$cursor, apiType=${Prefs.apiType}]" }
        updating = true
        try {
            val data = toViewRepository.getToView(
                cursor = cursor,
                preferApiType = Prefs.apiType
            )

            if (expectedGeneration != requestGeneration) return

            data.data.forEach { toViewItem ->
                if (expectedGeneration != requestGeneration) return
                if (dev.aaa1115910.bv.block.BlockManager.isPageEnabled(dev.aaa1115910.bv.block.BlockPage.ToView)
                    && dev.aaa1115910.bv.block.BlockManager.isBlocked(toViewItem.mid)
                ) return@forEach

                histories.addWithMainContext(
                    VideoCardData(
                        avid = toViewItem.oid,
                        title = toViewItem.title,
                        cover = toViewItem.cover,
                        upName = toViewItem.author,
                        upMid = toViewItem.mid,
                        timeString = if (toViewItem.progress == -1) context.getString(R.string.play_time_finish)
                        else context.getString(
                            R.string.play_time_history,
                            (toViewItem.progress * 1000L).formatHourMinSec(),
                            (toViewItem.duration * 1000L).formatHourMinSec()
                        )
                    )
                )
            }

            if (expectedGeneration != requestGeneration) return

            if (histories.size > maxItems) {
                val overflow = histories.size - maxItems
                repeat(overflow) {
                    if (histories.isNotEmpty()) {
                        histories.removeAt(histories.lastIndex)
                    }
                }
            }

            cursor = data.cursor
            logger.fInfo { "Update toview cursor: [cursor=$cursor]" }
            logger.fInfo { "Update histories success" }
            withContext(Dispatchers.Main) {
                if (expectedGeneration != requestGeneration) return@withContext
                if (initialLoadState == LoadState.Loading) {
                    initialLoadState = LoadState.Success
                }
                if (cursor == 0L) {
                    noMore = true
                }
            }
            if (cursor == 0L) {
                logger.fInfo { "No more toview" }
            }
        } catch (t: Throwable) {
            logger.fWarn { "Update histories failed: ${t.stackTraceToString()}" }
            withContext(Dispatchers.Main) {
                if (expectedGeneration == requestGeneration && histories.isEmpty()) {
                    initialLoadState = LoadState.Error
                }
            }
            when (t) {
                is AuthFailureException -> {
                    withContext(Dispatchers.Main) {
                        BVApp.context.getString(R.string.exception_auth_failure)
                            .toast(BVApp.context)
                    }
                    logger.fInfo { "User auth failure" }
                    if (!BuildConfig.DEBUG) userRepository.logout()
                }
                else -> Unit
            }
        } finally {
            if (expectedGeneration == requestGeneration) {
                updating = false
            }
        }
    }

    fun addToView(aid: Long, bvid: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                toViewRepository.addToView(
                    aid = aid,
                    bvid = bvid,
                    preferApiType = Prefs.apiType
                )
            }.onSuccess {
                _uiEffect.emit(UiEffect.ShowToast("添加到稍后再看"))
            }.onFailure {
                logger.fWarn { "Add toview failed: ${it.stackTraceToString()}" }
                _uiEffect.emit(UiEffect.ShowToast("添加到稍后再看失败"))
            }
        }
    }
    fun delToView(aid: Long, viewed: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                toViewRepository.delToView(
                    viewed = viewed,
                    aid = aid,
                    preferApiType = Prefs.apiType
                )
            }.onSuccess {
                histories.removeAll { it.avid == aid }
                _uiEffect.emit(UiEffect.ShowToast("删除稍后再看"))
            }.onFailure {
                logger.fWarn { "Delete toview failed: ${it.stackTraceToString()}" }
                _uiEffect.emit(UiEffect.ShowToast("删除稍后再看失败"))
            }
        }
    }
}