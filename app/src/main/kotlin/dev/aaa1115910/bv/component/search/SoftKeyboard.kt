package dev.aaa1115910.bv.component.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Checkbox
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.wjzfocus.WjzFocusHost
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntrySurface
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryId
import dev.aaa1115910.bv.wjzfocus.WjzFocusComponentId
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.activateLayer
import dev.aaa1115910.bv.wjzfocus.defaultEntry
import dev.aaa1115910.bv.wjzfocus.resolve
import dev.aaa1115910.bv.wjzfocus.submitExternalEntryFocus
import dev.aaa1115910.bv.wjzfocus.up
import dev.aaa1115910.bv.wjzfocus.wjzClickableFocus
import dev.aaa1115910.bv.wjzfocus.wjzFocusHostExits
import dev.aaa1115910.bv.wjzfocus.wjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.rememberWjzFocusCoordinator
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.ui.theme.BVTheme

enum class SoftKeyboardType {
    English,
    Japanese,
    Symbol
}

internal val SoftKeyboardScopeId = WjzFocusScopeId("search/keyboard")
private const val SoftKeyboardFocusComponentId = "soft_keyboard"
internal val SoftKeyboardEntryId = WjzFocusComponentId(SoftKeyboardFocusComponentId).defaultEntry()
internal val SoftKeyboardFirstKeyLocalId = wjzFocusLocalId("key", "first")
private const val SoftKeyboardColumnCount = 10
private const val SoftKeyboardRowCount = 11
private val SoftKeyboardCellSize = 38.dp
private val SoftKeyboardCellSpacing = 6.dp

private fun softKeyboardSlotLocalId(rowIndex: Int, columnIndex: Int) =
    wjzFocusLocalId("key", "slot", rowIndex, columnIndex)

private data class JapaneseKey(
    val label: String,
    val longPressInput: String? = null,
    val small: Boolean = false
)

private data class SoftKeyboardSlot(
    val row: Int,
    val column: Int
)

private sealed interface SoftKeyboardAction {
    data class Input(val value: String) : SoftKeyboardAction
    data class ChangeKeyboardType(val type: SoftKeyboardType) : SoftKeyboardAction
    object Clear : SoftKeyboardAction
    object Delete : SoftKeyboardAction
    object MoveCursorLeft : SoftKeyboardAction
    object MoveCursorRight : SoftKeyboardAction
    object Search : SoftKeyboardAction
    object OpenSymbolKeyboard : SoftKeyboardAction
}

private data class SoftKeyboardCell(
    val label: String? = null,
    val painterRes: Int? = null,
    val secondPainterRes: Int? = null,
    val small: Boolean = false,
    val span: Int = 1,
    val first: Boolean = false,
    val action: SoftKeyboardAction,
    val longPressAction: SoftKeyboardAction? = null
)

