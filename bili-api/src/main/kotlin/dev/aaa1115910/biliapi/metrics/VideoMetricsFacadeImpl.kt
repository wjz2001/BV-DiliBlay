package dev.aaa1115910.biliapi.metrics

import dev.aaa1115910.biliapi.http.BiliHttpApi
import dev.aaa1115910.biliapi.http.entity.video.PlayUrlData
import dev.aaa1115910.biliapi.http.entity.video.VideoInfo
import dev.aaa1115910.biliapi.repositories.AuthRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

@Single(binds = [VideoMetricsFacade::class])
class VideoMetricsFacadeImpl(
    private val authRepository: AuthRepository,
    private val globalLimiter: VideoMetricsGlobalConcurrencyLimiter
) : VideoMetricsFacade {
    private var remoteFetcher: suspend (aid: Long?, bvid: String?) -> StatEnvelope = { aid, bvid ->
        val sessionData = authRepository.sessionData
        val detail = BiliHttpApi.getVideoDetail(
            av = aid,
            bv = bvid,
            sessData = sessionData ?: ""
        ).getResponseData()
        val isVipVideo = fetchVipVideoFlag(
            view = detail.view,
            sessionData = sessionData,
            dedeUserId = authRepository.mid,
            buvid3 = authRepository.buvid3
        )
        CanonicalStatMapper.fromWebDetail(detail).withResolvedAccessFlags(isVipVideo)
    }
    private var config: VideoMetricsFacadeConfig = VideoMetricsFacadeConfig()
    private var timeProvider: () -> Long = System::currentTimeMillis
    private var detachedScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal constructor(
        authRepository: AuthRepository,
        remoteFetcher: suspend (aid: Long?, bvid: String?) -> StatEnvelope,
        config: VideoMetricsFacadeConfig = VideoMetricsFacadeConfig(),
        timeProvider: () -> Long = System::currentTimeMillis,
        detachedScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        globalLimiter: VideoMetricsGlobalConcurrencyLimiter = VideoMetricsGlobalConcurrencyLimiter()
    ) : this(
        authRepository = authRepository,
        globalLimiter = globalLimiter
    ) {
        this.remoteFetcher = remoteFetcher
        this.config = config
        this.timeProvider = timeProvider
        this.detachedScope = detachedScope
    }

    private data class LoadRecord(
        val envelope: StatEnvelope,
        val degraded: Boolean,
        val failureCode: String? = null,
        val failureMessage: String? = null
    )

    private val logger = LoggerFactory.getLogger(VideoMetricsFacadeImpl::class.java)
    private val singleFlightMutex = Mutex()
    private val inFlightLoads = ConcurrentHashMap<String, Deferred<LoadRecord>>()
    private val aliasIndex = ConcurrentHashMap<String, Long>()
    private val deferredPrefetchMutex = Mutex()
    private val pendingDeferredPrefetches = LinkedHashMap<String, PendingPrefetchRequest>()
    private var deferredPrefetchJob: Deferred<Unit>? = null
    private val cooldownUntilByScopeAndFailureCode = ConcurrentHashMap<String, Long>()
    private val prefetchPlanCounter = AtomicLong(0L)
    private val deferredScheduleCounter = AtomicLong(0L)
    private val deferredDrainCounter = AtomicLong(0L)

    override suspend fun load(request: VideoMetricsRequest): VideoMetricsEnvelope {
        val startedAt = timeProvider()
        val contextKey = VideoMetricsKeys.contextKey(request)
        val aliasKey = request.bvid.normalizeBvidOrNull()?.let(VideoMetricsKeys::aliasKey)
        val batchGroup = request.priority.toBatchGroup()

        val fresh = request.takeUnless { it.refreshReason == CanonicalRefreshReason.MANUAL_REFRESH }
            ?.let {
                VideoStatCache.getFreshOrNull(
                    aid = request.aid,
                    bvid = request.bvid,
                    nowMs = startedAt
                )
            }
        if (fresh != null) {
            debugLog("cache-hit-fresh", request, contextKey)
            return fresh.toFacadeEnvelope(
                request = request,
                contextKey = contextKey,
                aliasKey = aliasKey,
                inFlightShared = false,
                degraded = false,
                batchGroup = batchGroup,
                latencyMs = timeProvider() - startedAt
            )
        }

        val stale = VideoStatCache.getStaleOk(
            aid = request.aid,
            bvid = request.bvid,
            nowMs = startedAt
        )?.takeIf {
            it.stat.cacheStatus == CanonicalCacheStatus.STALE &&
                    startedAt <= (it.stat.updatedAt + config.staleMaxAgeMs)
        }

        if (request.allowStale && stale?.stat?.cacheStatus == CanonicalCacheStatus.STALE) {
            debugLog("cache-hit-stale", request, contextKey)
            triggerRefreshIfNeeded(request)
            return stale.toFacadeEnvelope(
                request = request,
                contextKey = contextKey,
                aliasKey = aliasKey,
                inFlightShared = false,
                degraded = false,
                batchGroup = batchGroup,
                latencyMs = timeProvider() - startedAt
            )
        }

        val loadKeys = resolveLoadKeys(request)
        val sharedTask = singleFlightMutex.withLock {
            loadKeys.firstNotNullOfOrNull { key ->
                inFlightLoads[key]?.takeUnless { it.isCompleted }
            }?.let { existing ->
                SharedTask(existing, joined = true)
            } ?: detachedScope.async {
                fetchAndCache(request)
            }.also { created ->
                loadKeys.forEach { key -> inFlightLoads[key] = created }
            }.let { created ->
                SharedTask(created, joined = false)
            }
        }

        if (sharedTask.joined) {
            debugLog("join-inflight", request, contextKey)
        }

        val record = try {
            sharedTask.deferred.await()
        } finally {
            singleFlightMutex.withLock {
                if (sharedTask.deferred.isCompleted) {
                    removeInFlight(sharedTask.deferred)
                }
            }
        }

        return record.envelope.toFacadeEnvelope(
            request = request,
            contextKey = contextKey,
            aliasKey = aliasKey,
            inFlightShared = sharedTask.joined,
            degraded = record.degraded,
            batchGroup = batchGroup,
            latencyMs = timeProvider() - startedAt,
            failureCode = record.failureCode,
            failureMessage = record.failureMessage
        )
    }

    override suspend fun prefetch(
        requests: List<VideoMetricsRequest>,
        options: VideoMetricsPrefetchOptions
    ) {
        val acceptedRequests = requests.take(config.maxPrefetchRequests)
        if (acceptedRequests.isEmpty()) return

        val prefetchPlanId = prefetchPlanCounter.incrementAndGet()
        val firstScreenCount = max(0, options.firstScreenCount)
        val deferredBatchSize = max(1, options.deferredBatchSize)
        var visibleBudget = firstScreenCount
        val immediate = ArrayList<VideoMetricsRequest>(minOf(firstScreenCount, acceptedRequests.size))
        val deferred = ArrayList<VideoMetricsRequest>(max(0, acceptedRequests.size - firstScreenCount))
        var overflowVisibleCount = 0

        acceptedRequests.forEach { request ->
            when (request.priority) {
                VideoMetricsPriority.VISIBLE if visibleBudget > 0 -> {
                    immediate += request.copy(
                        priority = VideoMetricsPriority.VISIBLE,
                        allowStale = true
                    )
                    visibleBudget--
                }
                VideoMetricsPriority.VISIBLE -> {
                    overflowVisibleCount++
                    deferred += request.copy(
                        priority = VideoMetricsPriority.PREFETCH,
                        allowStale = true
                    )
                }
                else -> {
                    deferred += request.copy(allowStale = true)
                }
            }
        }

        debugPrefetchLog(
            stage = "prefetch-plan",
            fields = arrayOf(
                "planId" to prefetchPlanId,
                "total" to requests.size,
                "accepted" to acceptedRequests.size,
                "dropped" to (requests.size - acceptedRequests.size),
                "immediate" to immediate.size,
                "deferred" to deferred.size,
                "visibleBudget" to firstScreenCount,
                "visibleOverflow" to overflowVisibleCount,
                "deferredBatchSize" to deferredBatchSize,
                "deferredStartDelayMs" to max(0L, options.deferredStartDelayMs),
                "interBatchDelayMs" to max(0L, options.interBatchDelayMs)
            )
        )

        runLimited(
            items = immediate,
            maxParallelism = minOf(immediate.size, globalLimiter.maxConcurrency())
        ) { request ->
            load(request)
        }

        if (deferred.isEmpty()) return

        scheduleDeferredPrefetch(
            prefetchPlanId = prefetchPlanId,
            requests = deferred,
            deferredBatchSize = deferredBatchSize,
            deferredStartDelayMs = max(0L, options.deferredStartDelayMs),
            interBatchDelayMs = max(0L, options.interBatchDelayMs)
        )
    }

    override fun invalidate(identity: VideoMetricsIdentity) {
        identity.bvid.normalizeBvidOrNull()?.let { normalized ->
            aliasIndex.remove(normalized)
        }
        VideoStatCache.invalidate(aid = identity.aid, bvid = identity.bvid)
    }

    private suspend fun fetchAndCache(request: VideoMetricsRequest): LoadRecord {
        val requestKey = resolveLoadKeys(request).first()
        val nowMs = timeProvider()
        val stale = VideoStatCache.getStaleOk(
            aid = request.aid,
            bvid = request.bvid,
            nowMs = nowMs
        )?.takeIf { nowMs <= (it.stat.updatedAt + config.maxDegradeAgeMs) }

        currentCooldown(
            nowMs = nowMs,
            request = request
        )?.let { cooldown ->
            debugLog("network-skipped:${cooldown.failureCode}", request, requestKey)
            val degradedEnvelope = stale ?: buildEmptyEnvelope(request, nowMs)
            return LoadRecord(
                envelope = degradedEnvelope,
                degraded = true,
                failureCode = cooldown.failureCode,
                failureMessage = "cooldown until ${cooldown.untilMs}"
            )
        }

        debugLog("network-start", request, requestKey)

        val networkEnvelope = try {
            globalLimiter.withPermit(request.priority) {
                if (request.timeoutMs != null) {
                    withTimeout(request.timeoutMs) {
                        remoteFetcher(request.aid, request.bvid)
                    }
                } else {
                    remoteFetcher(request.aid, request.bvid)
                }
            }.asFreshNetworkEnvelope(
                refreshReason = request.refreshReason,
                nowMs = timeProvider()
            )
        } catch (_: TimeoutCancellationException) {
            val failureCode = "TIMEOUT"
            val failureMessage = "TimeoutCancellationException"
            debugLog("network-failed:$failureCode", request, requestKey)
            val degradedEnvelope = stale ?: buildEmptyEnvelope(request, nowMs)
            return LoadRecord(
                envelope = degradedEnvelope,
                degraded = true,
                failureCode = failureCode,
                failureMessage = failureMessage
            )
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (throwable: Throwable) {
            val failureCode = throwable.toFailureCode()
            val failureMessage = throwable.toFailureMessage()
            recordCooldownIfNeeded(
                failureCode = failureCode,
                request = request,
                nowMs = timeProvider()
            )
            debugLog("network-failed:$failureCode", request, requestKey)
            val degradedEnvelope = stale ?: buildEmptyEnvelope(request, nowMs)
            return LoadRecord(
                envelope = degradedEnvelope,
                degraded = true,
                failureCode = failureCode,
                failureMessage = failureMessage
            )
        }

        VideoStatCache.put(
            envelope = networkEnvelope,
            ttlMs = config.cacheTtlMs,
            nowMs = networkEnvelope.stat.updatedAt
        )
        networkEnvelope.bvid.normalizeBvidOrNull()?.let { aliasIndex[it] = networkEnvelope.aid }
        debugLog("network-success", request, requestKey)
        return LoadRecord(
            envelope = networkEnvelope,
            degraded = false
        )
    }

    private fun triggerRefreshIfNeeded(request: VideoMetricsRequest) {
        val loadKeys = resolveLoadKeys(request)
        detachedScope.launch {
            val shouldLaunch = singleFlightMutex.withLock {
                val existing = loadKeys.firstNotNullOfOrNull { key ->
                    inFlightLoads[key]?.takeUnless { it.isCompleted }
                }
                if (existing != null) {
                    false
                } else {
                    val created = async { fetchAndCache(request) }
                    loadKeys.forEach { key -> inFlightLoads[key] = created }
                    true
                }
            }
            if (!shouldLaunch) return@launch

            try {
                loadKeys.firstNotNullOfOrNull { key -> inFlightLoads[key] }?.await()
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (throwable: Throwable) {
                val key = loadKeys.firstOrNull() ?: "unknown"
                logger.debug(
                    "video metrics async refresh failed: key={}, error={}",
                    key,
                    throwable.javaClass.simpleName,
                    throwable
                )
            } finally {
                singleFlightMutex.withLock {
                    loadKeys.firstNotNullOfOrNull { key -> inFlightLoads[key] }?.takeIf { it.isCompleted }?.let {
                        removeInFlight(it)
                    }
                }
            }
        }
    }

    private fun scheduleDeferredPrefetch(
        prefetchPlanId: Long,
        requests: List<VideoMetricsRequest>,
        deferredBatchSize: Int,
        deferredStartDelayMs: Long,
        interBatchDelayMs: Long
    ) {
        detachedScope.launch {
            val jobToAwait = deferredPrefetchMutex.withLock {
                val scheduleId = deferredScheduleCounter.incrementAndGet()
                val pendingBefore = pendingDeferredPrefetches.size
                var addedCount = 0
                var replacedCount = 0
                requests.forEach { request ->
                    val key = VideoMetricsKeys.deferredPrefetchKey(request)
                    if (pendingDeferredPrefetches.containsKey(key)) {
                        replacedCount++
                    } else {
                        addedCount++
                    }
                    pendingDeferredPrefetches[key] = PendingPrefetchRequest(
                        request = request,
                        sequence = pendingDeferredPrefetches[key]?.sequence ?: nextPrefetchSequence()
                    )
                }
                val pendingAfter = pendingDeferredPrefetches.size
                val debounceRestarted = deferredPrefetchJob?.isActive == true

                deferredPrefetchJob?.cancel()
                debugPrefetchLog(
                    stage = "prefetch-deferred-scheduled",
                    fields = arrayOf(
                        "planId" to prefetchPlanId,
                        "scheduleId" to scheduleId,
                        "incoming" to requests.size,
                        "added" to addedCount,
                        "merged" to replacedCount,
                        "pendingBefore" to pendingBefore,
                        "pendingAfter" to pendingAfter,
                        "debounceRestarted" to debounceRestarted,
                        "deferredStartDelayMs" to deferredStartDelayMs,
                        "deferredBatchSize" to deferredBatchSize,
                        "interBatchDelayMs" to interBatchDelayMs
                    )
                )
                detachedScope.async {
                    delay(deferredStartDelayMs)
                    drainDeferredPrefetches(
                        scheduleId = scheduleId,
                        deferredBatchSize = deferredBatchSize,
                        interBatchDelayMs = interBatchDelayMs
                    )
                }.also { deferredPrefetchJob = it }
            }

            try {
                jobToAwait.await()
            } catch (_: CancellationException) {
            }
        }
    }

    private suspend fun drainDeferredPrefetches(
        scheduleId: Long,
        deferredBatchSize: Int,
        interBatchDelayMs: Long
    ) {
        val drainId = deferredDrainCounter.incrementAndGet()
        val drained = deferredPrefetchMutex.withLock {
            pendingDeferredPrefetches.values
                .sortedBy { it.sequence }
                .map { it.request }
                .also { pendingDeferredPrefetches.clear() }
        }
        if (drained.isEmpty()) return

        val deferredBatches = drained.chunked(deferredBatchSize)
        debugPrefetchLog(
            stage = "prefetch-deferred-drain-start",
            fields = arrayOf(
                "scheduleId" to scheduleId,
                "drainId" to drainId,
                "total" to drained.size,
                "batchCount" to deferredBatches.size,
                "deferredBatchSize" to deferredBatchSize,
                "interBatchDelayMs" to interBatchDelayMs
            )
        )
        deferredBatches.forEachIndexed { index, batch ->
            debugPrefetchLog(
                stage = "prefetch-deferred-batch",
                fields = arrayOf(
                    "scheduleId" to scheduleId,
                    "drainId" to drainId,
                    "batchIndex" to (index + 1),
                    "batchCount" to deferredBatches.size,
                    "batchSize" to batch.size
                )
            )
            runLimited(
                items = batch,
                maxParallelism = minOf(batch.size, globalLimiter.maxConcurrency())
            ) { request ->
                load(request)
            }
            if (index != deferredBatches.lastIndex && interBatchDelayMs > 0L) {
                delay(interBatchDelayMs)
            }
        }
        debugPrefetchLog(
            stage = "prefetch-deferred-drain-finish",
            fields = arrayOf(
                "scheduleId" to scheduleId,
                "drainId" to drainId,
                "total" to drained.size,
                "batchCount" to deferredBatches.size
            )
        )
    }

    private fun resolveLoadKeys(request: VideoMetricsRequest): List<String> {
        val keys = linkedSetOf<String>()
        request.aid?.let { keys += VideoMetricsKeys.statKey(it) }

        val normalizedBvid = request.bvid.normalizeBvidOrNull()
        if (normalizedBvid != null) {
            aliasIndex[normalizedBvid]?.let { aliasedAid -> keys += VideoMetricsKeys.statKey(aliasedAid) }
            keys += VideoMetricsKeys.reqBvidKey(normalizedBvid)
        }

        if (keys.isEmpty()) error("aid and bvid cannot both be empty")
        return keys.toList()
    }

    private fun removeInFlight(deferred: Deferred<LoadRecord>) {
        inFlightLoads.entries.removeAll { it.value === deferred }
    }

    private suspend fun fetchVipVideoFlag(
        view: VideoInfo,
        sessionData: String?,
        dedeUserId: Long?,
        buvid3: String?
    ): Boolean? {
        val cid = view.cid.takeIf { it > 0L } ?: return null
        val playUrlData = runCatching {
            if (view.isOgv || view.redirectUrl != null || view.rights.pay == 1) {
                BiliHttpApi.getPgcVideoPlayUrlV2(
                    av = view.aid,
                    bv = view.bvid,
                    cid = cid,
                    qn = 127,
                    fnval = 4048,
                    fnver = 0,
                    fourk = 1,
                    sessData = sessionData,
                    buvid3 = buvid3
                ).getResponseData().videoInfo
            } else {
                BiliHttpApi.getVideoPlayUrl(
                    av = view.aid,
                    bv = view.bvid,
                    cid = cid,
                    qn = 127,
                    fnval = 4048,
                    fnver = 0,
                    fourk = 1,
                    sessData = sessionData,
                    dedeUserID = dedeUserId
                ).getResponseData()
            }
        }.getOrNull() ?: return null
        return VideoAccessClassifier.inferVipVideo(playUrlData)
    }

    private fun StatEnvelope.withResolvedAccessFlags(
        isVipVideo: Boolean?
    ): StatEnvelope {
        val resolvedFlags = VideoAccessClassifier.resolveAccessFlags(
            rawPaidVideo = stat.isPaidVideo,
            isVipVideo = isVipVideo
        )
        return copy(
            stat = stat.copy(
                isVipVideo = resolvedFlags.isVipVideo,
                isPaidVideo = resolvedFlags.isPaidVideo
            )
        )
    }

    private fun StatEnvelope.asFreshNetworkEnvelope(
        refreshReason: CanonicalRefreshReason,
        nowMs: Long
    ): StatEnvelope {
        return copy(
            bvid = bvid.normalizeBvidOrNull() ?: bvid,
            stat = stat.copy(
                source = CanonicalSource.API,
                cacheStatus = CanonicalCacheStatus.REFRESHED,
                updatedAt = nowMs,
                ttlMs = config.cacheTtlMs,
                expireAt = nowMs + config.cacheTtlMs,
                nextRefreshAt = nowMs + config.nextRefreshOffsetMs,
                refreshReason = refreshReason
            )
        )
    }

    private fun buildEmptyEnvelope(
        request: VideoMetricsRequest,
        nowMs: Long
    ): StatEnvelope {
        val normalizedBvid = request.bvid.normalizeBvidOrNull().orEmpty()
        return StatEnvelope(
            sourceId = CanonicalStatMapper.WEB_DETAIL_SOURCE_ID,
            aid = request.aid ?: 0L,
            bvid = normalizedBvid,
            cid = request.cid ?: 0L,
            stat = CanonicalStat(
                view = null,
                danmaku = null,
                reply = null,
                favorite = null,
                coin = null,
                share = null,
                like = null,
                durationSec = null,
                isVipVideo = null,
                isPaidVideo = null,
                isVerticalVideo = null,
                source = CanonicalSource.UNKNOWN,
                updatedAt = nowMs,
                precision = CanonicalPrecision.UNKNOWN,
                cacheStatus = CanonicalCacheStatus.MISS,
                ttlMs = null,
                expireAt = null,
                nextRefreshAt = null,
                refreshReason = request.refreshReason,
                fieldSources = null
            )
        )
    }

    private fun StatEnvelope.toFacadeEnvelope(
        request: VideoMetricsRequest,
        contextKey: String,
        aliasKey: String?,
        inFlightShared: Boolean,
        degraded: Boolean,
        batchGroup: VideoMetricsBatchGroup,
        latencyMs: Long,
        failureCode: String? = null,
        failureMessage: String? = null
    ): VideoMetricsEnvelope {
        val statKey = VideoMetricsKeys.statKey(aid.takeIf { it > 0L } ?: (request.aid ?: 0L))
        return VideoMetricsEnvelope(
            identity = VideoMetricsIdentity(
                aid = aid.takeIf { it > 0L } ?: request.aid,
                bvid = bvid.takeIf { it.isNotBlank() } ?: request.bvid.normalizeBvidOrNull(),
                cid = cid.takeIf { it > 0L } ?: request.cid
            ),
            snapshot = stat,
            runtime = VideoMetricsRuntimeMeta(
                sourceId = sourceId,
                contextKey = contextKey,
                statKey = statKey,
                aliasKey = aliasKey,
                inFlightShared = inFlightShared,
                degraded = degraded,
                batchGroup = batchGroup,
                latencyMs = latencyMs,
                failureCode = failureCode,
                failureMessage = failureMessage
            )
        )
    }

    private fun VideoMetricsPriority.toBatchGroup(): VideoMetricsBatchGroup {
        return when (this) {
            VideoMetricsPriority.VISIBLE -> VideoMetricsBatchGroup.INTERACTIVE
            VideoMetricsPriority.PREFETCH,
            VideoMetricsPriority.BACKGROUND -> VideoMetricsBatchGroup.DEFERRED
        }
    }

    private fun nextPrefetchSequence(): Long {
        return prefetchSequenceCounter++
    }

    private suspend fun <T, R> runLimited(
        items: List<T>,
        maxParallelism: Int,
        block: suspend (T) -> R
    ): List<R> {
        if (items.isEmpty()) return emptyList()
        val chunkSize = max(1, maxParallelism)
        return items.chunked(chunkSize).flatMap { chunk ->
            coroutineScope {
                chunk.map { item ->
                    async { block(item) }
                }.awaitAll()
            }
        }
    }

    private fun Throwable.toFailureCode(): String {
        return when (this) {
            is VideoMetricsRateLimitException -> {
                when (statusCode) {
                    412 -> "HTTP_412"
                    429 -> "HTTP_429"
                    else -> "HTTP_$statusCode"
                }
            }
            is HttpRequestTimeoutException -> "TIMEOUT"
            is ResponseException -> {
                when (response.status.value) {
                    412 -> "HTTP_412"
                    429 -> "HTTP_429"
                    in 500..599 -> "HTTP_5XX"
                    else -> "HTTP_${response.status.value}"
                }
            }
            else -> "NETWORK"
        }
    }

    private fun Throwable.toFailureMessage(): String {
        val message = message?.trim().orEmpty()
        return if (message.isBlank()) {
            javaClass.simpleName
        } else {
            "${javaClass.simpleName}: ${message.take(160)}"
        }
    }

    private fun recordCooldownIfNeeded(
        failureCode: String,
        request: VideoMetricsRequest,
        nowMs: Long
    ) {
        if (config.rateLimitCooldownMs <= 0L) return
        if (failureCode != "HTTP_412" && failureCode != "HTTP_429") return
        resolveCooldownScopeKeys(request).forEach { scopeKey ->
            cooldownUntilByScopeAndFailureCode[buildCooldownKey(scopeKey, failureCode)] =
                nowMs + config.rateLimitCooldownMs
        }
    }

    private fun currentCooldown(
        nowMs: Long,
        request: VideoMetricsRequest
    ): Cooldown? {
        val scopePrefixes = resolveCooldownScopeKeys(request)
            .map { it + COOLDOWN_KEY_SEPARATOR }
        return cooldownUntilByScopeAndFailureCode.entries
            .mapNotNull { (cooldownKey, untilMs) ->
                if (scopePrefixes.none { cooldownKey.startsWith(it) }) return@mapNotNull null
                val failureCode = cooldownKey.substringAfter(COOLDOWN_KEY_SEPARATOR, missingDelimiterValue = "")
                if (failureCode.isEmpty()) return@mapNotNull null
                if (untilMs > nowMs) {
                    Cooldown(failureCode = failureCode, untilMs = untilMs)
                } else {
                    cooldownUntilByScopeAndFailureCode.remove(cooldownKey, untilMs)
                    null
                }
            }
            .maxByOrNull { it.untilMs }
    }

    private fun resolveCooldownScopeKeys(request: VideoMetricsRequest): List<String> {
        val loadKeys = resolveLoadKeys(request)
        val statKey = loadKeys.firstOrNull { it.startsWith(STAT_KEY_PREFIX) }
        return buildList {
            if (statKey != null) add(statKey)
            loadKeys.filterNot { it == statKey }.forEach(::add)
        }
    }

    private fun buildCooldownKey(
        scopeKey: String,
        failureCode: String
    ): String {
        return scopeKey + COOLDOWN_KEY_SEPARATOR + failureCode
    }

    private fun debugLog(stage: String, request: VideoMetricsRequest, key: String) {
        logger.debug(
            "video metrics {} aid={} bvid={} cid={} key={}",
            stage,
            request.aid ?: "-",
            request.bvid.maskedBvid(),
            request.cid ?: "-",
            key
        )
    }

    private fun debugPrefetchLog(stage: String, fields: Array<Pair<String, Any?>>) {
        if (!logger.isDebugEnabled) return

        val fieldsStr = fields.joinToString(" ") { (k, v) -> "$k=$v" }
        logger.debug("video metrics {} {}", stage, fieldsStr)
    }

    private fun String?.normalizeBvidOrNull(): String? {
        return this?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
    }

    private fun String?.maskedBvid(): String {
        val normalized = normalizeBvidOrNull() ?: return "-"
        if (normalized.length <= 7) return normalized
        return normalized.take(4) + "***" + normalized.takeLast(3)
    }

    private data class SharedTask(
        val deferred: Deferred<LoadRecord>,
        val joined: Boolean
    )

    private data class PendingPrefetchRequest(
        val request: VideoMetricsRequest,
        val sequence: Long
    )

    private data class Cooldown(
        val failureCode: String,
        val untilMs: Long
    )

    private var prefetchSequenceCounter: Long = 0L

    private companion object {
        const val STAT_KEY_PREFIX: String = "video_metrics:stat:"
        const val COOLDOWN_KEY_SEPARATOR: String = "#"
    }
}

internal class VideoMetricsRateLimitException(
    val statusCode: Int,
    message: String = "rate limited"
) : IllegalStateException(message)
