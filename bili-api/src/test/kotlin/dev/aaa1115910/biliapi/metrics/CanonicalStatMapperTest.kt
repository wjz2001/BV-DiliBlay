package dev.aaa1115910.biliapi.metrics

import dev.aaa1115910.biliapi.http.entity.video.VideoInfo
import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CanonicalStatMapperTest {
    @Test
    fun `from web view keeps raw long view instead of overflow sentinel`() {
        val rawView = Int.MAX_VALUE.toLong() + 1L
        val videoInfo = Json.decodeFromString<VideoInfo>(
            """
            {
              "bvid": "BV1xx411c7mD",
              "aid": 1,
              "videos": 1,
              "tid": 1,
              "tname": "test",
              "copyright": 1,
              "pic": "https://example.com/cover.jpg",
              "title": "title",
              "pubdate": 1,
              "desc": "desc",
              "state": 0,
              "duration": 120,
              "rights": {
                "bp": 0,
                "elec": 0,
                "download": 0,
                "movie": 0,
                "pay": 0,
                "hd5": 0,
                "no_reprint": 0,
                "autoplay": 0,
                "ugc_pay": 0,
                "is_cooperation": 0,
                "ugc_pay_preview": 0,
                "arc_pay": 0
              },
              "owner": {
                "mid": 1,
                "name": "owner",
                "face": "https://example.com/face.jpg"
              },
              "stat": {
                "aid": 1,
                "view": $rawView,
                "danmaku": 2,
                "reply": 3,
                "favorite": 4,
                "coin": 5,
                "share": 6,
                "like": 7
              },
              "dynamic": "",
              "cid": 2,
              "dimension": {
                "width": 1920,
                "height": 1080,
                "rotate": 0
              }
            }
            """.trimIndent()
        )

        val envelope = CanonicalStatMapper.fromWebView(videoInfo, updatedAt = 1234L)

        assertEquals(Int.MIN_VALUE, videoInfo.stat.view)
        assertEquals(rawView, envelope.stat.view)
        assertEquals(false, envelope.stat.isPaidVideo)
        assertEquals(false, envelope.stat.isVerticalVideo)
        assertEquals(CanonicalPrecision.EXACT, envelope.stat.precision)
        assertEquals(CanonicalStatMapper.WEB_DETAIL_SOURCE_ID, envelope.sourceId)
    }

    @Test
    fun `from web view maps paid video metadata`() {
        val paidByRightsPay = CanonicalStatMapper.fromWebView(videoInfo(pay = 1)).stat
        val paidByUgcPay = CanonicalStatMapper.fromWebView(videoInfo(ugcPay = 1)).stat
        val paidByArcPay = CanonicalStatMapper.fromWebView(videoInfo(arcPay = 1)).stat
        val paidByChargeableSeason = CanonicalStatMapper.fromWebView(
            videoInfo(isChargeableSeason = true)
        ).stat
        val free = CanonicalStatMapper.fromWebView(videoInfo()).stat

        assertEquals(true, paidByRightsPay.isPaidVideo)
        assertEquals(true, paidByUgcPay.isPaidVideo)
        assertEquals(true, paidByArcPay.isPaidVideo)
        assertEquals(true, paidByChargeableSeason.isPaidVideo)
        assertEquals(false, free.isPaidVideo)
    }

    @Test
    fun `from web view maps manuscript level vertical metadata`() {
        val vertical = CanonicalStatMapper.fromWebView(
            videoInfo(width = 1080, height = 1920)
        ).stat
        val horizontal = CanonicalStatMapper.fromWebView(
            videoInfo(width = 1920, height = 1080)
        ).stat

        assertEquals(true, vertical.isVerticalVideo)
        assertEquals(false, horizontal.isVerticalVideo)
    }

    @Test
    fun `parse count marks BigDecimal precision only when truncated`() {
        val integer = CanonicalStatMapper.parseCount(BigDecimal("123"))
        val decimal = CanonicalStatMapper.parseCount(BigDecimal("1.2"))
        val stat = CanonicalStatMapper.buildCanonicalStat(
            fields = RawCanonicalStatFields(
                view = BigDecimal("123"),
                danmaku = BigDecimal("1.2")
            ),
            updatedAt = 1L,
            cacheStatus = CanonicalCacheStatus.MISS,
            refreshReason = CanonicalRefreshReason.DETAIL_SUPPLEMENT
        )

        assertEquals(123L, integer.value)
        assertEquals(false, integer.approximate)
        assertEquals(1L, decimal.value)
        assertEquals(true, decimal.approximate)
        assertEquals(123L, stat.view)
        assertEquals(1L, stat.danmaku)
        assertEquals(CanonicalPrecision.APPROX, stat.precision)
    }

    @Test
    fun `build canonical stat parses prose counts as approximate`() {
        val stat = CanonicalStatMapper.buildCanonicalStat(
            fields = RawCanonicalStatFields(
                view = "2.399万播放",
                danmaku = "1,234",
                reply = "12万",
                favorite = "1.2亿",
                coin = "42",
                share = null,
                like = "0.0001亿",
                durationSec = "01:01:01"
            ),
            updatedAt = 1L,
            cacheStatus = CanonicalCacheStatus.MISS,
            refreshReason = CanonicalRefreshReason.DETAIL_SUPPLEMENT
        )

        assertEquals(23_990L, stat.view)
        assertEquals(1_234L, stat.danmaku)
        assertEquals(120_000L, stat.reply)
        assertEquals(120_000_000L, stat.favorite)
        assertEquals(42L, stat.coin)
        assertNull(stat.share)
        assertEquals(10_000L, stat.like)
        assertEquals(3_661, stat.durationSec)
        assertEquals(CanonicalPrecision.APPROX, stat.precision)
    }

    @Test
    fun `build canonical stat rejects decimal string without unit`() {
        val stat = CanonicalStatMapper.buildCanonicalStat(
            fields = RawCanonicalStatFields(
                view = "2.3",
                danmaku = "3.0"
            ),
            updatedAt = 1L,
            cacheStatus = CanonicalCacheStatus.MISS,
            refreshReason = CanonicalRefreshReason.DETAIL_SUPPLEMENT
        )

        assertNull(stat.view)
        assertNull(stat.danmaku)
        assertEquals(CanonicalPrecision.UNKNOWN, stat.precision)
    }

    @Test
    fun `build canonical stat returns null for invalid or negative prose`() {
        val stat = CanonicalStatMapper.buildCanonicalStat(
            fields = RawCanonicalStatFields(
                view = "--",
                danmaku = "-1",
                reply = "万",
                favorite = "",
                coin = "abc"
            ),
            updatedAt = 1L,
            cacheStatus = CanonicalCacheStatus.UNKNOWN,
            refreshReason = CanonicalRefreshReason.UNKNOWN
        )

        assertNull(stat.view)
        assertNull(stat.danmaku)
        assertNull(stat.reply)
        assertNull(stat.favorite)
        assertNull(stat.coin)
        assertEquals(CanonicalPrecision.UNKNOWN, stat.precision)
    }

    private fun videoInfo(
        pay: Int = 0,
        ugcPay: Int = 0,
        arcPay: Int = 0,
        isChargeableSeason: Boolean = false,
        width: Int = 1920,
        height: Int = 1080
    ): VideoInfo {
        return Json.decodeFromString(
            """
            {
              "bvid": "BV1xx411c7mD",
              "aid": 1,
              "videos": 1,
              "tid": 1,
              "tname": "test",
              "copyright": 1,
              "pic": "https://example.com/cover.jpg",
              "title": "title",
              "pubdate": 1,
              "desc": "desc",
              "state": 0,
              "duration": 120,
              "rights": {
                "bp": 0,
                "elec": 0,
                "download": 0,
                "movie": 0,
                "pay": $pay,
                "hd5": 0,
                "no_reprint": 0,
                "autoplay": 0,
                "ugc_pay": $ugcPay,
                "is_cooperation": 0,
                "ugc_pay_preview": 0,
                "arc_pay": $arcPay
              },
              "owner": {
                "mid": 1,
                "name": "owner",
                "face": "https://example.com/face.jpg"
              },
              "stat": {
                "aid": 1,
                "view": 1,
                "danmaku": 2,
                "reply": 3,
                "favorite": 4,
                "coin": 5,
                "share": 6,
                "like": 7
              },
              "dynamic": "",
              "cid": 2,
              "dimension": {
                "width": $width,
                "height": $height,
                "rotate": 0
              },
              "is_chargeable_season": $isChargeableSeason
            }
            """.trimIndent()
        )
    }
}
