package dev.aaa1115910.bv.component.videocard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.focusGroup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.FavoriteFolderMetadata
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusItemKey
import dev.aaa1115910.bv.component.BvLazyFocusItemTarget
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzLazyFocusRestorerHost
import dev.aaa1115910.bv.component.CoAuthorsDialogHost
import dev.aaa1115910.bv.component.FavoriteDialog
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.wjzFocusable
import dev.aaa1115910.bv.component.handleUpHomeClick
import dev.aaa1115910.bv.component.rememberCoAuthorsDialogState
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.SmallVideoCardGridEvent
import dev.aaa1115910.bv.viewmodel.SmallVideoCardGridViewModel
import dev.aaa1115910.bv.ui.theme.C
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private class RowWrapController {
    private val requesters = mutableMapOf<Int, FocusRequester>()

    var lastFocusedIndex by mutableIntStateOf(0)
    var lastFocusedKey by mutableStateOf<WjzFocusItemKey?>(null)
    var itemCount: Int = 0
    var itemKeys: List<WjzFocusItemKey> = emptyList()
    var nodeIdPrefix: String = "videos-row"
    var focusLayer: WjzFocusLayer = WjzFocusLayer.Content
    var focusScopeId: WjzFocusScopeId? = null
    var listState: LazyListState? = null
    var leadingSlotOffset: Int = 0
    var leadingRequester: FocusRequester? = null
    var firstItemRequesterOverride: FocusRequester? = null
    var onItemFocusChanged: (WjzFocusItemKey, Boolean) -> Unit = { _, _ -> }

    fun requesterFor(index: Int): FocusRequester {
        if (index == 0 && firstItemRequesterOverride != null) {
            return firstItemRequesterOverride!!
        }
        return requesters.getOrPut(index) { FocusRequester() }
    }

    private fun itemKeyFor(index: Int): WjzFocusItemKey {
        return itemKeys[index]
    }

    private fun nodeIdFor(index: Int): WjzFocusNodeId {
        return WjzFocusNodeId("$nodeIdPrefix/item/${itemKeyFor(index)}")
    }

    private fun indexOfKey(key: WjzFocusItemKey): Int? {
        return itemKeys.indexOf(key).takeIf { it >= 0 }
    }

    suspend fun scrollToKey(key: WjzFocusItemKey) {
        val index = indexOfKey(key) ?: return
        val state = listState ?: return
        state.scrollToItem(index + leadingSlotOffset)
    }

    fun isKeyVisible(key: WjzFocusItemKey): Boolean {
        val index = indexOfKey(key) ?: return false
        val state = listState ?: return false
        return state.layoutInfo.visibleItemsInfo.any { it.index == index + leadingSlotOffset }
    }

    fun itemKeyAt(index: Int): WjzFocusItemKey {
        return itemKeyFor(index)
    }

    fun nodeIdAt(index: Int): WjzFocusNodeId {
        return nodeIdFor(index)
    }

    fun restoreTargetKey(previousItemKeys: List<WjzFocusItemKey>): WjzFocusItemKey? {
        val focusedKey = lastFocusedKey ?: return null
        if (itemKeys.isEmpty()) return null
        if (focusedKey in itemKeys) return focusedKey

        val previousIndex = previousItemKeys.indexOf(focusedKey)
            .takeIf { it >= 0 }
            ?: lastFocusedIndex
        return itemKeys[previousIndex.coerceIn(0, itemKeys.lastIndex)]
    }

    /**
     * 这里只处理“相邻项”和 leadingItem：
     * - 第一个左边 -> leadingItem（如果有）
     * - 中间项左右 -> 前后项
     * - 不在这里做首尾互跳
     *
     * 首尾循环交给 onPreviewKeyEvent + scrollToItem + requestFocus，
     * 这样对 LazyRow 才是稳定的。
     */
    @Composable
    fun Modifier.modifierFor(index: Int): Modifier {
        return targetFor(index)?.let { this.then(it.modifier) } ?: this
    }

    @Composable
    fun targetFor(index: Int): BvLazyFocusItemTarget? {
        if (index !in 0 until itemCount) return null

        val lastIndex = itemCount - 1
        val requester = requesterFor(index)
        val nodeId = nodeIdFor(index)
        val itemKey = itemKeyFor(index)
        val restorerId = "$nodeIdPrefix/lazy-restorer"

        val modifier = Modifier
            .wjzFocusable(
                nodeId = nodeId,
                layer = focusLayer,
                scopeId = focusScopeId,
                requester = requester,
                onFocusChanged = { hasFocus ->
                    if (hasFocus) {
                        lastFocusedIndex = index
                        lastFocusedKey = itemKey
                    }
                    onItemFocusChanged(itemKey, hasFocus)
                }
            )
            .focusProperties {
                when {
                    index == 0 && leadingRequester != null -> {
                        left = leadingRequester!!
                    }

                    index > 0 -> {
                        left = requesterFor(index - 1)
                    }
                }

                if (index < lastIndex) {
                    right = requesterFor(index + 1)
                }
            }
        return BvLazyFocusItemTarget(
            modifier = modifier,
            nodeId = nodeId,
            requester = requester,
            layer = focusLayer,
            scopeId = focusScopeId,
            itemKey = itemKey,
            restorerId = restorerId,
            listId = restorerId
        )
    }
}

