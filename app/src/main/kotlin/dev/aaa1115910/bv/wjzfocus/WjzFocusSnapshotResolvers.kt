package dev.aaa1115910.bv.wjzfocus

import androidx.compose.ui.focus.FocusDirection

/**
 * 网格快照里的二维坐标。
 *
 * [rowIndex] 和 [columnIndex] 都是当前组合帧中 entries/rows 的逻辑索引，不代表屏幕像素位置。
 * 普通 Grid 默认按索引拓扑移动；需要 span、瀑布流或视觉几何规则时应改用 Lazy 自定义 resolver 工具。
 */
data class WjzFocusGridPosition(
    val rowIndex: Int,
    val columnIndex: Int
)

/**
 * 基于本帧 entries 快照构造一维内部路由。
 *
 * 这个 resolver 不保存可变 controller，也不依赖 SideEffect 同步。调用方每次组合都用当前 entries
 * 生成 resolver，item 注册同一帧就能拿到正确拓扑。
 */
fun wjzLinearFocusResolver(
    entries: List<WjzFocusTargetEntry>,
    direction: WjzFocusDirections = horizontal,
    wrap: Boolean = false
): WjzFocusRouteResolver {
    return WjzFocusRouteResolver { currentEntryId, focusDirection ->
        val currentIndex = entries.indexOfFirst { entry -> entry.id == currentEntryId }
        val targetIndex = wjzFocusLinearTargetIndex(
            currentIndex = currentIndex,
            itemCount = entries.size,
            focusDirection = focusDirection,
            direction = direction,
            wrap = wrap
        ) ?: return@WjzFocusRouteResolver WjzFocusRouteResult.Missing
        WjzFocusRouteResult.Target(entries[targetIndex])
    }
}

/**
 * 基于本帧 rows 快照构造二维内部路由。
 *
 * 行长度可以不一致。垂直移动到短行时会把 columnIndex 收敛到目标行最后一列，
 * 例如 `ABC / DEF / G` 中 `F` 向下会得到 `G`；横向移动只在当前行内查找，
 * 到达行边界且未开启 wrap 时返回 [WjzFocusRouteResult.Missing]。
 */
fun wjzGridFocusResolver(
    rows: List<List<WjzFocusTargetEntry>>,
    horizontalDirection: WjzFocusDirections = horizontal,
    verticalDirection: WjzFocusDirections = vertical,
    horizontalWrap: Boolean = false,
    verticalWrap: Boolean = false
): WjzFocusRouteResolver {
    val rowSizes = rows.map { row -> row.size }
    val positionByEntryId = rows.flatMapIndexed { rowIndex, row ->
        row.mapIndexed { columnIndex, entry ->
            entry.id to WjzFocusGridPosition(
                rowIndex = rowIndex,
                columnIndex = columnIndex
            )
        }
    }.toMap()

    return WjzFocusRouteResolver { currentEntryId, focusDirection ->
        val currentPosition = positionByEntryId[currentEntryId]
            ?: return@WjzFocusRouteResolver WjzFocusRouteResult.Missing
        val targetPosition = wjzFocusGridTargetPosition(
            currentPosition = currentPosition,
            rowSizes = rowSizes,
            focusDirection = focusDirection,
            horizontalDirection = horizontalDirection,
            verticalDirection = verticalDirection,
            horizontalWrap = horizontalWrap,
            verticalWrap = verticalWrap
        ) ?: return@WjzFocusRouteResolver WjzFocusRouteResult.Missing
        WjzFocusRouteResult.Target(rows[targetPosition.rowIndex][targetPosition.columnIndex])
    }
}

/**
 * 计算一维列表的目标索引。
 *
 * 返回 null 表示该方向不属于当前轴、当前索引无效，或移动越界且未开启 wrap。
 */
fun wjzFocusLinearTargetIndex(
    currentIndex: Int,
    itemCount: Int,
    focusDirection: FocusDirection,
    direction: WjzFocusDirections = horizontal,
    wrap: Boolean = false
): Int? {
    if (currentIndex !in 0 until itemCount) return null
    val offset = focusDirection.offsetIn(direction) ?: return null
    val targetIndex = currentIndex + offset
    return when {
        targetIndex in 0 until itemCount -> targetIndex
        !wrap || itemCount == 0 -> null
        offset < 0 -> itemCount - 1
        else -> 0
    }
}

