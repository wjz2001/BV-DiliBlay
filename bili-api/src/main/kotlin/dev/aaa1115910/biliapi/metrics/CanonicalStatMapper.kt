package dev.aaa1115910.biliapi.metrics

import dev.aaa1115910.biliapi.http.entity.video.VideoDetail
import dev.aaa1115910.biliapi.http.entity.video.VideoInfo
import java.math.BigDecimal
import java.math.RoundingMode

data class RawCanonicalStatFields(
    val view: Any? = null,
    val danmaku: Any? = null,
    val reply: Any? = null,
    val favorite: Any? = null,
    val coin: Any? = null,
    val share: Any? = null,
    val like: Any? = null,
    val durationSec: Any? = null,
    val isVipVideo: Boolean? = null,
    val isPaidVideo: Boolean? = null,
    val isVerticalVideo: Boolean? = null
)

object CanonicalStatMapper {
    const val WEB_DETAIL_SOURCE_ID: String = "SRC-WEB-DETAIL"

    fun fromWebDetail(
        detail: VideoDetail,
        updatedAt: Long = System.currentTimeMillis(),
        cacheStatus: CanonicalCacheStatus = CanonicalCacheStatus.MISS,
        refreshReason: CanonicalRefreshReason = CanonicalRefreshReason.DETAIL_SUPPLEMENT
    ): StatEnvelope = fromWebView(
        view = detail.view,
        updatedAt = updatedAt,
        cacheStatus = cacheStatus,
        refreshReason = refreshReason
    )

    fun fromWebView(
        view: VideoInfo,
        updatedAt: Long = System.currentTimeMillis(),
        cacheStatus: CanonicalCacheStatus = CanonicalCacheStatus.MISS,
        refreshReason: CanonicalRefreshReason = CanonicalRefreshReason.DETAIL_SUPPLEMENT
    ): StatEnvelope {
        val stat = buildCanonicalStat(
            fields = RawCanonicalStatFields(
                view = view.stat.rawView,
                danmaku = view.stat.danmaku,
                reply = view.stat.reply,
                favorite = view.stat.favorite,
                coin = view.stat.coin,
                share = view.stat.share,
                like = view.stat.like,
                durationSec = view.duration,
                isVipVideo = null,
                isPaidVideo = VideoAccessClassifier.rawPaidVideo(view),
                isVerticalVideo = view.dimension.width < view.dimension.height
            ),
            updatedAt = updatedAt,
            cacheStatus = cacheStatus,
            refreshReason = refreshReason
        )
        return StatEnvelope(
            sourceId = WEB_DETAIL_SOURCE_ID,
            aid = view.aid,
            bvid = view.bvid,
            cid = view.cid,
            stat = stat
        )
    }

    internal fun buildCanonicalStat(
        fields: RawCanonicalStatFields,
        updatedAt: Long,
        cacheStatus: CanonicalCacheStatus,
        refreshReason: CanonicalRefreshReason
    ): CanonicalStat {
        val view = parseCount(fields.view)
        val danmaku = parseCount(fields.danmaku)
        val reply = parseCount(fields.reply)
        val favorite = parseCount(fields.favorite)
        val coin = parseCount(fields.coin)
        val share = parseCount(fields.share)
        val like = parseCount(fields.like)
        val durationSec = parseDurationSec(fields.durationSec)
        val approximate = listOf(view, danmaku, reply, favorite, coin, share, like).any { it.approximate }
        val hasAnyValue = listOf(
            view.value,
            danmaku.value,
            reply.value,
            favorite.value,
            coin.value,
            share.value,
            like.value
        ).any { it != null } || durationSec != null

        return CanonicalStat(
            view = view.value,
            danmaku = danmaku.value,
            reply = reply.value,
            favorite = favorite.value,
            coin = coin.value,
            share = share.value,
            like = like.value,
            durationSec = durationSec,
            isVipVideo = fields.isVipVideo,
            isPaidVideo = fields.isPaidVideo,
            isVerticalVideo = fields.isVerticalVideo,
            source = CanonicalSource.DETAIL_SUPPLEMENT,
            updatedAt = updatedAt,
            precision = when {
                approximate -> CanonicalPrecision.APPROX
                hasAnyValue -> CanonicalPrecision.EXACT
                else -> CanonicalPrecision.UNKNOWN
            },
            cacheStatus = cacheStatus,
            ttlMs = null,
            expireAt = null,
            nextRefreshAt = null,
            refreshReason = refreshReason,
            fieldSources = null
        )
    }

