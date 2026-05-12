package dev.aaa1115910.bv.viewmodel.user

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.SubscribedCollectionMetadata
import dev.aaa1115910.biliapi.http.entity.AuthFailureException
import dev.aaa1115910.biliapi.repositories.SubscriptionRepository
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.entity.state.GridViewportState
import dev.aaa1115910.bv.repository.UserRepository
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import dev.aaa1115910.bv.util.formatHourMinSec
import dev.aaa1115910.bv.viewmodel.common.DebouncedActivationController
import dev.aaa1115910.bv.viewmodel.common.LoadState
import dev.aaa1115910.bv.viewmodel.common.accountSessionKey
import dev.aaa1115910.bv.viewmodel.common.canAutoLoad
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel
import kotlin.random.Random

data class SubscribedCollectionRuntimeState(
    val items: ImmutableList<VideoCardData> = persistentListOf(),
    val page: Int = 1,
    val hasMore: Boolean = true,
    val loadState: LoadState = LoadState.Idle,
    val viewport: GridViewportState? = null
)

@KoinViewModel
class SubscribedCollectionViewModel(
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val _favoriteFolderMetadataList = MutableStateFlow(persistentListOf<SubscribedCollectionMetadata>())
    val favoriteFolderMetadataList: StateFlow<ImmutableList<SubscribedCollectionMetadata>> =
        _favoriteFolderMetadataList.asStateFlow()

    private val _folderStates = MutableStateFlow<PersistentMap<Long, SubscribedCollectionRuntimeState>>(persistentHashMapOf())
    val folderStates: StateFlow<ImmutableMap<Long, SubscribedCollectionRuntimeState>> = _folderStates.asStateFlow()

    var currentFavoriteFolderMetadata: SubscribedCollectionMetadata? by mutableStateOf(null)

    private val pageSize = 20

    private var updatingFolders = false
    private var updateFoldersJob: Job? = null

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
    private val folderLoadJobs = mutableMapOf<Long, Job>()
    private val folderLoadJobsLock = Any()
    private val requestMutex = Mutex()

    var initialLoadState by mutableStateOf(LoadState.Idle)
        private set

    var lastFailureWasAuth by mutableStateOf(false)
        private set

    @Volatile
    private var requestGeneration = 0L
    private var pendingRestoreFolderId: Long? = null
    private var loadedAccountSessionKey = userRepository.accountSessionKey()

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
        ensureAccountStateFresh()
        if (!initialLoadState.canAutoLoad()) return
        initialLoadState = LoadState.Loading
        updateFoldersInfo()
    }

    fun reloadAll() {
        ensureAccountStateFresh()
        pendingRestoreFolderId = currentFavoriteFolderMetadata?.id

        requestGeneration++
        cancelAllFolderLoads()
        updateFoldersJob?.cancel()
        autoLoadJob?.cancel()
        updateFoldersJob = null
        autoLoadJob = null

        _favoriteFolderMetadataList.value = persistentListOf()
        _folderStates.value = persistentHashMapOf()
        currentFavoriteFolderMetadata = null
        alignFolderActivation(null)
        updatingFolders = false
        initialLoadState = LoadState.Loading
        lastFailureWasAuth = false
        allowAutoLoad = false
        isAutoLoading = false
        loadingPaused = false

        updateFoldersInfo()
    }

    fun clearData() {
        requestGeneration++
        cancelAllFolderLoads()
        updateFoldersJob?.cancel()
        autoLoadJob?.cancel()
        updateFoldersJob = null
        autoLoadJob = null
        pendingRestoreFolderId = null

        _favoriteFolderMetadataList.value = persistentListOf()
        _folderStates.value = persistentHashMapOf()
        currentFavoriteFolderMetadata = null
        alignFolderActivation(null)
        updatingFolders = false
        initialLoadState = LoadState.Idle
        lastFailureWasAuth = false
        allowAutoLoad = false
        isAutoLoading = false
        loadingPaused = false
        loadedAccountSessionKey = userRepository.accountSessionKey()
    }

    private fun ensureAccountStateFresh() {
        val currentAccountSessionKey = userRepository.accountSessionKey()
        if (loadedAccountSessionKey == currentAccountSessionKey) return
        clearData()
        loadedAccountSessionKey = currentAccountSessionKey
    }

    fun stopAutoLoad() {
        autoLoadJob?.cancel()
        currentFavoriteFolderMetadata?.id?.let { cancelFolderLoad(it) }
        autoLoadJob = null

        isAutoLoading = false

        // 退出/切换时确保不处于暂停态
        loadingPaused = false

        // gate 由 UI 再次满足 1s 后开启
        allowAutoLoad = false
    }

    fun cancelOngoingLoads() {
        stopAutoLoad()
        updateLoadingPaused(true)
    }

    fun switchToFolder(folderMetadata: SubscribedCollectionMetadata) {
        val oldFolderId = currentFavoriteFolderMetadata?.id

        // 切换收藏夹：先停掉旧的自动加载/翻页任务
        stopAutoLoad()
        if (oldFolderId != null && oldFolderId != folderMetadata.id) {
            cancelFolderLoad(oldFolderId)
        }

        currentFavoriteFolderMetadata = folderMetadata

        val state = folderStateOf(folderMetadata.id)
        if (state.items.isEmpty() && state.loadState.canAutoLoad()) {
            updateFolderItems(force = true)
        }
    }

    fun updateFoldersInfo() {
        ensureAccountStateFresh()
        if (updatingFolders) return
        val expectedGeneration = requestGeneration
        updatingFolders = true
        logger.fInfo { "Updating favorite folders" }

        updateFoldersJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                lastFailureWasAuth = false
                subscriptionRepository.getSubscribedCollections(
                    mid = Prefs.uid,
                )
            }.onSuccess { folderList ->
                if (expectedGeneration != requestGeneration) return@launch
                withContext(Dispatchers.Main) {
                    if (expectedGeneration != requestGeneration) return@withContext

                    val restoreFolderId = pendingRestoreFolderId
                    _favoriteFolderMetadataList.value = folderList.toPersistentList()
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
        ensureAccountStateFresh()
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
                if (!folderStateOf(expectedFolderId).hasMore) break

                val ok = loadNextPage(
                    expectedFolderId = expectedFolderId,
                    expectedGeneration = expectedGeneration
                )

                if (currentFavoriteFolderMetadata?.id != expectedFolderId) break
                if (!folderStateOf(expectedFolderId).hasMore) break

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
        ensureAccountStateFresh()
        if (loadingPaused) return

        val expectedFolderId = currentFavoriteFolderMetadata?.id ?: return
        val expectedGeneration = requestGeneration

        if (force) {
            cancelFolderLoad(expectedFolderId)
            updateFolderState(expectedFolderId) { SubscribedCollectionRuntimeState(viewport = it.viewport) }
        }

        val state = folderStateOf(expectedFolderId)
        if (hasActiveFolderLoad(expectedFolderId)) return
        if (!state.hasMore) return

        val job = viewModelScope.launch(Dispatchers.Default) {
            loadNextPage(
                expectedFolderId = expectedFolderId,
                expectedGeneration = expectedGeneration
            )
        }

        putFolderLoadJob(expectedFolderId, job)
        job.invokeOnCompletion {
            removeFolderLoadJob(expectedFolderId, job)
        }
    }

    fun updateFolderViewport(folderId: Long, viewport: GridViewportState) {
        updateFolderState(folderId) {
            it.copy(viewport = viewport)
        }
    }

    private suspend fun loadNextPage(
        expectedFolderId: Long,
        expectedGeneration: Long
    ): Boolean = requestMutex.withLock {
        if (expectedGeneration != requestGeneration) return false
        if (loadingPaused) return false

        val folder = currentFavoriteFolderMetadata ?: return false
        if (folder.id != expectedFolderId) return false

        val state = folderStateOf(expectedFolderId)
        if (state.loadState == LoadState.Loading || !state.hasMore) return false

        updateFolderState(expectedFolderId) {
            it.copy(loadState = LoadState.Loading)
        }

        logger.fInfo { "Updating favorite folder items with media id: ${folder.id}" }

        return try {
            lastFailureWasAuth = false
            val favoriteFolderData = withContext(Dispatchers.IO) {
                subscriptionRepository.getSubscribedCollectionData(
                    metadata = folder,
                    pageSize = pageSize,
                    pageNumber = state.page,
                )
            }

            if (expectedGeneration != requestGeneration) return false

            val appended = favoriteFolderData.medias.mapNotNull { favoriteItem ->
                if (
                    dev.aaa1115910.bv.block.BlockManager.isPageEnabled(dev.aaa1115910.bv.block.BlockPage.Favorite) &&
                    dev.aaa1115910.bv.block.BlockManager.isBlocked(folder.upper?.mid ?: 0L)
                ) {
                    null
                } else {
                    VideoCardData(
                        avid = favoriteItem.id,
                        title = favoriteItem.title,
                        cover = favoriteItem.cover,
                        upName = folder.upper?.name ?: "",
                        upMid = folder.upper?.mid,
                        timeString = (favoriteItem.duration * 1000L).formatHourMinSec()
                    )
                }
            }

            withContext(Dispatchers.Main) {
                if (expectedGeneration != requestGeneration) return@withContext
                if (currentFavoriteFolderMetadata?.id != expectedFolderId) return@withContext

                updateFolderState(expectedFolderId) {
                    it.copy(
                        items = (it.items + appended).toPersistentList(),
                        page = it.page + 1,
                        hasMore = favoriteFolderData.info?.mediaCount?.let { count ->
                            state.page * pageSize < count
                        } ?: (appended.size >= pageSize),
                        loadState = LoadState.Success
                    )
                }
                lastFailureWasAuth = false

                if (state.page == 1 && initialLoadState == LoadState.Loading) {
                    initialLoadState = LoadState.Success
                }
            }

            logger.fInfo { "Update favorite items success" }
            true
        } catch (t: Throwable) {
            if (t is CancellationException) throw t

            logger.fInfo { "Update favorite items failed: ${t.stackTraceToString()}" }
            when (t) {
                is AuthFailureException -> {
                    if (expectedGeneration != requestGeneration) return false
                    withContext(Dispatchers.Main) {
                        if (expectedGeneration != requestGeneration) return@withContext
                        lastFailureWasAuth = true
                        updateFolderState(expectedFolderId) {
                            it.copy(loadState = LoadState.Error)
                        }
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
                        updateFolderState(expectedFolderId) {
                            it.copy(loadState = LoadState.Error)
                        }
                        if (initialLoadState == LoadState.Loading) {
                            initialLoadState = LoadState.Error
                        }
                    }
                }
            }
            false
        } finally {
            if (expectedGeneration == requestGeneration) {
                updateFolderState(expectedFolderId) {
                    if (it.loadState == LoadState.Loading) {
                        it.copy(loadState = LoadState.Idle)
                    } else {
                        it
                    }
                }
            }
        }
    }

    private fun folderStateOf(folderId: Long): SubscribedCollectionRuntimeState {
        return _folderStates.value[folderId] ?: SubscribedCollectionRuntimeState()
    }

    private fun updateFolderState(
        folderId: Long,
        transform: (SubscribedCollectionRuntimeState) -> SubscribedCollectionRuntimeState
    ) {
        _folderStates.update { states ->
            states.put(folderId, transform(states[folderId] ?: SubscribedCollectionRuntimeState()))
        }
    }

    private fun cancelFolderLoad(folderId: Long) {
        takeFolderLoadJob(folderId)?.cancel()
        updateFolderState(folderId) {
            it.copy(
                loadState = if (it.loadState == LoadState.Loading) LoadState.Idle else it.loadState
            )
        }
    }

    private fun cancelAllFolderLoads() {
        takeAllFolderLoadJobs().forEach { it.cancel() }
    }

    private fun hasActiveFolderLoad(folderId: Long): Boolean = synchronized(folderLoadJobsLock) {
        folderLoadJobs[folderId]?.isActive == true
    }

    private fun putFolderLoadJob(folderId: Long, job: Job) = synchronized(folderLoadJobsLock) {
        folderLoadJobs[folderId] = job
    }

    private fun removeFolderLoadJob(folderId: Long, job: Job) = synchronized(folderLoadJobsLock) {
        if (folderLoadJobs[folderId] == job) {
            folderLoadJobs.remove(folderId)
        }
    }

    private fun takeFolderLoadJob(folderId: Long): Job? = synchronized(folderLoadJobsLock) {
        folderLoadJobs.remove(folderId)
    }

    private fun takeAllFolderLoadJobs(): List<Job> = synchronized(folderLoadJobsLock) {
        folderLoadJobs.values.toList().also {
            folderLoadJobs.clear()
        }
    }

    override fun onCleared() {
        folderActivation.cancel()
        cancelAllFolderLoads()
        super.onCleared()
    }
}
