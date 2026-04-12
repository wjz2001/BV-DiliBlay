package dev.aaa1115910.bv.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.component.BlockTagItem
import dev.aaa1115910.bv.component.FollowGroupSelectDialog
import dev.aaa1115910.bv.component.LoadingTip
import dev.aaa1115910.bv.component.TvLazyVerticalGrid
import dev.aaa1115910.bv.relation.RelationGroupKind
import dev.aaa1115910.bv.screen.main.common.MainContentEntryRequest
import dev.aaa1115910.bv.screen.main.common.MainContentFocusTarget
import dev.aaa1115910.bv.screen.user.EmptyTip
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.ui.theme.C

import dev.aaa1115910.bv.util.requestFocus
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.user.FollowGroupCardState
import dev.aaa1115910.bv.viewmodel.user.FollowGroupCardUi
import dev.aaa1115910.bv.viewmodel.user.FollowUserUi
import dev.aaa1115910.bv.viewmodel.user.FollowViewModel
import kotlin.math.max
import org.koin.androidx.compose.koinViewModel

private data class GridHorizontalFocusLink(
    val leftIndex: Int?,
    val rightIndex: Int?
)

private fun buildGridHorizontalFocusLinks(
    itemCount: Int,
    columns: Int,
    isFocusable: (Int) -> Boolean
): Map<Int, GridHorizontalFocusLink> {
    if (itemCount <= 0 || columns <= 0) return emptyMap()

    val result = mutableMapOf<Int, GridHorizontalFocusLink>()

    fun findPreviousFocusable(index: Int): Int? {
        val rowStart = (index / columns) * columns
        for (i in index - 1 downTo rowStart) {
            if (isFocusable(i)) return i
        }

        var previousRowEnd = rowStart - 1
        while (previousRowEnd >= 0) {
            val previousRowStart = maxOf(0, previousRowEnd - columns + 1)
            for (i in previousRowEnd downTo previousRowStart) {
                if (isFocusable(i)) return i
            }
            previousRowEnd = previousRowStart - 1
        }
        return null
    }

    fun findNextFocusable(index: Int): Int? {
        val rowStart = (index / columns) * columns
        val rowEnd = minOf(rowStart + columns - 1, itemCount - 1)

        for (i in index + 1..rowEnd) {
            if (isFocusable(i)) return i
        }

        var nextRowStart = rowEnd + 1
        while (nextRowStart < itemCount) {
            val nextRowEnd = minOf(nextRowStart + columns - 1, itemCount - 1)
            for (i in nextRowStart..nextRowEnd) {
                if (isFocusable(i)) return i
            }
            nextRowStart = nextRowEnd + 1
        }
        return null
    }

    for (index in 0 until itemCount) {
        if (!isFocusable(index)) continue

        result[index] = GridHorizontalFocusLink(
            leftIndex = findPreviousFocusable(index),
            rightIndex = findNextFocusable(index)
        )
    }

    return result
}

