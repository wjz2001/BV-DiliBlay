package dev.aaa1115910.bv.screen

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.aaa1115910.biliapi.entity.danmaku.DanmakuMaskFrame
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.component.CoAuthorsDialogHost
import dev.aaa1115910.bv.component.DanmakuPlayerCompose
import dev.aaa1115910.bv.component.controllers.VideoPlayerController
import dev.aaa1115910.bv.component.controllers.VideoProgressSeek
import dev.aaa1115910.bv.component.handleUpHomeClick
import dev.aaa1115910.bv.component.ifElse
import dev.aaa1115910.bv.component.rememberCoAuthorsDialogState
import dev.aaa1115910.bv.entity.VideoAspectRatio
import dev.aaa1115910.bv.entity.VideoListItem
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.player.BvVideoPlayer
import dev.aaa1115910.bv.ui.effect.PlayerUiEffect
import dev.aaa1115910.bv.ui.state.PlayerState
import dev.aaa1115910.bv.util.DanmakuMaskFinder
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.VideoShotImageCache
import dev.aaa1115910.bv.util.calculateMaskDelay
import dev.aaa1115910.bv.util.danmakuMask
import dev.aaa1115910.bv.viewmodel.player.VideoPlayerV3ViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.androidx.compose.koinViewModel
import kotlin.math.absoluteValue

