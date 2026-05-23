package dev.aaa1115910.bv.wjzfocus

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Rect

/**
 * Lazy 当前可见 item 的几何快照。
 *
 * [bounds] 来自 Lazy layout info 的 viewport 坐标，可用于自定义 resolver 做视觉距离判断。
 * 这只描述可见 item；不可见 item 仍通过 entry/index/key 参与 ScrollTo 路径。
 */
data class WjzFocusVisibleItem(
    val entryId: String,
    val itemKey: WjzFocusItemKey,
    val index: Int,
    val bounds: Rect
)

/** 查询当前 entry 在完整 Lazy 数据中的索引。 */
fun WjzLazyFocusRouteContext.currentIndex(currentEntryId: String): Int? {
    return indexByEntryId(currentEntryId)
}

/**
 * 把目标索引转换成 Lazy 路由结果。
 *
 * 如果目标已可见，返回 [WjzLazyFocusRouteResult.Target]；如果目标存在但未组合，
 * 返回 [WjzLazyFocusRouteResult.ScrollTo]；索引无效则返回 [WjzLazyFocusRouteResult.Missing]。
 */
fun WjzLazyFocusRouteContext.resultForIndex(index: Int): WjzLazyFocusRouteResult {
    val entryId = entryIdByIndex(index) ?: return WjzLazyFocusRouteResult.Missing
    return resultForEntryId(entryId)
}

/** 按 entry id 构建 Target/ScrollTo/Missing 结果。 */
fun WjzLazyFocusRouteContext.resultForEntryId(entryId: String): WjzLazyFocusRouteResult {
    if (entryId in visibleEntryIds) {
        targetByEntryId(entryId)?.let { target ->
            return WjzLazyFocusRouteResult.Target(target)
        }
    }
    if (targetNodeIdByEntryId(entryId) != null) {
        return WjzLazyFocusRouteResult.ScrollTo(entryId)
    }
    return WjzLazyFocusRouteResult.Missing
}

/** 查询当前可见快照里的 item 几何信息。 */
fun WjzLazyFocusRouteContext.visibleItem(entryId: String): WjzFocusVisibleItem? {
    return visibleItems.firstOrNull { item -> item.entryId == entryId }
}

/**
 * 从可见 item 中选择视觉上最近的候选。
 *
 * 排序规则依次是：是否与主方向的横/纵交叉轴重叠、主轴距离、交叉轴中心距离、原始索引。
 * 这是几何工具，不会自动替代默认索引拓扑；是否使用由自定义 resolver 决定。
 */
fun wjzFocusNearestVisibleItem(
    currentItem: WjzFocusVisibleItem,
    visibleItems: List<WjzFocusVisibleItem>,
    direction: FocusDirection
): WjzFocusVisibleItem? {
    return visibleItems
        .asSequence()
        .filter { item -> item.entryId != currentItem.entryId }
        .mapNotNull { item ->
            val mainAxisDistance = currentItem.bounds.mainAxisDistanceTo(
                target = item.bounds,
                direction = direction
            ) ?: return@mapNotNull null
            WjzFocusNearestVisibleItemCandidate(
                item = item,
                hasCrossAxisOverlap = currentItem.bounds.crossAxisOverlaps(
                    target = item.bounds,
                    direction = direction
                ),
                mainAxisDistance = mainAxisDistance,
                crossAxisCenterDistance = currentItem.bounds.crossAxisCenterDistanceTo(
                    target = item.bounds,
                    direction = direction
                )
            )
        }
        .sortedWith(
            compareBy<WjzFocusNearestVisibleItemCandidate> { candidate ->
                if (candidate.hasCrossAxisOverlap) 0 else 1
            }.thenBy { candidate -> candidate.mainAxisDistance }
                .thenBy { candidate -> candidate.crossAxisCenterDistance }
                .thenBy { candidate -> candidate.item.index }
        )
        .firstOrNull()
        ?.item
}

