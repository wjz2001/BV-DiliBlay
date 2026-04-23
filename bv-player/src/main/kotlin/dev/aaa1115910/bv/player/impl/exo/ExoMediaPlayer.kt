package dev.aaa1115910.bv.player.impl.exo

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.source.MediaSource
import dev.aaa1115910.bv.player.AbstractVideoPlayer
import dev.aaa1115910.bv.player.VideoPlayerOptions
import dev.aaa1115910.bv.player.formatMinSec

@OptIn(UnstableApi::class)
open class ExoMediaPlayer(
    private val context: Context,
    private val options: VideoPlayerOptions
) : AbstractVideoPlayer(), Player.Listener {

    var mPlayer: ExoPlayer? = null
    private var mMediaSource: MediaSource? = null

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

    init {
        initPlayer()
    }

    fun hasPendingVideoEffectsReapply(): Boolean {
        return needsEffectReapplyAfterViewAttach
    }

    @OptIn(UnstableApi::class)
    override fun initPlayer() {
        // 如果外部误调用多次，这里先释放旧实例
        mPlayer?.removeListener(this)
        mPlayer?.release()
        mPlayer = null

        mPlayer = BvPlayerFactory.createPlayer(context, options)
            .apply {
                // 这里保持调用，确保 player 初始化后已有当前 effect 状态
                applyCurrentVideoEffects()
            }

        initListener()
    }

    private fun initListener() {
        mPlayer?.addListener(this)
    }

    fun applyVideoTransform(
        scaleX: Float,
        scaleY: Float,
        rotationDegrees: Float
    ) {
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
        applyCurrentVideoEffects()
        needsEffectReapplyAfterViewAttach = false
    }

    @OptIn(UnstableApi::class)
    override fun setHeader(headers: Map<String, String>) {
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
    override fun playUrl(videoUrl: String?, audioUrl: String?) {
        mMediaSource = buildMediaSource(videoUrl, audioUrl)
    }

    @OptIn(UnstableApi::class)
    override fun prepare() {
        val player = mPlayer ?: return
        val mediaSource = mMediaSource ?: return

        needsEffectReapplyAfterReady = hasNonIdentityVideoTransform()
        needsEffectReapplyAfterViewAttach = true

        player.setMediaSource(mediaSource)

        // 官方要求 prepare 前至少调用一次 setVideoEffects
        applyCurrentVideoEffects()
        player.prepare()
    }

    override fun start() {
        mPlayer?.play()
    }

    override fun pause() {
        mPlayer?.pause()
    }

    override fun stop() {
        mPlayer?.stop()
    }

    override fun reset() {
        TODO("Not yet implemented")
    }

    override val isPlaying: Boolean
        get() = mPlayer?.isPlaying == true

    override fun seekTo(time: Long) {
        mPlayer?.seekTo(time)
    }

    override fun release() {
        mPlayer?.removeListener(this)
        mPlayer?.release()
        mPlayer = null
        mMediaSource = null
    }

    override val currentPosition: Long
        get() = mPlayer?.currentPosition ?: 0

    override val duration: Long
        get() = mPlayer?.duration ?: 0

    override val bufferedPercentage: Int
        get() = mPlayer?.bufferedPercentage ?: 0

    override fun setOptions() {
        mPlayer?.playWhenReady = true
    }

    override var speed: Float
        get() = mPlayer?.playbackParameters?.speed ?: 1f
        set(value) {
            mPlayer?.setPlaybackSpeed(value)
        }

    override val tcpSpeed: Long
        get() = 0L

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_IDLE -> {}

            Player.STATE_BUFFERING -> {
                mPlayerEventListener?.onBuffering()
            }

            Player.STATE_READY -> {
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
        if (isPlaying) {
            mPlayerEventListener?.onPlay()
        } else {
            mPlayerEventListener?.onPause()
        }
    }

    override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) {
        mPlayerEventListener?.onSeekBack(seekBackIncrementMs)
    }

    override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) {
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
        mPlayerEventListener?.onError(error)
    }
}
