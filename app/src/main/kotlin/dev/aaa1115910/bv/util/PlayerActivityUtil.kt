package dev.aaa1115910.bv.util

import android.content.Context
import dev.aaa1115910.biliapi.entity.user.Author
import dev.aaa1115910.bv.activities.video.VideoPlayerV3Activity
import dev.aaa1115910.bv.entity.VideoSource
import dev.aaa1115910.bv.entity.proxy.ProxyArea

fun launchPlayerActivity(
    context: Context,
    avid: Long,
    cid: Long,
    title: String,
    partTitle: String,
    played: Long,
    source: VideoSource = VideoSource.Ugc,
    subType: Int? = null,
    epid: Int? = null,
    seasonId: Int? = null,
    proxyArea: ProxyArea = ProxyArea.MainLand,
    author: Author? = null,
) {
    VideoPlayerV3Activity.actionStart(
        context = context,
        avid = avid,
        cid = cid,
        title = title,
        partTitle = partTitle,
        played = played,
        source = source,
        subType = subType,
        epid = epid,
        seasonId = seasonId,
        proxyArea = proxyArea,
        author = author
    )
}
