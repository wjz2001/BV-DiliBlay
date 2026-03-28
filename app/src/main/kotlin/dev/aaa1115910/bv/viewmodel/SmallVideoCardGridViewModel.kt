package dev.aaa1115910.bv.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.FavoriteFolderMetadata
import dev.aaa1115910.biliapi.entity.user.CoAuthor
import dev.aaa1115910.biliapi.http.BiliHttpApi
import dev.aaa1115910.biliapi.repositories.FavoriteRepository
import dev.aaa1115910.biliapi.repositories.VideoDetailRepository
import dev.aaa1115910.bv.component.videocard.CoAuthorCacheStore
import dev.aaa1115910.bv.util.Prefs
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import org.koin.android.annotation.KoinViewModel

private val logger = KotlinLogging.logger {}

@Immutable
data class SmallVideoCardItemUiState(
    val isFavorite: Boolean = false,
    val hasMultipleCoAuthors: Boolean = false
)

@Immutable
data class SmallVideoCardCapabilitiesUiState(
    val canFavorite: Boolean = false,
    val canHistory: Boolean = false
)

@Immutable
data class FavoriteDialogUiState(
    val show: Boolean = false,
    val aid: Long? = null,
    val folders: List<FavoriteFolderMetadata> = emptyList(),
    val selectedFolderIds: List<Long> = emptyList()
)

@Immutable
data class CoAuthorsDialogUiState(
    val show: Boolean = false,
    val ownerAid: Long? = null,
    val authors: List<CoAuthor> = emptyList()
)

@Immutable
data class SmallVideoCardGridUiState(
    val capabilities: SmallVideoCardCapabilitiesUiState = SmallVideoCardCapabilitiesUiState(),
    val favoriteDialog: FavoriteDialogUiState = FavoriteDialogUiState(),
    val coAuthorsDialog: CoAuthorsDialogUiState = CoAuthorsDialogUiState(),
    val lastDismissedDialogAid: Long? = null
)

sealed interface SmallVideoCardGridEvent {
    data class Toast(val message: String) : SmallVideoCardGridEvent
    data class NavigateUp(val mid: Long, val name: String) : SmallVideoCardGridEvent
}

