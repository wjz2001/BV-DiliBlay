package dev.aaa1115910.bv.player.impl.exo

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dev.aaa1115910.bv.player.OkHttpUtil
import dev.aaa1115910.bv.player.VideoPlayerOptions

/**
 * Media3 / ExoPlayer 的唯一构建入口。
 *
 * Phase 1 只集中构建配置，不改变 UI、不改变播放源选择逻辑。
 */
@OptIn(UnstableApi::class)
object BvPlayerFactory {
    /**
     * 目的：规避部分平台异步 codec 队列实现导致的稳定性问题。
     * 副作用：可能降低解码吞吐或增加播放延迟。
     * 验收：开启/关闭后分别播放 AVC/HEVC/AV1 样本，确认无新增黑屏、卡死、音画不同步。
     */
    private const val FORCE_DISABLE_MEDIA_CODEC_ASYNCHRONOUS_QUEUEING = false

    /**
     * 目的：首选解码器失败时允许 Media3 回退到其他可用解码器。
     * 副作用：失败链路上起播耗时可能增加，且最终解码器可能与首选不一致。
     * 验收：播放硬解失败样本时可回退；正常样本 codec 与性能不发生非预期变化。
     */
    private const val ENABLE_DECODER_FALLBACK = false

    /**
     * 目的：预留一键关闭 tunneling，便于排查音视频直通链路导致的黑屏/无声问题。
     * 副作用：若未来启用 tunneling，关闭后可能增加功耗或降低部分设备播放效率。
     * 验收：未来启用 tunneling 后，关闭此开关仍可正常起播、seek、切后台恢复。
     */
    private const val DISABLE_TUNNELING = false

    /**
     * 目的：预留 surface detach 超时配置，避免 surface 生命周期异常时长时间阻塞。
     * 副作用：过短可能导致正常 detach 流程被提前中断。
     * 验收：切后台/返回/旋转/重绑 surface 时无新增崩溃、黑屏或卡死。
     */
    private val DETACH_SURFACE_TIMEOUT_MS: Long? = null

    /**
     * 目的：预留 release 超时配置，避免释放播放器时长时间阻塞。
     * 副作用：过短可能导致底层资源释放不完整或日志噪音增加。
     * 验收：连续进出播放页、多 P 切换后无资源泄漏、ANR 或 surface 残留。
     */
    private val RELEASE_TIMEOUT_MS: Long? = null

    fun createPlayer(
        context: Context,
        options: VideoPlayerOptions
    ): ExoPlayer {
        return ExoPlayer
            .Builder(context)
            .setRenderersFactory(createRenderersFactory(context, options))
            .setSeekForwardIncrementMs(10000L)
            .setSeekBackIncrementMs(5000L)
            .apply {
                DETACH_SURFACE_TIMEOUT_MS?.let { setDetachSurfaceTimeoutMs(it) }
                RELEASE_TIMEOUT_MS?.let { setReleaseTimeoutMs(it) }
            }
            .build()
    }

    fun createDataSourceFactory(
        context: Context,
        options: VideoPlayerOptions
    ): OkHttpDataSource.Factory {
        return OkHttpDataSource.Factory(OkHttpUtil.generateCustomSslOkHttpClient(context)).apply {
            options.userAgent?.let { setUserAgent(it) }
            options.referer?.let { setDefaultRequestProperties(mapOf("referer" to it)) }
        }
    }

    fun createMediaSource(
        dataSourceFactory: OkHttpDataSource.Factory,
        videoUrl: String?,
        audioUrl: String?
    ): MediaSource? {
        val videoMediaSource = videoUrl?.let {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(it))
        }
        val audioMediaSource = audioUrl?.let {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(it))
        }

        val mediaSources = listOfNotNull(videoMediaSource, audioMediaSource)
        if (mediaSources.isEmpty()) return null

        return if (mediaSources.size == 1) {
            mediaSources.first()
        } else {
            MergingMediaSource(*mediaSources.toTypedArray())
        }
    }

    private fun createRenderersFactory(
        context: Context,
        options: VideoPlayerOptions
    ): RenderersFactory {
        return DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(
                when (options.enableFfmpegAudioRenderer) {
                    true -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    false -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                }
            )
            if (options.enableSoftwareVideoDecoder) {
                setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                    val allDecoders = MediaCodecUtil.getDecoderInfos(
                        mimeType,
                        requiresSecureDecoder,
                        requiresTunnelingDecoder
                    )
                    val softwareDecoders = allDecoders.filter {
                        it.name.startsWith("OMX.google.") || it.name.startsWith("c2.android.")
                    }
                    softwareDecoders.ifEmpty { allDecoders }
                }
            } else {
                setMediaCodecSelector(MediaCodecSelector.DEFAULT)
            }
            if (FORCE_DISABLE_MEDIA_CODEC_ASYNCHRONOUS_QUEUEING) {
                forceDisableMediaCodecAsynchronousQueueing()
            }
            if (ENABLE_DECODER_FALLBACK) {
                setEnableDecoderFallback(true)
            }
        }
    }
}
