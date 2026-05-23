package dev.aaa1115910.bv.wjzfocus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * 普通二维网格封装。
 *
 * 该组件基于当前 [rows] 生成一份快照 resolver，并在每个 item 上先挂 [Modifier.wjzFocusRouter]、
 * 再挂 [Modifier.wjzFocusExits]。因此同一帧 item 注册拿到的就是同一帧拓扑，不需要稳定 controller。
 *
 * 行长度可以不一致；垂直移动会收敛到目标行的最后一个可用列，横向移动只在当前行内进行。
 */
@Composable
fun <T> WjzFocusGrid(
    rows: List<List<T>>,
    key: (T) -> String,
    gridId: String,
    modifier: Modifier = Modifier,
    layer: WjzFocusLayer = WjzFocusLayer.Content,
    scopeId: WjzFocusScopeId? = null,
    horizontalDirection: WjzFocusDirections = horizontal,
    verticalDirection: WjzFocusDirections = vertical,
    horizontalWrap: Boolean = false,
    verticalWrap: Boolean = false,
    itemContent: @Composable (item: T, focusModifier: Modifier) -> Unit
) {
    val targetRows = remember(rows, key, gridId, layer, scopeId) {
        buildGridTargetRows(
            rows = rows,
            key = key,
            gridId = gridId,
            layer = layer,
            scopeId = scopeId
        )
    }
    val flatTargets = remember(targetRows) { targetRows.flatten() }
    val resolver = remember(
        targetRows,
        horizontalDirection,
        verticalDirection,
        horizontalWrap,
        verticalWrap
    ) {
        wjzGridFocusResolver(
            rows = targetRows,
            horizontalDirection = horizontalDirection,
            verticalDirection = verticalDirection,
            horizontalWrap = horizontalWrap,
            verticalWrap = verticalWrap
        )
    }

    if (flatTargets.isNotEmpty()) {
        WjzFocusEntriesHost(
            componentId = gridId,
            default = { flatTargets.first() },
            entries = {
                flatTargets.forEach { target ->
                    entry(target.id) { target }
                }
            }
        )
    }

    Column(
        modifier = modifier,
        content = {
            rows.forEachIndexed { rowIndex, row ->
                Row(
                    content = {
                        row.forEachIndexed { columnIndex, item ->
                            val target = targetRows[rowIndex][columnIndex]
                            itemContent(
                                item,
                                Modifier
                                    .wjzFocusRouter(
                                        currentEntryId = target.id,
                                        resolver = resolver
                                    )
                                    .wjzFocusExits(
                                        nodeId = target.nodeId,
                                        layer = layer,
                                        scopeId = scopeId
                                    )
                            )
                        }
                    }
                )
            }
        }
    )
}

/**
 * 将业务 rows 映射为 WjzFocus target rows。
 *
 * [key] 必须在整个 grid 内唯一。业务 key 可以包含 WjzFocus 协议分隔符；
 * target entry id 和 item node id 会使用编码后的内部 id。
 */
private fun <T> buildGridTargetRows(
    rows: List<List<T>>,
    key: (T) -> String,
    gridId: String,
    layer: WjzFocusLayer,
    scopeId: WjzFocusScopeId?
): List<List<WjzFocusTargetEntry>> {
    val usedKeys = linkedSetOf<String>()
    return rows.map { row ->
        row.map { item ->
            val itemKey = key(item)
            require(usedKeys.add(itemKey)) {
                "duplicate wjz focus item key '$itemKey' in '$gridId'"
            }
            // Grid 的 resolver 只读 entryId；业务 key 先编码，保证 target.id 满足 local entry 规则。
            val entryId = wjzFocusEncodeItemEntryId(itemKey)
            val localId = WjzFocusLocalId(wjzFocusItemNodeId(listId = gridId, itemEntryId = entryId))
            val nodeId = scopeId?.resolve(localId) ?: WjzFocusNodeId(localId.value)
            WjzFocusTargetEntry(
                id = entryId,
                nodeId = nodeId,
                layer = layer,
                scopeId = scopeId
            )
        }
    }
}