private fun videoCardKey(item: VideoCardData): WjzFocusItemKey {
    return WjzFocusItemKey("${item.avid}:${item.cid ?: -1L}:${item.epId ?: -1}")
}

@Composable
fun VideosRowCore(
    modifier: Modifier = Modifier,
    header: String,
    fontSize: TextUnit = 14.sp,
    focusedHeaderColor: Color? = null,
    unfocusedHeaderColor: Color? = null,
    videos: ImmutableList<VideoCardData>,
    onVideoClicked: (VideoCardData) -> Unit,
    onAddWatchLater: ((Long) -> Unit)? = null,
    onGoToUpPage: ((Long, String) -> Unit)? = null,
    enableHorizontalWrap: Boolean = true,
    rowStateKey: String,
    entryFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    leadingItem: (@Composable (Modifier) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    manageRowFocusInternally: Boolean = true,
) {
    val viewModel: SmallVideoCardGridViewModel = koinViewModel(key = rowStateKey)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cardUiMap by viewModel.cardUiMap.collectAsStateWithLifecycle()
    val coAuthorsDialogState = rememberCoAuthorsDialogState()
    val scope = rememberCoroutineScope()
    val focusCoordinator = LocalWjzFocusCoordinator.current

    var hasFocus by remember { mutableStateOf(false) }
    val titleColor = if (hasFocus) {
        focusedHeaderColor ?: C.onSurface
    } else {
        unfocusedHeaderColor ?: C.onSurfaceVariant
    }

    val navigateUp = remember(context, onGoToUpPage) {
        onGoToUpPage ?: { mid: Long, name: String ->
            UpInfoActivity.actionStart(context, mid = mid, name = name)
        }
    }

    val favoriteFolders = remember { mutableStateListOf<FavoriteFolderMetadata>() }
    val selectedFolderIds = remember { mutableStateListOf<Long>() }

    val leadingFocusRequester = remember { FocusRequester() }
    val firstVideoFocusRequester = entryFocusRequester ?: remember { FocusRequester() }
    val rowFocusScopeId = remember(rowStateKey) {
        WjzFocusScopeId("videos-row/$rowStateKey")
    }
    val itemKeys = remember(videos) {
        videos.map { videoCardKey(it) }
    }

    val rowWrapController = remember { RowWrapController() }.apply {
        itemCount = videos.size
        this.itemKeys = itemKeys
        nodeIdPrefix = rowStateKey
        focusLayer = WjzFocusLayer.Content
        focusScopeId = rowFocusScopeId
        this.listState = listState
        this.leadingSlotOffset = if (leadingItem != null) 1 else 0
        leadingRequester = leadingItem?.let { leadingFocusRequester }
        firstItemRequesterOverride = firstVideoFocusRequester
    }
    var shouldRestoreFocusedItem by remember(rowStateKey) {
        mutableStateOf(false)
    }
    rowWrapController.onItemFocusChanged = { itemKey, itemHasFocus ->
        if (itemHasFocus) {
            shouldRestoreFocusedItem = true
        } else if (itemKey in itemKeys) {
            shouldRestoreFocusedItem = false
        }
    }
    val cardUiStateFor = remember(cardUiMap) {
        { aid: Long -> cardUiMap[aid] }
    }

    val leadingSlotOffset = if (leadingItem != null) 1 else 0
    var previousItemKeys by remember(rowStateKey) {
        mutableStateOf<List<WjzFocusItemKey>>(emptyList())
    }

    LaunchedEffect(Unit) {
        viewModel.refreshCapabilities()
    }

    LaunchedEffect(itemKeys) {
        if (previousItemKeys != itemKeys) {
            val targetKey = rowWrapController.restoreTargetKey(previousItemKeys)
            if (
                previousItemKeys.isNotEmpty() &&
                itemKeys.isNotEmpty() &&
                targetKey != null &&
                shouldRestoreFocusedItem
            ) {
                val targetIndex = itemKeys.indexOf(targetKey)
                if (targetIndex >= 0) {
                    focusCoordinator?.enqueueLazyRestore(
                        nodeId = rowWrapController.nodeIdAt(targetIndex),
                        itemKey = targetKey,
                        layer = WjzFocusLayer.Content,
                        scopeId = rowFocusScopeId,
                        restorerId = "$rowStateKey/lazy-restorer",
                        listId = "$rowStateKey/lazy-restorer"
                    )
                }
                shouldRestoreFocusedItem = false
            } else if (
                targetKey == null ||
                itemKeys.isEmpty() ||
                !hasFocus
            ) {
                shouldRestoreFocusedItem = false
            }
        }
        previousItemKeys = itemKeys
    }

    LaunchedEffect(uiState.favoriteDialog.folders, uiState.favoriteDialog.selectedFolderIds) {
        favoriteFolders.clear()
        favoriteFolders.addAll(uiState.favoriteDialog.folders)

        selectedFolderIds.clear()
        selectedFolderIds.addAll(uiState.favoriteDialog.selectedFolderIds)
    }

    LaunchedEffect(uiState.coAuthorsDialog.show, uiState.coAuthorsDialog.authors) {
        if (uiState.coAuthorsDialog.show) {
            handleUpHomeClick(
                authors = uiState.coAuthorsDialog.authors,
                state = coAuthorsDialogState,
                onNavigateSingle = { mid, name ->
                    navigateUp(mid, name)
                    viewModel.dismissCoAuthorsDialog()
                }
            )
        }
    }

    LaunchedEffect(coAuthorsDialogState.visible, uiState.coAuthorsDialog.show) {
        if (!coAuthorsDialogState.visible && uiState.coAuthorsDialog.show) {
            viewModel.dismissCoAuthorsDialog()
        }
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is SmallVideoCardGridEvent.Toast -> {
                        event.message.toast(context)
                    }

                    is SmallVideoCardGridEvent.NavigateUp -> {
                        navigateUp(event.mid, event.name)
                    }
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = LocalDensity.current.density * 1.25f,
            fontScale = LocalDensity.current.fontScale * 1.25f
        ),
        LocalSmallVideoCardGridViewModel provides viewModel,
        LocalSmallVideoCardGridUiState provides uiState
    ) {
        var columnModifier = modifier
        if (manageRowFocusInternally) {
            columnModifier = columnModifier
                .focusRestorer()
                .focusGroup()
        }
        columnModifier = columnModifier.onFocusChanged {
            hasFocus = it.hasFocus
            if (!it.hasFocus && rowWrapController.lastFocusedKey?.let { key -> key in itemKeys } == true) {
                shouldRestoreFocusedItem = false
            }
        }

        Column(
            modifier = columnModifier
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 62.dp),
                text = header,
                fontSize = fontSize,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            WjzLazyFocusRestorerHost(
                layer = WjzFocusLayer.Content,
                scopeId = rowFocusScopeId,
                restorerId = "$rowStateKey/lazy-restorer",
                listId = "$rowStateKey/lazy-restorer",
                scrollToItem = { key -> rowWrapController.scrollToKey(key) },
                isItemVisible = { key -> rowWrapController.isKeyVisible(key) }
            )

            LazyRow(
                state = listState,
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top,
                contentPadding = PaddingValues(horizontal = 58.dp)
            ) {
                if (leadingItem != null) {
                    item(key = "${rowStateKey}_leading_item") {
                        leadingItem(
                            Modifier
                                .focusRequester(leadingFocusRequester)
                                .focusProperties {
                                    if (videos.isNotEmpty()) {
                                        right = rowWrapController.requesterFor(0)
                                    }
                                    if (upFocusRequester != null) up = upFocusRequester
                                    if (downFocusRequester != null) down = downFocusRequester
                                }
                        )
                    }
                }

                itemsIndexed(
                    items = videos,
                    key = { _, item -> videoCardKey(item).value }
                ) { index, videoData ->
                    val lastIndex = videos.lastIndex
                    val focusTarget = rowWrapController.targetFor(index)?.let { target ->
                        target.copy(
                            modifier = target.modifier
                                .focusProperties {
                                    if (upFocusRequester != null) up = upFocusRequester
                                    if (downFocusRequester != null) down = downFocusRequester
                                }
                                .onPreviewKeyEvent { event ->
                                    if (!enableHorizontalWrap) {
                                        return@onPreviewKeyEvent false
                                    }
                                    if (videos.size <= 1) {
                                        return@onPreviewKeyEvent false
                                    }
                                    if (event.type != KeyEventType.KeyDown) {
                                        return@onPreviewKeyEvent false
                                    }

                                    when {
                                        // 没有 leadingItem 时，第一个视频按左，循环到最后一个视频
                                        event.key == Key.DirectionLeft &&
                                                index == 0 &&
                                                leadingItem == null -> {
                                            scope.launch {
                                                val targetKey = rowWrapController.itemKeyAt(lastIndex)
                                                focusCoordinator?.enqueueLazyRestore(
                                                    nodeId = rowWrapController.nodeIdAt(lastIndex),
                                                    itemKey = targetKey,
                                                    layer = WjzFocusLayer.Content,
                                                    scopeId = rowFocusScopeId,
                                                    restorerId = "$rowStateKey/lazy-restorer",
                                                    listId = "$rowStateKey/lazy-restorer"
                                                )
                                            }
                                            true
                                        }

                                        // 最后一个视频按右，循环到第一个视频
                                        event.key == Key.DirectionRight &&
                                                index == lastIndex -> {
                                            scope.launch {
                                                val targetKey = rowWrapController.itemKeyAt(0)
                                                focusCoordinator?.enqueueLazyRestore(
                                                    nodeId = rowWrapController.nodeIdAt(0),
                                                    itemKey = targetKey,
                                                    layer = WjzFocusLayer.Content,
                                                    scopeId = rowFocusScopeId,
                                                    restorerId = "$rowStateKey/lazy-restorer",
                                                    listId = "$rowStateKey/lazy-restorer"
                                                )
                                            }
                                            true
                                        }

                                        else -> false
                                    }
                                }
                        )
                    }

                    SmallVideoCard(
                        modifier = Modifier.width(200.dp),
                        focusTarget = focusTarget,
                        uiState = cardUiStateFor(videoData.avid),
                        data = videoData,
                        titleMaxLines = 1,
                        onClick = { onVideoClicked(videoData) },
                        coverDensityMultiplier = 1f,
                        coverFontScaleMultiplier = 1f,
                        infoDensityMultiplier = 1f,
                        infoFontScaleMultiplier = 1f,
                        onAddWatchLater = onAddWatchLater?.let { callback ->
                            { callback(videoData.avid) }
                        },
                        onGoToUpPage = onGoToUpPage?.let { callback ->
                            videoData.upMid?.let { mid ->
                                { callback(mid, videoData.upName) }
                            }
                        }
                    )
                }
            }
        }
    }

    FavoriteDialog(
        show = uiState.favoriteDialog.show,
        onHideDialog = viewModel::dismissFavoriteDialog,
        userFavoriteFolders = favoriteFolders,
        favoriteFolderIds = selectedFolderIds,
        onUpdateFavoriteFolders = viewModel::updateFavoriteFolders
    )

    CoAuthorsDialogHost(
        state = coAuthorsDialogState,
        onClickAuthor = { mid, name ->
            viewModel.onCoAuthorClicked(mid, name)
        }
    )
}

