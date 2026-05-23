package dev.aaa1115910.bv.screen.search

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.repositories.SearchType
import dev.aaa1115910.biliapi.repositories.SearchTypeResult
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.video.SeasonInfoActivity
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.component.LoadingTip
import dev.aaa1115910.bv.component.SearchTypeTopNavItem
import dev.aaa1115910.bv.component.TopNav
import dev.aaa1115910.bv.wjzfocus.WjzFocusHost
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryId
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryResolution
import dev.aaa1115910.bv.wjzfocus.WjzFocusHostExit
import dev.aaa1115910.bv.wjzfocus.WjzFocusItemKey
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.component.BvLazyFocusItemTarget
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.wjzFocusable
import dev.aaa1115910.bv.component.videocard.SmallVideoCardGridHost
import dev.aaa1115910.bv.component.videocard.SeasonCard
import dev.aaa1115910.bv.component.videocard.SmallVideoCard
import dev.aaa1115910.bv.component.rememberTvGridFocusTarget
import dev.aaa1115910.bv.entity.carddata.SeasonCardData
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.C

import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.focusedScale
import dev.aaa1115910.bv.util.formatHourMinSec
import dev.aaa1115910.bv.util.removeHtmlTags
import dev.aaa1115910.bv.util.rememberTvImageRequest
import dev.aaa1115910.bv.util.toWanString
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.common.LoadState
import dev.aaa1115910.bv.viewmodel.search.SearchResultViewModel
import dev.aaa1115910.bv.viewmodel.SmallVideoCardItemUiState
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.koin.androidx.compose.koinViewModel

private val SearchResultContentNodeId = WjzFocusNodeId("search/result/content")
private val SearchResultTopNavNodeId = WjzFocusNodeId("search/result/top-nav")
private val SearchResultRootScopeId = WjzFocusScopeId("search/result/root")
private val SearchResultTopNavEntryId = WjzFocusEntryId("search/result/top-nav")

