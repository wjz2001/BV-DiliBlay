package dev.aaa1115910.bv.util

import dev.aaa1115910.biliapi.http.BiliHttpApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val BILIBILI_VIDEO_LINK_REGEX = Regex(
    "((?:https?://)?(?:[a-zA-Z0-9-]+\\.)*bilibili\\.com/video/([bB][vV][A-Za-z0-9]{10}|[aA][vV]\\d+)[a-zA-Z0-9\\-._~:/?#@!$&*+=%]*)"
)

private val VIDEO_ID_IN_URL_REGEX = Regex("/video/([bB][vV][A-Za-z0-9]{10}|[aA][vV]\\d+)")
private val PURE_BV_REGEX = Regex("[bB][vV][A-Za-z0-9]{10}")
private val PURE_AV_REGEX = Regex("[aA][vV]\\d+")

private val TRAILING_NOISE = setOf('.', ',', '!', '?', ';', ':', '"', '\'', ')', ']', '}')

sealed class VideoLinkTextToken {
    data class Text(val text: String) : VideoLinkTextToken()
    data class VideoLink(val data: VideoLinkToken) : VideoLinkTextToken()
}

data class VideoLinkToken(
    val rawUrl: String,
    val cleanedUrl: String,
    val videoId: String
)

data class ResolvedVideoLink(
    val aid: Long,
    val cid: Long,
    val title: String
)

private fun normalizeVideoId(videoId: String): String? {
    return when {
        PURE_BV_REGEX.matches(videoId) -> "BV${videoId.drop(2)}"
        PURE_AV_REGEX.matches(videoId) -> "av${videoId.drop(2)}"
        else -> null
    }
}

private fun parsePureVideoId(text: String): String? {
    return normalizeVideoId(text)
}

fun trimTrailingNoise(rawUrl: String): String {
    var s = rawUrl.trim()
    while (s.isNotEmpty()) {
        val c = s.last()
        if (c !in TRAILING_NOISE) break

        val keepBracket = when (c) {
            ')' -> s.count { it == '(' } >= s.count { it == ')' }
            ']' -> s.count { it == '[' } >= s.count { it == ']' }
            '}' -> s.count { it == '{' } >= s.count { it == '}' }
            else -> false
        }
        if (keepBracket) break
        s = s.dropLast(1)
    }
    return s
}

fun splitTextByVideoLink(
    text: String,
    skipDetect: Boolean = false
): List<VideoLinkTextToken> {
    if (text.isEmpty()) return emptyList()
    if (skipDetect) return listOf(VideoLinkTextToken.Text(text))

    parsePureVideoId(text)?.let { normalizedVideoId ->
        return listOf(
            VideoLinkTextToken.VideoLink(
                VideoLinkToken(
                    rawUrl = text,
                    cleanedUrl = text,
                    videoId = normalizedVideoId
                )
            )
        )
    }

    val result = mutableListOf<VideoLinkTextToken>()
    var cursor = 0
    BILIBILI_VIDEO_LINK_REGEX.findAll(text).forEach { m ->
        val start = m.range.first
        val end = m.range.last + 1
        if (start > cursor) {
            result += VideoLinkTextToken.Text(text.substring(cursor, start))
        }

        val rawUrl = m.value
        val cleanedUrl = trimTrailingNoise(rawUrl)
        val videoId = (
            VIDEO_ID_IN_URL_REGEX.find(cleanedUrl)?.groupValues?.getOrNull(1)
                ?: m.groupValues.getOrNull(2)
            )?.let(::normalizeVideoId)

        result += if (!videoId.isNullOrBlank()) {
            VideoLinkTextToken.VideoLink(
                VideoLinkToken(
                    rawUrl = rawUrl,
                    cleanedUrl = cleanedUrl,
                    videoId = videoId
                )
            )
        } else {
            VideoLinkTextToken.Text(rawUrl)
        }
        cursor = end
    }

    if (cursor < text.length) {
        result += VideoLinkTextToken.Text(text.substring(cursor))
    }
    return result
}

suspend fun resolveVideoLink(
    token: VideoLinkToken,
    sessData: String? = Prefs.sessData.takeIf { it.isNotBlank() }
): ResolvedVideoLink? {
    return withContext(Dispatchers.IO) {
        val id = normalizeVideoId(token.videoId) ?: return@withContext null

        runCatching {
            val response = if (id.startsWith("av")) {
                val aid = id.drop(2).toLong()
                BiliHttpApi.getVideoInfo(av = aid, sessData = sessData)
            } else {
                BiliHttpApi.getVideoInfo(bv = id, sessData = sessData)
            }
            val data = response.getResponseData()
            ResolvedVideoLink(
                aid = data.aid,
                cid = data.cid,
                title = data.title
            )
        }.getOrNull()
    }
}
