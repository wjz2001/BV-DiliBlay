package dev.aaa1115910.bv.screen.main.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.aaa1115910.biliapi.entity.cheese.CheeseEpisode
import dev.aaa1115910.biliapi.entity.cheese.CheeseSeasonDetail
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.wjzfocus.WjzFocusItemKey
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.component.UpIcon
import dev.aaa1115910.bv.component.TvGridFocusHost
import dev.aaa1115910.bv.wjzfocus.wjzFocusable
import dev.aaa1115910.bv.component.rememberTvGridFocusModifier
import dev.aaa1115910.bv.entity.VideoSource
import dev.aaa1115910.bv.component.videocard.VideoPartButton
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.BvKeyDirection
import dev.aaa1115910.bv.util.ImageSize
import dev.aaa1115910.bv.util.bvKeyDirection
import dev.aaa1115910.bv.util.focusedBorder
import dev.aaa1115910.bv.util.launchPlayerActivity
import dev.aaa1115910.bv.util.resizedImageUrl
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.common.LoadState
import dev.aaa1115910.bv.viewmodel.video.CheeseSeasonViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.math.floor

@Composable
fun CheeseSeasonScreen(
    cheeseSeasonViewModel: CheeseSeasonViewModel = koinViewModel()
) {
    val detail = cheeseSeasonViewModel.detail
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            cheeseSeasonViewModel.loadState == LoadState.Loading -> {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = "课程加载中..."
                )
            }

            cheeseSeasonViewModel.loadState == LoadState.Error -> {
                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = "课程加载失败"
                )
            }

            detail != null -> {
                CheeseSeasonContent(
                    detail = detail,
                    onEpisodeClick = { episode ->
                        val epId = episode.epId
                            .takeIf { it in 1L..Int.MAX_VALUE.toLong() }
                            ?.toInt()
                        val seasonId = detail.seasonId
                            .takeIf { it in 1L..Int.MAX_VALUE.toLong() }
                            ?.toInt()

                        when {
                            !episode.canView -> "该课时暂不可播放".toast(context)
                            episode.aid <= 0L || episode.cid <= 0L || epId == null -> {
                                "课时信息缺失，无法播放".toast(context)
                            }

                            else -> {
                                launchPlayerActivity(
                                    context = context,
                                    avid = episode.aid,
                                    cid = episode.cid,
                                    title = detail.title,
                                    partTitle = episode.title,
                                    played = episode.watchedHistory.coerceAtLeast(0) * 1000L,
                                    source = VideoSource.Cheese,
                                    epid = epId,
                                    seasonId = seasonId
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CheeseSeasonContent(
    detail: CheeseSeasonDetail,
    onEpisodeClick: (CheeseEpisode) -> Unit
) {
    var episodeListExpanded by remember { mutableStateOf(false) }
    val targetEpisodeIndex = remember(detail.episodes, detail.lastEpId) {
        detail.episodes.indexOfFirst { it.epId == detail.lastEpId }
            .takeIf { it >= 0 }
            ?: 0
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !event.nativeKeyEvent.isLongPress) {
                    return@onPreviewKeyEvent false
                }

                val keyDirection = event.bvKeyDirection()
                when {
                    keyDirection == BvKeyDirection.Left ||
                        event.key == Key.MediaRewind -> {
                        episodeListExpanded = true
                        true
                    }

                    keyDirection == BvKeyDirection.Right ||
                        event.key == Key.MediaFastForward -> {
                        episodeListExpanded = false
                        true
                    }

                    else -> false
                }
            }
    ) {
        val columnWidth = 460.dp
        val horizontalSpacing = 16.dp
        val paneSpacing = 32.dp
        val maxColumnCount = remember(maxWidth) {
            floor(
                ((maxWidth.value + horizontalSpacing.value) / (columnWidth.value + horizontalSpacing.value))
                    .toDouble()
            )
                .toInt()
                .coerceAtLeast(1)
        }
        val episodeListWidth = if (episodeListExpanded) maxWidth else columnWidth
        val introWidth = if (episodeListExpanded) {
            0.dp
        } else {
            (maxWidth - paneSpacing - columnWidth).coerceAtLeast(0.dp)
        }
        val introAlpha = if (episodeListExpanded) 0f else 1f
        val episodeColumnCount = if (episodeListExpanded) maxColumnCount else 1

        CheeseSeasonIntro(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(introWidth)
                .fillMaxHeight(),
            detail = detail,
            alpha = introAlpha,
            focusEnabled = !episodeListExpanded
        )

        CheeseEpisodeList(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(episodeListWidth)
                .fillMaxHeight(),
            episodes = detail.episodes,
            seasonId = detail.seasonId,
            columnCount = episodeColumnCount,
            targetEpisodeIndex = targetEpisodeIndex,
            horizontalSpacing = horizontalSpacing,
            expanded = episodeListExpanded,
            onEpisodeClick = onEpisodeClick
        )
    }
}

@Composable
private fun CheeseSeasonIntro(
    modifier: Modifier = Modifier,
    detail: CheeseSeasonDetail,
    alpha: Float,
    focusEnabled: Boolean
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    val scrollOffsetPx = remember(density) {
        with(density) { 60.dp.toPx() }
    }

    LaunchedEffect(detail.seasonId) {
        listState.scrollToItem(0)
    }

    LazyColumn(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.bvKeyDirection()) {
                    BvKeyDirection.Down -> {
                        scope.launch {
                            listState.animateScrollBy(scrollOffsetPx)
                        }
                        true
                    }

                    BvKeyDirection.Up -> {
                        scope.launch {
                            listState.animateScrollBy(-scrollOffsetPx)
                        }
                        true
                    }

                    BvKeyDirection.Left,
                    BvKeyDirection.Right,
                    null -> false
                }
            },
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AsyncImage(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                model = detail.cover.resizedImageUrl(ImageSize.Default),
                contentDescription = null,
                contentScale = ContentScale.Fit
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = detail.title,
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val upMid = detail.upMid
                if (upMid != null && detail.upName.isNotBlank()) {
                    CheeseUpButton(
                        modifier = Modifier
                            .wjzFocusable(
                                nodeId = WjzFocusNodeId("cheese/${detail.seasonId}/up"),
                                layer = WjzFocusLayer.Content,
                                fallback = focusEnabled,
                                enabled = focusEnabled
                            ),
                        name = detail.upName,
                        onClick = {
                            UpInfoActivity.actionStart(
                                context = context,
                                mid = upMid,
                                name = detail.upName
                            )
                        }
                    )
                }
                if (upMid == null || detail.upName.isBlank()) {
                    Spacer(modifier = Modifier)
                }
                detail.releaseInfo?.let {
                    Text(
                        text = it,
                        color = C.onSurfaceVariant,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp)
                    )
                }
                detail.progressText?.let {
                    Text(
                        text = "上次看到：$it",
                        color = C.primary,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp)
                    )
                }
            }
        }

        detail.subtitle?.let { subtitle ->
            item {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp)
                )
            }
        }

        detail.courseContent?.let { content ->
            item {
                Text(
                    text = content,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp)
                )
            }
        }

        itemsIndexed(detail.briefImages) { _, image ->
            val imageModifier = image.aspectRatio
                ?.takeIf { it > 0f }
                ?.let { Modifier.fillMaxWidth().aspectRatio(1f / it) }
                ?: Modifier.fillMaxWidth()

            AsyncImage(
                modifier = imageModifier,
                model = image.url,
                contentDescription = null,
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun CheeseEpisodeList(
    modifier: Modifier = Modifier,
    episodes: List<CheeseEpisode>,
    seasonId: Long,
    columnCount: Int,
    targetEpisodeIndex: Int,
    horizontalSpacing: Dp,
    expanded: Boolean,
    onEpisodeClick: (CheeseEpisode) -> Unit
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(episodes, targetEpisodeIndex, expanded) {
        if (episodes.isNotEmpty()) {
            gridState.scrollToItem(targetEpisodeIndex + 1)
        }
    }

    TvGridFocusHost(
        modifier = modifier,
        state = gridState,
        columns = GridCells.Fixed(columnCount),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        nodeIdPrefix = "cheese/$seasonId/episodes",
        contentPadding = PaddingValues(bottom = 32.dp),
        focusItemCount = episodes.size,
        itemKeys = episodes.map { WjzFocusItemKey("Long:${it.epId}") },
        focusColumnCount = columnCount,
        enableRowHorizontalWrap = expanded
    ) {
        item(span = { GridItemSpan(columnCount) }) {
            Text(
                text = "课程列表",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        gridItemsIndexed(episodes, key = { _, episode -> episode.epId }) { index, episode ->
            val duration = episode.duration.coerceAtLeast(1)
            VideoPartButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(rememberTvGridFocusModifier(index)),
                index = episode.index.takeIf { it > 0 } ?: (index + 1),
                title = episode.title,
                duration = duration,
                played = if (episode.watched) duration else episode.watchedHistory.coerceAtLeast(0),
                onClick = { onEpisodeClick(episode) }
            )
        }
    }
}

@Composable
private fun CheeseUpButton(
    modifier: Modifier = Modifier,
    name: String,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RectangleShape)
            .background(Color.Transparent)
            .focusedBorder(RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UpIcon(color = C.onSurface)
        Text(
            text = name,
            color = C.onSurface,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