@Composable
fun SoftKeyboard(
    modifier: Modifier = Modifier,
    keyboardType: SoftKeyboardType = SoftKeyboardType.English,
    showSearchWithProxy: Boolean,
    enableSearchWithProxy: Boolean,
    onClick: (String) -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onMoveCursorLeft: () -> Unit,
    onMoveCursorRight: () -> Unit,
    onSearch: () -> Unit,
    onOpenSymbolKeyboard: () -> Unit = {},
    onKeyboardTypeChange: (SoftKeyboardType) -> Unit = {},
    onEnableSearchWithProxyChange: (Boolean) -> Unit,
    backEntryId: WjzFocusEntryId? = null,
    upEntryId: WjzFocusEntryId? = null,
    onFirstButtonPlaced: (() -> Unit)? = null
) {
    val parentCoordinator = LocalWjzFocusCoordinator.current
    val ownCoordinator = rememberWjzFocusCoordinator()
    val coordinator = parentCoordinator ?: ownCoordinator
    // 记录进入符号键盘前的键盘类型（默认英文）
    var sourceKeyboardType by remember { mutableStateOf(SoftKeyboardType.English) }

    LaunchedEffect(keyboardType) {
    // 如果当前不是符号键盘，就记录下来
        if (keyboardType != SoftKeyboardType.Symbol) {
            sourceKeyboardType = keyboardType
        }
    }

    val onKeyboardFirstButtonPlaced = {
        onFirstButtonPlaced?.invoke()
        Unit
    }

    WjzFocusHost(
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.key != Key.Back || event.type != KeyEventType.KeyDown) {
                return@onPreviewKeyEvent false
            }

            val entryId = backEntryId ?: return@onPreviewKeyEvent false
            coordinator.submitExternalEntryFocus(
                entryId = entryId,
                layerActivation = activateLayer,
                dedupeKey = "soft-keyboard-back-entry"
            )
            true
        },
        coordinator = coordinator,
        layer = WjzFocusLayer.Content,
        scopeId = SoftKeyboardScopeId,
        exits = wjzFocusHostExits {
            upEntryId?.let { up move it }
        }
    ) {
        WjzFocusEntrySurface(
            componentId = SoftKeyboardFocusComponentId,
            default = {
                defaultEntry(
                    nodeId = SoftKeyboardScopeId.resolve(SoftKeyboardFirstKeyLocalId),
                    layer = WjzFocusLayer.Content,
                    scopeId = SoftKeyboardScopeId
                )
            }
        )
        SoftKeyboardGridLayout(
            keyboardType = keyboardType,
            showSearchWithProxy = showSearchWithProxy,
            enableSearchWithProxy = enableSearchWithProxy,
            sourceKeyboardType = sourceKeyboardType,
            onClick = onClick,
            onClear = onClear,
            onDelete = onDelete,
            onMoveCursorLeft = onMoveCursorLeft,
            onMoveCursorRight = onMoveCursorRight,
            onSearch = onSearch,
            onOpenSymbolKeyboard = onOpenSymbolKeyboard,
            onKeyboardTypeChange = onKeyboardTypeChange,
            onEnableSearchWithProxyChange = onEnableSearchWithProxyChange,
            onFirstButtonPlaced = onKeyboardFirstButtonPlaced
        )
    }
}

@Composable
private fun SoftKeyboardGridLayout(
    keyboardType: SoftKeyboardType,
    showSearchWithProxy: Boolean,
    enableSearchWithProxy: Boolean,
    sourceKeyboardType: SoftKeyboardType,
    onClick: (String) -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onMoveCursorLeft: () -> Unit,
    onMoveCursorRight: () -> Unit,
    onSearch: () -> Unit,
    onOpenSymbolKeyboard: () -> Unit,
    onKeyboardTypeChange: (SoftKeyboardType) -> Unit,
    onEnableSearchWithProxyChange: (Boolean) -> Unit,
    onFirstButtonPlaced: (() -> Unit)?
) {
    val rows = remember(keyboardType, sourceKeyboardType) {
        normalizedKeyboardRows(
            when (keyboardType) {
                SoftKeyboardType.English -> englishKeyboardRows()
                SoftKeyboardType.Japanese -> japaneseKeyboardRows()
                SoftKeyboardType.Symbol -> symbolKeyboardRows(sourceKeyboardType = sourceKeyboardType)
            }
        )
    }
    val onAction: (SoftKeyboardAction) -> Unit = { action ->
        when (action) {
            is SoftKeyboardAction.Input -> onClick(action.value)
            is SoftKeyboardAction.ChangeKeyboardType -> onKeyboardTypeChange(action.type)
            SoftKeyboardAction.Clear -> onClear()
            SoftKeyboardAction.Delete -> onDelete()
            SoftKeyboardAction.MoveCursorLeft -> onMoveCursorLeft()
            SoftKeyboardAction.MoveCursorRight -> onMoveCursorRight()
            SoftKeyboardAction.Search -> onSearch()
            SoftKeyboardAction.OpenSymbolKeyboard -> onOpenSymbolKeyboard()
        }
    }

    Column(
        modifier = Modifier.width(
            SoftKeyboardCellSize * SoftKeyboardColumnCount +
                    SoftKeyboardCellSpacing * (SoftKeyboardColumnCount - 1)
        ),
        verticalArrangement = Arrangement.spacedBy(SoftKeyboardCellSpacing)
    ) {
        SoftKeyboardGrid(
            rows = rows,
            onAction = onAction,
            onFirstButtonPlaced = onFirstButtonPlaced
        )

        if (showSearchWithProxy) {
            Surface(
                modifier = Modifier,
                onClick = { onEnableSearchWithProxyChange(!enableSearchWithProxy) },
                shape = ClickableSurfaceDefaults.shape(shape = RectangleShape),
                colors = ClickableSurfaceDefaults.colors(
                    focusedContainerColor = C.inverseSurface,
                    pressedContainerColor = C.inverseSurface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = enableSearchWithProxy,
                        onCheckedChange = { onEnableSearchWithProxyChange(it) },
                    )
                    Text(text = "通过代理搜索")
                }
            }
        }
    }
}

