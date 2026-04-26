package dev.aaa1115910.bv.viewmodel.player

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.PlayData
import dev.aaa1115910.biliapi.entity.danmaku.DanmakuMask
import dev.aaa1115910.biliapi.entity.video.HeartbeatVideoType
import dev.aaa1115910.biliapi.entity.video.Subtitle
import dev.aaa1115910.biliapi.entity.video.SubtitleAiStatus
import dev.aaa1115910.biliapi.entity.video.SubtitleAiType
import dev.aaa1115910.biliapi.entity.video.SubtitleType
import dev.aaa1115910.biliapi.entity.video.VideoPage
import dev.aaa1115910.biliapi.repositories.VideoPlayRepository
import dev.aaa1115910.bilisubtitle.SubtitleParser
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.component.controllers.DanmakuType
import dev.aaa1115910.bv.component.controllers.playermenu.PlaySpeedItem
import dev.aaa1115910.bv.entity.Audio
import dev.aaa1115910.bv.entity.Resolution
import dev.aaa1115910.bv.entity.VideoAspectRatio
import dev.aaa1115910.bv.entity.VideoCodec
import dev.aaa1115910.bv.entity.VideoFlip
import dev.aaa1115910.bv.entity.VideoListItem
import dev.aaa1115910.bv.entity.VideoRotation
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.player.AbstractVideoPlayer
import dev.aaa1115910.bv.player.VideoPlayerListener
import dev.aaa1115910.bv.player.VideoPlayerOptions
import dev.aaa1115910.bv.player.impl.exo.ExoPlayerFactory
import dev.aaa1115910.bv.player.impl.exo.ExoMediaPlayer
import dev.aaa1115910.bv.player.danmaku.config.DanmakuConfig
import dev.aaa1115910.bv.player.danmaku.host.DanmakuSourceMode
import dev.aaa1115910.bv.player.danmaku.model.DanmakuFilterRule
import dev.aaa1115910.bv.player.danmaku.model.DanmakuSessionEvent
import dev.aaa1115910.bv.player.danmaku.model.DanmakuSessionEventType
import dev.aaa1115910.bv.player.danmaku.session.DanmakuLiveEventStream
import dev.aaa1115910.bv.player.danmaku.session.DanmakuLiveInput
import dev.aaa1115910.bv.repository.StartupCoverRepository
import dev.aaa1115910.bv.repository.VideoInfoRepository
import dev.aaa1115910.bv.screen.settings.content.ActionAfterPlayItems
import dev.aaa1115910.bv.ui.effect.PlayerUiEffect
import dev.aaa1115910.bv.ui.state.DanmakuState
import dev.aaa1115910.bv.ui.state.MediaProfileState
import dev.aaa1115910.bv.ui.state.PlayerState
import dev.aaa1115910.bv.ui.state.PlayerUiState
import dev.aaa1115910.bv.ui.state.SeekerState
import dev.aaa1115910.bv.ui.state.SubtitleState
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fException
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import dev.aaa1115910.bv.util.formatHourMinSec
import dev.aaa1115910.bv.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.annotation.KoinViewModel
import java.net.URI
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun media3Slashy(): String = androidx.media3.common.MediaLibraryInfo.VERSION_SLASHY

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun hasDetachSurfaceTimeout(t: Throwable?): Boolean {
    var cur: Throwable? = t
    while (cur != null) {
        if (cur is androidx.media3.exoplayer.ExoTimeoutException &&
            cur.timeoutOperation ==
            androidx.media3.exoplayer.ExoTimeoutException.TIMEOUT_OPERATION_DETACH_SURFACE
        ) return true
        cur = cur.cause
    }
    return false
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@KoinViewModel
class VideoPlayerV3ViewModel(
    private val videoInfoRepository: VideoInfoRepository,
    private val videoPlayRepository: VideoPlayRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger { }

    var videoPlayer: AbstractVideoPlayer? by mutableStateOf(null)
        private set

    var isDanmakuRefreshing: Boolean by mutableStateOf(false)
        private set

    private var playData: PlayData? = null

    private val subtitleHttpClient = HttpClient(OkHttp)
    private val detachedWorkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class OneShotApiOverride(
        val aid: Long,
        val cid: Long,
        val apiType: ApiType,
        val preferredQualityId: Int?,
        val generation: Long
    )

    @Volatile
    private var oneShotApiOverride: OneShotApiOverride? = null

    @Volatile
    private var tempPlaySpeedOverride: Float? = null

    @Volatile
    private var currentPlayApiType: ApiType = Prefs.apiType

    @Volatile
    private var app302FallbackTried: Boolean = false

    private data class ContinueSelection(
        val type: SubtitleType,
        val langKey: String
    )

    private data class ContinuePlayPending(
        val aid: Long,
        val cid: Long,
        val selection: ContinueSelection?
    )

    @Volatile
    private var allowFastResumeOnNextStart: Boolean = false

    @Volatile
    private var continuePlayPending: ContinuePlayPending? = null

    @Volatile
    private var suppressPlayerErrors: Boolean = false

    @Volatile
    private var needRecreateOnStart: Boolean = false

    @Volatile
    private var fastResumeEnabled: Boolean = true

    @Volatile
    private var surfaceBugDetected: Boolean = false

    private var pendingResumePositionMs: Long = 0L
    private val recreateInProgress = AtomicBoolean(false)

    @Volatile
    private var envLogged: Boolean = false

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState = _uiState.asStateFlow()

    private val _seekerState = MutableStateFlow(SeekerState())
    val seekerState = _seekerState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<PlayerUiEffect>()
    val uiEffect = _uiEffect.asSharedFlow()

    private val _danmakuHostState = MutableStateFlow(DanmakuHostViewModelState())
    val danmakuHostState = _danmakuHostState.asStateFlow()

    private val _danmakuHostCommands = MutableSharedFlow<DanmakuHostCommand>(extraBufferCapacity = 16)
    val danmakuHostCommands = _danmakuHostCommands.asSharedFlow()

    private var seekerUpdateJob: Job? = null
    private var clockUpdateJob: Job? = null
    private var heartbeatJob: Job? = null
    private var loadPlayUrlJob: Job? = null
    private var subtitleLoadJob: Job? = null

    private var backToStartCountdownJob: Job? = null
    private var playNextCountdownJob: Job? = null
    private var previewTipCountdownJob: Job? = null

    private var ugcPagesPrefetchJob: Job? = null
    private var lastPrefetchCenterAid: Long = -1L
    private var lastPrefetchAidsHash: Int = 0

    private val ugcPagesPrefetchDelayMs = 800L
    private val ugcPagesPrefetchFailureDelayMs = 3000L
    private val bufferingShowDelayMs = 150L
    private val bufferingMinShowMs = 400L

    private val loadGeneration = AtomicLong(0L)

    @Volatile
    private var activeLoadGeneration: Long = 0L
    private var bufferingShowJob: Job? = null
    private var bufferingHideJob: Job? = null
    @Volatile
    private var isRawBuffering: Boolean = false
    @Volatile
    private var bufferingVisibleSinceMs: Long = 0L

    private val videoPlayerListener = object : VideoPlayerListener {
        override fun onError(error: Exception) {
            if (tryAutoSwitchToWebForApp302(error)) return

            if (suppressPlayerErrors) {
                val isDetachTimeoutByType = hasDetachSurfaceTimeout(error)
                val msg = if (!isDetachTimeoutByType) error.message.orEmpty() else ""
                val st = if (!isDetachTimeoutByType) error.stackTraceToString() else ""

                val isSurfaceDetachError =
                    isDetachTimeoutByType ||
                            msg.contains("Detaching surface timed out", ignoreCase = true) ||
                            st.contains("Detaching surface timed out", ignoreCase = true) ||
                            st.contains("native_setSurface", ignoreCase = true) ||
                            st.contains("setOutputSurface", ignoreCase = true)

                if (isSurfaceDetachError) {
                    surfaceBugDetected = true
                    needRecreateOnStart = true
                    Log.e("BugDebug", "ViewModel onError suppressed (surface bug): ${error.message}", error)
                    return
                }
            }

            Log.e("BugDebug", "ViewModel onError -> PlayerState.Error", error)
            logger.info { "onError: $error" }
            onBufferingStateChanged(false)
            _uiState.update {
                it.copy(
                    playerState = PlayerState.Error(
                        error.message ?: "Unknown error"
                    )
                )
            }
            syncDanmakuHostStateFromPlayerState()
        }

        override fun onReady() {
            logger.info { "onReady" }
            _uiState.update { it.copy(playerState = PlayerState.Ready) }
            onBufferingStateChanged(false)
            syncDanmakuHostStateFromPlayerState()

            val tempSpeed = tempPlaySpeedOverride
            if (tempSpeed != null) {
                videoPlayer?.speed = tempSpeed
                syncDanmakuHostStateFromPlayerState()
            } else {
                updatePlaySpeed(forceUpdate = true)
            }

            startSeekerUpdater()
        }

        override fun onPlay() {
            logger.info { "onPlay" }
            _uiState.update {
                it.copy(
                    playerState = PlayerState.Playing,
                    hasStartedPlayback = true
                )
            }
            syncDanmakuHostStateFromPlayerState()
            onBufferingStateChanged(false)

            if (_uiState.value.lastPlayed > 0) {
                seekToLastPlayed()
                _uiState.update { it.copy(lastPlayed = 0L) }
            }
        }

        override fun onPause() {
            logger.info { "onPause" }
            _uiState.update { it.copy(playerState = PlayerState.Paused) }
            syncDanmakuHostStateFromPlayerState()
        }

        override fun onBuffering() {
            logger.info { "onBuffering" }
            onBufferingStateChanged(true)
            syncDanmakuHostStateFromPlayerState()
        }

        override fun onEnd() {
            logger.info { "onEnd" }
            stopSeekerUpdater()
            onBufferingStateChanged(false)

            _uiState.update { it.copy(playerState = PlayerState.Ended) }
            syncDanmakuHostStateFromPlayerState()
            viewModelScope.launch {
                _uiEffect.emit(PlayerUiEffect.PlayEnded)
            }
        }

        override fun onSeekBack(seekBackIncrementMs: Long) {}
        override fun onSeekForward(seekForwardIncrementMs: Long) {}
        override fun onRenderedFirstFrame() {
            _uiState.update {
                if (it.hasRenderedFirstFrame) it else it.copy(hasRenderedFirstFrame = true)
            }
        }
    }

    init {
        videoInfoRepository.videoList
            .onEach { newList ->
                if (newList.isEmpty()) return@onEach

                _uiState.update { currentState ->
                    val updated = currentState.copy(availableVideoList = newList)

                    if (updated.epid != null) return@update updated

                    val currentItem = newList.firstOrNull { it.aid == updated.aid }
                    val pageTitle = currentItem
                        ?.ugcPages
                        ?.firstOrNull { it.cid == updated.cid }
                        ?.title
                        .orEmpty()

                    if (pageTitle.isNotBlank() && pageTitle != updated.partTitle) {
                        updated.copy(partTitle = pageTitle)
                    } else {
                        updated
                    }
                }
                logger.fInfo { "Sync video list from repo, size: ${newList.size}" }
                restartUgcPagesPrefetchIfNeeded(newList)
            }
            .launchIn(viewModelScope)

        videoInfoRepository.videoDetailState
            .filter { it?.aid == _uiState.value.aid }
            .onEach { newDetail ->
                if (newDetail == null) return@onEach

                _uiState.update { currentState ->
                    currentState.copy(
                        relatedVideos = newDetail.relatedVideos,
                        ugcSeason = newDetail.ugcSeason,
                        coAuthors = newDetail.coAuthors
                    )
                }
                logger.fInfo { "Sync related videos from repo" }

                syncVideoListFromUgcSeasonIfNeeded(
                    detailAid = newDetail.aid,
                    ugcSeason = newDetail.ugcSeason
                )
            }
            .launchIn(viewModelScope)

        uiState
            .map { it.aid to it.cid }
            .distinctUntilChanged()
            .mapLatest { (aid, cid) ->
                if (aid == 0L || cid == 0L) return@mapLatest emptyList()

                _uiState.update { it.copy(availableChapters = emptyList()) }

                runCatching {
                    withContext(Dispatchers.IO) {
                        videoPlayRepository.getViewPoints(aid = aid, cid = cid)
                    }
                }.fold(
                    onSuccess = { viewPoints ->
                        viewPoints.mapNotNull { element ->
                            val obj = element as? JsonObject ?: return@mapNotNull null
                            val content = obj["content"]?.jsonPrimitive?.content.orEmpty()
                            val from = obj["from"]?.jsonPrimitive?.content?.toIntOrNull()
                                ?: return@mapNotNull null
                            val to = obj["to"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                            if (content.isBlank()) return@mapNotNull null

                            dev.aaa1115910.bv.ui.state.PlayerChapter(
                                content = content,
                                fromSec = from,
                                toSec = to
                            )
                        }
                    },
                    onFailure = { throwable ->
                        logger.fWarn {
                            "Load viewpoints failed: aid=$aid, cid=$cid, error=${throwable.stackTraceToString()}"
                        }
                        emptyList()
                    }
                )
            }
            .onEach { chapters ->
                _uiState.update { it.copy(availableChapters = chapters) }
            }
            .launchIn(viewModelScope)
    }

    fun init(
        aid: Long,
        cid: Long,
        epid: Int?,
        title: String,
        partTitle: String,
        lastPlayed: Long,
        fromSeason: Boolean,
        subType: Int,
        seasonId: Int,
        proxyArea: ProxyArea = ProxyArea.MainLand,
        authorMid: Long = 0,
        authorName: String
    ) {
        _uiState.update {
            it.copy(
                aid = aid,
                cid = cid,
                epid = epid.takeIf { value -> value != 0 },
                seasonId = seasonId,
                title = title,
                partTitle = partTitle,
                startupCover = StartupCoverRepository[aid],
                lastPlayed = lastPlayed,
                fromSeason = fromSeason,
                subType = subType,
                proxyArea = proxyArea,
                authorMid = authorMid,
                authorName = authorName,
                hasRenderedFirstFrame = false,
                hasStartedPlayback = false,
                mediaProfileState = MediaProfileState(
                    qualityId = Prefs.defaultQuality.code,
                    videoCodec = Prefs.defaultVideoCodec,
                    audio = Prefs.defaultAudio
                ),
                playSpeed = Prefs.defaultPlaySpeed.speed,
                videoRotation = null,
                videoFlip = null,
                danmakuState = DanmakuState(
                    scale = Prefs.defaultDanmakuScale,
                    opacity = Prefs.defaultDanmakuOpacity,
                    area = Prefs.defaultDanmakuArea,
                    rollingDurationFactor = Prefs.defaultDanmakuRollingDurationFactor,
                    vodFilterLevel = Prefs.defaultDanmakuFilterLevel,
                    liveFilterLevel = Prefs.defaultLiveDanmakuFilterLevel,
                    colorful = Prefs.defaultDanmakuColorful,
                    maskEnabled = Prefs.defaultDanmakuMask,
                    enabledTypes = Prefs.defaultDanmakuTypes,
                    danmakuEnabled = Prefs.defaultDanmakuEnabled,
                ),
                subtitleState = SubtitleState(
                    fontSize = Prefs.defaultSubtitleFontSize,
                    opacity = Prefs.defaultSubtitleBackgroundOpacity,
                    bottomPadding = Prefs.defaultSubtitleBottomPadding
                )
            )
        }
        syncDanmakuHostStateFromPlayerState()

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                videoInfoRepository.ensureCoAuthorsLoaded(
                    aid = aid,
                    preferApiType = Prefs.apiType
                )
            }.onFailure { e ->
                logger.fWarn {
                    "Prefetch coAuthors in player failed: aid=$aid, apiType=${Prefs.apiType}, error=${e.stackTraceToString()}"
                }
            }
        }

        startClockUpdater()
        restartUgcPagesPrefetchIfNeeded(_uiState.value.availableVideoList)
    }

    /**
     * 方案 B：公开初始化播放器接口，供 Activity 按上游方式调用
     */
    fun initVideoPlayer(context: Context) {
        if (videoPlayer != null) return

        val options = VideoPlayerOptions(
            userAgent = when (Prefs.apiType) {
                ApiType.Web -> context.getString(R.string.video_player_user_agent_http)
                ApiType.App -> context.getString(R.string.video_player_user_agent_client)
            },
            referer = when (Prefs.apiType) {
                ApiType.Web -> context.getString(R.string.video_player_referer)
                ApiType.App -> null
            },
            enableFfmpegAudioRenderer = Prefs.enableFfmpegAudioRenderer,
            enableSoftwareVideoDecoder = Prefs.enableSoftwareVideoDecoder
        )

        val player = ExoPlayerFactory().create(context.applicationContext, options)

        attachPlayer(player)
    }

    /**
     * 方案 B：公开加载入口，供 Activity 按上游方式调用
     */
    fun loadVideoWithResources() {
        val state = _uiState.value

        if (state.aid == 0L || state.cid == 0L) {
            logger.fWarn { "Skip loadVideoWithResources: invalid aid/cid" }
            return
        }

        if (videoPlayer == null) {
            logger.fWarn { "Skip loadVideoWithResources: videoPlayer is null" }
            return
        }

        _uiState.update {
            it.copy(
                hasRenderedFirstFrame = false,
                hasStartedPlayback = false
            )
        }

        loadPlayUrl(
            avid = state.aid,
            cid = state.cid,
            epid = state.epid,
            seasonId = state.seasonId.takeIf { it != 0 },
            title = state.title,
            partTitle = state.partTitle.ifBlank { state.title }
        )
    }

    fun attachPlayer(player: AbstractVideoPlayer) {
        videoPlayer = player
        videoPlayer?.setPlayerEventListener(videoPlayerListener)
        applyVideoTransformToPlayer()
    }

    fun detachPlayer() {
        stopSeekerUpdater()
        syncProgress(scope = detachedWorkScope, isDetaching = true)

        videoPlayer?.release()
        videoPlayer = null
    }

    fun setSuppressPlayerErrors(suppress: Boolean) {
        suppressPlayerErrors = suppress
        Log.i("BugDebug", "ViewModel setSuppressPlayerErrors=$suppress")
    }

    fun onHostStopReleaseForRecreate() {
        val player = videoPlayer
        val pos = player?.currentPosition ?: 0L
        pendingResumePositionMs = pos.coerceAtLeast(0L)
        needRecreateOnStart = true

        _uiState.update { it.copy(lastPlayed = pendingResumePositionMs) }

        Log.i(
            "BugDebug",
            "ViewModel onHostStopReleaseForRecreate: posMs=$pendingResumePositionMs"
        )

        runCatching {
            (player as? ExoMediaPlayer)
                ?.mPlayer
                ?.clearVideoSurface()
        }

        runCatching { player?.release() }
            .onFailure { Log.e("BugDebug", "ViewModel: release() failed", it) }
    }

    fun onHostStartRecreateAndAutoPlayIfNeeded() {
        if (!needRecreateOnStart) return
        if (!recreateInProgress.compareAndSet(false, true)) {
            Log.w("BugDebug", "ViewModel onHostStart: recreate already in progress, skip")
            return
        }

        needRecreateOnStart = false
        _uiState.update {
            it.copy(
                hasRenderedFirstFrame = false,
                hasStartedPlayback = false
            )
        }

        try {
            val player = videoPlayer
            if (player == null) {
                Log.e("BugDebug", "ViewModel onHostStart: videoPlayer is null (unexpected)")
                return
            }

            Log.i("BugDebug", "ViewModel onHostStart: recreate player and auto play")

            runCatching { player.initPlayer() }
                .onFailure {
                    Log.e("BugDebug", "ViewModel: initPlayer() failed", it)
                    _uiState.update { s ->
                        s.copy(
                            playerState = PlayerState.Error(
                                it.message ?: "initPlayer failed"
                            )
                        )
                    }
                    return
                }

            applyVideoTransformToPlayer()

            if (!envLogged) {
                envLogged = true
                Log.i(
                    "BugDebug",
                    "Env: MANUFACTURER=${Build.MANUFACTURER}, MODEL=${Build.MODEL}, SDK=${Build.VERSION.SDK_INT}," +
                            " Media3=${media3Slashy()}, softDecode=${Prefs.enableSoftwareVideoDecoder}"
                )
            }

            val cachedPlayData = playData
            if (cachedPlayData != null) {
                _uiState.update {
                    it.copy(
                        hasRenderedFirstFrame = false,
                        hasStartedPlayback = false
                    )
                }
                runCatching {
                    playQuality(
                        playData = cachedPlayData,
                        apiType = currentPlayApiType,
                        qn = _uiState.value.mediaProfileState.qualityId,
                        codec = _uiState.value.mediaProfileState.videoCodec,
                        audio = _uiState.value.mediaProfileState.audio
                    )
                    if (pendingResumePositionMs > 0) {
                        player.seekTo(pendingResumePositionMs)
                        _uiState.update { it.copy(lastPlayed = 0L) }
                    }
                    player.setOptions()
                    player.start()
                }.onFailure {
                    Log.e("BugDebug", "ViewModel: replay with cached playData failed", it)
                    _uiState.update { s ->
                        s.copy(
                            playerState = PlayerState.Error(
                                it.message ?: "replay failed"
                            )
                        )
                    }
                }
            } else {
                player.setOptions()
                val st = _uiState.value
                Log.w("BugDebug", "ViewModel: playData is null, fallback to loadPlayUrl")
                loadPlayUrl(
                    avid = st.aid,
                    cid = st.cid,
                    epid = st.epid,
                    seasonId = st.seasonId.takeIf { it != 0 },
                    title = st.title,
                    partTitle = st.partTitle
                )
            }
        } finally {
            recreateInProgress.set(false)
        }
    }

    fun onHostStopFastResume() {
        val player = videoPlayer ?: return

        // 只有真正经历过一次 stop，下一次 start 才允许 fast resume
        allowFastResumeOnNextStart = true

        pendingResumePositionMs = player.currentPosition.coerceAtLeast(0L)
        _uiState.update { it.copy(lastPlayed = pendingResumePositionMs) }

        Log.i(
            "BugDebug",
            "ViewModel onHostStopFastResume: fast=$fastResumeEnabled surfaceBugDetected=$surfaceBugDetected posMs=$pendingResumePositionMs"
        )

        runCatching { player.pause() }

        if (!fastResumeEnabled) {
            onHostStopReleaseForRecreate()
            return
        }

        if (surfaceBugDetected) {
            onHostStopReleaseForRecreate()
        }
    }

    fun onHostStartFastResumeOrRecreate() {
        if (needRecreateOnStart) {
            Log.i("BugDebug", "ViewModel onHostStartFastResumeOrRecreate: recreate")
            onHostStartRecreateAndAutoPlayIfNeeded()
            surfaceBugDetected = false
            allowFastResumeOnNextStart = false
            return
        }

        // 首次启动时跳过 fast resume，避免和 loadVideoWithResources() 的 start 重叠
        if (!allowFastResumeOnNextStart) {
            Log.i("BugDebug", "ViewModel onHostStartFastResumeOrRecreate: skip fast resume on first start")
            return
        }

        if (!fastResumeEnabled) {
            allowFastResumeOnNextStart = false
            return
        }

        Log.i("BugDebug", "ViewModel onHostStartFastResumeOrRecreate: fast resume")
        allowFastResumeOnNextStart = false
        runCatching {
            videoPlayer?.let { player ->
                val currentPosition = player.currentPosition.coerceAtLeast(0L)
                val shouldSeekToPendingPosition = pendingResumePositionMs > 0 &&
                        (currentPosition == 0L || pendingResumePositionMs - currentPosition > 1000L)

                if (shouldSeekToPendingPosition) {
                    player.seekTo(pendingResumePositionMs)
                    _uiState.update { it.copy(lastPlayed = 0L) }
                }

                player.start()
            }
        }
    }

    fun loadSubtitle(
        id: Long,
        expectedAid: Long = _uiState.value.aid,
        expectedCid: Long = _uiState.value.cid,
        expectedGeneration: Long? = null
    ) {
        subtitleLoadJob?.cancel()
        subtitleLoadJob = viewModelScope.launch(Dispatchers.IO) {
            if (!isSubtitleRequestCurrent(expectedAid, expectedCid, expectedGeneration)) return@launch

            if (id == -1L) {
                if (isSubtitleRequestCurrent(expectedAid, expectedCid, expectedGeneration)) {
                    _uiState.update {
                        it.copy(
                            subtitleId = -1,
                            subtitleData = emptyList()
                        )
                    }
                }
                return@launch
            }

            var subtitleName = ""
            runCatching {
                val subtitle =
                    _uiState.value.availableSubtitles.find { it.id == id } ?: return@runCatching
                subtitleName = subtitle.langDoc
                logger.info { "Subtitle url: ${subtitle.url}" }

                val responseText = subtitleHttpClient.get(subtitle.url).bodyAsText()
                val subtitleData = SubtitleParser.fromBccString(responseText)

                if (!isSubtitleRequestCurrent(expectedAid, expectedCid, expectedGeneration)) {
                    return@runCatching
                }

                _uiState.update {
                    it.copy(
                        subtitleId = id,
                        subtitleData = subtitleData
                    )
                }
            }.onFailure {
                if (it is CancellationException) throw it
                logger.fInfo { "Load subtitle failed: ${it.stackTraceToString()}" }
            }.onSuccess {
                if (subtitleName.isNotBlank()) {
                    logger.fInfo { "Load subtitle $subtitleName success" }
                }
            }
        }
    }

    fun updatePlaySpeed(
        speed: Float? = null,
        forceUpdate: Boolean = false
    ) {
        val currentSpeed = _uiState.value.playSpeed
        val targetSpeed = speed ?: currentSpeed

        if (!forceUpdate && currentSpeed == targetSpeed) return

        _uiState.update { it.copy(playSpeed = targetSpeed) }
        videoPlayer?.speed = targetSpeed
        syncDanmakuHostStateFromPlayerState()

        if (speed != null) {
            tempPlaySpeedOverride = null
            Prefs.defaultPlaySpeed = PlaySpeedItem.fromSpeedNearest(targetSpeed)
        }
    }

    fun updateVideoAspectRatio(aspectRatio: VideoAspectRatio) {
        _uiState.update {
            it.copy(aspectRatio = aspectRatio)
        }
    }

    fun resetVideoTransform() {
        if (_uiState.value.videoRotation == null && _uiState.value.videoFlip == null) return

        _uiState.update {
            it.copy(
                videoRotation = null,
                videoFlip = null
            )
        }

        applyVideoTransformToPlayer(
            rotation = null,
            flip = null
        )
    }

    fun updateVideoRotation(rotation: VideoRotation?) {
        if (_uiState.value.videoRotation == rotation) return

        _uiState.update {
            it.copy(videoRotation = rotation)
        }

        applyVideoTransformToPlayer(rotation = rotation)
    }

    fun updateVideoFlip(flip: VideoFlip?) {
        if (_uiState.value.videoFlip == flip) return

        _uiState.update {
            it.copy(videoFlip = flip)
        }

        applyVideoTransformToPlayer(flip = flip)
    }

    private fun applyVideoTransformToPlayer(
        rotation: VideoRotation? = _uiState.value.videoRotation,
        flip: VideoFlip? = _uiState.value.videoFlip
    ) {
        (videoPlayer as? ExoMediaPlayer)?.applyVideoTransform(
            scaleX = flip?.scaleX ?: 1f,
            scaleY = flip?.scaleY ?: 1f,
            rotationDegrees = rotation?.effectDegreesCounterClockwise ?: 0f
        )
    }

    fun updateMediaProfile(action: MediaProfileSettingAction) {
        val old = _uiState.value.mediaProfileState
        val new = when (action) {
            is MediaProfileSettingAction.SetQuality -> old.copy(qualityId = action.value)
            is MediaProfileSettingAction.SetVideoCodec -> old.copy(videoCodec = action.value)
            is MediaProfileSettingAction.SetAudio -> old.copy(audio = action.value)
        }

        if (old == new) return

        _uiState.update { it.copy(mediaProfileState = new) }

        videoPlayer?.let { player ->
            player.pause()
            val currentPosition = player.currentPosition

            playData?.let { currentPlayData ->
                playQuality(
                    playData = currentPlayData,
                    apiType = currentPlayApiType,
                    qn = new.qualityId,
                    codec = new.videoCodec,
                    audio = new.audio
                )

                if (currentPosition > 0) {
                    player.seekTo(currentPosition)
                }
                player.start()
            }
        }
    }

    fun updateDanmakuState(action: DanmakuSettingAction) {
        val old = _uiState.value.danmakuState
        val new = when (action) {
            is DanmakuSettingAction.SetScale -> old.copy(scale = action.value.coerceIn(0.5f, 4f))
            is DanmakuSettingAction.SetOpacity -> old.copy(opacity = action.value.coerceIn(0f, 1f))
            is DanmakuSettingAction.SetArea -> old.copy(area = action.value.coerceIn(0f, 1f))
            is DanmakuSettingAction.SetRollingDurationFactor -> {
                old.copy(rollingDurationFactor = action.value.coerceIn(0.2f, 1.8f))
            }
            is DanmakuSettingAction.SetVodFilterLevel -> {
                old.copy(vodFilterLevel = action.value.coerceIn(0, 10))
            }
            is DanmakuSettingAction.SetLiveFilterLevel -> {
                old.copy(liveFilterLevel = action.value.coerceIn(0, 60))
            }
            is DanmakuSettingAction.SetColorful -> old.copy(colorful = action.value)
            is DanmakuSettingAction.SetMaskEnabled -> old.copy(maskEnabled = action.enabled)
            is DanmakuSettingAction.SetDanmakuEnabled -> old.copy(danmakuEnabled = action.enabled)
            is DanmakuSettingAction.SetEnabledTypes -> old.copy(enabledTypes = action.types)
        }

        if (old == new) return

        val wasEnabled = old.danmakuEnabled
        val isEnabled = new.danmakuEnabled

        _uiState.update { it.copy(danmakuState = new) }
        syncDanmakuHostStateFromPlayerState()

        if (new.enabledTypes != old.enabledTypes) {
            Prefs.defaultDanmakuTypes = new.enabledTypes
        }
        if (new.scale != old.scale) {
            Prefs.defaultDanmakuScale = new.scale
        }
        if (new.rollingDurationFactor != old.rollingDurationFactor) {
            Prefs.defaultDanmakuRollingDurationFactor = new.rollingDurationFactor
        }
        if (new.vodFilterLevel != old.vodFilterLevel) {
            Prefs.defaultDanmakuFilterLevel = new.vodFilterLevel
        }
        if (new.liveFilterLevel != old.liveFilterLevel) {
            Prefs.defaultLiveDanmakuFilterLevel = new.liveFilterLevel
        }
        if (new.colorful != old.colorful) {
            Prefs.defaultDanmakuColorful = new.colorful
        }
        if (new.area != old.area) {
            Prefs.defaultDanmakuArea = new.area
        }
        if (new.opacity != old.opacity) {
            Prefs.defaultDanmakuOpacity = new.opacity
        }
        if (new.maskEnabled != old.maskEnabled) {
            Prefs.defaultDanmakuMask = new.maskEnabled

            if (!new.maskEnabled) {
                _uiState.update { it.copy(danmakuMask = null) }
                syncDanmakuHostStateFromPlayerState()
            } else if (new.danmakuEnabled) {
                val aid = _uiState.value.aid
                val cid = _uiState.value.cid
                val epid = _uiState.value.epid
                val generation = activeLoadGeneration

                viewModelScope.launch(Dispatchers.IO) {
                    updateDanmakuMask(
                        aid = aid,
                        cid = cid,
                        expectedEpid = epid,
                        generation = generation
                    )
                }
            }
        }
        if (new.danmakuEnabled != old.danmakuEnabled) {
            Prefs.defaultDanmakuEnabled = new.danmakuEnabled
        }

        if (wasEnabled && !isEnabled) {
            _uiState.update { it.copy(danmakuMask = null) }
            syncDanmakuHostStateFromPlayerState()
            viewModelScope.launch {
                _danmakuHostCommands.emit(DanmakuHostCommand.Clear(reason = "danmaku_disabled"))
            }
            return
        }

        if (!wasEnabled && isEnabled) {
            viewModelScope.launch {
                updatePlaySpeed(forceUpdate = true)

                val aid = _uiState.value.aid
                val cid = _uiState.value.cid
                val epid = _uiState.value.epid
                val generation = activeLoadGeneration

                if (_uiState.value.danmakuState.maskEnabled) {
                    updateDanmakuMask(
                        aid = aid,
                        cid = cid,
                        expectedEpid = epid,
                        generation = generation
                    )
                }
            }
        }
    }

    fun seekDanmakuHost(posMs: Long) {
        viewModelScope.launch {
            _danmakuHostCommands.emit(
                DanmakuHostCommand.Seek(
                    positionMs = posMs.coerceAtLeast(0L),
                    forceFetch = false,
                    reason = "player_seek"
                )
            )
        }
    }

    fun reloadDanmakuFromCurrent() {
        viewModelScope.launch {
            if (!_uiState.value.danmakuState.danmakuEnabled) return@launch
            if (isDanmakuRefreshing) return@launch

            isDanmakuRefreshing = true
            val currentPosMs = (videoPlayer?.currentPosition ?: 0L).coerceAtLeast(0L)

            try {
                _danmakuHostCommands.emit(
                    DanmakuHostCommand.RefreshFromPosition(
                        positionMs = currentPosMs,
                        forceFetch = true,
                        reason = "manual_refresh"
                    )
                )
                logger.fInfo { "Danmaku refresh started from $currentPosMs ms" }
                "弹幕刷新成功".toast(BVApp.context)
            } catch (err: Throwable) {
                if (err is CancellationException) throw err
                logger.fWarn { "Reload danmaku failed: ${err.stackTraceToString()}" }
                "弹幕刷新失败".toast(BVApp.context)
            } finally {
                isDanmakuRefreshing = false
            }
        }
    }

    fun attachLiveDanmakuInput(roomId: Long, input: DanmakuLiveInput) {
        _danmakuHostState.update { current ->
            current.copy(
                sourceMode = DanmakuSourceMode.Live(
                    roomId = roomId,
                    input = input
                ),
                live = current.live.copy(
                    roomId = roomId,
                    enabled = true,
                    state = "attached",
                    errorMessage = null
                )
            )
        }
    }

    fun attachLiveDanmakuEventStream(roomId: Long, eventStream: DanmakuLiveEventStream) {
        _danmakuHostState.update { current ->
            current.copy(
                sourceMode = DanmakuSourceMode.Live(
                    roomId = roomId,
                    eventStream = eventStream
                ),
                live = current.live.copy(
                    roomId = roomId,
                    enabled = true,
                    state = "attached",
                    errorMessage = null
                )
            )
        }
    }

    fun detachLiveDanmaku(reason: String = "detach_live") {
        _danmakuHostState.update { current ->
            current.copy(
                sourceMode = buildVodSourceMode(_uiState.value),
                live = DanmakuLiveInterfaceState(
                    state = reason
                )
            )
        }
    }

    fun onDanmakuHostSessionStateChanged(sessionId: String, state: String, errorMessage: String? = null) {
        _danmakuHostState.update { current ->
            current.copy(
                live = current.live.copy(
                    sessionId = sessionId,
                    state = state,
                    errorMessage = errorMessage
                )
            )
        }
    }

    fun onDanmakuHostSessionEvent(event: DanmakuSessionEvent) {
        when (event.type) {
            DanmakuSessionEventType.LiveConnected -> {
                onDanmakuHostSessionStateChanged(
                    sessionId = event.sessionId,
                    state = "connected",
                    errorMessage = null
                )
            }

            DanmakuSessionEventType.LiveDisconnected -> {
                onDanmakuHostSessionStateChanged(
                    sessionId = event.sessionId,
                    state = "disconnected",
                    errorMessage = null
                )
            }

            DanmakuSessionEventType.LiveStateChanged -> {
                onDanmakuHostSessionStateChanged(
                    sessionId = event.sessionId,
                    state = event.payload["state"] as? String ?: "unknown",
                    errorMessage = event.payload["error"] as? String
                )
            }

            DanmakuSessionEventType.Error -> {
                onDanmakuHostSessionStateChanged(
                    sessionId = event.sessionId,
                    state = "error",
                    errorMessage = event.payload["message"] as? String
                )
            }

            else -> Unit
        }
    }

    private fun syncDanmakuHostStateFromPlayerState() {
        val state = _uiState.value
        val currentPositionMs = (videoPlayer?.currentPosition ?: 0L).coerceAtLeast(0L)
        val hasVisibleSubtitle = state.subtitleId != -1L &&
            state.subtitleData.any { it.isShowing(currentPositionMs) }
        val extraBottomPaddingPx = if (hasVisibleSubtitle) {
            (4 * BVApp.context.resources.displayMetrics.density).roundToInt()
        } else {
            0
        }
        val subtitleBottomPaddingPx = (
            state.subtitleState.bottomPadding.value * BVApp.context.resources.displayMetrics.density
        ).roundToInt() + extraBottomPaddingPx
        val config = state.danmakuState.toDanmakuConfig(
            bottomPaddingPx = subtitleBottomPaddingPx,
        )
        _danmakuHostState.update { current ->
            current.copy(
                sourceMode = if (current.live.enabled && current.sourceMode is DanmakuSourceMode.Live) {
                    current.sourceMode
                } else {
                    buildVodSourceMode(state)
                },
                config = config,
                filterRule = config.toFilterRule(),
                maskEnabled = state.danmakuState.danmakuEnabled && state.danmakuState.maskEnabled,
                mask = state.danmakuMask,
                aid = state.aid,
                cid = state.cid,
                currentPositionMs = (videoPlayer?.currentPosition ?: 0L).coerceAtLeast(0L),
                isPlaying = state.playerState == PlayerState.Playing,
                playbackSpeed = tempPlaySpeedOverride ?: state.playSpeed
            )
        }
    }

    private fun buildVodSourceMode(state: PlayerUiState): DanmakuSourceMode {
        return if (
            state.danmakuState.danmakuEnabled &&
            state.aid != 0L &&
            state.cid != 0L
        ) {
            DanmakuSourceMode.Vod(
                aid = state.aid,
                cid = state.cid
            )
        } else {
            DanmakuSourceMode.None
        }
    }

    fun startTempPlaySpeed(speed: Float) {
        tempPlaySpeedOverride = speed
        videoPlayer?.speed = speed
        syncDanmakuHostStateFromPlayerState()
    }

    fun endTempPlaySpeed(speed: Float) {
        tempPlaySpeedOverride = null
        videoPlayer?.speed = speed
        syncDanmakuHostStateFromPlayerState()
    }

    fun updateSubtitleState(action: SubtitleSettingAction) {
        val old = _uiState.value.subtitleState
        val new = when (action) {
            is SubtitleSettingAction.SetFontSize -> old.copy(fontSize = action.value)
            is SubtitleSettingAction.SetOpacity -> old.copy(opacity = action.value)
            is SubtitleSettingAction.SetBottomPadding -> old.copy(bottomPadding = action.value)
        }

        if (old == new) return

        _uiState.update { it.copy(subtitleState = new) }

        // ===== 持久化副作用 =====
        if (new.fontSize != old.fontSize) {
            Prefs.defaultSubtitleFontSize = new.fontSize
        }

        if (new.opacity != old.opacity) {
            Prefs.defaultSubtitleBackgroundOpacity = new.opacity
        }

        if (new.bottomPadding != old.bottomPadding) {
            Prefs.defaultSubtitleBottomPadding = new.bottomPadding
        }
        syncDanmakuHostStateFromPlayerState()
    }

    /**
     * 触发播放结束后的检查逻辑
     */
    fun checkAndPlayNext() {
        when (Prefs.actionAfterPlay) {
            ActionAfterPlayItems.Pause -> return
            ActionAfterPlayItems.Exit -> {
                viewModelScope.launch {
                    _uiEffect.emit(PlayerUiEffect.FinishActivity)
                }
                return
            }

            ActionAfterPlayItems.PlayRelated -> {
                val firstRelatedVideo = _uiState.value.relatedVideos.firstOrNull()
                firstRelatedVideo?.cid?.let {
                    val nextVideo = VideoListItem(
                        aid = firstRelatedVideo.avid,
                        cid = firstRelatedVideo.cid,
                        title = firstRelatedVideo.title
                    )
                    playNewVideo(newVideo = nextVideo)

                    // 因为番剧无相关视频，需要继续播放，所以在这里return
                    return
                }
            }

            ActionAfterPlayItems.PlayNext -> {
                /* 继续执行 */
            }
        }

        val currentState = _uiState.value
        val videoList = currentState.availableVideoList
        val currentCid = currentState.cid

        // 1. 查找当前视频在列表中的位置
        val videoListIndex = videoList.indexOfFirst { it.aid == currentState.aid }
        val currentVideoItem = videoList.getOrNull(videoListIndex)

        // 2. 预计算下一个播放项 (NextTarget)
        var nextTarget: NextPlayTarget? = null

        // 逻辑 A: 检查是否有下一个分 P (UGC Page)
        if (currentVideoItem?.ugcPages?.isNotEmpty() == true) {
            val currentInnerIndex = currentVideoItem.ugcPages.indexOfFirst { it.cid == currentCid }
            if (currentInnerIndex != -1 && currentInnerIndex + 1 < currentVideoItem.ugcPages.size) {
                val nextPage = currentVideoItem.ugcPages[currentInnerIndex + 1]
                nextTarget = NextPlayTarget.UgcPage(currentVideoItem, nextPage)
            }
        }

        // 逻辑 B: 如果没有分 P，检查是否有下一个视频
        if (nextTarget == null && videoListIndex + 1 < videoList.size) {
            val nextVideo = videoList[videoListIndex + 1]
            nextTarget = NextPlayTarget.VideoItem(nextVideo)
        }

        // 3. 根据查找结果执行操作
        if (nextTarget != null) {
            startNextEpisodeCountdown(nextTarget)
        } else {
            // 没有下一集了，发送事件关闭页面
            viewModelScope.launch {
                _uiEffect.emit(PlayerUiEffect.FinishActivity)
            }
        }
    }

    fun cancelPlayNext() {
        playNextCountdownJob?.cancel()
        _uiState.update { it.copy(showSkipToNextEp = false) }
    }

    fun backToStart() {
        backToStartCountdownJob?.cancel()
        _uiState.update { it.copy(showBackToStart = false) }

        videoPlayer?.seekTo(0)
        seekDanmakuHost(0L)
    }

    /**
     * 开始周期性更新播放进度
     */
    fun startSeekerUpdater() {
        // 防止重复启动
        if (seekerUpdateJob?.isActive == true) return

        seekerUpdateJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                updateSeekerState()
                delay(100)
            }
        }
    }

    private fun setBufferingVisible(visible: Boolean) {
        _uiState.update { state ->
            if (state.isBuffering == visible) state else state.copy(isBuffering = visible)
        }
    }

    private fun forceShowBufferingTip() {
        isRawBuffering = true
        bufferingShowJob?.cancel()
        bufferingHideJob?.cancel()
        bufferingVisibleSinceMs = System.currentTimeMillis()
        setBufferingVisible(true)
    }

    private fun onBufferingStateChanged(buffering: Boolean) {
        isRawBuffering = buffering
        if (buffering) {
            bufferingHideJob?.cancel()
            if (_uiState.value.isBuffering) return
            bufferingShowJob?.cancel()
            bufferingShowJob = viewModelScope.launch {
                delay(bufferingShowDelayMs)
                if (!isRawBuffering) return@launch
                bufferingVisibleSinceMs = System.currentTimeMillis()
                setBufferingVisible(true)
            }
            return
        }

        bufferingShowJob?.cancel()
        if (!_uiState.value.isBuffering) return

        bufferingHideJob?.cancel()
        bufferingHideJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            val remainMs = (bufferingVisibleSinceMs + bufferingMinShowMs - now).coerceAtLeast(0L)
            if (remainMs > 0L) {
                delay(remainMs)
            }
            if (!isRawBuffering) {
                setBufferingVisible(false)
            }
        }
    }

    fun seekToTime(time: Long) {
        videoPlayer?.seekTo(time)
        _seekerState.update { it.copy(currentTime = time) }
        seekDanmakuHost(time)
    }

    fun playNewVideo(
        newVideo: VideoListItem,
        partTitleOverride: String? = null
    ) {
        videoPlayer?.pause()

        val state = _uiState.value

        val shouldUpdateVideoDetail = state.aid != newVideo.aid
        val shouldUpdateVideoList = !state.availableVideoList.any { it.aid == newVideo.aid }

        // 切换视频时更新detail
        if (shouldUpdateVideoDetail) {
            viewModelScope.launch(Dispatchers.IO) {
                videoInfoRepository.loadVideoDetail(newVideo.aid, Prefs.apiType)
            }
        }

        // 新视频不在当前视频列表时更新列表
        if (shouldUpdateVideoList) {
            videoInfoRepository.updateVideoList(listOf(newVideo))
        }

        // 更新播放历史并上传
        syncProgress(viewModelScope)

        _uiState.update {
            it.copy(
                aid = newVideo.aid,
                cid = newVideo.cid,
                epid = newVideo.epid,
                seasonId = newVideo.seasonId ?: 0,
                title = newVideo.title,
                startupCover = StartupCoverRepository[newVideo.aid],
            hasRenderedFirstFrame = false,
            hasStartedPlayback = false
            )
        }
        forceShowBufferingTip()

        val immediatePartTitle = partTitleOverride
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: newVideo.ugcPages
                ?.firstOrNull { it.cid == newVideo.cid }
                ?.title
                ?.trim()
                .orEmpty()
                .ifBlank { newVideo.title }

        viewModelScope.launch {
            resetDanmakuForVideoSwitch()
            loadPlayUrl(
                avid = newVideo.aid,
                cid = newVideo.cid,
                epid = newVideo.epid,
                seasonId = newVideo.seasonId,
                title = newVideo.title,
                partTitle = immediatePartTitle
            )
        }
    }

    private suspend fun resetDanmakuForVideoSwitch() {
        _danmakuHostCommands.emit(DanmakuHostCommand.Clear(reason = "video_switch"))
        syncDanmakuHostStateFromPlayerState()
    }

    fun trySendHeartbeat() {
        if (Prefs.incognitoMode) return
        syncProgress(scope = viewModelScope, updateLocal = false)
    }

    fun ensureUgcPagesLoaded(aid: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            videoInfoRepository.ensureUgcPagesLoaded(aid = aid, preferApiType = Prefs.apiType)
        }
    }

    private fun syncVideoListFromUgcSeasonIfNeeded(
        detailAid: Long,
        ugcSeason: dev.aaa1115910.biliapi.entity.video.season.UgcSeason?,
    ) {
        if (ugcSeason == null) return

        val playingAid = _uiState.value.aid
        if (playingAid == 0L) return
        if (detailAid != playingAid) return

        val sections = ugcSeason.sections
        if (sections.isEmpty()) return

        val targetSection = sections.firstOrNull { section ->
            section.episodes.any { ep -> ep.aid == playingAid }
        } ?: return

        val oldByAid = _uiState.value.availableVideoList.associateBy { it.aid }
        val seasonIdOrNull = _uiState.value.seasonId.takeIf { it != 0 }

        val newList = targetSection.episodes.map { ep ->
            oldByAid[ep.aid]?.copy(
                aid = ep.aid,
                cid = ep.cid,
                epid = ep.epid,
                seasonId = seasonIdOrNull,
                title = ep.title,
            ) ?: VideoListItem(
                aid = ep.aid,
                cid = ep.cid,
                epid = ep.epid,
                seasonId = seasonIdOrNull,
                title = ep.title,
            )
        }

        val current = _uiState.value.availableVideoList
        val sameAidsInOrder =
            current.size == newList.size && current.map { it.aid } == newList.map { it.aid }
        if (sameAidsInOrder) return

        videoInfoRepository.updateVideoList(newList)
    }

    private fun computeAidsHash(list: List<VideoListItem>): Int {
        var hash = 1
        list.forEach { item ->
            hash = 31 * hash + item.aid.hashCode()
        }
        return hash
    }

    private fun restartUgcPagesPrefetchIfNeeded(videoList: List<VideoListItem>) {
        val state = _uiState.value

        if (state.epid != null) return

        val centerAid = state.aid
        if (centerAid <= 0L) return
        if (videoList.isEmpty()) return

        val aidsHash = computeAidsHash(videoList)
        val shouldRestart = centerAid != lastPrefetchCenterAid || aidsHash != lastPrefetchAidsHash

        if (!shouldRestart && ugcPagesPrefetchJob?.isActive == true) return

        lastPrefetchCenterAid = centerAid
        lastPrefetchAidsHash = aidsHash

        ugcPagesPrefetchJob?.cancel()
        ugcPagesPrefetchJob = viewModelScope.launch(Dispatchers.IO) {
            val centerIndex = videoList.indexOfFirst { it.aid == centerAid }
            if (centerIndex == -1) return@launch

            suspend fun prefetchItem(item: VideoListItem) {
                if (item.epid != null) return

                val delayMs = runCatching {
                    videoInfoRepository.ensureUgcPagesLoaded(
                        aid = item.aid,
                        preferApiType = Prefs.apiType
                    )
                }.fold(
                    onSuccess = { ugcPagesPrefetchDelayMs },
                    onFailure = { throwable ->
                        if (throwable is CancellationException) throw throwable
                        logger.fWarn {
                            "UGC pages prefetch failed: aid=${item.aid}, error=${throwable.stackTraceToString()}"
                        }
                        ugcPagesPrefetchFailureDelayMs
                    }
                )
                delay(delayMs)
            }

            prefetchItem(videoList[centerIndex])

            val lastIndex = videoList.lastIndex
            val maxDelta = maxOf(centerIndex, lastIndex - centerIndex)
            for (delta in 1..maxDelta) {
                val upIndex = centerIndex - delta
                if (upIndex >= 0) {
                    prefetchItem(videoList[upIndex])
                }

                val downIndex = centerIndex + delta
                if (downIndex <= lastIndex) {
                    prefetchItem(videoList[downIndex])
                }
            }
        }
    }

    private fun loadPlayUrl(
        avid: Long,
        cid: Long,
        epid: Int? = null,
        seasonId: Int? = null,
        title: String = _uiState.value.title,
        partTitle: String? = null,
        preferApi: ApiType = Prefs.apiType,
        preferredQualityId: Int? = null,
        isAutoApp302Fallback: Boolean = false,
    ) {
        val normalizedEpid = epid?.takeIf { it != 0 }
        val generation = loadGeneration.incrementAndGet()
        activeLoadGeneration = generation

        if (!isAutoApp302Fallback) {
            app302FallbackTried = false
        }

        if (preferApi != Prefs.apiType || preferredQualityId != null) {
            oneShotApiOverride = OneShotApiOverride(
                aid = avid,
                cid = cid,
                apiType = preferApi,
                preferredQualityId = preferredQualityId,
                generation = generation
            )
        }

        val isSwitching =
            avid != _uiState.value.aid || cid != _uiState.value.cid || normalizedEpid != _uiState.value.epid
        val resolvedPartTitle = when {
            partTitle != null -> partTitle
            isSwitching -> title
            else -> _uiState.value.partTitle
        }

        previewTipCountdownJob?.cancel()

        _uiState.update {
            it.copy(
                aid = avid,
                cid = cid,
                epid = normalizedEpid,
                seasonId = seasonId ?: 0,
                title = title,
                partTitle = resolvedPartTitle,
                showPreviewTip = false
            )
        }
        syncDanmakuHostStateFromPlayerState()

        restartUgcPagesPrefetchIfNeeded(_uiState.value.availableVideoList)

        loadPlayUrlJob?.cancel()
        loadPlayUrlJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val playUrlDeferred = async {
                    loadPlayUrlImpl(
                        avid = avid,
                        cid = cid,
                        epid = normalizedEpid ?: 0,
                        preferApi = preferApi,
                        proxyArea = _uiState.value.proxyArea,
                        generation = generation
                    )
                }

                launch {
                    val danmakuEnabled = _uiState.value.danmakuState.danmakuEnabled
                    if (danmakuEnabled) {
                        if (_uiState.value.danmakuState.maskEnabled) {
                            updateDanmakuMask(
                                aid = avid,
                                cid = cid,
                                expectedEpid = normalizedEpid,
                                generation = generation
                            )
                        } else {
                            _uiState.update { it.copy(danmakuMask = null) }
                            syncDanmakuHostStateFromPlayerState()
                        }
                    } else {
                        _uiState.update { it.copy(danmakuMask = null) }
                        syncDanmakuHostStateFromPlayerState()
                    }
                }


                launch {
                    updateSubtitle(
                        aid = avid,
                        cid = cid,
                        expectedEpid = normalizedEpid,
                        generation = generation
                    )

                    if (!isCurrentLoadRequest(generation, avid, cid, normalizedEpid)) return@launch

                    val pending = continuePlayPending

                    if (pending != null &&
                        pending.aid == avid &&
                        pending.cid == cid &&
                        Prefs.continuePlayAutoSubtitleEnabled
                    ) {
                        continuePlayPending = null
                        val selection = pending.selection
                        if (selection != null) {
                            val targetSubtitle = pickContinuePlaySubtitle(
                                selection,
                                _uiState.value.availableSubtitles
                            )
                            if (targetSubtitle != null) {
                                logger.info {
                                    "Continue play auto subtitle: ${targetSubtitle.langDoc} (id=${targetSubtitle.id})"
                                }
                                loadSubtitle(
                                    id = targetSubtitle.id,
                                    expectedAid = avid,
                                    expectedCid = cid,
                                    expectedGeneration = generation
                                )
                            } else {
                                logger.info {
                                    "Continue play auto subtitle: no match for ${selection.type}|${selection.langKey}, skip"
                                }
                            }
                        } else {
                            logger.info { "Continue play auto subtitle: last episode subtitle is OFF, keep OFF" }
                        }
                    } else {
                        if (pending != null &&
                            (pending.aid != avid || pending.cid != cid || !Prefs.continuePlayAutoSubtitleEnabled)
                        ) {
                            continuePlayPending = null
                        }

                        val targetSubtitle = pickNormalAutoSubtitle(_uiState.value.availableSubtitles)
                        if (targetSubtitle != null) {
                            logger.info {
                                "Normal auto subtitle: ${targetSubtitle.langDoc} (id=${targetSubtitle.id})"
                            }
                            loadSubtitle(
                                id = targetSubtitle.id,
                                expectedAid = avid,
                                expectedCid = cid,
                                expectedGeneration = generation
                            )
                        }
                    }
                }

                launch {
                    updateVideoShot(
                        aid = avid,
                        cid = cid,
                        expectedEpid = normalizedEpid,
                        generation = generation
                    )
                }

                launch {
                    updateVideoPages(
                        aid = avid,
                        expectedEpid = normalizedEpid,
                        generation = generation
                    )
                }

                val loaded = playUrlDeferred.await()
                if (loaded) {
                    logger.info { "Play URL loaded, start playback now" }
                } else {
                    logger.warn { "Play URL load skipped or failed: aid=$avid, cid=$cid, generation=$generation" }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Loading video data error: $e" }
            }
        }
    }

    private suspend fun loadPlayUrlImpl(
        avid: Long,
        cid: Long,
        epid: Int = 0,
        preferApi: ApiType = Prefs.apiType,
        proxyArea: ProxyArea = ProxyArea.MainLand,
        generation: Long
    ): Boolean {
        val matchedOverride = oneShotApiOverride?.takeIf {
            it.aid == avid && it.cid == cid && it.generation == generation
        }
        if (matchedOverride != null) {
            oneShotApiOverride = null
        }

        val effectiveApi = matchedOverride?.apiType ?: preferApi
        val effectivePreferredQualityId = matchedOverride?.preferredQualityId

        logger.fInfo {
            "Load play url:[av=$avid, cid=$cid, preferApi=$preferApi, effectiveApi=$effectiveApi, proxyArea=$proxyArea, generation=$generation]"
        }

        return try {
            val localPlayData = fetchPlayData(
                avid = avid,
                cid = cid,
                epid = epid,
                preferApi = effectiveApi,
                proxyArea = proxyArea
            )

            if (!isCurrentLoadRequest(generation, avid, cid, epid.takeIf { it != 0 })) {
                logger.fInfo { "Discard stale playData: aid=$avid, cid=$cid, generation=$generation" }
                return false
            }

            logger.fInfo { "Load play data response success" }
            logger.info { "Play data: $localPlayData" }

            val resolutionMap = localPlayData.dashVideos
                .distinctBy { it.quality }
                .associate { video ->
                    video.quality to Resolution.fromCode(video.quality).getShortDisplayName(BVApp.context)
                }
            logger.fInfo { "Video available resolution: $resolutionMap" }

            // 解析并去重可用的音质 (使用 buildList 和 distinct 替代 forEach + mutableList)
            val availableAudioList = buildList {
                addAll(localPlayData.dashAudios.map { Audio.fromCode(it.codecId) })
                localPlayData.dolby?.let { add(Audio.fromCode(it.codecId)) }
                localPlayData.flac?.let { add(Audio.fromCode(it.codecId)) }
            }.distinct()

            logger.fInfo { "Video available audio: $availableAudioList" }

            val targetQualityId = calculateTargetQuality(
                availableQualities = resolutionMap.keys,
                defaultQualityCode = effectivePreferredQualityId ?: Prefs.defaultQuality.code
            )
            val targetAudio = calculateTargetAudio(
                availableAudio = availableAudioList,
                defaultAudio = Prefs.defaultAudio
            )

            // 统一批量更新 UI State (避免多次触发重组)
            _uiState.update {
                it.copy(
                    availableQuality = resolutionMap,
                    availableAudio = availableAudioList,
                    mediaProfileState = it.mediaProfileState.copy(
                        qualityId = targetQualityId,
                        audio = targetAudio
                    )
                )
            }

            val targetCodec = getTargetVideoCodec(
                playData = localPlayData,
                apiType = effectiveApi
            )

            if (!isCurrentLoadRequest(generation, avid, cid, epid.takeIf { it != 0 })) {
                logger.fInfo { "Skip stale playQuality: aid=$avid, cid=$cid, generation=$generation" }
                return false
            }

            if (localPlayData.needPay) {
                startShowPreviewTipCountdown()
            }

            withContext(Dispatchers.Main) {
                if (!isCurrentLoadRequest(generation, avid, cid, epid.takeIf { it != 0 })) {
                    return@withContext
                }

                playData = localPlayData
                currentPlayApiType = effectiveApi

                playQuality(
                    playData = localPlayData,
                    apiType = effectiveApi,
                    qn = targetQualityId,
                    codec = targetCodec,
                    audio = targetAudio,
                )
                videoPlayer?.start()
            }

            logger.fInfo { "Load play url success" }
            true
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable

            if (tryAutoSwitchToWebForApp302(throwable)) {
                return false
            }

            if (isCurrentLoadRequest(generation, avid, cid, epid.takeIf { it != 0 })) {
                _uiState.update {
                    it.copy(
                        playerState = PlayerState.Error(throwable.message ?: "Unknown error"),
                    )
                }
            }
            logger.fException(throwable) { "Load video failed" }
            false
        }
    }

    private suspend fun fetchPlayData(
        avid: Long,
        cid: Long,
        epid: Int,
        preferApi: ApiType,
        proxyArea: ProxyArea
    ): PlayData {
        return if (_uiState.value.fromSeason) {
            videoPlayRepository.getPgcPlayData(
                aid = avid,
                cid = cid,
                epid = epid,
                preferCodec = Prefs.defaultVideoCodec.toBiliApiCodeType(),
                preferApiType = preferApi,
                enableProxy = Prefs.enableProxy,
                proxyArea = when (proxyArea) {
                    ProxyArea.MainLand -> ""
                    ProxyArea.HongKong -> "hk"
                    ProxyArea.TaiWan -> "tw"
                }
            )
        } else {
            videoPlayRepository.getPlayData(
                aid = avid,
                cid = cid,
                preferApiType = preferApi
            )
        }
    }

    private fun calculateTargetQuality(
        availableQualities: Set<Int>,
        defaultQualityCode: Int
    ): Int {
        if (availableQualities.contains(defaultQualityCode)) return defaultQualityCode

        val sortedQualities = availableQualities.sorted()
        return sortedQualities.findLast { it <= defaultQualityCode }
            ?: sortedQualities.firstOrNull()
            ?: 0
    }

    private fun calculateTargetAudio(
        availableAudio: List<Audio>,
        defaultAudio: Audio
    ): Audio {
        if (availableAudio.contains(defaultAudio)) return defaultAudio

        // Fallback 逻辑
        return when {
            defaultAudio == Audio.ADolbyAtoms && availableAudio.contains(Audio.AHiRes) -> Audio.AHiRes
            defaultAudio == Audio.AHiRes && availableAudio.contains(Audio.ADolbyAtoms) -> Audio.ADolbyAtoms
            availableAudio.contains(Audio.A192K) -> Audio.A192K
            availableAudio.contains(Audio.A132K) -> Audio.A132K
            availableAudio.contains(Audio.A64K) -> Audio.A64K
            else -> availableAudio.firstOrNull() ?: Audio.A132K
        }
    }

    private suspend fun updateVideoPages(
        aid: Long,
        expectedEpid: Int?,
        generation: Long
    ) {
        if (!isCurrentLoadRequest(generation, aid, _uiState.value.cid, expectedEpid)) return

        val state = _uiState.value
        val currentItem = state.availableVideoList.firstOrNull { it.aid == aid }
        if (currentItem?.epid != null || expectedEpid != null || state.epid != null) return

        videoInfoRepository.ensureUgcPagesLoaded(
            aid = aid,
            preferApiType = Prefs.apiType
        )
    }

    private fun getTargetVideoCodec(
        playData: PlayData,
        apiType: ApiType
    ): VideoCodec {
        val state = _uiState.value

        if (apiType == ApiType.App && playData.codec.isEmpty()) {
            val videoItem = playData.dashVideos
                .find { it.quality == state.mediaProfileState.qualityId }
                ?: playData.dashVideos.firstOrNull()

            val codec = videoItem?.let { VideoCodec.fromCodecId(it.codecId) } ?: Prefs.defaultVideoCodec

            _uiState.update {
                it.copy(
                    availableVideoCodec = listOf(codec),
                    mediaProfileState = it.mediaProfileState.copy(videoCodec = codec)
                )
            }
            return codec
        }

        val codecList = playData.codec[state.mediaProfileState.qualityId]
            ?.mapNotNull { VideoCodec.fromCodecString(it) }
            ?.takeIf { it.isNotEmpty() }

        if (codecList.isNullOrEmpty()) {
            val fallbackItem = playData.dashVideos
                .find { it.quality == state.mediaProfileState.qualityId }
                ?: playData.dashVideos.firstOrNull()
            val fallbackCodec =
                fallbackItem?.let { VideoCodec.fromCodecId(it.codecId) } ?: Prefs.defaultVideoCodec

            _uiState.update {
                it.copy(
                    availableVideoCodec = listOf(fallbackCodec),
                    mediaProfileState = it.mediaProfileState.copy(videoCodec = fallbackCodec)
                )
            }
            logger.fWarn { "Codec map missing for quality=${state.mediaProfileState.qualityId}, fallback to $fallbackCodec" }
            return fallbackCodec
        }

        val targetVideoCodec = if (codecList.contains(Prefs.defaultVideoCodec)) {
            Prefs.defaultVideoCodec
        } else {
            codecList.minByOrNull { it.ordinal } ?: Prefs.defaultVideoCodec
        }

        _uiState.update {
            it.copy(
                availableVideoCodec = codecList,
                mediaProfileState = it.mediaProfileState.copy(videoCodec = targetVideoCodec)
            )
        }
        logger.fInfo { "Select codec: $targetVideoCodec" }
        return targetVideoCodec
    }

    private fun playQuality(
        playData: PlayData,
        apiType: ApiType,
        qn: Int? = null,
        codec: VideoCodec? = null,
        audio: Audio? = null
    ) {
        val player = videoPlayer ?: return
        val state = _uiState.value

        val targetQn = qn ?: state.mediaProfileState.qualityId
        val targetCodec = codec ?: state.mediaProfileState.videoCodec
        val targetAudio = audio ?: state.mediaProfileState.audio

        logger.fInfo {
            "Video quality：${state.availableQuality[targetQn]}, video encoding：${
                targetCodec.getDisplayName(BVApp.context)
            }"
        }

        val foundVideoItem = playData.dashVideos.find {
            when (apiType) {
                ApiType.Web -> it.quality == targetQn && it.codecs?.startsWith(targetCodec.prefix) == true
                ApiType.App -> {
                    if (playData.codec.isEmpty()) it.quality == targetQn
                    else it.quality == targetQn && it.codecs?.startsWith(targetCodec.prefix) == true
                }
            }
        }

        val actualVideoItem = foundVideoItem ?: playData.dashVideos.firstOrNull() ?: run {
            _uiState.update { it.copy(playerState = PlayerState.Error("视频源不可用")) }
            return
        }

        var videoUrl = actualVideoItem.baseUrl
        val videoUrls = mutableListOf<String?>()
        videoUrls.add(actualVideoItem.baseUrl)
        videoUrls.addAll(actualVideoItem.backUrl)

        val audioItem = playData.dashAudios.find { it.codecId == targetAudio.code }
            ?: playData.dolby.takeIf { it?.codecId == targetAudio.code }
            ?: playData.flac.takeIf { it?.codecId == targetAudio.code }
            ?: playData.dashAudios.minByOrNull { it.codecId }

        var audioUrl: String? = audioItem?.baseUrl
        val audioUrls = mutableListOf<String>()
        audioItem?.baseUrl?.let { audioUrls.add(it) }
        audioUrls.addAll(audioItem?.backUrl ?: emptyList())

        logger.fInfo { "all video hosts: ${videoUrls.mapNotNull { it?.let { u -> with(URI(u)) { "$scheme://$authority" } } }}" }
        logger.fInfo { "all audio hosts: ${audioUrls.map { with(URI(it)) { "$scheme://$authority" } }}" }

        //replace cdn
        if (Prefs.enableProxy && state.proxyArea != ProxyArea.MainLand) {
            videoUrl = videoUrl.replaceUrlDomainWithAliCdn()
            audioUrl = audioUrl?.replaceUrlDomainWithAliCdn()
        } else {
            // 如果未通过网络代理获得播放地址，才判断是否应该替换为官方 cdn
            videoUrl = selectOfficialCdnUrl(videoUrls.filterNotNull())
            audioUrl = if (audioUrls.isNotEmpty()) selectOfficialCdnUrl(audioUrls) else null
        }

        logger.fInfo {
            "Audio encoding：${
                (Audio.fromCode(audioItem?.codecId ?: 0)).getDisplayName(BVApp.context)
            }"
        }

        logger.info { "Video url: $videoUrl" }
        logger.info { "Audio url: $audioUrl" }

        // 必须发生在 prepare 之前
        applyVideoTransformToPlayer()

        val playbackHeaders = buildPlaybackHeaders(apiType)
        player.setHeader(playbackHeaders)
        logger.fInfo {
            "Apply playback headers for apiType=$apiType, referer=${playbackHeaders.containsKey("referer")}"
        }
        player.playUrl(videoUrl, audioUrl)
        player.prepare()

        _uiState.update {
            it.copy(
                videoHeight = actualVideoItem.height,
                videoWidth = actualVideoItem.width
            )
        }
    }

    private suspend fun updateSubtitle(
        aid: Long,
        cid: Long,
        expectedEpid: Int?,
        generation: Long
    ) {
        if (!isCurrentLoadRequest(generation, aid, cid, expectedEpid)) return

        runCatching {
            val subtitleData = videoPlayRepository.getSubtitle(
                aid = aid,
                cid = cid,
                preferApiType = Prefs.apiType
            )

            if (!isCurrentLoadRequest(generation, aid, cid, expectedEpid)) return@runCatching

            _uiState.update { currentState ->
                val newSubtitles: List<Subtitle> = buildList {
                    add(
                        Subtitle(
                            id = -1,
                            lang = "",
                            langDoc = "关闭",
                            url = "",
                            type = SubtitleType.CC,
                            aiType = SubtitleAiType.Normal,
                            aiStatus = SubtitleAiStatus.None
                        )
                    )
                    addAll(subtitleData)
                    sortBy { it.id }
                }
                currentState.copy(
                    subtitleId = -1,
                    subtitleData = emptyList(),
                    availableSubtitles = newSubtitles
                )
            }

            logger.fInfo { "Update subtitle size: ${subtitleData.size}" }
        }.onFailure {
            if (it is CancellationException) throw it
            logger.fWarn { "Update subtitle failed: ${it.stackTraceToString()}" }
        }
    }

    private fun Subtitle.normalizedLangKey(): String {
        val raw = (lang.ifBlank { langDoc }).trim()
        if (raw.isEmpty()) return ""

        val noAiPrefix = if (raw.startsWith("ai-", ignoreCase = true)) raw.substring(3) else raw
        val primary = noAiPrefix.substringBefore("-")
        return primary.lowercase()
    }

    private fun pickContinuePlaySubtitle(
        selection: ContinueSelection,
        tracks: List<Subtitle>
    ): Subtitle? {
        val list = tracks.filter { it.id != -1L }

        fun find(type: SubtitleType): Subtitle? =
            list.firstOrNull { it.type == type && it.normalizedLangKey() == selection.langKey }

        return if (selection.type == SubtitleType.AI) {
            find(SubtitleType.AI) ?: find(SubtitleType.CC)
        } else {
            find(SubtitleType.CC)
        }
    }

    private data class LangTracks(
        var cc: Subtitle? = null,
        var ai: Subtitle? = null
    )

    private fun pickNormalAutoSubtitle(tracks: List<Subtitle>): Subtitle? {
        val ruleTokens = Prefs.autoSubtitleRuleTokens.toSet()
        if (ruleTokens.isEmpty()) return null

        val byLang = linkedMapOf<String, LangTracks>()
        tracks.filter { it.id != -1L }.forEach { t ->
            val key = t.normalizedLangKey()
            if (key.isBlank()) return@forEach

            val entry = byLang.getOrPut(key) { LangTracks() }
            when (t.type) {
                SubtitleType.CC -> if (entry.cc == null) entry.cc = t
                SubtitleType.AI -> if (entry.ai == null) entry.ai = t
            }
        }

        fun langSortKey(langKey: String): Pair<Int, String> = when (langKey) {
            "zh" -> 0 to ""
            "en" -> 1 to ""
            else -> 2 to langKey
        }

        val orderedLangKeys = byLang.keys.sortedWith(
            compareBy({ langSortKey(it).first }, { langSortKey(it).second })
        )

        orderedLangKeys.forEach { langKey ->
            val entry = byLang[langKey] ?: return@forEach
            if (entry.cc != null && ruleTokens.contains("CC|$langKey")) {
                return entry.cc
            }
            if (entry.ai != null && ruleTokens.contains("AI|$langKey")) {
                return entry.ai
            }
        }

        return null
    }

    private fun syncProgress(
        scope: CoroutineScope,
        updateLocal: Boolean = true,
        isDetaching: Boolean = false
    ) {
        val player = videoPlayer ?: return
        val state = _uiState.value

        val currentTime = (player.currentPosition.coerceAtLeast(0) / 1000).toInt()
        val totalTime = (player.duration.coerceAtLeast(0) / 1000).toInt()
        val reportTime = if (currentTime >= totalTime) -1 else currentTime

        if (updateLocal) {
            videoInfoRepository.updateHistory(
                progress = reportTime,
                lastPlayedCid = state.cid
            )
        }

        if (!Prefs.incognitoMode) {
            heartbeatJob?.cancel()
            heartbeatJob = scope.launch(Dispatchers.IO) {
                try {
                    if (isDetaching) {
                        withTimeout(3000L) { uploadHistory(state, reportTime) }
                    } else {
                        uploadHistory(state, reportTime)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn { "Failed to upload history: $e" }
                }
            }
        }
    }

    private suspend fun uploadHistory(uiState: PlayerUiState, time: Int) {
        try {
            with(uiState) {
                val currentApiType = Prefs.apiType

                if (!fromSeason) {
                    logger.info { "Send heartbeat:[avid=$aid, cid=$cid, time=$time]" }
                    videoPlayRepository.sendHeartbeat(
                        aid = aid,
                        cid = cid,
                        time = time,
                        preferApiType = currentApiType
                    )
                } else {
                    logger.info { "Send heartbeat:[avid=$aid, cid=$cid, epid=$epid, sid=$seasonId, time=$time]" }
                    videoPlayRepository.sendHeartbeat(
                        aid = aid,
                        cid = cid,
                        time = time,
                        type = HeartbeatVideoType.Season,
                        subType = subType,
                        epid = epid,
                        seasonId = seasonId,
                        preferApiType = currentApiType
                    )
                }
            }
            logger.info { "Send heartbeat success" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn { "Send heartbeat failed: ${e.stackTraceToString()}" }
        }
    }

    private suspend fun updateDanmakuMask(
        aid: Long,
        cid: Long,
        expectedEpid: Int?,
        generation: Long
    ) {
        if (!isCurrentLoadRequest(generation, aid, cid, expectedEpid)) return

        runCatching {
            val mask = videoPlayRepository.getDanmakuMask(
                aid = aid,
                cid = cid,
                preferApiType = Prefs.apiType
            )

            _uiState.update { it.copy(danmakuMask = mask) }
            syncDanmakuHostStateFromPlayerState()

            if (!isCurrentLoadRequest(generation, aid, cid, expectedEpid)) return@runCatching

            logger.fInfo { "Load danmaku mask segments: ${mask?.segmentCount ?: 0}" }
        }.onFailure {
            if (it is CancellationException) throw it
            logger.fWarn { "Load danmaku mask failed: ${it.stackTraceToString()}" }
        }
    }

    private suspend fun updateVideoShot(
        aid: Long,
        cid: Long,
        expectedEpid: Int?,
        generation: Long
    ) {
        if (!isCurrentLoadRequest(generation, aid, cid, expectedEpid)) return

        _uiState.update { it.copy(videoShot = null) }

        runCatching {
            val videoShot = videoPlayRepository.getVideoShot(
                aid = aid,
                cid = cid,
                preferApiType = Prefs.apiType
            )

            if (!isCurrentLoadRequest(generation, aid, cid, expectedEpid)) return@runCatching

            _uiState.update { it.copy(videoShot = videoShot) }
            logger.fInfo { "Load video shot success" }
        }.onFailure { err ->
            if (err is CancellationException) throw err
            logger.fWarn { "Load video shot failed: ${err.stackTraceToString()}" }
        }
    }

    private fun buildPlaybackHeaders(apiType: ApiType): Map<String, String> {
        return when (apiType) {
            ApiType.Web -> mapOf(
                "User-Agent" to BVApp.context.getString(R.string.video_player_user_agent_http),
                "referer" to BVApp.context.getString(R.string.video_player_referer)
            )

            ApiType.App -> mapOf(
                "User-Agent" to BVApp.context.getString(R.string.video_player_user_agent_client)
            )
        }
    }

    private fun startNextEpisodeCountdown(target: NextPlayTarget) {
        playNextCountdownJob?.cancel()

        playNextCountdownJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showSkipToNextEp = true,
                )
            }
            delay(1000)

            playNextTarget(target)
            _uiState.update { it.copy(showSkipToNextEp = false) }
        }
    }

    private fun startShowPreviewTipCountdown() {
        previewTipCountdownJob?.cancel()

        previewTipCountdownJob = viewModelScope.launch {
            _uiState.update { it.copy(showPreviewTip = true) }
            delay(5000)
            _uiState.update { it.copy(showPreviewTip = false) }
        }
    }

    private fun playNextTarget(target: NextPlayTarget) {
        fun prepareContinuePending(nextAid: Long, nextCid: Long) {
            if (!Prefs.continuePlayAutoSubtitleEnabled) {
                continuePlayPending = null
                return
            }

            val state = _uiState.value
            val currentSubtitleId = state.subtitleId

            val selection: ContinueSelection? = if (currentSubtitleId == -1L) {
                null
            } else {
                state.availableSubtitles
                    .firstOrNull { it.id == currentSubtitleId }
                    ?.let { track ->
                        ContinueSelection(
                            type = track.type,
                            langKey = track.normalizedLangKey()
                        )
                    }
            }

            continuePlayPending = ContinuePlayPending(
                aid = nextAid,
                cid = nextCid,
                selection = selection
            )
        }

        when (target) {
            is NextPlayTarget.UgcPage -> {
                logger.info { "Play next UGC page: ${target.page.title}" }

                _uiState.update { it.copy(partTitle = target.page.title) }

                prepareContinuePending(
                    nextAid = target.parentVideo.aid,
                    nextCid = target.page.cid
                )

                playNewVideo(
                    newVideo = VideoListItem(
                        aid = target.parentVideo.aid,
                        cid = target.page.cid,
                        title = target.title
                    ),
                    partTitleOverride = target.page.title
                )
            }

            is NextPlayTarget.VideoItem -> {
                logger.info { "Play next video item: ${target.video.title}" }

                prepareContinuePending(
                    nextAid = target.video.aid,
                    nextCid = target.video.cid
                )

                playNewVideo(
                    newVideo = VideoListItem(
                        aid = target.video.aid,
                        cid = target.video.cid,
                        title = target.title,
                        epid = target.video.epid,
                        seasonId = target.video.seasonId,
                    )
                )
            }
        }
    }

    private fun seekToLastPlayed() {
        val time = _uiState.value.lastPlayed
        logger.fInfo { "Back to history: ${time.formatHourMinSec()}" }

        videoPlayer?.seekTo(time)
        seekDanmakuHost(time)

        _uiState.update { it.copy(showBackToStart = true) }

        backToStartCountdownJob?.cancel()
        backToStartCountdownJob = viewModelScope.launch {
            delay(5000)
            _uiState.update { it.copy(showBackToStart = false) }
        }
    }

    private fun stopSeekerUpdater() {
        seekerUpdateJob?.cancel()
        seekerUpdateJob = null
    }

    private fun updateSeekerState() {
        val player = videoPlayer ?: return

        val currentPos = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.coerceAtLeast(0L)
        _seekerState.update {
            it.copy(
                totalDuration = duration,
                currentTime = currentPos,
                bufferedPercentage = player.bufferedPercentage,
                debugInfo = player.debugInfo
            )
        }
    }

    private fun startClockUpdater() {
        clockUpdateJob?.cancel()
        clockUpdateJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                updateClock()
                delay(1000)
            }
        }
    }

    private fun updateClock() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        _uiState.update { it.copy(clock = Pair(hour, minute)) }
    }

    private fun selectOfficialCdnUrl(urls: List<String>): String {
        if (!Prefs.preferOfficialCdn) {
            logger.fInfo { "doesn't need to filter official cdn url, select the first url" }
            return urls.first()
        }
        val filteredUrls = urls
            .filter { !it.contains(".mcdn.bilivideo.") }
            .filter { !it.contains(".szbdyd.com") }
            .filter {
                !Regex("^(https?://)?(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d{1,5})?)(/[a-zA-Z0-9_./-]*)?(\\?.*)?$")
                    .matches(it)
            }
        return if (filteredUrls.isEmpty()) {
            logger.fInfo { "doesn't find any official cdn url, select the first url" }
            urls.first()
        } else {
            logger.fInfo { "filtered official cdn urls: $filteredUrls" }
            filteredUrls.first()
        }
    }

    private fun String.replaceUrlDomainWithAliCdn(): String {
        val replaceDomainKeywords = listOf(
            "mirroraliov",
            "mirrorakam"
        )
        if (replaceDomainKeywords.none { this.contains(it) }) return this

        return this.toUri()
            .buildUpon()
            .authority("upos-sz-mirrorali.bilivideo.com")
            .build()
            .toString()
    }

    private fun isCurrentLoadRequest(
        generation: Long,
        aid: Long,
        cid: Long,
        expectedEpid: Int?
    ): Boolean {
        val normalizedEpid = expectedEpid?.takeIf { it != 0 }
        val state = _uiState.value
        return generation == activeLoadGeneration &&
                state.aid == aid &&
                state.cid == cid &&
                state.epid == normalizedEpid
    }

    private fun isSubtitleRequestCurrent(
        expectedAid: Long,
        expectedCid: Long,
        expectedGeneration: Long?
    ): Boolean {
        val state = _uiState.value
        if (state.aid != expectedAid || state.cid != expectedCid) return false
        if (expectedGeneration != null && expectedGeneration != activeLoadGeneration) return false
        return true
    }

    private fun isSubtitleOrDanmakuRequestCurrent(
        expectedAid: Long,
        expectedCid: Long,
        expectedGeneration: Long
    ): Boolean {
        val state = _uiState.value
        return expectedGeneration == activeLoadGeneration &&
                state.aid == expectedAid &&
                state.cid == expectedCid
    }

    private fun isHttp302Error(error: Throwable): Boolean {
        val responseCode = (error as? ResponseException)?.response?.status?.value
        if (responseCode == 302) return true

        val msg = error.message.orEmpty()
        val stack = error.stackTraceToString()
        val has302 = Regex("""\b302\b""").containsMatchIn("$msg\n$stack")
        if (!has302) return false

        return msg.contains("http", ignoreCase = true) ||
                msg.contains("status", ignoreCase = true) ||
                msg.contains("response", ignoreCase = true) ||
                stack.contains("HTTP/1.1 302", ignoreCase = true) ||
                stack.contains("status: 302", ignoreCase = true) ||
                stack.contains("Response code: 302", ignoreCase = true)
    }

    private fun tryAutoSwitchToWebForApp302(error: Throwable): Boolean {
        if (currentPlayApiType != ApiType.App) return false
        if (app302FallbackTried) return false
        if (!isHttp302Error(error)) return false

        app302FallbackTried = true

        val state = _uiState.value
        val fallbackQualityId = state.mediaProfileState.qualityId.takeIf { it > 0 }

        logger.fWarn {
            "App chain hit HTTP 302, fallback once to Web, qualityId=$fallbackQualityId"
        }

        loadPlayUrl(
            avid = state.aid,
            cid = state.cid,
            epid = state.epid,
            seasonId = state.seasonId.takeIf { it != 0 },
            title = state.title,
            partTitle = state.partTitle,
            preferApi = ApiType.Web,
            preferredQualityId = fallbackQualityId,
            isAutoApp302Fallback = true
        )
        return true
    }

    private sealed interface NextPlayTarget {
        val title: String

        data class UgcPage(val parentVideo: VideoListItem, val page: VideoPage) : NextPlayTarget {
            override val title: String = parentVideo.title
        }

        data class VideoItem(val video: VideoListItem) : NextPlayTarget {
            override val title: String = video.title
        }
    }

    override fun onCleared() {
        super.onCleared()

        loadPlayUrlJob?.cancel()
        subtitleLoadJob?.cancel()
        seekerUpdateJob?.cancel()
        clockUpdateJob?.cancel()
        heartbeatJob?.cancel()
        playNextCountdownJob?.cancel()
        backToStartCountdownJob?.cancel()
        previewTipCountdownJob?.cancel()
        ugcPagesPrefetchJob?.cancel()
        bufferingShowJob?.cancel()
        bufferingHideJob?.cancel()

        runCatching { subtitleHttpClient.close() }

        if (!Prefs.incognitoMode) {
            val player = videoPlayer
            if (player != null) {
                val currentTimeSeconds = (player.currentPosition.coerceAtLeast(0) / 1000).toInt()
                val totalTimeSeconds = (player.duration.coerceAtLeast(0) / 1000).toInt()
                val reportTime = if (currentTimeSeconds >= totalTimeSeconds) -1 else currentTimeSeconds
                val stateSnapshot = _uiState.value

                runBlocking {
                    withContext(Dispatchers.IO) {
                        uploadHistory(stateSnapshot, reportTime)
                    }
                }
            }
        }
    }
}

