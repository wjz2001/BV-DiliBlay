package dev.aaa1115910.biliapi.metrics

data class VideoMetricsRequest(
    val aid: Long? = null,
    val bvid: String? = null,
    val cid: Long? = null,
    val refreshReason: CanonicalRefreshReason = CanonicalRefreshReason.INITIAL_LOAD,
    val allowStale: Boolean = true,
    val priority: VideoMetricsPriority = VideoMetricsPriority.VISIBLE,
    val timeoutMs: Long? = 1_500L
) {
    init {
        require(aid != null || !bvid.isNullOrBlank()) {
            "aid and bvid cannot both be empty"
        }
    }
}

enum class VideoMetricsPriority {
    VISIBLE,
    PREFETCH,
    BACKGROUND
}

data class VideoMetricsIdentity(
    val aid: Long? = null,
    val bvid: String? = null,
    val cid: Long? = null
)

data class VideoMetricsRuntimeMeta(
    val sourceId: String,
    val contextKey: String,
    val statKey: String,
    val aliasKey: String? = null,
    val inFlightShared: Boolean,
    val degraded: Boolean,
    val batchGroup: VideoMetricsBatchGroup,
    val latencyMs: Long,
    val failureCode: String? = null,
    val failureMessage: String? = null
)

enum class VideoMetricsBatchGroup {
    INTERACTIVE,
    DEFERRED
}

data class VideoMetricsEnvelope(
    val identity: VideoMetricsIdentity,
    val snapshot: CanonicalMetricsSnapshot,
    val runtime: VideoMetricsRuntimeMeta
)

data class VideoMetricsPrefetchOptions(
    val firstScreenCount: Int = 15,
    val deferredBatchSize: Int = 6,
    val deferredStartDelayMs: Long = 500L,
    val interBatchDelayMs: Long = 250L
)

data class VideoMetricsFacadeConfig(
    val cacheTtlMs: Long = 120_000L,
    val staleMaxAgeMs: Long = 600_000L,
    val maxDegradeAgeMs: Long = 1_800_000L,
    val nextRefreshOffsetMs: Long = 90_000L,
    val maxPrefetchRequests: Int = 60,
    val rateLimitCooldownMs: Long = 30_000L
) {
    init {
        require(cacheTtlMs >= 0L) { "cacheTtlMs must be >= 0" }
        require(staleMaxAgeMs >= cacheTtlMs) { "staleMaxAgeMs must be >= cacheTtlMs" }
        require(maxDegradeAgeMs >= staleMaxAgeMs) { "maxDegradeAgeMs must be >= staleMaxAgeMs" }
        require(nextRefreshOffsetMs >= 0L) { "nextRefreshOffsetMs must be >= 0" }
        require(maxPrefetchRequests > 0) { "maxPrefetchRequests must be > 0" }
        require(rateLimitCooldownMs >= 0L) { "rateLimitCooldownMs must be >= 0" }
    }
}

interface VideoMetricsFacade {
    suspend fun load(request: VideoMetricsRequest): VideoMetricsEnvelope

    suspend fun prefetch(
        requests: List<VideoMetricsRequest>,
        options: VideoMetricsPrefetchOptions = VideoMetricsPrefetchOptions()
    )

    fun invalidate(identity: VideoMetricsIdentity)
}
