package dev.aaa1115910.bv.component.controllers

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.video.Subtitle
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.component.comments.VideoCommentsDialog
import dev.aaa1115910.bv.entity.VideoAspectRatio
import dev.aaa1115910.bv.entity.VideoFlip
import dev.aaa1115910.bv.entity.VideoListItem
import dev.aaa1115910.bv.entity.VideoRotation
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.repository.StartupCoverRepository
import dev.aaa1115910.bv.ui.state.PlayerState
import dev.aaa1115910.bv.ui.state.PlayerUiState
import dev.aaa1115910.bv.ui.state.SeekerState
import dev.aaa1115910.bv.ui.theme.AppBlack
import dev.aaa1115910.bv.util.VideoShotImageCache
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.player.DanmakuSettingAction
import dev.aaa1115910.bv.viewmodel.player.MediaProfileSettingAction
import dev.aaa1115910.bv.viewmodel.player.SubtitleSettingAction
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VideoPlayerController(
    modifier: Modifier = Modifier,
    aid: Long,
    fromSeason: Boolean,
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

    content: @Composable () -> Unit
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
        if (isPlaying) onPause() else onPlay()
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        // 中键和下键需要区分短按和长按
        val isConfirmKey =
            event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.Spacebar || event.key == Key.DirectionDown

        if (event.type == KeyEventType.KeyUp && !isConfirmKey) {
            return true
        }

        if (event.key == Key.DirectionDown && directionDownLongPressGuard) {
            if (event.type == KeyEventType.KeyUp) {
                directionDownLongPressGuard = false
            }
            return true
        }

        if (
            (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.Spacebar) &&
            confirmLongPressGuard
        ) {
            if (event.type == KeyEventType.KeyUp) {
                val originSpeed = confirmLongPressOriginSpeed ?: uiState.playSpeed
                onTempPlaySpeedEnd(originSpeed)
                confirmLongPressOriginSpeed = null
                confirmLongPressGuard = false
            }
            return true
        }

        logger.info { "[${event.key} press]" }

        when (event.key) {
            Key.Back -> {
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

            Key.Menu -> {
                showInfoSeekController = false
                showMenuController = !showMenuController
                return true
            }

            Key(763) -> {
                showMenuController = true
                return true
            }

            Key.MediaPlayPause -> {
                onPlayPause()
                return true
            }

            Key.MediaPlay -> {
                if (!isPlaying) onPlay()
                return true
            }

            Key.MediaPause -> {
                if (isPlaying) onPause()
                return true
            }
        }

        if (showClickableControllers) {
            return false
        } else {
            when (event.key) {
                Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                    if (event.type == KeyEventType.KeyDown) {
                        // 新的一次按下确保 guard 关闭；长按触发时再打开
                        if (event.nativeKeyEvent.repeatCount == 0 && !event.nativeKeyEvent.isLongPress) {
                            confirmLongPressGuard = false
                            confirmLongPressOriginSpeed = null
                            return true
                        }

                        if (event.nativeKeyEvent.isLongPress) {
                            //showMenuController = true
                            // 触发长按：临时倍速 = 当前倍速 * 2
                            if (!confirmLongPressGuard) {
                                val originSpeed = uiState.playSpeed
                                confirmLongPressOriginSpeed = originSpeed

                                val boostedSpeed = (originSpeed * 2.0f).coerceAtMost(5f)
                                // 只在临时倍速“生效时”提示一次
                                "播放速度：${boostedSpeed}倍".toast(context)
                                onTempPlaySpeedStart(boostedSpeed)

                                // 打开 guard，让后续事件（包括 KeyUp）被上面的 guard 分支吞掉
                                confirmLongPressGuard = true
                            }
                            return true
                        }
                    }
                    onPlayPause()
                    return true
                }

                Key.DirectionUp -> {
                    showUpPanelController = true
                    return true
                }

                Key.DirectionDown -> {
                    if (event.type == KeyEventType.KeyDown) {
                        // 新的一次按下确保 guard 关闭；长按触发时再打开
                        if (event.nativeKeyEvent.repeatCount == 0 && !event.nativeKeyEvent.isLongPress) {
                            directionDownLongPressGuard = false
                            return true
                        }

                        if (event.nativeKeyEvent.isLongPress) {
                            // 打开 guard，让后续事件（包括 KeyUp）被上面的 guard 分支吞掉
                            directionDownLongPressGuard = true
                            onBackToStart()
                            return true
                        }

                        return true
                    }

                    focusInfoButtonsOnShow = true
                    showInfoSeekController = true
                    return true
                }

                Key.MediaRewind, Key.DirectionLeft -> {
                    if (uiState.showSkipToNextEp) onCancelSkipToNextEp()
                    focusInfoButtonsOnShow = false
                    showInfoSeekController = true
                    onDirectionLeft()
                    return true
                }

                Key.MediaFastForward, Key.DirectionRight -> {
                    focusInfoButtonsOnShow = false
                    showInfoSeekController = true
                    onDirectionRight()
                    return true
                }
            }
        }

        return false
    }

    Box(
        modifier = modifier
            .background(AppBlack)
            .focusable()
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
        content()
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
            if (uiState.subtitleId != -1L) {
                val currentTime = seekerState.value.currentTime

                BottomSubtitle(
                    subtitleData = uiState.subtitleData,
                    currentTime = currentTime,
                    fontSize = uiState.subtitleState.fontSize,
                    opacity = uiState.subtitleState.opacity,
                    padding = uiState.subtitleState.bottomPadding,
                )
            }

            SkipTips(
                showBackToStart = uiState.showBackToStart,
                showSkipToNextEp = uiState.showSkipToNextEp,
                showPreviewTip = uiState.showPreviewTip,
            )

            PlayStateTips(
                isPlaying = uiState.playerState == PlayerState.Playing,
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

            ControllerVideoInfo(
                modifier = Modifier.focusable(),
                show = showInfoSeekController,
                focusButtonsOnShow = focusInfoButtonsOnShow,
                onConsumeFocusButtonsOnShow = { focusInfoButtonsOnShow = false },
                isSeeking = isSeeking,
                goTime = goTime,
                seekerState = seekerState.value,
                title = uiState.title,
                secondTitle = secondTitle,
                clock = uiState.clock,
                currentPlaySpeed = uiState.playSpeed,
                videoShot = uiState.videoShot,
                videoShotCache = videoShotCache,
                videoRotation = uiState.videoRotation,
                videoFlip = uiState.videoFlip,
                fromSeason = fromSeason,
                danmakuEnabled = uiState.danmakuState.danmakuEnabled,
                isLooping = isLooping,
                onDirectionLeft = { onDirectionLeft() },
                onDirectionRight = { onDirectionRight() },
                onSeekGoTime = { onSeekGoTime() },
                onPlayPause = { onPlayPause() },
                onDanmakuSwitchChange = {
                    onDanmakuSettingChange(
                        DanmakuSettingAction.SetDanmakuEnabled(!uiState.danmakuState.danmakuEnabled)
                    )
                },
                onShowSettings = {
                    showInfoSeekController = false
                    showMenuController = true
                },
                onShowRelatedVideos = {
                    if (isPlaying) onPause()

                    showInfoSeekController = false
                    showRelatedVideosController = true
                },
                onGoToVideoInfo = {
                    StartupCoverRepository.put(aid, uiState.startupCover)
                    VideoInfoActivity.actionStart(
                        context = context,
                        aid = aid,
                        fromSeason = fromSeason,
                        fromController = true,
                        proxyArea = proxyArea
                    )
                },
                onToggleLoop = onToggleLoop,
                onGoToUpPage = onGoToUpPage,
                hasMultipleCoAuthors = uiState.coAuthors.distinctBy { it.mid }.size > 1,
                onShowTimeJump = {
                    onPause()
                    showInfoSeekController = false
                    showTimeJumpDialog = true
                },
                onShowComments = {
                    onPause()
                    showInfoSeekController = false
                    showCommentsDialog = true
                },
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
