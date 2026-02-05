package dev.aaa1115910.bv.viewmodel.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.http.BiliPlusHttpApi
import dev.aaa1115910.biliapi.repositories.CoinRepository
import dev.aaa1115910.biliapi.repositories.FavoriteRepository
import dev.aaa1115910.biliapi.repositories.LikeRepository
import dev.aaa1115910.biliapi.repositories.OneClickTripleActionRepository
import dev.aaa1115910.biliapi.repositories.UserRepository
import dev.aaa1115910.biliapi.repositories.VideoDetailRepository
import dev.aaa1115910.bv.entity.VideoListItem
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.repository.VideoInfoRepository
import dev.aaa1115910.bv.ui.effect.VideoDetailUiEffect
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fDebug
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import dev.aaa1115910.bv.util.formatHourMinSec
import dev.aaa1115910.bv.util.toWanString
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class VideoDetailViewModel(
    private val videoInfoRepository: VideoInfoRepository,
    private val videoDetailRepository: VideoDetailRepository,
    private val userRepository: UserRepository,
    private val favoriteRepository: FavoriteRepository,
    private val likeRepository: LikeRepository,
    private val coinRepository: CoinRepository,
    private val oneClickTripleActionRepository: OneClickTripleActionRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger { }

    private val _uiState = MutableStateFlow(VideoDetailUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<VideoDetailUiEffect>()
    val uiEvent = _uiEffect.asSharedFlow()

    init {
        // 加载设置
        _uiState.update { it.copy(showVideoInfo = Prefs.showVideoInfo) }

        videoInfoRepository.history
            .onEach { newHistory ->
                _uiState.update { currentState ->
                    currentState.copy(
                        lastPlayedCid = newHistory.lastPlayedCid,
                        lastPlayedTime = newHistory.progress
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun init(
        aid: Long,
        fromSeason: Boolean = false,
        fromController: Boolean = false,
        proxyArea: ProxyArea = ProxyArea.MainLand
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (proxyArea != ProxyArea.MainLand) {
                val redirectSuccess = tryRedirectToSeason(aid, proxyArea)
                if (redirectSuccess) return@launch // 如果跳转成功，终止后续加载
            }

            runCatching {
                loadVideoDetail(aid, fromSeason, fromController)
            }.onFailure { e ->
                handleLoadFailure(e, aid)
            }
        }
    }

    fun updateVideoList(sectionIndex: Int) {
        val videoDetail = _uiState.value.videoDetail
        val partVideoList =
            videoDetail?.ugcSeason?.sections?.get(sectionIndex)?.episodes?.mapIndexed { _, episode ->
                VideoListItem(
                    aid = episode.aid,
                    cid = episode.cid,
                    title = episode.title
                )
            }
        videoInfoRepository.updateVideoList(partVideoList ?: emptyList())
    }

    fun updateVideoList(videoListItem: List<VideoListItem>) {
        videoInfoRepository.updateVideoList(videoListItem)
    }

    fun setFollow(follow: Boolean) {
        val userMid = _uiState.value.videoDetail?.author?.mid ?: return

        viewModelScope.launch(Dispatchers.IO) {
            logger.fInfo { "${if (follow) "Add" else "Del"} follow to user $userMid" }

            val result = if (follow) {
                userRepository.followUser(
                    mid = userMid,
                    preferApiType = Prefs.apiType
                )
            } else {
                userRepository.unfollowUser(
                    mid = userMid,
                    preferApiType = Prefs.apiType
                )
            }

            logger.fInfo { "${if (follow) "Add" else "Del"} follow result: $result" }

            updateFollowingState()
        }
    }

    fun updateVideoFavoriteData(folderIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val avid = _uiState.value.videoDetail?.aid
                val favoriteFolders = _uiState.value.favoriteFolders
                require(favoriteFolders.isNotEmpty()) { "Favorite folders not found" }
                require(avid != null) { "Video info is null" }
                logger.info { "Update video av${avid} to favorite folder $folderIds" }

                favoriteRepository.updateVideoToFavoriteFolder(
                    aid = avid,
                    addMediaIds = folderIds,
                    delMediaIds = favoriteFolders.map { it.id } - folderIds.toSet()
                )
            }.onFailure {
                logger.fInfo { "Update video to favorite folder failed: ${it.stackTraceToString()}" }
                _uiEffect.emit(VideoDetailUiEffect.ShowToast(it.message ?: "unknown error"))
            }.onSuccess {
                logger.fInfo { "Update video to favorite folder success" }
                _uiState.update {
                    it.copy(
                        isFavorite = folderIds.isNotEmpty(),
                        videoFavoriteFolderIds = folderIds.toSet()
                    )
                }
            }
        }
    }

    fun addVideoToDefaultFavoriteFolder() {
        val videoFavoriteFolderIds = _uiState.value.videoFavoriteFolderIds
        val defaultFavoriteFolderId = getDefaultFavoriteFolderId()

        updateVideoFavoriteData(
            (videoFavoriteFolderIds + listOfNotNull(defaultFavoriteFolderId)).toList()
        )
    }

    fun updateVideoLiked(like: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentDetail = _uiState.value.videoDetail ?: run {
                logger.warn { "updateVideoLiked failed: videoDetail is null" }
                return@launch
            }

            logger.info { "Check video ${currentDetail.aid} is liked: $like" }
            runCatching {
                likeRepository.updateVideoLiked(
                    like = like,
                    aid = currentDetail.aid,
                    bvid = currentDetail.bvid
                )
            }.onSuccess {
                logger.fInfo { "Update video liked status success" }
                _uiState.update { currentState ->
                    currentState.copy(isLiked = like)
                }
            }.onFailure { throwable ->
                logger.fInfo { "Update video liked status failed: ${throwable.message}" }
                _uiEffect.emit(VideoDetailUiEffect.ShowToast("点赞失败:${throwable.message ?: "unknown error"}"))
            }
        }
    }

    fun sendVideoCoin() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentDetail = _uiState.value.videoDetail ?: run {
                logger.warn { "sendVideoCoin failed: videoDetail is null" }
                return@launch
            }

            runCatching {
                coinRepository.sendVideoCoin(
                    aid = currentDetail.aid,
                    bvid = currentDetail.bvid
                )
            }.onSuccess {
                logger.fInfo { "Send video coin success" }
                _uiState.update { currentState ->
                    currentState.copy(isCoined = true)
                }
            }.onFailure { throwable ->
                logger.fInfo { "Send video coin failed: ${throwable.message}" }
                _uiEffect.emit(VideoDetailUiEffect.ShowToast("投币失败:${throwable.message ?: "unknown error"}"))
            }
        }
    }

    fun sendVideoOneClickTripleAction() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentDetail = _uiState.value.videoDetail ?: run {
                logger.warn { "sendVideoOneClickTripleAction failed: videoDetail is null" }
                return@launch
            }

            runCatching {
                oneClickTripleActionRepository.sendVideoOneClickTripleAction(
                    aid = currentDetail.aid,
                    bvid = currentDetail.bvid
                )
            }.onSuccess { data ->
                logger.fInfo { "Send video one click triple action success" }
                if (data != null) {
                    _uiState.update { currentState ->
                        val defaultFolderId = getDefaultFavoriteFolderId()

                        currentState.copy(
                            isLiked = data.like,
                            isCoined = data.coin,
                            isFavorite = data.fav,
                            videoFavoriteFolderIds = defaultFolderId?.let {
                                currentState.videoFavoriteFolderIds + it
                            } ?: currentState.videoFavoriteFolderIds
                        )
                    }
                    _uiEffect.emit(VideoDetailUiEffect.ShowToast("一键三连"))
                }
            }.onFailure { throwable ->
                logger.fInfo { "Send video one click triple action failed: ${throwable.message}" }
                _uiEffect.emit(VideoDetailUiEffect.ShowToast("一键三连失败:${throwable.message ?: "unknown error"}"))
            }
        }
    }

    private suspend fun loadVideoDetail(aid: Long, fromSeason: Boolean, fromController: Boolean) {
        val shouldDirectlyPlay = !fromController && (fromSeason || !_uiState.value.showVideoInfo)

        _uiState.update { it.copy(fromSeason = fromSeason, fromController = fromController) }

        logger.fInfo { "Load detail: [avid=$aid, preferApiType=${Prefs.apiType.name}, autoPlay=$shouldDirectlyPlay]" }

        val videoDetail = videoDetailRepository.getVideoDetail(
            aid = aid,
            preferApiType = Prefs.apiType
        )

        _uiState.update {
            it.copy(
                videoDetail = videoDetail,
                lastPlayedCid = videoDetail.history.lastPlayedCid,
                lastPlayedTime = videoDetail.history.progress,
                isLiked = videoDetail.userActions.like,
                isCoined = videoDetail.userActions.coin,
                isFavorite = videoDetail.userActions.favorite,
                loadingState = if (shouldDirectlyPlay) VideoInfoState.Loading else VideoInfoState.Success
            )
        }

        logger.fInfo { "Load video av$aid success" }

        if (shouldDirectlyPlay) {
            val targetCid = videoDetail.history.lastPlayedCid.takeIf { it != 0L }
                ?: videoDetail.pages.firstOrNull()?.cid

            if (targetCid != null) {
                logger.fInfo { "Directly play video: [av:$aid, cid:$targetCid]" }
                _uiEffect.emit(VideoDetailUiEffect.DirectlyPlay(targetCid))
                return
            } else {
                logger.fWarn { "Auto play failed: CID not found." }
                _uiState.update {
                    it.copy(
                        loadingState = VideoInfoState.Error,
                        errorTip = "视频不存在"
                    )
                }
                return
            }
        }

        updateFollowingState()
        updateRelatedVideos()
        if (Prefs.isLogin) {
            fetchFavoriteData(aid)
        }
    }

    private suspend fun handleLoadFailure(e: Throwable, aid: Long) {
        val errorMessage = e.localizedMessage ?: "未知错误"
        logger.fInfo { "Get video info failed: ${e.stackTraceToString()}" }

        val isVideoNotFound = when (Prefs.apiType) {
            ApiType.Web -> errorMessage == "啥都木有"
            ApiType.App -> errorMessage == "访问权限不足"
            else -> false
        }

        if (!isVideoNotFound || !Prefs.enableProxy) {
            _uiState.update {
                it.copy(
                    errorTip = errorMessage,
                    loadingState = VideoInfoState.Error
                )
            }
            return
        }

        logger.fInfo { "Trying get video info through proxy server" }
        val fallbackSuccess = tryRedirectToSeason(aid, ProxyArea.HongKong)

        if (!fallbackSuccess) {
            _uiState.update {
                it.copy(errorTip = "视频不存在", loadingState = VideoInfoState.Error)
            }
        }
    }

    private suspend fun tryRedirectToSeason(aid: Long, area: ProxyArea): Boolean {
        return runCatching {
            val seasonId = BiliPlusHttpApi.getSeasonIdByAvid(aid)
            logger.info { "Get season id from biliplus: $seasonId ($area)" }

            if (seasonId != null) {
                logger.fInfo { "Redirect to season $seasonId" }
                _uiEffect.emit(
                    VideoDetailUiEffect.LaunchSeasonInfoActivity(
                        seasonId = seasonId,
                        proxyArea = area
                    )
                )
                true
            } else {
                false
            }
        }.getOrElse { e ->
            logger.fWarn { "Redirect failed: ${e.stackTraceToString()}" }
            false
        }
    }

    private fun fetchFavoriteData(avid: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                favoriteRepository.getAllFavoriteFolderMetadataList(
                    mid = Prefs.uid,
                    rid = avid,
                    preferApiType = Prefs.apiType
                )
            }.onSuccess { result ->
                _uiState.update { it.copy(favoriteFolders = result) }
                logger.fDebug { "Update favoriteFolders size: ${result.size}" }
                val videoInFavoriteFolderIdsResult = result
                    .filter { it.videoInThisFav }.map { it.id }
                _uiState.update { it.copy(videoFavoriteFolderIds = videoInFavoriteFolderIdsResult.toSet()) }
            }
        }
    }

    private fun getDefaultFavoriteFolderId(): Long? {
        val defaultFavoriteFolder =
            _uiState.value.favoriteFolders.firstOrNull { it.title == "默认收藏夹" }
        return defaultFavoriteFolder?.id
    }

    private fun updateFollowingState() {
        viewModelScope.launch(Dispatchers.IO) {
            val userMid = _uiState.value.videoDetail?.author?.mid ?: -1
            logger.fInfo { "Checking is following user $userMid" }
            val isFollowing = userRepository.checkIsFollowing(
                mid = userMid,
                preferApiType = Prefs.apiType
            )
            logger.fInfo { "Following user result: $isFollowing" }
            _uiState.update { it.copy(isFollowingUp = isFollowing ?: false) }
        }
    }

    private fun updateRelatedVideos() {
        logger.fInfo { "Start update relate video" }
        val videoDetail = _uiState.value.videoDetail
        val relateVideoCardDataList = videoDetail?.relatedVideos?.map {
            VideoCardData(
                avid = it.aid,
                cid = it.cid,
                title = it.title,
                cover = it.cover,
                upName = it.author?.name ?: "",
                upMid = it.author?.mid,
                timeString = (it.duration * 1000L).formatHourMinSec(),
                playString = it.view.toWanString(),
                danmakuString = it.danmaku.toWanString(),
                jumpToSeason = it.jumpToSeason,
                epId = it.epid,
            )
        } ?: emptyList()

        _uiState.update { it.copy(relatedVideos = relateVideoCardDataList) }
        videoInfoRepository.updateRelatedVideos(relateVideoCardDataList)

        logger.fInfo { "Update ${relateVideoCardDataList.size} relate videos" }
    }
}

enum class VideoInfoState {
    Loading,
    Success,
    Error
}