@Composable
fun FollowContent(
    navFocusRequester: FocusRequester,
    drawerFocusRequester: FocusRequester,
    pendingDrawerEntryRequest: MainContentEntryRequest? = null,
    onDrawerEntryConsumed: (Long) -> Unit = {},
    onDefaultFocusReady: (() -> Unit)? = null,
    followViewModel: FollowViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val groupGridState = rememberLazyGridState()
    val detailGridState = rememberLazyGridState()
    var focusOnContent by remember { mutableStateOf(false) }

    val selectedGroupId = followViewModel.selectedGroupId
    val title = if (selectedGroupId == null) {
        stringResource(R.string.user_homepage_follow)
    } else {
        followViewModel.currentTitle.ifBlank { stringResource(R.string.user_homepage_follow) }
    }
    val count = if (selectedGroupId == null) {
        followViewModel.totalUsers
    } else {
        followViewModel.currentCount
    }
    val currentUsers = followViewModel.currentUsers

    LaunchedEffect(Unit) {
        followViewModel.uiEvent.collect { event ->
            when (event) {
                is UiEffect.ShowToast -> event.message.toast(context)
            }
        }
    }

    val focusableGroups = remember(followViewModel.groupCards) {
        followViewModel.groupCards.filter { it.state == FollowGroupCardState.NORMAL }
    }
    val focusableGroupIds = remember(focusableGroups) {
        focusableGroups.map { it.groupId }
    }
    val requestedGroupFocusId = remember(
        pendingDrawerEntryRequest?.id,
        focusableGroupIds,
        followViewModel.preferredGroupFocusId
    ) {
        when (pendingDrawerEntryRequest?.target) {
            MainContentFocusTarget.LeftEntry -> focusableGroupIds.firstOrNull()
            MainContentFocusTarget.RightEntry -> focusableGroupIds.lastOrNull()
            null -> when {
                followViewModel.preferredGroupFocusId in focusableGroupIds -> {
                    followViewModel.preferredGroupFocusId
                }

                else -> focusableGroupIds.firstOrNull()
            }
        }
    }

    val focusableUserKeys = remember(currentUsers) {
        currentUsers.map { it.stableKey }
    }
    val requestedUserFocusKey = remember(
        pendingDrawerEntryRequest?.id,
        focusableUserKeys,
        followViewModel.preferredDetailUserKey
    ) {
        when (pendingDrawerEntryRequest?.target) {
            MainContentFocusTarget.LeftEntry -> focusableUserKeys.firstOrNull()
            MainContentFocusTarget.RightEntry -> focusableUserKeys.lastOrNull()
            null -> followViewModel.preferredDetailUserKey ?: focusableUserKeys.firstOrNull()
        }
    }

    BackHandler(enabled = selectedGroupId != null && !followViewModel.showFollowGroupDialog) {
        followViewModel.exitGroupDetail()
    }

    LaunchedEffect(
        followViewModel.updating,
        selectedGroupId,
        requestedGroupFocusId
    ) {
        if (!followViewModel.updating &&
            selectedGroupId == null &&
            requestedGroupFocusId != null
        ) {
            navFocusRequester.requestFocus(scope)
        }
    }

    LaunchedEffect(
        followViewModel.updating,
        selectedGroupId,
        requestedUserFocusKey
    ) {
        if (!followViewModel.updating &&
            selectedGroupId != null &&
            requestedUserFocusKey != null
        ) {
            navFocusRequester.requestFocus(scope)
        }
    }

    LaunchedEffect(selectedGroupId) {
        if (selectedGroupId != null) {
            detailGridState.scrollToItem(0)
        }
    }

    LaunchedEffect(
        selectedGroupId,
        requestedGroupFocusId,
        requestedUserFocusKey
    ) {
        val ready = if (selectedGroupId == null) {
            requestedGroupFocusId != null
        } else {
            requestedUserFocusKey != null
        }
        if (ready) {
            onDefaultFocusReady?.invoke()
        }
    }

    LaunchedEffect(
        pendingDrawerEntryRequest?.id,
        selectedGroupId,
        requestedGroupFocusId,
        requestedUserFocusKey
    ) {
        val request = pendingDrawerEntryRequest ?: return@LaunchedEffect
        val ready = if (selectedGroupId == null) {
            requestedGroupFocusId != null
        } else {
            requestedUserFocusKey != null
        }
        if (!ready) return@LaunchedEffect

        navFocusRequester.requestFocus(scope)
        onDrawerEntryConsumed(request.id)
    }

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = LocalDensity.current.density * 1.25f,
            fontScale = LocalDensity.current.fontScale * 1.25f
        )
    ) {
        Scaffold(
            topBar = {
                Box(
                    modifier = Modifier.padding(
                        start = 48.dp,
                        top = 24.dp,
                        bottom = 8.dp,
                        end = 48.dp
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = title,
                            fontSize = 24.sp
                        )
                        Text(
                            text = stringResource(R.string.load_data_count, count),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .onFocusChanged { focusOnContent = it.hasFocus }
                    .onPreviewKeyEvent {
                        if (it.key == Key.Back) {
                            if (it.type == KeyEventType.KeyUp && focusOnContent) {
                                drawerFocusRequester.requestFocus()
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
                    }
            ) {
                when {
                    followViewModel.groupCards.isEmpty() && followViewModel.updating -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingTip()
                        }
                    }

                    followViewModel.groupCards.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyTip()
                        }
                    }

                    selectedGroupId == null -> {
                        FollowGroupGrid(
                            state = groupGridState,
                            groups = followViewModel.groupCards,
                            requestedFocusGroupId = requestedGroupFocusId,
                            navFocusRequester = navFocusRequester,
                            onGroupFocused = followViewModel::onGroupFocused,
                            onGroupClick = followViewModel::enterGroup
                        )
                    }

                    else -> {
                        FollowUserGrid(
                            state = detailGridState,
                            users = currentUsers,
                            requestedFocusUserKey = requestedUserFocusKey,
                            navFocusRequester = navFocusRequester,
                            onUserClick = { user ->
                                UpInfoActivity.actionStart(
                                    context = context,
                                    mid = user.mid,
                                    name = user.name
                                )
                            },
                            onUserLongClick = { user ->
                                followViewModel.openFollowGroupDialog(user)
                            }
                        )
                    }
                }
            }
        }

        FollowGroupSelectDialog(
            show = followViewModel.showFollowGroupDialog,
            title = "选择关注分组",
            tags = followViewModel.followTags.map { BlockTagItem(it.tagid, it.name, it.count) },
            initialSelectedTagIds = followViewModel.followGroupDialogInitialSelectedTagIds,
            onHideDialog = { followViewModel.hideFollowGroupDialog() },
            onSubmit = { selectedTagIds ->
                followViewModel.submitFollowGroupSelection(selectedTagIds)
            }
        )
    }
}

