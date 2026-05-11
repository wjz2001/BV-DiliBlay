package dev.aaa1115910.bv.screen.main

import androidx.compose.foundation.BorderStroke
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.component.BlockTagItem
import dev.aaa1115910.bv.component.BvTabLabel
import dev.aaa1115910.bv.component.BvUnderlineTabRow
import dev.aaa1115910.bv.component.FollowGroupSelectDialog
import dev.aaa1115910.bv.component.LoadingTip
import dev.aaa1115910.bv.component.MainChromeDefaults
import dev.aaa1115910.bv.component.MainTopBarContainer
import dev.aaa1115910.bv.component.MainTopTabSeparator
import dev.aaa1115910.bv.component.TvLazyVerticalGrid
import dev.aaa1115910.bv.screen.main.common.MainContentEntryRequest
import dev.aaa1115910.bv.screen.main.common.MainContentFocusTarget
import dev.aaa1115910.bv.screen.user.EmptyTip
import dev.aaa1115910.bv.tv.component.TvAlertDialog
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.requestFocus
import dev.aaa1115910.bv.util.rememberTvImageRequest
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.user.FollowViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private class GroupQueryState {
    var rawQuery by mutableStateOf("")
    var debouncedQuery by mutableStateOf("")
    var debounceJob: Job? = null
}

private data class GridFocusLink(
    val leftIndex: Int,
    val rightIndex: Int,
    val upIndex: Int,
    val downIndex: Int
)

