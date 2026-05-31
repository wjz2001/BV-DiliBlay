package dev.aaa1115910.bv.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import androidx.tv.material3.Text as TvText
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits

private fun WjzFocusNodeId.toDialogLocalFocusId(dialogScopeId: WjzFocusScopeId?): String {
    val scopePrefix = dialogScopeId?.value?.let { "$it/" }
    return if (scopePrefix != null && value.startsWith(scopePrefix)) {
        value.removePrefix(scopePrefix)
    } else {
        value
    }
}

/**
 * 单选列表 Dialog（TV 焦点友好）。
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
    sourceScopeId: WjzFocusScopeId? = null,
    dialogScopeId: WjzFocusScopeId? = null,
    containerNodeId: WjzFocusNodeId? = null,
    itemNodeId: ((T) -> WjzFocusNodeId)? = null
) {
    if (!visible) return

    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val maxHeightDp = with(density) {
        (windowInfo.containerSize.height * maxHeightFraction).toDp()
    }

    val titleSlot: (@Composable () -> Unit)? =
        title.takeIf { it.isNotBlank() }?.let { t -> { M3Text(t) } }

    TvAlertDialog(
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
                dialogScopeId = dialogScopeId,
                itemNodeId = itemNodeId,
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
        sourceScopeId = sourceScopeId,
        dialogScopeId = dialogScopeId,
        containerNodeId = containerNodeId
    )
}

@Composable
internal fun <T> RadioMenuSelectListContent(
    modifier: Modifier = Modifier, // ✅ 作用于 LazyColumn（heightIn/maxHeight/padding 等）
    items: List<T>,
    selected: (T) -> Boolean,
    onClick: (T) -> Unit,
    text: (T) -> String,

    // key & default focus
    itemKey: ((T) -> Any)? = null,
    defaultFocusKey: Any? = null,
    defaultFocusIndex: Int? = null,
    dialogScopeId: WjzFocusScopeId? = null,
    itemNodeId: ((T) -> WjzFocusNodeId)? = null,

    // layout
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
) {
    val listState = rememberLazyListState()

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
            val itemNode = itemNodeId?.invoke(item)
                ?: WjzFocusNodeId("dialog/radio-menu/item/${itemKey?.invoke(item) ?: index}")
            var hasFocus by remember { mutableStateOf(false) }
            val itemModifier = Modifier.wjzFocusExits(
                id = itemNode.toDialogLocalFocusId(dialogScopeId),
                layer = WjzFocusLayer.Dialog,
                onFocusChanged = { hasFocus = it }
            )

            RadioMenuSelectItem(
                modifier = itemModifier,
                text = text(item),
                selected = selected(item),
                hasFocus = hasFocus,
                onClick = { onClick(item) }
            )
        }
    }
}

@Composable
private fun RadioMenuSelectItem(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    hasFocus: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = modifier,
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = null, // 交给 ListItem 的 onClick
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