@Composable
private fun FollowGroupGrid(
    modifier: Modifier = Modifier,
    state: LazyGridState,
    groups: List<FollowGroupCardUi>,
    requestedFocusGroupId: Int?,
    navFocusRequester: FocusRequester,
    onGroupFocused: (Int) -> Unit,
    onGroupClick: (Int) -> Unit
) {
    val groupRequesters = remember(groups) {
        groups
            .filter { it.state == FollowGroupCardState.NORMAL }
            .associate { it.groupId to FocusRequester() }
    }
    val focusLinks = remember(groups) {
        buildGridHorizontalFocusLinks(
            itemCount = groups.size,
            columns = 4
        ) { index ->
            groups[index].state == FollowGroupCardState.NORMAL
        }
    }

    val firstFocusableGroupId = remember(groups) {
        groups.firstOrNull { it.state == FollowGroupCardState.NORMAL }?.groupId
    }
    val lastFocusableGroupId = remember(groups) {
        groups.lastOrNull { it.state == FollowGroupCardState.NORMAL }?.groupId
    }

    fun requesterForGroupId(groupId: Int?): FocusRequester {
        return when (groupId) {
            null -> navFocusRequester
            requestedFocusGroupId -> navFocusRequester
            else -> groupRequesters.getValue(groupId)
        }
    }

    fun requesterForIndex(index: Int): FocusRequester {
        val groupId = groups[index].groupId
        return requesterForGroupId(groupId)
    }

    TvLazyVerticalGrid(
        modifier = modifier,
        state = state,
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        itemsIndexed(
            items = groups,
            key = { _, item -> item.groupId }
        ) { index, group ->
            val enabled = group.state == FollowGroupCardState.NORMAL
            val focusLink = focusLinks[index]

            val cardModifier = when {
                !enabled -> Modifier.focusProperties { canFocus = false }
                else -> Modifier
                    .focusRequester(
                        if (group.groupId == requestedFocusGroupId) {
                            navFocusRequester
                        } else {
                            groupRequesters.getValue(group.groupId)
                        }
                    )
                    .focusProperties {
                        left = focusLink?.leftIndex?.let(::requesterForIndex)
                            ?: requesterForGroupId(lastFocusableGroupId)
                        right = focusLink?.rightIndex?.let(::requesterForIndex)
                            ?: requesterForGroupId(firstFocusableGroupId)
                    }
            }

            FollowGroupCard(
                modifier = cardModifier,
                group = group,
                onFocusChange = { hasFocus ->
                    if (hasFocus && enabled) {
                        onGroupFocused(group.groupId)
                    }
                },
                onClick = {
                    if (enabled) {
                        onGroupClick(group.groupId)
                    }
                }
            )
        }
    }
}

