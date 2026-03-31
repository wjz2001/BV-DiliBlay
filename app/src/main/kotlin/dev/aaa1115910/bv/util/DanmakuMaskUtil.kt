package dev.aaa1115910.bv.util

import dev.aaa1115910.biliapi.entity.danmaku.DanmakuMask
import dev.aaa1115910.biliapi.entity.danmaku.DanmakuMaskFrame
import dev.aaa1115910.biliapi.entity.danmaku.DanmakuMaskSegment

class DanmakuMaskFinder {

    private var cachedSegment: DanmakuMaskSegment? = null

    /** 收到新 mask（切集、开关切换）时调用，清除缓存 */
    fun reset() {
        cachedSegment = null
    }

    /**
     * 根据当前播放时间查找对应帧。
     *
     * @param mask       从 [uiState.danmakuMask] 取得的 [DanmakuMask] 对象
     * @param currentTime 当前播放时间（毫秒）
     * @return 当前应渲染的 [DanmakuMaskFrame]，无则返回 null
     */
    fun findFrame(mask: DanmakuMask, currentTime: Long): DanmakuMaskFrame? {
        // 1. 当前缓存的 segment 是否仍然覆盖此时间点
        val cached = cachedSegment
        if (cached == null || currentTime !in cached.range) {
            // 2. 需要切换 segment：按需解压，旧 segment 自动失去引用可被 GC
            cachedSegment = mask.getSegmentAt(currentTime)
        }

        // 3. 在当前 segment 内线性查找帧
        return cachedSegment?.frames?.lastOrNull { currentTime in it.range }
    }
}

// 2. 计算下一次 Loop 的休眠时间
// 逻辑：如果有蒙版，休眠到蒙版结束(但限制在20~300ms以便响应Seek)；否则根据播放状态决定轮询快慢
fun calculateMaskDelay(
    currentFrame: DanmakuMaskFrame?,
    currentTime: Long,
    isPlaying: Boolean
): Long {
    return when {
        currentFrame != null && isPlaying -> {
            (currentFrame.range.last - currentTime).coerceIn(20L, 300L)
        }
        isPlaying -> 100L // 正常播放轮询
        else -> 200L      // 暂停或异常状态降低频率
    }
}