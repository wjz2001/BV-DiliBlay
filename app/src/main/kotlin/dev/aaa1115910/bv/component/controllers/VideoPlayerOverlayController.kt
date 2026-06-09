package dev.aaa1115910.bv.component.controllers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.video.Subtitle
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntrySurface
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryId
import dev.aaa1115910.bv.wjzfocus.WjzFocusHost
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.WjzFocusRequestResult
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusSourceToken
import dev.aaa1115910.bv.wjzfocus.WjzFocusSubmitIntent
import dev.aaa1115910.bv.wjzfocus.down
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.component.comments.VideoCommentsDialog
import dev.aaa1115910.bv.wjzfocus.rememberWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.target
import dev.aaa1115910.bv.wjzfocus.up
import dev.aaa1115910.bv.wjzfocus.wjzFocusLocalId
import dev.aaa1115910.bv.entity.VideoAspectRatio
import dev.aaa1115910.bv.entity.VideoFlip
import dev.aaa1115910.bv.entity.VideoListItem
import dev.aaa1115910.bv.entity.VideoRotation
import dev.aaa1115910.bv.entity.VideoSource
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.repository.StartupCoverRepository
import dev.aaa1115910.bv.ui.state.PlayerState
import dev.aaa1115910.bv.ui.state.PlayerUiState
import dev.aaa1115910.bv.ui.state.SeekerState
import dev.aaa1115910.bv.ui.theme.AppBlack
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.BvKeyDirection
import dev.aaa1115910.bv.util.VideoShotImageCache
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.util.bvKeyDirection
import dev.aaa1115910.bv.util.isConfirmKey
import dev.aaa1115910.bv.util.isKeyDown
import dev.aaa1115910.bv.util.isKeyUp
import dev.aaa1115910.bv.util.isMenuKey
import dev.aaa1115910.bv.viewmodel.player.DanmakuSettingAction
import dev.aaa1115910.bv.viewmodel.player.MediaProfileSettingAction
import dev.aaa1115910.bv.viewmodel.player.PlayerDemandFeature
import dev.aaa1115910.bv.viewmodel.player.SubtitleSettingAction
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PlayerControllerFocusScopeId = WjzFocusScopeId("player/controller")
private const val PlayerControllerFocusComponentId = "playerController"
private val PlayerControllerRootFocusLocalId = wjzFocusLocalId("root")
private const val PlayerControllerUpPanelEntryLocalId = "upPanel"
private const val PlayerControllerPlayerControlsEntryLocalId = "playerControls"
private const val PlayerControllerInfoPanelEntryLocalId = "infoPanel"
private const val PlayerControllerMenuEntryLocalId = "menu"
private val PlayerControllerUpPanelDefaultFocusLocalId = wjzFocusLocalId("up-panel", "nav", "video")
private val PlayerControllerFirstActionFocusLocalId = wjzFocusLocalId("actions", "danmaku")
private val PlayerControllerUpPanelEntryId =
    WjzFocusEntryId("$PlayerControllerFocusComponentId/$PlayerControllerUpPanelEntryLocalId")
private val PlayerControllerPlayerControlsEntryId =
    WjzFocusEntryId("$PlayerControllerFocusComponentId/$PlayerControllerPlayerControlsEntryLocalId")
private val PlayerControllerMenuEntryId =
    WjzFocusEntryId("$PlayerControllerFocusComponentId/$PlayerControllerMenuEntryLocalId")

private fun playerControllerTarget(localId: WjzFocusLocalId) =
    PlayerControllerFocusScopeId.target(localId).copy(layer = WjzFocusLayer.Player)

