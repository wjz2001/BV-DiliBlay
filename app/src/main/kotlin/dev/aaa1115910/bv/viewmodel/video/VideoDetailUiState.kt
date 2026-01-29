package dev.aaa1115910.bv.viewmodel.video

import dev.aaa1115910.biliapi.entity.FavoriteFolderMetadata
import dev.aaa1115910.biliapi.entity.video.VideoDetail
import dev.aaa1115910.bv.entity.carddata.VideoCardData

data class VideoDetailUiState(
    val videoDetail: VideoDetail? = null,
    val loadingState: VideoInfoState = VideoInfoState.Loading,
    val tip: String = "Loading...",
    val lastPlayedCid: Long = 0,
    val lastPlayedTime: Int = 0,
    val isFollowingUp: Boolean = false,
    val isLiked: Boolean = false,
    val isCoined: Boolean = false,
    val isFavorite: Boolean = false,
    val fromSeason: Boolean = false,
    val fromController: Boolean = false,
    val relatedVideos: List<VideoCardData> = emptyList(),
    val favoriteFolders: List<FavoriteFolderMetadata> = emptyList(),
    val videoFavoriteFolderIds: List<Long> = emptyList(),
)
