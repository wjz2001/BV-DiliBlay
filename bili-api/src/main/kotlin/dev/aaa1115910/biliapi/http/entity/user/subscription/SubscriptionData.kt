package dev.aaa1115910.biliapi.http.entity.user.subscription

import dev.aaa1115910.biliapi.http.entity.user.favorite.CntInfo
import dev.aaa1115910.biliapi.http.entity.user.favorite.Upper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionData(
    val list: List<SubscriptionItem> = emptyList(),
    @SerialName("has_more")
    val hasMore: Boolean = false
)

@Serializable
data class SubscriptionItem(
    val id: Long,
    val fid: Long? = null,
    val mid: Long? = null,
    val attr: Int? = null,
    val title: String,
    val cover: String? = null,
    val upper: Upper? = null,
    @SerialName("cover_type")
    val coverType: Int? = null,
    val intro: String? = null,
    val ctime: Long? = null,
    val mtime: Long? = null,
    val state: Int? = null,
    @SerialName("fav_state")
    val favState: Int? = null,
    @SerialName("media_count")
    val mediaCount: Int = 0,
    @SerialName("view_count")
    val viewCount: Int? = null,
    val type: Int,
    @SerialName("cnt_info")
    val cntInfo: CntInfo? = null
)

@Serializable
data class SubscriptionSeasonData(
    val info: SubscriptionItem? = null,
    val medias: List<SubscriptionSeasonMedia> = emptyList()
)

@Serializable
data class SubscriptionSeasonMedia(
    val id: Long,
    val title: String,
    val cover: String,
    val duration: Int = 0,
    val pubtime: Long? = null,
    val bvid: String? = null,
    @SerialName("cnt_info")
    val cntInfo: CntInfo? = null
)