@Composable
private fun SoftKeyboardGrid(
    rows: List<List<SoftKeyboardCell?>>,
    onAction: (SoftKeyboardAction) -> Unit,
    onFirstButtonPlaced: (() -> Unit)?
) {
    val slots = remember(rows) {
        buildList {
            rows.forEachIndexed { rowIndex, rowCells ->
                rowCells.forEachIndexed { columnIndex, cell ->
                    if (cell != null) {
                        add(SoftKeyboardSlot(row = rowIndex, column = columnIndex))
                    }
                }
            }
        }
    }

    Layout(
        content = {
            slots.forEach { slot ->
                val cell = rows[slot.row][slot.column] ?: return@forEach
                SoftKeyboardGridCell(
                    cell = cell,
                    slot = slot,
                    onAction = onAction,
                    onPlaced = if (cell.first) {
                        onFirstButtonPlaced
                    } else {
                        null
                    }
                )
            }
        }
    ) { measurables, constraints ->
        val cellSize = SoftKeyboardCellSize.roundToPx()
        val spacing = SoftKeyboardCellSpacing.roundToPx()
        val width = cellSize * SoftKeyboardColumnCount + spacing * (SoftKeyboardColumnCount - 1)
        val height = cellSize * SoftKeyboardRowCount + spacing * (SoftKeyboardRowCount - 1)
        val placeables = measurables.mapIndexed { index, measurable ->
            val cell = rows[slots[index].row][slots[index].column]
            val span = cell?.span ?: 1
            val cellWidth = cellSize * span + spacing * (span - 1)
            measurable.measure(
                constraints.copy(
                    minWidth = cellWidth,
                    maxWidth = cellWidth,
                    minHeight = cellSize,
                    maxHeight = cellSize
                )
            )
        }

        layout(
            width = width.coerceIn(constraints.minWidth, constraints.maxWidth),
            height = height.coerceIn(constraints.minHeight, constraints.maxHeight)
        ) {
            placeables.forEachIndexed { index, placeable ->
                val slot = slots[index]
                placeable.placeRelative(
                    x = slot.column * (cellSize + spacing),
                    y = slot.row * (cellSize + spacing)
                )
            }
        }
    }
}

@Composable
private fun SoftKeyboardGridCell(
    cell: SoftKeyboardCell,
    slot: SoftKeyboardSlot,
    onAction: (SoftKeyboardAction) -> Unit,
    onPlaced: (() -> Unit)?
) {
    var placedNotified by remember(slot, cell.first) { mutableStateOf(false) }
    val localId = if (cell.first) {
        SoftKeyboardFirstKeyLocalId
    } else {
        softKeyboardSlotLocalId(slot.row, slot.column)
    }
    val modifier = Modifier
        .width(SoftKeyboardCellSize * cell.span + SoftKeyboardCellSpacing * (cell.span - 1))
        .height(SoftKeyboardCellSize)
        .wjzClickableFocus(
            localId = localId,
            onClick = { onAction(cell.action) },
            onLongClick = cell.longPressAction?.let { longPressAction ->
                { onAction(longPressAction) }
            },
            layer = WjzFocusLayer.Content
        )
        .onGloballyPositioned {
            if (!placedNotified) {
                placedNotified = true
                onPlaced?.invoke()
            }
        }

    SoftKeyboardVisualCell(
        modifier = modifier,
        label = cell.label,
        painterRes = cell.painterRes,
        secondPainterRes = cell.secondPainterRes,
        small = cell.small
    )
}

