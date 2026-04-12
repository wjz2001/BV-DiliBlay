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
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.FavoriteFolderMetadata
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.component.ifElse
import dev.aaa1115910.bv.component.videocard.SmallVideoCard
import dev.aaa1115910.bv.component.videocard.SmallVideoCardGridHost
import dev.aaa1115910.bv.component.videocard.rememberGridRowWrapModifier
import dev.aaa1115910.bv.tv.component.TvAlertDialog
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.requestFocus
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.user.FavoriteViewModel
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private class FolderQueryState {
    var rawQuery by mutableStateOf("")
    var debouncedQuery by mutableStateOf("")
    var debounceJob: Job? = null
}

@Composable
fun FavoriteScreen(
    modifier: Modifier = Modifier,
    lazyGridState: LazyGridState = rememberLazyGridState(),
    favoriteViewModel: FavoriteViewModel = koinViewModel(),
    toViewViewModel: ToViewViewModel = koinViewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultFocusRequester = remember { FocusRequester() }
    var focusOnTabs by remember { mutableStateOf(true) }
    var pendingBackToTabsFocus by remember { mutableStateOf(false) }
    var readyFocusTargetFolderId by remember { mutableStateOf<Long?>(null) }

    // 每个收藏夹自己的搜索状态（切换收藏夹不清空；离开本页面才清空）
    val folderQueryStates = remember { mutableStateMapOf<Long, FolderQueryState>() }

    fun getQueryState(folderId: Long): FolderQueryState {
        return folderQueryStates.getOrPut(folderId) { FolderQueryState() }
    }

    fun clearFolderQuery(folderId: Long) {
        val st = folderQueryStates[folderId] ?: return
        st.debounceJob?.cancel()
        st.debounceJob = null
        st.rawQuery = ""
        st.debouncedQuery = ""
    }

    fun onFolderQueryChange(folderId: Long, newText: String) {
        val st = getQueryState(folderId)
        st.rawQuery = newText
        st.debounceJob?.cancel()
        st.debounceJob = scope.launch {
            delay(900)
            st.debouncedQuery = st.rawQuery
        }
    }

    fun onFolderSearchAction(folderId: Long) {
        val st = getQueryState(folderId)
        st.debounceJob?.cancel()
        st.debounceJob = null
        st.debouncedQuery = st.rawQuery
    }

    val folderList = favoriteViewModel.favoriteFolderMetadataList
    val focusedFolderId = favoriteViewModel.focusedFolderId
    val activeFolderId = favoriteViewModel.activeFolderId

    fun resolveFolderIdInList(candidate: Long?): Long? {
        if (candidate == null) return null
        return candidate.takeIf { id -> folderList.any { it.id == id } }
    }

    val currentFolderId by remember(folderList, favoriteViewModel.currentFavoriteFolderMetadata) {
        derivedStateOf {
            resolveFolderIdInList(favoriteViewModel.currentFavoriteFolderMetadata?.id)
        }
    }

    val displayFocusedFolderId by remember(
        focusOnTabs,
        focusedFolderId,
        currentFolderId,
        folderList
    ) {
        derivedStateOf {
            val focusedFolderIdInList = resolveFolderIdInList(focusedFolderId)

            when {
                focusOnTabs ->
                    focusedFolderIdInList
                        ?: currentFolderId
                        ?: folderList.firstOrNull()?.id

                else ->
                    currentFolderId
                        ?: folderList.firstOrNull()?.id
            }
        }
    }

    val currentTabIndex by remember(displayFocusedFolderId, folderList) {
        derivedStateOf {
            if (folderList.isEmpty()) return@derivedStateOf 0

            folderList.indexOfFirst { it.id == displayFocusedFolderId }
                .takeIf { it >= 0 }
                ?: 0
        }
    }

    val focusTargetIndex by remember(displayFocusedFolderId, folderList) {
        derivedStateOf {
            if (folderList.isEmpty()) return@derivedStateOf 0

            folderList.indexOfFirst { it.id == displayFocusedFolderId }
                .takeIf { it >= 0 }
                ?: 0
        }
    }

    val focusTargetFolderId by remember(focusTargetIndex, folderList) {
        derivedStateOf {
            folderList.getOrNull(focusTargetIndex)?.id
        }
    }

    val visibleFavorites by remember {
        derivedStateOf {
            val folderId = currentFolderId
            if (folderId == null) return@derivedStateOf favoriteViewModel.favorites

            val q = folderQueryStates[folderId]?.debouncedQuery?.trim().orEmpty()
            if (q.isBlank()) {
                favoriteViewModel.favorites
            } else {
                favoriteViewModel.favorites.filter { it.title.contains(q, ignoreCase = true) }
            }
        }
    }

    val currentFolderQuery by remember {
        derivedStateOf {
            val folderId = currentFolderId ?: return@derivedStateOf ""
            folderQueryStates[folderId]?.debouncedQuery?.trim().orEmpty()
        }
    }

    var autoLoadGateTargetFolderId by remember { mutableStateOf<Long?>(null) }
    var autoLoadGateNonce by remember { mutableIntStateOf(0) }

    fun clearExplicitSearchAutoLoadGate() {
        autoLoadGateTargetFolderId = null
    }

    fun armExplicitSearchAutoLoadGate(targetFolderId: Long) {
        autoLoadGateTargetFolderId = targetFolderId
        autoLoadGateNonce++
    }

    // 搜索弹窗
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchDialogFolderId by remember { mutableStateOf<Long?>(null) }
    var searchFieldHasFocus by remember { mutableStateOf(false) }

    // 仅当关键词发生变化时，关闭搜索框后恢复一次搜索态自动补页
    var forceResumeSearchAutoLoadFolderId by remember { mutableStateOf<Long?>(null) }

    fun activateFolderByClick(folderMetadata: FavoriteFolderMetadata) {
        val folderId = folderMetadata.id
        val isCurrentFolder = favoriteViewModel.currentFavoriteFolderMetadata?.id == folderId
        val shouldArmExplicitGate = isCurrentFolder && currentFolderQuery.isNotBlank()

        favoriteViewModel.onFolderClicked(folderId)
        favoriteViewModel.switchToFolder(folderMetadata)

        if (shouldArmExplicitGate) {
            armExplicitSearchAutoLoadGate(folderId)
        }
    }

    fun activateFolderForSearchAction(folderMetadata: FavoriteFolderMetadata) {
        val folderId = folderMetadata.id
        favoriteViewModel.onFolderClicked(folderId)

        if (favoriteViewModel.currentFavoriteFolderMetadata?.id != folderId) {
            favoriteViewModel.switchToFolder(folderMetadata)
        }
    }

    fun requestTabsFocus() {
        pendingBackToTabsFocus = true
        defaultFocusRequester.requestFocus(scope)
    }

    fun closeSearchDialog(apply: Boolean) {
        val folderId = searchDialogFolderId
        var shouldResumeSearchAutoLoad = false

        if (apply && folderId != null) {
            val queryState = getQueryState(folderId)
            val beforeQuery = queryState.debouncedQuery.trim()

            onFolderSearchAction(folderId)

            val afterQuery = queryState.debouncedQuery.trim()
            val queryChanged = beforeQuery != afterQuery

            shouldResumeSearchAutoLoad = queryChanged && afterQuery.isNotBlank()
        }

        showSearchDialog = false
        searchDialogFolderId = null

        forceResumeSearchAutoLoadFolderId = if (shouldResumeSearchAutoLoad) {
            folderId
        } else {
            null
        }
    }

    // Dialog 打开时暂停加载；关闭后继续
    // 模式切换：无关键词触底翻页；有关键词自动补页
    DisposableEffect(Unit) {
        favoriteViewModel.syncFolderActivationToCurrent()

        onDispose {
            favoriteViewModel.syncFolderActivationToCurrent()
            folderQueryStates.values.forEach { it.debounceJob?.cancel() }
            folderQueryStates.clear()
            favoriteViewModel.stopAutoLoad()
        }
    }

    LaunchedEffect(
        activeFolderId,
        folderList
    ) {
        val targetFolderId = activeFolderId ?: return@LaunchedEffect
        val targetFolder = folderList.firstOrNull { it.id == targetFolderId } ?: return@LaunchedEffect

        if (favoriteViewModel.currentFavoriteFolderMetadata?.id != targetFolderId) {
            favoriteViewModel.switchToFolder(targetFolder)
        }
    }

    LaunchedEffect(
        currentFolderId,
        currentFolderQuery,
        showSearchDialog,
        focusOnTabs,
        forceResumeSearchAutoLoadFolderId
    ) {
        val id = currentFolderId
        val inSearchMode = id != null && currentFolderQuery.isNotBlank()

        if (!inSearchMode) {
            forceResumeSearchAutoLoadFolderId = null
            clearExplicitSearchAutoLoadGate()
            favoriteViewModel.allowAutoLoad = false
            favoriteViewModel.updateLoadingPaused(false)
            return@LaunchedEffect
        }

        if (showSearchDialog) {
            favoriteViewModel.allowAutoLoad = true
            favoriteViewModel.updateLoadingPaused(true)
            return@LaunchedEffect
        }

        if (forceResumeSearchAutoLoadFolderId == id) {
            favoriteViewModel.allowAutoLoad = true
            favoriteViewModel.updateLoadingPaused(false)
            favoriteViewModel.startAutoLoad()
            forceResumeSearchAutoLoadFolderId = null
            return@LaunchedEffect
        }

        if (focusOnTabs) {
            favoriteViewModel.allowAutoLoad = false
            favoriteViewModel.updateLoadingPaused(false)
            return@LaunchedEffect
        }

        favoriteViewModel.allowAutoLoad = true
        favoriteViewModel.updateLoadingPaused(false)
        favoriteViewModel.startAutoLoad()
    }

    LaunchedEffect(lazyGridState, currentFolderId, currentFolderQuery) {
        if (currentFolderId == null) return@LaunchedEffect
        if (currentFolderQuery.isNotBlank()) return@LaunchedEffect

        snapshotFlow { lazyGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .filter { index ->
                index != null && index >= favoriteViewModel.favorites.size - 20
            }
            .collect {
                favoriteViewModel.updateFolderItems()
            }
    }

    LaunchedEffect(
        autoLoadGateTargetFolderId,
        autoLoadGateNonce,
        currentFolderId,
        currentFolderQuery,
        showSearchDialog,
        focusedFolderId,
        focusOnTabs
    ) {
        val id = autoLoadGateTargetFolderId ?: return@LaunchedEffect
        if (currentFolderQuery.isBlank() || showSearchDialog) return@LaunchedEffect

        delay(1000)

        val canResume = currentFolderId == id

        if (
            autoLoadGateTargetFolderId == id &&
            canResume &&
            currentFolderQuery.isNotBlank() &&
            !showSearchDialog
        ) {
            favoriteViewModel.allowAutoLoad = true
            favoriteViewModel.updateLoadingPaused(false)
            favoriteViewModel.startAutoLoad()
            clearExplicitSearchAutoLoadGate()
        }
    }

    LaunchedEffect(folderList, focusTargetFolderId) {
        readyFocusTargetFolderId = null
    }

    LaunchedEffect(
        pendingBackToTabsFocus,
        readyFocusTargetFolderId,
        focusTargetFolderId
    ) {
        val targetFolderId = focusTargetFolderId ?: return@LaunchedEffect

        if (
            pendingBackToTabsFocus &&
            readyFocusTargetFolderId == targetFolderId
        ) {
            pendingBackToTabsFocus = false
            defaultFocusRequester.requestFocus(scope)
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

    val density = LocalDensity.current
    val tabLabelFontSize = MaterialTheme.typography.labelLarge.fontSize
    val filterIconSizeDp = with(density) {
        // 让 icon 和文字等高
        tabLabelFontSize.toDp()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                // Dialog 打开时，返回键优先关闭 Dialog
                if (showSearchDialog && it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                    closeSearchDialog(apply = true)
                    return@onPreviewKeyEvent true
                }
                // 只处理返回键的抬起事件
                if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                    // 如果焦点当前在 TabRow 上
                    if (focusOnTabs) {
                        // 调用父组件传递的 onBack 回调
                        onBack()
                    } else {
                        // 如果焦点在下面的内容（LazyVerticalGrid）里，
                        // 则将焦点移动到 TabRow
                        defaultFocusRequester.requestFocus()
                    }
                    // 返回 true，表示此事件已处理，系统无需再做默认的返回操作
                    return@onPreviewKeyEvent true
                }
                // 对于其他按键，返回 false
                false
            },
        horizontalAlignment = Alignment.Start

    ) {
        if (folderList.isNotEmpty()) {
            TabRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .onFocusChanged { state ->
                        focusOnTabs = state.hasFocus
                        if (state.hasFocus) {
                            pendingBackToTabsFocus = false
                        } else {
                            favoriteViewModel.syncFolderActivationToCurrent()
                        }
                    }
                    .focusRestorer(defaultFocusRequester),
                selectedTabIndex = currentTabIndex,
                separator = { Spacer(modifier = Modifier.width(12.dp)) },
                indicator = { tabPositions, doesTabRowHaveFocus ->
                    tabPositions.getOrNull(currentTabIndex)?.let { currentTabPosition ->
                        TabRowDefaults.PillIndicator(
                            currentTabPosition = currentTabPosition,
                            doesTabRowHaveFocus = doesTabRowHaveFocus,
                            activeColor = MaterialTheme.colorScheme.primary,
                            inactiveColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                },
            ) {
                folderList.forEachIndexed { index, folderMetadata ->
                    val folderId = folderMetadata.id
                    val queryState = folderQueryStates[folderId]
                    val isSearching = queryState?.debouncedQuery?.isNotBlank() == true
                    var longPressTriggered by remember(folderId) { mutableStateOf(false) }

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
                                index == focusTargetIndex,
                                Modifier
                                    .focusRequester(defaultFocusRequester)
                                    .onGloballyPositioned {
                                        readyFocusTargetFolderId = folderId
                                    }
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
                                            activateFolderForSearchAction(folderMetadata)

                                            // debouncedQuery 非空才算“搜索态”
                                            val isSearchingNow =
                                                folderQueryStates[folderId]?.debouncedQuery?.isNotBlank() == true

                                            if (isSearchingNow) {
                                                // 已在搜索态：再次长按 => 清空搜索
                                                clearFolderQuery(folderId)
                                            } else {
                                                // 未搜索：长按打开 dialog
                                                showSearchDialog = true
                                                searchDialogFolderId = folderId
                                            }
                                        }
                                        return@onPreviewKeyEvent true
                                    }
                                    return@onPreviewKeyEvent false
                                }

                                if (event.type == KeyEventType.KeyUp && longPressTriggered) {
                                    longPressTriggered = false
                                    return@onPreviewKeyEvent true
                                }

                                false
                            },
                        selected = displayFocusedFolderId == folderId,
                        onFocus = {
                            if (focusedFolderId != folderId) {
                                favoriteViewModel.onFolderFocused(folderId)
                            }
                        },
                        onClick = {
                            activateFolderByClick(folderMetadata)
                        }
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
                                if (isSearching) {
                                    Icon(
                                        modifier = Modifier.size(filterIconSizeDp),
                                        imageVector = Icons.Rounded.FilterList,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                Text(
                                    text = folderMetadata.title,
                                    color = LocalContentColor.current,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        SmallVideoCardGridHost(
            modifier = modifier,
            state = lazyGridState,
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            horizontalWrapItemCount = visibleFavorites.size
        ) {
            if (visibleFavorites.isNotEmpty()) {
                itemsIndexed(
                    items = visibleFavorites,
                    key = { _, favorite -> favorite.avid }
                ) { index, favorite ->
                    Box(contentAlignment = Alignment.Center) {
                        SmallVideoCard(
                            frameModifier = rememberGridRowWrapModifier(index),
                            data = favorite,
                            onClick = {
                                VideoInfoActivity.actionStart(
                                    context = context,
                                    aid = favorite.avid,
                                    epid = favorite.epId,
                                )
                            },
                            onAddWatchLater = {
                                toViewViewModel.addToView(favorite.avid)
                            },
                            onGoToDetailPage = {
                                VideoInfoActivity.actionStart(
                                    context = context,
                                    fromController = true,
                                    aid = favorite.avid,
                                    epid = favorite.epId,
                                )
                            },
                            onGoToUpPage = favorite.upMid?.let {
                                { UpInfoActivity.actionStart(context, it, favorite.upName) }
                            }
                        )
                    }
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyTip()
                }
            }
        }
    }

    // ===== 搜索 Dialog =====
    if (showSearchDialog) {
        val folderId = searchDialogFolderId
        val folderTitle =
            favoriteViewModel.favoriteFolderMetadataList.firstOrNull { it.id == folderId }?.title.orEmpty()

        if (folderId != null) {
            val st = getQueryState(folderId)

            val searchFocusedLineColor = C.primary
            val searchUnfocusedLineColor = C.onSurfaceVariant

            TvAlertDialog(
                onDismissRequest = {
                    // 点返回视为“完成输入并应用”
                    closeSearchDialog(apply = true)
                },
                // title 传对应收藏夹名字
                title = {
                    Text(text = "在 $folderTitle 中搜索")
                },
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
                        onValueChange = { onFolderQueryChange(folderId, it) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 26.sp, lineHeight = 30.sp),
                        shape = RectangleShape,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { onFolderSearchAction(folderId) }
                        )
                    )
                },
                confirmButton = { },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            )
        }
    }
}