@Composable
fun VideosRow(
    modifier: Modifier = Modifier,
    header: String,
    focusedHeaderColor: Color? = null,
    unfocusedHeaderColor: Color? = null,
    videos: ImmutableList<VideoCardData>,
    onVideoClicked: (VideoCardData) -> Unit,
    onAddWatchLater: ((Long) -> Unit)? = null,
    onGoToUpPage: ((Long, String) -> Unit)? = null,
    enableHorizontalWrap: Boolean = true,
    rowStateKey: String? = null,
    entryFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    manageRowFocusInternally: Boolean = true,
) {
    val resolvedRowStateKey = rowStateKey ?: remember(header, videos) {
        val firstAid = videos.firstOrNull()?.avid ?: 0L
        "VideosRow:$header:$firstAid:${videos.size}"
    }

    VideosRowCore(
        modifier = modifier,
        header = header,
        focusedHeaderColor = focusedHeaderColor,
        unfocusedHeaderColor = unfocusedHeaderColor,
        videos = videos,
        onVideoClicked = onVideoClicked,
        onAddWatchLater = onAddWatchLater,
        onGoToUpPage = onGoToUpPage,
        enableHorizontalWrap = enableHorizontalWrap,
        rowStateKey = resolvedRowStateKey,
        entryFocusRequester = entryFocusRequester,
        upFocusRequester = upFocusRequester,
        downFocusRequester = downFocusRequester,
        leadingItem = null,
        manageRowFocusInternally = manageRowFocusInternally
    )
}