/** 在 [WjzLazyFocusRouteContext] 上直接执行最近可见 item 查询。 */
fun WjzLazyFocusRouteContext.nearestVisibleItem(
    currentEntryId: String,
    direction: FocusDirection
): WjzFocusVisibleItem? {
    val currentItem = visibleItem(currentEntryId) ?: return null
    return wjzFocusNearestVisibleItem(
        currentItem = currentItem,
        visibleItems = visibleItems,
        direction = direction
    )
}

/** LazyColumn 的默认索引拓扑，支持 [reversedLayout]。 */
fun wjzLazyColumnTargetIndex(
    currentIndex: Int,
    itemCount: Int,
    direction: FocusDirection,
    reversedLayout: Boolean = false,
    wrap: Boolean = false
): Int? {
    return wjzFocusLinearTargetIndex(
        currentIndex = currentIndex,
        itemCount = itemCount,
        focusDirection = direction,
        direction = if (reversedLayout) vertical.reversed else vertical,
        wrap = wrap
    )
}

/** LazyRow 的默认索引拓扑，支持 [reversedLayout]。 */
fun wjzLazyRowTargetIndex(
    currentIndex: Int,
    itemCount: Int,
    direction: FocusDirection,
    reversedLayout: Boolean = false,
    wrap: Boolean = false
): Int? {
    return wjzFocusLinearTargetIndex(
        currentIndex = currentIndex,
        itemCount = itemCount,
        focusDirection = direction,
        direction = if (reversedLayout) horizontal.reversed else horizontal,
        wrap = wrap
    )
}

/**
 * LazyVerticalGrid 的默认索引拓扑。
 *
 * [columns] 是当前帧按 Compose GridCells 规则计算出的列数；span/瀑布流等特殊布局不应使用该工具。
 */
fun wjzLazyGridTargetIndex(
    currentIndex: Int,
    columns: Int,
    itemCount: Int,
    direction: FocusDirection,
    horizontalWrap: Boolean = false,
    verticalWrap: Boolean = false
): Int? {
    if (columns <= 0 || currentIndex !in 0 until itemCount) return null
    val position = WjzFocusGridPosition(
        rowIndex = currentIndex / columns,
        columnIndex = currentIndex % columns
    )
    val rowSizes = List((itemCount + columns - 1) / columns) { rowIndex ->
        (itemCount - rowIndex * columns).coerceIn(0, columns)
    }
    val targetPosition = wjzFocusGridTargetPosition(
        currentPosition = position,
        rowSizes = rowSizes,
        focusDirection = direction,
        horizontalDirection = horizontal,
        verticalDirection = vertical,
        horizontalWrap = horizontalWrap,
        verticalWrap = verticalWrap
    ) ?: return null
    return targetPosition.rowIndex * columns + targetPosition.columnIndex
}

/**
 * LazyHorizontalGrid 的默认索引拓扑。
 *
 * [rows] 是当前帧按 Compose GridCells 规则计算出的行数。
 */
fun wjzLazyHorizontalGridTargetIndex(
    currentIndex: Int,
    rows: Int,
    itemCount: Int,
    direction: FocusDirection,
    horizontalWrap: Boolean = false,
    verticalWrap: Boolean = false
): Int? {
    if (rows <= 0 || currentIndex !in 0 until itemCount) return null
    val position = WjzFocusGridPosition(
        rowIndex = currentIndex % rows,
        columnIndex = currentIndex / rows
    )
    val columnSizes = List((itemCount + rows - 1) / rows) { columnIndex ->
        (itemCount - columnIndex * rows).coerceIn(0, rows)
    }
    val rowSizes = List(rows) { rowIndex ->
        columnSizes.indexOfLast { columnSize -> rowIndex < columnSize } + 1
    }
    val targetPosition = wjzFocusGridTargetPosition(
        currentPosition = position,
        rowSizes = rowSizes,
        focusDirection = direction,
        horizontalDirection = horizontal,
        verticalDirection = vertical,
        horizontalWrap = horizontalWrap,
        verticalWrap = verticalWrap
    ) ?: return null
    return targetPosition.columnIndex * rows + targetPosition.rowIndex
}