@KoinViewModel
class SmallVideoCardGridViewModel(
    private val favoriteRepository: FavoriteRepository,
    private val videoDetailRepository: VideoDetailRepository
) : ViewModel() {

    private val cardUiFlows =
        ConcurrentHashMap<Long, MutableStateFlow<SmallVideoCardItemUiState>>()

    private val checkedFavoriteAids = ConcurrentHashMap<Long, Boolean>()
    private val historyCidCache = ConcurrentHashMap<Long, Long>()
    private val coAuthorsCache = ConcurrentHashMap<Long, List<CoAuthor>>()

    private val favoriteCheckJobs = ConcurrentHashMap<Long, Job>()
    private val coAuthorPrefetchJobs = ConcurrentHashMap<Long, Job>()

    private val _uiState = MutableStateFlow(
        SmallVideoCardGridUiState(
            capabilities = buildCapabilities()
        )
    )
    val uiState: StateFlow<SmallVideoCardGridUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SmallVideoCardGridEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    fun refreshCapabilities() {
        _uiState.value = _uiState.value.copy(
            capabilities = buildCapabilities()
        )
    }

    fun cardUiFlow(aid: Long): StateFlow<SmallVideoCardItemUiState> {
        return cardUiFlows.getOrPut(aid) {
            MutableStateFlow(SmallVideoCardItemUiState())
        }.asStateFlow()
    }

    private fun updateCardUi(
        aid: Long,
        transform: (SmallVideoCardItemUiState) -> SmallVideoCardItemUiState
    ) {
        val flow = cardUiFlows.getOrPut(aid) {
            MutableStateFlow(SmallVideoCardItemUiState())
        }
        flow.value = transform(flow.value)
    }

    fun onActionsShown(
        aid: Long,
        canGoToUpPage: Boolean
    ) {
        ensureFavoriteChecked(aid)
        if (canGoToUpPage) {
            prefetchCoAuthors(aid)
        }
    }

    fun onActionsClosed(aid: Long) {
        coAuthorPrefetchJobs.remove(aid)?.cancel()
        CoAuthorCacheStore.cancelInFlight(
            avid = aid,
            apiType = Prefs.apiType
        )
    }

    private fun ensureFavoriteChecked(aid: Long) {
        if (!canCheckFavorite()) return
        if (checkedFavoriteAids.containsKey(aid)) return
        if (favoriteCheckJobs[aid]?.isActive == true) return

        favoriteCheckJobs[aid] = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                favoriteRepository.checkVideoFavoured(
                    aid = aid,
                    preferApiType = Prefs.apiType
                )
            }.onSuccess { favoured ->
                withContext(Dispatchers.Main) {
                    checkedFavoriteAids[aid] = true
                    updateCardUi(aid) { it.copy(isFavorite = favoured) }
                }
            }.onFailure { e ->
                logger.warn { "checkVideoFavoured failed: aid=$aid, error=${e.stackTraceToString()}" }
                withContext(Dispatchers.Main) {
                    checkedFavoriteAids[aid] = true
                }
            }
        }
    }

    private fun prefetchCoAuthors(aid: Long) {
        if (coAuthorsCache.containsKey(aid)) return
        if (coAuthorPrefetchJobs[aid]?.isActive == true) return

        coAuthorPrefetchJobs[aid] = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                CoAuthorCacheStore.getOrFetch(
                    avid = aid,
                    preferApiType = Prefs.apiType,
                    repository = videoDetailRepository
                ).authors
            }.onSuccess { authors ->
                withContext(Dispatchers.Main) {
                    coAuthorsCache[aid] = authors
                    updateCardUi(aid) {
                        it.copy(hasMultipleCoAuthors = authors.distinctBy { author -> author.mid }.size > 1)
                    }
                }
            }.onFailure { e ->
                if (e is CancellationException) return@onFailure
                logger.warn { "prefetchCoAuthors failed: aid=$aid, error=${e.stackTraceToString()}" }
            }
        }
    }

    fun reportHistory(aid: Long) {
        if (!_uiState.value.capabilities.canHistory) return

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val resolvedCid = historyCidCache[aid]
                    ?: videoDetailRepository
                        .getUgcPages(
                            aid = aid,
                            preferApiType = Prefs.apiType
                        )
                        .firstOrNull()
                        ?.cid
                        ?.takeIf { it != 0L }

                require(resolvedCid != null) { "cid is null" }
                historyCidCache[aid] = resolvedCid

                BiliHttpApi.sendHeartbeat(
                    avid = aid,
                    cid = resolvedCid,
                    playedTime = 1,
                    accessKey = Prefs.accessToken.takeIf { it.isNotEmpty() }
                )
            }.onSuccess {
                _events.tryEmit(SmallVideoCardGridEvent.Toast("已添加至历史记录"))
            }.onFailure { e ->
                logger.warn { "reportHistory failed: aid=$aid, error=${e.stackTraceToString()}" }
            }
        }
    }

    fun openFavoriteDialog(aid: Long) {
        if (!_uiState.value.capabilities.canFavorite) return

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val list = favoriteRepository.getAllFavoriteFolderMetadataList(
                    mid = Prefs.uid,
                    rid = aid,
                    preferApiType = Prefs.apiType
                )
                val selected = list.filter { it.videoInThisFav }.map { it.id }

                withContext(Dispatchers.Main) {
                    updateCardUi(aid) { it.copy(isFavorite = selected.isNotEmpty()) }
                    _uiState.value = _uiState.value.copy(
                        favoriteDialog = FavoriteDialogUiState(
                            show = true,
                            aid = aid,
                            folders = list,
                            selectedFolderIds = selected
                        )
                    )
                }
            }.onFailure { e ->
                logger.warn { "openFavoriteDialog failed: aid=$aid, error=${e.stackTraceToString()}" }
            }
        }
    }

    fun dismissFavoriteDialog() {
        val dismissedAid = _uiState.value.favoriteDialog.aid
        _uiState.value = _uiState.value.copy(
            favoriteDialog = FavoriteDialogUiState(),
            lastDismissedDialogAid = dismissedAid
        )
    }

    fun consumeLastDismissedDialogAid(aid: Long) {
        if (_uiState.value.lastDismissedDialogAid == aid) {
            _uiState.value = _uiState.value.copy(lastDismissedDialogAid = null)
        }
    }

    fun updateFavoriteFolders(folderIds: List<Long>) {
        val dialog = _uiState.value.favoriteDialog
        val aid = dialog.aid ?: return
        val allFolders = dialog.folders

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                favoriteRepository.updateVideoToFavoriteFolder(
                    aid = aid,
                    addMediaIds = folderIds,
                    delMediaIds = allFolders.map { it.id } - folderIds.toSet(),
                    preferApiType = Prefs.apiType
                )
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    updateCardUi(aid) { it.copy(isFavorite = folderIds.isNotEmpty()) }
                    _uiState.value = _uiState.value.copy(
                        favoriteDialog = dialog.copy(
                            selectedFolderIds = folderIds
                        )
                    )
                }
            }.onFailure { e ->
                logger.warn { "updateFavoriteFolders failed: aid=$aid, error=${e.stackTraceToString()}" }
            }
        }
    }

    fun openCoAuthorsOrNavigate(
        aid: Long,
        fallbackMid: Long?,
        fallbackName: String?
    ) {
        val cached = coAuthorsCache[aid]
        if (cached != null) {
            handleCoAuthors(aid, cached, fallbackMid, fallbackName)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                CoAuthorCacheStore.getOrFetch(
                    avid = aid,
                    preferApiType = Prefs.apiType,
                    repository = videoDetailRepository
                ).authors
            }.onSuccess { authors ->
                withContext(Dispatchers.Main) {
                    coAuthorsCache[aid] = authors
                    updateCardUi(aid) {
                        it.copy(hasMultipleCoAuthors = authors.distinctBy { author -> author.mid }.size > 1)
                    }
                    handleCoAuthors(aid, authors, fallbackMid, fallbackName)
                }
            }.onFailure { e ->
                logger.warn { "openCoAuthorsOrNavigate failed: aid=$aid, error=${e.stackTraceToString()}" }
                if (fallbackMid != null && !fallbackName.isNullOrEmpty()) {
                    _events.tryEmit(SmallVideoCardGridEvent.NavigateUp(fallbackMid, fallbackName))
                }
            }
        }
    }

    private fun handleCoAuthors(
        aid: Long,
        authors: List<CoAuthor>,
        fallbackMid: Long?,
        fallbackName: String?
    ) {
        val distinct = authors.distinctBy { it.mid }

        when {
            distinct.isEmpty() -> {
                if (fallbackMid != null && !fallbackName.isNullOrEmpty()) {
                    _events.tryEmit(SmallVideoCardGridEvent.NavigateUp(fallbackMid, fallbackName))
                }
            }

            distinct.size == 1 -> {
                val author = distinct.first()
                _events.tryEmit(SmallVideoCardGridEvent.NavigateUp(author.mid, author.name))
            }

            else -> {
                _uiState.value = _uiState.value.copy(
                    coAuthorsDialog = CoAuthorsDialogUiState(
                        show = true,
                        ownerAid = aid,
                        authors = distinct
                    )
                )
            }
        }
    }

    fun dismissCoAuthorsDialog() {
        val dismissedAid = _uiState.value.coAuthorsDialog.ownerAid
        _uiState.value = _uiState.value.copy(
            coAuthorsDialog = CoAuthorsDialogUiState(),
            lastDismissedDialogAid = dismissedAid
        )
    }

    fun onCoAuthorClicked(mid: Long, name: String) {
        _events.tryEmit(SmallVideoCardGridEvent.NavigateUp(mid, name))
        dismissCoAuthorsDialog()
    }

    private fun buildCapabilities(): SmallVideoCardCapabilitiesUiState {
        val canFavorite = when (Prefs.apiType) {
            ApiType.Web -> Prefs.sessData.isNotEmpty() && Prefs.biliJct.isNotEmpty() && Prefs.uid != 0L
            ApiType.App -> Prefs.accessToken.isNotEmpty() && Prefs.uid != 0L
        }
        val canHistory = Prefs.accessToken.isNotEmpty()

        return SmallVideoCardCapabilitiesUiState(
            canFavorite = canFavorite,
            canHistory = canHistory
        )
    }

    private fun canCheckFavorite(): Boolean {
        return when (Prefs.apiType) {
            ApiType.Web -> Prefs.sessData.isNotEmpty() && Prefs.uid != 0L
            ApiType.App -> Prefs.accessToken.isNotEmpty() && Prefs.uid != 0L
        }
    }
}