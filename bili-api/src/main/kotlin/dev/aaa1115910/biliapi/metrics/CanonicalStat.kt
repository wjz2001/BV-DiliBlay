package dev.aaa1115910.biliapi.metrics

enum class CanonicalSource {
    API,
    DETAIL_SUPPLEMENT,
    CACHE,
    MIXED,
    UNKNOWN
}

enum class CanonicalPrecision {
    EXACT,
    APPROX,
    UNKNOWN
}

enum class CanonicalCacheStatus {
    MISS,
    HIT,
    STALE,
    REFRESHED,
    UNKNOWN
}

enum class CanonicalRefreshReason {
    INITIAL_LOAD,
    CACHE_MISS,
    CACHE_EXPIRED,
    MANUAL_REFRESH,
    DETAIL_SUPPLEMENT,
    RETRY,
    UNKNOWN
}

data class CanonicalStat(
    val view: Long?,
    val danmaku: Long?,
    val reply: Long?,
    val favorite: Long?,
    val coin: Long?,
    val share: Long?,
    val like: Long?,
    val durationSec: Int?,
    val isVipVideo: Boolean?,
    val isPaidVideo: Boolean?,
    val isVerticalVideo: Boolean?,
    val source: CanonicalSource,
    val updatedAt: Long,
    val precision: CanonicalPrecision,
    val cacheStatus: CanonicalCacheStatus,
    val ttlMs: Long?,
    val expireAt: Long?,
    val nextRefreshAt: Long?,
    val refreshReason: CanonicalRefreshReason,
    val fieldSources: Map<String, CanonicalSource>?
)

data class StatEnvelope(
    val sourceId: String,
    val aid: Long,
    val bvid: String,
    val cid: Long,
    val stat: CanonicalStat
)

typealias CanonicalMetricsSnapshot = CanonicalStat
