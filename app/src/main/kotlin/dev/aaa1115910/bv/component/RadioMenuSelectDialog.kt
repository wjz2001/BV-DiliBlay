package dev.aaa1115910.bv.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.RadioButton
import androidx.tv.material3.RadioButtonDefaults
import androidx.tv.material3.Text as TvText
import dev.aaa1115910.bv.ui.theme.C
import kotlinx.coroutines.delay

/**
 * 单选列表 Dialog（TV 焦点友好）：
 * - defaultFocusKey 优先
 * - defaultFocusIndex 兜底（1-based + 负数倒数；0 非法）
 */
@Composable
internal fun <T> RadioMenuSelectDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    items: List<T>,
    selected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    text: (T) -> String,
    itemKey: ((T) -> Any)? = null,
    defaultFocusKey: Any? = null,
    defaultFocusIndex: Int? = null,
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

    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val maxHeightDp = with(density) {
        (windowInfo.containerSize.height * maxHeightFraction).toDp()
    }

    val titleSlot: (@Composable () -> Unit)? =
        title.takeIf { it.isNotBlank() }?.let { t -> { M3Text(t) } }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {},
        modifier = Modifier.fillMaxWidth(widthFraction),
        title = titleSlot,
        text = {
            RadioMenuSelectListContent(
                modifier = Modifier
                    .heightIn(max = maxHeightDp)
                    .padding(outerPadding),
                items = items,
                selected = selected,
                onClick = onSelect,
                text = text,
                itemKey = itemKey,
                defaultFocusKey = defaultFocusKey,
                defaultFocusIndex = defaultFocusIndex,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
            )
        },
        shape = shape,
        containerColor = containerColor,
        titleContentColor = contentColor,
        textContentColor = contentColor,
        tonalElevation = tonalElevation,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    )
}

@Composable
private fun <T> RadioMenuSelectListContent(
    modifier: Modifier = Modifier, // ✅ 作用于 LazyColumn（heightIn/maxHeight/padding 等）
    items: List<T>,
    selected: (T) -> Boolean,
    onClick: (T) -> Unit,
    text: (T) -> String,

    // key & default focus
    itemKey: ((T) -> Any)? = null,
    defaultFocusKey: Any? = null,
    defaultFocusIndex: Int? = null,

    // layout
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // 计算“最终要聚焦的 item 下标（0-based）”
    val targetIndex = remember(items, itemKey, defaultFocusKey, defaultFocusIndex) {
        items.resolveTargetFocusIndex(
            itemKey = itemKey,
            defaultFocusKey = defaultFocusKey,
            defaultFocusIndex = defaultFocusIndex
        )
    }

    /**
     * 请求默认焦点的策略：
     * 1) 先滚动到目标 item，保证它可见
     * 2) TV 场景下节点可能还没“挂稳”，做短暂重试更可靠
     */
    LaunchedEffect(targetIndex) {
        if (targetIndex == null) return@LaunchedEffect

        runCatching { listState.scrollToItem(targetIndex) }

        repeat(5) {
            runCatching { focusRequester.requestFocus() }
            delay(50L)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier // ✅ 修复：不再引用不存在的 listModifier
            .fillMaxWidth(),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement,
    ) {
        itemsIndexed(
            items = items,
            key = itemKey?.let { k -> { _: Int, item: T -> k(item) } }
        ) { index, item ->
            val itemModifier =
                if (index == targetIndex) Modifier.focusRequester(focusRequester) else Modifier

            RadioMenuSelectItem(
                modifier = itemModifier,
                text = text(item),
                selected = selected(item),
                onClick = { onClick(item) }
            )
        }
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

    // 1) 按 key 优先
    if (defaultFocusKey != null) {
        val idxByKey = if (itemKey != null) {
            indexOfFirst { item -> itemKey(item) == defaultFocusKey }
        } else {
            // 没有 itemKey 时，退化为 item == defaultFocusKey（只有你把 item 自己当 key 才有意义）
            indexOfFirst { item -> item == defaultFocusKey }
        }
        if (idxByKey >= 0) return idxByKey
    }

    // 2) 回退到 index 规则
    return resolveFocusIndex(defaultFocusIndex)
}

/**
 * 把 index 规则转换为 0-based 下标。
 *
 * request 语义：
 * - null：不自动聚焦
 * - >0：1-based（1=第1个）
 * - <0：倒数（-1=最后一个）
 * - 0：非法（按你要求 -> 不聚焦）
 *
 * 越界：返回 null（不聚焦）
 */
private fun <T> List<T>.resolveFocusIndex(request: Int?): Int? {
    if (request == null) return null
    if (isEmpty()) return null
    if (request == 0) return null

    val index0 = if (request > 0) {
        request - 1          // 1 -> 0, 2 -> 1 ...
    } else {
        size + request       // -1 -> size-1, -2 -> size-2 ...
    }

    return index0.takeIf { it in indices }
}

@Composable
private fun RadioMenuSelectItem(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    // 高亮只认真实焦点（onFocusChanged）
    var hasFocus by remember { mutableStateOf(false) }

    ListItem(
        modifier = modifier.onFocusChanged { hasFocus = it.hasFocus },
        leadingContent = {
            RadioButton(
                modifier = Modifier.focusable(false),
                selected = selected,
                onClick = {}, // 交给 ListItem 的 onClick
                colors = settingsSelectRadioColors(hasFocus = hasFocus)
            )
        },
        headlineContent = { TvText(text = text) },
        onClick = onClick,
        selected = selected,
        colors = settingsSelectItemColors()
    )
}

@Composable
private fun settingsSelectItemColors() = ListItemDefaults.colors(
    // 默认（未聚焦/未按压/未选中）：透明 -> 露出父容器背景
    containerColor = Color.Transparent,
    contentColor = C.onSurface,

    // focused
    focusedContainerColor = C.primaryContainer,
    focusedContentColor = C.onSurface,

    // pressed
    pressedContainerColor = C.secondaryContainer,
    pressedContentColor = C.onSurface,

    // selected（未聚焦）
    selectedContainerColor = C.tertiaryContainer,
    selectedContentColor = C.onSurface,

    // disabled
    disabledContainerColor = C.surfaceVariant,
    disabledContentColor = C.disabled,

    // focused + selected
    focusedSelectedContainerColor = C.primaryContainer,
    focusedSelectedContentColor = C.onSurface,

    // pressed + selected
    pressedSelectedContainerColor = C.secondaryContainer,
    pressedSelectedContentColor = C.onSurface
)

@Composable
private fun settingsSelectRadioColors(hasFocus: Boolean) = RadioButtonDefaults.colors(
    selectedColor = if (hasFocus) C.primary else C.secondary,
    unselectedColor = if (hasFocus) C.primary else C.secondary,
    disabledSelectedColor = C.disabled,
    disabledUnselectedColor = C.disabled
)