@Composable
fun FollowContent(
    navFocusRequester: FocusRequester,
    drawerFocusRequester: FocusRequester,
    pendingDrawerEntryRequest: MainContentEntryRequest? = null,
    onDrawerEntryConsumed: (Long) -> Unit = {},
    onDefaultFocusReady: (() -> Unit)? = null,
    lazyGridState: LazyGridState = rememberLazyGridState(),
    followViewModel: FollowViewModel = koinViewModel(),
    active: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val contentEntryFocusRequester = remember { FocusRequester() }

    var focusOnTabs by remember { mutableStateOf(true) }
    var topNavReadyGroupId by remember { mutableStateOf<Int?>(null) }
    var contentReadyGroupId by remember { mutableStateOf<Int?>(null) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchDialogGroupId by remember { mutableStateOf<Int?>(null) }
    var searchFieldHasFocus by remember { mutableStateOf(false) }

    val groupQueryStates = remember { mutableStateMapOf<Int, GroupQueryState>() }

    fun getQueryState(groupId: Int): GroupQueryState {
        return groupQueryStates.getOrPut(groupId) { GroupQueryState() }
    }

    fun clearGroupQuery(groupId: Int) {
        val state = groupQueryStates[groupId] ?: return
        state.debounceJob?.cancel()
        state.debounceJob = null
        state.rawQuery = ""
        state.debouncedQuery = ""
    }

    fun onGroupQueryChange(groupId: Int, newText: String) {
        val state = getQueryState(groupId)
        state.rawQuery = newText
        state.debounceJob?.cancel()
        state.debounceJob = scope.launch {
            delay(900)
            state.debouncedQuery = state.rawQuery
        }
    }

    fun onGroupSearchAction(groupId: Int) {
        val state = getQueryState(groupId)
        state.debounceJob?.cancel()
        state.debounceJob = null
        state.debouncedQuery = state.rawQuery
    }

    fun closeSearchDialog(apply: Boolean) {
        val groupId = searchDialogGroupId
        if (apply && groupId != null) {
            onGroupSearchAction(groupId)
        }
        showSearchDialog = false
        searchDialogGroupId = null
    }

    val groupList by followViewModel.groupCards.collectAsStateWithLifecycle()
    val followTags by followViewModel.followTags.collectAsStateWithLifecycle()
    val followGroupDialogInitialSelectedTagIds by followViewModel.followGroupDialogInitialSelectedTagIds.collectAsStateWithLifecycle()
    val focusedGroupId = followViewModel.focusedGroupId
    val currentGroupId = followViewModel.currentGroupId

    fun resolveGroupIdInList(candidate: Int?): Int? {
        if (candidate == null) return null
        return candidate.takeIf { id -> groupList.any { it.groupId == id } }
    }

    val displayFocusedGroupId by remember(
        focusOnTabs,
        focusedGroupId,
        currentGroupId,
        groupList
    ) {
        derivedStateOf {
            val focusedGroupIdInList = resolveGroupIdInList(focusedGroupId)
            val currentGroupIdInList = resolveGroupIdInList(currentGroupId)

            when {
                else ->
                    focusedGroupIdInList
                        ?: currentGroupIdInList
                        ?: groupList.firstOrNull()?.groupId
            }
        }
    }

    val requestedGroupFocusId = remember(
        pendingDrawerEntryRequest?.id,
        groupList,
        followViewModel.preferredGroupFocusId
    ) {
        when (pendingDrawerEntryRequest?.target) {
            MainContentFocusTarget.LeftEntry -> groupList.firstOrNull()?.groupId
            MainContentFocusTarget.RightEntry -> groupList.lastOrNull()?.groupId
            null -> resolveGroupIdInList(followViewModel.preferredGroupFocusId)
                ?: groupList.firstOrNull()?.groupId
        }
    }

    LaunchedEffect(
        pendingDrawerEntryRequest?.id,
        requestedGroupFocusId,
        groupList,
        followViewModel.activeGroupId,
        followViewModel.focusedGroupId,
        active
    ) {
        if (!active) return@LaunchedEffect
        if (pendingDrawerEntryRequest == null) return@LaunchedEffect
        val desiredGroupId = requestedGroupFocusId ?: return@LaunchedEffect
        if (groupList.none { it.groupId == desiredGroupId }) return@LaunchedEffect

        if (followViewModel.activeGroupId != desiredGroupId) {
            followViewModel.onGroupClicked(desiredGroupId)
        } else if (followViewModel.focusedGroupId != desiredGroupId) {
            followViewModel.onGroupFocused(desiredGroupId)
        }
    }

    val focusTargetIndex by remember(requestedGroupFocusId, groupList) {
        derivedStateOf {
            if (groupList.isEmpty()) return@derivedStateOf 0
            groupList.indexOfFirst { it.groupId == requestedGroupFocusId }
                .takeIf { it >= 0 }
                ?: 0
        }
    }

    val focusTargetGroupId by remember(focusTargetIndex, groupList) {
        derivedStateOf {
            groupList.getOrNull(focusTargetIndex)?.groupId
        }
    }

    val visibleUsers by remember {
        derivedStateOf {
            val groupId = followViewModel.currentGroupId
            val currentUsers = followViewModel.currentUsers
            if (groupId == null) return@derivedStateOf currentUsers

            val query = groupQueryStates[groupId]?.debouncedQuery?.trim().orEmpty()
            if (query.isBlank()) {
                currentUsers
            } else {
                currentUsers.filter {
                    it.name.contains(query, ignoreCase = true) ||
                            it.sign.contains(query, ignoreCase = true)
                }
            }
        }
    }
    val userRequesters = remember { mutableMapOf<String, FocusRequester>() }
    visibleUsers.forEach { user ->
        androidx.compose.runtime.key(user.stableKey) {
            val requester = remember { FocusRequester() }
            userRequesters[user.stableKey] = requester
        }
    }
    val contentFocusLinks = remember(visibleUsers) {
        buildGridFocusLinks(
            itemCount = visibleUsers.size,
            columns = 4
        )
    }

    fun contentRequesterForIndex(index: Int): FocusRequester {
        return if (index == 0) {
            contentEntryFocusRequester
        } else {
            userRequesters.getValue(visibleUsers[index].stableKey)
        }
    }

    LaunchedEffect(lifecycleOwner, active) {
        if (!active) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            followViewModel.uiEvent.collect { event ->
                when (event) {
                    is UiEffect.ShowToast -> event.message.toast(context)
                }
            }
        }
    }

    LaunchedEffect(active) {
        if (active) {
            followViewModel.activateFollowScreen()
        } else {
            followViewModel.freezeFollowScreen()
        }
        followViewModel.syncGroupActivationToCurrent()
    }

    DisposableEffect(Unit) {
        onDispose {
            followViewModel.freezeFollowScreen()
            followViewModel.syncGroupActivationToCurrent()
            groupQueryStates.values.forEach { it.debounceJob?.cancel() }
            groupQueryStates.clear()
        }
    }

    LaunchedEffect(followViewModel.activeGroupId, groupList, active) {
        if (!active) return@LaunchedEffect
        if (followViewModel.activeGroupId == null && groupList.isNotEmpty()) {
            followViewModel.onGroupClicked(groupList.first().groupId)
        }
    }

    LaunchedEffect(groupList, focusTargetGroupId) {
        topNavReadyGroupId = null
    }

    LaunchedEffect(topNavReadyGroupId, focusTargetGroupId, active) {
        if (!active) return@LaunchedEffect
        val targetGroupId = focusTargetGroupId ?: return@LaunchedEffect
        if (topNavReadyGroupId == targetGroupId) {
            onDefaultFocusReady?.invoke()
        }
    }

    LaunchedEffect(
        pendingDrawerEntryRequest?.id,
        topNavReadyGroupId,
        focusTargetGroupId,
        followViewModel.activeGroupId,
        followViewModel.focusedGroupId,
        active
    ) {
        if (!active) return@LaunchedEffect
        val request = pendingDrawerEntryRequest ?: return@LaunchedEffect
        val targetGroupId = focusTargetGroupId ?: return@LaunchedEffect
        if (topNavReadyGroupId != targetGroupId) return@LaunchedEffect
        if (followViewModel.activeGroupId != targetGroupId) return@LaunchedEffect
        if (followViewModel.focusedGroupId != targetGroupId) return@LaunchedEffect

        navFocusRequester.requestFocus(scope)
        onDrawerEntryConsumed(request.id)
    }

    val visibleCount = visibleUsers.size

    LaunchedEffect(currentGroupId, visibleUsers) {
        contentReadyGroupId = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                if (showSearchDialog && it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                    closeSearchDialog(apply = true)
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        when {
            groupList.isEmpty() && followViewModel.updating -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingTip()
                }
            }

            groupList.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyTip()
                }
            }

            else -> {
                MainTopBarContainer(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        BvUnderlineTabRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 88.dp)
                                .onFocusChanged { state ->
                                    focusOnTabs = state.hasFocus
                                    if (!state.hasFocus) {
                                        followViewModel.syncGroupActivationToCurrent()
                                    }
                                },
                            items = groupList,
                            selectedItem = groupList.firstOrNull { it.groupId == displayFocusedGroupId },
                            entryFocusItem = if (pendingDrawerEntryRequest == null) {
                                null
                            } else {
                                groupList.firstOrNull { it.groupId == requestedGroupFocusId }
                            },
                            itemKey = { it.groupId },
                            defaultFocusRequester = navFocusRequester,
                            onDefaultFocusReady = { readyKey ->
                                val readyGroupId = readyKey as? Int
                                if (readyGroupId != null && topNavReadyGroupId != readyGroupId) {
                                    topNavReadyGroupId = readyGroupId
                                }
                            },
                            separator = { MainTopTabSeparator() },
                            onSelectedChanged = { group ->
                                if (followViewModel.focusedGroupId != group.groupId) {
                                    followViewModel.onGroupFocused(group.groupId)
                                }
                            },
                            onClick = { group ->
                                followViewModel.onGroupClicked(group.groupId)
                            },
                            onLongClick = { group ->
                                followViewModel.onGroupClicked(group.groupId)
                                val isSearchingNow =
                                    groupQueryStates[group.groupId]?.debouncedQuery?.isNotBlank() == true
                                if (isSearchingNow) {
                                    clearGroupQuery(group.groupId)
                                } else {
                                    showSearchDialog = true
                                    searchDialogGroupId = group.groupId
                                }
                                true
                            },
                            onLeftExit = {
                                drawerFocusRequester.requestFocus(scope)
                            },
                            onRightExit = {
                                drawerFocusRequester.requestFocus(scope)
                            },
                            contentFocusRequester = contentEntryFocusRequester,
                            contentFocusReadyKey = contentReadyGroupId,
                            onContentFocusRequested = { group ->
                                if (currentGroupId != group.groupId) {
                                    followViewModel.onGroupClicked(group.groupId)
                                }
                            },
                            blockUp = true,
                            autoRequestEntryFocus = false,
                            tabContent = { group, _, _ ->
                                val groupId = group.groupId
                                val queryState = groupQueryStates[groupId]
                                val isSearching = queryState?.debouncedQuery?.isNotBlank() == true

                                BvTabLabel(
                                    text = group.title,
                                    icon = { iconSize ->
                                        Icon(
                                            modifier = Modifier.size(iconSize),
                                            imageVector = Icons.Rounded.FilterList,
                                            contentDescription = null
                                        )
                                    },
                                    showIcon = isSearching
                                )
                            }
                        )

                        Text(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(start = 12.dp),
                            text = stringResource(R.string.load_data_count, visibleCount),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                TvLazyVerticalGrid(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRestorer(contentEntryFocusRequester)
                        .onPreviewKeyEvent {
                            if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                                navFocusRequester.requestFocus(scope)
                                true
                            } else {
                                false
                            }
                        },
                    state = lazyGridState,
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (visibleUsers.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyTip(
                                text = if (
                                    currentGroupId != null &&
                                    groupQueryStates[currentGroupId]?.debouncedQuery?.isNotBlank() == true
                                ) {
                                    "没有匹配的UP主"
                                } else {
                                    "该分组暂无UP主"
                                }
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = visibleUsers,
                            key = { _, user -> user.stableKey }
                        ) { index, user ->
                            val focusLink = contentFocusLinks[index]
                            UpCard(
                                modifier = Modifier
                                    .focusRequester(contentRequesterForIndex(index))
                                    .then(
                                        if (index == 0) {
                                            Modifier.onGloballyPositioned {
                                                if (active && currentGroupId != null) {
                                                    contentReadyGroupId = currentGroupId
                                                }
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .focusProperties {
                                        left = contentRequesterForIndex(focusLink.leftIndex)
                                        right = contentRequesterForIndex(focusLink.rightIndex)
                                        up = if (index < 4) {
                                            navFocusRequester
                                        } else {
                                            contentRequesterForIndex(focusLink.upIndex)
                                        }
                                        down = contentRequesterForIndex(focusLink.downIndex)
                                    },
                                face = user.avatar,
                                sign = if (user.isSelfEntry && user.sign.isBlank()) {
                                    "我的主页"
                                } else {
                                    user.sign
                                },
                                username = user.name,
                                onFocusChange = {},
                                onClick = {
                                    UpInfoActivity.actionStart(
                                        context = context,
                                        mid = user.mid,
                                        name = user.name
                                    )
                                },
                                onLongClick = {
                                    if (!user.isSelfEntry) {
                                        followViewModel.openFollowGroupDialog(user)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    FollowGroupSelectDialog(
        show = followViewModel.showFollowGroupDialog,
        title = "选择关注分组",
        tags = followTags.map { BlockTagItem(it.tagid, it.name, it.count) },
        initialSelectedTagIds = followGroupDialogInitialSelectedTagIds,
        onHideDialog = { followViewModel.hideFollowGroupDialog() },
        onSubmit = { selectedTagIds ->
            followViewModel.submitFollowGroupSelection(selectedTagIds)
        }
    )

    if (showSearchDialog) {
        val groupId = searchDialogGroupId
        val groupTitle = groupList.firstOrNull { it.groupId == groupId }?.title.orEmpty()

        if (groupId != null) {
            val state = getQueryState(groupId)
            val searchFocusedLineColor = C.primary
            val searchUnfocusedLineColor = C.onSurfaceVariant

            TvAlertDialog(
                onDismissRequest = {
                    closeSearchDialog(apply = true)
                },
                title = {
                    Text(text = "在 $groupTitle 中搜索")
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
                        value = state.rawQuery,
                        onValueChange = { onGroupQueryChange(groupId, it) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 26.sp, lineHeight = 30.sp),
                        shape = RectangleShape,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { onGroupSearchAction(groupId) }
                        )
                    )
                },
                confirmButton = {},
                properties = DialogProperties(usePlatformDefaultWidth = false)
            )
        }
    }
}

private fun buildGridFocusLinks(
    itemCount: Int,
    columns: Int
): List<GridFocusLink> {
    if (itemCount <= 0 || columns <= 0) return emptyList()

    val rowCount = ((itemCount - 1) / columns) + 1

    fun rowStart(index: Int): Int = (index / columns) * columns
    fun rowEnd(index: Int): Int = minOf(rowStart(index) + columns - 1, itemCount - 1)
    fun column(index: Int): Int = index % columns
    fun row(index: Int): Int = index / columns

    fun findUp(index: Int): Int {
        val col = column(index)
        for (step in 1..rowCount) {
            val candidateRow = (row(index) - step + rowCount) % rowCount
            val candidate = candidateRow * columns + col
            if (candidate < itemCount) return candidate
        }
        return index
    }

    fun findDown(index: Int): Int {
        val col = column(index)
        for (step in 1..rowCount) {
            val candidateRow = (row(index) + step) % rowCount
            val candidate = candidateRow * columns + col
            if (candidate < itemCount) return candidate
        }
        return index
    }

    return List(itemCount) { index ->
        val currentRowStart = rowStart(index)
        val currentRowEnd = rowEnd(index)
        GridFocusLink(
            leftIndex = if (index == currentRowStart) currentRowEnd else index - 1,
            rightIndex = if (index == currentRowEnd) currentRowStart else index + 1,
            upIndex = findUp(index),
            downIndex = findDown(index)
        )
    }
}

@Composable
private fun UpCard(
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
        shape = ClickableSurfaceDefaults.shape(shape = RectangleShape),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 3.dp, color = C.selectedBorder),
                shape = RectangleShape
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
                    .size(48.dp),
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = faceRequest,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds
                )
            }

            Column(
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Text(
                    text = username,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = sign.ifBlank { " " },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
