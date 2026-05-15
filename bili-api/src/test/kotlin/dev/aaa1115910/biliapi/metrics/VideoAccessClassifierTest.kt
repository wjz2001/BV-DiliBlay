package dev.aaa1115910.biliapi.metrics

import dev.aaa1115910.biliapi.http.entity.video.PlayUrlData
import dev.aaa1115910.biliapi.http.entity.video.RecordInfo
import dev.aaa1115910.biliapi.http.entity.video.SupportFormat
import dev.aaa1115910.biliapi.http.entity.video.Durl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoAccessClassifierTest {
    @Test
    fun `resolve access flags keeps vip and paid mutually exclusive`() {
        assertEquals(
            VideoAccessClassifier.ResolvedAccessFlags(
                isVipVideo = true,
                isPaidVideo = false
            ),
            VideoAccessClassifier.resolveAccessFlags(
                rawPaidVideo = true,
                isVipVideo = true
            )
        )
        assertEquals(
            VideoAccessClassifier.ResolvedAccessFlags(
                isVipVideo = false,
                isPaidVideo = true
            ),
            VideoAccessClassifier.resolveAccessFlags(
                rawPaidVideo = true,
                isVipVideo = false
            )
        )
        assertEquals(
            VideoAccessClassifier.ResolvedAccessFlags(
                isVipVideo = null,
                isPaidVideo = true
            ),
            VideoAccessClassifier.resolveAccessFlags(
                rawPaidVideo = true,
                isVipVideo = null
            )
        )
        assertEquals(
            VideoAccessClassifier.ResolvedAccessFlags(
                isVipVideo = false,
                isPaidVideo = false
            ),
            VideoAccessClassifier.resolveAccessFlags(
                rawPaidVideo = true,
                isVipVideo = false,
                hasPaid = true
            )
        )
        assertEquals(
            VideoAccessClassifier.ResolvedAccessFlags(
                isVipVideo = false,
                isPaidVideo = true
            ),
            VideoAccessClassifier.resolveAccessFlags(
                rawPaidVideo = true,
                isVipVideo = false,
                hasPaid = null
            )
        )
        assertEquals(
            VideoAccessClassifier.ResolvedAccessFlags(
                isVipVideo = false,
                isPaidVideo = true
            ),
            VideoAccessClassifier.resolveAccessFlags(
                rawPaidVideo = false,
                isVipVideo = false,
                hasPaid = false,
                isPreview = true
            )
        )
        assertEquals(
            VideoAccessClassifier.ResolvedAccessFlags(
                isVipVideo = false,
                isPaidVideo = false
            ),
            VideoAccessClassifier.resolveAccessFlags(
                rawPaidVideo = true,
                isVipVideo = false,
                hasPaid = true,
                isPreview = true
            )
        )
    }

    @Test
    fun `infer vip video uses support formats when available`() {
        assertEquals(
            true,
            VideoAccessClassifier.inferVipVideo(
                playUrlData(
                    supportFormats = listOf(
                        supportFormat(needVip = false),
                        supportFormat(needVip = true)
                    )
                )
            )
        )
        assertEquals(
            false,
            VideoAccessClassifier.inferVipVideo(
                playUrlData(
                    supportFormats = listOf(
                        supportFormat(needVip = false)
                    )
                )
            )
        )
        assertNull(VideoAccessClassifier.inferVipVideo(playUrlData(supportFormats = emptyList())))
    }

    @Test
    fun `infer preview uses preview flag description and durl fallback`() {
        assertEquals(
            true,
            VideoAccessClassifier.inferPreview(
                playUrlData(
                    supportFormats = emptyList(),
                    isPreview = 1
                )
            )
        )
        assertEquals(
            true,
            VideoAccessClassifier.inferPreview(
                playUrlData(
                    supportFormats = emptyList(),
                    acceptDescription = listOf("试看 6 分钟")
                )
            )
        )
        assertEquals(
            true,
            VideoAccessClassifier.inferPreview(
                playUrlData(
                    supportFormats = emptyList(),
                    durl = listOf(durl())
                )
            )
        )
    }

    private fun playUrlData(
        supportFormats: List<SupportFormat>,
        isPreview: Int = 0,
        acceptDescription: List<String> = emptyList(),
        durl: List<Durl> = emptyList()
    ): PlayUrlData {
        return PlayUrlData(
            isPreview = isPreview,
            from = "test",
            result = "suee",
            message = "",
            quality = 80,
            format = "dash",
            timeLength = 1,
            acceptFormat = "",
            acceptDescription = acceptDescription,
            acceptQuality = emptyList(),
            videoCodecId = 7,
            seekParam = "start",
            seekType = "offset",
            durl = durl,
            supportFormats = supportFormats,
            recordInfo = RecordInfo(recordIcon = "", record = "")
        )
    }

    private fun supportFormat(
        needVip: Boolean
    ): SupportFormat {
        return SupportFormat(
            quality = 80,
            format = "dash",
            newDescription = "1080P",
            displayDesc = "1080P",
            superScript = "",
            needVip = needVip
        )
    }

    private fun durl(): Durl {
        return Durl(
            order = 1,
            length = 1,
            size = 1,
            ahead = "",
            vhead = "",
            url = "https://example.com/video.mp4"
        )
    }
}
