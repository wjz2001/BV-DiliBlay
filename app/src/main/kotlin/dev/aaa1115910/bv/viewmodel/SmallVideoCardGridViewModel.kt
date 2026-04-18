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
import org.koin.core.annotation.KoinViewModel
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * 单张卡片的外部 UI 状态。
 *
 * 这些状态是“卡片自己展示要用到的”，但不应该再由 SmallVideoCard 内部自己维护：
 * - isFavorite：收藏图标是否高亮
 * - hasMultipleCoAuthors：UP 图标显示单人还是多人
 */
@Immutable
data class SmallVideoCardItemUiState(
    val isFavorite: Boolean = false,
    val hasMultipleCoAuthors: Boolean = false
)

/**
 * 当前页面/会话下，SmallVideoCard 的通用能力状态。
 *
 * 这些状态通常和当前登录态、凭证有关。
 */
@Immutable
data class SmallVideoCardCapabilitiesUiState(
    val canFavorite: Boolean = false,
    val canHistory: Boolean = false
)

/**
 * 页面级 FavoriteDialog 状态。
 */
@Immutable
data class FavoriteDialogUiState(
    val show: Boolean = false,
    val aid: Long? = null,
    val folders: List<FavoriteFolderMetadata> = emptyList(),
    val selectedFolderIds: List<Long> = emptyList()
)

/**
 * 页面级 CoAuthorsDialog 状态。
 */
@Immutable
data class CoAuthorsDialogUiState(
    val show: Boolean = false,
    val ownerAid: Long? = null,
    val authors: List<CoAuthor> = emptyList()
)

/**
 * SmallVideoCardGridHost 关注的页面级 UI 状态。
 */
@Immutable
data class SmallVideoCardGridUiState(
    val capabilities: SmallVideoCardCapabilitiesUiState = SmallVideoCardCapabilitiesUiState(),
    val favoriteDialog: FavoriteDialogUiState = FavoriteDialogUiState(),
    val coAuthorsDialog: CoAuthorsDialogUiState = CoAuthorsDialogUiState(),
    val lastDismissedDialogAid: Long? = null
)

/**
 * 页面级一次性事件。
 *
 * 注意：
 * - Toast、导航这类事件不适合放在 StateFlow 里反复持有
 * - 更适合走 SharedFlow
 */
sealed interface SmallVideoCardGridEvent {
    data class Toast(val message: String) : SmallVideoCardGridEvent
    data class NavigateUp(val mid: Long, val name: String) : SmallVideoCardGridEvent
}

/**
 * 通用 SmallVideoCard Grid ViewModel。
 *
 * 目标：
 * 1. 不把卡片通用逻辑散落到每个页面 ViewModel
 * 2. 不再让 SmallVideoCard 自己持有 repository / cache / jobs / dialog state
 * 3. 统一管理：
 *    - 收藏状态检查
 *    - 历史记录上报
 *    - 联合投稿缓存与弹窗
 *    - 收藏夹弹窗
 *
 * 这个 VM 由 SmallVideoCardGridHost 统一持有。
 */
