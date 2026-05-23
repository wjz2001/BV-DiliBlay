package dev.aaa1115910.bv.screen.main.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.wjzfocus.WjzFocusItemKey
import dev.aaa1115910.bv.component.BvTabLabel
import dev.aaa1115910.bv.component.BvUnderlineTabRow
import dev.aaa1115910.bv.component.MainTopBarContainer
import dev.aaa1115910.bv.component.MainTopTabDefaults
import dev.aaa1115910.bv.component.MainTopTabSeparator
import dev.aaa1115910.bv.component.RadioMenuSelectDialog
import dev.aaa1115910.bv.component.videocard.SmallVideoCard
import dev.aaa1115910.bv.component.videocard.SmallVideoCardGridHost
import dev.aaa1115910.bv.component.rememberTvGridFocusTarget
import dev.aaa1115910.bv.entity.VideoSource
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.component.TvAlertDialog
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private class TabQueryState {
    var rawQuery by mutableStateOf("")
    var debouncedQuery by mutableStateOf("")
    var debounceJob: Job? = null
}

@Composable
fun ToViewScreen(
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    active: Boolean = true,
    activationSerial: Long = 0L,
    refreshSerial: Long = 0L,
    toViewViewModel: ToViewViewModel = koinViewModel(),
    contentEntryFocusRequester: FocusRequester? = null,
    tabFocusRequester: FocusRequester? = null,
    onContentEntryReady: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val histories by toViewViewModel.histories.collectAsStateWithLifecycle()
    val toViewTabFocusRequester = tabFocusRequester
    val toViewContentEntryFocusRequester = contentEntryFocusRequester
    var readyFocusTargetTabIndex by remember { mutableStateOf<Int?>(null) }
    var contentReadyTabIndex by remember { mutableStateOf<Int?>(null) }

    var pendingRemovalAid by remember { mutableStateOf<Long?>(null) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showDeleteWatchedDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchDialogTabIndex by remember { mutableStateOf<Int?>(null) }
    var searchFieldHasFocus by remember { mutableStateOf(false) }

    val deleteDialogItems = remember { listOf("是", "否") }
    val tabQueryStates = remember { mutableStateMapOf<Int, TabQueryState>() }

    fun getQueryState(index: Int): TabQueryState {
        return tabQueryStates.getOrPut(index) { TabQueryState() }
    }

    fun clearTabQuery(index: Int) {
        val st = tabQueryStates[index] ?: return
        st.debounceJob?.cancel()
        st.debounceJob = null
        st.rawQuery = ""
        st.debouncedQuery = ""
    }

    fun onTabQueryChange(index: Int, newText: String) {
        val st = getQueryState(index)
        st.rawQuery = newText
        st.debounceJob?.cancel()
        st.debounceJob = scope.launch {
            delay(900)
            st.debouncedQuery = st.rawQuery
        }
    }

    fun onTabSearchAction(index: Int) {
        val st = getQueryState(index)
        st.debounceJob?.cancel()
        st.debounceJob = null
        st.debouncedQuery = st.rawQuery
    }

    fun closeSearchDialog(apply: Boolean) {
        val index = searchDialogTabIndex
        if (apply && index != null) {
            onTabSearchAction(index)
        }
        showSearchDialog = false
        searchDialogTabIndex = null
    }

    fun removeWatchedFromLocalList() {
        toViewViewModel.removeWatchedFromLocalList()
    }

    val groupedHistories by remember {
        derivedStateOf {
            val unwatched = ArrayList<VideoCardData>(histories.size)
            val watched = ArrayList<VideoCardData>()

            histories.forEach { item ->
                if (item.timeString == "已看完") {
                    watched.add(item)
                } else {
                    unwatched.add(item)
                }
            }

            unwatched to watched
        }
    }
    val unwatched = groupedHistories.first
    val watched = groupedHistories.second
    val visibleItems by remember(selectedTabIndex, unwatched, watched, tabQueryStates) {
        derivedStateOf {
            if (selectedTabIndex == 1) return@derivedStateOf watched
            val q = tabQueryStates[0]?.debouncedQuery?.trim().orEmpty()
            if (q.isBlank()) {
                unwatched
            } else {
                unwatched.filter { it.title.contains(q, ignoreCase = true) }
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

    DisposableEffect(active) {
        onDispose {
            toViewViewModel.cancelOngoingLoads()
        }
    }

    LaunchedEffect(active, activationSerial) {
        if (!active) return@LaunchedEffect
        if (activationSerial == 0L) return@LaunchedEffect
        withFrameNanos { }
        toViewViewModel.ensureLoaded(showErrorToast = false)
    }

    LaunchedEffect(active, refreshSerial) {
        if (!active) return@LaunchedEffect
        if (refreshSerial == 0L) return@LaunchedEffect
        gridState.scrollToItem(0)
        toViewViewModel.refreshSnapshotIncrementally()
    }

    LaunchedEffect(gridState, active) {
        if (!active) return@LaunchedEffect
        snapshotFlow {
            Triple(
                gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
                histories.size,
                toViewViewModel.noMore
            )
        }
            .distinctUntilChanged()
            .filter { (index, size, noMore) ->
                index != null &&
                        !noMore &&
                        index >= size - 20
            }
            .collect {
                toViewViewModel.update()
            }
    }

    val tabTitles = remember { listOf("未看完", "已看完") }
    val tabItems = remember(tabTitles) { tabTitles.indices.toList() }
    val searchFocusedLineColor = C.primary
    val searchUnfocusedLineColor = C.onSurfaceVariant
    DisposableEffect(Unit) {
        onDispose {
            readyFocusTargetTabIndex = null
        }
    }

    LaunchedEffect(selectedTabIndex) {
        readyFocusTargetTabIndex = null
        contentReadyTabIndex = null
    }

    LaunchedEffect(readyFocusTargetTabIndex, selectedTabIndex, active) {
        if (!active) return@LaunchedEffect
        if (readyFocusTargetTabIndex == selectedTabIndex) {
            onContentEntryReady()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                if (showDeleteWatchedDialog && it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                    showDeleteWatchedDialog = false
                    return@onPreviewKeyEvent true
                }
                if (showSearchDialog && it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                    closeSearchDialog(apply = true)
                    return@onPreviewKeyEvent true
                }

                false
            }
    ) {
        MainTopBarContainer {
            BvUnderlineTabRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MainTopTabDefaults.TabRowHorizontalPadding),
                items = tabItems,
                selectedItem = selectedTabIndex,
                entryFocusItem = selectedTabIndex,
                itemKey = { it },
                defaultFocusRequester = toViewTabFocusRequester,
                onDefaultFocusReady = { readyKey ->
                    readyFocusTargetTabIndex = readyKey as? Int
                },
                separator = { MainTopTabSeparator() },
                onSelectedChanged = { index ->
                    if (selectedTabIndex != index) {
                        selectedTabIndex = index
                    }
                },
                onClick = { index -> selectedTabIndex = index },
                onLongClick = { index ->
                    selectedTabIndex = index
                    if (index == 1) {
                        showDeleteWatchedDialog = true
                    } else {
                        val isSearching =
                            tabQueryStates[0]?.debouncedQuery?.isNotBlank() == true
                        if (isSearching) {
                            clearTabQuery(0)
                        } else {
                            showSearchDialog = true
                            searchDialogTabIndex = 0
                        }
                    }
                    true
                },
                contentFocusRequester = toViewContentEntryFocusRequester,
                contentFocusReadyKey = contentReadyTabIndex,
                onContentFocusRequested = { index ->
                    if (selectedTabIndex != index) {
                        selectedTabIndex = index
                    }
                },
                onUp = {
                    onBack()
                    true
                },
                backFocusEnabled = active,
                autoRequestEntryFocus = false,
                tabContent = { index, _, _ ->
                    val title = tabTitles[index]

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(MainTopTabDefaults.TabContentHeight),
                            contentAlignment = Alignment.Center
                        ) {
                            BvTabLabel(
                                text = title,
                                icon = { iconSize ->
                                    Icon(
                                        modifier = Modifier.size(iconSize),
                                        imageVector = Icons.Rounded.FilterList,
                                        contentDescription = null
                                    )
                                },
                                showIcon = index == 0 && tabQueryStates[0]?.debouncedQuery?.isNotBlank() == true
                            )
                        }
                },
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        SmallVideoCardGridHost(
            modifier = modifier,
            state = gridState,
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(24.dp),
            nodeIdPrefix = "to-view/${tabTitles[selectedTabIndex]}",
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            focusItemCount = visibleItems.size,
            focusItemKeys = visibleItems.map { WjzFocusItemKey("Long:${it.avid}") },
            focusColumnCount = 4,
            entryFocusRequester = toViewContentEntryFocusRequester,
            upFocusRequester = toViewTabFocusRequester,
            onEntryFocusReady = {
                contentReadyTabIndex = selectedTabIndex
                onContentEntryReady()
            }
        ) { cardUiStateFor ->
            if (visibleItems.isNotEmpty()) {
                itemsIndexed(
                    items = visibleItems,
                    key = { _, item -> item.avid },
                    contentType = { _, _ -> if (selectedTabIndex == 0) "to_view_unwatched" else "to_view_watched" }
                ) { index, item ->
                    Box(contentAlignment = Alignment.Center) {
                        SmallVideoCard(
                            focusTarget = rememberTvGridFocusTarget(index),
                            uiState = cardUiStateFor(item.avid),
                            data = item,
                            pendingRemoval = pendingRemovalAid == item.avid,
                            onPendingRemovalFocusLost = {
                                if (pendingRemovalAid == item.avid) {
                                    pendingRemovalAid = null
                                    toViewViewModel.removeFromLocalList(item.avid)
                                }
                            },
                            onClick = {
                                VideoInfoActivity.actionStart(
                                    context = context,
                                    aid = item.avid,
                                    epid = item.epId,
                                    source = if (item.epId != null) VideoSource.Pgc else VideoSource.Ugc,
                                    proxyArea = ProxyArea.checkProxyArea(item.title)
                                )
                            },
                            onAddWatchLater = {
                                scope.launch {
                                    if (toViewViewModel.deleteToViewRemote(item.avid)) {
                                        pendingRemovalAid = item.avid
                                    }
                                }
                            },
                            onGoToUpPage = item.upMid?.let {
                                { UpInfoActivity.actionStart(context, it, item.upName) }
                            }
                        )
                    }
                }
            } else {
                item(
                    key = if (selectedTabIndex == 0) "to_view_empty_unwatched" else "to_view_empty_watched",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    EmptyTip()
                }
            }
        }
    }

    RadioMenuSelectDialog(
        visible = showDeleteWatchedDialog,
        onDismissRequest = {
            showDeleteWatchedDialog = false
        },
        title = "全部删除",
        items = deleteDialogItems,
        selected = { it == "否" },
        onSelect = { item ->
            if (item == "是") {
                scope.launch {
                    if (toViewViewModel.deleteToViewRemote(aid = 0L, viewed = true)) {
                        removeWatchedFromLocalList()
                    }
                }
            }
            showDeleteWatchedDialog = false
        },
        text = { it },
        itemKey = { it },
        defaultFocusKey = "否"
    )

    if (showSearchDialog) {
        val tabIndex = searchDialogTabIndex
        if (tabIndex != null) {
            val st = getQueryState(tabIndex)

            TvAlertDialog(
                onDismissRequest = { closeSearchDialog(apply = true) },
                title = { Text(text = "在 未看完 中搜索") },
                text = {
                    TextField(
                        modifier = Modifier
                            .width(600.dp)
                            .onFocusChanged { searchFieldHasFocus = it.hasFocus }
                            .drawWithContent {
                                drawContent()
                                val stroke = 3.dp.toPx()
                                val y = size.height - stroke / 2f
                                drawLine(
                                    color = if (searchFieldHasFocus) {
                                        searchFocusedLineColor
                                    } else {
                                        searchUnfocusedLineColor
                                    },
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = stroke
                                )
                            },
                        value = st.rawQuery,
                        onValueChange = { onTabQueryChange(tabIndex, it) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 26.sp, lineHeight = 30.sp),
                        shape = RectangleShape,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onTabSearchAction(tabIndex) })
                    )
                },
                confirmButton = { },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            )
        }
    }
}