@Composable
private fun SoftKeyboardVisualCell(
    modifier: Modifier,
    label: String?,
    painterRes: Int?,
    secondPainterRes: Int?,
    small: Boolean
) {
    Surface(
        modifier = modifier,
        shape = RectangleShape
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (painterRes != null && secondPainterRes != null) {
                Icon(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 9.dp)
                        .size(20.dp),
                    painter = painterResource(id = painterRes),
                    contentDescription = null
                )
                Text(
                    text = "/",
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 9.dp)
                        .size(20.dp),
                    painter = painterResource(id = secondPainterRes),
                    contentDescription = null
                )
            } else if (painterRes != null) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(id = painterRes),
                    contentDescription = null
                )
            } else if (label != null) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    text = label,
                    textAlign = TextAlign.Center,
                    style = if (small) {
                        MaterialTheme.typography.titleSmall
                    } else if (label.length > 1) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.titleMedium
                    }
                )
            }
        }
    }
}

private fun emptyKeyboardRow(): MutableList<SoftKeyboardCell?> {
    return MutableList(SoftKeyboardColumnCount) { null }
}

private fun normalizedKeyboardRows(rows: List<List<SoftKeyboardCell?>>): List<List<SoftKeyboardCell?>> {
    return buildList {
        rows.take(SoftKeyboardRowCount).forEach { row ->
            add(row + List((SoftKeyboardColumnCount - row.size).coerceAtLeast(0)) { null })
        }
        repeat((SoftKeyboardRowCount - size).coerceAtLeast(0)) {
            add(emptyKeyboardRow())
        }
    }
}

private fun centeredKeyboardRow(cells: List<SoftKeyboardCell?>): List<SoftKeyboardCell?> {
    val row = emptyKeyboardRow()
    val start = ((SoftKeyboardColumnCount - cells.sumOf { it?.span ?: 1 }) / 2).coerceAtLeast(0)
    var column = start
    cells.forEach { cell ->
        if (column >= SoftKeyboardColumnCount) return@forEach
        row[column] = cell
        column += cell?.span ?: 1
    }
    return row
}

private fun softKeyboardTextCell(
    label: String,
    first: Boolean = false,
    small: Boolean = false,
    action: SoftKeyboardAction = SoftKeyboardAction.Input(label),
    longPressAction: SoftKeyboardAction? = null
): SoftKeyboardCell {
    return SoftKeyboardCell(
        label = label,
        small = small,
        first = first,
        action = action,
        longPressAction = longPressAction
    )
}

private fun softKeyboardIconCell(
    painterRes: Int,
    span: Int = 1,
    action: SoftKeyboardAction
): SoftKeyboardCell {
    return SoftKeyboardCell(
        painterRes = painterRes,
        span = span,
        action = action
    )
}

private fun softKeyboardDoubleIconCell(
    firstPainterRes: Int,
    secondPainterRes: Int,
    span: Int,
    action: SoftKeyboardAction
): SoftKeyboardCell {
    return SoftKeyboardCell(
        painterRes = firstPainterRes,
        secondPainterRes = secondPainterRes,
        span = span,
        action = action
    )
}

private fun softKeyboardInputCell(
    label: String,
    first: Boolean = false,
    small: Boolean = false,
    longPressInput: String? = null
) = softKeyboardTextCell(
    label = label,
    first = first,
    small = small,
    longPressAction = longPressInput?.let { SoftKeyboardAction.Input(it) }
)

private fun softKeyboardDigitCell(label: String, first: Boolean = false) =
    softKeyboardInputCell(label = label, first = first)

private fun softKeyboardOpenSymbolCell() =
    softKeyboardIconCell(R.drawable.emoji_symbols, action = SoftKeyboardAction.OpenSymbolKeyboard)

private fun softKeyboardMoveCursorLeftCell() =
    softKeyboardTextCell("◄", action = SoftKeyboardAction.MoveCursorLeft)