@Composable
private fun FollowUserGrid(
    modifier: Modifier = Modifier,
    state: LazyGridState,
    users: List<FollowUserUi>,
    requestedFocusUserKey: String?,
    navFocusRequester: FocusRequester,
    onUserClick: (FollowUserUi) -> Unit,
    onUserLongClick: (FollowUserUi) -> Unit
) {
    val userRequesters = remember(users) {
        users.associate { it.stableKey to FocusRequester() }
    }
    val focusLinks = remember(users) {
        buildGridHorizontalFocusLinks(
            itemCount = users.size,
            columns = 3
        ) { true }
    }

    val firstFocusableUserKey = remember(users) {
        users.firstOrNull()?.stableKey
    }
    val lastFocusableUserKey = remember(users) {
        users.lastOrNull()?.stableKey
    }

    fun requesterForUserKey(userKey: String?): FocusRequester {
        return when (userKey) {
            null -> navFocusRequester
            requestedFocusUserKey -> navFocusRequester
            else -> userRequesters.getValue(userKey)
        }
    }

    fun requesterForIndex(index: Int): FocusRequester {
        val userKey = users[index].stableKey
        return requesterForUserKey(userKey)
    }

    TvLazyVerticalGrid(
        modifier = modifier,
        state = state,
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (users.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyTip(text = "该分组暂无UP主")
            }
        } else {
            itemsIndexed(
                items = users,
                key = { _, item -> item.stableKey }
            ) { index, user ->
                val focusLink = focusLinks[index]

                val cardModifier = Modifier
                    .focusRequester(
                        if (user.stableKey == requestedFocusUserKey) {
                            navFocusRequester
                        } else {
                            userRequesters.getValue(user.stableKey)
                        }
                    )
                    .focusProperties {
                        left = focusLink?.leftIndex?.let(::requesterForIndex)
                            ?: requesterForUserKey(lastFocusableUserKey)
                        right = focusLink?.rightIndex?.let(::requesterForIndex)
                            ?: requesterForUserKey(firstFocusableUserKey)
                    }

                UpCard(
                    modifier = cardModifier,
                    face = user.avatar,
                    sign = user.sign,
                    username = user.name,
                    onFocusChange = {},
                    onClick = {
                        onUserClick(user)
                    },
                    onLongClick = {
                        onUserLongClick(user)
                    }
                )
            }
        }
    }
}

@Composable
private fun FollowGroupCard(
    modifier: Modifier = Modifier,
    group: FollowGroupCardUi,
    onFocusChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val enabled = group.state == FollowGroupCardState.NORMAL
    val cardModifier = modifier
        .onFocusChanged { onFocusChange(it.hasFocus) }
        .fillMaxWidth()
        .height(148.dp)

    if (enabled) {
        Surface(
            modifier = cardModifier,
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
            onClick = onClick
        ) {
            FollowGroupCardContent(group = group)
        }
    } else {
        Surface(
            modifier = cardModifier,
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RectangleShape
        ) {
            FollowGroupCardContent(group = group)
        }
    }
}

