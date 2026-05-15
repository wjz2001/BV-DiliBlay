package dev.aaa1115910.biliapi.metrics

import dev.aaa1115910.biliapi.http.entity.video.PlayUrlData
import dev.aaa1115910.biliapi.http.entity.video.VideoInfo

internal object VideoAccessClassifier {
    enum class PlaybackAccessSource {
        WEB_UGC,
        WEB_PGC,
        APP_UGC,
        APP_PGC,
        UNKNOWN
    }

    data class PlaybackAccessFlags(
        val source: PlaybackAccessSource,
        val isVipVideo: Boolean?,
        val hasPaid: Boolean?,
        val isPreview: Boolean?
    )

    data class ResolvedAccessFlags(
        val isVipVideo: Boolean?,
        val isPaidVideo: Boolean?
    )

    fun rawPaidVideo(view: VideoInfo): Boolean {
        return view.isChargeableSeason ||
                view.rights.pay == 1 ||
                view.rights.ugcPay == 1 ||
                view.rights.arcPay == 1
    }

    fun inferVipVideo(playUrlData: PlayUrlData): Boolean? {
        return if (playUrlData.supportFormats.isNotEmpty()) {
            playUrlData.supportFormats.any { it.needVip }
        } else {
            null
        }
    }

    fun inferPreview(playUrlData: PlayUrlData): Boolean {
        return playUrlData.isPreview == 1 ||
                playUrlData.acceptDescription.any { it.contains("试看") } ||
                (playUrlData.dash == null && playUrlData.durl.isNotEmpty())
    }

    fun resolveAccessFlags(
        rawPaidVideo: Boolean?,
        isVipVideo: Boolean?,
        hasPaid: Boolean? = null,
        isPreview: Boolean? = null
    ): ResolvedAccessFlags {
        val resolvedPaidVideo = when {
            isVipVideo == true -> false
            hasPaid == true -> false
            isPreview == true -> true
            rawPaidVideo == null -> null
            else -> rawPaidVideo
        }
        return ResolvedAccessFlags(
            isVipVideo = isVipVideo,
            isPaidVideo = resolvedPaidVideo
        )
    }
}
