package dev.aaa1115910.bv.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntrySurface
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryId
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.defaultEntry
import dev.aaa1115910.bv.wjzfocus.wjzFocusGroup
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import androidx.tv.material3.Text as TvText
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.toast
import kotlinx.coroutines.delay

private val OrderedMultiSelectDialogScopeId = WjzFocusScopeId("dialog/ordered-multi-select")
private val OrderedMultiSelectDialogContainerNodeId =
    WjzFocusNodeId("dialog/ordered-multi-select/container")
private const val OrderedMultiSelectFocusComponentPrefix = "ordered_multi_select_dialog"
private const val OrderedMultiSelectTargetFocusId = "target"

/**
 * 带顺序的多选列表 Dialog（TV 焦点友好）：
 * - 点击选中时分配 01..99 中最小缺失序号
 * - 点击已选项时只删除自身序号，不重排其它项
 * - 关闭弹窗时一次性提交按序号排序后的结果
 */
@Composable
internal fun <T, ID> OrderedMultiSelectDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    items: List<T>,
    initialSelectedIds: List<ID>,
    itemId: (T) -> ID,
    onSubmit: (List<ID>) -> Unit,
    text: (T) -> String,
    itemKey: ((T) -> Any)? = null,
    defaultFocusKey: Any? = null,
    defaultFocusIndex: Int? = null,
    emptySelectionToastText: String = "请至少选择一项",
    discontinuousOrderToastText: String = "序号不连续，请补齐后再退出",
    requiredSelectedIds: Collection<ID> = emptyList(),
    requiredSelectionToastText: String = "请先选择必选项",
    onBlockedExit: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    containerColor: Color = C.surface,
    contentColor: Color = C.onSurface,
    tonalElevation: Dp = 4.dp,
    outerPadding: PaddingValues = PaddingValues(24.dp),
    itemSpacing: Dp = 8.dp,
    /** Dialog 宽度占屏幕宽度比例 */
    widthFraction: Float = 0.6f,
    /** 列表最大高度占窗口高度比例 */
    maxHeightFraction: Float = 0.5f,
) {
    if (!visible) return

    val context = LocalContext.current
    val selectedOrders = remember { mutableStateMapOf<ID, Int>() }
    val validIds = remember(items, itemId) { items.map(itemId).toSet() }
    val requiredItemIds = remember(validIds, requiredSelectedIds) {
        requiredSelectedIds.toSet().intersect(validIds)
    }
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val maxHeightDp = with(density) {
        (windowInfo.containerSize.height * maxHeightFraction).toDp()
    }

    LaunchedEffect(true, items, initialSelectedIds) {
        if (!visible) return@LaunchedEffect

        selectedOrders.clear()
        selectedOrders.putAll(initialSelectedIds.toInitialSelectedOrders(validIds))
    }

    val titleSlot: (@Composable () -> Unit)? = if (title.isNotBlank()) {
        { M3Text(title) }
    } else {
        null
    }

    TvAlertDialog(
        onDismissRequest = {
            when {
                requiredItemIds.hasMissingSelection(selectedOrders) -> {
                    requiredSelectionToastText.toast(context)
                    onBlockedExit?.invoke()
                }
                selectedOrders.isEmpty() -> emptySelectionToastText.toast(context)
                !selectedOrders.values.hasContinuousOrders() -> discontinuousOrderToastText.toast(context)
                else -> {
                    onSubmit(
                        selectedOrders.entries
                            .sortedBy { it.value }
                            .map { it.key }
                    )
                    onDismissRequest()
                }
            }
        },
        confirmButton = {},
        modifier = Modifier.fillMaxWidth(widthFraction),
        title = titleSlot,
        text = {
            OrderedMultiSelectListContent(
                modifier = Modifier
                    .heightIn(max = maxHeightDp)
                    .padding(outerPadding),
                items = items,
                selectedOrders = selectedOrders,
                itemId = itemId,
                onSelectedOrdersChange = { orders ->
                    selectedOrders.clear()
                    selectedOrders.putAll(orders)
                },
                text = text,
                itemKey = itemKey,
                defaultFocusKey = defaultFocusKey,
                defaultFocusIndex = defaultFocusIndex,
                requestDefaultFocus = true,
                requiredSelectedIds = requiredSelectedIds,
                requiredSelectionToastText = requiredSelectionToastText,
                onBlockedExit = onBlockedExit,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
            )
        },
        shape = shape,
        containerColor = containerColor,
        titleContentColor = contentColor,
        textContentColor = contentColor,
        tonalElevation = tonalElevation,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        dialogScopeId = OrderedMultiSelectDialogScopeId,
        containerNodeId = OrderedMultiSelectDialogContainerNodeId
    )
}

