package dev.aaa1115910.bv.player.danmaku.mask

import dev.aaa1115910.biliapi.entity.danmaku.DanmakuMask
import dev.aaa1115910.bv.player.danmaku.model.MaskRegionSet
import dev.aaa1115910.bv.util.DanmakuMaskFinder

data class Viewport(
    val width: Int,
    val height: Int,
)

interface DanmakuMaskAdapter {
    fun reset() = Unit

    suspend fun adapt(
        mask: DanmakuMask?,
        viewport: Viewport,
        positionMs: Long = 0L,
    ): MaskRegionSet
}

class DefaultDanmakuMaskAdapter : DanmakuMaskAdapter {
    private val maskFinder = DanmakuMaskFinder()
    private var currentMask: DanmakuMask? = null

    override fun reset() {
        currentMask = null
        maskFinder.reset()
    }

    override suspend fun adapt(
        mask: DanmakuMask?,
        viewport: Viewport,
        positionMs: Long,
    ): MaskRegionSet {
        if (mask == null) {
            return MaskRegionSet(
                viewportWidth = viewport.width,
                viewportHeight = viewport.height,
                frame = null,
                regions = emptyList(),
            )
        }

        if (mask !== currentMask) {
            currentMask = mask
            maskFinder.reset()
        }

        return MaskRegionSet(
            viewportWidth = viewport.width,
            viewportHeight = viewport.height,
            frame = maskFinder.findFrame(mask, positionMs),
            regions = emptyList(),
        )
    }
}