sealed interface DanmakuSettingAction {
    data class SetScale(val value: Float) : DanmakuSettingAction
    data class SetOpacity(val value: Float) : DanmakuSettingAction
    data class SetArea(val value: Float) : DanmakuSettingAction
    data class SetRollingDurationFactor(val value: Float) : DanmakuSettingAction
    data class SetVodFilterLevel(val value: Int) : DanmakuSettingAction
    data class SetLiveFilterLevel(val value: Int) : DanmakuSettingAction
    data class SetColorful(val value: Boolean) : DanmakuSettingAction
    data class SetMaskEnabled(val enabled: Boolean) : DanmakuSettingAction
    data class SetEnabledTypes(val types: List<DanmakuType>) : DanmakuSettingAction
    data class SetDanmakuEnabled(val enabled: Boolean) : DanmakuSettingAction
}

data class DanmakuHostViewModelState(
    val sourceMode: DanmakuSourceMode = DanmakuSourceMode.None,
    val config: DanmakuConfig = DanmakuConfig(),
    val filterRule: DanmakuFilterRule = DanmakuFilterRule(),
    val maskEnabled: Boolean = false,
    val mask: DanmakuMask? = null,
    val aid: Long = 0L,
    val cid: Long = 0L,
    val currentPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1f,
    val live: DanmakuLiveInterfaceState = DanmakuLiveInterfaceState(),
)