private fun softKeyboardMoveCursorRightCell() =
    softKeyboardTextCell("►", action = SoftKeyboardAction.MoveCursorRight)

private fun softKeyboardSearchCell() =
    softKeyboardIconCell(R.drawable.search, action = SoftKeyboardAction.Search)

private fun softKeyboardDeleteCell() =
    softKeyboardIconCell(R.drawable.backspace, action = SoftKeyboardAction.Delete)

private fun softKeyboardClearCell() =
    softKeyboardIconCell(R.drawable.delete, action = SoftKeyboardAction.Clear)

private fun softKeyboardLanguageIconRes(keyboardType: SoftKeyboardType) = when (keyboardType) {
    SoftKeyboardType.English -> R.drawable.match_case
    SoftKeyboardType.Japanese -> R.drawable.language_japanese_kana
    SoftKeyboardType.Symbol -> R.drawable.emoji_symbols
}

private fun softKeyboardLanguageSwitchCell(
    targetType: SoftKeyboardType,
    span: Int = 1,
    doubleIcon: Boolean = false
): SoftKeyboardCell {
    val action = SoftKeyboardAction.ChangeKeyboardType(targetType)
    return if (doubleIcon) {
        softKeyboardDoubleIconCell(
            firstPainterRes = R.drawable.match_case,
            secondPainterRes = R.drawable.language_japanese_kana,
            span = span,
            action = action
        )
    } else {
        softKeyboardIconCell(
            painterRes = softKeyboardLanguageIconRes(targetType),
            span = span,
            action = action
        )
    }
}

private fun englishControlCells(
    reversed: Boolean
): List<SoftKeyboardCell?> {
    val cells = listOf(
        softKeyboardOpenSymbolCell(),
        softKeyboardMoveCursorLeftCell(),
        softKeyboardMoveCursorRightCell(),
        softKeyboardSearchCell(),
        softKeyboardDeleteCell(),
        softKeyboardClearCell()
    )
    return if (reversed) cells.reversed() else cells
}

private fun englishKeyboardRows(): List<List<SoftKeyboardCell?>> {
    val keyRows = listOf(
        listOf("A", "B", "C", "D", "E", "F"),
        listOf("G", "H", "I", "J", "K", "L"),
        listOf("M", "N", "O", "P", "Q", "R"),
        listOf("S", "T", "U", "V", "W", "X"),
        listOf("Y", "Z", "1", "2", "3", "4"),
        listOf("5", "6", "7", "8", "9", "0")
    )
    return buildList {
        add(centeredKeyboardRow(englishControlCells(reversed = false)))
        keyRows.forEachIndexed { rowIndex, rowKeys ->
            add(centeredKeyboardRow(rowKeys.mapIndexed { index, label ->
                if (label.length == 1 && label[0] in '0'..'9') {
                    softKeyboardDigitCell(label = label)
                } else {
                    softKeyboardInputCell(
                        label = label,
                        first = rowIndex == 0 && index == 0,
                        longPressInput = if (label.length == 1 && label[0] in 'A'..'Z') {
                            label.lowercase()
                        } else {
                            null
                        }
                    )
                }
            }))
        }
        add(centeredKeyboardRow(englishControlCells(reversed = true)))
        add(centeredKeyboardRow(listOf(
            softKeyboardLanguageSwitchCell(
                targetType = SoftKeyboardType.Japanese,
                span = 2,
                doubleIcon = true
            )
        )))
    }
}

private fun japaneseControlCells(
    reversed: Boolean
): List<SoftKeyboardCell?> {
    val cells = listOf(
        softKeyboardOpenSymbolCell(),
        softKeyboardMoveCursorLeftCell(),
        softKeyboardMoveCursorRightCell(),
        softKeyboardSearchCell(),
        softKeyboardDeleteCell(),
        softKeyboardClearCell(),
        softKeyboardLanguageSwitchCell(targetType = SoftKeyboardType.English),
        softKeyboardTextCell("ー", action = SoftKeyboardAction.Input("ヴ")),
        softKeyboardInputCell(
            label = "っ",
            small = true,
            longPressInput = "ッ"
        ),
        softKeyboardInputCell(
            label = "ん",
            small = true,
            longPressInput = "ン"
        )
    )
    return if (reversed) cells.reversed() else cells
}