@Composable
internal fun <T, ID> OrderedMultiSelectListContent(
    modifier: Modifier = Modifier,
    items: List<T>,
    selectedOrders: Map<ID, Int>,
    itemId: (T) -> ID,
    onSelectedOrdersChange: (Map<ID, Int>) -> Unit,
    text: (T) -> String,
    requiredSelectedIds: Collection<ID> = emptyList(),
    requiredSelectionToastText: String = "请先选择必选项",
    onBlockedExit: (() -> Unit)? = null,

    // key & default focus
    itemKey: ((T) -> Any)? = null,
    defaultFocusKey: Any? = null,
    defaultFocusIndex: Int? = null,
    requestDefaultFocus: Boolean = true,

    // layout
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
) {
    val context = LocalContext.current
    val focusCoordinator = LocalWjzFocusCoordinator.current
    val focusScopeId = LocalWjzFocusScopeId.current ?: OrderedMultiSelectDialogScopeId
    val listState = rememberLazyListState()
    var pendingLoopTargetIndex by remember { mutableStateOf<Int?>(null) }

    val targetIndex = remember(items, itemKey, defaultFocusKey, defaultFocusIndex) {
        items.resolveTargetFocusIndex(
            itemKey = itemKey,
            defaultFocusKey = defaultFocusKey,
            defaultFocusIndex = defaultFocusIndex
        )
    }
    // 阻止隐藏初始tab
    val defaultFocusId = remember(items, itemKey, defaultFocusKey) {
        if (defaultFocusKey == null) {
            null
        } else {
            items.firstOrNull { item ->
                (itemKey?.invoke(item) ?: itemId(item)) == defaultFocusKey
            }?.let(itemId)
        }
    }
    val requiredItemIds = remember(items, itemId, requiredSelectedIds) {
        val validIds = items.map(itemId).toSet()
        requiredSelectedIds.toSet().intersect(validIds)
    }
    val focusComponentId = remember(focusScopeId) {
        "${OrderedMultiSelectFocusComponentPrefix}_${focusScopeId.value.hashCode()}"
    }
    val focusEntryId = remember(focusComponentId) { WjzFocusEntryId.parse(focusComponentId) }
    val focusHostTargetIndex = pendingLoopTargetIndex ?: targetIndex
    val focusHostTargetNodeId = when {
        focusHostTargetIndex != null && focusHostTargetIndex in items.indices -> {
            orderedMultiSelectNodeId(
                index = focusHostTargetIndex,
                item = items[focusHostTargetIndex],
                targetIndex = targetIndex,
                scopeId = focusScopeId,
                itemId = itemId,
                itemKey = itemKey
            )
        }

        items.isNotEmpty() -> {
            orderedMultiSelectNodeId(
                index = 0,
                item = items.first(),
                targetIndex = targetIndex,
                scopeId = focusScopeId,
                itemId = itemId,
                itemKey = itemKey
            )
        }

        else -> OrderedMultiSelectDialogContainerNodeId
    }

    WjzFocusEntrySurface(
        componentId = focusComponentId,
        default = {
            defaultEntry(
                nodeId = focusHostTargetNodeId,
                layer = WjzFocusLayer.Dialog,
                scopeId = focusScopeId
            )
        }
    )

    LaunchedEffect(targetIndex, requestDefaultFocus) {
        if (!requestDefaultFocus) return@LaunchedEffect
        if (targetIndex == null) return@LaunchedEffect

        val targetVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
        if (!targetVisible) {
            runCatching { listState.scrollToItem(targetIndex) }
        }

        repeat(5) {
            focusCoordinator?.requestEntryFocus(focusEntryId)
            delay(50L)
        }
    }

    LaunchedEffect(pendingLoopTargetIndex, items, targetIndex) {
        val loopTargetIndex = pendingLoopTargetIndex ?: return@LaunchedEffect
        if (loopTargetIndex !in items.indices) {
            pendingLoopTargetIndex = null
            return@LaunchedEffect
        }

        val targetVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == loopTargetIndex }
        if (!targetVisible) {
            runCatching { listState.scrollToItem(loopTargetIndex) }
        }

        repeat(5) {
            focusCoordinator?.requestEntryFocus(focusEntryId)
            delay(50L)
        }

        pendingLoopTargetIndex = null
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .wjzFocusGroup(),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement,
    ) {
        itemsIndexed(
            items = items,
            key = itemKey?.let { k -> { _: Int, item: T -> k(item) } }
        ) { index, item ->
            var hasFocus by remember { mutableStateOf(false) }
            val nodeId = orderedMultiSelectNodeId(
                index = index,
                item = item,
                targetIndex = targetIndex,
                scopeId = focusScopeId,
                itemId = itemId,
                itemKey = itemKey
            )
            val itemModifier =
                Modifier.wjzFocusExits(
                    id = nodeId.value.removePrefix("${focusScopeId.value}/"),
                    layer = WjzFocusLayer.Dialog,
                    onFocusChanged = { hasFocus = it }
                )
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft, Key.DirectionRight -> {
                                if (requiredItemIds.hasMissingSelection(selectedOrders)) {
                                    requiredSelectionToastText.toast(context)
                                    onBlockedExit?.invoke()
                                }
                                true
                            }

                            Key.DirectionUp -> {
                                if (index == 0 && items.size > 1) {
                                    pendingLoopTargetIndex = items.lastIndex
                                    true
                                } else {
                                    false
                                }
                            }

                            Key.DirectionDown -> {
                                if (index == items.lastIndex && items.size > 1) {
                                    pendingLoopTargetIndex = 0
                                    true
                                } else {
                                    false
                                }
                            }

                            else -> false
                        }
                    }
            val id = itemId(item)
            val order = selectedOrders[id]

            OrderedMultiSelectItem(
                modifier = itemModifier,
                text = text(item),
                order = order,
                hasFocus = hasFocus,
                onClick = {
                    if (id == defaultFocusId && order != null) {
                        "初始页面不能隐藏".toast(context)
                    } else {
                        val nextOrders = selectedOrders.toMutableMap()

                        if (order != null) {
                            nextOrders.remove(id)
                        } else {
                            val nextOrder = nextOrders.values.firstMissingOrder()
                            if (nextOrder != null) {
                                nextOrders[id] = nextOrder
                            }
                        }

                        onSelectedOrdersChange(nextOrders)
                    }
                }
            )
        }
    }
}

