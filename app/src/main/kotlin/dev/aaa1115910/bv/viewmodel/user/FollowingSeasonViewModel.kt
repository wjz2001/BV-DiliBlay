package dev.aaa1115910.bv.viewmodel.user

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.season.FollowingSeason
import dev.aaa1115910.biliapi.entity.season.FollowingSeasonStatus
import dev.aaa1115910.biliapi.entity.season.FollowingSeasonType
import dev.aaa1115910.biliapi.repositories.SeasonRepository
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.viewmodel.common.LoadState
import dev.aaa1115910.bv.viewmodel.common.canAutoLoad
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class FollowingSeasonViewModel(
    private val seasonRepository: SeasonRepository
) : ViewModel() {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    val followingSeasons = mutableStateListOf<FollowingSeason>()
    var followingSeasonType = FollowingSeasonType.Bangumi
    var followingSeasonStatus = FollowingSeasonStatus.All

    private var pageNumber = 1
    private val pageSize = 20
    var noMore by mutableStateOf(false)
    private var updating = false

    private var updateJob: Job? = null
    var initialLoadState by mutableStateOf(LoadState.Idle)
        private set
    @Volatile private var requestGeneration = 0L
    private val maxItems = 360

    init {
        followingSeasonType = FollowingSeasonType.Bangumi
        followingSeasonStatus = FollowingSeasonStatus.All
    }

    fun clearData() {
        requestGeneration++
        updateJob?.cancel()
        pageNumber = 1
        updating = false
        noMore = false
        followingSeasons.clear()
        initialLoadState = LoadState.Idle
    }

    fun ensureLoaded() {
        if (!initialLoadState.canAutoLoad()) return
        initialLoadState = LoadState.Loading
        loadMore()
    }

    fun reloadAll() {
        requestGeneration++
        updateJob?.cancel()
        pageNumber = 1
        updating = false
        noMore = false
        followingSeasons.clear()
        initialLoadState = LoadState.Loading
        loadMore()
    }

    fun loadMore() {
        if (updateJob?.isActive == true) return
        val expectedGeneration = requestGeneration
        updateJob = viewModelScope.launch(Dispatchers.IO) {
            updateData(expectedGeneration)
        }
    }

    private suspend fun updateData(expectedGeneration: Long) {
        if (expectedGeneration != requestGeneration) return
        if (updating || noMore) return

        updating = true
        try {
            logger.fInfo { "Updating following season data" }
            val response = seasonRepository.getFollowingSeasons(
                type = followingSeasonType,
                status = followingSeasonStatus,
                pageNumber = pageNumber,
                pageSize = pageSize,
                preferApiType = Prefs.apiType
            )

            if (expectedGeneration != requestGeneration) return

            withContext(Dispatchers.Main) {
                if (expectedGeneration != requestGeneration) return@withContext
                if (pageSize * pageNumber >= response.total) noMore = true
                pageNumber++
                followingSeasons.addAll(response.list)

                if (followingSeasons.size > maxItems) {
                    val overflow = followingSeasons.size - maxItems
                    repeat(overflow) {
                        if (followingSeasons.isNotEmpty()) {
                            followingSeasons.removeAt(followingSeasons.lastIndex)
                        }
                    }
                }

                if (initialLoadState == LoadState.Loading) {
                    initialLoadState = LoadState.Success
                }
            }
            logger.fInfo { "Following season count: ${response.list.size}" }
        } catch (t: Throwable) {
            logger.fInfo { "Update following seasons failed: ${t.stackTraceToString()}" }
            withContext(Dispatchers.Main) {
                if (expectedGeneration == requestGeneration && followingSeasons.isEmpty()) {
                    initialLoadState = LoadState.Error
                }
            }
        } finally {
            if (expectedGeneration == requestGeneration) {
                updating = false
            }
        }
    }
}

