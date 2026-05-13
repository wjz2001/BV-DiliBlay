package dev.aaa1115910.bv.player.impl.exo

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaLoadData
import dev.aaa1115910.bv.player.AbstractVideoPlayer
import dev.aaa1115910.bv.player.PlaybackRequest
import dev.aaa1115910.bv.player.PlaybackSource
import dev.aaa1115910.bv.player.VideoPlayerOptions
import dev.aaa1115910.bv.player.formatMinSec
import java.io.IOException

@OptIn(UnstableApi::class)
open class ExoMediaPlayer(
    private val context: Context,
    private val options: VideoPlayerOptions
) : AbstractVideoPlayer(), Player.Listener {

    var mPlayer: ExoPlayer? = null
    private var prepareStartElapsedMs: Long = 0L
    private var prepareSerial: Long = 0L
    private var firstReadyElapsedMs: Long = 0L
    private var firstPlayingElapsedMs: Long = 0L
    private var firstFrameElapsedMs: Long = 0L
    private var bufferingStartElapsedMs: Long = 0L
    private var bufferingCount: Int = 0
    private var startupBufferingCount: Int = 0
    private var totalBufferingDurationMs: Long = 0L
    private var startupBufferingDurationMs: Long = 0L
    private var droppedFramesSincePrepare: Int = 0
    private var lastPlaybackState: Int = Player.STATE_IDLE
    private var lastSeekReason: String = "none"
    private var lastSeekTargetMs: Long = C.TIME_UNSET
    private var lastSeekElapsedMs: Long = 0L

    /**
     * 当前画面变换状态。
     */
    private var currentVideoScaleX: Float = 1f
    private var currentVideoScaleY: Float = 1f
    private var currentVideoRotationDegrees: Float = 0f

    /**
     * 不是“pipeline 是否 primed”，而是：
     * 当前这次 transform / prepare 之后，
     * 在 surface 真正完成绑定后，是否还需要补打一遍 effect。
     */
    private var needsEffectReapplyAfterViewAttach: Boolean = false
    private var needsEffectReapplyAfterReady: Boolean = false

    @OptIn(UnstableApi::class)
    private val dataSourceFactory =
        BvPlayerFactory.createDataSourceFactory(context, options)

    private val analyticsListener = object : AnalyticsListener {
        override fun onLoadStarted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            retryCount: Int
        ) {
            BvPlayerDiag.d(
                event = "loadStarted",
                player = mPlayer,
                msg = "retry=$retryCount ${prepareTiming()} ${BvPlayerDiag.loadInfo(loadEventInfo)} ${BvPlayerDiag.mediaLoadData(mediaLoadData)}"
            )
        }

        override fun onLoadCompleted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData
        ) {
            BvPlayerDiag.d(
                event = "loadCompleted",
                player = mPlayer,
                msg = "${prepareTiming()} ${BvPlayerDiag.loadInfo(loadEventInfo)} ${BvPlayerDiag.mediaLoadData(mediaLoadData)}"
            )
        }

        override fun onLoadError(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            error: IOException,
            wasCanceled: Boolean
        ) {
            BvPlayerDiag.e(
                event = "loadError",
                player = mPlayer,
                throwable = error,
                msg = "wasCanceled=$wasCanceled ${prepareTiming()} ${BvPlayerDiag.loadInfo(loadEventInfo)} ${BvPlayerDiag.mediaLoadData(mediaLoadData)}"
            )
        }

        override fun onBandwidthEstimate(
            eventTime: AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long
        ) {
            BvPlayerDiag.d(
                event = "bandwidthEstimate",
                player = mPlayer,
                msg = "${prepareTiming()} totalLoadTimeMs=$totalLoadTimeMs totalBytesLoaded=$totalBytesLoaded bitrateEstimate=$bitrateEstimate"
            )
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            BvPlayerDiag.i(
                event = "audioDecoderInitialized",
                player = mPlayer,
                msg = "decoder=$decoderName initDurationMs=$initializationDurationMs ${prepareTiming()}"
            )
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            BvPlayerDiag.i(
                event = "videoDecoderInitialized",
                player = mPlayer,
                msg = "decoder=$decoderName initDurationMs=$initializationDurationMs ${prepareTiming()}"
            )
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            BvPlayerDiag.i(
                event = "audioInputFormatChanged",
                player = mPlayer,
                msg = "${prepareTiming()} ${BvPlayerDiag.format(format)}"
            )
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            BvPlayerDiag.i(
                event = "videoInputFormatChanged",
                player = mPlayer,
                msg = "${prepareTiming()} ${BvPlayerDiag.format(format)}"
            )
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long
        ) {
            droppedFramesSincePrepare += droppedFrames
            BvPlayerDiag.w(
                event = "droppedVideoFrames",
                player = mPlayer,
                msg = "droppedFrames=$droppedFrames elapsedMs=$elapsedMs totalSincePrepare=$droppedFramesSincePrepare " +
                        "${prepareTiming()} sinceFirstReadyMs=${elapsedSince(firstReadyElapsedMs)} " +
                        "sinceFirstFrameMs=${elapsedSince(firstFrameElapsedMs)} startupWindow=${isStartupWindow()}"
            )
        }

        override fun onVideoFrameProcessingOffset(
            eventTime: AnalyticsListener.EventTime,
            totalProcessingOffsetUs: Long,
            frameCount: Int
        ) {
            BvPlayerDiag.d(
                event = "videoFrameProcessingOffset",
                player = mPlayer,
                msg = "totalProcessingOffsetUs=$totalProcessingOffsetUs frameCount=$frameCount ${prepareTiming()}"
            )
        }

        override fun onAudioUnderrun(
            eventTime: AnalyticsListener.EventTime,
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long
        ) {
            BvPlayerDiag.w(
                event = "audioUnderrun",
                player = mPlayer,
                msg = "bufferSize=$bufferSize bufferSizeMs=$bufferSizeMs elapsedSinceLastFeedMs=$elapsedSinceLastFeedMs ${prepareTiming()}"
            )
        }

        override fun onAudioSinkError(eventTime: AnalyticsListener.EventTime, audioSinkError: Exception) {
            BvPlayerDiag.e("audioSinkError", mPlayer, audioSinkError)
        }

        override fun onAudioCodecError(eventTime: AnalyticsListener.EventTime, audioCodecError: Exception) {
            BvPlayerDiag.e("audioCodecError", mPlayer, audioCodecError)
        }

        override fun onVideoCodecError(eventTime: AnalyticsListener.EventTime, videoCodecError: Exception) {
            BvPlayerDiag.e("videoCodecError", mPlayer, videoCodecError)
        }
    }

    init {
        initPlayer()
    }

    fun hasPendingVideoEffectsReapply(): Boolean {
        return needsEffectReapplyAfterViewAttach
    }

    @OptIn(UnstableApi::class)
    override fun initPlayer() {
        BvPlayerDiag.i(
            event = "initPlayer",
            player = mPlayer,
            msg = "releaseOld=${mPlayer != null} enableFfmpegAudioRenderer=${options.enableFfmpegAudioRenderer} enableSoftwareVideoDecoder=${options.enableSoftwareVideoDecoder}"
        )
        // 如果外部误调用多次，这里先释放旧实例
        mPlayer?.removeListener(this)
        mPlayer?.removeAnalyticsListener(analyticsListener)
        mPlayer?.release()
        mPlayer = null
        resetTimeline()

        mPlayer = BvPlayerFactory.createPlayer(context, options)
            .apply {
                // 这里保持调用，确保 player 初始化后已有当前 effect 状态
                applyCurrentVideoEffects()
            }

        initListener()
        BvPlayerDiag.i("initPlayerDone", mPlayer)
    }

    private fun initListener() {
        mPlayer?.addListener(this)
        mPlayer?.addAnalyticsListener(analyticsListener)
    }

    fun applyVideoTransform(
        scaleX: Float,
        scaleY: Float,
        rotationDegrees: Float
    ) {
        BvPlayerDiag.i(
            event = "applyVideoTransform",
            player = mPlayer,
            msg = "scaleX=$scaleX scaleY=$scaleY rotationDegrees=$rotationDegrees"
        )
        currentVideoScaleX = scaleX
        currentVideoScaleY = scaleY
        currentVideoRotationDegrees = normalizeRotationDegrees(rotationDegrees)

        if (mPlayer?.playbackState != Player.STATE_READY) {
            needsEffectReapplyAfterReady = true
        }

        applyCurrentVideoEffects()
    }

    private fun normalizeRotationDegrees(rotationDegrees: Float): Float {
        val normalized = rotationDegrees % 360f
        return if (normalized < 0f) normalized + 360f else normalized
    }

    private fun applyCurrentVideoEffects() {
        mPlayer?.setVideoEffects(
            buildVideoEffects(
                scaleX = currentVideoScaleX,
                scaleY = currentVideoScaleY,
                rotationDegrees = currentVideoRotationDegrees
            )
        )
    }

    private fun buildVideoEffects(
        scaleX: Float,
        scaleY: Float,
        rotationDegrees: Float
    ): List<Effect> {
        val isIdentity = scaleX == 1f && scaleY == 1f && rotationDegrees % 360f == 0f
        if (isIdentity) return emptyList()

        return listOf(
            ScaleAndRotateTransformation.Builder()
                .setScale(scaleX, scaleY)
                .setRotationDegrees(rotationDegrees)
                .build()
        )
    }

    /**
     * 关键补丁：
     * 当 surface 已经把 player 重新挂上，并且至少过了一帧消息循环后，
     * 再把当前 effect 重新 apply 一次。
     */
    fun reapplyVideoEffectsAfterViewBound() {
        if (!needsEffectReapplyAfterViewAttach) return
        BvPlayerDiag.i("reapplyVideoEffectsAfterViewBound", mPlayer)
        applyCurrentVideoEffects()
        needsEffectReapplyAfterViewAttach = false
    }

    @OptIn(UnstableApi::class)
    private fun setHeader(headers: Map<String, String>) {
        BvPlayerDiag.i(
            event = "setHeader",
            player = mPlayer,
            msg = "keys=${headers.keys.joinToString()} hasUserAgent=${headers.keys.any { it.equals("User-Agent", ignoreCase = true) }} hasReferer=${headers.keys.any { it.equals("referer", ignoreCase = true) }}"
        )
        val userAgent = headers.entries
            .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value
        if (!userAgent.isNullOrBlank()) {
            dataSourceFactory.setUserAgent(userAgent)
        }

        val requestHeaders = headers
            .filterKeys { !it.equals("User-Agent", ignoreCase = true) }
            .mapKeys { if (it.key.equals("referer", ignoreCase = true)) "referer" else it.key }

        dataSourceFactory.setDefaultRequestProperties(requestHeaders)
    }

    @OptIn(UnstableApi::class)
    private fun buildMediaSource(
        videoUrl: String?,
        audioUrl: String?
    ): MediaSource? {
        return BvPlayerFactory.createMediaSource(dataSourceFactory, videoUrl, audioUrl)
    }

    private fun hasNonIdentityVideoTransform(): Boolean {
        return currentVideoScaleX != 1f ||
                currentVideoScaleY != 1f ||
                currentVideoRotationDegrees % 360f != 0f
    }

    @OptIn(UnstableApi::class)
    override fun prepare(request: PlaybackRequest) {
        val player = mPlayer ?: return

        needsEffectReapplyAfterReady = hasNonIdentityVideoTransform()
        needsEffectReapplyAfterViewAttach = true

        resetTimeline()
        prepareSerial += 1
        prepareStartElapsedMs = SystemClock.elapsedRealtime()
        BvPlayerDiag.i(
            event = "prepare",
            player = player,
            msg = "serial=$prepareSerial hasTransform=${hasNonIdentityVideoTransform()} " +
                    "needsEffectReapplyAfterViewAttach=$needsEffectReapplyAfterViewAttach " +
                    "startPositionMs=${request.startPositionMs} source=${request.source.diagName()}"
        )

        setHeader(request.headers)

        when (val source = request.source) {
            is PlaybackSource.Single -> {
                BvPlayerDiag.i(
                    event = "setMediaItem",
                    player = player,
                    msg = "prepareSerial=$prepareSerial url=${BvPlayerDiag.source(source.url, null)} " +
                            "startPositionMs=${request.startPositionMs}"
                )
                player.setMediaItem(MediaItem.fromUri(source.url), request.startPositionMs.coerceAtLeast(0L))
            }

            is PlaybackSource.SeparateVideoAudio -> {
                BvPlayerDiag.i(
                    event = "setMediaSource",
                    player = player,
                    msg = "prepareSerial=$prepareSerial ${BvPlayerDiag.source(source.videoUrl, source.audioUrl)} " +
                            "startPositionMs=${request.startPositionMs}"
                )
                val mediaSource = buildMediaSource(source.videoUrl, source.audioUrl) ?: return
                player.setMediaSource(mediaSource, request.startPositionMs.coerceAtLeast(0L))
            }
        }

        // 官方要求 prepare 前至少调用一次 setVideoEffects
        applyCurrentVideoEffects()
        player.prepare()
    }

    override fun start() {
        BvPlayerDiag.i("start", mPlayer, prepareTiming())
        mPlayer?.play()
    }

    override fun pause() {
        BvPlayerDiag.i("pause", mPlayer, prepareTiming())
        mPlayer?.pause()
    }

    override fun stop() {
        BvPlayerDiag.i("stop", mPlayer)
        mPlayer?.stop()
    }

    override fun reset() {
        TODO("Not yet implemented")
    }

    override val isPlaying: Boolean
        get() = mPlayer?.isPlaying == true

    override fun seekTo(time: Long) {
        seekTo(time, "unspecified")
    }

    override fun seekTo(time: Long, reason: String) {
        lastSeekReason = reason
        lastSeekTargetMs = time
        lastSeekElapsedMs = SystemClock.elapsedRealtime()
        BvPlayerDiag.i(
            event = "seekTo",
            player = mPlayer,
            msg = "reason=$reason targetMs=$time ${prepareTiming()} callSite=${callSite()}"
        )
        mPlayer?.seekTo(time)
    }

    override fun release() {
        BvPlayerDiag.i("release", mPlayer, startupSummary())
        mPlayer?.removeListener(this)
        mPlayer?.removeAnalyticsListener(analyticsListener)
        mPlayer?.release()
        mPlayer = null
        resetTimeline()
    }

    override val currentPosition: Long
        get() = mPlayer?.currentPosition ?: 0

    override val duration: Long
        get() = mPlayer?.duration ?: 0

    override val bufferedPercentage: Int
        get() = mPlayer?.bufferedPercentage ?: 0

    override var speed: Float
        get() = mPlayer?.playbackParameters?.speed ?: 1f
        set(value) {
            BvPlayerDiag.i("setSpeed", mPlayer, "speed=$value ${prepareTiming()}")
            mPlayer?.setPlaybackSpeed(value)
        }

    override val tcpSpeed: Long
        get() = 0L

    override fun onPlaybackStateChanged(playbackState: Int) {
        val now = SystemClock.elapsedRealtime()
        val previousState = lastPlaybackState
        val stateChangedAfterSeek = lastSeekElapsedMs > 0L && now - lastSeekElapsedMs <= 1500L
        val stateReason = when {
            playbackState == Player.STATE_BUFFERING && stateChangedAfterSeek ->
                "afterSeek(reason=$lastSeekReason,targetMs=$lastSeekTargetMs,seekAgoMs=${now - lastSeekElapsedMs})"
            playbackState == Player.STATE_BUFFERING && firstReadyElapsedMs == 0L -> "startupInitialBuffer"
            playbackState == Player.STATE_READY && previousState == Player.STATE_BUFFERING -> "bufferingEnd"
            else -> "normal"
        }

        if (playbackState == Player.STATE_BUFFERING && previousState != Player.STATE_BUFFERING) {
            bufferingStartElapsedMs = now
            bufferingCount += 1
            if (isStartupWindow(now)) startupBufferingCount += 1
            BvPlayerDiag.i(
                event = "bufferingStart",
                player = mPlayer,
                msg = "reason=$stateReason count=$bufferingCount startupCount=$startupBufferingCount ${prepareTiming(now)}"
            )
        }

        if (previousState == Player.STATE_BUFFERING && playbackState != Player.STATE_BUFFERING && bufferingStartElapsedMs > 0L) {
            val bufferingDurationMs = now - bufferingStartElapsedMs
            totalBufferingDurationMs += bufferingDurationMs
            if (isStartupWindow(now)) startupBufferingDurationMs += bufferingDurationMs
            BvPlayerDiag.i(
                event = "bufferingEnd",
                player = mPlayer,
                msg = "nextState=${BvPlayerDiag.stateName(playbackState)} durationMs=$bufferingDurationMs " +
                        "totalMs=$totalBufferingDurationMs startupMs=$startupBufferingDurationMs ${prepareTiming(now)}"
            )
            bufferingStartElapsedMs = 0L
        }

        BvPlayerDiag.i(
            event = "playbackStateChanged",
            player = mPlayer,
            msg = "from=${BvPlayerDiag.stateName(previousState)} state=${BvPlayerDiag.stateName(playbackState)} " +
                    "reason=$stateReason ${prepareTiming(now)}"
        )
        lastPlaybackState = playbackState
        when (playbackState) {
            Player.STATE_IDLE -> {}

            Player.STATE_BUFFERING -> {
                mPlayerEventListener?.onBuffering()
            }

            Player.STATE_READY -> {
                if (firstReadyElapsedMs == 0L) {
                    firstReadyElapsedMs = now
                    BvPlayerDiag.i("startupReady", mPlayer, startupSummary(now))
                }
                // 如果用户在播放器真正 READY 前就点了旋转/翻转，就把当前 effect 补打一遍。
                if (needsEffectReapplyAfterReady || hasNonIdentityVideoTransform()) {
                    applyCurrentVideoEffects()
                    needsEffectReapplyAfterReady = false
                }
                mPlayerEventListener?.onReady()
            }

            Player.STATE_ENDED -> {
                mPlayerEventListener?.onEnd()
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying && firstPlayingElapsedMs == 0L) {
            firstPlayingElapsedMs = SystemClock.elapsedRealtime()
        }
        BvPlayerDiag.i(
            event = "isPlayingChanged",
            player = mPlayer,
            msg = "isPlaying=$isPlaying ${prepareTiming()}"
        )
        if (isPlaying) {
            mPlayerEventListener?.onPlay()
        } else {
            mPlayerEventListener?.onPause()
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        BvPlayerDiag.i(
            event = "playWhenReadyChanged",
            player = mPlayer,
            msg = "playWhenReady=$playWhenReady reason=${BvPlayerDiag.playWhenReadyReasonName(reason)} ${prepareTiming()}"
        )
    }

    override fun onIsLoadingChanged(isLoading: Boolean) {
        BvPlayerDiag.i(
            event = "isLoadingChanged",
            player = mPlayer,
            msg = "isLoading=$isLoading ${prepareTiming()}"
        )
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        BvPlayerDiag.i(
            event = "playbackParametersChanged",
            player = mPlayer,
            msg = "speed=${playbackParameters.speed} pitch=${playbackParameters.pitch} ${prepareTiming()}"
        )
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        BvPlayerDiag.i(
            event = "positionDiscontinuity",
            player = mPlayer,
            msg = "reason=${BvPlayerDiag.discontinuityReasonName(reason)} oldMs=${oldPosition.positionMs} " +
                    "newMs=${newPosition.positionMs} lastSeekReason=$lastSeekReason " +
                    "lastSeekTargetMs=$lastSeekTargetMs ${prepareTiming()}"
        )
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        BvPlayerDiag.i(
            event = "videoSizeChanged",
            player = mPlayer,
            msg = BvPlayerDiag.videoSize(videoSize)
        )
    }

    override fun onRenderedFirstFrame() {
        val firstFrameCostMs = if (prepareStartElapsedMs > 0L) {
            SystemClock.elapsedRealtime() - prepareStartElapsedMs
        } else {
            0L
        }
        if (firstFrameElapsedMs == 0L) {
            firstFrameElapsedMs = SystemClock.elapsedRealtime()
        }
        BvPlayerDiag.i(
            event = "renderedFirstFrame",
            player = mPlayer,
            msg = "prepareToFirstFrameMs=$firstFrameCostMs ${startupSummary()}"
        )
        mPlayerEventListener?.onRenderedFirstFrame()
    }

    override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) {
        BvPlayerDiag.d("seekBackIncrementChanged", mPlayer, "seekBackIncrementMs=$seekBackIncrementMs")
        mPlayerEventListener?.onSeekBack(seekBackIncrementMs)
    }

    override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) {
        BvPlayerDiag.d("seekForwardIncrementChanged", mPlayer, "seekForwardIncrementMs=$seekForwardIncrementMs")
        mPlayerEventListener?.onSeekForward(seekForwardIncrementMs)
    }

    override val debugInfo: String
        get() {
            return """
                player: ${androidx.media3.common.MediaLibraryInfo.VERSION_SLASHY}
                time: ${currentPosition.formatMinSec()} / ${duration.formatMinSec()}
                buffered: $bufferedPercentage%
                resolution: ${mPlayer?.videoSize?.width} x ${mPlayer?.videoSize?.height}
                audio: ${mPlayer?.audioFormat?.bitrate ?: 0} kbps
                video codec: ${mPlayer?.videoFormat?.sampleMimeType ?: "null"}
                audio codec: ${mPlayer?.audioFormat?.sampleMimeType ?: "null"} (${getAudioRendererName()})
            """.trimIndent()
        }

    private fun getAudioRendererName(): String {
        val rendererCount = mPlayer?.rendererCount ?: return "UnknownRenderer"
        for (i in 0 until rendererCount) {
            val renderer = mPlayer!!.getRenderer(i)
            if (renderer.trackType == C.TRACK_TYPE_AUDIO && renderer.state == Renderer.STATE_STARTED) {
                return renderer.name
            }
        }
        return "UnknownRenderer"
    }

    override val videoWidth: Int
        get() = mPlayer?.videoSize?.width ?: 0

    override val videoHeight: Int
        get() = mPlayer?.videoSize?.height ?: 0

    override fun onPlayerError(error: PlaybackException) {
        BvPlayerDiag.e(
            event = "playerError",
            player = mPlayer,
            throwable = error,
            msg = "errorCode=${error.errorCode} errorCodeName=${error.errorCodeName} ${startupSummary()}"
        )
        mPlayerEventListener?.onError(error)
    }

    private fun resetTimeline() {
        prepareStartElapsedMs = 0L
        firstReadyElapsedMs = 0L
        firstPlayingElapsedMs = 0L
        firstFrameElapsedMs = 0L
        bufferingStartElapsedMs = 0L
        bufferingCount = 0
        startupBufferingCount = 0
        totalBufferingDurationMs = 0L
        startupBufferingDurationMs = 0L
        droppedFramesSincePrepare = 0
        lastPlaybackState = Player.STATE_IDLE
        lastSeekReason = "none"
        lastSeekTargetMs = C.TIME_UNSET
        lastSeekElapsedMs = 0L
    }

    private fun prepareTiming(now: Long = SystemClock.elapsedRealtime()): String {
        return "serial=$prepareSerial sincePrepareMs=${elapsedSince(prepareStartElapsedMs, now)}"
    }

    private fun startupSummary(now: Long = SystemClock.elapsedRealtime()): String {
        return "${prepareTiming(now)} prepareToReadyMs=${elapsedBetween(prepareStartElapsedMs, firstReadyElapsedMs)} " +
                "prepareToPlayingMs=${elapsedBetween(prepareStartElapsedMs, firstPlayingElapsedMs)} " +
                "prepareToFirstFrameMs=${elapsedBetween(prepareStartElapsedMs, firstFrameElapsedMs)} " +
                "bufferingCount=$bufferingCount startupBufferingCount=$startupBufferingCount " +
                "totalBufferingMs=$totalBufferingDurationMs startupBufferingMs=$startupBufferingDurationMs " +
                "droppedSincePrepare=$droppedFramesSincePrepare lastSeekReason=$lastSeekReason lastSeekTargetMs=$lastSeekTargetMs"
    }

    private fun elapsedSince(startMs: Long, now: Long = SystemClock.elapsedRealtime()): Long {
        return if (startMs > 0L) now - startMs else -1L
    }

    private fun elapsedBetween(startMs: Long, endMs: Long): Long {
        return if (startMs > 0L && endMs > 0L) endMs - startMs else -1L
    }

    private fun isStartupWindow(now: Long = SystemClock.elapsedRealtime()): Boolean {
        val sincePrepareMs = elapsedSince(prepareStartElapsedMs, now)
        return sincePrepareMs in 0..10_000
    }

    private fun callSite(): String {
        return Thread.currentThread().stackTrace
            .firstOrNull {
                val className = it.className
                !className.contains("ExoMediaPlayer") &&
                        !className.contains("AbstractVideoPlayer") &&
                        !className.startsWith("java.lang.Thread")
            }
            ?.let { "${it.className}.${it.methodName}:${it.lineNumber}" }
            ?: "unknown"
    }

    private fun PlaybackSource.diagName(): String {
        return when (this) {
            is PlaybackSource.Single -> "Single"
            is PlaybackSource.SeparateVideoAudio -> "SeparateVideoAudio"
        }
    }
}
