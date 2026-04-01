package dev.aaa1115910.bv.screen.user

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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import kotlin.math.max
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
import dev.aaa1115910.bv.component.LoadingTip
import dev.aaa1115910.bv.component.TvLazyVerticalGrid
import dev.aaa1115910.bv.relation.RelationGroupKind
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.requestFocus
import dev.aaa1115910.bv.viewmodel.user.FollowGroupCardState
import dev.aaa1115910.bv.viewmodel.user.FollowGroupCardUi
import dev.aaa1115910.bv.viewmodel.user.FollowUserUi
import dev.aaa1115910.bv.viewmodel.user.FollowViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun FollowScreen(
    modifier: Modifier = Modifier,
    followViewModel: FollowViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val groupGridState = rememberLazyGridState()
    val detailGridState = rememberLazyGridState()
    val groupFocusRequester = remember { FocusRequester() }
    val detailFocusRequester = remember { FocusRequester() }

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

    BackHandler(enabled = selectedGroupId != null) {
        followViewModel.exitGroupDetail()
    }

    LaunchedEffect(
        followViewModel.updating,
        selectedGroupId,
        followViewModel.preferredGroupFocusId
    ) {
        val preferredGroupFocusId = followViewModel.preferredGroupFocusId
        if (!followViewModel.updating && selectedGroupId == null && preferredGroupFocusId != null) {
            groupFocusRequester.requestFocus(scope)
        }
    }

    LaunchedEffect(
        followViewModel.updating,
        selectedGroupId,
        followViewModel.preferredDetailUserKey
    ) {
        val preferredDetailUserKey = followViewModel.preferredDetailUserKey
        if (!followViewModel.updating && selectedGroupId != null && preferredDetailUserKey != null) {
            detailFocusRequester.requestFocus(scope)
        }
    }

    LaunchedEffect(selectedGroupId) {
        if (selectedGroupId != null) {
            detailGridState.scrollToItem(0)
        }
    }

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = LocalDensity.current.density * 1.25f,
            fontScale = LocalDensity.current.fontScale * 1.25f
        )
    ) {
        Scaffold(
            modifier = modifier,
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
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        ) { innerPadding ->
            when {
                followViewModel.groupCards.isEmpty() && followViewModel.updating -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingTip()
                    }
                }

                followViewModel.groupCards.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyTip()
                    }
                }

                selectedGroupId == null -> {
                    FollowGroupGrid(
                        modifier = Modifier.padding(innerPadding),
                        state = groupGridState,
                        groups = followViewModel.groupCards,
                        focusGroupId = followViewModel.preferredGroupFocusId,
                        focusRequester = groupFocusRequester,
                        onGroupFocused = followViewModel::onGroupFocused,
                        onGroupClick = followViewModel::enterGroup
                    )
                }

                else -> {
                    FollowUserGrid(
                        modifier = Modifier.padding(innerPadding),
                        state = detailGridState,
                        users = currentUsers,
                        focusUserKey = followViewModel.preferredDetailUserKey,
                        focusRequester = detailFocusRequester,
                        onUserClick = { user ->
                            UpInfoActivity.actionStart(
                                context = context,
                                mid = user.mid,
                                name = user.name
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowGroupGrid(
    modifier: Modifier = Modifier,
    state: LazyGridState,
    groups: List<FollowGroupCardUi>,
    focusGroupId: Int?,
    focusRequester: FocusRequester,
    onGroupFocused: (Int) -> Unit,
    onGroupClick: (Int) -> Unit
) {
    TvLazyVerticalGrid(
        modifier = modifier,
        state = state,
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(
            items = groups,
            key = { it.groupId }
        ) { group ->
            val cardModifier = if (group.groupId == focusGroupId) {
                Modifier.focusRequester(focusRequester)
            } else {
                Modifier
            }

            FollowGroupCard(
                modifier = cardModifier,
                group = group,
                onFocusChange = { hasFocus ->
                    if (hasFocus) {
                        onGroupFocused(group.groupId)
                    }
                },
                onClick = {
                    onGroupClick(group.groupId)
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
    focusUserKey: String?,
    focusRequester: FocusRequester,
    onUserClick: (FollowUserUi) -> Unit
) {
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
            items(
                items = users,
                key = { it.stableKey }
            ) { user ->
                val cardModifier = if (user.stableKey == focusUserKey) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }

                UpCard(
                    modifier = cardModifier,
                    face = user.avatar,
                    sign = user.sign,
                    username = user.name,
                    onFocusChange = {},
                    onClick = {
                        onUserClick(user)
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
        .focusProperties { canFocus = enabled }
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
            shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.large),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(width = 3.dp, color = Color.White),
                    shape = MaterialTheme.shapes.large
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
            shape = MaterialTheme.shapes.large
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

        // 先测实际文字宽度
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
                color = Color.White.copy(alpha = 0.68f),
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

        // 先根据单行文字高度估算图标高度
        val equalSpace = ((constraints.maxHeight - looseTitle.height - looseSubtitle.height) / 3)
            .coerceAtLeast(0)

        var iconSize = (looseTitle.height + equalSpace + looseSubtitle.height)
            .coerceAtLeast(1)

        // 避免整体超宽
        val availableTextWidth = (constraints.maxWidth - iconSize - iconTextGap).coerceAtLeast(1)
        textBlockWidth = minOf(textBlockWidth, maxTextWidth, availableTextWidth)

        // 正式测量两行文字：同宽，文字居中
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
                color = Color.White.copy(alpha = 0.68f),
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

        // 上边距 = 行间距 = 下边距
        val desiredLineGap = 6.dp.roundToPx() // 这里改小一点，两行就更近
        val maxAvailableGap = (
                constraints.maxHeight - titlePlaceable.height - subtitlePlaceable.height
                ).coerceAtLeast(0)
        val lineGap = minOf(desiredLineGap, maxAvailableGap)

        // 图标高度仍然和两行文字整体高度对齐
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

        // 整组内容水平居中：保证图标左边距 = 文字右边距
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
fun UpCard(
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
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.large),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 3.dp, color = Color.White),
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
                color = Color.White
            ) {
                AsyncImage(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
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