@Composable
private fun FollowGroupCardContent(group: FollowGroupCardUi) {
    val subtitle = when (group.state) {
        FollowGroupCardState.NORMAL -> "${group.count} 位UP主"
        FollowGroupCardState.EMPTY -> "空"
    }

    val titleStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = 24.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
    val subtitleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 18.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    SubcomposeLayout(
        modifier = Modifier.fillMaxSize()
    ) { constraints ->
        val maxTextWidth = 220.dp.roundToPx()
        val iconTextGap = 42.dp.roundToPx()

        val looseMaxWidth = minOf(maxTextWidth, constraints.maxWidth)

        val looseTitle = subcompose("title_loose") {
            Text(
                text = group.title,
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }.first().measure(
            Constraints(
                minWidth = 0,
                maxWidth = looseMaxWidth
            )
        )

        val looseSubtitle = subcompose("subtitle_loose") {
            Text(
                text = subtitle,
                style = subtitleStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }.first().measure(
            Constraints(
                minWidth = 0,
                maxWidth = looseMaxWidth
            )
        )

        var textBlockWidth = max(looseTitle.width, looseSubtitle.width).coerceAtLeast(1)

        val equalSpace = ((constraints.maxHeight - looseTitle.height - looseSubtitle.height) / 3)
            .coerceAtLeast(0)

        var iconSize = (looseTitle.height + equalSpace + looseSubtitle.height)
            .coerceAtLeast(1)

        val availableTextWidth = (constraints.maxWidth - iconSize - iconTextGap).coerceAtLeast(1)
        textBlockWidth = minOf(textBlockWidth, maxTextWidth, availableTextWidth)

        val titlePlaceable = subcompose("title_final") {
            Text(
                text = group.title,
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }.first().measure(
            Constraints(
                minWidth = textBlockWidth,
                maxWidth = textBlockWidth
            )
        )

        val subtitlePlaceable = subcompose("subtitle_final") {
            Text(
                text = subtitle,
                style = subtitleStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }.first().measure(
            Constraints(
                minWidth = textBlockWidth,
                maxWidth = textBlockWidth
            )
        )

        val desiredLineGap = 6.dp.roundToPx()
        val maxAvailableGap = (
                constraints.maxHeight - titlePlaceable.height - subtitlePlaceable.height
                ).coerceAtLeast(0)
        val lineGap = minOf(desiredLineGap, maxAvailableGap)

        iconSize = (titlePlaceable.height + lineGap + subtitlePlaceable.height)
            .coerceAtLeast(1)

        val iconPlaceable = subcompose("icon") {
            Icon(
                modifier = Modifier.fillMaxSize(),
                imageVector = relationGroupIcon(group),
                contentDescription = null
            )
        }.first().measure(
            Constraints.fixed(iconSize, iconSize)
        )

        val sidePadding = 10.dp.roundToPx()
        val contentWidth = iconPlaceable.width + iconTextGap + textBlockWidth
        val availableWidth = (constraints.maxWidth - sidePadding * 2).coerceAtLeast(0)
        val startX = sidePadding + ((availableWidth - contentWidth) / 2).coerceAtLeast(0)

        val iconX = startX
        val textX = iconX + iconPlaceable.width + iconTextGap

        val textBlockHeight = titlePlaceable.height + lineGap + subtitlePlaceable.height
        val titleY = ((constraints.maxHeight - textBlockHeight) / 2).coerceAtLeast(0)
        val subtitleY = titleY + titlePlaceable.height + lineGap
        val iconY = titleY - 5.dp.roundToPx()

        layout(constraints.maxWidth, constraints.maxHeight) {
            iconPlaceable.placeRelative(iconX, iconY)
            titlePlaceable.placeRelative(textX, titleY)
            subtitlePlaceable.placeRelative(textX, subtitleY)
        }
    }
}

private fun relationGroupIcon(group: FollowGroupCardUi): ImageVector {
    if (group.state == FollowGroupCardState.EMPTY) {
        return Icons.Rounded.FolderOff
    }

    return when (group.kind) {
        RelationGroupKind.DEFAULT -> Icons.Rounded.FolderShared
        RelationGroupKind.SPECIAL -> Icons.Rounded.FolderSpecial
        RelationGroupKind.NORMAL -> Icons.Rounded.FolderShared
        RelationGroupKind.ORPHAN -> Icons.Rounded.QuestionMark
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
                    model = face,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