/**
 * 计算二维索引网格的目标坐标。
 *
 * [rowSizes] 描述每一行的 item 数，允许末行短于前面行。垂直移动会尽量保持原列，
 * 如果目标行更短则落到该行最后一个 item；这让缺项网格仍能得到稳定、可预期的索引路由。
 */
fun wjzFocusGridTargetPosition(
    currentPosition: WjzFocusGridPosition,
    rowSizes: List<Int>,
    focusDirection: FocusDirection,
    horizontalDirection: WjzFocusDirections = horizontal,
    verticalDirection: WjzFocusDirections = vertical,
    horizontalWrap: Boolean = false,
    verticalWrap: Boolean = false
): WjzFocusGridPosition? {
    if (!currentPosition.isValidIn(rowSizes)) return null

    focusDirection.offsetIn(horizontalDirection)?.let { offset ->
        return wjzFocusHorizontalGridTargetPosition(
            currentPosition = currentPosition,
            rowSizes = rowSizes,
            offset = offset,
            wrap = horizontalWrap
        )
    }

    focusDirection.offsetIn(verticalDirection)?.let { offset ->
        return wjzFocusVerticalGridTargetPosition(
            currentPosition = currentPosition,
            rowSizes = rowSizes,
            offset = offset,
            wrap = verticalWrap
        )
    }

    return null
}

private fun wjzFocusHorizontalGridTargetPosition(
    currentPosition: WjzFocusGridPosition,
    rowSizes: List<Int>,
    offset: Int,
    wrap: Boolean
): WjzFocusGridPosition? {
    val rowSize = rowSizes[currentPosition.rowIndex]
    val targetColumnIndex = currentPosition.columnIndex + offset
    // 横向只在当前行内移动；是否跨到行首/行尾由 horizontalWrap 明确控制。
    return when {
        targetColumnIndex in 0 until rowSize -> currentPosition.copy(columnIndex = targetColumnIndex)
        !wrap || rowSize == 0 -> null
        offset < 0 -> currentPosition.copy(columnIndex = rowSize - 1)
        else -> currentPosition.copy(columnIndex = 0)
    }
}

private fun wjzFocusVerticalGridTargetPosition(
    currentPosition: WjzFocusGridPosition,
    rowSizes: List<Int>,
    offset: Int,
    wrap: Boolean
): WjzFocusGridPosition? {
    val targetRowIndex = currentPosition.rowIndex + offset
    // 垂直方向保持列意图，但目标行不足时收敛到该行最后一个有效列。
    return when {
        targetRowIndex in rowSizes.indices -> targetRowIndex.toCoercedPosition(currentPosition.columnIndex, rowSizes)
        !wrap -> null
        offset < 0 -> rowSizes.findLastNonEmptyRowIndex()
            ?.toCoercedPosition(currentPosition.columnIndex, rowSizes)
        else -> rowSizes.findFirstNonEmptyRowIndex()
            ?.toCoercedPosition(currentPosition.columnIndex, rowSizes)
    }
}

private fun FocusDirection.offsetIn(direction: WjzFocusDirections): Int? {
    val directions = direction.directions
    return when (this) {
        directions.getOrNull(0) -> -1
        directions.getOrNull(1) -> 1
        else -> null
    }
}

private fun WjzFocusGridPosition.isValidIn(rowSizes: List<Int>): Boolean {
    return rowIndex in rowSizes.indices && columnIndex in 0 until rowSizes[rowIndex]
}

private fun Int.toCoercedPosition(
    columnIndex: Int,
    rowSizes: List<Int>
): WjzFocusGridPosition? {
    val rowSize = rowSizes[this]
    if (rowSize <= 0) return null
    return WjzFocusGridPosition(
        rowIndex = this,
        columnIndex = columnIndex.coerceAtMost(rowSize - 1)
    )
}

private fun List<Int>.findFirstNonEmptyRowIndex(): Int? {
    return indices.firstOrNull { index -> this[index] > 0 }
}

private fun List<Int>.findLastNonEmptyRowIndex(): Int? {
    return indices.lastOrNull { index -> this[index] > 0 }
}

