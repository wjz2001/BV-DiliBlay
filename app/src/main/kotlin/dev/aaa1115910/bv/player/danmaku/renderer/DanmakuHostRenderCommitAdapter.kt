package dev.aaa1115910.bv.player.danmaku.renderer

import dev.aaa1115910.bv.player.danmaku.ui.DanmakuHostRenderCommit

fun DanmakuRendererState.toHostRenderCommit(): DanmakuHostRenderCommit {
    return DanmakuHostRenderCommit(
        frame = frame,
        snapshot = snapshot,
        maskRegionSet = maskRegionSet,
        lastSnapshotFrameId = lastSnapshotFrameId,
        activeItemCount = activeItemCount,
        maskEnabled = maskEnabled,
        renderSerial = renderSerial,
        clearSerial = clearSerial,
        renderStats = renderStats,
        reason = reason,
    )
}
