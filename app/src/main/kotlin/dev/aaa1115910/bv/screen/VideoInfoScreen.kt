package dev.aaa1115910.bv.screen

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SuggestionChip
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
import dev.aaa1115910.bv.component.TvLazyVerticalGrid
import dev.aaa1115910.bv.component.UpIcon
import dev.aaa1115910.bv.component.buttons.CoinButton
import dev.aaa1115910.bv.component.buttons.FavoriteButton
import dev.aaa1115910.bv.component.buttons.LikeButton
import dev.aaa1115910.bv.component.buttons.CommentButton
import dev.aaa1115910.bv.component.comments.VideoCommentsDialog
import dev.aaa1115910.bv.component.ifElse
import dev.aaa1115910.bv.component.videocard.VideosRow
import dev.aaa1115910.bv.component.CoAuthorsDialogHost
import dev.aaa1115910.bv.component.rememberCoAuthorsDialogState
import dev.aaa1115910.bv.entity.VideoListItem
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.screen.settings.content.BlockGroupSelectDialog
import dev.aaa1115910.bv.screen.settings.content.BlockTagItem
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.ui.effect.VideoDetailUiEffect
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.focusedBorder
import dev.aaa1115910.bv.util.formatPubTimeString
import dev.aaa1115910.bv.util.launchPlayerActivity
import dev.aaa1115910.bv.util.requestFocus
import dev.aaa1115910.bv.util.swapListWithMainContext
import dev.aaa1115910.bv.util.toWanString
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import dev.aaa1115910.bv.viewmodel.video.VideoDetailState
import dev.aaa1115910.bv.viewmodel.video.VideoDetailViewModel
import dev.aaa1115910.bv.viewmodel.video.VideoInfoState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.math.ceil

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
    val scrollState = rememberScrollState()

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
            // 先确保关注分组列表已加载，否则 tags=[] 会导致初始选中无法映射到 UI，退出仍然变空
            val tagsOk = runCatching { videoDetailViewModel.loadFollowTagsIfNeeded() }
                .getOrDefault(false)
            if (!tagsOk) {
                "未获取到关注分组列表，已取消打开以避免误操作".toast(context)
                return@launch
            }

            //  composable 里捕获的 uiState在协程内不会自动刷新
            val tagsSnapshot = videoDetailViewModel.uiState.value.followTags

            // 再读取“我是否关注 + 该 UP 当前所在分组”作为初始选中
            val (wasFollowing, initialSelected) = runCatching {
                videoDetailViewModel.getUpFollowStateAndTagIds(upMid)
            }.getOrElse {
                // 失败兜底：尽量避免打开即退=取关
                val fallbackFollowing = uiState.isFollowingUp
                val fallbackSelected = if (fallbackFollowing) listOf(0) else emptyList()
                fallbackFollowing to fallbackSelected
            }

            // 把初始 tagIds 过滤到“当前真实 tags 列表”中，避免因为列表缺失导致初始选中被吞掉 -> 退出变空 -> 误取关
            val presentIds = tagsSnapshot.map { it.tagid }.toSet()
            val normalizedInitial = normalizeTagIds(initialSelected)
            val filteredInitial = normalizedInitial.filter { presentIds.contains(it) }

            // 已关注但 filtered 为空：优先 fallback 到默认分组 0（若存在），否则直接不打开，避免误操作
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

    val containsVerticalScreenVideo by remember(uiState.videoDetailState) {
        derivedStateOf {
            uiState.videoDetailState?.run {
                val inPages = pages.any { it.dimension.isVertical }
                inPages || ugcSeason?.sections?.any { section ->
                    section.episodes.any { it.dimension?.isVertical == true }
                } == true
            } ?: false
        }
    }

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

    fun playCurrentVideo(cid: Long? = null) {
        val videoDetailState = uiState.videoDetailState ?: return
        val targetCid = cid ?: videoDetailState.cid
        val lastPlayedCid = videoDetailState.lastPlayedCid
        val lastPlayedTime = videoDetailState.lastPlayedTime
        val fromSeason = uiState.fromSeason

        /*
        videoDetailViewModel.updateVideoList(
            listOf(
                VideoListItem(
                    aid = videoDetailState.aid,
                    cid = cid ?: videoDetailState.cid,
                    title = videoDetailState.title,
                )
            )
        )
         */
        val newVideoList = mutableListOf<VideoListItem>()
        // 根据视频类型构建列表
        if (videoDetailState.ugcSeason != null) {
            // --- 情况 A: UGC 合集 (UGC Season) ---
            // 逻辑：将当前分区的所有的“集”作为父项加入列表

            // 尝试找到包含当前 cid 的 section，如果找不到则默认使用当前 videoDetailState 所在的 section 或第一个 section
            val currentSection = videoDetailState.ugcSeason?.sections?.find { section ->
                section.episodes.any { it.cid == targetCid || it.aid == videoDetailState.aid }
            } ?: videoDetailState.ugcSeason?.sections?.firstOrNull()

            if (currentSection != null) {
                currentSection.episodes.forEach { episode ->
                    newVideoList.add(
                        VideoListItem(
                            aid = episode.aid,
                            cid = episode.cid,
                            //epid = episode.id,
                            seasonId = videoDetailState.ugcSeason!!.id,
                            title = episode.title
                            // ugcPages 将由播放器内部的 Repository 自动获取
                        )
                    )
                }
            }
        } else {
            // --- 情况 B: 普通视频 (单P 或 多P) ---
            // 逻辑：只添加一个代表当前视频的 Item。
            // 播放器内的 Repository 会检测到它，并自动加载它的所有分P填充到 ugcPages 中。
            newVideoList.add(
                VideoListItem(
                    aid = videoDetailState.aid,
                    cid = videoDetailState.cid, // 使用视频的主 CID
                    title = videoDetailState.title
                )
            )
        }

        //  将构建好的列表推送到 ViewModel
        videoDetailViewModel.updateVideoList(newVideoList)

        launchPlayerActivity(
            context = context,
            avid = videoDetailState.aid,
            cid = cid ?: videoDetailState.cid,
            title = videoDetailState.title,
            /*
            partTitle = videoDetailState.pages.find { it.cid == cid }?.title
                ?: videoDetailState.pages.first().title,
             */
            partTitle = videoDetailState.pages.find { it.cid ==  targetCid }?.title
                ?: videoDetailState.ugcSeason?.sections?.flatMap { it.episodes }
                    ?.find { it.cid ==  targetCid }?.title
                ?: "",
            played = if (cid?.let { it == lastPlayedCid }
                    ?: (videoDetailState.cid == lastPlayedCid)) {
                lastPlayedTime * 1000
            } else 0,
            fromSeason = fromSeason,
            author = videoDetailState.author)
    }

    LaunchedEffect(Unit) {
        videoDetailViewModel.uiEvent.collect { event ->
            when (event) {
                is VideoDetailUiEffect.ShowToast -> event.message.toast(context)
                is VideoDetailUiEffect.LaunchPlayerActivity -> {
                    playCurrentVideo(event.cid)
                }

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
                is UiEffect.ShowToast -> {
                    event.message.toast(context)
                }
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
            CompositionLocalProvider(
                LocalBringIntoViewSpec provides bringIntoViewSpec
            ) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    Column(
                        modifier = modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(top = 16.dp, bottom = 64.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 视频提示
                        /*
                        if (uiState.videoDetailState?.isUpowerExclusive == true) {
                            ArgueTip(text = stringResource(R.string.video_info_argue_tip_upower_exclusive))
                        }
                        if (containsVerticalScreenVideo) {
                            ArgueTip(text = stringResource(R.string.video_info_argue_tip_vertical_screen))
                        }
                        if (uiState.videoDetailState?.argueTip != null) {
                            ArgueTip(text = uiState.videoDetailState?.argueTip!!)
                        }
                         */

                        // 视频信息
                        val videoDetailState = uiState.videoDetailState ?: return@Column
                        if (videoDetailState != null) {
                            VideoInfoData(
                                defaultFocusRequester = defaultFocusRequester,
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
                                    // 点击封面播放当前视频
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

                        BlockGroupSelectDialog(
                            show = showFollowGroupDialog,
                            title = "选择关注分组",
                            tags = uiState.followTags.map { BlockTagItem(it.tagid, it.name, it.count) },
                            lightMode = true,
                            initialSelectedTagIds = followGroupDialogInitialSelectedTagIds,
                            initialMembersCache = emptyMap(),
                            onHideDialog = { showFollowGroupDialog = false },
                            onSubmit = { selectedTagIds, _ ->
                                val finalSelected = normalizeTagIds(selectedTagIds)
                                val initialSelected = normalizeTagIds(followGroupDialogInitialSelectedTagIds)

                                // 打开即退/未改动：不做任何事
                                if (finalSelected == initialSelected && followGroupDialogWasFollowing) return@BlockGroupSelectDialog

                                videoDetailViewModel.submitFollowGroupSelection(
                                    wasFollowing = followGroupDialogWasFollowing,
                                    selectedTagIds = finalSelected
                                )
                            }
                        )

                            // 视频分P
                            VideoPartRow(
                                pages = videoDetailState.pages,
                                lastPlayedCid = videoDetailState.lastPlayedCid,
                                lastPlayedTime = videoDetailState.lastPlayedTime,
                                enablePartListDialog =
                                    (videoDetailState.pages.size > 5),
                                onClick = { cid ->
                                    logger.fInfo { "Click video part: [av:${videoDetailState.aid}, bv:${videoDetailState.bvid}, cid:$cid]" }
                                    // 播放当前视频的对应分P
                                    playCurrentVideo(cid)
                                }
                            )

                            // 合集
                            videoDetailState.ugcSeason?.let { season ->
                                season.sections.forEachIndexed { index, section ->
                                    VideoUgcSeasonRow(
                                        title = if (season.sections.size == 1) season.title else section.title,
                                        episodes = section.episodes,
                                        lastPlayedCid = videoDetailState.lastPlayedCid,
                                        lastPlayedTime = videoDetailState.lastPlayedTime,
                                        enableUgcListDialog = section.episodes.size > 5,
                                        /*
                                onClick = { aid, cid ->
                                    logger.fInfo { "Click ugc season part: [av:$aid, cid:$cid]" }

                                            // 读取合集内视频
                                            videoDetailViewModel.updateVideoList(index)
                                            // 加载合集内对应视频的详情（主要是为了共享给播放器页）
                                            videoDetailViewModel.loadVideoDetail(aid)

                                            val currentEpisode =
                                                section.episodes.find { it.cid == cid }
                                            val episodeTitle = currentEpisode?.title ?: ""

                                    launchPlayerActivity(
                                        context = context,
                                        avid = aid,
                                        cid = cid,
                                        title = episodeTitle,
                                        partTitle = episodeTitle,
                                        played = if (cid == lastPlayedCid) lastPlayedTime * 1000 else 0,
                                        fromSeason = false,

                                        author = videoDetail.author
                                    )
                                }
                                 */
                                    onClick = { aid, _ -> // cid 在这里不再需要，可以用 _ 忽略
                                        logger.fInfo { "Click ugc season part, navigating to VideoInfoScreen for avid: $aid" }
                                        // 直接启动新的 VideoInfoActivity
                                        VideoInfoActivity.actionStart(context, aid)
                                    }
                                )
                            }
                        }
                        val relatedVideos = videoDetailState.relatedVideos
                        if (relatedVideos.isNotEmpty()) {
                                CompositionLocalProvider(
                                    LocalDensity provides Density(
                                        density = LocalDensity.current.density * 1.25f,
                                        fontScale = LocalDensity.current.fontScale * 1.25f
                                    )
                                ) {
                                    VideosRow(
                                        header = stringResource(R.string.video_info_related_video_title),
                                        videos = relatedVideos,
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
                                                aid =videoData.avid)
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
                                }
                        }
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
            .background(Color.Black),
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
            shape = MaterialTheme.shapes.small
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

        Row(
            modifier = modifier
                .padding(horizontal = 50.dp, vertical = 16.dp),
        ) {
            Surface(
                modifier = Modifier
                    .focusRequester(defaultFocusRequester)
                    .weight(3f)
                    .aspectRatio(1.6f)
                    .onGloballyPositioned { coordinates ->
                        heightIs = with(localDensity) { coordinates.size.height.toDp() }
                    },
                onClick = onClickCover,
                shape = ClickableSurfaceDefaults.shape(
                    shape = MaterialTheme.shapes.large,
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.border
                        ),
                        shape = MaterialTheme.shapes.large
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
            // Spacer(modifier = Modifier.width(24.dp))
            Spacer(modifier = Modifier.width(48.dp))
            Column(
                modifier = Modifier
                    .weight(7f)
                    .height(heightIs),
                // verticalArrangement = Arrangement.SpaceBetween
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        //text = videoDetail.title,
                        text = if (videoDetail.isUpowerExclusive) "充电▶ ${videoDetail.title}" else videoDetail.title,
                        // style = MaterialTheme.typography.titleLarge,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 44.sp),
                        // maxLines = 1,
                        maxLines = 2,
                        lineHeight = 50.sp,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
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
                                    modifier = Modifier.size(squareSize)
                                        .aspectRatio(1f),
                                    onClick = { coAuthorsDialogState.open(videoDetail.coAuthors) },
                                    shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        focusedContainerColor = Color.White.copy(alpha = 0.2f),
                                        pressedContainerColor = Color.White.copy(alpha = 0.2f)
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                    border = ClickableSurfaceDefaults.border(
                                        focusedBorder = Border(
                                            border = BorderStroke(width = 3.dp, color = Color.White),
                                            shape = MaterialTheme.shapes.small
                                        )
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Group,
                                        contentDescription = "联合投稿",
                                        tint = Color.White,
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
                                    onDelFollow = onDelFollow
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
                                // LocalTextStyle provides MaterialTheme.typography.labelMedium
                                LocalTextStyle provides MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 27.sp
                                )
                            ) {
                                Text(text = "发布于 ${videoDetail.publishDate.formatPubTimeString()}")
                                Text(text = "·")
                                Text(text = "播放量 ${(videoDetail.stat.view).toWanString()}")
                                Text(text = "·")
                                Text(text = "弹幕 ${(videoDetail.stat.danmaku).toWanString()}")
                                Text(text = "·")
                                Text(text = "投币 ${videoDetail.stat.coin.toWanString()}")
                                /*
                                Text(text = "·")
                                Text(text = "点赞 ${videoDetail.stat.like.toWanString()}")
                                Text(text = "·")
                                Text(text = "收藏 ${videoDetail.stat.favorite.toWanString()}")
                                 */
                            }
                        }
                    }
                    /*
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                   UpButton(
                        name = videoDetail.author.name,
                        followed = isFollowing,
                        onClickUp = onClickUp,
                        onAddFollow = onAddFollow,
                        onDelFollow = onDelFollow
                    )

                }
*/
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FavoriteButton(
                        isFavorite = isFavorite,
                        countText = videoDetail.stat.favorite.toWanString(),
                        userFavoriteFolders = userFavoriteFolders,
                        favoriteFolderIds = favoriteFolderIds,
                        onAddToDefaultFavoriteFolder = onAddToDefaultFavoriteFolder,
                        onUpdateFavoriteFolders = onUpdateFavoriteFolders
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    CommentButton(
                        countText = videoDetail.stat.reply.toWanString(),
                        onClick = { showCommentsDialog = true }
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    LikeButton(
                        isLiked = isLiked,
                        countText = videoDetail.stat.like.toWanString(),
                        onClick = { onUpdateLiked(!isLiked) },
                        onLongClick = { onSendVideoOneClickTripleAction() })
                    /*
                    CoinButton(
                        isCoined = isCoined,
                        countText = videoDetail.stat.coin.toWanString(),
                        onClick = onSendVideoCoin,
                    )
                     */
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = tags) { tag ->
                            SuggestionChip(onClick = {
                                onClickTip(tag)
                            }) {
                                Text(text = tag.name)
                            }
                        }
                    }
                }
                VideoDescription(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
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
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(Color.White.copy(alpha = 0.2f))
                    .focusedBorder(MaterialTheme.shapes.small)
                    .padding(4.dp)
                    .clickable { onClickUp() },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UpIcon(color = Color.White)
                Text(text = name, color = Color.White)
            }
            AnimatedVisibility(visible = isLogin) {
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(Color.White.copy(alpha = 0.2f))
                        .focusedBorder(MaterialTheme.shapes.small)
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                        .clickable { if (followed) onDelFollow() else onAddFollow() }
                        .animateContentSize()
                ) {
                    if (followed) {
                        Icon(
                            imageVector = Icons.Rounded.Done,
                            contentDescription = null,
                            tint = Color.White
                        )
                    Text(
                        text = stringResource(R.string.video_info_followed),
                        color = Color.White
                    )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(text = stringResource(R.string.video_info_follow), color = Color.White)
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
        var hasFocus by remember { mutableStateOf(false) }
        val titleColor = if (hasFocus) Color.White else Color.White.copy(alpha = 0.6f)
        val titleFontSize by animateFloatAsState(
            //targetValue = if (hasFocus) 30f else 14f,
            targetValue = 14f,
            label = "title font size"
        )
        if (description.isNotBlank()) {
            Column(
                modifier = modifier
                // .padding(horizontal = 50.dp),
            ) {
                Text(
                    text = stringResource(R.string.video_info_description_title),
                    fontSize = titleFontSize.sp,
                    color = titleColor
                )
                Box(
                    modifier = Modifier
                        .padding(top = 15.dp)
                        .fillMaxWidth()
                        .weight(1f)
                        .onFocusChanged { hasFocus = it.hasFocus }
                        .clip(MaterialTheme.shapes.medium)
                        .focusedBorder(MaterialTheme.shapes.medium)
                        .padding(8.dp)
                        .padding(8.dp)
                        .clickable { showDescriptionDialog = true }
                ) {
                    Text(
                        modifier = Modifier.fillMaxSize(),
                        text = description.ifBlank { " " },
                        maxLines = 5,
                        fontSize = 22.sp,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                }
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
            AlertDialog(
                modifier = modifier,
                onDismissRequest = { onHideDialog() },
                title = {
                    Text(
                        text = stringResource(R.string.video_info_description_title),
                        color = Color.White
                    )
                },
                text = {
                    LazyColumn {
                        item {
                            Text(
                                text = description,
                                fontSize = 28.sp,
                                lineHeight = 34.sp,
                            )
                        }
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
        fontSize: Int // <-- 关键修改：接收 Int 类型
    ) {
        // 1. 根据传入的单位标识符，在内部进行计算
        val value = when (unit.lowercase()) {
            "h" -> duration / 3600
            "m" -> (duration % 3600) / 60
            "s" -> duration % 60
            else -> 0L
        }
        // 2. 在调用 Text 组件时，将 Int 转换为 .sp
        Text(
            text = String.format("%02d", value),
            fontSize = fontSize.sp, // <-- 关键修改：在这里进行单位转换
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
            // modifier = modifier,
            modifier = modifier.height(96.dp),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                pressedContainerColor = MaterialTheme.colorScheme.inverseSurface
            ),
            shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.medium),
            onClick = { onClick() }
        ) {
            Box(
                modifier = Modifier
                    // .size(200.dp, 64.dp)
                    .fillMaxSize(),
                // contentAlignment = Alignment.Center
                contentAlignment = Alignment.CenterStart

            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.2f))
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
                            // weight(1f) 会让标题占据除了时长之外的所有剩余空间
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
                        color = Color.White.copy(alpha = 0.5f)
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
                /*
            Text(
                modifier = Modifier
                    // .padding(8.dp),
                    .padding(10.dp),
                text = "P$index $title",
                fontSize = LocalTextStyle.current.fontSize * 1.4,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            */
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
            //modifier = modifier.size(64.dp),
            modifier = modifier.size(96.dp),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                pressedContainerColor = MaterialTheme.colorScheme.inverseSurface
            ),
            shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.medium),
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
        pages: List<VideoPage>,
        lastPlayedCid: Long = 0,
        lastPlayedTime: Int = 0,
        enablePartListDialog: Boolean = false,
        onClick: (cid: Long) -> Unit
    ) {
        val focusRequester = remember { FocusRequester() }
        var hasFocus by remember { mutableStateOf(false) }
        var showPartListDialog by remember { mutableStateOf(false) }
        val titleColor = if (hasFocus) Color.White else Color.White.copy(alpha = 0.6f)
        val titleFontSize by animateFloatAsState(
            //targetValue = if (hasFocus) 30f else 14f,
            targetValue = 14f,
            label = "title font size"
        )

        Column(
            modifier = modifier
                .padding(start = 50.dp)
                .onFocusChanged { hasFocus = it.hasFocus },
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.video_info_part_row_title),
                fontSize = titleFontSize.sp,
                color = titleColor
            )

            LazyRow(
                modifier = Modifier
                    .padding(top = 15.dp)
                    .focusRestorer(focusRequester),
                contentPadding = PaddingValues(12.dp),
                // horizontalArrangement = Arrangement.spacedBy(16.dp)
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (enablePartListDialog) {
                    item {
                        VideoPartRowButton(
                            onClick = { showPartListDialog = true }
                        ) {
                            Icon(
                                // modifier = Modifier.size(36.dp),
                                modifier = Modifier.size(54.dp),
                                imageVector = Icons.Rounded.ViewModule,
                                contentDescription = null
                            )
                        }
                    }
                }

                val matchedPage = pages.find { it.cid == lastPlayedCid }
                if (matchedPage != null && pages.size > 1) {
                    item {
                        // 分P历史播放按钮
                        VideoPartRowButton(
                            onClick = { onClick(matchedPage.cid) }
                        ) {
                            Icon(
                                // modifier = Modifier.size(36.dp),
                                modifier = Modifier.size(54.dp),
                                imageVector = Icons.Rounded.History,
                                contentDescription = null
                            )
                        }
                    }
                }

                itemsIndexed(items = pages, key = { _, page -> page.cid }) { index, page ->
                    VideoPartButton(
                        modifier = Modifier
                            .ifElse(index == 0, Modifier.focusRequester(focusRequester))
                            // .width(200.dp),
                            .width(300.dp),
                        index = index + 1,
                        title = page.title,
                        played = if (page.cid == lastPlayedCid) lastPlayedTime else 0,
                        duration = page.duration,
                        onClick = { onClick(page.cid) }
                    )
                }
            }
        }

        VideoPartListDialog(
            show = showPartListDialog,
            onHideDialog = { showPartListDialog = false },
            pages = pages,
            title = "分 P 列表",
            onClick = onClick
        )
    }

    @Composable
    fun VideoUgcSeasonRow(
        modifier: Modifier = Modifier,
        title: String,
        episodes: List<Episode>,
        lastPlayedCid: Long = 0,
        lastPlayedTime: Int = 0,
        enableUgcListDialog: Boolean = false,
        onClick: (avid: Long, cid: Long) -> Unit
    ) {
        val focusRequester = remember { FocusRequester() }
        var hasFocus by remember { mutableStateOf(false) }
        var showUgcListDialog by remember { mutableStateOf(false) }
        val titleColor = if (hasFocus) Color.White else Color.White.copy(alpha = 0.6f)
        val titleFontSize by animateFloatAsState(
            // targetValue = if (hasFocus) 30f else 14f,
            targetValue = 14f,
            label = "title font size"
        )

        // 计算当前正在播放的视频在列表中的索引
        val playingIndex = remember(episodes, lastPlayedCid) {
            episodes.indexOfFirst { it.cid == lastPlayedCid }
        }

        Column(
            modifier = modifier
                .padding(start = 50.dp)
                .onFocusChanged { hasFocus = it.hasFocus },
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = titleFontSize.sp,
                color = titleColor
            )

            LazyRow(
                modifier = Modifier
                    .padding(top = 15.dp)
                    .focusRestorer(focusRequester),
                contentPadding = PaddingValues(12.dp),
                // horizontalArrangement = Arrangement.spacedBy(16.dp)
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (enableUgcListDialog) {
                    item {
                        VideoPartRowButton(
                            onClick = { showUgcListDialog = true }
                        ) {
                            Icon(
                                // modifier = Modifier.size(36.dp),
                                modifier = Modifier.size(54.dp),
                                imageVector = Icons.Rounded.ViewModule,
                                contentDescription = null
                            )
                        }
                    }
                }

                val matchedEp = episodes.find { it.cid == lastPlayedCid }
                if (matchedEp != null && episodes.size > 1) {
                    item {
                        // ugc分季历史播放按钮
                        VideoPartRowButton(
                            onClick = { onClick(matchedEp.aid, matchedEp.cid) }
                        ) {
                            Icon(
                                // modifier = Modifier.size(36.dp),
                                modifier = Modifier.size(54.dp),
                                imageVector = Icons.Rounded.History,
                                contentDescription = null
                            )
                        }
                    }
                }

                itemsIndexed(items = episodes) { index, episode ->

                    // 为每个分P独立计算播放进度
                    val currentPlayedTime = when {
                        // 1. 如果没有播放记录，所有分P进度都为0
                        playingIndex == -1 -> 0
                        // 2. 如果当前分P在正在播放的分P之前，说明已经看完，进度为满
                        index < playingIndex -> episode.duration
                        // 3. 如果当前分P就是正在播放的分P，使用历史记录的进度
                        index == playingIndex -> lastPlayedTime
                        // 4. 如果当前分P在正在播放的分P之后，说明还没看，进度为0
                        else -> 0
                    }

                    VideoPartButton(
                        modifier = Modifier
                            .ifElse(index == 0, Modifier.focusRequester(focusRequester))
                            // .width(200.dp),
                            .width(300.dp),
                        index = index + 1,
                        title = episode.title,
                        // played = if (episode.cid == lastPlayedCid) lastPlayedTime else 0,
                        // duration = episode.duration,
                        played = currentPlayedTime,
                        duration = episode.duration,
                        onClick = { onClick(episode.aid, episode.cid) }
                    )
                }
            }
        }

        VideoUgcListDialog(
            show = showUgcListDialog,
            onHideDialog = { showUgcListDialog = false },
            episodes = episodes,
            title = "合集列表",
            onClick = onClick
        )
    }

    @Composable
    private fun VideoPartListDialog(
        modifier: Modifier = Modifier,
        show: Boolean,
        title: String,
        pages: List<VideoPage>,
        onHideDialog: () -> Unit,
        onClick: (cid: Long) -> Unit
    ) {
        val scope = rememberCoroutineScope()

        var selectedTabIndex by remember { mutableIntStateOf(0) }
        // val tabCount by remember { mutableIntStateOf(ceil(pages.size / 20.0).toInt()) }
        val tabCount by remember { mutableIntStateOf(ceil(pages.size / 35.0).toInt()) }
        val selectedVideoPart = remember { mutableStateListOf<VideoPage>() }

        fun normalizeTagIds(ids: List<Int>): List<Int> {
            val dedup = ids.distinct().sorted()
            return if (dedup.contains(0) && dedup.size > 1) listOf(0) else dedup
        }

        val tabRowFocusRequester = remember { FocusRequester() }
        val videoListFocusRequester = remember { FocusRequester() }
        val listState = rememberLazyGridState()

        LaunchedEffect(selectedTabIndex) {
            // val fromIndex = selectedTabIndex * 20
            val fromIndex = selectedTabIndex * 35
            // var toIndex = (selectedTabIndex + 1) * 20
            var toIndex = (selectedTabIndex + 1) * 35
            if (toIndex >= pages.size) {
                toIndex = pages.size
            }
            selectedVideoPart.swapListWithMainContext(pages.subList(fromIndex, toIndex))
        }

        LaunchedEffect(show) {
            if (show && tabCount > 1) tabRowFocusRequester.requestFocus(scope)
            if (show && tabCount == 1) videoListFocusRequester.requestFocus(scope)
        }

        // ✅ 在 Dialog 外部获取当前正确的 Density
        val currentDensity = LocalDensity.current

        if (show) {
            Dialog(
                onDismissRequest = onHideDialog,
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,  // 不使用默认宽度
                    dismissOnBackPress = true,        // 返回键关闭
                    dismissOnClickOutside = false     // 点击外部不关闭（TV端建议）
                )
            ) {
                // 使用 CompositionLocalProvider 将正确的 Density 应用到 Dialog 的内容中
                CompositionLocalProvider(LocalDensity provides currentDensity) {
                    // ✅ 使用 Surface 创建全屏背景
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()              // ✅ 全屏
                            .background(Color.Black),  // 半透明背景
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(48.dp),  // ✅ 调整内边距
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // ✅ 标题栏
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.headlineMedium,  // ✅ 更大的标题
                                    color = Color.White
                                )
                            }

                            // ✅ 标签页
                            if (tabCount > 1) {
                                TabRow(
                                    modifier = Modifier
                                        .onFocusChanged {
                                            if (it.hasFocus) {
                                                scope.launch(Dispatchers.Main) {
                                                    listState.scrollToItem(0)
                                                }
                                            }
                                        },
                                    selectedTabIndex = selectedTabIndex,
                                    separator = { Spacer(modifier = Modifier.width(12.dp)) },
                                ) {
                                    for (i in 0 until tabCount) {
                                        Tab(
                                            modifier = if (i == 0) Modifier.focusRequester(
                                                tabRowFocusRequester
                                            ) else Modifier,
                                            selected = i == selectedTabIndex,
                                            onFocus = { selectedTabIndex = i },
                                        ) {
                                            val startIndex = i * 35 + 1
                                            val endIndex = minOf((i + 1) * 35, pages.size)
                                            val tabText = if (startIndex == endIndex) {
                                                "P$startIndex"
                                            } else {
                                                "P$startIndex-$endIndex"
                                            }
                                            Text(
                                                // text = "P${i * 20 + 1}-${(i + 1) * 20}",
                                                text = tabText,
                                                fontSize = 16.sp,  // ✅ 更大的标签字号
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

                            // ✅ 视频列表网格
                            TvLazyVerticalGrid(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),  // ✅ 填充剩余空间
                                columns = GridCells.Fixed(5),  // ✅ 从2列增加到5列
                                contentPadding = PaddingValues(16.dp),
                                // verticalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                                // horizontalArrangement = Arrangement.spacedBy(16.dp)
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                itemsIndexed(
                                    items = selectedVideoPart,
                                    key = { _, video -> video.cid }
                                ) { index, page ->
                                    val buttonModifier =
                                        if (index == 0 && tabCount == 1) {
                                            Modifier.focusRequester(videoListFocusRequester)
                                        } else Modifier

                                    VideoPartButton(
                                        modifier = buttonModifier,
                                        // index = selectedTabIndex * 20 + index + 1,  //  显示正确的索引
                                        index = selectedTabIndex * 35 + index + 1,
                                        title = page.title,
                                        played = 0,
                                        duration = page.duration,
                                        onClick = {
                                            onClick(page.cid)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            /*
        AlertDialog(
            modifier = modifier,
            title = { Text(text = title) },
            onDismissRequest = { onHideDialog() },
            confirmButton = {},
            properties = DialogProperties(usePlatformDefaultWidth = false),
            text = {
                Column(
                    modifier = Modifier.size(600.dp, 330.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TabRow(
                        modifier = Modifier
                            .onFocusChanged {
                                if (it.hasFocus) {
                                    scope.launch(Dispatchers.Main) {
                                        listState.scrollToItem(0)
                                    }
                                }
                            },
                        selectedTabIndex = selectedTabIndex,
                        separator = { Spacer(modifier = Modifier.width(12.dp)) },
                    ) {
                        for (i in 0 until tabCount) {
                            Tab(
                                modifier = if (i == 0) Modifier.focusRequester(
                                    tabRowFocusRequester
                                ) else Modifier,
                                selected = i == selectedTabIndex,
                                onFocus = { selectedTabIndex = i },
                            ) {
                                Text(
                                    text = "P${i * 20 + 1}-${(i + 1) * 20}",
                                    fontSize = 12.sp,
                                    color = LocalContentColor.current,
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 6.dp
                                    )
                                )
                            }
                        }
                    }

                    TvLazyVerticalGrid(
                        state = listState,
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = selectedVideoPart,
                            key = { _, video -> video.cid }
                        ) { index, page ->
                            val buttonModifier =
                                if (index == 0) Modifier.focusRequester(videoListFocusRequester) else Modifier

                            VideoPartButton(
                                modifier = buttonModifier,
                                index = index + 1,
                                title = page.title,
                                played = 0,
                                duration = page.duration,
                                onClick = { onClick(page.cid) }
                            )
                        }
                    }
                }
            }
        )
        */
        }
    }

    @Composable
    private fun VideoUgcListDialog(
        modifier: Modifier = Modifier,
        show: Boolean,
        title: String,
        episodes: List<Episode>,
        onHideDialog: () -> Unit,
        onClick: (avid: Long, cid: Long) -> Unit
    ) {
        val scope = rememberCoroutineScope()

        var selectedTabIndex by remember { mutableIntStateOf(0) }
        // val tabCount by remember { mutableIntStateOf(ceil(episodes.size / 20.0).toInt()) }
        val tabCount by remember { mutableIntStateOf(ceil(episodes.size / 35.0).toInt()) }
        val selectedVideoPart = remember { mutableStateListOf<Episode>() }

        val tabRowFocusRequester = remember { FocusRequester() }
        val videoListFocusRequester = remember { FocusRequester() }
        val listState = rememberLazyGridState()

        LaunchedEffect(selectedTabIndex) {
            // val fromIndex = selectedTabIndex * 20
            val fromIndex = selectedTabIndex * 35
            // var toIndex = (selectedTabIndex + 1) * 20
            var toIndex = (selectedTabIndex + 1) * 35
            if (toIndex >= episodes.size) {
                toIndex = episodes.size
            }
            selectedVideoPart.swapListWithMainContext(episodes.subList(fromIndex, toIndex))
        }

        LaunchedEffect(show) {
            if (show && tabCount > 1) tabRowFocusRequester.requestFocus(scope)
            if (show && tabCount == 1) videoListFocusRequester.requestFocus(scope)
        }

        // 在 Dialog 外部获取当前正确的 Density
        val currentDensity = LocalDensity.current

        if (show) {
            Dialog(  // ✅ 使用 Dialog
                onDismissRequest = onHideDialog,
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,  // 不使用默认宽度
                    dismissOnBackPress = true,        // 返回键关闭
                    dismissOnClickOutside = false     // 点击外部不关闭（TV端建议）
                )
            ) {
                // ✅ 步骤 2: 使用 CompositionLocalProvider 将正确的 Density 应用到 Dialog 的内容中
                CompositionLocalProvider(LocalDensity provides currentDensity) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.extraLarge
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
                                    style = MaterialTheme.typography.headlineMedium,  // ✅ 更大的标题
                                    color = Color.White
                                )
                            }

                            // ✅ 标签页
                            if (tabCount > 1) {
                                TabRow(
                                    modifier = Modifier
                                        .onFocusChanged {
                                            if (it.hasFocus) {
                                                scope.launch(Dispatchers.Main) {
                                                    listState.scrollToItem(0)
                                                }
                                            }
                                        },
                                    selectedTabIndex = selectedTabIndex,
                                    separator = { Spacer(modifier = Modifier.width(12.dp)) },
                                ) {
                                    for (i in 0 until tabCount) {
                                        Tab(
                                            modifier = if (i == 0) Modifier.focusRequester(
                                                tabRowFocusRequester
                                            ) else Modifier,
                                            selected = i == selectedTabIndex,
                                            onFocus = { selectedTabIndex = i },
                                        ) {
                                            val startIndex = i * 35 + 1
                                            val endIndex = minOf((i + 1) * 35, episodes.size)
                                            val tabText = if (startIndex == endIndex) {
                                                "P$startIndex"
                                            } else {
                                                "P$startIndex-$endIndex"
                                            }
                                            Text(
                                                // text = "P${i * 20 + 1}-${(i + 1) * 20}",
                                                text = tabText,
                                                fontSize = 16.sp,  // ✅ 更大的标签字号
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

                            // 视频列表
                            TvLazyVerticalGrid(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                columns = GridCells.Fixed(5),
                                contentPadding = PaddingValues(16.dp),
                                // verticalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                                // horizontalArrangement = Arrangement.spacedBy(16.dp)
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                itemsIndexed(
                                    items = selectedVideoPart,
                                    key = { _, video -> video.cid }
                                ) { index, episode ->
                                    val buttonModifier =
                                        if (index == 0 && tabCount == 1) {
                                            Modifier.focusRequester(videoListFocusRequester)
                                        } else Modifier

                                    VideoPartButton(
                                        modifier = buttonModifier,
                                        // index = selectedTabIndex * 20 + index + 1,
                                        index = selectedTabIndex * 35 + index + 1,
                                        title = episode.title,
                                        played = 0,
                                        duration = episode.duration,
                                        onClick = {
                                            onClick(episode.aid, episode.cid)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                /*
        AlertDialog(
            modifier = modifier,
            title = { Text(text = title) },
            onDismissRequest = { onHideDialog() },
            confirmButton = {},
            properties = DialogProperties(usePlatformDefaultWidth = false),
            text = {
                Column(
                    modifier = Modifier.size(600.dp, 330.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TabRow(
                        modifier = Modifier
                            .onFocusChanged {
                                if (it.hasFocus) {
                                    scope.launch(Dispatchers.Main) {
                                        listState.scrollToItem(0)
                                    }
                                }
                            },
                        selectedTabIndex = selectedTabIndex,
                        separator = { Spacer(modifier = Modifier.width(12.dp)) },
                    ) {
                        for (i in 0 until tabCount) {
                            Tab(
                                modifier = if (i == 0) Modifier.focusRequester(
                                    tabRowFocusRequester
                                ) else Modifier,
                                selected = i == selectedTabIndex,
                                onFocus = { selectedTabIndex = i },
                            ) {
                                Text(
                                    text = "P${i * 20 + 1}-${(i + 1) * 20}",
                                    fontSize = 12.sp,
                                    color = LocalContentColor.current,
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 6.dp
                                    )
                                )
                            }
                        }
                    }

                    TvLazyVerticalGrid(
                        state = listState,
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = selectedVideoPart,
                            key = { _, video -> video.cid }
                        ) { index, episode ->
                            val buttonModifier =
                                if (index == 0) Modifier.focusRequester(videoListFocusRequester) else Modifier

                            VideoPartButton(
                                modifier = buttonModifier,
                                index = index + 1,
                                title = episode.title,
                                played = 0,
                                duration = episode.duration,
                                onClick = { onClick(episode.aid, episode.cid) }
                            )
                        }
                    }
                }
            }
        )
        */
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