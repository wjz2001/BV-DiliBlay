package dev.aaa1115910.biliapi.http.entity.user.subscription

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeasonArchivesData(
    val archives: List<SeasonArchive> = emptyList(),
    val meta: Meta? = null,
    val page: Page? = null
) {
    @Serializable
    data class SeasonArchive(
        val aid: Long,
        val bvid: String? = null,
        val duration: Int = 0,
        val pic: String = "",
        val pubdate: Long? = null,
        val stat: Stat? = null,
        val title: String = ""
    ) {
        @Serializable
        data class Stat(
            val view: Int? = null,
            val danmaku: Int? = null
        )
    }

    @Serializable
    data class Meta(
        val cover: String? = null,
        val mid: Long? = null,
        val name: String? = null,
        @SerialName("season_id")
        val seasonId: Long? = null,
        val total: Int? = null
    )

    @Serializable
    data class Page(
        @SerialName("page_num")
        val pageNum: Int? = null,
        @SerialName("page_size")
        val pageSize: Int? = null,
        val total: Int? = null
    )
}