/** 构建 LazyColumn 默认 resolver。 */
fun wjzLazyColumnRouteResolver(
    reversedLayout: Boolean = false,
    wrap: Boolean = false
): WjzLazyFocusRouteResolver {
    return WjzLazyFocusRouteResolver { context, currentEntryId, direction ->
        val currentIndex = context.currentIndex(currentEntryId)
            ?: return@WjzLazyFocusRouteResolver WjzLazyFocusRouteResult.Missing
        val targetIndex = wjzLazyColumnTargetIndex(
            currentIndex = currentIndex,
            itemCount = context.itemCount,
            direction = direction,
            reversedLayout = reversedLayout,
            wrap = wrap
        ) ?: return@WjzLazyFocusRouteResolver WjzLazyFocusRouteResult.Missing
        context.resultForIndex(targetIndex)
    }
}

private data class WjzFocusNearestVisibleItemCandidate(
    val item: WjzFocusVisibleItem,
    val hasCrossAxisOverlap: Boolean,
    val mainAxisDistance: Float,
    val crossAxisCenterDistance: Float
)

private fun Rect.mainAxisDistanceTo(
    target: Rect,
    direction: FocusDirection
): Float? {
    // 只有目标位于指定方向一侧时才参与候选排序。
    return when (direction) {
        FocusDirection.Up -> (top - target.bottom).takeIf { distance -> distance >= 0f }
        FocusDirection.Down -> (target.top - bottom).takeIf { distance -> distance >= 0f }
        FocusDirection.Left -> (left - target.right).takeIf { distance -> distance >= 0f }
        FocusDirection.Right -> (target.left - right).takeIf { distance -> distance >= 0f }
        else -> null
    }
}

private fun Rect.crossAxisOverlaps(
    target: Rect,
    direction: FocusDirection
): Boolean {
    // 主方向移动优先选择在交叉轴上有投影重叠的候选，符合电视焦点的直觉。
    return when (direction) {
        FocusDirection.Up,
        FocusDirection.Down -> left < target.right && target.left < right
        FocusDirection.Left,
        FocusDirection.Right -> top < target.bottom && target.top < bottom
        else -> false
    }
}

private fun Rect.crossAxisCenterDistanceTo(
    target: Rect,
    direction: FocusDirection
): Float {
    // 没有重叠时，用交叉轴中心距离作为第二层几何稳定性排序。
    return when (direction) {
        FocusDirection.Up,
        FocusDirection.Down -> kotlin.math.abs(center.x - target.center.x)
        FocusDirection.Left,
        FocusDirection.Right -> kotlin.math.abs(center.y - target.center.y)
        else -> Float.MAX_VALUE
    }
}

/** 构建 LazyRow 默认 resolver。 */
fun wjzLazyRowRouteResolver(
    reversedLayout: Boolean = false,
    wrap: Boolean = false
): WjzLazyFocusRouteResolver {
    return WjzLazyFocusRouteResolver { context, currentEntryId, direction ->
        val currentIndex = context.currentIndex(currentEntryId)
            ?: return@WjzLazyFocusRouteResolver WjzLazyFocusRouteResult.Missing
        val targetIndex = wjzLazyRowTargetIndex(
            currentIndex = currentIndex,
            itemCount = context.itemCount,
            direction = direction,
            reversedLayout = reversedLayout,
            wrap = wrap
        ) ?: return@WjzLazyFocusRouteResolver WjzLazyFocusRouteResult.Missing
        context.resultForIndex(targetIndex)
    }
}

