package dev.aaa1115910.biliapi.metrics

import dev.aaa1115910.biliapi.http.entity.video.PlayUrlData
import dev.aaa1115910.biliapi.http.entity.video.RecordInfo
import dev.aaa1115910.biliapi.http.entity.video.SupportFormat
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

    private fun playUrlData(
        supportFormats: List<SupportFormat>
    ): PlayUrlData {
        return PlayUrlData(
            from = "test",
            result = "suee",
            message = "",
            quality = 80,
            format = "dash",
            timeLength = 1,
            acceptFormat = "",
            acceptDescription = emptyList(),
            acceptQuality = emptyList(),
            videoCodecId = 7,
            seekParam = "start",
            seekType = "offset",
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
}
