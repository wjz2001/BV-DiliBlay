package dev.aaa1115910.bv.viewmodel.video

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.aaa1115910.biliapi.entity.video.VideoDetail
import dev.aaa1115910.biliapi.repositories.VideoDetailRepository
import dev.aaa1115910.bv.entity.VideoListItem
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.repository.VideoInfoRepository
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.swapListWithMainContext
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class VideoDetailViewModel(
    private val videoInfoRepository: VideoInfoRepository,
    private val videoDetailRepository: VideoDetailRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger { }
    var state by mutableStateOf(VideoInfoState.Loading)
    var videoDetail: VideoDetail? by mutableStateOf(null)

    var relatedVideos = mutableStateListOf<VideoCardData>()

    fun updateUgcSeasonSectionVideoList(sectionIndex: Int){
        val partVideoList =
            videoDetail!!.ugcSeason!!.sections[sectionIndex].episodes.mapIndexed { _, episode ->
                VideoListItem(
                    aid = episode.aid,
                    cid = episode.cid,
                    title = episode.title
                )
            }
        videoInfoRepository.clearVideoList()
        videoInfoRepository.addToVideoList(partVideoList)
    }

    fun clearVideoList() {
        videoInfoRepository.clearVideoList()
    }
    fun addToVideoList(videoListItem: List<VideoListItem>) {
        videoInfoRepository.addToVideoList(videoListItem)
    }

    suspend fun loadDetail(aid: Long) {
        logger.fInfo { "Load detail: [avid=$aid, preferApiType=${Prefs.apiType.name}]" }
        // state = VideoInfoState.Loading
        // 在开始异步任务前，先在主线程更新状态为 Loading
        withContext(Dispatchers.Main) {
            state = VideoInfoState.Loading
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val videoDetailData = videoDetailRepository.getVideoDetail(
                    aid = aid,
                    preferApiType = Prefs.apiType
                )
                withContext(Dispatchers.Main) { videoDetail = videoDetailData }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    state = VideoInfoState.Error
                    logger.fInfo { "Load video av$aid failed: ${it.stackTraceToString()}" }
                }
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    state = VideoInfoState.Success
                    logger.fInfo { "Load video av$aid success" }

                    updateRelatedVideos()
                }
            }.getOrThrow()
        }
    }

    suspend fun loadDetailOnlyUpdateHistory(aid: Long) {
        logger.fInfo { "Load detail only update history: [avid=$aid, preferApiType=${Prefs.apiType.name}]" }
        runCatching {
            val historyData = videoDetailRepository.getVideoDetail(
                aid = aid,
                preferApiType = Prefs.apiType
            ).history
            // withContext(Dispatchers.Main) { videoDetail?.history = historyData }
            // 切换到主线程来安全地更新状态
            withContext(Dispatchers.Main) {
                    // 使用 data class 的 copy() 方法创建一个新的 VideoDetail 实例，
                    // 只更新其中的 history 字段，然后用这个新实例替换整个旧的状态。
                videoDetail?.let {
                    videoDetail = it.copy(history = historyData)
                }
            }
        }.onFailure {
            logger.fInfo { "Load video av$aid only update history failed: ${it.stackTraceToString()}" }
        }.onSuccess {
            logger.fInfo { "Load video av$aid only update history success: ${videoDetail?.history}" }
        }
    }

    private suspend fun updateRelatedVideos() {
        logger.fInfo { "Start update relate video" }
        val relateVideoCardDataList = videoDetail?.relatedVideos?.map {
            VideoCardData(
                avid = it.aid,
                title = it.title,
                cover = it.cover,
                upName = it.author?.name ?: "",
                time = it.duration * 1000L,
                play = it.view,
                danmaku = it.danmaku,
                jumpToSeason = it.jumpToSeason,
                epId = it.epid,
            )
        } ?: emptyList()
        relatedVideos.swapListWithMainContext(relateVideoCardDataList)
        logger.fInfo { "Update ${relateVideoCardDataList.size} relate videos" }
    }
}

enum class VideoInfoState {
    Loading,
    Success,
    Error
}