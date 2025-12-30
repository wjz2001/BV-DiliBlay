package dev.aaa1115910.biliapi.repositories

import bilibili.app.interfaces.v1.HistoryGrpcKt
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.user.ToViewData
import dev.aaa1115910.biliapi.http.BiliHttpApi
import org.koin.core.annotation.Single

@Single
class ToViewRepository(
    private val authRepository: AuthRepository,
    private val channelRepository: ChannelRepository
) {
    private val historyStub
        get() = runCatching {
            HistoryGrpcKt.HistoryCoroutineStub(channelRepository.defaultChannel!!)
        }.getOrNull()

    suspend fun getToView(
        cursor: Long,
        preferApiType: ApiType = ApiType.Web
    ): ToViewData {
        return when (preferApiType) {
            ApiType.Web -> {
                val data = BiliHttpApi.getToView(
                    // viewAt = cursor,
                    sessData = authRepository.sessionData!!,
                ).getResponseData()
                ToViewData.fromToViewResponse(data)
            }

            ApiType.App -> {
                val data = BiliHttpApi.getToView(
                    // viewAt = cursor,
                    sessData = authRepository.sessionData!!,
                ).getResponseData()
                ToViewData.fromToViewResponse(data)
            }
        }
    }

    suspend fun addToView(
        aid: Long,
        bvid: String? = null,
    ) {
        val (success, message) = BiliHttpApi.addToView(
            avid = aid,
            bvid = bvid,
            csrf = authRepository.biliJct ?: "",
            sessData = authRepository.sessionData!!,
        )
        if (!success) throw Exception("添加到稍后再看失败：$message")
    }

    suspend fun delToView(
        aid: Long,
        viewed: Boolean = false
    ) {
        val (success, message) = BiliHttpApi.delToView(
            viewed = viewed,
            avid = aid,
            csrf = authRepository.biliJct ?: "",
            sessData = authRepository.sessionData!!,
        )
        if (!success) throw Exception("删除稍后再看失败：$message")
    }
}
