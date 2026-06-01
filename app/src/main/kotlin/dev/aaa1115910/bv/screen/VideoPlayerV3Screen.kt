package dev.aaa1115910.bv.screen

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.component.CoAuthorsDialogHost
import dev.aaa1115910.bv.component.controllers.BottomSubtitle
import dev.aaa1115910.bv.component.controllers.VideoPlayerController
import dev.aaa1115910.bv.component.controllers.VideoProgressSeek
import dev.aaa1115910.bv.component.handleUpHomeClick
import dev.aaa1115910.bv.component.rememberCoAuthorsDialogState
import dev.aaa1115910.bv.entity.VideoAspectRatio
import dev.aaa1115910.bv.entity.VideoListItem
import dev.aaa1115910.bv.player.AbstractVideoPlayer
import dev.aaa1115910.bv.player.BvPlayerSurface
import dev.aaa1115910.bv.wjzdanmaku.api.DanmakuSurface
import dev.aaa1115910.bv.player.impl.exo.ExoMediaPlayer
import dev.aaa1115910.bv.repository.StartupCoverRepository
import dev.aaa1115910.bv.ui.effect.PlayerUiEffect
import dev.aaa1115910.bv.ui.state.PlayerState
import dev.aaa1115910.bv.ui.theme.AppBlack
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.VideoShotImageCache
import dev.aaa1115910.bv.viewmodel.player.PlayerDemandFeature
import dev.aaa1115910.bv.viewmodel.player.VideoPlayerV3ViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.androidx.compose.koinViewModel