private fun symbolControlCells(
    sourceKeyboardType: SoftKeyboardType? = null
): List<SoftKeyboardCell?> {
    val cells = mutableListOf<SoftKeyboardCell?>(
        softKeyboardMoveCursorLeftCell(),
        softKeyboardMoveCursorRightCell(),
        softKeyboardSearchCell(),
        softKeyboardDeleteCell(),
        softKeyboardClearCell()
    )
    if (sourceKeyboardType != null) {
        cells.add(softKeyboardLanguageSwitchCell(targetType = sourceKeyboardType))
    }
    return cells
}

private fun japaneseKeyboardRows(): List<List<SoftKeyboardCell?>> {
    val keys = listOf(
        listOf(JapaneseKey("あ", "ア"), JapaneseKey("い", "イ"), JapaneseKey("う", "ウ"), JapaneseKey("え", "エ"), JapaneseKey("お", "オ"), JapaneseKey("か", "カ"), JapaneseKey("が", "ガ"), JapaneseKey("き", "キ"), JapaneseKey("ぎ", "ギ"), JapaneseKey("く", "ク")),
        listOf(JapaneseKey("ぐ", "グ"), JapaneseKey("け", "ケ"), JapaneseKey("げ", "ゲ"), JapaneseKey("こ", "コ"), JapaneseKey("ご", "ゴ"), JapaneseKey("さ", "サ"), JapaneseKey("ざ", "ザ"), JapaneseKey("し", "シ"), JapaneseKey("じ", "ジ"), JapaneseKey("す", "ス")),
        listOf(JapaneseKey("ず", "ズ"), JapaneseKey("せ", "セ"), JapaneseKey("ぜ", "ゼ"), JapaneseKey("そ", "ソ"), JapaneseKey("ぞ", "ゾ"), JapaneseKey("た", "タ"), JapaneseKey("だ", "ダ"), JapaneseKey("ち", "チ"), JapaneseKey("ぢ", "ヂ"), JapaneseKey("つ", "ツ")),
        listOf(JapaneseKey("づ", "ヅ"), JapaneseKey("て", "テ"), JapaneseKey("で", "デ"), JapaneseKey("と", "ト"), JapaneseKey("ど", "ド"), JapaneseKey("な", "ナ"), JapaneseKey("に", "ニ"), JapaneseKey("ぬ", "ヌ"), JapaneseKey("ね", "ネ"), JapaneseKey("の", "ノ")),
        listOf(JapaneseKey("は", "ハ"), JapaneseKey("ば", "バ"), JapaneseKey("ぱ", "パ"), JapaneseKey("ひ", "ヒ"), JapaneseKey("び", "ビ"), JapaneseKey("ぴ", "ピ"), JapaneseKey("ふ", "フ"), JapaneseKey("ぶ", "ブ"), JapaneseKey("ぷ", "プ"), JapaneseKey("へ", "ヘ")),
        listOf(JapaneseKey("べ", "ベ"), JapaneseKey("ぺ", "ペ"), JapaneseKey("ほ", "ホ"), JapaneseKey("ぼ", "ボ"), JapaneseKey("ぽ", "ポ"), JapaneseKey("ま", "マ"), JapaneseKey("み", "ミ"), JapaneseKey("む", "ム"), JapaneseKey("め", "メ"), JapaneseKey("も", "モ")),
        listOf(JapaneseKey("や", "ヤ"), JapaneseKey("ゃ", "ャ", true), JapaneseKey("ゐ", "ヰ"), JapaneseKey("ゆ", "ユ"), JapaneseKey("ゅ", "ュ", true), JapaneseKey("ゑ", "ヱ"), JapaneseKey("よ", "ヨ"), JapaneseKey("ょ", "ョ", true), JapaneseKey("ら", "ラ"), JapaneseKey("り", "リ")),
        listOf(JapaneseKey("る", "ル"), JapaneseKey("れ", "レ"), JapaneseKey("ろ", "ロ"), JapaneseKey("わ", "ワ"), JapaneseKey("を", "ヲ"), JapaneseKey("ァ", "ぁ", true), JapaneseKey("ィ", "ぃ", true), JapaneseKey("ぅ", "ゥ", true), JapaneseKey("ェ", "ぇ", true), JapaneseKey("ォ", "ぉ", true))
    )
    return buildList {
        add(japaneseControlCells(reversed = false))
        add(('1'..'9').map { softKeyboardDigitCell(it.toString()) } + softKeyboardDigitCell("0"))
        keys.forEachIndexed { rowIndex, rowKeys ->
            add(rowKeys.mapIndexed { index, key ->
                softKeyboardInputCell(
                    label = key.label,
                    first = rowIndex == 0 && index == 0,
                    small = key.small,
                    longPressInput = key.longPressInput
                )
            })
        }
        add(japaneseControlCells(reversed = true))
    }
}
private fun symbolKeyboardRows(
    sourceKeyboardType: SoftKeyboardType
): List<List<SoftKeyboardCell?>> {
    val symbols = listOf(
        "·", "。", "，", "、", "？", "￥", "！", "：", "；",
        "【】", "【", "】", "“”", "“", "”", "‘’", "‘", "’",
        "《》", "〈", "〉", "……", "——", ",", ".", "?", "!",
        ":", ";", "\"\"", "\"", "''", "'", "...", "-", "_",
        "~", "@", "#", "$", "^", "&", "*", "()", "(",
        ")", "+", "=", "{}", "{", "}", "[]", "[", "]",
        "/", "\\", "|", "<>", "<", ">", "、", "。", "…",
        "！", "？", "「」", "「", "」", "『』", "『", "』", "«»",
        "«", "»", "-"
    )
    val cells = buildList {
        add(null)
        addAll(symbolControlCells(sourceKeyboardType = sourceKeyboardType))
        symbols.forEachIndexed { index, label ->
            add(
                softKeyboardTextCell(
                    label = label,
                    first = index == 0,
                    action = SoftKeyboardAction.Input(label)
                )
            )
        }
        addAll(symbolControlCells().reversed())
    }
    return cells.chunked(8).map { centeredKeyboardRow(it) }
}

