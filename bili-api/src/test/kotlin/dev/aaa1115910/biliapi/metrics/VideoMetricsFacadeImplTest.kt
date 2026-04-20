package dev.aaa1115910.biliapi.metrics

import dev.aaa1115910.biliapi.repositories.AuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import io.ktor.http.HttpStatusCode
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class VideoMetricsFacadeImplTest {
    @AfterTest
    fun tearDown() {
        VideoStatCache.clear()
    }

    @Test
    fun `load deduplicates same in-flight key`() = runBlocking {
        val callCount = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        val facade = newFacade(
            remoteFetcher = { aid, bvid ->
                callCount.incrementAndGet()
                gate.await()
                testEnvelope(
                    aid = aid ?: 1001L,
                    bvid = bvid ?: "BV1AA411C7MD",
                    updatedAt = 2_000L
                )
            }
        )

        val first = async { facade.load(VideoMetricsRequest(aid = 1001L, allowStale = false, timeoutMs = null)) }
        val second = async { facade.load(VideoMetricsRequest(aid = 1001L, allowStale = false, timeoutMs = null)) }

        delay(50)
        gate.complete(Unit)
        val results = awaitAll(first, second)

        assertEquals(1, callCount.get())
        assertEquals(1001L, results[0].identity.aid)
        assertEquals(1001L, results[1].identity.aid)
        assertTrue(results.any { it.runtime.inFlightShared })
    }

    @Test
    fun `load deduplicates aid and bvid in-flight aliases`() = runBlocking {
        val callCount = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        val facade = newFacade(
            remoteFetcher = { aid, bvid ->
                callCount.incrementAndGet()
                gate.await()
                testEnvelope(
                    aid = aid ?: 4001L,
                    bvid = bvid ?: "BV1ZZ411C7MD",
                    updatedAt = 2_000L
                )
            }
        )

        val first = async {
            facade.load(
                VideoMetricsRequest(
                    aid = 4001L,
                    bvid = "BV1ZZ411C7MD",
                    allowStale = false,
                    timeoutMs = null
                )
            )
        }
        val second = async {
            facade.load(
                VideoMetricsRequest(
                    bvid = "bv1zz411c7md",
                    allowStale = false,
                    timeoutMs = null
                )
            )
        }

        delay(50)
        gate.complete(Unit)
        val results = awaitAll(first, second)

        assertEquals(1, callCount.get())
        assertTrue(results.any { it.runtime.inFlightShared })
        assertEquals(4001L, results[0].identity.aid)
        assertEquals(4001L, results[1].identity.aid)
    }

    @Test
    fun `load falls back to stale cache on network failure`() = runBlocking {
        VideoStatCache.put(
            envelope = testEnvelope(
                aid = 1002L,
                bvid = "BV1BB411C7MD",
                updatedAt = 1_000L
            ),
            ttlMs = 1_000L,
            nowMs = 1_000L
        )
        val facade = newFacade(
            nowMs = 5_000L,
            remoteFetcher = { _, _ -> error("boom") }
        )

        val result = facade.load(
            VideoMetricsRequest(aid = 1002L, allowStale = false, timeoutMs = null)
        )

        assertEquals(CanonicalCacheStatus.STALE, result.snapshot.cacheStatus)
        assertTrue(result.runtime.degraded)
        assertEquals("NETWORK", result.runtime.failureCode)
    }

    @Test
    fun `load returns empty envelope when network fails and no cache exists`() = runBlocking {
        val facade = newFacade(
            nowMs = 7_000L,
            remoteFetcher = { _, _ -> error("boom") }
        )

        val result = facade.load(
            VideoMetricsRequest(bvid = "BV1CC411C7MD", allowStale = false, timeoutMs = null)
        )

        assertNull(result.snapshot.view)
        assertEquals(CanonicalSource.UNKNOWN, result.snapshot.source)
        assertEquals(CanonicalCacheStatus.MISS, result.snapshot.cacheStatus)
        assertTrue(result.runtime.degraded)
    }

    @Test
    fun `prefetch respects global semaphore concurrency`() = runBlocking {
        val currentActive = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val facade = newFacade(
            config = VideoMetricsFacadeConfig(
                cacheTtlMs = 2_000L,
                staleMaxAgeMs = 5_000L,
                maxDegradeAgeMs = 10_000L,
                nextRefreshOffsetMs = 1_000L
            ),
            remoteFetcher = { aid, bvid ->
                val active = currentActive.incrementAndGet()
                maxActive.updateAndGet { maxOf(it, active) }
                delay(80)
                currentActive.decrementAndGet()
                testEnvelope(
                    aid = aid ?: 0L,
                    bvid = bvid ?: "BV${aid}XX",
                    updatedAt = 3_000L
                )
            }
        )

        facade.prefetch(
            requests = (1L..7L).map { aid ->
                VideoMetricsRequest(aid = aid, allowStale = false, timeoutMs = null)
            },
            options = VideoMetricsPrefetchOptions(
                firstScreenCount = 7,
                deferredBatchSize = 7,
                deferredStartDelayMs = 0L,
                interBatchDelayMs = 0L
            )
        )

        assertEquals(VideoMetricsGlobalConcurrencyLimiter.DEFAULT_MAX_CONCURRENCY, maxActive.get())
        assertEquals(0, currentActive.get())
    }

    @Test
    fun `load returns fresh cache without remote fetch or limiter permit`() = runBlocking {
        VideoStatCache.put(
            envelope = testEnvelope(
                aid = 1004L,
                bvid = "BV1EE411C7MD",
                updatedAt = 2_000L
            ),
            ttlMs = 5_000L,
            nowMs = 2_000L
        )
        val remoteCallCount = AtomicInteger(0)
        val limiterEnterCount = AtomicInteger(0)
        val facade = newFacade(
            nowMs = 3_000L,
            remoteFetcher = { _, _ ->
                remoteCallCount.incrementAndGet()
                fail("remoteFetcher should not be called on fresh cache hit")
            },
            globalLimiter = VideoMetricsGlobalConcurrencyLimiter(
                onPermitEntered = { limiterEnterCount.incrementAndGet() }
            )
        )

        val result = facade.load(
            VideoMetricsRequest(aid = 1004L, allowStale = false, timeoutMs = null)
        )

        assertEquals(CanonicalSource.CACHE, result.snapshot.source)
        assertEquals(CanonicalCacheStatus.HIT, result.snapshot.cacheStatus)
        assertEquals(true, result.snapshot.isPaidVideo)
        assertEquals(true, result.snapshot.isVerticalVideo)
        assertFalse(result.runtime.inFlightShared)
        assertFalse(result.runtime.degraded)
        assertEquals(0, remoteCallCount.get())
        assertEquals(0, limiterEnterCount.get())
    }

    @Test
    fun `prefetch runs visible first and defers the rest`() = runBlocking {
        val callOrder = mutableListOf<Long>()
        val facade = newFacade(
            remoteFetcher = { aid, bvid ->
                synchronized(callOrder) {
                    callOrder += aid ?: -1L
                }
                testEnvelope(
                    aid = aid ?: 0L,
                    bvid = bvid ?: "BV${aid}XX",
                    updatedAt = 3_000L
                )
            }
        )

        facade.prefetch(
            requests = (1L..18L).map { aid ->
                VideoMetricsRequest(aid = aid, allowStale = false, timeoutMs = null)
            },
            options = VideoMetricsPrefetchOptions(
                firstScreenCount = 15,
                deferredBatchSize = 10,
                deferredStartDelayMs = 500L,
                interBatchDelayMs = 0L
            )
        )

        delay(100)
        val immediateCalls = synchronized(callOrder) { callOrder.toList() }
        assertEquals(15, immediateCalls.size)
        assertEquals((1L..15L).toList(), immediateCalls.sorted())

        delay(550)
        val allCalls = synchronized(callOrder) { callOrder.toList() }
        assertEquals(18, allCalls.size)
        assertEquals((1L..18L).toList(), allCalls.sorted())
    }

    @Test
    fun `prefetch drops requests over configured cap`() = runBlocking {
        val callCount = AtomicInteger(0)
        val facade = newFacade(
            config = VideoMetricsFacadeConfig(maxPrefetchRequests = 3),
            remoteFetcher = { aid, bvid ->
                callCount.incrementAndGet()
                testEnvelope(
                    aid = aid ?: 0L,
                    bvid = bvid ?: "BV${aid}XX",
                    updatedAt = 3_000L
                )
            }
        )

        facade.prefetch(
            requests = (1L..5L).map { aid ->
                VideoMetricsRequest(aid = aid, allowStale = false, timeoutMs = null)
            },
            options = VideoMetricsPrefetchOptions(
                firstScreenCount = 5,
                deferredBatchSize = 5,
                deferredStartDelayMs = 0L,
                interBatchDelayMs = 0L
            )
        )

        assertEquals(3, callCount.get())
    }

    @Test
    fun `http 429 cooldown only blocks same aid scope`() = runBlocking {
        val callCount = AtomicInteger(0)
        val facade = newFacade(
            config = VideoMetricsFacadeConfig(rateLimitCooldownMs = 30_000L),
            remoteFetcher = { aid, bvid ->
                callCount.incrementAndGet()
                if (callCount.get() == 1) {
                    throw VideoMetricsRateLimitException(HttpStatusCode.TooManyRequests.value, "too many requests")
                }
                testEnvelope(
                    aid = aid ?: 0L,
                    bvid = bvid ?: "BV${aid}XX",
                    updatedAt = 3_000L
                )
            }
        )

        val first = facade.load(
            VideoMetricsRequest(aid = 5001L, allowStale = false, timeoutMs = null)
        )
        val second = facade.load(
            VideoMetricsRequest(aid = 5001L, allowStale = false, timeoutMs = null)
        )
        val third = facade.load(
            VideoMetricsRequest(aid = 5002L, allowStale = false, timeoutMs = null)
        )

        assertEquals("HTTP_429", first.runtime.failureCode)
        assertEquals("HTTP_429", second.runtime.failureCode)
        assertNull(third.runtime.failureCode)
        assertEquals(2, callCount.get())
        assertEquals(second.runtime.failureMessage?.startsWith("cooldown until"), true)
        assertFalse(third.runtime.degraded)
    }

    @Test
    fun `http 429 cooldown is shared by aid and bvid aliases of aliases`() = runBlocking {
        val callCount = AtomicInteger(0)
        val facade = newFacade(
            config = VideoMetricsFacadeConfig(rateLimitCooldownMs = 30_000L),
            remoteFetcher = { aid, bvid ->
                callCount.incrementAndGet()
                if (callCount.get() == 1) {
                    throw VideoMetricsRateLimitException(HttpStatusCode.TooManyRequests.value, "too many requests")
                }
                testEnvelope(
                    aid = aid ?: 6001L,
                    bvid = bvid ?: "BV1ZZ411C7MD",
                    updatedAt = 3_000L
                )
            }
        )

        val first = facade.load(
            VideoMetricsRequest(
                aid = 6001L,
                bvid = "BV1ZZ411C7MD",
                allowStale = false,
                timeoutMs = null
            )
        )
        val second = facade.load(
            VideoMetricsRequest(
                bvid = "bv1zz411c7md",
                allowStale = false,
                timeoutMs = null
            )
        )

        assertEquals("HTTP_429", first.runtime.failureCode)
        assertEquals("HTTP_429", second.runtime.failureCode)
        assertEquals(1, callCount.get())
        assertEquals(second.runtime.failureMessage?.startsWith("cooldown until"), true)
    }

    @Test
    fun `load maps visible and prefetch priorities to expected batch groups`() = runBlocking {
        val facade = newFacade(
            remoteFetcher = { aid, bvid ->
                testEnvelope(
                    aid = aid ?: 0L,
                    bvid = bvid ?: "BV${aid}XX",
                    updatedAt = 2_000L
                )
            }
        )

        val visible = facade.load(
            VideoMetricsRequest(aid = 2001L, priority = VideoMetricsPriority.VISIBLE, timeoutMs = null)
        )
        val prefetch = facade.load(
            VideoMetricsRequest(aid = 2002L, priority = VideoMetricsPriority.PREFETCH, timeoutMs = null)
        )

        assertEquals(VideoMetricsBatchGroup.INTERACTIVE, visible.runtime.batchGroup)
        assertEquals(VideoMetricsBatchGroup.DEFERRED, prefetch.runtime.batchGroup)
        assertEquals(VideoMetricsKeys.contextKey(VideoMetricsRequest(aid = 2001L, priority = VideoMetricsPriority.VISIBLE, timeoutMs = null)), visible.runtime.contextKey)
        assertEquals(VideoMetricsKeys.statKey(2001L), visible.runtime.statKey)
        assertEquals(VideoMetricsKeys.contextKey(VideoMetricsRequest(aid = 2002L, priority = VideoMetricsPriority.PREFETCH, timeoutMs = null)), prefetch.runtime.contextKey)
        assertEquals(VideoMetricsKeys.statKey(2002L), prefetch.runtime.statKey)
    }

    @Test
    fun `visible load bypasses deferred queue saturation`() = runBlocking<Unit> {
        val backgroundStarted = CompletableDeferred<Unit>()
        val releaseBackground = CompletableDeferred<Unit>()
        val visibleStarted = CompletableDeferred<Unit>()
        val secondDeferredStarted = CompletableDeferred<Unit>()
        val facade = newFacade(
            remoteFetcher = { aid, bvid ->
                when (aid) {
                    3001L -> {
                        backgroundStarted.complete(Unit)
                        releaseBackground.await()
                    }
                    3002L -> {
                        secondDeferredStarted.complete(Unit)
                    }
                    3003L -> {
                        visibleStarted.complete(Unit)
                    }
                }
                testEnvelope(
                    aid = aid ?: 0L,
                    bvid = bvid ?: "BV${aid}XX",
                    updatedAt = 4_000L
                )
            },
            globalLimiter = VideoMetricsGlobalConcurrencyLimiter(
                totalConcurrency = 2,
                deferredConcurrency = 1
            )
        )

        val firstDeferred = async {
            facade.load(
                VideoMetricsRequest(aid = 3001L, priority = VideoMetricsPriority.PREFETCH, timeoutMs = null)
            )
        }
        backgroundStarted.await()

        val secondDeferred = async {
            facade.load(
                VideoMetricsRequest(aid = 3002L, priority = VideoMetricsPriority.PREFETCH, timeoutMs = null)
            )
        }
        val visible = async {
            facade.load(
                VideoMetricsRequest(aid = 3003L, priority = VideoMetricsPriority.VISIBLE, timeoutMs = null)
            )
        }

        withTimeout(500L) {
            visible.await()
        }
        assertTrue(visibleStarted.isCompleted)
        assertFalse(secondDeferredStarted.isCompleted)

        releaseBackground.complete(Unit)
        firstDeferred.await()
        secondDeferred.await()
    }

    @Test
    fun `load returns stale immediately when allowStale is true`() = runBlocking<Unit> {
        VideoStatCache.put(
            envelope = testEnvelope(
                aid = 1003L,
                bvid = "BV1DD411C7MD",
                updatedAt = 1_000L
            ),
            ttlMs = 1_000L,
            nowMs = 1_000L
        )
        val gate = CompletableDeferred<Unit>()
        val callCount = AtomicInteger(0)
        val facade = newFacade(
            nowMs = 5_000L,
            remoteFetcher = { aid, bvid ->
                callCount.incrementAndGet()
                gate.await()
                testEnvelope(
                    aid = aid ?: 1003L,
                    bvid = bvid ?: "BV1DD411C7MD",
                    updatedAt = 5_000L
                )
            }
        )

        val result = facade.load(
            VideoMetricsRequest(aid = 1003L, allowStale = true, timeoutMs = null)
        )

        assertEquals(CanonicalCacheStatus.STALE, result.snapshot.cacheStatus)
        assertFalse(result.runtime.degraded)
        delay(50)
        assertEquals(1, callCount.get())
        gate.complete(Unit)
    }

    private fun newFacade(
        config: VideoMetricsFacadeConfig = VideoMetricsFacadeConfig(),
        nowMs: Long = 10_000L,
        remoteFetcher: suspend (Long?, String?) -> StatEnvelope,
        globalLimiter: VideoMetricsGlobalConcurrencyLimiter = VideoMetricsGlobalConcurrencyLimiter()
    ): VideoMetricsFacadeImpl {
        return VideoMetricsFacadeImpl(
            authRepository = stubAuthRepository(),
            remoteFetcher = remoteFetcher,
            config = config,
            timeProvider = { nowMs },
            detachedScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO),
            globalLimiter = globalLimiter
        )
    }

    private fun stubAuthRepository(
        sessionData: String? = null
    ): AuthRepository {
        return AuthRepository().apply {
            this.sessionData = sessionData
        }
    }

    private fun testEnvelope(
        aid: Long,
        bvid: String,
        updatedAt: Long
    ): StatEnvelope {
        return StatEnvelope(
            sourceId = CanonicalStatMapper.WEB_DETAIL_SOURCE_ID,
            aid = aid,
            bvid = bvid,
            cid = 2001L,
            stat = CanonicalStat(
                view = 123L,
                danmaku = 45L,
                reply = 6L,
                favorite = 7L,
                coin = 8L,
                share = 9L,
                like = 10L,
                durationSec = 120,
                isPaidVideo = true,
                isVerticalVideo = true,
                source = CanonicalSource.DETAIL_SUPPLEMENT,
                updatedAt = updatedAt,
                precision = CanonicalPrecision.EXACT,
                cacheStatus = CanonicalCacheStatus.MISS,
                ttlMs = null,
                expireAt = null,
                nextRefreshAt = null,
                refreshReason = CanonicalRefreshReason.CACHE_MISS,
                fieldSources = null
            )
        )
    }
}
