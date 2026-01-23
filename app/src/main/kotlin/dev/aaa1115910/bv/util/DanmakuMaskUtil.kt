package dev.aaa1115910.bv.util

import dev.aaa1115910.biliapi.entity.danmaku.DanmakuMaskFrame
import dev.aaa1115910.biliapi.entity.danmaku.DanmakuMaskSegment

// 1. 查找器类：封装了 "缓存 -> 顺序检查 -> 二分查找" 的复杂逻辑
class DanmakuMaskFinder {
    // 内部状态：记录上一次命中的 Segment 索引
    private var lastSegmentIndex = 0

    fun findFrame(masks: List<DanmakuMaskSegment>, currentTime: Long): DanmakuMaskFrame? {
        if (masks.isEmpty()) return null

        // A. 快速路径：检查缓存 (当前段)
        val currentSegment = masks.getOrNull(lastSegmentIndex)
        if (currentSegment != null && currentTime in currentSegment.range) {
            return findFrameInSegment(currentSegment, currentTime)
        }

        // B. 快速路径：检查下一段 (顺序播放优化)
        val nextSegment = masks.getOrNull(lastSegmentIndex + 1)
        if (nextSegment != null && currentTime in nextSegment.range) {
            lastSegmentIndex++
            return findFrameInSegment(nextSegment, currentTime)
        }

        // C. 慢速路径：二分查找 (Seek 后定位)
        val foundIndex = binarySearchSegment(masks, currentTime)
        return if (foundIndex >= 0) {
            lastSegmentIndex = foundIndex
            findFrameInSegment(masks[foundIndex], currentTime)
        } else {
            null
        }
    }

    // 重置缓存 (当视频源更换时调用)
    fun reset() {
        lastSegmentIndex = 0
    }

    // 内部工具：二分查找 Segment
    private fun binarySearchSegment(masks: List<DanmakuMaskSegment>, time: Long): Int {
        var low = 0
        var high = masks.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val segment = masks[mid]
            when {
                time < segment.range.first -> high = mid - 1
                time > segment.range.last -> low = mid + 1
                else -> return mid
            }
        }
        return -1
    }

    // 内部工具：在 Segment 内查找 Frame (这里简单用 firstOrNull，因为通常段内帧数极少)
    private fun findFrameInSegment(segment: DanmakuMaskSegment, time: Long): DanmakuMaskFrame? {
        return segment.frames.firstOrNull { time in it.range }
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