@Composable
fun SoftKeyboardKey(
    modifier: Modifier = Modifier,
    focusLocalId: WjzFocusLocalId? = null,
    key: String,
    small: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val keyModifier = if (focusLocalId == null) {
        modifier
    } else {
        modifier.wjzClickableFocus(
            localId = focusLocalId,
            onClick = onClick,
            onLongClick = onLongClick,
            layer = WjzFocusLayer.Content
        )
    }

    Surface(
        modifier = keyModifier,
        shape = RectangleShape
    ) {
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                text = key,
                textAlign = TextAlign.Center,
                style = if (small) {
                    MaterialTheme.typography.titleSmall
                } else if (key.length > 1) {
                    // 如果是多字符按键(如"【】")，使用更小的字体
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.titleMedium
                }
            )
        }
    }
}

@Composable
fun SoftKeyboardIconKey(
    modifier: Modifier = Modifier,
    painterRes: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RectangleShape)
    ) {
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(id = painterRes),
                contentDescription = null
            )
        }
    }
}

@Composable
fun SoftKeyboardButton(
    modifier: Modifier = Modifier,
    key: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(38.dp),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RectangleShape)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = key,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Preview
@Composable
private fun SoftKeyboardKeyPreview() {
    BVTheme {
        SoftKeyboardKey(
            key = "X",
            onClick = {}
        )
    }
}

@Preview
@Composable
private fun SoftKeyboardPreview() {
    BVTheme {
        SoftKeyboard(
            showSearchWithProxy = true,
            enableSearchWithProxy = true,
            onClick = {},
            onClear = {},
            onDelete = {},
            onMoveCursorLeft = {},
            onMoveCursorRight = {},
            onSearch = {},
            onEnableSearchWithProxyChange = {}
        )
    }
}