@KoinViewModel
class SmallVideoCardGridViewModel(
    private val favoriteRepository: FavoriteRepository,
    private val videoDetailRepository: VideoDetailRepository
) : ViewModel() {

    /**
     * 每张卡自己的 UI State Flow。
     * key = aid
     */
    private val cardUiFlows =
        ConcurrentHashMap<Long, MutableStateFlow<SmallVideoCardItemUiState>>()

    /**
     * 记录哪些 aid 已经做过 favorite 轻量校准。
     *
     * 注意：
     * - 不能用 ConcurrentHashMap.newKeySet()，因为 minSdk=21 下会报 API 24+
     * - 用 map 模拟 set 即可
     */
    private val checkedFavoriteAids = ConcurrentHashMap<Long, Boolean>()

    /**
     * 历史记录上报用的 cid 缓存。
     */
    private val historyCidCache = ConcurrentHashMap<Long, Long>()

    /**
     * 联合投稿作者缓存。
     */
    private val coAuthorsCache = ConcurrentHashMap<Long, List<CoAuthor>>()

    /**
     * 防止重复发起 favorite 轻量校准请求。
     */
    private val favoriteCheckJobs = ConcurrentHashMap<Long, Job>()

    /**
     * 防止重复发起 coAuthors 预取。
     */
    private val coAuthorPrefetchJobs = ConcurrentHashMap<Long, Job>()

    /**
     * 页面级可观察状态。
     */
    private val _uiState = MutableStateFlow(
        SmallVideoCardGridUiState(
            capabilities = buildCapabilities()
        )
    )
    val uiState: StateFlow<SmallVideoCardGridUiState> = _uiState.asStateFlow()

    /**
     * 页面级一次性事件流。
     */
    private val _events = MutableSharedFlow<SmallVideoCardGridEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    /**
     * 页面进入时刷新能力状态。
     * 适合在 Host 的 LaunchedEffect(Unit) 里调用一次。
     */
    fun refreshCapabilities() {
        _uiState.value = _uiState.value.copy(
            capabilities = buildCapabilities()
        )
    }

    /**
     * 获取指定 aid 对应的卡片 UI Flow。
     *
     * SmallVideoCard 会只 collect 自己那一张卡的 flow，
     * 从而避免“任意一张卡状态变化导致整页卡片一起重组”。
     */
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

    /**
     * 卡片进入 actions 状态时通知 VM。
     *
     * 当前做两件事：
     * 1. favorite 轻量校准
     * 2. coAuthors 预取
     */
    fun onActionsShown(
        aid: Long,
        canGoToUpPage: Boolean
    ) {
        ensureFavoriteChecked(aid)
        if (canGoToUpPage) {
            prefetchCoAuthors(aid)
        }
    }

    /**
     * 卡片退出 actions 状态时通知 VM。
     */
    fun onActionsClosed(aid: Long) {
        coAuthorPrefetchJobs.remove(aid)?.cancel()
        CoAuthorCacheStore.cancelInFlight(
            avid = aid,
            apiType = Prefs.apiType
        )
    }

    /**
     * 轻量校准 favorite 状态。
     *
     * 注意：
     * - 这里只是校准“有没有被收藏”
     * - 不拉收藏夹列表
     */
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
                logger.warn {
                    "checkVideoFavoured failed: aid=$aid, error=${e.stackTraceToString()}"
                }
                withContext(Dispatchers.Main) {
                    checkedFavoriteAids[aid] = true
                }
            }
        }
    }

    /**
     * 预取联合投稿作者。
     *
     * 这里只是预取与缓存，不直接弹窗。
     */
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
                        it.copy(
                            hasMultipleCoAuthors = authors.distinctBy { author -> author.mid }.size > 1
                        )
                    }
                }
            }.onFailure { e ->
                if (e is CancellationException) return@onFailure
                logger.warn {
                    "prefetchCoAuthors failed: aid=$aid, error=${e.stackTraceToString()}"
                }
            }
        }
    }

    /**
     * 历史记录上报。
     */
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
                logger.warn {
                    "reportHistory failed: aid=$aid, error=${e.stackTraceToString()}"
                }
            }
        }
    }

    /**
     * 打开 FavoriteDialog。
     *
     * 这里会先拉取收藏夹列表，再更新页面级 dialog state。
     */
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
                logger.warn {
                    "openFavoriteDialog failed: aid=$aid, error=${e.stackTraceToString()}"
                }
            }
        }
    }

    /**
     * 关闭 FavoriteDialog。
     * 同时记录 lastDismissedDialogAid，方便卡片把焦点拉回默认按钮。
     */
    fun dismissFavoriteDialog() {
        val dismissedAid = _uiState.value.favoriteDialog.aid
        _uiState.value = _uiState.value.copy(
            favoriteDialog = FavoriteDialogUiState(),
            lastDismissedDialogAid = dismissedAid
        )
    }

    /**
     * 卡片消费“最近被关闭的 dialog 所属 aid”。
     */
    fun consumeLastDismissedDialogAid(aid: Long) {
        if (_uiState.value.lastDismissedDialogAid == aid) {
            _uiState.value = _uiState.value.copy(lastDismissedDialogAid = null)
        }
    }

    /**
     * 更新收藏夹勾选结果。
     */
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
                logger.warn {
                    "updateFavoriteFolders failed: aid=$aid, error=${e.stackTraceToString()}"
                }
            }
        }
    }

    /**
     * 打开联合投稿作者选择，或直接跳转单作者。
     *
     * Host 模式下：
     * - SmallVideoCard 不自己处理跳转
     * - 统一由 VM 发事件给 Host
     */
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
                        it.copy(
                            hasMultipleCoAuthors = authors.distinctBy { author -> author.mid }.size > 1
                        )
                    }
                    handleCoAuthors(aid, authors, fallbackMid, fallbackName)
                }
            }.onFailure { e ->
                logger.warn {
                    "openCoAuthorsOrNavigate failed: aid=$aid, error=${e.stackTraceToString()}"
                }
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

    /**
     * 关闭 coAuthors dialog。
     */
    fun dismissCoAuthorsDialog() {
        val dismissedAid = _uiState.value.coAuthorsDialog.ownerAid
        _uiState.value = _uiState.value.copy(
            coAuthorsDialog = CoAuthorsDialogUiState(),
            lastDismissedDialogAid = dismissedAid
        )
    }

    /**
     * 点击某个作者后，统一发导航事件。
     */
    fun onCoAuthorClicked(mid: Long, name: String) {
        _events.tryEmit(SmallVideoCardGridEvent.NavigateUp(mid, name))
        dismissCoAuthorsDialog()
    }

    private fun buildCapabilities(): SmallVideoCardCapabilitiesUiState {
        val canFavorite = when (Prefs.apiType) {
            ApiType.Web -> Prefs.sessData.isNotEmpty() &&
                    Prefs.biliJct.isNotEmpty() &&
                    Prefs.uid != 0L

            ApiType.App -> Prefs.accessToken.isNotEmpty() &&
                    Prefs.uid != 0L
        }

        val canHistory = Prefs.accessToken.isNotEmpty()

        return SmallVideoCardCapabilitiesUiState(
            canFavorite = canFavorite,
            canHistory = canHistory
        )
    }

    private fun canCheckFavorite(): Boolean {
        return when (Prefs.apiType) {
            ApiType.Web -> Prefs.sessData.isNotEmpty() &&
                    Prefs.uid != 0L

            ApiType.App -> Prefs.accessToken.isNotEmpty() &&
                    Prefs.uid != 0L
        }
    }
}