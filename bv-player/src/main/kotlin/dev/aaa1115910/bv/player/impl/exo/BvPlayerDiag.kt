package dev.aaa1115910.bv.player.impl.exo

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.net.URI

@OptIn(UnstableApi::class)
internal object BvPlayerDiag {
    private const val TAG = "BvPlayerDiag"

    fun d(event: String, player: ExoPlayer?, msg: String = "") {
        Log.d(TAG, line(event, player, msg))
    }

    fun i(event: String, player: ExoPlayer?, msg: String = "") {
        Log.i(TAG, line(event, player, msg))
    }

    fun w(event: String, player: ExoPlayer?, msg: String = "") {
        Log.w(TAG, line(event, player, msg))
    }

    fun e(event: String, player: ExoPlayer?, throwable: Throwable, msg: String = "") {
        Log.e(TAG, line(event, player, msg), throwable)
    }

    fun source(videoUrl: String?, audioUrl: String?): String {
        return "video=${safeUrl(videoUrl)} audio=${safeUrl(audioUrl)}"
    }

    fun format(format: Format?): String {
        if (format == null) return "null"
        return buildString {
            append("mime=${format.sampleMimeType}")
            append(" codecs=${format.codecs}")
            append(" bitrate=${format.bitrate}")
            if (format.width > 0 || format.height > 0) {
                append(" size=${format.width}x${format.height}")
            }
            if (format.frameRate > 0f) {
                append(" fps=${format.frameRate}")
            }
            if (format.channelCount > 0 || format.sampleRate > 0) {
                append(" audio=${format.channelCount}ch/${format.sampleRate}Hz")
            }
            if (!format.language.isNullOrBlank()) {
                append(" lang=${format.language}")
            }
            if (!format.id.isNullOrBlank()) {
                append(" id=${format.id}")
            }
        }
    }

    fun videoSize(videoSize: VideoSize): String {
        return "${videoSize.width}x${videoSize.height} rotation=${videoSize.unappliedRotationDegrees} ratio=${videoSize.pixelWidthHeightRatio}"
    }

    fun loadInfo(loadEventInfo: LoadEventInfo): String {
        return "uri=${safeUri(loadEventInfo.uri)} elapsedMs=${loadEventInfo.elapsedRealtimeMs} loadMs=${loadEventInfo.loadDurationMs} bytes=${loadEventInfo.bytesLoaded}"
    }

    fun mediaLoadData(mediaLoadData: MediaLoadData): String {
        return "dataType=${dataTypeName(mediaLoadData.dataType)} track=${trackTypeName(mediaLoadData.trackType)} startUs=${mediaLoadData.mediaStartTimeMs} endUs=${mediaLoadData.mediaEndTimeMs} format=[${format(mediaLoadData.trackFormat)}]"
    }

    fun stateName(state: Int): String {
        return when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN($state)"
        }
    }

    fun playWhenReadyReasonName(reason: Int): String {
        return when (reason) {
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
            Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
            Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "END_OF_MEDIA_ITEM"
            else -> "UNKNOWN($reason)"
        }
    }

    fun discontinuityReasonName(reason: Int): String {
        return when (reason) {
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "AUTO_TRANSITION"
            Player.DISCONTINUITY_REASON_SEEK -> "SEEK"
            Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "SEEK_ADJUSTMENT"
            Player.DISCONTINUITY_REASON_SKIP -> "SKIP"
            Player.DISCONTINUITY_REASON_REMOVE -> "REMOVE"
            Player.DISCONTINUITY_REASON_INTERNAL -> "INTERNAL"
            else -> "UNKNOWN($reason)"
        }
    }

    private fun line(event: String, player: ExoPlayer?, msg: String): String {
        return buildString {
            append("event=").append(event)
            if (msg.isNotBlank()) {
                append(' ').append(msg)
            }
            player?.let {
                append(" | ").append(snapshot(it))
            }
        }
    }

    private fun snapshot(player: ExoPlayer): String {
        val position = player.currentPosition
        val buffered = player.bufferedPosition
        val duration = player.duration
        val bufferedLeft = if (buffered >= 0 && position >= 0) buffered - position else C.TIME_UNSET
        return buildString {
            append("state=").append(stateName(player.playbackState))
            append(" playWhenReady=").append(player.playWhenReady)
            append(" isPlaying=").append(player.isPlaying)
            append(" isLoading=").append(player.isLoading)
            append(" suppression=").append(player.playbackSuppressionReason)
            append(" posMs=").append(position)
            append(" bufferedMs=").append(buffered)
            append(" bufferedLeftMs=").append(bufferedLeft)
            append(" durationMs=").append(duration)
            append(" bufferedPct=").append(player.bufferedPercentage)
            append(" speed=").append(player.playbackParameters.speed)
            append(" mediaIndex=").append(player.currentMediaItemIndex)
            append(" videoSize=").append(player.videoSize.width).append('x').append(player.videoSize.height)
            append(" videoFormat=[").append(format(player.videoFormat)).append(']')
            append(" audioFormat=[").append(format(player.audioFormat)).append(']')
        }
    }

    private fun safeUrl(url: String?): String {
        if (url.isNullOrBlank()) return "null"
        return runCatching {
            val uri = URI(url)
            "${uri.scheme}://${uri.host}${uri.path} query=${!uri.query.isNullOrBlank()}"
        }.getOrElse {
            val parsed = Uri.parse(url)
            "${parsed.scheme}://${parsed.host}${parsed.path} query=${!parsed.query.isNullOrBlank()}"
        }
    }

    private fun safeUri(uri: Uri?): String {
        if (uri == null) return "null"
        return "${uri.scheme}://${uri.host}${uri.path} query=${!uri.query.isNullOrBlank()}"
    }

    private fun dataTypeName(dataType: Int): String {
        return when (dataType) {
            C.DATA_TYPE_MEDIA -> "MEDIA"
            C.DATA_TYPE_MEDIA_INITIALIZATION -> "MEDIA_INITIALIZATION"
            C.DATA_TYPE_MANIFEST -> "MANIFEST"
            C.DATA_TYPE_TIME_SYNCHRONIZATION -> "TIME_SYNCHRONIZATION"
            C.DATA_TYPE_DRM -> "DRM"
            C.DATA_TYPE_AD -> "AD"
            C.DATA_TYPE_UNKNOWN -> "UNKNOWN"
            else -> "OTHER($dataType)"
        }
    }

    private fun trackTypeName(trackType: Int): String {
        return when (trackType) {
            C.TRACK_TYPE_DEFAULT -> "DEFAULT"
            C.TRACK_TYPE_AUDIO -> "AUDIO"
            C.TRACK_TYPE_VIDEO -> "VIDEO"
            C.TRACK_TYPE_TEXT -> "TEXT"
            C.TRACK_TYPE_METADATA -> "METADATA"
            C.TRACK_TYPE_CAMERA_MOTION -> "CAMERA_MOTION"
            C.TRACK_TYPE_IMAGE -> "IMAGE"
            C.TRACK_TYPE_NONE -> "NONE"
            C.TRACK_TYPE_UNKNOWN -> "UNKNOWN"
            else -> "OTHER($trackType)"
        }
    }
}
