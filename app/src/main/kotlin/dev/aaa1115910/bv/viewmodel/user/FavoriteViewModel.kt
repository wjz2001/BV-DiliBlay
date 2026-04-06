package dev.aaa1115910.bv.viewmodel.user

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.FavoriteFolderMetadata
import dev.aaa1115910.biliapi.entity.FavoriteItemType
import dev.aaa1115910.biliapi.http.entity.AuthFailureException
import dev.aaa1115910.biliapi.repositories.FavoriteRepository
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.repository.UserRepository
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import dev.aaa1115910.bv.util.formatHourMinSec
import dev.aaa1115910.bv.viewmodel.common.DebouncedActivationController
import dev.aaa1115910.bv.viewmodel.common.LoadState
import dev.aaa1115910.bv.viewmodel.common.canAutoLoad
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
class FavoriteViewModel(
    private val favoriteRepository: FavoriteRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    var favoriteFolderMetadataList by mutableStateOf<List<FavoriteFolderMetadata>>(emptyList())
        private set
    var favorites by mutableStateOf<List<VideoCardData>>(emptyList())
        private set

    var currentFavoriteFolderMetadata: FavoriteFolderMetadata? by mutableStateOf(null)

    private val pageSize = 20
    private var pageNumber = 1
    private var hasMore = true

    private var updatingFolders = false
    private var updatingFolderItems = false

    private var updateFoldersJob: Job? = null
    private var updateItemsJob: Job? = null

    // 1s 悬停 gate + 后台渐进加载
    // UI 在“Tab 按钮停留满 1s”后才会置 true
    var allowAutoLoad by mutableStateOf(false)

    // Dialog 打开时暂停加载；关闭后继续
    var loadingPaused by mutableStateOf(false)
        private set

    fun updateLoadingPaused(paused: Boolean) {
        loadingPaused = paused
    }

    var isAutoLoading by mutableStateOf(false)
        private set

    private var autoLoadJob: Job? = null
    private val requestMutex = Mutex()

    var initialLoadState by mutableStateOf(LoadState.Idle)
        private set

    var lastFailureWasAuth by mutableStateOf(false)
        private set

    @Volatile
    private var requestGeneration = 0L
    private var pendingRestoreFolderId: Long? = null

    private val folderActivation = DebouncedActivationController<Long?>(
        initial = null,
        scope = viewModelScope,
    )

    val focusedFolderId get() = folderActivation.focused
    val activeFolderId get() = folderActivation.active

    fun onFolderFocused(target: Long) = folderActivation.onFocused(target)
    fun onFolderClicked(target: Long) = folderActivation.onClicked(target)

    private fun alignFolderActivation(target: Long?) {
        folderActivation.onClicked(target)
    }

    fun syncFolderActivationToCurrent() {
        alignFolderActivation(currentFavoriteFolderMetadata?.id)
    }

    fun ensureLoaded() {
        if (!initialLoadState.canAutoLoad()) return
        initialLoadState = LoadState.Loading
        updateFoldersInfo()
    }

    fun reloadAll() {
        pendingRestoreFolderId = currentFavoriteFolderMetadata?.id

        requestGeneration++
        updateFoldersJob?.cancel()
        updateItemsJob?.cancel()
        autoLoadJob?.cancel()
        updateFoldersJob = null
        updateItemsJob = null
        autoLoadJob = null

        favoriteFolderMetadataList = emptyList()
        favorites = emptyList()
        currentFavoriteFolderMetadata = null
        alignFolderActivation(null)
        updatingFolders = false
        updatingFolderItems = false
        initialLoadState = LoadState.Loading
        lastFailureWasAuth = false
        allowAutoLoad = false
        isAutoLoading = false
        loadingPaused = false
        resetPageNumber()

        updateFoldersInfo()
    }

    fun clearData() {
        requestGeneration++
        updateFoldersJob?.cancel()
        updateItemsJob?.cancel()
        autoLoadJob?.cancel()
        updateFoldersJob = null
        updateItemsJob = null
        autoLoadJob = null
        pendingRestoreFolderId = null

        favoriteFolderMetadataList = emptyList()
        favorites = emptyList()
        currentFavoriteFolderMetadata = null
        alignFolderActivation(null)
        updatingFolders = false
        updatingFolderItems = false
        initialLoadState = LoadState.Idle
        lastFailureWasAuth = false
        allowAutoLoad = false
        isAutoLoading = false
        loadingPaused = false
        resetPageNumber()
    }

    fun stopAutoLoad() {
        // 切换收藏夹时：必须立刻停
        autoLoadJob?.cancel()
        updateItemsJob?.cancel()
        autoLoadJob = null
        updateItemsJob = null

        isAutoLoading = false
        updatingFolderItems = false

        // 退出/切换时确保不处于暂停态
        loadingPaused = false

        // gate 由 UI 再次满足 1s 后开启
        allowAutoLoad = false
    }

    fun switchToFolder(folderMetadata: FavoriteFolderMetadata) {
        // 切换收藏夹：先停掉旧的自动加载/翻页任务
        stopAutoLoad()

        // 切到新收藏夹并重置当前列表
        currentFavoriteFolderMetadata = folderMetadata
        favorites = emptyList()
        resetPageNumber()

        // 立即拉取新收藏夹第一页
        updateFolderItems(force = true)
    }

    fun updateFoldersInfo() {
        if (updatingFolders) return
        val expectedGeneration = requestGeneration
        updatingFolders = true
        logger.fInfo { "Updating favorite folders" }

        updateFoldersJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                lastFailureWasAuth = false
                favoriteRepository.getAllFavoriteFolderMetadataList(
                    mid = Prefs.uid,
                    preferApiType = Prefs.apiType
                )
            }.onSuccess { folderList ->
                if (expectedGeneration != requestGeneration) return@launch
                withContext(Dispatchers.Main) {
                    if (expectedGeneration != requestGeneration) return@withContext

                    val restoreFolderId = pendingRestoreFolderId
                    favoriteFolderMetadataList = folderList
                    currentFavoriteFolderMetadata =
                        folderList.firstOrNull { it.id == restoreFolderId }
                            ?: folderList.firstOrNull()
                    pendingRestoreFolderId = null
                    lastFailureWasAuth = false

                    syncFolderActivationToCurrent()
                }
                logger.fInfo { "Update favorite folders success: ${folderList.map { it.id }}" }
                updateFolderItems(force = true)
            }.onFailure { throwable ->
                logger.fWarn { "Update favorite folders failed: ${throwable.stackTraceToString()}" }
                when (throwable) {
                    is AuthFailureException -> {
                        if (expectedGeneration != requestGeneration) return@onFailure
                        withContext(Dispatchers.Main) {
                            if (expectedGeneration != requestGeneration) return@withContext
                            lastFailureWasAuth = true
                            initialLoadState = LoadState.Error
                            if (!BuildConfig.DEBUG) userRepository.logout()
                        }
                    }

                    else -> {
                        if (expectedGeneration != requestGeneration) return@onFailure
                        withContext(Dispatchers.Main) {
                            if (expectedGeneration != requestGeneration) return@withContext
                            lastFailureWasAuth = false
                            initialLoadState = LoadState.Error
                        }
                    }
                }
            }
            if (expectedGeneration == requestGeneration) {
                updatingFolders = false
            }
        }
    }

    fun startAutoLoad() {
        if (!allowAutoLoad) return
        if (autoLoadJob?.isActive == true) return

        val expectedFolderId = currentFavoriteFolderMetadata?.id ?: return
        val expectedGeneration = requestGeneration

        autoLoadJob = viewModelScope.launch(Dispatchers.Default) {
            isAutoLoading = true

            var backoffMs = 0L
            while (isActive) {
                if (expectedGeneration != requestGeneration) break

                while (isActive && loadingPaused) {
                    delay(100)
                }

                if (!allowAutoLoad) {
                    delay(100)
                    continue
                }

                if (currentFavoriteFolderMetadata?.id != expectedFolderId) break
                if (!hasMore) break

                val ok = loadNextPage(
                    expectedFolderId = expectedFolderId,
                    expectedGeneration = expectedGeneration
                )

                if (currentFavoriteFolderMetadata?.id != expectedFolderId) break
                if (!hasMore) break

                if (ok) {
                    backoffMs = 0L
                    delay(Random.nextLong(100L, 200L))
                } else {
                    backoffMs = if (backoffMs == 0L) 5_000L else (backoffMs * 2).coerceAtMost(60_000L)
                    delay(backoffMs)
                }
            }

            isAutoLoading = false
        }
    }

    fun updateFolderItems(force: Boolean = false) {
        if (force) {
            updateItemsJob?.cancel()
            resetPageNumber()
            updatingFolderItems = false
        }

        if (loadingPaused) return

        val expectedFolderId = currentFavoriteFolderMetadata?.id ?: return
        val expectedGeneration = requestGeneration

        updateItemsJob = viewModelScope.launch(Dispatchers.Default) {
            loadNextPage(
                expectedFolderId = expectedFolderId,
                expectedGeneration = expectedGeneration
            )
        }
    }

    fun resetPageNumber() {
        pageNumber = 1
        hasMore = true
    }

    private suspend fun loadNextPage(
        expectedFolderId: Long,
        expectedGeneration: Long
    ): Boolean = requestMutex.withLock {
        if (expectedGeneration != requestGeneration) return false
        if (loadingPaused) return false

        val folder = currentFavoriteFolderMetadata ?: return false
        if (folder.id != expectedFolderId) return false

        if (updatingFolderItems || !hasMore) return false
        updatingFolderItems = true

        logger.fInfo { "Updating favorite folder items with media id: ${folder.id}" }

        return try {
            lastFailureWasAuth = false
            val favoriteFolderData = withContext(Dispatchers.IO) {
                favoriteRepository.getFavoriteFolderData(
                    mediaId = folder.id,
                    pageSize = pageSize,
                    pageNumber = pageNumber,
                    preferApiType = Prefs.apiType
                )
            }

            if (expectedGeneration != requestGeneration) return false

            val appended = favoriteFolderData.medias.mapNotNull { favoriteItem ->
                if (favoriteItem.type != FavoriteItemType.Video) {
                    null
                } else if (
                    dev.aaa1115910.bv.block.BlockManager.isPageEnabled(dev.aaa1115910.bv.block.BlockPage.Favorite) &&
                    dev.aaa1115910.bv.block.BlockManager.isBlocked(favoriteItem.upper.mid)
                ) {
                    null
                } else {
                    VideoCardData(
                        avid = favoriteItem.id,
                        title = favoriteItem.title,
                        cover = favoriteItem.cover,
                        upName = favoriteItem.upper.name,
                        upMid = favoriteItem.upper.mid,
                        timeString = (favoriteItem.duration * 1000L).formatHourMinSec()
                    )
                }
            }

            withContext(Dispatchers.Main) {
                if (expectedGeneration != requestGeneration) return@withContext
                if (currentFavoriteFolderMetadata?.id != expectedFolderId) return@withContext

                favorites = favorites + appended
                lastFailureWasAuth = false

                if (pageNumber == 1 && initialLoadState == LoadState.Loading) {
                    initialLoadState = LoadState.Success
                }
            }

            hasMore = favoriteFolderData.hasMore
            pageNumber++
            logger.fInfo { "Update favorite items success" }
            true
        } catch (t: Throwable) {
            logger.fInfo { "Update favorite items failed: ${t.stackTraceToString()}" }
            when (t) {
                is AuthFailureException -> {
                    if (expectedGeneration != requestGeneration) return false
                    withContext(Dispatchers.Main) {
                        if (expectedGeneration != requestGeneration) return@withContext
                        lastFailureWasAuth = true
                        if (initialLoadState == LoadState.Loading) {
                            initialLoadState = LoadState.Error
                        }
                        if (!BuildConfig.DEBUG) userRepository.logout()
                    }
                }

                else -> {
                    if (expectedGeneration != requestGeneration) return false
                    withContext(Dispatchers.Main) {
                        if (expectedGeneration != requestGeneration) return@withContext
                        lastFailureWasAuth = false
                        if (initialLoadState == LoadState.Loading) {
                            initialLoadState = LoadState.Error
                        }
                    }
                }
            }
            false
        } finally {
            if (expectedGeneration == requestGeneration) {
                updatingFolderItems = false
            }
        }
    }

    override fun onCleared() {
        folderActivation.cancel()
        super.onCleared()
    }
}