package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.http.BiliHttpApi
import dev.aaa1115910.biliapi.http.entity.video.OneClickTripleAction

class OneClickTripleActionRepository(private val authRepository: AuthRepository) {
    suspend fun sendVideoOneClickTripleAction(
        aid: Long,
        bvid: String? = null
    ): OneClickTripleAction? {
        val (success, message, data)
                = BiliHttpApi.sendVideoOneClickTripleAction(
            avid = aid,
            bvid = bvid, csrf = authRepository.biliJct ?: "",
            sessData = authRepository.sessionData!!,
        )
        if (!success) throw Exception("投币失败：$message")
        return data
    }
}