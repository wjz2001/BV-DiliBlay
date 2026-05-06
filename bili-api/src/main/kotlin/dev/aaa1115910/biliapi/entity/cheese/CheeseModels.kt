package dev.aaa1115910.biliapi.entity.cheese

data class PurchasedCoursePage(
    val courses: List<PurchasedCourse>,
    val next: Boolean,
    val total: Int,
    val pageNumber: Int,
    val pageSize: Int,
    val rawJson: String
)

data class PurchasedCourse(
    val seasonId: Long,
    val title: String,
    val cover: String,
    val subtitle: String?,
    val upName: String,
    val upMid: Long?,
    val epCountText: String,
    val progressText: String?
)

data class CheeseSeasonDetail(
    val seasonId: Long,
    val title: String,
    val cover: String,
    val subtitle: String?,
    val courseContent: String?,
    val releaseInfo: String?,
    val upName: String,
    val upMid: Long?,
    val briefImages: List<CheeseBriefImage>,
    val episodes: List<CheeseEpisode>,
    val progressText: String?,
    val lastEpId: Long?,
    val rawJson: String
)

data class CheeseBriefImage(
    val url: String,
    val aspectRatio: Float?
)

data class CheeseEpisode(
    val epId: Long,
    val aid: Long,
    val cid: Long,
    val index: Int,
    val title: String,
    val subtitle: String?,
    val cover: String,
    val duration: Int,
    val play: Long,
    val watched: Boolean,
    val watchedHistory: Int,
    val canView: Boolean
)
