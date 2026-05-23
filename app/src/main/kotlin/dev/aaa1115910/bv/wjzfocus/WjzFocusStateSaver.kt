package dev.aaa1115910.bv.wjzfocus

import java.io.Serializable

/**
 * 可跨 Activity 重建保存的 WjzFocus 轻量历史。
 *
 * 该结构只保存稳定寻址信息，不保存 requester、generation、bounds 等运行时对象。
 * 恢复时 Coordinator 只导入历史，真正请求焦点仍由后续 Host resume 或 pending 消费路径重新校验。
 */
data class WjzFocusSavedState(
    val activeLayer: WjzFocusLayer = WjzFocusLayer.Content,
    val recentFocus: List<WjzFocusSavedRecentLayer> = emptyList(),
    val sourceStack: List<WjzFocusSavedSource> = emptyList(),
    val lastFocusedScopes: List<WjzFocusSavedLayerScope> = emptyList()
) : Serializable

/** 单个 layer 内的最近 leaf 焦点记录，按旧到新保存。 */
data class WjzFocusSavedRecentLayer(
    val layer: WjzFocusLayer,
    val nodes: List<WjzFocusSavedRecentNode>
) : Serializable

/** 最近 leaf 焦点节点。 */
data class WjzFocusSavedRecentNode(
    val nodeId: String,
    val scopeId: String?
) : Serializable

/** Dialog/Overlay 等临时 layer 的来源历史。 */
data class WjzFocusSavedSource(
    val layer: WjzFocusLayer,
    val scopeId: String?,
    val nodeId: String?,
    val token: Long
) : Serializable

/** Host resume 资格使用的最近 leaf scope。 */
data class WjzFocusSavedLayerScope(
    val layer: WjzFocusLayer,
    val scopeId: String?
) : Serializable