@Composable
private fun OrderedMultiSelectItem(
    modifier: Modifier = Modifier,
    text: String,
    order: Int?,
    hasFocus: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = modifier,
        headlineContent = { TvText(text = text) },
        trailingContent = {
            NumberedSelectBox(
                order = order,
                hasFocus = hasFocus
            )
        },
        onClick = onClick,
        selected = order != null,
        colors = orderedSelectItemColors()
    )
}

@Composable
private fun NumberedSelectBox(
    order: Int?,
    hasFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    val selected = order != null
    val targetSize by animateDpAsState(
        targetValue = if (selected) 46.dp else 42.dp,
        label = "OrderedMultiSelect box size"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            hasFocus -> C.primary
            selected -> C.secondary
            else -> C.onSurface
        },
        label = "OrderedMultiSelect box border"
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            if (hasFocus) C.primary else C.secondary
        } else {
            Color.Transparent
        },
        label = "OrderedMultiSelect box container"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            if (hasFocus) C.onPrimary else C.onSecondary
        } else {
            C.onSurface
        },
        label = "OrderedMultiSelect box content"
    )

    Box(
        modifier = modifier
            .size(targetSize)
            .background(containerColor, RoundedCornerShape(8.dp))
            .border(
                border = BorderStroke(2.dp, borderColor),
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = order,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.85f)) togetherWith
                        (fadeOut() + scaleOut(targetScale = 0.85f))
            },
            label = "OrderedMultiSelect number"
        ) { value ->
            if (value != null) {
                TvText(
                    text = value.coerceIn(1, 99).toString().padStart(2, '0'),
                    color = contentColor,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun <ID> List<ID>.toInitialSelectedOrders(validIds: Set<ID>): Map<ID, Int> {
    val orders = linkedMapOf<ID, Int>()
    this.forEach { id ->
        if (orders.size >= 99) return@forEach
        if (id in validIds && id !in orders) {
            orders[id] = orders.size + 1
        }
    }
    return orders
}

private fun Collection<Int>.firstMissingOrder(): Int? {
    val usedOrders = toSet()
    return (1..99).firstOrNull { it !in usedOrders }
}

private fun Collection<Int>.hasContinuousOrders(): Boolean {
    val sortedOrders = sorted()
    return sortedOrders == (1..sortedOrders.size).toList()
}

private fun <ID> Collection<ID>.hasMissingSelection(selectedOrders: Map<ID, Int>): Boolean {
    return any { id -> selectedOrders[id] == null }
}

private fun <T, ID> orderedMultiSelectNodeId(
    index: Int,
    item: T,
    targetIndex: Int?,
    scopeId: WjzFocusScopeId,
    itemId: (T) -> ID,
    itemKey: ((T) -> Any)?
): WjzFocusNodeId {
    return if (index == targetIndex) {
        WjzFocusNodeId("${scopeId.value}/$OrderedMultiSelectTargetFocusId")
    } else {
        WjzFocusNodeId(
            "${scopeId.value}/item/${itemKey?.invoke(item) ?: itemId(item)}"
        )
    }
}

/**
 * 计算要默认聚焦的目标下标（0-based）。
 *
 * 规则：
 * 1) 如果 defaultFocusKey != null：按 key 优先查找（找到就返回）
 * 2) 否则/找不到：回退到 defaultFocusIndex 规则（1-based + 负数倒数，不允许 0）
 * 3) 最终找不到合法目标：返回 null（不自动聚焦）
 */
private fun <T> List<T>.resolveTargetFocusIndex(
    itemKey: ((T) -> Any)?,
    defaultFocusKey: Any?,
    defaultFocusIndex: Int?
): Int? {
    if (isEmpty()) return null

    if (defaultFocusKey != null) {
        val idxByKey = if (itemKey != null) {
            indexOfFirst { item -> itemKey(item) == defaultFocusKey }
        } else {
            indexOfFirst { item -> item == defaultFocusKey }
        }
        if (idxByKey >= 0) return idxByKey
    }

    return resolveFocusIndex(defaultFocusIndex)
}

/**
 * 把 index 规则转换为 0-based 下标。
 *
 * request 语义：
 * - null：不自动聚焦
 * - >0：1-based（1=第1个）
 * - <0：倒数（-1=最后一个）
 * - 0：非法（不聚焦）
 *
 * 越界：返回 null（不聚焦）
 */
private fun <T> List<T>.resolveFocusIndex(request: Int?): Int? {
    if (request == null) return null
    if (isEmpty()) return null
    if (request == 0) return null

    val index0 = if (request > 0) {
        request - 1
    } else {
        size + request
    }

    return index0.takeIf { it in indices }
}

@Composable
private fun orderedSelectItemColors() = ListItemDefaults.colors(
    containerColor = Color.Transparent,
    contentColor = C.onSurface,

    focusedContainerColor = C.primaryContainer,
    focusedContentColor = C.onSurface,

    pressedContainerColor = C.secondaryContainer,
    pressedContentColor = C.onSurface,

    selectedContainerColor = C.tertiaryContainer,
    selectedContentColor = C.onSurface,

    disabledContainerColor = C.surfaceVariant,
    disabledContentColor = C.disabled,

    focusedSelectedContainerColor = C.primaryContainer,
    focusedSelectedContentColor = C.onSurface,

    pressedSelectedContainerColor = C.secondaryContainer,
    pressedSelectedContentColor = C.onSurface
)
