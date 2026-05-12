package dev.aaa1115910.bv.viewmodel.video

import dev.aaa1115910.biliapi.entity.FavoriteFolderMetadata
import dev.aaa1115910.biliapi.entity.richtext.RichTextContent
import dev.aaa1115910.biliapi.entity.user.Author
import dev.aaa1115910.biliapi.entity.video.Tag
import dev.aaa1115910.biliapi.entity.video.VideoDetail.Stat
import dev.aaa1115910.biliapi.entity.video.VideoPage
import dev.aaa1115910.biliapi.entity.video.season.UgcSeason
import dev.aaa1115910.biliapi.http.entity.relation.RelationTag
import dev.aaa1115910.biliapi.metrics.VideoMetricsEnvelope
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.Date

data class VideoDetailUiState(
    val videoDetailState: VideoDetailState? = null,
    val loadingState: VideoInfoState = VideoInfoState.Loading,
    val errorTip: String = "",
    val isFollowingUp: Boolean = false,
    val followTags: ImmutableList<RelationTag> = persistentListOf(),
    val showVideoInfo: Boolean = true,
    val fromController: Boolean = false,
    val favoriteFolders: ImmutableList<FavoriteFolderMetadata> = persistentListOf(),
    val videoFavoriteFolderIds: Set<Long> = emptySet()
) {
    val shouldShowLoading: Boolean
        get() = loadingState == VideoInfoState.Loading ||
                videoDetailState?.redirectToEp == true ||
                (!fromController && !showVideoInfo)
}

data class VideoDetailState(
    val aid: Long = 0,
    val bvid: String? = null,
    val cid: Long,
    val epid: Int? = null,
    val cover: String,
    val title: String,
    val publishDate: Date,
    val stat: Stat,
    val metrics: VideoMetricsEnvelope? = null,
    val author: Author,
    val tags: ImmutableList<Tag>,
    val isUpowerExclusive: Boolean = false,
    val redirectToEp: Boolean,
    val argueTip: String?,
    val description: String,
    val descriptionContent: RichTextContent,
    val pages: ImmutableList<VideoPage>,
    val relatedVideos: ImmutableList<VideoCardData>,
    val ugcSeason: UgcSeason?,
    val lastPlayedCid: Long,
    val lastPlayedTime: Int,
    val isLiked: Boolean,
    val isCoined: Boolean,
    val isFavorite: Boolean,
    val coAuthors: ImmutableList<dev.aaa1115910.biliapi.entity.user.CoAuthor> = persistentListOf(),
)
