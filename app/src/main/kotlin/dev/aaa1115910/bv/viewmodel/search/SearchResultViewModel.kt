package dev.aaa1115910.bv.viewmodel.search

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.repositories.SearchFilterDuration
import dev.aaa1115910.biliapi.repositories.SearchFilterOrderType
import dev.aaa1115910.biliapi.repositories.SearchRepository
import dev.aaa1115910.biliapi.repositories.SearchType
import dev.aaa1115910.biliapi.repositories.SearchTypePage
import dev.aaa1115910.biliapi.repositories.SearchTypeResult
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.util.Partition
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fException
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.viewmodel.common.DebouncedActivationController
import dev.aaa1115910.bv.viewmodel.common.LoadState
import dev.aaa1115910.bv.viewmodel.common.canAutoLoad
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class SearchResultViewModel(
    private val searchRepository: SearchRepository
) : ViewModel() {
    companion object {
        private val logger = KotlinLogging.logger { }
        private const val SEARCH_RESULT_TIMEOUT_MS = 8_000L
    }

    var keyword by mutableStateOf("")

    var videoSearchResult by mutableStateOf(SearchResult(SearchType.Video))
    var mediaBangumiSearchResult by mutableStateOf(SearchResult(SearchType.MediaBangumi))
    var mediaFtSearchResult by mutableStateOf(SearchResult(SearchType.MediaFt))
    var biliUserSearchResult by mutableStateOf(SearchResult(SearchType.BiliUser))

    var selectedOrder by mutableStateOf(SearchFilterOrderType.ComprehensiveSort)
    var selectedDuration by mutableStateOf(SearchFilterDuration.All)
    var selectedPartition: Partition? by mutableStateOf(null)
    var selectedChildPartition: Partition? by mutableStateOf(null)

    private val hasMore = true

    var enableProxySearchResult = false

    private val searchTypeActivation = DebouncedActivationController(
        initial = SearchType.Video,
        scope = viewModelScope,
    )

    val focusedSearchType get() = searchTypeActivation.focused
    val activeSearchType get() = searchTypeActivation.active

    private val _loadStateMap = MutableStateFlow(
        SearchType.entries.fold(persistentHashMapOf<SearchType, LoadState>()) { map, type ->
            map.put(type, LoadState.Idle)
        }
    )
    val loadStateMap = _loadStateMap.asStateFlow()
    private val loadingTypes = mutableSetOf<SearchType>()
    private val requestTokenMap = mutableMapOf<SearchType, Long>()
    private val requestMutexMap = mutableMapOf<SearchType, kotlinx.coroutines.sync.Mutex>()

    fun loadStateOf(type: SearchType): LoadState = _loadStateMap.value[type] ?: LoadState.Idle

    private fun setLoadState(type: SearchType, state: LoadState) {
        _loadStateMap.update { it.put(type, state) }
    }

    fun onSearchTypeFocused(target: SearchType) = searchTypeActivation.onFocused(target)
    fun onSearchTypeClicked(target: SearchType) = searchTypeActivation.onClicked(target)

    private fun tokenOf(type: SearchType): Long = requestTokenMap[type] ?: 0L
    private fun bumpToken(type: SearchType): Long {
        val next = tokenOf(type) + 1L
        requestTokenMap[type] = next
        return next
    }
    private fun mutexOf(type: SearchType): kotlinx.coroutines.sync.Mutex =
        requestMutexMap.getOrPut(type) { kotlinx.coroutines.sync.Mutex() }

    fun onKeywordChanged(newKeyword: String, enableProxy: Boolean) {
        if (keyword == newKeyword && enableProxySearchResult == enableProxy) return
        keyword = newKeyword
        enableProxySearchResult = enableProxy
        resetAllForNewKeyword()
        ensureLoaded(activeSearchType)
    }

    fun updateActiveType() {
        update(activeSearchType)
    }

    fun resetAllForNewKeyword() {
        SearchType.entries.forEach {
            bumpToken(it)
            setLoadState(it, LoadState.Idle)
        }
        resetPages()
        clearResults()
        loadingTypes.clear()
    }

    fun ensureLoaded(type: SearchType) {
        if (!loadStateOf(type).canAutoLoad()) return
        setLoadState(type, LoadState.Loading)
        loadMore(type, ignoreUpdating = true)
    }

    fun update(type: SearchType) {
        val token = bumpToken(type)
        resetPage(type)
        clearResult(type)
        setLoadState(type, LoadState.Loading)
        loadMore(type, ignoreUpdating = true, expectedToken = token)
    }

    private fun resetPage(type: SearchType) {
        when (type) {
            SearchType.Video -> videoSearchResult = videoSearchResult.resetPage()
            SearchType.MediaBangumi -> mediaBangumiSearchResult = mediaBangumiSearchResult.resetPage()
            SearchType.MediaFt -> mediaFtSearchResult = mediaFtSearchResult.resetPage()
            SearchType.BiliUser -> biliUserSearchResult = biliUserSearchResult.resetPage()
        }
    }

    private fun clearResult(type: SearchType) {
        when (type) {
            SearchType.Video -> videoSearchResult = videoSearchResult.clear()
            SearchType.MediaBangumi -> mediaBangumiSearchResult = mediaBangumiSearchResult.clear()
            SearchType.MediaFt -> mediaFtSearchResult = mediaFtSearchResult.clear()
            SearchType.BiliUser -> biliUserSearchResult = biliUserSearchResult.clear()
        }
    }

    private fun resetPages() {
        videoSearchResult = videoSearchResult.resetPage()
        mediaBangumiSearchResult = mediaBangumiSearchResult.resetPage()
        mediaFtSearchResult = mediaFtSearchResult.resetPage()
        biliUserSearchResult = biliUserSearchResult.resetPage()
    }

    private fun clearResults() {
        videoSearchResult = videoSearchResult.clear()
        mediaBangumiSearchResult = mediaBangumiSearchResult.clear()
        mediaFtSearchResult = mediaFtSearchResult.clear()
        biliUserSearchResult = biliUserSearchResult.clear()
    }

    fun loadMore(
        searchType: SearchType,
        ignoreUpdating: Boolean = false,
        expectedToken: Long = tokenOf(searchType)
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            mutexOf(searchType).withLock {
                if (expectedToken != tokenOf(searchType)) return@withLock
                if (!ignoreUpdating && loadingTypes.contains(searchType)) return@withLock
                loadingTypes.add(searchType)

                try {
                    val page = when (searchType) {
                        SearchType.Video -> videoSearchResult.page
                        SearchType.MediaBangumi -> mediaBangumiSearchResult.page
                        SearchType.MediaFt -> mediaFtSearchResult.page
                        SearchType.BiliUser -> biliUserSearchResult.page
                    }

                    logger.fInfo { "Load search result: [keyword=$keyword, type=$searchType, page=$page]" }

                    val response = withTimeout(SEARCH_RESULT_TIMEOUT_MS) {
                        searchRepository.searchType(
                            keyword = keyword,
                            type = searchType,
                            page = page,
                            tid = selectedChildPartition?.tid ?: selectedPartition?.tid,
                            order = selectedOrder,
                            duration = selectedDuration,
                            preferApiType = Prefs.apiType,
                            enableProxy = enableProxySearchResult
                        )
                    }

                    val filteredResponse = if (
                        searchType == SearchType.Video &&
                        dev.aaa1115910.bv.block.BlockManager.isPageEnabled(dev.aaa1115910.bv.block.BlockPage.SearchVideo)
                    ) {
                        response.copy(
                            videos = response.videos.filter { video ->
                                !dev.aaa1115910.bv.block.BlockManager.isBlocked(video.mid)
                            }
                        )
                    } else {
                        response
                    }

                    if (expectedToken != tokenOf(searchType)) return@withLock

                    withContext(Dispatchers.Main) {
                        if (expectedToken != tokenOf(searchType)) return@withContext
                        when (searchType) {
                            SearchType.Video -> {
                                videoSearchResult = videoSearchResult.copy(
                                    videos = (videoSearchResult.videos + filteredResponse.videos).toPersistentList(),
                                    page = filteredResponse.page
                                )
                            }

                            SearchType.MediaBangumi -> {
                                mediaBangumiSearchResult = mediaBangumiSearchResult.copy(
                                    mediaBangumis = (mediaBangumiSearchResult.mediaBangumis + response.pgcs).toPersistentList(),
                                    page = response.page
                                )
                            }

                            SearchType.MediaFt -> {
                                mediaFtSearchResult = mediaFtSearchResult.copy(
                                    mediaFts = (mediaFtSearchResult.mediaFts + response.pgcs).toPersistentList(),
                                    page = response.page
                                )
                            }

                            SearchType.BiliUser -> {
                                biliUserSearchResult = biliUserSearchResult.copy(
                                    biliUsers = (biliUserSearchResult.biliUsers + response.users).toPersistentList(),
                                    page = response.page
                                )
                            }
                        }
                        setLoadState(searchType, LoadState.Success)
                    }
                } catch (t: Throwable) {
                    logger.fException(t) { "Load search result failed: [keyword=$keyword, type=$searchType]" }
                    withContext(Dispatchers.Main) {
                        if (expectedToken != tokenOf(searchType)) return@withContext
                        val count = when (searchType) {
                            SearchType.Video -> videoSearchResult.count
                            SearchType.MediaBangumi -> mediaBangumiSearchResult.count
                            SearchType.MediaFt -> mediaFtSearchResult.count
                            SearchType.BiliUser -> biliUserSearchResult.count
                        }
                        if (count == 0) {
                            setLoadState(searchType, LoadState.Error)
                        }
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        if (expectedToken != tokenOf(searchType)) return@withContext
                        loadingTypes.remove(searchType)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        searchTypeActivation.cancel()
        super.onCleared()
    }

    data class SearchResult(
        val type: SearchType,
        val videos: ImmutableList<SearchTypeResult.Video> = persistentListOf(),
        val mediaBangumis: ImmutableList<SearchTypeResult.Pgc> = persistentListOf(),
        val mediaFts: ImmutableList<SearchTypeResult.Pgc> = persistentListOf(),
        val biliUsers: ImmutableList<SearchTypeResult.User> = persistentListOf(),
        val page: SearchTypePage = SearchTypePage()
    ) {
        val count get() = videos.size + mediaBangumis.size + mediaFts.size + biliUsers.size

        fun resetPage() = copy(page = SearchTypePage())

        fun clear() :SearchResult = copy(
            videos = persistentListOf(),
            mediaBangumis = persistentListOf(),
            mediaFts = persistentListOf(),
            biliUsers = persistentListOf(),
            page = SearchTypePage()
        )

        fun appendSearchResultData(searchTypeResult: SearchTypeResult): SearchResult {
            return when (type) {
                SearchType.Video -> copy(
                    videos = (videos + searchTypeResult.videos).toPersistentList(),
                    page = searchTypeResult.page
                )
                SearchType.MediaBangumi -> copy(
                    mediaBangumis = (mediaBangumis + searchTypeResult.pgcs).toPersistentList(),
                    page = searchTypeResult.page
                )
                SearchType.MediaFt -> copy(
                    mediaFts = (mediaFts + searchTypeResult.pgcs).toPersistentList(),
                    page = searchTypeResult.page
                )
                SearchType.BiliUser -> copy(
                    biliUsers = (biliUsers + searchTypeResult.users).toPersistentList(),
                    page = searchTypeResult.page
                )
            }
        }

    }
}

enum class SearchResultType(
    val type: String,
    private val strRes: Int
) {
    Video(type = "video", strRes = R.string.search_result_type_name_video),
    MediaBangumi(type = "media_bangumi", R.string.search_result_type_name_media_bangumi),
    MediaFt(type = "media_ft", strRes = R.string.search_result_type_name_media_ft),
    BiliUser(type = "bili_user", strRes = R.string.search_result_type_name_bili_user);

    fun getDisplayName(context: Context) = context.getString(strRes)
}