/** 构建 LazyVerticalGrid 默认 resolver。 */
fun wjzLazyGridRouteResolver(
    columns: Int,
    horizontalWrap: Boolean = false,
    verticalWrap: Boolean = false
): WjzLazyFocusRouteResolver {
    return WjzLazyFocusRouteResolver { context, currentEntryId, direction ->
        val currentIndex = context.currentIndex(currentEntryId)
            ?: return@WjzLazyFocusRouteResolver WjzLazyFocusRouteResult.Missing
        val targetIndex = wjzLazyGridTargetIndex(
            currentIndex = currentIndex,
            columns = columns,
            itemCount = context.itemCount,
            direction = direction,
            horizontalWrap = horizontalWrap,
            verticalWrap = verticalWrap
        ) ?: return@WjzLazyFocusRouteResolver WjzLazyFocusRouteResult.Missing
        context.resultForIndex(targetIndex)
    }
}

/** 构建 LazyHorizontalGrid 默认 resolver。 */
fun wjzLazyHorizontalGridRouteResolver(
    rows: Int,
    horizontalWrap: Boolean = false,
    verticalWrap: Boolean = false
): WjzLazyFocusRouteResolver {
    return WjzLazyFocusRouteResolver { context, currentEntryId, direction ->
        val currentIndex = context.currentIndex(currentEntryId)
            ?: return@WjzLazyFocusRouteResolver WjzLazyFocusRouteResult.Missing
        val targetIndex = wjzLazyHorizontalGridTargetIndex(
            currentIndex = currentIndex,
            rows = rows,
            itemCount = context.itemCount,
            direction = direction,
            horizontalWrap = horizontalWrap,
            verticalWrap = verticalWrap
        ) ?: return@WjzLazyFocusRouteResolver WjzLazyFocusRouteResult.Missing
        context.resultForIndex(targetIndex)
    }
}

/**
 * 构建显式邻接表 resolver。
 *
 * 该 DSL 使用 `near` 关键字表达“某方向的邻近 entry”，避免和外部出口 DSL 的 `move`
 * 发生语义混淆。邻接表只负责内部移动，不负责边界封闭。
 */
fun wjzLazyFocusNeighborResolver(
    build: WjzLazyFocusNeighborResolverBuilder.() -> Unit
): WjzLazyFocusRouteResolver {
    val builder = WjzLazyFocusNeighborResolverBuilder()
    builder.build()
    return builder.buildResolver()
}

/** 显式邻接表 resolver 的顶层构建器。 */
class WjzLazyFocusNeighborResolverBuilder {
    private val neighbors = linkedMapOf<String, Map<FocusDirection, String>>()

    /** 为一个 entry 声明若干方向上的内部邻居。 */
    fun item(
        entryId: String,
        build: WjzLazyFocusNeighborItemBuilder.() -> Unit
    ) {
        val itemBuilder = WjzLazyFocusNeighborItemBuilder()
        itemBuilder.build()
        neighbors[entryId] = itemBuilder.buildNeighbors()
    }

    /** 固化构建结果，返回只读 resolver。 */
    internal fun buildResolver(): WjzLazyFocusRouteResolver {
        val routes = neighbors.toMap()
        return WjzLazyFocusRouteResolver { context, currentEntryId, direction ->
            val entryId = routes[currentEntryId]?.get(direction)
                ?: return@WjzLazyFocusRouteResolver WjzLazyFocusRouteResult.Missing
            context.resultForEntryId(entryId)
        }
    }
}

/** 单个 entry 的邻居声明构建器。 */
class WjzLazyFocusNeighborItemBuilder {
    private val neighbors = linkedMapOf<FocusDirection, String>()

    /** 声明一个或多个方向上的邻近 entry。 */
    infix fun WjzFocusDirections.near(entryId: String) {
        directions.forEach { direction ->
            neighbors[direction] = entryId
        }
    }

    /** 返回该 entry 的方向邻接表。 */
    internal fun buildNeighbors(): Map<FocusDirection, String> {
        return neighbors.toMap()
    }
}
