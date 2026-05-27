package dev.aaa1115910.bv.screen.main.home

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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import dev.aaa1115910.bv.util.isKeyUp
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
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.wjzfocus.WjzFocusItemKey
import dev.aaa1115910.bv.wjzfocus.wjzObserveFocusChanged
import dev.aaa1115910.bv.component.BlockTagItem
import dev.aaa1115910.bv.component.BvTabLabel
import dev.aaa1115910.bv.component.BvUnderlineTabRow
import dev.aaa1115910.bv.component.FollowGroupSelectDialog
import dev.aaa1115910.bv.component.LoadingTip
import dev.aaa1115910.bv.component.MainTopBarContainer
import dev.aaa1115910.bv.component.MainTopTabDefaults
import dev.aaa1115910.bv.component.MainTopTabSeparator
import dev.aaa1115910.bv.component.TvGridFocusHost
import dev.aaa1115910.bv.component.rememberTvGridFocusModifier
import dev.aaa1115910.bv.component.TvAlertDialog
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.ui.theme.C
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

@Composable
fun FollowScreen(
    modifier: Modifier = Modifier,
    lazyGridState: LazyGridState = rememberLazyGridState(),
    active: Boolean = true,
    activationSerial: Long = 0L,
    refreshSerial: Long = 0L,
    followViewModel: FollowViewModel = koinViewModel(),
    onContentEntryReady: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

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

    val requestedGroupFocusId = remember(groupList, followViewModel.preferredGroupFocusId) {
        resolveGroupIdInList(followViewModel.preferredGroupFocusId)
            ?: groupList.firstOrNull()?.groupId
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

    LaunchedEffect(active, activationSerial) {
        if (!active) return@LaunchedEffect
        if (activationSerial == 0L) return@LaunchedEffect
        withFrameNanos { }
        followViewModel.activateFollowScreen()
    }

    LaunchedEffect(active, refreshSerial) {
        if (!active) return@LaunchedEffect
        if (refreshSerial == 0L) return@LaunchedEffect
        lazyGridState.scrollToItem(0)
        followViewModel.freezeFollowScreen()
        followViewModel.activateFollowScreen()
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
            onContentEntryReady()
        }
    }

    val visibleCount = visibleUsers.size

    LaunchedEffect(currentGroupId, visibleUsers) {
        contentReadyGroupId = null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                if (showSearchDialog && it.key == Key.Back && it.isKeyUp()) {
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
                MainTopBarContainer {
                    BvUnderlineTabRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MainTopTabDefaults.TabRowHorizontalPadding)
                            .wjzObserveFocusChanged { hasFocus ->
                                focusOnTabs = hasFocus
                                if (!hasFocus) {
                                    followViewModel.syncGroupActivationToCurrent()
                                }
                            },
                        items = groupList,
                        selectedItem = groupList.firstOrNull { it.groupId == displayFocusedGroupId },
                        entryFocusItem = groupList.firstOrNull { it.groupId == requestedGroupFocusId },
                        itemKey = { it.groupId },
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
                        onUp = {
                            onBack()
                            true
                        },
                        backFocusEnabled = active,
                        contentFocusEnabled = true,
                        contentFocusReadyKey = contentReadyGroupId,
                        onContentFocusRequested = { group ->
                            if (currentGroupId != group.groupId) {
                                followViewModel.onGroupClicked(group.groupId)
                            }
                        },
                        autoRequestEntryFocus = false,
                        tabContent = { group, _, _ ->
                            val groupId = group.groupId
                            val queryState = groupQueryStates[groupId]
                            val isSearching = queryState?.debouncedQuery?.isNotBlank() == true

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(MainTopTabDefaults.TabContentHeight),
                                contentAlignment = Alignment.Center
                            ) {
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
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    text = stringResource(R.string.load_data_count, visibleCount),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                TvGridFocusHost(
                    modifier = Modifier
                        .fillMaxSize(),
                    state = lazyGridState,
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    nodeIdPrefix = "follow/${currentGroupId ?: "empty"}/users",
                    onEntryFocusReady = {
                        contentReadyGroupId = currentGroupId
                        onContentEntryReady()
                    },
                    focusItemCount = visibleUsers.size,
                    itemKeys = visibleUsers.map { WjzFocusItemKey("String:${it.stableKey}") },
                    focusColumnCount = 4
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
                            UpCard(
                                modifier = rememberTvGridFocusModifier(index),
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
                            .wjzObserveFocusChanged { searchFieldHasFocus = it }
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
            .wjzObserveFocusChanged { onFocusChange(it) }
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