@Composable
fun VideoPlayerV3Screen(
    modifier: Modifier = Modifier,
    playerViewModel: VideoPlayerV3ViewModel = koinViewModel()
) {
    val logger = KotlinLogging.logger { }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val videoPlayer = playerViewModel.videoPlayer

    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val seekerState = playerViewModel.seekerState.collectAsStateWithLifecycle()
    val danmakuHostViewModelState by playerViewModel.danmakuHostState.collectAsStateWithLifecycle()
    val featureGate by playerViewModel.featureGate.collectAsStateWithLifecycle()
    val latestPlayerState by rememberUpdatedState(uiState.playerState)
    val startupCoverAlpha by animateFloatAsState(
        targetValue = if (
            uiState.startupCover.isNotBlank() &&
            !(uiState.hasRenderedFirstFrame && uiState.hasStartedPlayback)
        ) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "startupCoverAlpha"
    )

    val coAuthorsDialogState = rememberCoAuthorsDialogState()
    var lastCoAuthorsDialogVisible by remember { mutableStateOf(false) }

    var isLooping by remember { mutableStateOf(false) }
    val coverGone = uiState.hasRenderedFirstFrame && uiState.hasStartedPlayback

    val videoShotCache by remember(uiState.videoShot) { mutableStateOf(VideoShotImageCache()) }
    fun finishPlayer() {
        playerViewModel.setSuppressPlayerErrors(true)
        playerViewModel.getUgcDetailBackStackForExit()
            .drop(1)
            .forEach { aid ->
                VideoInfoActivity.actionStart(context, aid = aid)
            }
        (context as Activity).finish()
    }

    if (videoPlayer == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(AppBlack)
        )
        return
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            playerViewModel.uiEffect.collect { effect ->
                when (effect) {
                    PlayerUiEffect.FinishActivity -> {
                        finishPlayer()
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
    }

    // 循环发送心跳，避免捕获陈旧 uiState
    LaunchedEffect(Unit) {
        delay(5000)
        while (isActive) {
            if (latestPlayerState == PlayerState.Playing) {
                playerViewModel.trySendHeartbeat()
            }
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

    LaunchedEffect(coverGone, uiState.aid, uiState.cid) {
        if (coverGone) {
            withFrameNanos { }
            playerViewModel.onCoverGoneNextFrame()
        }
    }

    PlayerVideoStage(
        modifier = modifier,
        uiState = uiState,
        videoPlayer = videoPlayer,
        startupCoverAlpha = startupCoverAlpha
    ) { displayAspectRatio ->
        if (featureGate.visualLayersOnline && danmakuHostViewModelState.config.enabled) {
            DanmakuSurface(
                modifier = Modifier.fillMaxSize(),
                currentTime = seekerState.value.currentTime,
                isPlaying = danmakuHostViewModelState.isPlaying,
                playbackSpeed = danmakuHostViewModelState.playbackSpeed,
                config = danmakuHostViewModelState.config,
                mask = uiState.danmakuMask,
                maskEnabled = danmakuHostViewModelState.maskEnabled,
                sourceMode = danmakuHostViewModelState.sourceMode,
                videoAspectRatio = displayAspectRatio,
                commandFlow = playerViewModel.danmakuHostCommands,
                onSessionEvent = playerViewModel::onDanmakuHostSessionEvent
            )
        }

        if (featureGate.visualLayersOnline && uiState.subtitleId != -1L) {
            BottomSubtitle(
                subtitleData = uiState.subtitleData,
                currentTime = seekerState.value.currentTime,
                fontSize = uiState.subtitleState.fontSize,
                opacity = uiState.subtitleState.opacity,
                padding = uiState.subtitleState.bottomPadding,
            )
        }

        if (featureGate.visualLayersOnline && Prefs.showPersistentSeek) {
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

        if (featureGate.visualLayersOnline) {
            VideoPlayerController(
                modifier = Modifier.fillMaxSize(),
                aid = uiState.aid,
                source = uiState.source,
                proxyArea = uiState.proxyArea,
                isLooping = isLooping,
                isPlaying = videoPlayer.isPlaying,
                videoShotCache = videoShotCache,
                uiState = uiState,
                seekerState = seekerState,
                isDanmakuRefreshing = playerViewModel.isDanmakuRefreshing,
                onPlay = { videoPlayer.start() },
                onPause = {
                    videoPlayer.pause()
                    playerViewModel.trySendHeartbeat()
                },
                onExit = { finishPlayer() },
                onGoTime = { time -> playerViewModel.seekToTime(time) },
                onBackToStart = { playerViewModel.backToStart() },
                onPlayNewVideo = {
                    playerViewModel.trySendHeartbeat()
                    playerViewModel.playNewVideo(it)
                },
                onCancelSkipToNextEp = { playerViewModel.cancelPlayNext() },
                onToggleLoop = { isLooping = !isLooping },
                onGoToUpPage = {
                    playerViewModel.onDemandFeatureRequested(PlayerDemandFeature.BottomBar)
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
                onVideoTransformReset = { playerViewModel.resetVideoTransform() },
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
                onTempPlaySpeedStart = { speed ->
                    playerViewModel.startTempPlaySpeed(speed)
                },
                onTempPlaySpeedEnd = { speed ->
                    playerViewModel.endTempPlaySpeed(speed)
                },
                onDanmakuSettingChange = { action ->
                    playerViewModel.updateDanmakuState(action)
                    logger.info { "On danmaku state change" }
                },
                onDanmakuReload = { playerViewModel.reloadDanmakuFromCurrent() },
                onSubtitleChange = { subtitle -> playerViewModel.loadSubtitle(subtitle.id) },
                onSubtitleSettingChange = { action ->
                    logger.info { "On subtitle config change" }
                    playerViewModel.updateSubtitleState(action)
                },
                onRelatedVideoClicked = { video ->
                    StartupCoverRepository.put(video.avid, video.cover)
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
                onDemandFeatureRequested = playerViewModel::onDemandFeatureRequested
            )
        }
    }

    CoAuthorsDialogHost(
        state = coAuthorsDialogState,
        onClickAuthor = { mid, name ->
            UpInfoActivity.actionStart(context, mid = mid, name = name)
        }
    )
}

@Composable
private fun PlayerVideoStage(
    modifier: Modifier,
    uiState: dev.aaa1115910.bv.ui.state.PlayerUiState,
    videoPlayer: AbstractVideoPlayer,
    startupCoverAlpha: Float,
    visualLayer: @Composable BoxScope.(displayAspectRatio: Float) -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(AppBlack),
        contentAlignment = Alignment.Center
    ) {
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

        val composeSurfacePlayer = (videoPlayer as? ExoMediaPlayer)?.mPlayer

        Box(
            modifier = playerModifier
                .align(Alignment.Center)
                .background(Color.Black)
        ) {
            BvPlayerSurface(
                modifier = Modifier.fillMaxSize(),
                player = composeSurfacePlayer
            )
        }

        if (startupCoverAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppBlack.copy(alpha = startupCoverAlpha))
            ) {
                AsyncImage(
                    model = uiState.startupCover,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                        .graphicsLayer(alpha = startupCoverAlpha),
                    contentScale = ContentScale.Fit
                )
            }
        }

        visualLayer(displayAspectRatio)
    }
}
