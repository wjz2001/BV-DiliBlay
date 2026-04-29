package dev.aaa1115910.bv.screen.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.component.RadioMenuSelectDialog
import dev.aaa1115910.bv.component.ifElse
import dev.aaa1115910.bv.component.videocard.SmallVideoCard
import dev.aaa1115910.bv.component.videocard.SmallVideoCardGridHost
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.tv.component.TvAlertDialog
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
    toViewViewModel: ToViewViewModel = koinViewModel(),
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultFocusRequester = remember { FocusRequester() }
    var focusOnTabs by remember { mutableStateOf(true) }
    var pendingBackToTabsFocus by remember { mutableStateOf(false) }
    var readyFocusTargetTabIndex by remember { mutableStateOf<Int?>(null) }

    var pendingRemovalAid by remember { mutableStateOf<Long?>(null) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showDeleteWatchedDialog by remember { mutableStateOf(false) }
    var openDeleteWatchedDialogOnKeyUp by remember { mutableStateOf(false) }
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
        toViewViewModel.histories.removeAll { it.timeString == "已看完" }
    }

    val groupedHistories by remember {
        derivedStateOf {
            val unwatched = ArrayList<VideoCardData>(toViewViewModel.histories.size)
            val watched = ArrayList<VideoCardData>()

            toViewViewModel.histories.forEach { item ->
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

    LaunchedEffect(Unit) {
        toViewViewModel.uiEvent.collect { event ->
            when (event) {
                is UiEffect.ShowToast -> {
                    event.message.toast(context)
                }
            }
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            Triple(
                gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
                toViewViewModel.histories.size,
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
    val searchFocusedLineColor = C.primary
    val searchUnfocusedLineColor = C.onSurfaceVariant
    val density = LocalDensity.current
    val tabLabelFontSize = MaterialTheme.typography.labelLarge.fontSize
    val filterIconSizeDp = with(density) {
        tabLabelFontSize.toDp()
    }

    fun requestTabsFocus() {
        pendingBackToTabsFocus = true
        defaultFocusRequester.requestFocus()
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingBackToTabsFocus = false
            readyFocusTargetTabIndex = null
        }
    }

    LaunchedEffect(selectedTabIndex) {
        readyFocusTargetTabIndex = null
    }

    LaunchedEffect(pendingBackToTabsFocus, readyFocusTargetTabIndex, selectedTabIndex) {
        if (pendingBackToTabsFocus && readyFocusTargetTabIndex == selectedTabIndex) {
            pendingBackToTabsFocus = false
            defaultFocusRequester.requestFocus()
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

                if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                    if (focusOnTabs) {
                        onBack()
                    } else {
                        requestTabsFocus()
                    }
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        TabRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .onFocusChanged { state ->
                    focusOnTabs = state.hasFocus
                    if (state.hasFocus) {
                        pendingBackToTabsFocus = false
                    }
                }
                .focusRestorer(defaultFocusRequester),
            selectedTabIndex = selectedTabIndex,
            separator = { Spacer(modifier = Modifier.width(12.dp)) },
            indicator = { tabPositions, doesTabRowHaveFocus ->
                tabPositions.getOrNull(selectedTabIndex)?.let { currentTabPosition ->
                    TabRowDefaults.PillIndicator(
                        currentTabPosition = currentTabPosition,
                        doesTabRowHaveFocus = doesTabRowHaveFocus,
                        activeColor = MaterialTheme.colorScheme.primary,
                        inactiveColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
            }
        ) {
            tabTitles.forEachIndexed { index, title ->
                var longPressTriggered by remember(index) { mutableStateOf(false) }
                Tab(
                    colors = TabDefaults.pillIndicatorTabColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                        selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                        focusedSelectedContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .ifElse(
                            index == selectedTabIndex,
                            Modifier
                                .focusRequester(defaultFocusRequester)
                                .onGloballyPositioned { readyFocusTargetTabIndex = index }
                        )
                        .onPreviewKeyEvent { event ->
                            val isConfirmKey =
                                event.key == Key.DirectionCenter ||
                                        event.key == Key.Enter ||
                                        event.key == Key.Spacebar

                            if (!isConfirmKey) return@onPreviewKeyEvent false

                            if (event.type == KeyEventType.KeyDown) {
                                if (event.nativeKeyEvent.isLongPress) {
                                    if (!longPressTriggered) {
                                        longPressTriggered = true
                                        selectedTabIndex = index
                                        if (index == 1) {
                                            openDeleteWatchedDialogOnKeyUp = true
                                        } else {
                                            openDeleteWatchedDialogOnKeyUp = false
                                            val isSearching =
                                                tabQueryStates[0]?.debouncedQuery?.isNotBlank() == true
                                            if (isSearching) {
                                                clearTabQuery(0)
                                            } else {
                                                showSearchDialog = true
                                                searchDialogTabIndex = 0
                                            }
                                        }
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                return@onPreviewKeyEvent false
                            }

                            if (event.type == KeyEventType.KeyUp && longPressTriggered) {
                                longPressTriggered = false
                                if (openDeleteWatchedDialogOnKeyUp && index == 1) {
                                    openDeleteWatchedDialogOnKeyUp = false
                                    showDeleteWatchedDialog = true
                                }
                                return@onPreviewKeyEvent true
                            }

                            false
                        },
                    selected = selectedTabIndex == index,
                    onFocus = {
                        if (selectedTabIndex != index) {
                            selectedTabIndex = index
                        }
                    },
                    onClick = { selectedTabIndex = index }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (index == 0 && tabQueryStates[0]?.debouncedQuery?.isNotBlank() == true) {
                                Icon(
                                    modifier = Modifier.size(filterIconSizeDp),
                                    imageVector = Icons.Rounded.FilterList,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            Text(
                                text = title,
                                color = LocalContentColor.current,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        SmallVideoCardGridHost(
            modifier = modifier,
            state = gridState,
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (visibleItems.isNotEmpty()) {
                items(
                    items = visibleItems,
                    key = { it.avid },
                    contentType = { if (selectedTabIndex == 0) "to_view_unwatched" else "to_view_watched" }
                ) { item ->
                    Box(contentAlignment = Alignment.Center) {
                        SmallVideoCard(
                            data = item,
                            delToView = true,
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
                            onGoToDetailPage = {
                                VideoInfoActivity.actionStart(
                                    context = context,
                                    fromController = true,
                                    aid = item.avid,
                                    epid = item.epId
                                )
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
