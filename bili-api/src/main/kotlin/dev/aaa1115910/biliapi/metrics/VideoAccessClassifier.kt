package dev.aaa1115910.biliapi.metrics

import dev.aaa1115910.biliapi.http.entity.video.PlayUrlData
import dev.aaa1115910.biliapi.http.entity.video.VideoInfo

internal object VideoAccessClassifier {
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

    fun resolveAccessFlags(
        rawPaidVideo: Boolean?,
        isVipVideo: Boolean?
    ): ResolvedAccessFlags {
        val resolvedPaidVideo = when {
            isVipVideo == true -> false
            rawPaidVideo == null -> null
            else -> rawPaidVideo
        }
        return ResolvedAccessFlags(
            isVipVideo = isVipVideo,
            isPaidVideo = resolvedPaidVideo
        )
    }
}