@Composable
fun VideoPlayerV3Screen(
    modifier: Modifier = Modifier,
    playerViewModel: VideoPlayerV3ViewModel = koinViewModel()
) {
    val logger = KotlinLogging.logger { }
    val context = LocalContext.current
    val videoPlayer = playerViewModel.videoPlayer
    val danmakuPlayer = playerViewModel.danmakuPlayer

    val uiState by playerViewModel.uiState.collectAsState()
    val seekerState = playerViewModel.seekerState.collectAsState()

    val coAuthorsDialogState = rememberCoAuthorsDialogState()
    var lastCoAuthorsDialogVisible by remember { mutableStateOf(false) }

    val maskFinder = remember { DanmakuMaskFinder() }
    var currentDanmakuMaskFrame: DanmakuMaskFrame? by remember { mutableStateOf(null) }
    var isLooping by remember { mutableStateOf(false) }

    val videoShotCache by remember(uiState.videoShot) { mutableStateOf(VideoShotImageCache()) }

    if (videoPlayer == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        return
    }

    LaunchedEffect(Unit) {
        playerViewModel.uiEffect.collect { effect ->
            when (effect) {
                PlayerUiEffect.FinishActivity -> {
                    playerViewModel.setSuppressPlayerErrors(true)
                    (context as Activity).finish()
                }

                PlayerUiEffect.PlayEnded -> {
                    if (isLooping) {
                        playerViewModel.backToStart()
                        return@collect
                    }

                    playerViewModel.checkAndPlayNext()
                }
            }
        }
    }

    // 循环发送心跳
    LaunchedEffect(Unit) {
        delay(5000)
        while (isActive) {
            if (uiState.playerState == PlayerState.Playing) playerViewModel.trySendHeartbeat()
            delay(15000)
        }
    }

    LaunchedEffect(coAuthorsDialogState.visible) {
        val now = coAuthorsDialogState.visible
        if (!now && lastCoAuthorsDialogVisible) {
            videoPlayer.start()
        }
        lastCoAuthorsDialogVisible = now
    }

    // 弹幕防遮挡蒙版更新
    LaunchedEffect(uiState.danmakuState.maskEnabled, uiState.danmakuMasks) {
        if (!uiState.danmakuState.maskEnabled || uiState.danmakuMasks.isEmpty()) {
            currentDanmakuMaskFrame = null
            return@LaunchedEffect
        }

        maskFinder.reset()

        val masks = uiState.danmakuMasks
        var lastCheckTime = -1L

        while (isActive) {
            val currentTime = seekerState.value.currentTime
            val isPlaying = uiState.playerState == PlayerState.Playing
            val isTimeJumping = (currentTime - lastCheckTime).absoluteValue > 200

            if (isPlaying || isTimeJumping) {
                val foundFrame = maskFinder.findFrame(masks, currentTime)

                if (currentDanmakuMaskFrame != foundFrame) {
                    currentDanmakuMaskFrame = foundFrame
                }

                lastCheckTime = currentTime

                val delayTime = calculateMaskDelay(foundFrame, currentTime, isPlaying)
                delay(delayTime)
            } else {
                delay(500L)
            }
        }
    }

    VideoPlayerController(
        modifier = modifier,
        aid = uiState.aid,
        fromSeason = uiState.fromSeason,
        proxyArea = ProxyArea.MainLand,
        isLooping = isLooping,
        isPlaying = videoPlayer.isPlaying,
        videoShotCache = videoShotCache,
        uiState = uiState,
        seekerState = seekerState,
        onPlay = { videoPlayer.start() },
        onPause = {
            videoPlayer.pause()
            playerViewModel.trySendHeartbeat()
        },
        onExit = {
            playerViewModel.setSuppressPlayerErrors(true)
            (context as Activity).finish()
        },
        onGoTime = { time ->
            playerViewModel.seekToTime(time)
        },
        onBackToStart = { playerViewModel.backToStart() },
        onPlayNewVideo = {
            playerViewModel.trySendHeartbeat()
            playerViewModel.playNewVideo(it)
        },
        onCancelSkipToNextEp = {
            playerViewModel.cancelPlayNext()
        },
        onToggleLoop = {
            isLooping = !isLooping
        },
        onGoToUpPage = {
            val dedup = uiState.coAuthors.distinctBy { it.mid }

            if (dedup.size > 1) {
                videoPlayer.pause()
                playerViewModel.trySendHeartbeat()
            }

            handleUpHomeClick(
                authors = uiState.coAuthors,
                state = coAuthorsDialogState,
                onNavigateSingle = { mid, name ->
                    UpInfoActivity.actionStart(
                        context,
                        mid = mid,
                        name = name
                    )
                }
            )
        },
        onMediaProfileSettingChange = { action ->
            playerViewModel.updateMediaProfile(action)
        },
        onAspectRatioChange = { aspectRadio ->
            playerViewModel.updateVideoAspectRatio(aspectRadio)
        },
        onVideoTransformReset = {
            playerViewModel.resetVideoTransform()
        },
        onVideoRotationChange = { rotation ->
            playerViewModel.updateVideoRotation(rotation)
        },
        onVideoFlipChange = { flip ->
            playerViewModel.updateVideoFlip(flip)
        },
        onPlaySpeedChange = { speed ->
            logger.info { "Set default play speed: $speed" }
            playerViewModel.updatePlaySpeed(speed)
        },
        onTempPlaySpeedChange = { speed ->
            videoPlayer.speed = speed
            playerViewModel.safeUpdateDanmakuPlaySpeedTemp(speed)
        },
        onDanmakuSettingChange = { action ->
            playerViewModel.updateDanmakuState(action)
            logger.info { "On danmaku state change" }
        },
        onSubtitleChange = { subtitle ->
            playerViewModel.loadSubtitle(subtitle.id)
        },
        onSubtitleSettingChange = { action ->
            logger.info { "On subtitle config change" }
            playerViewModel.updateSubtitleState(action)
        },
        onRelatedVideoClicked = { video ->
            video.cid?.let {
                playerViewModel.playNewVideo(
                    VideoListItem(
                        aid = video.avid,
                        cid = video.cid,
                        title = video.title,
                    )
                )
            }
        },
        onEnsureUgcPagesLoaded = playerViewModel::ensureUgcPagesLoaded,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            LaunchedEffect(videoPlayer) {
                videoPlayer.setOptions()
            }

            val actualVideoWidth = videoPlayer.videoWidth
            val actualVideoHeight = videoPlayer.videoHeight

            val baseAspectRatio = when (uiState.aspectRatio) {
                VideoAspectRatio.Default -> {
                    when {
                        actualVideoWidth > 0 && actualVideoHeight > 0 -> {
                            actualVideoWidth / actualVideoHeight.toFloat()
                        }

                        uiState.videoHeight > 0 && uiState.videoWidth > 0 -> {
                            uiState.videoWidth / uiState.videoHeight.toFloat()
                        }

                        else -> 16 / 9f
                    }
                }

                VideoAspectRatio.FourToThree -> 4 / 3f
                VideoAspectRatio.SixteenToNine -> 16 / 9f
            }.takeIf { it > 0f } ?: (16 / 9f)

            val displayAspectRatio = if (uiState.videoRotation?.isQuarterTurn == true) {
                1f / baseAspectRatio
            } else {
                baseAspectRatio
            }

            val screenAspectRatio = if (maxHeight.value > 0f) {
                maxWidth.value / maxHeight.value
            } else {
                16 / 9f
            }

            val playerModifier = if (displayAspectRatio > screenAspectRatio) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(displayAspectRatio)
            } else {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(displayAspectRatio)
            }

            BvVideoPlayer(
                modifier = playerModifier
                    .align(Alignment.Center)
                    .onGloballyPositioned { coordinates ->
                        val size = coordinates.size
                    },
                videoPlayer = videoPlayer,
            )

            DanmakuPlayerCompose(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(uiState.danmakuState.opacity)
                    .ifElse(
                        { Prefs.defaultDanmakuMask },
                        Modifier.danmakuMask(currentDanmakuMaskFrame, displayAspectRatio)
                    ),
                danmakuPlayer = danmakuPlayer,
            )

            if (Prefs.showPersistentSeek) {
                VideoProgressSeek(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    duration = seekerState.value.totalDuration,
                    position = seekerState.value.currentTime,
                    bufferedPercentage = seekerState.value.bufferedPercentage,
                    isPersistentSeek = true
                )
            }
        }
    }

    CoAuthorsDialogHost(
        state = coAuthorsDialogState,
        onClickAuthor = { mid, name ->
            UpInfoActivity.actionStart(context, mid = mid, name = name)
        }
    )
}