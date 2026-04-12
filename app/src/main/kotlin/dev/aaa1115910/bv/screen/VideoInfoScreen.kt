package dev.aaa1115910.bv.screen

import java.util.Locale
import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SuggestionChip
import androidx.tv.material3.SuggestionChipDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.aaa1115910.biliapi.entity.FavoriteFolderMetadata
import dev.aaa1115910.biliapi.entity.video.Dimension
import dev.aaa1115910.biliapi.entity.video.Tag
import dev.aaa1115910.biliapi.entity.video.VideoPage
import dev.aaa1115910.biliapi.entity.video.season.Episode
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.video.SeasonInfoActivity
import dev.aaa1115910.bv.activities.video.TagActivity
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.component.BlockTagItem
import dev.aaa1115910.bv.component.CoAuthorsDialogHost
import dev.aaa1115910.bv.component.FollowGroupSelectDialog
import dev.aaa1115910.bv.component.UpIcon
import dev.aaa1115910.bv.component.rememberCoAuthorsDialogState
import dev.aaa1115910.bv.component.comments.VideoCommentsDialog
import dev.aaa1115910.bv.component.videocard.SmallVideoCard
import dev.aaa1115910.bv.component.videocard.SmallVideoCardGridHost
import dev.aaa1115910.bv.component.videocard.VideosRow
import dev.aaa1115910.bv.component.videocard.VideosRowCore
import dev.aaa1115910.bv.component.buttons.CoinButton
import dev.aaa1115910.bv.component.buttons.CommentButton
import dev.aaa1115910.bv.component.buttons.FavoriteButton
import dev.aaa1115910.bv.component.buttons.LikeButton
import dev.aaa1115910.bv.entity.VideoListItem
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.ui.effect.VideoDetailUiEffect

import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.formatHourMinSec
import dev.aaa1115910.bv.util.formatPubTimeString
import dev.aaa1115910.bv.util.launchPlayerActivity
import dev.aaa1115910.bv.util.toWanString
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.util.focusedBorder
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import dev.aaa1115910.bv.viewmodel.video.VideoDetailState
import dev.aaa1115910.bv.viewmodel.video.VideoDetailViewModel
import dev.aaa1115910.bv.viewmodel.video.VideoInfoState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.math.ceil