@Composable
fun VideoPlayerOverlayController(
    modifier: Modifier = Modifier,
    aid: Long,
    source: VideoSource,
    proxyArea: ProxyArea,

    // play state
    isLooping: Boolean,
    isPlaying: Boolean,

    // UI related state
    videoShotCache: VideoShotImageCache,
    uiState: PlayerUiState,
    seekerState: State<SeekerState>,
    isDanmakuRefreshing: Boolean = false,

    // player events
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onExit: () -> Unit,
    onGoTime: (time: Long) -> Unit,
    onBackToStart: () -> Unit,
    onCancelSkipToNextEp: () -> Unit,
    onPlayNewVideo: (VideoListItem) -> Unit,
    onToggleLoop: () -> Unit,
    onGoToUpPage: () -> Unit,

    //menu events
    onMediaProfileSettingChange: (MediaProfileSettingAction) -> Unit,
    onAspectRatioChange: (VideoAspectRatio) -> Unit,
    onVideoTransformReset: () -> Unit,
    onVideoRotationChange: (VideoRotation?) -> Unit,
    onVideoFlipChange: (VideoFlip?) -> Unit,
    onPlaySpeedChange: (Float) -> Unit,
    onTempPlaySpeedStart: (Float) -> Unit,
    onTempPlaySpeedEnd: (Float) -> Unit,
    onDanmakuSettingChange: (DanmakuSettingAction) -> Unit,
    onDanmakuReload: () -> Unit,
    onSubtitleChange: (Subtitle) -> Unit,
    onSubtitleSettingChange: (SubtitleSettingAction) -> Unit,
    onRelatedVideoClicked: (VideoCardData) -> Unit,

    onEnsureUgcPagesLoaded: (aid: Long) -> Unit,
    onDemandFeatureRequested: (PlayerDemandFeature) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logger = KotlinLogging.logger {}

    //var showListController by remember { mutableStateOf(false) }
    var showUpPanelController by remember { mutableStateOf(false) }
    var showMenuController by remember { mutableStateOf(false) }
    var showInfoSeekController by remember { mutableStateOf(false) }
    var showRelatedVideosController by remember { mutableStateOf(false) }
    var directionDownLongPressGuard by remember { mutableStateOf(false) }
    var confirmLongPressGuard by remember { mutableStateOf(false) }
    var confirmLongPressOriginSpeed by remember { mutableStateOf<Float?>(null) }
    val showClickableControllers by remember {
        derivedStateOf { showUpPanelController || showMenuController || showInfoSeekController || showRelatedVideosController ||
            directionDownLongPressGuard }
    }

    var lastPressBack by remember { mutableLongStateOf(0L) }
    var goTime by remember { mutableLongStateOf(0L) }

    var isSeeking by remember { mutableStateOf(false) }
    var seekChangeCount by remember { mutableIntStateOf(0) }
    var lastSeekChangeTime by remember { mutableLongStateOf(0L) }

    var seekCountdown: Job? by remember { mutableStateOf(null) }
    var hideInfoSeekControllerCountdown: Job? by remember { mutableStateOf(null) }

    var showTimeJumpDialog by remember { mutableStateOf(false) }
    var showCommentsDialog by remember { mutableStateOf(false) }
    var focusInfoButtonsOnShow by remember { mutableStateOf(false) }
    val focusCoordinator = rememberWjzFocusCoordinator()
    var playerSourceToken by remember { mutableStateOf<WjzFocusSourceToken?>(null) }
    val currentPlayerSourceToken by rememberUpdatedState(playerSourceToken)

    LaunchedEffect(showClickableControllers) {
        if (showClickableControllers) {
            if (playerSourceToken == null) {
                playerSourceToken = focusCoordinator.activateLayer(
                    layer = WjzFocusLayer.Player,
                    recordSource = true
                )
            }
            repeat(2) {
                focusCoordinator.restoreActiveLayer(PlayerControllerFocusScopeId)
                delay(100)
            }
        } else if (playerSourceToken != null) {
            val restored = focusCoordinator.restoreSourceLayer(
                expectedActiveLayer = WjzFocusLayer.Player,
                token = playerSourceToken
            )
            if (restored) {
                playerSourceToken = null
            }
        }
    }

    DisposableEffect(focusCoordinator) {
        onDispose {
            currentPlayerSourceToken?.let {
                focusCoordinator.restoreSourceLayer(
                    expectedActiveLayer = WjzFocusLayer.Player,
                    token = it
                )
            }
            playerSourceToken = null
        }
    }

    LaunchedEffect(focusCoordinator.activeLayer) {
        if (focusCoordinator.activeLayer == WjzFocusLayer.Player) {
            focusCoordinator.restoreActiveLayer(PlayerControllerFocusScopeId)
        }
    }

    fun calCoefficient(): Int {
        return if (System.currentTimeMillis() - lastSeekChangeTime < 200) {
            seekChangeCount++
            seekChangeCount / 5
        } else {
            seekChangeCount = 0
            0
        }
    }

    fun onTimeForward() {
        isSeeking = true
        val targetTime = goTime + (10000 + calCoefficient() * 5000)
        goTime =
            if (targetTime > seekerState.value.totalDuration) seekerState.value.totalDuration else targetTime
        lastSeekChangeTime = System.currentTimeMillis()
        logger.info { "onTimeForward: [goTime=$goTime]" }
    }

    fun onTimeBack() {
        isSeeking = true
        // 快退一次从10s改为5s
        val targetTime = goTime - (5000 + calCoefficient() * 5000)
        goTime = if (targetTime < 0) 0 else targetTime
        lastSeekChangeTime = System.currentTimeMillis()
        logger.info { "onTimeBack: [goTime=$goTime]" }
    }

    fun startSeekCountdown() {
        seekCountdown?.cancel()
        seekCountdown = scope.launch {
            delay(1000)

            onGoTime(goTime)
            if (!isPlaying) onPlay()

            isSeeking = false
            showInfoSeekController = false
            hideInfoSeekControllerCountdown?.cancel()
        }
    }

    fun onDirectionLeft() {
        if (!isSeeking) goTime = seekerState.value.currentTime
        onTimeBack()
        startSeekCountdown()
    }

    fun onDirectionRight() {
        if (!isSeeking) goTime = seekerState.value.currentTime
        onTimeForward()
        startSeekCountdown()
    }

    fun onSeekGoTime() {
        onGoTime(goTime)
        isSeeking = false
        if (!isPlaying) onPlay()
        showInfoSeekController = false
        seekCountdown?.cancel()
    }

    fun onPlayPause() {
        if (isPlaying) {
            onPause()
        } else {
            onPlay()
        }
    }

    fun requestPlayerControllerEntryFocus(entryId: WjzFocusEntryId): Boolean {
        if (playerSourceToken == null) {
            playerSourceToken = focusCoordinator.activateLayer(
                layer = WjzFocusLayer.Player,
                recordSource = true
            )
        } else {
            focusCoordinator.activateLayer(WjzFocusLayer.Player)
        }
        return when (focusCoordinator.submitEntryFocusIntent(
            entryId = entryId,
            intent = WjzFocusSubmitIntent.ExternalEntry(
                dedupeKey = entryId.value
            )
        )) {
            WjzFocusRequestResult.Focused,
            WjzFocusRequestResult.Enqueued -> true
            WjzFocusRequestResult.Dropped,
            WjzFocusRequestResult.Failed -> false
        }
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        // 确认键和下键都要区分短按/长按
        val isConfirmOrDown =
            event.isConfirmKey() || event.bvKeyDirection() == BvKeyDirection.Down

        if (event.isKeyUp() && !isConfirmOrDown) {
            return true
        }

        if (event.bvKeyDirection() == BvKeyDirection.Down && directionDownLongPressGuard) {
            if (event.isKeyUp()) {
                directionDownLongPressGuard = false
            }
            return true
        }

        if (event.isConfirmKey() && confirmLongPressGuard) {
            if (event.isKeyUp()) {
                val originSpeed = confirmLongPressOriginSpeed ?: uiState.playSpeed
                onTempPlaySpeedEnd(originSpeed)
                confirmLongPressOriginSpeed = null
                confirmLongPressGuard = false
            }
            return true
        }

        logger.info { "[${event.key} press]" }

        when {
            event.key == Key.Back -> {
                if (showClickableControllers) {
                    showMenuController = false
                    showUpPanelController = false
                    showInfoSeekController = false
                    showRelatedVideosController = false
                } else {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastPressBack < 3000) {
                        onExit()
                    } else {
                        lastPressBack = currentTime
                        R.string.video_player_press_back_again_to_exit.toast(context)
                    }
                }
                return true
            }

            event.isMenuKey() -> {
                if (showMenuController) {
                    showMenuController = false
                } else {
                    requestPlayerControllerEntryFocus(PlayerControllerMenuEntryId)
                }
                return true
            }

            event.key == Key.MediaPlayPause -> {
                onPlayPause()
                return true
            }

            event.key == Key.MediaPlay -> {
                if (!isPlaying) onPlay()
                return true
            }

            event.key == Key.MediaPause -> {
                if (isPlaying) onPause()
                return true
            }
        }

        if (showClickableControllers) {
            return false
        }

        if (event.isConfirmKey()) {
            if (event.isKeyDown()) {
                if (event.nativeKeyEvent.repeatCount == 0 && !event.nativeKeyEvent.isLongPress) {
                    confirmLongPressGuard = false
                    confirmLongPressOriginSpeed = null
                    return true
                }

                if (event.nativeKeyEvent.isLongPress) {
                    if (!confirmLongPressGuard) {
                        val originSpeed = uiState.playSpeed
                        confirmLongPressOriginSpeed = originSpeed

                        val boostedSpeed = (originSpeed * 2.0f).coerceAtMost(5f)
                        "播放速度：${boostedSpeed}倍".toast(context)
                        onTempPlaySpeedStart(boostedSpeed)

                        confirmLongPressGuard = true
                    }
                    return true
                }
            }

            onPlayPause()
            return true
        }

        when (event.bvKeyDirection()) {
            BvKeyDirection.Up -> {
                requestPlayerControllerEntryFocus(PlayerControllerUpPanelEntryId)
                return true
            }

            BvKeyDirection.Down -> {
                if (event.isKeyDown()) {
                    if (event.nativeKeyEvent.repeatCount == 0 && !event.nativeKeyEvent.isLongPress) {
                        directionDownLongPressGuard = false
                        return true
                    }

                    if (event.nativeKeyEvent.isLongPress) {
                        directionDownLongPressGuard = true
                        onBackToStart()
                        return true
                    }

                    return true
                }

                requestPlayerControllerEntryFocus(PlayerControllerPlayerControlsEntryId)
                return true
            }

            BvKeyDirection.Left -> {
                if (uiState.showSkipToNextEp) onCancelSkipToNextEp()
                focusInfoButtonsOnShow = false
                showInfoSeekController = true
                onDirectionLeft()
                return true
            }

            BvKeyDirection.Right -> {
                focusInfoButtonsOnShow = false
                showInfoSeekController = true
                onDirectionRight()
                return true
            }

            null -> return false
        }
    }

    WjzFocusHost(
        modifier = modifier,
        coordinator = focusCoordinator,
        layer = WjzFocusLayer.Player,
        scopeId = PlayerControllerFocusScopeId
    ) {
        WjzFocusEntrySurface(
            componentId = PlayerControllerFocusComponentId,
            default = {
                playerControllerTarget(PlayerControllerRootFocusLocalId)
            },
            entries = {
                entry(PlayerControllerUpPanelEntryLocalId) {
                    onDemandFeatureRequested(PlayerDemandFeature.BottomBar)
                    showMenuController = false
                    showInfoSeekController = false
                    showRelatedVideosController = false
                    focusInfoButtonsOnShow = false
                    showUpPanelController = true
                    playerControllerTarget(PlayerControllerUpPanelDefaultFocusLocalId)
                }
                entry(PlayerControllerPlayerControlsEntryLocalId) {
                    onDemandFeatureRequested(PlayerDemandFeature.BottomBar)
                    showMenuController = false
                    showUpPanelController = false
                    showRelatedVideosController = false
                    focusInfoButtonsOnShow = true
                    showInfoSeekController = true
                    playerControllerTarget(PlayerControllerFirstActionFocusLocalId)
                }
                entry(PlayerControllerInfoPanelEntryLocalId) {
                    onDemandFeatureRequested(PlayerDemandFeature.BottomBar)
                    showMenuController = false
                    showUpPanelController = false
                    showRelatedVideosController = false
                    focusInfoButtonsOnShow = false
                    showInfoSeekController = true
                    playerControllerTarget(PlayerControllerRootFocusLocalId)
                }
                entry(PlayerControllerMenuEntryLocalId) {
                    showInfoSeekController = false
                    showUpPanelController = false
                    showRelatedVideosController = false
                    focusInfoButtonsOnShow = false
                    showMenuController = true
                    playerControllerTarget(PlayerControllerRootFocusLocalId)
                }
            }
        )
        Box(
            modifier = Modifier
                .wjzFocusExits(
                    localId = PlayerControllerRootFocusLocalId,
                    layer = WjzFocusLayer.Player,
                    exits = {
                        up move PlayerControllerUpPanelEntryId
                        down move PlayerControllerPlayerControlsEntryId
                    }
                )
                .onPreviewKeyEvent { event ->
                // 重置 info 控制器的隐藏倒计时 (只要有按键活动就重置)
                if (showInfoSeekController) {
                    hideInfoSeekControllerCountdown?.cancel()
                    hideInfoSeekControllerCountdown = scope.launch {
                        delay(5000)
                        showInfoSeekController = false
                    }
                }
                // 调用分离出去的处理函数
                handleKeyEvent(event)
            }
        ) {
        if (BuildConfig.DEBUG) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(AppBlack.copy(alpha = 0.3f))
            ) {
                Text(
                    modifier = Modifier.padding(8.dp),
                    text = seekerState.value.debugInfo
                )
            }
        }
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = LocalDensity.current.density * 1.5f,
                fontScale = LocalDensity.current.fontScale * 1.5f
            )
        ) {
            SkipTips(
                showBackToStart = uiState.showBackToStart,
                showSkipToNextEp = uiState.showSkipToNextEp,
                showPreviewTip = uiState.showPreviewTip,
            )

            PlayStateTips(
                isBuffering = uiState.isBuffering,
                isError = uiState.playerState is PlayerState.Error,
                errorMessage = (uiState.playerState as? PlayerState.Error)?.message,
            )

            RelatedVideosController(
                show = showRelatedVideosController,
                relatedVideos = uiState.relatedVideos,
                onVideoClicked = {
                    onRelatedVideoClicked(it)
                    showRelatedVideosController = false
                }
            )

            val secondTitle = uiState.partTitle.ifBlank {
                uiState.availableVideoList.firstOrNull { it.cid == uiState.cid }?.title.orEmpty()
            }
            val bottomActions = listOfNotNull(
                VideoPlayerOverlayAction.Resource(
                    id = "danmaku",
                    iconRes = if (uiState.danmakuState.danmakuEnabled) {
                        R.drawable.danmaku_on_24px
                    } else {
                        R.drawable.danmaku_off_24px
                    },
                    description = "弹幕开关",
                    onClick = {
                        onDanmakuSettingChange(
                            DanmakuSettingAction.SetDanmakuEnabled(
                                !uiState.danmakuState.danmakuEnabled
                            )
                        )
                    }
                ),
                VideoPlayerOverlayAction.Resource(
                    id = "comments",
                    iconRes = R.drawable.comment_24px,
                    description = "评论",
                    onClick = {
                        onPause()
                        showInfoSeekController = false
                        showCommentsDialog = true
                    }
                ),
                VideoPlayerOverlayAction.Resource(
                    id = "time-jump",
                    iconRes = R.drawable.manage_history_24px,
                    description = "时间跳转",
                    onClick = {
                        onPause()
                        showInfoSeekController = false
                        showTimeJumpDialog = true
                    }
                ),
                if (!Prefs.showVideoInfo) {
                    VideoPlayerOverlayAction.Vector(
                        id = "video-info",
                        imageVector = Icons.Rounded.Info,
                        description = "详情页",
                        onClick = {
                            StartupCoverRepository.put(aid, uiState.startupCover)
                            val targetSeasonId = uiState.seasonId.toLong().takeIf { it > 0L }
                            if (source == VideoSource.Cheese && targetSeasonId == null) {
                                "课程详情缺少 seasonId".toast(context)
                            } else {
                                VideoInfoActivity.actionStart(
                                    context = context,
                                    aid = aid,
                                    source = source,
                                    epid = uiState.epid,
                                    seasonId = targetSeasonId,
                                    fromController = true,
                                    proxyArea = proxyArea
                                )
                            }
                        }
                    )
                } else {
                    null
                },
                if (source.isUgc) {
                    VideoPlayerOverlayAction.Resource(
                        id = "up-page",
                        iconRes = if (uiState.coAuthors.distinctBy { it.mid }.size > 1) {
                            R.drawable.group_24px
                        } else {
                            R.drawable.contact_page_24px
                        },
                        description = "up主页",
                        onClick = onGoToUpPage
                    )
                } else {
                    null
                },
                if (source.isUgc) {
                    VideoPlayerOverlayAction.Resource(
                        id = "related-videos",
                        iconRes = R.drawable.related_videos_24px,
                        description = "相关视频",
                        onClick = {
                            onDemandFeatureRequested(PlayerDemandFeature.BottomBar)
                            if (isPlaying) onPause()

                            showInfoSeekController = false
                            showRelatedVideosController = true
                        }
                    )
                } else {
                    null
                },
                VideoPlayerOverlayAction.Resource(
                    id = "loop",
                    iconRes = if (isLooping) {
                        R.drawable.repeat_one_on_24px
                    } else {
                        R.drawable.repeat_one_24px
                    },
                    description = "循环播放",
                    onClick = onToggleLoop
                ),
            )

            VideoPlayerInfoOverlay(
                show = showInfoSeekController,
                focusButtonsOnShow = focusInfoButtonsOnShow,
                onConsumeFocusButtonsOnShow = { focusInfoButtonsOnShow = false },
                isSeeking = isSeeking,
                goTime = goTime,
                seekerState = seekerState.value,
                title = uiState.title,
                secondTitle = secondTitle,
                currentPlaySpeed = uiState.playSpeed,
                videoShot = uiState.videoShot,
                videoShotCache = videoShotCache,
                videoRotation = uiState.videoRotation,
                videoFlip = uiState.videoFlip,
                actions = bottomActions,
            )

            TimeJumpDialog(
                show = showTimeJumpDialog,
                durationMs = seekerState.value.totalDuration,
                onDismiss = { showTimeJumpDialog = false; onPlay() },
                onGoTime = { targetMs -> onGoTime(targetMs) }
            )

            UpPanelController(
                show = showUpPanelController,
                uiState = uiState,
                currentTimeMs = seekerState.value.currentTime,
                isPlaying = isPlaying,
                onDismiss = { showUpPanelController = false },
                onGoTime = { targetMs ->
                    onGoTime(targetMs)
                    if (!isPlaying) onPlay()
                },
                onPlay = onPlay,
                onPlayNewVideo = onPlayNewVideo,
                onEnsureUgcPagesLoaded = onEnsureUgcPagesLoaded
            )

            MenuController(
                show = showMenuController,
                uiState = uiState,
                isDanmakuRefreshing = isDanmakuRefreshing,
                onResolutionChange = { qualityId ->
                    onMediaProfileSettingChange(
                        MediaProfileSettingAction.SetQuality(qualityId)
                    )
                },
                onCodecChange = { codec ->
                    onMediaProfileSettingChange(
                        MediaProfileSettingAction.SetVideoCodec(codec)
                    )
                },
                onAudioChange = { audio ->
                    onMediaProfileSettingChange(
                        MediaProfileSettingAction.SetAudio(audio)
                    )
                },
                onAspectRatioChange = onAspectRatioChange,
                onVideoTransformReset = onVideoTransformReset,
                onVideoRotationChange = onVideoRotationChange,
                onVideoFlipChange = onVideoFlipChange,
                onPlaySpeedChange = onPlaySpeedChange,
                onDanmakuSwitchChange = { types ->
                    onDanmakuSettingChange(DanmakuSettingAction.SetEnabledTypes(types))
                },
                onDanmakuSizeChange = { scale ->
                    onDanmakuSettingChange(DanmakuSettingAction.SetScale(scale))
                },
                onDanmakuOpacityChange = { opacity ->
                    onDanmakuSettingChange(DanmakuSettingAction.SetOpacity(opacity))
                },
                onDanmakuRollingDurationFactorChange = { factor ->
                    onDanmakuSettingChange(DanmakuSettingAction.SetRollingDurationFactor(factor))
                },
                onDanmakuVodFilterLevelChange = { level ->
                    onDanmakuSettingChange(DanmakuSettingAction.SetVodFilterLevel(level))
                },
                onDanmakuLiveFilterLevelChange = { level ->
                    onDanmakuSettingChange(DanmakuSettingAction.SetLiveFilterLevel(level))
                },
                onDanmakuColorfulChange = { enabled ->
                    onDanmakuSettingChange(DanmakuSettingAction.SetColorful(enabled))
                },
                onDanmakuAreaChange = { area ->
                    onDanmakuSettingChange(DanmakuSettingAction.SetArea(area))
                },
                onDanmakuMaskChange = { enabled ->
                    onDanmakuSettingChange(DanmakuSettingAction.SetMaskEnabled(enabled))
                },
                onDanmakuReload = onDanmakuReload,
                onSubtitleChange = onSubtitleChange,
                onSubtitleSizeChange = { size ->
                    onSubtitleSettingChange(SubtitleSettingAction.SetFontSize(size))
                },
                onSubtitleBackgroundOpacityChange = { opacity ->
                    onSubtitleSettingChange(SubtitleSettingAction.SetOpacity(opacity))
                },
                onSubtitleBottomPadding = { padding ->
                    onSubtitleSettingChange(SubtitleSettingAction.SetBottomPadding(padding))
                }
            )
        }

            VideoCommentsDialog(
                show = showCommentsDialog,
                aid = aid,
                onDismissRequest = {
                    showCommentsDialog = false
                    onPlay()
                }
            )
        }
    }
}
