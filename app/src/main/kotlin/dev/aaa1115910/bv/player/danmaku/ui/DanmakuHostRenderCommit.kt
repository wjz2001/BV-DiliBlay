package dev.aaa1115910.bv.player.danmaku.ui

import dev.aaa1115910.bv.player.danmaku.model.DanmakuRenderFrame
import dev.aaa1115910.bv.player.danmaku.model.DanmakuRenderSnapshot
import dev.aaa1115910.bv.player.danmaku.model.MaskRegionSet

/**
 * UI/Host boundary object for committing renderer output into [DanmakuHostState].
 *
 * Keep this DTO free from renderer implementation details so the host state only
 * receives the already-rendered frame/snapshot and counters it needs to coordinate
 * playback, tracks and visibility.
 */
data class DanmakuHostRenderCommit(
    val frame: DanmakuRenderFrame? = null,
    val snapshot: DanmakuRenderSnapshot? = frame?.snapshot,
    val maskRegionSet: MaskRegionSet? = null,
    val lastSnapshotFrameId: Long = snapshot?.frameId ?: 0L,
    val activeItemCount: Int = snapshot?.count ?: 0,
    val maskEnabled: Boolean = frame?.hasMask ?: false,
    val renderSerial: Long = 0L,
    val clearSerial: Long = 0L,
    val renderStats: DanmakuHostRenderStats = DanmakuHostRenderStats(),
    val reason: String = frame?.reason ?: "init",
)