@Composable
fun SearchResultScreen(
    modifier: Modifier = Modifier,
    keyword: String? = null,
    enableProxy: Boolean? = null,
    contentEntryFocusRequester: FocusRequester? = null,
    onContentEntryReady: () -> Unit = {},
    onBackToInput: (() -> Unit)? = null,
    searchResultViewModel: SearchResultViewModel = koinViewModel(),
    toViewViewModel: ToViewViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusCoordinator = LocalWjzFocusCoordinator.current
    val logger = KotlinLogging.logger { }
    val tabRowFocusRequester = contentEntryFocusRequester
    val topNavContentFocusRequester = contentEntryFocusRequester ?: FocusRequester.Default
    var contentReadySearchType by remember { mutableStateOf<SearchTypeTopNavItem?>(null) }

    var rowSize by remember { mutableIntStateOf(4) }

    val focusedSearchType = searchResultViewModel.focusedSearchType
    val activeSearchType = searchResultViewModel.activeSearchType
    val gridStateMap = remember {
        mutableMapOf<SearchType, LazyGridState>()
    }
    val gridState = gridStateMap.getOrPut(activeSearchType) {
        LazyGridState()
    }

    val searchResult = when (activeSearchType) {
        SearchType.Video -> searchResultViewModel.videoSearchResult
        SearchType.MediaBangumi -> searchResultViewModel.mediaBangumiSearchResult
        SearchType.MediaFt -> searchResultViewModel.mediaFtSearchResult
        SearchType.BiliUser -> searchResultViewModel.biliUserSearchResult
    }
    val loadStateMap by searchResultViewModel.loadStateMap.collectAsStateWithLifecycle()
    val loadState = loadStateMap[activeSearchType] ?: LoadState.Idle

    var showFilter by remember { mutableStateOf(false) }
    var focusOnContent by remember { mutableStateOf(false) }
    var filterInitialized by remember { mutableStateOf(false) }

    val isVideoSearchViaWebApi by remember {
        derivedStateOf {
            activeSearchType == SearchType.Video &&
                    Prefs.apiType == ApiType.Web
        }
    }

    val selectedOrder = searchResultViewModel.selectedOrder
    val selectedDuration = searchResultViewModel.selectedDuration
    val selectedPartition = searchResultViewModel.selectedPartition
    val selectedChildPartition = searchResultViewModel.selectedChildPartition

    val onClickResult: (SearchTypeResult.SearchTypeResultItem) -> Unit = { resultItem ->
        when (resultItem) {
            is SearchTypeResult.Video -> {
                VideoInfoActivity.actionStart(
                    context = context,
                    aid = resultItem.aid
                )
            }

            is SearchTypeResult.Pgc -> {
                SeasonInfoActivity.actionStart(
                    context = context,
                    seasonId = resultItem.seasonId,
                    proxyArea = ProxyArea.checkProxyArea(resultItem.title)
                )
            }

            is SearchTypeResult.User -> {
                UpInfoActivity.actionStart(
                    context = context,
                    mid = resultItem.mid,
                    name = resultItem.name
                )
            }

            else -> {}
        }
    }

    fun requestTopNavFocus(): Boolean {
        val coordinator = focusCoordinator ?: return false
        coordinator.switchLayer(WjzFocusLayer.TopNav)
        return coordinator.restoreActiveLayer(SearchResultRootScopeId)
    }
    val backToTabRow: () -> Unit = { requestTopNavFocus() }

    fun SearchTypeTopNavItem.toSearchType(): SearchType = when (this) {
        SearchTypeTopNavItem.Video -> SearchType.Video
        SearchTypeTopNavItem.MediaBangumi -> SearchType.MediaBangumi
        SearchTypeTopNavItem.MediaFt -> SearchType.MediaFt
        SearchTypeTopNavItem.BiliUser -> SearchType.BiliUser
    }

    fun SearchType.toTopNavItem(): SearchTypeTopNavItem = when (this) {
        SearchType.Video -> SearchTypeTopNavItem.Video
        SearchType.MediaBangumi -> SearchTypeTopNavItem.MediaBangumi
        SearchType.MediaFt -> SearchTypeTopNavItem.MediaFt
        SearchType.BiliUser -> SearchTypeTopNavItem.BiliUser
    }

    LaunchedEffect(keyword, enableProxy) {
        if (keyword != null && enableProxy != null) {
            if (keyword.isNotBlank()) {
                searchResultViewModel.onKeywordChanged(
                    newKeyword = keyword,
                    enableProxy = enableProxy
                )
            }
        } else {
            val intent = (context as Activity).intent
            if (intent.hasExtra("keyword")) {
                val intentKeyword = intent.getStringExtra("keyword") ?: ""
                val intentEnableProxy = intent.getBooleanExtra("enableProxy", false)
                if (intentKeyword == "") context.finish()
                searchResultViewModel.onKeywordChanged(
                    newKeyword = intentKeyword,
                    enableProxy = intentEnableProxy
                )
            } else {
                context.finish()
            }
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            toViewViewModel.uiEvent.collect { event ->
                when (event) {
                    is UiEffect.ShowToast -> {
                        event.message.toast(context)
                    }
                }
            }
        }
    }

    LaunchedEffect(activeSearchType) {
        searchResultViewModel.ensureLoaded(activeSearchType)
    }

    LaunchedEffect(activeSearchType) {
        rowSize = when (activeSearchType) {
            SearchType.Video -> 4
            SearchType.MediaBangumi, SearchType.MediaFt -> 6
            SearchType.BiliUser -> 3
        }
    }

    LaunchedEffect(
        selectedOrder, selectedDuration, selectedPartition, selectedChildPartition
    ) {
        if (!filterInitialized) {
            filterInitialized = true
            return@LaunchedEffect
        }
        if (searchResultViewModel.keyword.isBlank()) return@LaunchedEffect
        logger.fInfo { "Start update search result because filter updated" }
        searchResultViewModel.updateActiveType()
    }


    LaunchedEffect(gridState, searchResult) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .filter { index ->
                index != null && index >= searchResult.count - 20
            }
            .collect {
                searchResultViewModel.loadMore(searchResult.type)
            }
    }

    WjzFocusHost(
        modifier = modifier,
        layer = WjzFocusLayer.Content,
        scopeId = SearchResultRootScopeId,
        exits = listOf(
            WjzFocusHostExit(FocusDirection.Up, SearchResultTopNavEntryId)
        ),
        onHostExit = { request ->
            when (request.direction) {
                FocusDirection.Up -> {
                    if (requestTopNavFocus()) {
                        WjzFocusEntryResolution.Cancel
                    } else {
                        WjzFocusEntryResolution.Reject
                    }
                }

                else -> WjzFocusEntryResolution.Reject
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.onKeyEvent {
                if (it.key == Key.Menu) {
                    if (it.type == KeyEventType.KeyDown) return@onKeyEvent true
                    if (isVideoSearchViaWebApi) {
                        showFilter = true
                        return@onKeyEvent true
                    }
                }
                false
            },
            topBar = {
                Box(
                    modifier = Modifier.padding(start = 48.dp, top = 24.dp, bottom = 8.dp, end = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = searchResultViewModel.keyword,
                            fontSize = 24.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TopNav(
                            modifier = Modifier.weight(2f),
                            leadingContent = {},
                            items = SearchTypeTopNavItem.entries,
                            selectedItem = focusedSearchType.toTopNavItem(),
                            defaultFocusRequester = tabRowFocusRequester,
                            contentFocusRequester = topNavContentFocusRequester,
                            contentFocusReadyKey = contentReadySearchType,
                            focusNodeId = SearchResultTopNavNodeId,
                            onContentFocusRequested = { nav ->
                                val target = (nav as SearchTypeTopNavItem).toSearchType()
                                if (target != activeSearchType) {
                                    searchResultViewModel.onSearchTypeClicked(target)
                                }
                            },
                            onSelectedChanged = { nav ->
                                val target = (nav as SearchTypeTopNavItem).toSearchType()
                                searchResultViewModel.onSearchTypeFocused(target)
                            },
                            onClick = { nav ->
                                val target = (nav as SearchTypeTopNavItem).toSearchType()
                                searchResultViewModel.onSearchTypeClicked(target)
                            }
                        )
                        Text(
                            text = (if (isVideoSearchViaWebApi) "菜单键打开筛选 | " else "") +
                                    stringResource(R.string.load_data_count, searchResult.count),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        ) { innerPadding ->
            if (onBackToInput != null) {
                BackHandler { onBackToInput() }
            } else {
                BackHandler(focusOnContent) { backToTabRow() }
            }

            Column(
                modifier = Modifier.padding(innerPadding)
            ) {
                val currentItems: List<SearchTypeResult.SearchTypeResultItem> = when (searchResult.type) {
                    SearchType.Video -> searchResult.videos
                    SearchType.MediaBangumi -> searchResult.mediaBangumis
                    SearchType.MediaFt -> searchResult.mediaFts
                    SearchType.BiliUser -> searchResult.biliUsers
                }

                if (currentItems.isEmpty() && loadState != LoadState.Idle) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wjzFocusable(
                                nodeId = SearchResultContentNodeId,
                                layer = WjzFocusLayer.Content,
                                fallback = true,
                                onFocusChanged = { focusOnContent = it }
                            )
                            .onGloballyPositioned {
                                contentReadySearchType = searchResult.type.toTopNavItem()
                                onContentEntryReady()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when (loadState) {
                            LoadState.Loading -> LoadingTip()
                            LoadState.Error -> Text(
                                text = "搜索结果加载失败，请稍后重试",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LoadState.Success -> Text(
                                text = "暂无搜索结果",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LoadState.Idle -> Unit
                        }
                    }
                } else {
                    SmallVideoCardGridHost(
                        modifier = Modifier
                            .onFocusChanged { focusOnContent = it.hasFocus },
                        state = gridState,
                        columns = GridCells.Fixed(rowSize),
                        contentPadding = PaddingValues(24.dp),
                        nodeIdPrefix = "search/${searchResult.type.toTopNavItem()}",
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        entryFocusRequester = null,
                        upFocusRequester = tabRowFocusRequester,
                        onEntryFocusReady = {
                            contentReadySearchType = searchResult.type.toTopNavItem()
                            onContentEntryReady()
                        },
                        focusItemCount = currentItems.size,
                        focusItemKeys = currentItems.map { item ->
                            when (item) {
                                is SearchTypeResult.Video -> WjzFocusItemKey("video_${item.aid}")
                                is SearchTypeResult.Pgc -> WjzFocusItemKey("pgc_${item.seasonId}")
                                is SearchTypeResult.User -> WjzFocusItemKey("user_${item.mid}")
                                else -> error("Unsupported search result focus item key")
                            }
                        },
                        focusColumnCount = rowSize
                    ) { cardUiStateFor ->
                        itemsIndexed(
                            items = currentItems,
                            key = { _, item ->
                                when (item) {
                                    is SearchTypeResult.Video -> "video_${item.aid}"
                                    is SearchTypeResult.Pgc -> "pgc_${item.seasonId}"
                                    is SearchTypeResult.User -> "user_${item.mid}"
                                    else -> error("Unsupported search result item key")
                                }
                            }
                        ) { index, item ->
                            val focusTarget = rememberTvGridFocusTarget(index)
                            SearchResultListItem(
                                modifier = focusTarget?.modifier ?: Modifier,
                                focusTarget = focusTarget,
                                searchResult = item,
                                uiState = (item as? SearchTypeResult.Video)?.let { cardUiStateFor(it.aid) },
                                onClick = { onClickResult(item) },
                                onAddWatchLater = { aid ->
                                    toViewViewModel.addToView(aid)
                                },
                                onGoToUpPage = { mid, upName ->
                                    UpInfoActivity.actionStart(context, mid, upName)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    SearchResultVideoFilter(
        show = showFilter,
        sourceScopeId = SearchResultRootScopeId,
        onHideFilter = { showFilter = false },
        selectedOrder = selectedOrder,
        selectedDuration = selectedDuration,
        selectedPartition = selectedPartition,
        selectedChildPartition = selectedChildPartition,
        onSelectedOrderChange = { searchResultViewModel.selectedOrder = it },
        onSelectedDurationChange = { searchResultViewModel.selectedDuration = it },
        onSelectedPartitionChange = { searchResultViewModel.selectedPartition = it },
        onSelectedChildPartitionChange = {
            searchResultViewModel.selectedChildPartition = it
        }
    )
}

@Composable
fun UpCard(
    modifier: Modifier = Modifier,
    face: String,
    sign: String,
    username: String,
    onFocusChange: (hasFocus: Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val faceRequest = rememberTvImageRequest(
        url = face,
        widthDp = 48.dp,
        heightDp = 48.dp
    )

    Surface(
        modifier = modifier
            .onFocusChanged { onFocusChange(it.hasFocus) }
            .size(280.dp, 80.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            pressedContainerColor = MaterialTheme.colorScheme.surface
        ),
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.large),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 3.dp, color = C.selectedBorder),
                shape = MaterialTheme.shapes.large
            )
        ),
        onClick = onClick,
        onLongClick = onLongClick
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .padding(start = 12.dp, end = 8.dp)
                    .size(48.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                AsyncImage(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    model = faceRequest,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds
                )
            }
            Column {
                Text(
                    text = username,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = sign,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SearchResultListItem(
    modifier: Modifier = Modifier,
    focusTarget: BvLazyFocusItemTarget? = null,
    searchResult: SearchTypeResult.SearchTypeResultItem,
    uiState: SmallVideoCardItemUiState?,
    onClick: () -> Unit,
    onAddWatchLater: ((Long) -> Unit),
    onGoToUpPage: ((Long, String) -> Unit),
) {
    when (searchResult) {
        is SearchTypeResult.Video -> {
            SmallVideoCard(
                focusTarget = focusTarget,
                uiState = uiState,
                data = VideoCardData(
                    avid = searchResult.aid,
                    title = searchResult.title.removeHtmlTags(),
                    cover = searchResult.cover,
                    upMid = searchResult.mid,
                    playString = searchResult.play.takeIf { it != -1 }.toWanString(),
                    danmakuString = searchResult.danmaku.takeIf { it != -1 }.toWanString(),
                    timeString = (searchResult.duration * 1000L).formatHourMinSec(),
                    upName = searchResult.author,
                    pubTime = searchResult.pubTime
                ),
                onClick = onClick,
                onAddWatchLater = { onAddWatchLater(searchResult.aid) },
                onGoToUpPage = { onGoToUpPage(searchResult.mid, searchResult.author) }
            )
        }

        is SearchTypeResult.Pgc -> {
            SeasonCard(
                modifier = modifier,
                data = SeasonCardData(
                    seasonId = searchResult.seasonId,
                    title = searchResult.title.removeHtmlTags(),
                    cover = searchResult.cover,
                    rating = stringResource(R.string.rating_format, searchResult.star)
                ),
                onClick = onClick,
                onFocus = {}
            )
        }

        is SearchTypeResult.User -> {
            UpCard(
                modifier = modifier.focusedScale(0.95f),
                face = searchResult.avatar,
                sign = searchResult.sign,
                username = searchResult.name,
                onFocusChange = {},
                onClick = onClick
            )
        }

        else -> Unit
    }
}

fun SearchType.getDisplayName(context: Context) = when (this) {
    SearchType.Video -> context.getString(R.string.search_result_type_name_video)
    SearchType.MediaBangumi -> context.getString(R.string.search_result_type_name_media_bangumi)
    SearchType.MediaFt -> context.getString(R.string.search_result_type_name_media_ft)
    SearchType.BiliUser -> context.getString(R.string.search_result_type_name_bili_user)
}

@Preview
@Composable
fun UpCardPreview() {
    BVTheme {
        UpCard(
            face = "",
            sign = "一只业余做翻译的Klei迷，动态区UP（自称），缺氧官中反馈可私信",
            username = "username",
            onFocusChange = {},
            onClick = {}
        )
    }
}
