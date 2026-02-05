package dev.aaa1115910.bv.repository

import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.video.VideoDetail.History
import dev.aaa1115910.biliapi.repositories.VideoDetailRepository
import dev.aaa1115910.bv.entity.VideoListItem
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single

@Single
class VideoInfoRepository(private val videoDetailRepository: VideoDetailRepository) {
    private val _videoList = MutableStateFlow<List<VideoListItem>>(emptyList())
    private val _history = MutableStateFlow(History(progress = 0, lastPlayedCid = 0))
    private val _relatedVideos = MutableStateFlow<List<VideoCardData>>(emptyList())

    val videoList = _videoList.asStateFlow()
    val history = _history.asStateFlow()
    val relatedVideos = _relatedVideos.asStateFlow()

    suspend fun updateUgcPages(preferApiType: ApiType = ApiType.Web) {
        _videoList.update { oldList ->
            oldList.map { item ->
                val pages =
                    videoDetailRepository.getUgcPages(aid = item.aid, preferApiType = preferApiType)
                if (pages.size > 1) {
                    item.copy(
                        ugcPages = pages,
                    )
                } else {
                    item
                }
            }
        }
    }

    fun updateVideoList(videoListItem: List<VideoListItem>) {
        _videoList.update { videoListItem }
    }

    fun updateHistory(progress: Int, lastPlayedCid: Long) {
        _history.update { History(progress = progress, lastPlayedCid = lastPlayedCid) }
    }

    fun updateRelatedVideos(relatedVideos: List<VideoCardData>) {
        _relatedVideos.update { relatedVideos }
    }
}