    internal data class ParsedCount(
        val value: Long?,
        val approximate: Boolean
    )

    internal fun parseCount(value: Any?): ParsedCount {
        return when (value) {
            null -> ParsedCount(value = null, approximate = false)
            is Byte -> ParsedCount(value.toLong().takeIf { it >= 0L }, approximate = false)
            is Short -> ParsedCount(value.toLong().takeIf { it >= 0L }, approximate = false)
            is Int -> ParsedCount(value.toLong().takeIf { it >= 0L }, approximate = false)
            is Long -> ParsedCount(value.takeIf { it >= 0L }, approximate = false)
            is Float -> parseCount(value.toString())
            is Double -> parseCount(value.toString())
            is BigDecimal -> {
                if (value.signum() < 0) return ParsedCount(value = null, approximate = false)
                val normalized = value.setScale(0, RoundingMode.DOWN)
                ParsedCount(
                    value = normalized.longValueExactOrNull(),
                    approximate = value.compareTo(normalized) != 0
                )
            }
            is String -> parseCountString(value)
            else -> ParsedCount(value = null, approximate = false)
        }
    }

    internal fun parseDurationSec(value: Any?): Int? {
        return when (value) {
            null -> null
            is Byte -> value.toInt().takeIf { it >= 0 }
            is Short -> value.toInt().takeIf { it >= 0 }
            is Int -> value.takeIf { it >= 0 }
            is Long -> value.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
            is String -> parseDurationString(value)
            else -> null
        }
    }

    private fun parseCountString(raw: String): ParsedCount {
        val normalized = raw.trim()
            .replace('，', ',')
            .replace(",", "")
            .replace('．', '.')
            .replace("播放", "")
            .replace("观看", "")
            .replace("弹幕", "")
            .trim()
        if (normalized.isEmpty()) return ParsedCount(value = null, approximate = false)

        val unit = when {
            normalized.endsWith("万") -> BigDecimal("10000")
            normalized.endsWith("亿") -> BigDecimal("100000000")
            else -> BigDecimal.ONE
        }
        val numberPart = if (unit == BigDecimal.ONE) normalized else normalized.dropLast(1).trim()
        if (numberPart.isEmpty()) return ParsedCount(value = null, approximate = false)
        if (unit == BigDecimal.ONE && !INTEGER_COUNT_REGEX.matches(numberPart)) {
            return ParsedCount(value = null, approximate = false)
        }

        val decimal = numberPart.toBigDecimalOrNull() ?: return ParsedCount(value = null, approximate = false)
        if (decimal.signum() < 0) return ParsedCount(value = null, approximate = false)

        val scaled = decimal.multiply(unit).setScale(0, RoundingMode.DOWN)
        return ParsedCount(
            value = scaled.longValueExactOrNull(),
            approximate = unit != BigDecimal.ONE
        )
    }

    private fun parseDurationString(raw: String): Int? {
        val normalized = raw.trim()
        if (normalized.isEmpty()) return null
        val plainNumber = normalized.toIntOrNull()
        if (plainNumber != null) return plainNumber.takeIf { it >= 0 }

        val parts = normalized.split(":")
        if (parts.size !in 2..3) return null
        val numbers = parts.map { it.trim().toIntOrNull() ?: return null }
        if (numbers.any { it < 0 }) return null

        return if (numbers.size == 3) {
            val (hour, minute, second) = numbers
            hour * 3600 + minute * 60 + second
        } else {
            val (minute, second) = numbers
            minute * 60 + second
        }
    }

    private fun BigDecimal.longValueExactOrNull(): Long? = runCatching { longValueExact() }.getOrNull()

    private val INTEGER_COUNT_REGEX = Regex("^\\d+$")
}