data class DanmakuLiveInterfaceState(
    val roomId: Long = 0L,
    val enabled: Boolean = false,
    val sessionId: String = "",
    val state: String = "idle",
    val errorMessage: String? = null,
)

sealed interface DanmakuHostCommand {
    data class Seek(
        val positionMs: Long,
        val forceFetch: Boolean,
        val reason: String,
    ) : DanmakuHostCommand

    data class RefreshFromPosition(
        val positionMs: Long,
        val forceFetch: Boolean,
        val reason: String,
    ) : DanmakuHostCommand

    data class Clear(
        val reason: String,
    ) : DanmakuHostCommand
}

private fun DanmakuState.toDanmakuConfig(bottomPaddingPx: Int): DanmakuConfig {
    return DanmakuConfig(
        enabled = danmakuEnabled,
        opacity = opacity.coerceIn(0f, 1f),
        textSizeScale = (scale * 100).toInt().coerceAtLeast(1),
        durationMultiplier = 2f - rollingDurationFactor.coerceIn(0.2f, 1.8f),
        minLevel = vodFilterLevel.coerceAtLeast(0),
        vodMinLevel = vodFilterLevel.coerceIn(0, 10),
        liveMinLevel = liveFilterLevel.coerceIn(0, 60),
        colorful = colorful,
        bottomPaddingPx = bottomPaddingPx.coerceAtLeast(0),
        area = area.coerceIn(0f, 1f),
        allowScroll = enabledTypes.contains(DanmakuType.All) || enabledTypes.contains(DanmakuType.Rolling),
        allowTop = enabledTypes.contains(DanmakuType.All) || enabledTypes.contains(DanmakuType.Top),
        allowBottom = enabledTypes.contains(DanmakuType.All) || enabledTypes.contains(DanmakuType.Bottom),
    )
}

sealed interface SubtitleSettingAction {
    data class SetFontSize(val value: TextUnit) : SubtitleSettingAction
    data class SetOpacity(val value: Float) : SubtitleSettingAction
    data class SetBottomPadding(val value: Dp) : SubtitleSettingAction
}

sealed interface MediaProfileSettingAction {
    data class SetQuality(val value: Int) : MediaProfileSettingAction
    data class SetVideoCodec(val value: VideoCodec) : MediaProfileSettingAction
    data class SetAudio(val value: Audio) : MediaProfileSettingAction
}
