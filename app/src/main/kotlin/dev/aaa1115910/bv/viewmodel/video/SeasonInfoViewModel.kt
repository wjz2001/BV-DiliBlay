package dev.aaa1115910.bv.viewmodel.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.video.season.SeasonDetail
import dev.aaa1115910.biliapi.repositories.VideoDetailRepository
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class SeasonInfoViewModel(
    private val videoDetailRepository: VideoDetailRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger { }

    fun updateSeasonData(
        seasonId: Int?,
        epId: Int?,
        proxyArea: ProxyArea,
        onSuccess: (SeasonDetail) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                videoDetailRepository.getPgcVideoDetail(
                    seasonId = seasonId,
                    epid = epId,
                    preferApiType = if (proxyArea != ProxyArea.MainLand) ApiType.App else Prefs.apiType
                )
            }.onSuccess { data ->
                withContext(Dispatchers.Main) { onSuccess(data) }
            }.onFailure {
                logger.fInfo { "Get season info failed: ${it.stackTraceToString()}" }
                withContext(Dispatchers.Main) { onFailure(it) }
            }
        }
    }

    fun updateHistoryAfterBack(
        seasonId: Int?,
        epId: Int?,
        proxyArea: ProxyArea,
        onSuccess: (SeasonDetail.UserStatus.Progress?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            delay(200)
            runCatching {
                videoDetailRepository.getPgcVideoDetail(
                    seasonId = seasonId,
                    epid = epId,
                    preferApiType = if (proxyArea != ProxyArea.MainLand) ApiType.App else Prefs.apiType
                ).userStatus.progress
            }.onSuccess { progress ->
                withContext(Dispatchers.Main) { onSuccess(progress) }
                logger.info { "update user status progress: $progress" }
            }.onFailure {
                logger.fInfo { "update user status progress failed: ${it.stackTraceToString()}" }
            }
        }
    }
}