private fun Modifier.sectionFocusNav(
    up: FocusRequester? = null,
    down: FocusRequester? = null,
): Modifier = this.focusProperties {
    onExit = {
        when (requestedFocusDirection) {
            FocusDirection.Up -> {
                if (up != null) {
                    up.requestFocus()
                    cancelFocusChange()
                }
            }

            FocusDirection.Down -> {
                if (down != null) {
                    down.requestFocus()
                    cancelFocusChange()
                }
            }

            else -> Unit
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoInfoScreen(
    modifier: Modifier = Modifier,
    videoDetailViewModel: VideoDetailViewModel = koinViewModel(),
    toViewViewModel: ToViewViewModel = koinViewModel(),
) {
    val context = (LocalContext.current) as Activity
    val logger = KotlinLogging.logger { }

    val defaultFocusRequester = remember { FocusRequester() }
    val uiState by videoDetailViewModel.uiState.collectAsState()

    val scope = rememberCoroutineScope()

    var showFollowGroupDialog by remember { mutableStateOf(false) }
    var followGroupDialogWasFollowing by remember { mutableStateOf(false) }
    var followGroupDialogInitialSelectedTagIds by remember { mutableStateOf<List<Int>>(emptyList()) }

    fun normalizeTagIds(ids: List<Int>): List<Int> {
        val dedup = ids.distinct().sorted()
        return if (dedup.contains(0) && dedup.size > 1) listOf(0) else dedup
    }

    fun openFollowGroupDialog() {
        val upMid = uiState.videoDetailState?.author?.mid ?: return
        if (showFollowGroupDialog) return

        scope.launch {
            val tagsOk = runCatching { videoDetailViewModel.loadFollowTagsIfNeeded() }
                .getOrDefault(false)
            if (!tagsOk) {
                "未获取到关注分组列表，已取消打开以避免误操作".toast(context)
                return@launch
            }

            val tagsSnapshot = videoDetailViewModel.uiState.value.followTags

            val (wasFollowing, initialSelected) = runCatching {
                videoDetailViewModel.getUpFollowStateAndTagIds(upMid)
            }.getOrElse {
                val fallbackFollowing = uiState.isFollowingUp
                val fallbackSelected = if (fallbackFollowing) listOf(0) else emptyList()
                fallbackFollowing to fallbackSelected
            }

            val presentIds = tagsSnapshot.map { it.tagid }.toSet()
            val normalizedInitial = normalizeTagIds(initialSelected)
            val filteredInitial = normalizedInitial.filter { presentIds.contains(it) }

            val safeInitial = when {
                wasFollowing && filteredInitial.isEmpty() && presentIds.contains(0) -> listOf(0)
                else -> filteredInitial
            }
            if (wasFollowing && safeInitial.isEmpty()) {
                "未能解析当前关注分组，已取消打开以避免误操作".toast(context)
                return@launch
            }

            followGroupDialogWasFollowing = wasFollowing
            followGroupDialogInitialSelectedTagIds = safeInitial
            showFollowGroupDialog = true
        }
    }

    var lastPlayedAid by remember { mutableLongStateOf(0L) }

    val bringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float
            ): Float {
                val targetPosition = containerSize * 0.3f
                return offset - targetPosition
            }
        }
    }

    fun performLaunchPlayer(
        targetAid: Long,
        targetCid: Long,
        targetTitle: String,
        targetPartTitle: String,
        isFromSeason: Boolean
    ) {
        val videoDetailState = uiState.videoDetailState ?: return

        val playedTime = if (targetCid == videoDetailState.lastPlayedCid) {
            videoDetailState.lastPlayedTime * 1000
        } else {
            0
        }

        if (lastPlayedAid != targetAid) {
            videoDetailViewModel.loadVideoDetail(targetAid)
            lastPlayedAid = targetAid
        }

        launchPlayerActivity(
            context = context,
            avid = targetAid,
            cid = targetCid,
            title = targetTitle,
            partTitle = targetPartTitle,
            played = playedTime,
            fromSeason = isFromSeason,
            author = videoDetailState.author
        )
    }

    fun playCurrentVideo(cid: Long? = null) {
        val videoDetailState = uiState.videoDetailState ?: return
        val targetCid = cid ?: videoDetailState.cid

        val newVideoList = mutableListOf<VideoListItem>()
        if (videoDetailState.ugcSeason != null) {
            val currentSection = videoDetailState.ugcSeason.sections.find { section ->
                section.episodes.any { it.cid == targetCid || it.aid == videoDetailState.aid }
            } ?: videoDetailState.ugcSeason.sections.firstOrNull()

            currentSection?.episodes?.forEach { episode ->
                newVideoList.add(
                    VideoListItem(
                        aid = episode.aid,
                        cid = episode.cid,
                        seasonId = videoDetailState.ugcSeason.id,
                        title = episode.title
                    )
                )
            }
        } else {
            newVideoList.add(
                VideoListItem(
                    aid = videoDetailState.aid,
                    cid = videoDetailState.cid,
                    title = videoDetailState.title
                )
            )
        }
        videoDetailViewModel.updateVideoList(newVideoList)

        val partTitle = videoDetailState.pages.find { it.cid == targetCid }?.title
            ?: videoDetailState.ugcSeason?.sections?.flatMap { it.episodes }
                ?.find { it.cid == targetCid }?.title
            ?: ""

        performLaunchPlayer(
            targetAid = videoDetailState.aid,
            targetCid = targetCid,
            targetTitle = videoDetailState.title,
            targetPartTitle = partTitle,
            isFromSeason = uiState.fromSeason
        )
    }

    LaunchedEffect(Unit) {
        videoDetailViewModel.uiEvent.collect { event ->
            when (event) {
                is VideoDetailUiEffect.ShowToast -> event.message.toast(context)
                is VideoDetailUiEffect.LaunchPlayerActivity -> playCurrentVideo(event.cid)
                is VideoDetailUiEffect.DirectlyPlay -> {
                    playCurrentVideo(event.cid)
                    context.finish()
                }

                is VideoDetailUiEffect.LaunchSeasonInfoActivity -> {
                    logger.fInfo { "Redirect to season ${event.seasonId}" }
                    SeasonInfoActivity.actionStart(
                        context = context,
                        seasonId = event.seasonId,
                        epId = event.epid,
                        proxyArea = event.proxyArea
                    )
                    context.finish()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        toViewViewModel.uiEvent.collect { event ->
            when (event) {
                is UiEffect.ShowToast -> event.message.toast(context)
            }
        }
    }

    LaunchedEffect(uiState.loadingState) {
        if (uiState.loadingState == VideoInfoState.Success && !uiState.shouldShowLoading) {
            defaultFocusRequester.requestFocus()
        }
    }

    when {
        uiState.shouldShowLoading -> {
            FullScreenMessage(message = "Loading...")
        }

        uiState.loadingState == VideoInfoState.Error -> {
            FullScreenMessage(message = uiState.errorTip)
        }

        else -> {
            val videoDetailState = uiState.videoDetailState
            if (videoDetailState == null) {
                FullScreenMessage(message = "Loading...")
                return
            }

            val partEntry = remember { FocusRequester() }
            val relatedEntry = remember { FocusRequester() }

            val displayedUgcSections = remember(videoDetailState.ugcSeason) {
                videoDetailState.ugcSeason?.sections.orEmpty()
                    .filter { it.episodes.isNotEmpty() }
            }

            val ugcEntries = remember(displayedUgcSections.size) {
                List(displayedUgcSections.size) { FocusRequester() }
            }

            val relatedVideos = videoDetailState.relatedVideos

            fun firstBelowPart(): FocusRequester? =
                ugcEntries.firstOrNull() ?: relatedEntry.takeIf { relatedVideos.isNotEmpty() }

            fun lastAboveRelated(): FocusRequester =
                ugcEntries.lastOrNull() ?: partEntry

            CompositionLocalProvider(
                LocalBringIntoViewSpec provides bringIntoViewSpec
            ) {
                Scaffold(
                    containerColor = C.background
                ) { innerPadding ->
                    BoxWithConstraints(
                        modifier = modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        val density = LocalDensity.current
                        val viewportHeightPx = constraints.maxHeight

                        var videoInfoHeightPx by remember { mutableIntStateOf(0) }
                        var partRowHeightPx by remember { mutableIntStateOf(0) }
                        val ugcRowHeightsPx = remember { mutableStateMapOf<Int, Int>() }
                        var relatedRowHeightPx by remember { mutableIntStateOf(0) }

                        //related 上方各个 section 之间的间距
                        val itemSpacingPx = with(density) { 4.dp.roundToPx() }

                        val topPaddingPx = with(density) { 8.dp.roundToPx() }


                        val ugcTotalHeightPx = displayedUgcSections.indices.sumOf {
                            ugcRowHeightsPx[it] ?: 0
                        }

                        val baseItemCountAboveRelated = 2 + displayedUgcSections.size
                        val spacingAboveRelatedPx = if (relatedVideos.isNotEmpty()) {
                            baseItemCountAboveRelated * itemSpacingPx
                        } else {
                            0
                        }

                        val contentAboveRelatedPx = topPaddingPx +
                                videoInfoHeightPx +
                                partRowHeightPx +
                                ugcTotalHeightPx +
                                spacingAboveRelatedPx

                        //当前屏幕里，在“上面的内容 + related 自己”都摆完以后，还剩多少垂直空间
                        val spaceForRelatedSectionPx = (
                                viewportHeightPx -
                                        contentAboveRelatedPx -
                                        relatedRowHeightPx
                                )

                        //控制底部留白
                        val relatedBottomPaddingPx = if (relatedVideos.isNotEmpty()) {
                            spaceForRelatedSectionPx.coerceIn(
                                with(density) { 2.dp.roundToPx() },
                                with(density) { 32.dp.roundToPx() }
                            )
                        } else {
                            0
                        }

                        //吃掉剩余的其余空间
                        val relatedTopSpacerPx = if (relatedVideos.isNotEmpty()) {
                            (spaceForRelatedSectionPx - relatedBottomPaddingPx).coerceAtLeast(0)
                        } else {
                            0
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 0.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item(key = "video_info_data") {
                                VideoInfoData(
                                    modifier = Modifier
                                        .focusGroup()
                                        .onSizeChanged { videoInfoHeightPx = it.height },
                                    defaultFocusRequester = defaultFocusRequester,
                                    coverDownRequester = partEntry,
                                    videoDetail = videoDetailState,
                                    isFollowing = uiState.isFollowingUp,
                                    tags = videoDetailState.tags,
                                    isFavorite = videoDetailState.isFavorite,
                                    isLiked = videoDetailState.isLiked,
                                    isCoined = videoDetailState.isCoined,
                                    userFavoriteFolders = uiState.favoriteFolders,
                                    favoriteFolderIds = uiState.videoFavoriteFolderIds.toList(),
                                    onClickCover = {
                                        logger.fInfo { "Click video cover" }
                                        playCurrentVideo(videoDetailState.lastPlayedCid.takeIf { it != 0L })
                                    },
                                    onClickUp = {
                                        UpInfoActivity.actionStart(
                                            context,
                                            mid = videoDetailState.author.mid,
                                            name = videoDetailState.author.name
                                        )
                                    },
                                    onAddFollow = { openFollowGroupDialog() },
                                    onDelFollow = { openFollowGroupDialog() },
                                    onClickTip = { tag ->
                                        TagActivity.actionStart(
                                            context = context,
                                            tagId = tag.id,
                                            tagName = tag.name
                                        )
                                    },
                                    onAddToDefaultFavoriteFolder = {
                                        videoDetailViewModel.addVideoToDefaultFavoriteFolder()
                                    },
                                    onUpdateFavoriteFolders = {
                                        videoDetailViewModel.updateVideoFavoriteData(it)
                                    },
                                    onUpdateLiked = { liked ->
                                        videoDetailViewModel.updateVideoLiked(liked)
                                    },
                                    onSendVideoCoin = {
                                        videoDetailViewModel.sendVideoCoin()
                                    },
                                    onSendVideoOneClickTripleAction = {
                                        videoDetailViewModel.sendVideoOneClickTripleAction()
                                    }
                                )
                            }

                            item(key = "video_part_row") {
                                VideoPartRow(
                                    modifier = Modifier
                                        .sectionFocusNav(
                                            up = defaultFocusRequester,
                                            down = firstBelowPart()
                                        )
                                        .onSizeChanged { partRowHeightPx = it.height },
                                    entryFocusRequester = partEntry,
                                    upFocusRequester = defaultFocusRequester,
                                    downFocusRequester = firstBelowPart(),
                                    pages = videoDetailState.pages,
                                    lastPlayedCid = videoDetailState.lastPlayedCid,
                                    lastPlayedTime = videoDetailState.lastPlayedTime,
                                    enablePartListDialog = (videoDetailState.pages.size > 5),
                                    onClick = { cid -> playCurrentVideo(cid) }
                                )
                            }

                            videoDetailState.ugcSeason?.let { season ->
                                itemsIndexed(
                                    items = displayedUgcSections,
                                    key = { index, section ->
                                        "ugc_${index}_${section.title}_${section.episodes.firstOrNull()?.aid ?: 0L}"
                                    }
                                ) { index, section ->
                                    val upRequester =
                                        if (index == 0) partEntry else ugcEntries[index - 1]
                                    val downRequester = ugcEntries.getOrNull(index + 1)
                                        ?: relatedEntry.takeIf { relatedVideos.isNotEmpty() }

                                    VideoUgcSeasonRow(
                                        modifier = Modifier
                                            .sectionFocusNav(
                                                up = upRequester,
                                                down = downRequester
                                            )
                                            .onSizeChanged { ugcRowHeightsPx[index] = it.height },
                                        entryFocusRequester = ugcEntries[index],
                                        upFocusRequester = upRequester,
                                        downFocusRequester = downRequester,
                                        title = if (displayedUgcSections.size == 1) {
                                            season.title
                                        } else {
                                            section.title
                                        },
                                        episodes = section.episodes,
                                        onEpisodeClick = { episode: Episode ->
                                            logger.fInfo {
                                                "Click ugc season part: [aid:${episode.aid}, cid:${episode.cid}]"
                                            }

                                            val episodeDisplayTitle =
                                                episode.longTitle.ifBlank { episode.title }

                                            if (Prefs.showVideoInfo) {
                                                VideoInfoActivity.actionStart(context, episode.aid)
                                            } else {
                                                videoDetailViewModel.updateVideoList(
                                                    section.episodes.map {
                                                        VideoListItem(
                                                            aid = it.aid,
                                                            cid = it.cid,
                                                            seasonId = season.id,
                                                            title = it.longTitle.ifBlank { it.title }
                                                        )
                                                    }
                                                )
                                                performLaunchPlayer(
                                                    targetAid = episode.aid,
                                                    targetCid = episode.cid,
                                                    targetTitle = episodeDisplayTitle,
                                                    targetPartTitle = "",
                                                    isFromSeason = true
                                                )
                                            }
                                        }
                                    )
                                }
                            }

                            if (relatedVideos.isNotEmpty()) {
                                item(key = "related_videos_section") {
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Spacer(
                                            modifier = Modifier.height(
                                                with(density) { relatedTopSpacerPx.toDp() }
                                            )
                                        )

                                        VideosRow(
                                            modifier = Modifier
                                                .onSizeChanged { relatedRowHeightPx = it.height },
                                            header = stringResource(R.string.video_info_related_video_title),
                                            videos = relatedVideos,
                                            entryFocusRequester = relatedEntry,
                                            upFocusRequester = lastAboveRelated(),
                                            downFocusRequester = null,
                                            onVideoClicked = { videoData ->
                                                if (videoData.jumpToSeason) {
                                                    SeasonInfoActivity.actionStart(
                                                        context = context,
                                                        epId = videoData.epId!!,
                                                        proxyArea = ProxyArea.checkProxyArea(videoData.title)
                                                    )
                                                } else {
                                                    VideoInfoActivity.actionStart(
                                                        context = context,
                                                        aid = videoData.avid
                                                    )
                                                }
                                            },
                                            onAddWatchLater = { aid ->
                                                toViewViewModel.addToView(aid)
                                            },
                                            onGoToDetailPage = { aid ->
                                                VideoInfoActivity.actionStart(
                                                    context = context,
                                                    fromController = true,
                                                    aid = aid
                                                )
                                            },
                                            onGoToUpPage = { mid, upName ->
                                                UpInfoActivity.actionStart(context, mid, upName)
                                            }
                                        )

                                        Spacer(
                                            modifier = Modifier.height(
                                                with(density) { relatedBottomPaddingPx.toDp() }
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        FollowGroupSelectDialog(
                            show = showFollowGroupDialog,
                            title = "选择关注分组",
                            tags = uiState.followTags.map {
                                BlockTagItem(it.tagid, it.name, it.count)
                            },
                            initialSelectedTagIds = followGroupDialogInitialSelectedTagIds,
                            onHideDialog = { showFollowGroupDialog = false },
                            onSubmit = { selectedTagIds ->
                                val finalSelected = normalizeTagIds(selectedTagIds)
                                val initialSelected =
                                    normalizeTagIds(followGroupDialogInitialSelectedTagIds)

                                if (finalSelected == initialSelected && followGroupDialogWasFollowing) {
                                    return@FollowGroupSelectDialog
                                }

                                videoDetailViewModel.submitFollowGroupSelection(
                                    wasFollowing = followGroupDialogWasFollowing,
                                    initialSelectedTagIds = initialSelected,
                                    selectedTagIds = finalSelected
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.background),
    ) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = message
        )
    }
}

@Composable
fun ArgueTip(
    modifier: Modifier = Modifier,
    text: String
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 50.dp),
        colors = SurfaceDefaults.colors(
            containerColor = Color.Yellow.copy(alpha = 0.2f),
            contentColor = Color.Yellow
        ),
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 8.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = Color.Yellow
            )
            Text(text = text)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoInfoData(
    modifier: Modifier = Modifier,
    defaultFocusRequester: FocusRequester,
    coverDownRequester: FocusRequester? = null,
    videoDetail: VideoDetailState,
    isFollowing: Boolean,
    tags: List<Tag>,
    isFavorite: Boolean,
    isLiked: Boolean,
    isCoined: Boolean,
    userFavoriteFolders: List<FavoriteFolderMetadata> = emptyList(),
    favoriteFolderIds: List<Long> = emptyList(),
    onClickCover: () -> Unit,
    onClickUp: () -> Unit,
    onAddFollow: () -> Unit,
    onDelFollow: () -> Unit,
    onClickTip: (Tag) -> Unit,
    onAddToDefaultFavoriteFolder: () -> Unit,
    onUpdateFavoriteFolders: (List<Long>) -> Unit,
    onUpdateLiked: (Boolean) -> Unit,
    onSendVideoCoin: () -> Unit,
    onSendVideoOneClickTripleAction: () -> Unit
) {
    val localDensity = LocalDensity.current
    val context = LocalContext.current
    val coAuthorsDialogState = rememberCoAuthorsDialogState()
    var heightIs by remember { mutableStateOf(0.dp) }

    var showCommentsDialog by remember { mutableStateOf(false) }

    val upButtonFocusRequester = remember { FocusRequester() }
    val actionFavoriteFocusRequester = remember { FocusRequester() }
    val actionCommentFocusRequester = remember { FocusRequester() }
    val actionLikeFocusRequester = remember { FocusRequester() }
    val actionCoinFocusRequester = remember { FocusRequester() }
    val descriptionFocusRequester = remember { FocusRequester() }

    val hasDescription = remember(videoDetail.description) {
        val normalized = videoDetail.description.trim()
        normalized.isNotEmpty() && normalized != "-"
    }
    val actionDownRequester = if (hasDescription) descriptionFocusRequester else coverDownRequester

    Row(
        modifier = modifier
            .padding(horizontal = 50.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier
                .focusRequester(defaultFocusRequester)
                .focusProperties {
                    right = actionFavoriteFocusRequester
                    if (coverDownRequester != null) {
                        down = coverDownRequester
                    }
                }
                .weight(2.7f)
                .aspectRatio(1.6f)
                .onGloballyPositioned { coordinates ->
                    heightIs = with(localDensity) { coordinates.size.height.toDp() }
                },
            onClick = onClickCover,
            scale = ClickableSurfaceDefaults.scale(
                focusedScale = 1.05f
            ),
            shape = ClickableSurfaceDefaults.shape(
                shape = RectangleShape
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(
                        width = 3.dp,
                        color = C.outline
                    ),
                    shape = RectangleShape
                )
            ),
        ) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = videoDetail.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(48.dp))

        Column(
            modifier = Modifier
                .weight(7.3f)
                .height(heightIs),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = videoDetail.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 40.sp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Top,
                        trim = LineHeightStyle.Trim.Both
                    )
                ),
                maxLines = 2,
                lineHeight = 46.sp,
                overflow = TextOverflow.Ellipsis,
                color = C.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val coAuthorCount = remember(videoDetail.coAuthors) {
                        videoDetail.coAuthors.distinctBy { it.mid }.size
                    }
                    var upButtonHeightPx by remember { mutableIntStateOf(0) }
                    val density = LocalDensity.current
                    val fallbackSize = 6.dp
                    val squareSize = remember(upButtonHeightPx, density) {
                        if (upButtonHeightPx > 0) with(density) { upButtonHeightPx.toDp() } else fallbackSize
                    }

                    if (coAuthorCount > 1) {
                        Surface(
                            modifier = Modifier
                                .size(squareSize)
                                .aspectRatio(1f),
                            onClick = { coAuthorsDialogState.open(videoDetail.coAuthors) },
                            shape = ClickableSurfaceDefaults.shape(shape = RectangleShape),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = C.surfaceVariant,
                                focusedContainerColor = C.surfaceVariant,
                                pressedContainerColor = C.surfaceVariant
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(width = 3.dp, color = C.selectedBorder),
                                    shape = RectangleShape
                                )
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Group,
                                contentDescription = "联合投稿",
                                tint = C.onSurface,
                                modifier = Modifier.padding(3.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier.onSizeChanged { upButtonHeightPx = it.height }
                    ) {
                        UpButton(
                            name = videoDetail.author.name,
                            followed = isFollowing,
                            onClickUp = onClickUp,
                            onAddFollow = onAddFollow,
                            onDelFollow = onDelFollow,
                            upInfoModifier = Modifier
                                .focusRequester(upButtonFocusRequester)
                                .focusProperties {
                                    down = actionFavoriteFocusRequester
                                },
                            followModifier = Modifier.focusProperties {
                                down = actionFavoriteFocusRequester
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.labelMedium.copy(
                            fontSize = 25.sp
                        )
                    ) {
                        Text(text = "发布于 ${videoDetail.publishDate.formatPubTimeString()}")
                        Text(text = "·")
                        Text(text = "播放量 ${(videoDetail.stat.view).toWanString()}")
                        Text(text = "·")
                        Text(text = "弹幕 ${(videoDetail.stat.danmaku).toWanString()}")
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.End)
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FavoriteButton(
                    modifier = Modifier
                        .focusRequester(actionFavoriteFocusRequester)
                        .focusProperties {
                            left = defaultFocusRequester
                            right = actionCommentFocusRequester
                            up = upButtonFocusRequester
                            if (actionDownRequester != null) down = actionDownRequester
                        },
                    isFavorite = isFavorite,
                    countText = videoDetail.stat.favorite.toWanString(),
                    userFavoriteFolders = userFavoriteFolders,
                    favoriteFolderIds = favoriteFolderIds,
                    onAddToDefaultFavoriteFolder = onAddToDefaultFavoriteFolder,
                    onUpdateFavoriteFolders = onUpdateFavoriteFolders
                )

                Spacer(modifier = Modifier.width(5.dp))

                CommentButton(
                    modifier = Modifier
                        .focusRequester(actionCommentFocusRequester)
                        .focusProperties {
                            left = actionFavoriteFocusRequester
                            right = actionLikeFocusRequester
                            up = upButtonFocusRequester
                            if (actionDownRequester != null) down = actionDownRequester
                        },
                    countText = videoDetail.stat.reply.toWanString(),
                    onClick = { showCommentsDialog = true }
                )

                Spacer(modifier = Modifier.width(5.dp))

                LikeButton(
                    modifier = Modifier
                        .focusRequester(actionLikeFocusRequester)
                        .focusProperties {
                            left = actionCommentFocusRequester
                            right = actionCoinFocusRequester
                            up = upButtonFocusRequester
                            if (actionDownRequester != null) down = actionDownRequester
                        },
                    isLiked = isLiked,
                    countText = videoDetail.stat.like.toWanString(),
                    onClick = { onUpdateLiked(!isLiked) },
                    onLongClick = { onSendVideoOneClickTripleAction() }
                )

                Spacer(modifier = Modifier.width(5.dp))

                CoinButton(
                    modifier = Modifier
                        .focusRequester(actionCoinFocusRequester)
                        .focusProperties {
                            left = actionLikeFocusRequester
                            up = upButtonFocusRequester
                            if (actionDownRequester != null) down = actionDownRequester
                        },
                    isCoined = isCoined,
                    countText = videoDetail.stat.coin.toWanString(),
                    onClick = onSendVideoCoin,
                )

                Spacer(modifier = Modifier.width(10.dp))

                LazyRow(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = tags) { tag ->
                        SuggestionChip(
                            onClick = { onClickTip(tag) },
                            shape = SuggestionChipDefaults.shape(
                                shape = RectangleShape
                            )
                        ) {
                            Text(text = tag.name)
                        }
                    }
                }
            }

            VideoDescription(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(
                        if (hasDescription) {
                            Modifier
                                .focusRequester(descriptionFocusRequester)
                                .focusProperties {
                                    up = actionFavoriteFocusRequester
                                    if (coverDownRequester != null) down = coverDownRequester
                                }
                        } else {
                            Modifier
                        }
                    ),
                description = videoDetail.description
            )

            VideoCommentsDialog(
                show = showCommentsDialog,
                aid = videoDetail.aid,
                onDismissRequest = { showCommentsDialog = false }
            )

            CoAuthorsDialogHost(
                state = coAuthorsDialogState,
                onClickAuthor = { mid, name ->
                    UpInfoActivity.actionStart(context, mid = mid, name = name)
                }
            )
        }
    }
}

@Composable
private fun UpButton(
    modifier: Modifier = Modifier,
    upInfoModifier: Modifier = Modifier,
    followModifier: Modifier = Modifier,
    name: String,
    followed: Boolean,
    onClickUp: () -> Unit,
    onAddFollow: () -> Unit,
    onDelFollow: () -> Unit
) {
    val view = LocalView.current
    val isLogin by remember { mutableStateOf(if (!view.isInEditMode) Prefs.isLogin else true) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = upInfoModifier
                .clip(RectangleShape)
                .background(C.surfaceVariant)
                .focusedBorder(RectangleShape)
                .padding(4.dp)
                .clickable { onClickUp() },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UpIcon(color = C.onSurface)
            Text(text = name, color = C.onSurface)
        }
        AnimatedVisibility(visible = isLogin) {
            Row(
                modifier = followModifier
                    .clip(RectangleShape)
                    .background(C.surfaceVariant)
                    .focusedBorder(RectangleShape)
                    .padding(horizontal = 4.dp, vertical = 3.dp)
                    .clickable { if (followed) onDelFollow() else onAddFollow() }
                    .animateContentSize()
            ) {
                if (followed) {
                    Icon(
                        imageVector = Icons.Rounded.Done,
                        contentDescription = null,
                        tint = C.onSurface
                    )
                    Text(
                        text = stringResource(R.string.video_info_followed),
                        color = C.onSurface
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = C.onSurface
                    )
                    Text(text = stringResource(R.string.video_info_follow), color = C.onSurface)
                }
            }
        }
    }
}

@Composable
fun VideoDescription(
    modifier: Modifier = Modifier,
    description: String,
) {
    var showDescriptionDialog by remember { mutableStateOf(false) }
    val normalizedDescription = description.trim()
    if (normalizedDescription.isNotEmpty() && normalizedDescription != "-") {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RectangleShape)
                .focusedBorder(RectangleShape)
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .clickable { showDescriptionDialog = true }
        ) {
            Text(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentHeight(Alignment.CenterVertically),
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(stringResource(R.string.video_info_description_title))
                        append("：")
                    }
                    append(normalizedDescription)
                },
                maxLines = 10,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                overflow = TextOverflow.Ellipsis,
                color = C.onSurface
            )
        }
    }
    VideoDescriptionDialog(
        show = showDescriptionDialog,
        onHideDialog = { showDescriptionDialog = false },
        description = description
    )
}

@Composable
fun VideoDescriptionDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onHideDialog: () -> Unit,
    description: String
) {
    if (show) {
        val scrollState = rememberScrollState()
        val scope = rememberCoroutineScope()
        val focusRequester = remember { FocusRequester() }

        var viewportHeightPx by remember { mutableIntStateOf(0) }
        val stepPx = remember(viewportHeightPx) {
            maxOf(1, (viewportHeightPx * 0.2f).toInt())
        }
        val canScroll by remember {
            derivedStateOf { scrollState.maxValue > 0 }
        }

        LaunchedEffect(canScroll) {
            if (show && canScroll) {
                focusRequester.requestFocus()
            }
        }

        AlertDialog(
            modifier = modifier,
            shape = RectangleShape,
            onDismissRequest = { onHideDialog() },
            title = {
                Text(
                    text = stringResource(R.string.video_info_description_title),
                    color = C.onSurface
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .onSizeChanged { viewportHeightPx = it.height }
                        .focusRequester(focusRequester)
                        .focusable(enabled = canScroll)
                        .onPreviewKeyEvent { event ->
                            if (!canScroll) return@onPreviewKeyEvent false
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                            when (event.key) {
                                Key.DirectionDown -> {
                                    val target = (scrollState.value + stepPx)
                                        .coerceIn(0, scrollState.maxValue)
                                    scope.launch { scrollState.animateScrollTo(target) }
                                    true
                                }
                                Key.DirectionUp -> {
                                    val target = (scrollState.value - stepPx)
                                        .coerceIn(0, scrollState.maxValue)
                                    scope.launch { scrollState.animateScrollTo(target) }
                                    true
                                }
                                else -> false
                            }
                        }
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                        text = description,
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                    )
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun DurationUnitText(
    duration: Int,
    unit: String,
    fontSize: Int
) {
    // 根据传入的单位标识符，在内部进行计算
    val value = when (unit.lowercase()) {
        "h" -> duration / 3600
        "m" -> (duration % 3600) / 60
        "s" -> duration % 60
        else -> 0L
    }
    // 在调用 Text 组件时，将 Int 转换为 .sp
    Text(
        text = String.format(Locale.getDefault(), "%02d", value),
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun VideoPartButton(
    modifier: Modifier = Modifier,
    index: Int,
    title: String,
    duration: Int,
    played: Int = 0,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(96.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = C.surfaceVariant,
            focusedContainerColor = C.inverseSurface,
            pressedContainerColor = C.inverseSurface
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RectangleShape),
        onClick = { onClick() }
    ) {
        //播放进度覆盖
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            //播放进度覆盖
            Box(
                modifier = Modifier
                    .background(C.primaryContainer.copy(alpha = 0.65f))
                    .fillMaxHeight()
                    .fillMaxWidth(if (played < 0) 1f else (played / duration.toFloat()))
            ) {}
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：标题
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                    text = "P$index $title",
                    fontSize = LocalTextStyle.current.fontSize * 1.5,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                VerticalDivider(
                    // 添加垂直内边距，让线变短一点
                    modifier = Modifier.padding(vertical = 12.dp),
                    // 使用 thickness 参数设置分割线的厚度（宽度）
                    thickness = 1.dp,
                    // 设置分割线的颜色
                    color = C.outline
                )
                // 右侧：垂直显示的时长
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceAround,
                    // 增加内边距，使其与按钮边缘有一定距离
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 6.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
                ) {
                    // 只有当视频时长超过1小时才显示小时
                    if (duration >= 3600) {
                        DurationUnitText(duration = duration, unit = "h", fontSize = 24)
                    }
                    DurationUnitText(duration = duration, unit = "m", fontSize = 21)
                    DurationUnitText(duration = duration, unit = "s", fontSize = 19)
                }
            }
        }
    }
}

@Composable
fun VideoPartRowButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier.size(96.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = C.surfaceVariant,
            focusedContainerColor = C.inverseSurface,
            pressedContainerColor = C.inverseSurface
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RectangleShape),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

@Composable
fun VideoPartRow(
    modifier: Modifier = Modifier,
    entryFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    pages: List<VideoPage>,
    lastPlayedCid: Long = 0,
    lastPlayedTime: Int = 0,
    enablePartListDialog: Boolean = false,
    onClick: (cid: Long) -> Unit
) {
    val firstPageFocusRequester = entryFocusRequester ?: remember { FocusRequester() }
    val leadingFocusRequester = remember { FocusRequester() }
    val fallbackFocusRequester = remember { FocusRequester() }
    val pageFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }

    fun pageRequesterFor(index: Int): FocusRequester {
        if (index == 0) return firstPageFocusRequester
        return pageFocusRequesters.getOrPut(index) { FocusRequester() }
    }

    val restoreFallback = when {
        pages.isNotEmpty() -> firstPageFocusRequester
        enablePartListDialog -> leadingFocusRequester
        else -> fallbackFocusRequester
    }

    var showPartListDialog by remember { mutableStateOf(false) }

    LazyRow(
        modifier = modifier
            .padding(top = 8.dp)
            .focusRestorer(restoreFallback)
            .focusGroup()
            .padding(start = 50.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .height(96.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    stringResource(R.string.video_info_part_row_title)
                        .filterNot { it.isWhitespace() }
                        .replace("p", "Ｐ")
                        .forEach { ch ->
                            Text(
                                text = ch.toString(),
                                color = C.onSurface,
                                fontSize = 14.sp,
                                lineHeight = 14.sp,
                                style = TextStyle(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                        }
                }
            }
        }

        if (enablePartListDialog) {
            item {
                VideoPartRowButton(
                    modifier = Modifier
                        .focusRequester(leadingFocusRequester)
                        .focusProperties {
                            if (pages.isNotEmpty()) {
                                right = firstPageFocusRequester
                            }
                            if (upFocusRequester != null) up = upFocusRequester
                            if (downFocusRequester != null) down = downFocusRequester
                        },
                    onClick = { showPartListDialog = true }
                ) {
                    Icon(
                        modifier = Modifier.size(54.dp),
                        imageVector = Icons.Rounded.ViewModule,
                        contentDescription = null
                    )
                }
            }
        }

        itemsIndexed(items = pages, key = { _, page -> page.cid }) { index, page ->
            VideoPartButton(
                modifier = Modifier
                    .focusRequester(pageRequesterFor(index))
                    .focusProperties {
                        when {
                            index == 0 && enablePartListDialog -> {
                                left = leadingFocusRequester
                            }

                            index > 0 -> {
                                left = pageRequesterFor(index - 1)
                            }
                        }

                        if (index < pages.lastIndex) {
                            right = pageRequesterFor(index + 1)
                        }

                        if (upFocusRequester != null) up = upFocusRequester
                        if (downFocusRequester != null) down = downFocusRequester
                    }
                    .width(300.dp),
                index = index + 1,
                title = page.title,
                played = if (page.cid == lastPlayedCid) lastPlayedTime else 0,
                duration = page.duration,
                onClick = { onClick(page.cid) }
            )
        }
    }

    PagedVideoInfinityListDialog(
        show = showPartListDialog,
        onHideDialog = { showPartListDialog = false },
        items = pages,
        title = "分 P 列表",
        itemKey = { it.cid }
    ) { itemModifier, absoluteIndex, page ->
        VideoPartButton(
            modifier = itemModifier,
            index = absoluteIndex + 1,
            title = page.title,
            played = 0,
            duration = page.duration,
            onClick = { onClick(page.cid) }
        )
    }
}

fun Episode.toVideoCardData(): VideoCardData {
    val displayTitle = longTitle.ifBlank { title }

    return VideoCardData(
        avid = aid,
        cid = cid,
        epId = epid,
        title = displayTitle,
        cover = cover,
        upName = upName,
        upMid = upMid,
        playString = playCount.toWanString(),
        danmakuString = danmakuCount.toWanString(),
        timeString = (duration * 1000L).formatHourMinSec(),
        jumpToSeason = false,
        pubTime = pubTime
    )
}

@Composable
fun UgcSeasonLeadingButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(200.dp)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(143.75.dp)
                .aspectRatio(0.5f),
            onClick = onClick,
            shape = CardDefaults.shape(RectangleShape),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(3.dp, C.outline),
                    shape = RectangleShape
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(C.background),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(54.dp),
                    imageVector = Icons.Rounded.ZoomOutMap,
                    contentDescription = "打开合集列表",
                    tint = C.onSurface
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "展开全部",
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "占位",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun VideoUgcSeasonRow(
    modifier: Modifier = Modifier,
    title: String,
    episodes: List<Episode>,
    onEpisodeClick: (Episode) -> Unit,
    onAddWatchLater: ((Long) -> Unit)? = null,
    onGoToDetailPage: ((Long) -> Unit)? = null,
    onGoToUpPage: ((Long, String) -> Unit)? = null,
    rowStateKey: String? = null,
    entryFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
) {
    if (episodes.isEmpty()) return

    var showUgcListDialog by remember { mutableStateOf(false) }

    val resolvedRowStateKey = rowStateKey ?: remember(title, episodes) {
        val firstAid = episodes.firstOrNull()?.aid ?: 0L
        "VideoUgcSeasonRow:$title:$firstAid:${episodes.size}"
    }

    val videos = remember(episodes) { episodes.map { it.toVideoCardData() } }

    val episodeByIdentity = remember(episodes, videos) {
        episodes.zip(videos).associate { (episode, videoData) ->
            Triple(videoData.avid, videoData.cid ?: -1L, videoData.epId ?: -1) to episode
        }
    }

    VideosRowCore(
        modifier = modifier,
        header = title,
        fontSize = 16.sp,
        videos = videos,
        onVideoClicked = { videoData ->
            episodeByIdentity[
                Triple(videoData.avid, videoData.cid ?: -1L, videoData.epId ?: -1)
            ]?.let { episode ->
                onEpisodeClick(episode)
            }
        },
        onAddWatchLater = onAddWatchLater,
        onGoToDetailPage = onGoToDetailPage,
        onGoToUpPage = onGoToUpPage,
        enableHorizontalWrap = false,
        rowStateKey = resolvedRowStateKey,
        entryFocusRequester = entryFocusRequester,
        upFocusRequester = upFocusRequester,
        downFocusRequester = downFocusRequester,
        leadingItem = if (episodes.size > 6) {
            { itemModifier ->
                UgcSeasonLeadingButton(
                    modifier = itemModifier,
                    onClick = { showUgcListDialog = true }
                )
            }
        } else {
            null
        }
    )

    PagedVideoInfinityListDialog(
        show = showUgcListDialog,
        onHideDialog = { showUgcListDialog = false },
        items = videos,
        title = title,
        itemKey = { Triple(it.avid, it.cid ?: -1L, it.epId ?: -1) },
        columnCount = 5,
        pageSize = 35,
        verticalSpacing = 12,
        horizontalSpacing = 24,
        contentPadding = PaddingValues(
            start = 24.dp,
            top = 24.dp,
            end = 24.dp,
            bottom = 96.dp
        ),
    ) { itemModifier, _, videoData ->
        SmallVideoCard(
            modifier = Modifier,
            frameModifier = itemModifier,
            data = videoData,
            titleMaxLines = 3,
            onClick = {
                episodeByIdentity[
                    Triple(videoData.avid, videoData.cid ?: -1L, videoData.epId ?: -1)
                ]?.let { episode ->
                    showUgcListDialog = false
                    onEpisodeClick(episode)
                }
            },
            onAddWatchLater = onAddWatchLater?.let { callback ->
                { callback(videoData.avid) }
            },
            onGoToDetailPage = onGoToDetailPage?.let { callback ->
                { callback(videoData.avid) }
            },
            onGoToUpPage = if (videoData.upMid != null && onGoToUpPage != null) {
                { onGoToUpPage(videoData.upMid, videoData.upName) }
            } else {
                null
            }
        )
    }
}

private data class PendingFocusTarget(
    val tabIndex: Int,
    val itemIndex: Int
)

private enum class DialogFocusArea {
    Tabs,
    Grid
}

@Composable
fun <T> PagedVideoInfinityListDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    title: String,
    items: List<T>,
    onHideDialog: () -> Unit,
    itemKey: (T) -> Any,
    pageSize: Int = 35,
    columnCount: Int = 5,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalSpacing: Int = 10,
    horizontalSpacing: Int = 20,
    tabTextBuilder: (startIndex: Int, endIndex: Int) -> String = { startIndex, endIndex ->
        if (startIndex == endIndex) "P$startIndex" else "P$startIndex-$endIndex"
    },
    itemContent: @Composable (modifier: Modifier, absoluteIndex: Int, item: T) -> Unit
) {
    // 这些状态必须放在 if (!show) return 前面，这样关闭 Dialog 后还能保留焦点恢复信息
    val listState = rememberLazyGridState()

    val tabFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    // 这里改成“绝对索引 -> FocusRequester”，避免不同页的同位置 item 共用 requester
    val itemFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }

    // 每页记住上次聚焦到哪个 item（页内索引）
    val lastFocusedItemIndexByPage = remember { mutableMapOf<Int, Int>() }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var pendingFocus by remember { mutableStateOf<PendingFocusTarget?>(null) }
    var lastFocusedArea by remember { mutableStateOf(DialogFocusArea.Tabs) }

    if (!show) return

    val tabCount = remember(items.size, pageSize) {
        if (items.isEmpty()) 0 else ceil(items.size / pageSize.toDouble()).toInt()
    }

    val selectedItems = remember(items, selectedTabIndex, pageSize, tabCount) {
        if (tabCount == 0 || selectedTabIndex !in 0 until tabCount) {
            emptyList()
        } else {
            val fromIndex = selectedTabIndex * pageSize
            val toIndex = minOf(fromIndex + pageSize, items.size)
            items.subList(fromIndex, toIndex)
        }
    }

    fun tabRequesterFor(index: Int): FocusRequester {
        return tabFocusRequesters.getOrPut(index) { FocusRequester() }
    }

    fun itemRequesterFor(absoluteIndex: Int): FocusRequester {
        return itemFocusRequesters.getOrPut(absoluteIndex) { FocusRequester() }
    }

    fun pageItemCount(tabIndex: Int): Int {
        val fromIndex = tabIndex * pageSize
        if (fromIndex >= items.size) return 0
        val toIndex = minOf(fromIndex + pageSize, items.size)
        return toIndex - fromIndex
    }

    fun restoredItemIndex(tabIndex: Int): Int {
        val count = pageItemCount(tabIndex)
        if (count <= 0) return -1
        return (lastFocusedItemIndexByPage[tabIndex] ?: 0).coerceIn(0, count - 1)
    }

    fun topIndexForColumn(itemCount: Int, column: Int): Int {
        if (itemCount <= 0) return -1
        return minOf(column, itemCount - 1)
    }

    fun bottomIndexForColumn(itemCount: Int, column: Int): Int {
        if (itemCount <= 0) return -1
        val lastIndex = itemCount - 1
        val lastRowStart = (lastIndex / columnCount) * columnCount
        return minOf(lastRowStart + column, lastIndex)
    }

    fun focusItemOnPage(tabIndex: Int, itemIndex: Int) {
        if (itemIndex < 0) return
        pendingFocus = PendingFocusTarget(
            tabIndex = tabIndex,
            itemIndex = itemIndex
        )
        if (selectedTabIndex != tabIndex) {
            selectedTabIndex = tabIndex
        }
    }

    fun moveHorizontallyFromEdge(currentIndex: Int, moveRight: Boolean): Boolean {
        val row = currentIndex / columnCount
        val currentPageCount = pageItemCount(selectedTabIndex)
        if (currentPageCount <= 0) return false

        // 单页：同行左右循环
        if (tabCount <= 1) {
            val rowStart = row * columnCount
            val rowEnd = minOf(rowStart + columnCount - 1, currentPageCount - 1)
            val targetIndex = if (moveRight) rowStart else rowEnd
            lastFocusedArea = DialogFocusArea.Grid
            focusItemOnPage(selectedTabIndex, targetIndex)
            return true
        }

        // 多页：跨页同行跳转
        val targetTabIndex = if (moveRight) {
            (selectedTabIndex + 1) % tabCount
        } else {
            (selectedTabIndex - 1 + tabCount) % tabCount
        }

        val targetCount = pageItemCount(targetTabIndex)
        if (targetCount <= 0) return false

        val targetRowStart = row * columnCount
        val targetIndex = when {
            targetRowStart >= targetCount -> {
                // 目标页没有对应行，聚焦最后一个
                targetCount - 1
            }

            moveRight -> {
                // 下一页同行最左
                targetRowStart
            }

            else -> {
                // 上一页同行最右；该行不满则取该行最后一个
                minOf(targetRowStart + columnCount - 1, targetCount - 1)
            }
        }

        lastFocusedArea = DialogFocusArea.Grid
        focusItemOnPage(targetTabIndex, targetIndex)
        return true
    }

    // items/pageSize 变化时修正 selectedTabIndex
    LaunchedEffect(tabCount) {
        if (tabCount == 0) {
            pendingFocus = null
            return@LaunchedEffect
        }
        if (selectedTabIndex >= tabCount) {
            selectedTabIndex = tabCount - 1
        }
    }

    // Dialog 打开时恢复焦点
    LaunchedEffect(tabCount) {
        if (!show || tabCount == 0) return@LaunchedEffect

        val safeTab = selectedTabIndex.coerceIn(0, tabCount - 1)
        if (safeTab != selectedTabIndex) {
            selectedTabIndex = safeTab
        }

        val restoreIndex = restoredItemIndex(safeTab)
        pendingFocus = null

        when {
            // 单页没有 TabRow，直接恢复到上次 item
            tabCount == 1 && restoreIndex >= 0 -> {
                lastFocusedArea = DialogFocusArea.Grid
                focusItemOnPage(safeTab, restoreIndex)
            }

            // 上次焦点在 Grid，则恢复到上次 item
            lastFocusedArea == DialogFocusArea.Grid && restoreIndex >= 0 -> {
                lastFocusedArea = DialogFocusArea.Grid
                focusItemOnPage(safeTab, restoreIndex)
            }

            // 否则恢复到 Tab
            else -> {
                lastFocusedArea = DialogFocusArea.Tabs
                tabRequesterFor(safeTab).requestFocus()
            }
        }
    }

    // 正常切页但不是“指定跳到某个 item”时，把列表滚回顶部
    LaunchedEffect(selectedTabIndex, tabCount, pendingFocus) {
        if (!show || tabCount == 0) return@LaunchedEffect
        if (pendingFocus == null) {
            listState.scrollToItem(0)
        }
    }

    // 统一处理 item 聚焦：先滚动到目标，再等目标可见，再 requestFocus
    LaunchedEffect(selectedTabIndex, selectedItems.size, pendingFocus) {
        if (!show || selectedItems.isEmpty()) return@LaunchedEffect

        val focus = pendingFocus
        if (focus != null && focus.tabIndex == selectedTabIndex) {
            val localIndex = focus.itemIndex.coerceIn(0, selectedItems.lastIndex)
            val absoluteIndex = focus.tabIndex * pageSize + localIndex

            listState.scrollToItem(localIndex)

            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.any { it.index == localIndex }
            }.first { it }

            itemRequesterFor(absoluteIndex).requestFocus()
            pendingFocus = null
            return@LaunchedEffect
        }

        // 单页场景下，如果没有 pendingFocus，仍然尝试恢复到上次 item
        if (tabCount == 1) {
            val restoreIndex = restoredItemIndex(selectedTabIndex).coerceAtLeast(0)
            val absoluteIndex = selectedTabIndex * pageSize + restoreIndex

            listState.scrollToItem(restoreIndex)

            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.any { it.index == restoreIndex }
            }.first { it }

            itemRequesterFor(absoluteIndex).requestFocus()
        }
    }

    val currentDensity = LocalDensity.current

    Dialog(
        onDismissRequest = onHideDialog,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        CompositionLocalProvider(LocalDensity provides currentDensity) {
            Surface(
                modifier = modifier
                    .fillMaxSize()
                    .background(C.background),
                colors = SurfaceDefaults.colors(
                    containerColor = C.surface
                ),
                shape = RectangleShape
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(48.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = C.onSurface
                        )
                    }

                    if (tabCount > 1) {
                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            separator = { Spacer(modifier = Modifier.width(12.dp)) },
                        ) {
                            for (i in 0 until tabCount) {
                                Tab(
                                    modifier = Modifier
                                        .focusRequester(tabRequesterFor(i))
                                        .onPreviewKeyEvent { event ->
                                            if (event.type != KeyEventType.KeyDown) {
                                                return@onPreviewKeyEvent false
                                            }

                                            val count = pageItemCount(i)
                                            if (count <= 0) {
                                                return@onPreviewKeyEvent false
                                            }

                                            val rememberedIndex = restoredItemIndex(i)
                                            val column =
                                                if (rememberedIndex >= 0) rememberedIndex % columnCount else 0

                                            when (event.key) {
                                                Key.DirectionDown -> {
                                                    val target = topIndexForColumn(count, column)
                                                    lastFocusedArea = DialogFocusArea.Grid
                                                    focusItemOnPage(i, target)
                                                    true
                                                }

                                                Key.DirectionUp -> {
                                                    val target = bottomIndexForColumn(count, column)
                                                    lastFocusedArea = DialogFocusArea.Grid
                                                    focusItemOnPage(i, target)
                                                    true
                                                }

                                                else -> false
                                            }
                                        },
                                    selected = i == selectedTabIndex,
                                    onFocus = {
                                        // 跨页恢复 Grid 焦点时，Tab 可能会短暂抢到焦点，这里忽略掉
                                        if (pendingFocus == null) {
                                            lastFocusedArea = DialogFocusArea.Tabs
                                            selectedTabIndex = i
                                        }
                                    },
                                ) {
                                    val startIndex = i * pageSize + 1
                                    val endIndex = minOf((i + 1) * pageSize, items.size)
                                    Text(
                                        text = tabTextBuilder(startIndex, endIndex),
                                        fontSize = 16.sp,
                                        color = LocalContentColor.current,
                                        modifier = Modifier.padding(
                                            horizontal = 20.dp,
                                            vertical = 10.dp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    SmallVideoCardGridHost(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        columns = GridCells.Fixed(columnCount),
                        contentPadding = contentPadding,
                        verticalArrangement = Arrangement.spacedBy(verticalSpacing.dp),
                        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing.dp),
                        horizontalWrapColumnCount = columnCount
                    ) {
                        itemsIndexed(
                            items = selectedItems,
                            key = { _, item -> itemKey(item) }
                        ) { index, item ->
                            val absoluteIndex = selectedTabIndex * pageSize + index
                            val itemCount = selectedItems.size
                            val rowStart = (index / columnCount) * columnCount
                            val rowEnd = minOf(rowStart + columnCount - 1, itemCount - 1)
                            val column = index % columnCount
                            val lastRowStart = ((itemCount - 1) / columnCount) * columnCount

                            val topTarget = topIndexForColumn(itemCount, column)
                            val bottomTarget = bottomIndexForColumn(itemCount, column)

                            itemContent(
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(itemRequesterFor(absoluteIndex))
                                    .onFocusChanged {
                                        if (it.hasFocus) {
                                            lastFocusedArea = DialogFocusArea.Grid
                                            lastFocusedItemIndexByPage[selectedTabIndex] = index
                                        }
                                    }
                                    .onPreviewKeyEvent { event ->
                                        if (event.type != KeyEventType.KeyDown) {
                                            return@onPreviewKeyEvent false
                                        }

                                        when (event.key) {
                                            Key.DirectionRight -> {
                                                if (index != rowEnd) {
                                                    false
                                                } else {
                                                    moveHorizontallyFromEdge(
                                                        currentIndex = index,
                                                        moveRight = true
                                                    )
                                                }
                                            }

                                            Key.DirectionLeft -> {
                                                if (index != rowStart) {
                                                    false
                                                } else {
                                                    moveHorizontallyFromEdge(
                                                        currentIndex = index,
                                                        moveRight = false
                                                    )
                                                }
                                            }

                                            Key.DirectionUp -> {
                                                if (index >= columnCount) {
                                                    false
                                                } else {
                                                    if (tabCount > 1) {
                                                        lastFocusedArea = DialogFocusArea.Tabs
                                                        tabRequesterFor(selectedTabIndex).requestFocus()
                                                        true
                                                    } else {
                                                        lastFocusedArea = DialogFocusArea.Grid
                                                        focusItemOnPage(selectedTabIndex, bottomTarget)
                                                        true
                                                    }
                                                }
                                            }

                                            Key.DirectionDown -> {
                                                if (index < lastRowStart) {
                                                    false
                                                } else {
                                                    if (tabCount > 1) {
                                                        lastFocusedArea = DialogFocusArea.Tabs
                                                        tabRequesterFor(selectedTabIndex).requestFocus()
                                                        true
                                                    } else {
                                                        lastFocusedArea = DialogFocusArea.Grid
                                                        focusItemOnPage(selectedTabIndex, topTarget)
                                                        true
                                                    }
                                                }
                                            }

                                            else -> false
                                        }
                                    },
                                absoluteIndex,
                                item
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun VideoPartButtonShortTextPreview() {
    BVTheme {
        VideoPartButton(
            index = 2,
            title = "这是一段短文字",
            duration = 100,
            onClick = {}
        )
    }
}

@Preview
@Composable
fun VideoPartButtonLongTextPreview() {
    BVTheme {
        VideoPartButton(
            index = 2,
            title = "这可能是我这辈子距离梅西最近的一次",
            played = 23333,
            duration = 3800,
            onClick = {}
        )
    }
}

@Preview
@Composable
fun VideoPartRowPreview() {
    val pages = remember { mutableStateListOf<VideoPage>() }
    for (i in 0..10) {
        pages.add(
            VideoPage(
                cid = 1000L + i,
                index = i,
                title = "这可能是我这辈子距离梅西最近的一次",
                duration = 10,
                dimension = Dimension(0, 0)
            )
        )
    }
    BVTheme {
        VideoPartRow(pages = pages, onClick = {})
    }
}

@Preview
@Composable
fun VideoDescriptionPreview() {
    BVTheme {
        VideoDescription(description = "12435678")
    }
}

@Preview
@Composable
private fun UpButtonPreview() {
    var followed by remember { mutableStateOf(false) }
    BVTheme {
        UpButton(
            name = "12435678",
            followed = followed,
            onClickUp = { followed = !followed },
            onAddFollow = {},
            onDelFollow = {}
        )
    }
}