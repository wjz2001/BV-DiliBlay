package dev.aaa1115910.biliapi.metrics

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VideoStatCacheTest {
    @AfterTest
    fun tearDown() {
        VideoStatCache.clear()
    }

    @Test
    fun `get fresh or null hits by aid within ttl`() {
        val envelope = createEnvelope(
            aid = 1001L,
            bvid = "BV1xx411c7mD"
        )

        VideoStatCache.put(
            envelope = envelope,
            ttlMs = 1_000L,
            nowMs = 10_000L
        )

        val fresh = VideoStatCache.getFreshOrNull(aid = 1001L, nowMs = 10_999L)

        assertNotNull(fresh)
        assertEquals(CanonicalSource.CACHE, fresh.stat.source)
        assertEquals(CanonicalCacheStatus.HIT, fresh.stat.cacheStatus)
        assertEquals(1_000L, fresh.stat.ttlMs)
        assertEquals(11_000L, fresh.stat.expireAt)
    }

    @Test
    fun `get fresh or null misses after ttl but stale ok still returns`() {
        val envelope = createEnvelope(
            aid = 1002L,
            bvid = "BV1ab411c7mD"
        )

        VideoStatCache.put(
            envelope = envelope,
            ttlMs = 1_000L,
            nowMs = 5_000L
        )

        val fresh = VideoStatCache.getFreshOrNull(aid = 1002L, nowMs = 6_000L)
        val stale = VideoStatCache.getStaleOk(aid = 1002L, nowMs = 6_000L)

        assertNull(fresh)
        assertNotNull(stale)
        assertEquals(1002L, stale.aid)
        assertEquals(CanonicalCacheStatus.STALE, stale.stat.cacheStatus)
    }

    @Test
    fun `supports bvid lookup and alias to aid`() {
        val envelope = createEnvelope(
            aid = 1003L,
            bvid = " bv1cd411c7md "
        )

        VideoStatCache.put(
            envelope = envelope,
            ttlMs = 3_000L,
            nowMs = 20_000L
        )

        val byBvid = VideoStatCache.getFreshOrNull(bvid = "BV1CD411C7MD", nowMs = 20_100L)
        val byAid = VideoStatCache.getFreshOrNull(aid = 1003L, nowMs = 20_100L)

        assertNotNull(byBvid)
        assertNotNull(byAid)
        assertEquals("BV1CD411C7MD", byBvid.bvid)
        assertEquals(byAid, byBvid)
    }

    @Test
    fun `invalidate by bvid clears both bvid and aid entries`() {
        val envelope = createEnvelope(
            aid = 1004L,
            bvid = "BV1ef411c7mD"
        )

        VideoStatCache.put(envelope = envelope, nowMs = 30_000L)
        VideoStatCache.invalidate(bvid = "bv1ef411c7md")

        assertNull(VideoStatCache.getStaleOk(aid = 1004L))
        assertNull(VideoStatCache.getStaleOk(bvid = "BV1EF411C7MD"))
    }

    @Test
    fun `get stale ok returns hit when entry is still fresh`() {
        val envelope = createEnvelope(
            aid = 1005L,
            bvid = "BV1gh411c7mD"
        )

        VideoStatCache.put(
            envelope = envelope,
            ttlMs = 1_000L,
            nowMs = 8_000L
        )

        val result = VideoStatCache.getStaleOk(aid = 1005L, nowMs = 8_999L)

        assertNotNull(result)
        assertEquals(CanonicalCacheStatus.HIT, result.stat.cacheStatus)
    }

    @Test
    fun `approx does not overwrite exact`() {
        VideoStatCache.put(
            envelope = createEnvelope(
                aid = 1006L,
                bvid = "BV1ij411c7mD",
                precision = CanonicalPrecision.EXACT,
                view = 999L
            ),
            ttlMs = 1_000L,
            nowMs = 10_000L
        )

        VideoStatCache.put(
            envelope = createEnvelope(
                aid = 1006L,
                bvid = "BV1ij411c7mD",
                precision = CanonicalPrecision.APPROX,
                view = 111L
            ),
            ttlMs = 5_000L,
            nowMs = 11_000L
        )

        val cached = VideoStatCache.getStaleOk(aid = 1006L, nowMs = 12_000L)

        assertNotNull(cached)
        assertEquals(999L, cached.stat.view)
        assertEquals(CanonicalPrecision.EXACT, cached.stat.precision)
        assertEquals(11_000L, cached.stat.expireAt)
    }

    private fun createEnvelope(
        aid: Long,
        bvid: String,
        precision: CanonicalPrecision = CanonicalPrecision.EXACT,
        view: Long = 123L
    ): StatEnvelope {
        return StatEnvelope(
            sourceId = CanonicalStatMapper.WEB_DETAIL_SOURCE_ID,
            aid = aid,
            bvid = bvid,
            cid = 2001L,
            stat = CanonicalStat(
                view = view,
                danmaku = 45L,
                reply = 6L,
                favorite = 7L,
                coin = 8L,
                share = 9L,
                like = 10L,
                durationSec = 120,
                isPaidVideo = false,
                isVerticalVideo = false,
                source = CanonicalSource.DETAIL_SUPPLEMENT,
                updatedAt = 1_234L,
                precision = precision,
                cacheStatus = CanonicalCacheStatus.MISS,
                ttlMs = null,
                expireAt = null,
                nextRefreshAt = null,
                refreshReason = CanonicalRefreshReason.DETAIL_SUPPLEMENT,
                fieldSources = null
            )
        )
    }
}
