package dev.aaa1115910.bv.component.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusTransitionGuard
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntrySurface
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryId
import dev.aaa1115910.bv.wjzfocus.WjzFocusComponentId
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.activateLayer
import dev.aaa1115910.bv.wjzfocus.defaultEntry
import dev.aaa1115910.bv.wjzfocus.resolve
import dev.aaa1115910.bv.wjzfocus.submitExternalEntryFocus
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.wjzfocus.wjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.rememberWjzFocusCoordinator
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.isConfirmKey
import dev.aaa1115910.bv.util.isKeyDown
import dev.aaa1115910.bv.util.isKeyUp

enum class SoftKeyboardType {
    English,
    Japanese,
    Symbol
}

internal val SoftKeyboardScopeId = WjzFocusScopeId("search/keyboard")
private const val SoftKeyboardFocusComponentId = "soft_keyboard"
internal val SoftKeyboardEntryId = WjzFocusComponentId(SoftKeyboardFocusComponentId).defaultEntry()
internal val SoftKeyboardFirstKeyLocalId = wjzFocusLocalId("key", "first")

private data class JapaneseKey(
    val label: String,
    val longPressInput: String? = null,
    val small: Boolean = false
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
    onFirstButtonPlaced: (() -> Unit)? = null
) {
    val parentCoordinator = LocalWjzFocusCoordinator.current
    val ownCoordinator = rememberWjzFocusCoordinator()
    val coordinator = parentCoordinator ?: ownCoordinator
    var keyboardTransitionLocked by remember { mutableStateOf(false) }
    // 记录进入符号键盘前的键盘类型（默认英文）
    var sourceKeyboardType by remember { mutableStateOf(SoftKeyboardType.English) }

    LaunchedEffect(keyboardType) {
    // 如果当前不是符号键盘，就记录下来
        if (keyboardType != SoftKeyboardType.Symbol) {
            sourceKeyboardType = keyboardType
        }

        keyboardTransitionLocked = true
        withFrameNanos { }
        keyboardTransitionLocked = false
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
        scopeId = SoftKeyboardScopeId
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
        WjzFocusTransitionGuard(locked = keyboardTransitionLocked)
        when (keyboardType) {
            SoftKeyboardType.English -> EnglishKeyboardLayout(
                showSearchWithProxy = showSearchWithProxy,
                enableSearchWithProxy = enableSearchWithProxy,
                onClick = onClick,
                onClear = onClear,
                onDelete = onDelete,
                onMoveCursorLeft = onMoveCursorLeft,
                onMoveCursorRight = onMoveCursorRight,
                onSearch = onSearch,
                onOpenSymbolKeyboard = onOpenSymbolKeyboard,
                onKeyboardTypeChange = onKeyboardTypeChange,
                onEnableSearchWithProxyChange = onEnableSearchWithProxyChange,
                onFirstButtonPlaced = onFirstButtonPlaced
            )

            SoftKeyboardType.Japanese -> JapaneseKeyboardLayout(
                onClick = onClick,
                onClear = onClear,
                onDelete = onDelete,
                onMoveCursorLeft = onMoveCursorLeft,
                onMoveCursorRight = onMoveCursorRight,
                onSearch = onSearch,
                onOpenSymbolKeyboard = onOpenSymbolKeyboard,
                onKeyboardTypeChange = onKeyboardTypeChange,
                onFirstButtonPlaced = onFirstButtonPlaced
            )

            SoftKeyboardType.Symbol -> SymbolKeyboardLayout(
                onClick = onClick,
                onClear = onClear,
                onDelete = onDelete,
                onMoveCursorLeft = onMoveCursorLeft,
                onMoveCursorRight = onMoveCursorRight,
                onSearch = onSearch,
                onKeyboardTypeChange = onKeyboardTypeChange,
                sourceKeyboardType = sourceKeyboardType,
                onFirstButtonPlaced = onFirstButtonPlaced
            )
        }
    }
}

@Composable
private fun EnglishKeyboardControlsRow(
    reversed: Boolean,
    onOpenSymbolKeyboard: () -> Unit,
    onMoveCursorRight: () -> Unit,
    onMoveCursorLeft: () -> Unit,
    onSearch: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit
) {
    val cells = listOf<@Composable () -> Unit>(
        {
            SoftKeyboardIconKey(
                painterRes = R.drawable.emoji_symbols,
                onClick = onOpenSymbolKeyboard
            )
        },
        {
            SoftKeyboardKey(
                key = "◂",
                onClick = onMoveCursorLeft
            )
        },
        {
            SoftKeyboardKey(
                key = "▸",
                onClick = onMoveCursorRight
            )
        },
        {
            SoftKeyboardIconKey(
                painterRes = R.drawable.search,
                onClick = onSearch
            )
        },
        {
            SoftKeyboardIconKey(
                painterRes = R.drawable.backspace,
                onClick = onDelete
            )
        },
        {
            SoftKeyboardIconKey(
                painterRes = R.drawable.delete,
                onClick = onClear
            )
        }
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        (if (reversed) cells.reversed() else cells).forEach { cell ->
            cell()
        }
    }
}

@Composable
private fun EnglishKeyboardLayout(
    modifier: Modifier = Modifier,
    showSearchWithProxy: Boolean,
    enableSearchWithProxy: Boolean,
    onClick: (String) -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onMoveCursorLeft: () -> Unit,
    onMoveCursorRight: () -> Unit,
    onSearch: () -> Unit,
    onOpenSymbolKeyboard: () -> Unit,
    onKeyboardTypeChange: (SoftKeyboardType) -> Unit,
    onEnableSearchWithProxyChange: (Boolean) -> Unit,
    onFirstButtonPlaced: (() -> Unit)? = null
) {
    var firstButtonPlacedNotified by remember { mutableStateOf(false) }

    val keys = listOf(
        listOf("A", "B", "C", "D", "E", "F"),
        listOf("G", "H", "I", "J", "K", "L"),
        listOf("M", "N", "O", "P", "Q", "R"),
        listOf("S", "T", "U", "V", "W", "X"),
        listOf("Y", "Z", "1", "2", "3", "4"),
        listOf("5", "6", "7", "8", "9", "0")
    )

    Column(
        modifier = modifier.width(258.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        EnglishKeyboardControlsRow(
            reversed = false,
            onOpenSymbolKeyboard = onOpenSymbolKeyboard,
            onMoveCursorRight = onMoveCursorRight,
            onMoveCursorLeft = onMoveCursorLeft,
            onSearch = onSearch,
            onDelete = onDelete,
            onClear = onClear
        )

        keys.forEachIndexed { rowIndex, rowKeys ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowKeys.forEachIndexed { index, key ->
                    val keyModifier = if (rowIndex == 0 && index == 0) {
                        Modifier
                            .wjzFocusExits(
                                localId = SoftKeyboardFirstKeyLocalId,
                                layer = WjzFocusLayer.Content
                            )
                            .onGloballyPositioned {
                                if (!firstButtonPlacedNotified) {
                                    firstButtonPlacedNotified = true
                                    onFirstButtonPlaced?.invoke()
                                }
                            }
                    } else {
                        Modifier
                    }
                    SoftKeyboardKey(
                        modifier = keyModifier,
                        key = key,
                        onClick = { onClick(key) },
                        onLongClick = if (key.length == 1 && key[0] in 'A'..'Z') {
                            { onClick(key.lowercase()) }
                        } else {
                            null
                        }
                    )
                }
            }
        }

        EnglishKeyboardControlsRow(
            reversed = true,
            onOpenSymbolKeyboard = onOpenSymbolKeyboard,
            onMoveCursorRight = onMoveCursorRight,
            onMoveCursorLeft = onMoveCursorLeft,
            onSearch = onSearch,
            onDelete = onDelete,
            onClear = onClear
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            SoftKeyboardKanaCaseKey(onClick = { onKeyboardTypeChange(SoftKeyboardType.Japanese) })
        }
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
private fun JapaneseKeyboardLayout(
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onMoveCursorLeft: () -> Unit,
    onMoveCursorRight: () -> Unit,
    onSearch: () -> Unit,
    onOpenSymbolKeyboard: () -> Unit,
    onKeyboardTypeChange: (SoftKeyboardType) -> Unit,
    onFirstButtonPlaced: (() -> Unit)? = null
) {
    var firstButtonPlacedNotified by remember { mutableStateOf(false) }
    val keys = listOf(
        listOf(
            JapaneseKey("1"),
            JapaneseKey("2"),
            JapaneseKey("3"),
            JapaneseKey("4"),
            JapaneseKey("5"),
            JapaneseKey("6"),
            JapaneseKey("7"),
            JapaneseKey("8"),
            JapaneseKey("9"),
            JapaneseKey("0")
        ),
        listOf(
            JapaneseKey("あ", longPressInput = "ア"),
            JapaneseKey("い", longPressInput = "イ"),
            JapaneseKey("う", longPressInput = "ウ"),
            JapaneseKey("え", longPressInput = "エ"),
            JapaneseKey("お", longPressInput = "オ"),
            JapaneseKey("か", longPressInput = "カ"),
            JapaneseKey("が", longPressInput = "ガ"),
            JapaneseKey("き", longPressInput = "キ"),
            JapaneseKey("ぎ", longPressInput = "ギ"),
            JapaneseKey("く", longPressInput = "ク")
        ),
        listOf(
            JapaneseKey("ぐ", longPressInput = "グ"),
            JapaneseKey("け", longPressInput = "ケ"),
            JapaneseKey("げ", longPressInput = "ゲ"),
            JapaneseKey("こ", longPressInput = "コ"),
            JapaneseKey("ご", longPressInput = "ゴ"),
            JapaneseKey("さ", longPressInput = "サ"),
            JapaneseKey("ざ", longPressInput = "ザ"),
            JapaneseKey("し", longPressInput = "シ"),
            JapaneseKey("じ", longPressInput = "ジ"),
            JapaneseKey("す", longPressInput = "ス")
        ),
        listOf(
            JapaneseKey("ず", longPressInput = "ズ"),
            JapaneseKey("せ", longPressInput = "セ"),
            JapaneseKey("ぜ", longPressInput = "ゼ"),
            JapaneseKey("そ", longPressInput = "ソ"),
            JapaneseKey("ぞ", longPressInput = "ゾ"),
            JapaneseKey("た", longPressInput = "タ"),
            JapaneseKey("だ", longPressInput = "ダ"),
            JapaneseKey("ち", longPressInput = "チ"),
            JapaneseKey("ぢ", longPressInput = "ヂ"),
            JapaneseKey("つ", longPressInput = "ツ")
        ),
        listOf(
            JapaneseKey("づ", longPressInput = "ヅ"),
            JapaneseKey("て", longPressInput = "テ"),
            JapaneseKey("で", longPressInput = "デ"),
            JapaneseKey("と", longPressInput = "ト"),
            JapaneseKey("ど", longPressInput = "ド"),
            JapaneseKey("な", longPressInput = "ナ"),
            JapaneseKey("に", longPressInput = "ニ"),
            JapaneseKey("ぬ", longPressInput = "ヌ"),
            JapaneseKey("ね", longPressInput = "ネ"),
            JapaneseKey("の", longPressInput = "ノ")
        ),
        listOf(
            JapaneseKey("は", longPressInput = "ハ"),
            JapaneseKey("ば", longPressInput = "バ"),
            JapaneseKey("ぱ", longPressInput = "パ"),
            JapaneseKey("ひ", longPressInput = "ヒ"),
            JapaneseKey("び", longPressInput = "ビ"),
            JapaneseKey("ぴ", longPressInput = "ピ"),
            JapaneseKey("ふ", longPressInput = "フ"),
            JapaneseKey("ぶ", longPressInput = "ブ"),
            JapaneseKey("ぷ", longPressInput = "プ"),
            JapaneseKey("へ", longPressInput = "ヘ")
        ),
        listOf(
            JapaneseKey("べ", longPressInput = "ベ"),
            JapaneseKey("ぺ", longPressInput = "ペ"),
            JapaneseKey("ほ", longPressInput = "ホ"),
            JapaneseKey("ぼ", longPressInput = "ボ"),
            JapaneseKey("ぽ", longPressInput = "ポ"),
            JapaneseKey("ま", longPressInput = "マ"),
            JapaneseKey("み", longPressInput = "ミ"),
            JapaneseKey("む", longPressInput = "ム"),
            JapaneseKey("め", longPressInput = "メ"),
            JapaneseKey("も", longPressInput = "モ")
        ),
        listOf(
            JapaneseKey("や", longPressInput = "ヤ"),
            JapaneseKey("ゃ", longPressInput = "ャ", small = true),
            JapaneseKey("ゐ", longPressInput = "ヰ"),
            JapaneseKey("ゆ", longPressInput = "ユ"),
            JapaneseKey("ゅ", longPressInput = "ュ", small = true),
            JapaneseKey("ゑ", longPressInput = "ヱ"),
            JapaneseKey("よ", longPressInput = "ヨ"),
            JapaneseKey("ょ", longPressInput = "ョ", small = true),
            JapaneseKey("ら", longPressInput = "ラ"),
            JapaneseKey("り", longPressInput = "リ")
        ),
        listOf(
            JapaneseKey("る", longPressInput = "ル"),
            JapaneseKey("れ", longPressInput = "レ"),
            JapaneseKey("ろ", longPressInput = "ロ"),
            JapaneseKey("わ", longPressInput = "ワ"),
            JapaneseKey("を", longPressInput = "ヲ"),
            JapaneseKey("ァ", longPressInput = "ぁ", small = true),
            JapaneseKey("ィ", longPressInput = "ぃ", small = true),
            JapaneseKey("ぅ", longPressInput = "ゥ", small = true),
            JapaneseKey("ェ", longPressInput = "ぇ", small = true),
            JapaneseKey("ォ", longPressInput = "ぉ", small = true)
        )
    )

    Column(
        modifier = modifier.width(434.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        JapaneseKeyboardControlsRow(
            reversed = false,
            onClick = onClick,
            onClear = onClear,
            onDelete = onDelete,
            onMoveCursorLeft = onMoveCursorLeft,
            onMoveCursorRight = onMoveCursorRight,
            onSearch = onSearch,
            onOpenSymbolKeyboard = onOpenSymbolKeyboard,
            onKeyboardTypeChange = onKeyboardTypeChange
        )
        keys.forEachIndexed { rowIndex, rowKeys ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowKeys.forEachIndexed { index, key ->
                    val keyModifier = if (rowIndex == 1 && index == 0) {
                        Modifier
                            .wjzFocusExits(
                                localId = SoftKeyboardFirstKeyLocalId,
                                layer = WjzFocusLayer.Content
                            )
                            .onGloballyPositioned {
                                if (!firstButtonPlacedNotified) {
                                    firstButtonPlacedNotified = true
                                    onFirstButtonPlaced?.invoke()
                                }
                            }
                    } else {
                        Modifier
                    }

                    SoftKeyboardKey(
                        modifier = keyModifier,
                        key = key.label,
                        small = key.small,
                        onClick = { onClick(key.label) },
                        onLongClick = key.longPressInput?.let { longPressInput ->
                            { onClick(longPressInput) }
                        }
                    )
                }
            }
        }
        JapaneseKeyboardControlsRow(
            reversed = true,
            onClick = onClick,
            onClear = onClear,
            onDelete = onDelete,
            onMoveCursorLeft = onMoveCursorLeft,
            onMoveCursorRight = onMoveCursorRight,
            onSearch = onSearch,
            onOpenSymbolKeyboard = onOpenSymbolKeyboard,
            onKeyboardTypeChange = onKeyboardTypeChange
        )
    }
}

@Composable
private fun SymbolKeyboardLayout(
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onMoveCursorLeft: () -> Unit,
    onMoveCursorRight: () -> Unit,
    onSearch: () -> Unit,
    onKeyboardTypeChange: (SoftKeyboardType) -> Unit,
    sourceKeyboardType: SoftKeyboardType,
    onFirstButtonPlaced: (() -> Unit)? = null
) {
    var firstButtonPlacedNotified by remember { mutableStateOf(false) }

    // 将所有按键拍平到一个列表中，方便自动“往前补”和换行
    val allCells = mutableListOf<@Composable () -> Unit>()

    // 顶部控制键
    allCells.add { SoftKeyboardDisabledIconKey(painterRes = R.drawable.emoji_symbols) }
    allCells.add { SoftKeyboardKey(key = "◂", onClick = onMoveCursorLeft) }
    allCells.add { SoftKeyboardKey(key = "▸", onClick = onMoveCursorRight) }
    allCells.add { SoftKeyboardIconKey(painterRes = R.drawable.search, onClick = onSearch) }
    allCells.add { SoftKeyboardIconKey(painterRes = R.drawable.backspace, onClick = onDelete) }
    allCells.add { SoftKeyboardIconKey(painterRes = R.drawable.delete, onClick = onClear) }
    // 单格语言切换按钮
    allCells.add {
        val iconRes = if (sourceKeyboardType == SoftKeyboardType.Japanese) {
            R.drawable.language_japanese_kana
        } else {
            R.drawable.match_case
        }
        SoftKeyboardIconKey(
            painterRes = iconRes,
            onClick = { onKeyboardTypeChange(sourceKeyboardType) }
        )
    }

    // 符号按键
    val symbols = listOf(
        "·", "。", "，", "、", "？", "￥", "！", "：", "；",
        "【】", "【", "】", "“”", "“", "”", "‘’", "‘", "’",
        "《》", "〈", "〉", "……", "——", ",", ".", "?", "!",
        ":", ";", "\"\"", "\"", "''", "'", "...", "-", "_",
        "~", "@", "#", "$", "^", "&", "*", "()", "(",
        ")", "+", "=", "{}", "{", "}", "[]", "[", "]",
        "/", "\\", "|", "<>", "<", ">", "、", "。", "…",
        "！", "？", "「」", "「", "」", "『』", "『", "』", "«»",
        "«", "»", "-", "", "", "", "", "", "")

    symbols.forEachIndexed { index, key ->
        allCells.add {
            // 将默认焦点的 Modifier 挂载到第一个符号按键上
            val keyModifier = if (index == 0) {
                Modifier
                    .wjzFocusExits(
                        localId = SoftKeyboardFirstKeyLocalId,
                        layer = WjzFocusLayer.Content
                    )
                    .onGloballyPositioned {
                        if (!firstButtonPlacedNotified) {
                            firstButtonPlacedNotified = true
                            onFirstButtonPlaced?.invoke()
                        }
                    }
            } else {
                Modifier
            }

            SoftKeyboardKey(
                modifier = keyModifier,
                key = key,
                onClick = { onClick(key) }
            )
        }
    }

    // 底部控制键
    allCells.add { SoftKeyboardIconKey(painterRes = R.drawable.delete, onClick = onClear) }
    allCells.add { SoftKeyboardIconKey(painterRes = R.drawable.backspace, onClick = onDelete) }
    allCells.add { SoftKeyboardIconKey(painterRes = R.drawable.search, onClick = onSearch) }
    allCells.add { SoftKeyboardKey(key = "◂", onClick = onMoveCursorLeft) }
    allCells.add { SoftKeyboardKey(key = "▸", onClick = onMoveCursorRight) }

    // 按 8 列 11 行渲染
    Column(
        modifier = modifier.width(346.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        allCells.chunked(8).forEach { rowCells ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowCells.forEach { cell ->
                    cell()
                }
            }
        }
    }
}

@Composable
private fun SymbolKeyboardControlsRow(
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onMoveCursorLeft: () -> Unit,
    onMoveCursorRight: () -> Unit,
    onSearch: () -> Unit,
    onKeyboardTypeChange: (SoftKeyboardType) -> Unit,
    sourceKeyboardType: SoftKeyboardType
) {
    val cells = listOf<@Composable () -> Unit>(
        {
            SoftKeyboardDisabledIconKey(
                painterRes = R.drawable.emoji_symbols
            )
        },
        {
            SoftKeyboardKey(
                key = "◂",
                onClick = onMoveCursorLeft
            )
        },
        {
            SoftKeyboardKey(
                key = "▸",
                onClick = onMoveCursorRight
            )
        },
        {
            SoftKeyboardIconKey(
                painterRes = R.drawable.search,
                onClick = onSearch
            )
        },
        {
            SoftKeyboardIconKey(
                painterRes = R.drawable.backspace,
                onClick = onDelete
            )
        },
        {
            SoftKeyboardIconKey(
                painterRes = R.drawable.delete,
                onClick = onClear
            )
        },
        {
            SoftKeyboardKanaCaseKey(
                onClick = { onKeyboardTypeChange(sourceKeyboardType) }
            )
        }
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        cells.forEach { cell -> cell() }
    }
}

@Composable
private fun SymbolKeyboardBottomControls(
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onMoveCursorLeft: () -> Unit,
    onMoveCursorRight: () -> Unit,
    onSearch: () -> Unit
) {
    SoftKeyboardIconKey(
        painterRes = R.drawable.delete,
        onClick = onClear
    )
    SoftKeyboardIconKey(
        painterRes = R.drawable.backspace,
        onClick = onDelete
    )
    SoftKeyboardIconKey(
        painterRes = R.drawable.search,
        onClick = onSearch
    )
    SoftKeyboardKey(
        key = "◂",
        onClick = onMoveCursorLeft
    )
    SoftKeyboardKey(
        key = "▸",
        onClick = onMoveCursorRight
    )
}

@Composable
private fun JapaneseKeyboardControlsRow(
    reversed: Boolean,
    onClick: (String) -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onMoveCursorLeft: () -> Unit,
    onMoveCursorRight: () -> Unit,
    onSearch: () -> Unit,
    onOpenSymbolKeyboard: () -> Unit,
    onKeyboardTypeChange: (SoftKeyboardType) -> Unit
) {
    val cells = listOf<@Composable () -> Unit>(
        {
            SoftKeyboardIconKey(
                painterRes = R.drawable.emoji_symbols,
                onClick = onOpenSymbolKeyboard
            )
        },
        {
            SoftKeyboardKey(
                key = "◂",
                onClick = onMoveCursorLeft
            )
        },
        {
            SoftKeyboardKey(
                key = "▸",
                onClick = onMoveCursorRight
            )
        },
        {
            SoftKeyboardIconKey(
                painterRes = R.drawable.search,
                onClick = onSearch
            )
        },
        {
            SoftKeyboardIconKey(
                painterRes = R.drawable.backspace,
                onClick = onDelete
            )
        },
        {
            SoftKeyboardIconKey(
                painterRes = R.drawable.delete,
                onClick = onClear
            )
        },
        {
            SoftKeyboardKanaCaseKey(
                onClick = { onKeyboardTypeChange(SoftKeyboardType.English) }
            )
        },
        {
            SoftKeyboardKey(
                key = "ー",
                onClick = { onClick("ヴ") }
            )
        },
        {
            SoftKeyboardKey(
                key = "っ",
                small = true,
                onClick = { onClick("っ") },
                onLongClick = { onClick("ッ") }
            )
        },
        {
            SoftKeyboardKey(
                key = "ん",
                small = true,
                onClick = { onClick("ん") },
                onLongClick = { onClick("ン") }
            )
        }
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        (if (reversed) cells.reversed() else cells).forEach { cell ->
            cell()
        }
    }
}

@Composable
fun SoftKeyboardKey(
    modifier: Modifier = Modifier,
    key: String,
    small: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    var longPressGuard by remember { mutableStateOf(false) }
    val keyModifier = if (onLongClick == null) {
        modifier
    } else {
        modifier.onKeyEvent { event ->
            if (!event.isConfirmKey()) {
                return@onKeyEvent false
            }

            if (longPressGuard) {
                if (event.isKeyUp()) {
                    longPressGuard = false
                }
                return@onKeyEvent true
            }

            if (event.isKeyDown() && event.nativeKeyEvent.isLongPress) {
                onLongClick()
                longPressGuard = true
                return@onKeyEvent true
            }

            false
        }
    }

    Surface(
        modifier = keyModifier,
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RectangleShape)
    ) {
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = key,
                textAlign = TextAlign.Center,
                style = if (small) {
                    MaterialTheme.typography.titleSmall
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
private fun SoftKeyboardDisabledIconKey(
    modifier: Modifier = Modifier,
    painterRes: Int
) {
    Box(
        modifier = modifier.size(38.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(id = painterRes),
            contentDescription = null,
            tint = C.disabled
        )
    }
}

@Composable
private fun SoftKeyboardSpacer() {
    Spacer(modifier = Modifier.size(38.dp))
}

@Composable
private fun SoftKeyboardKanaCaseKey(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RectangleShape)
    ) {
        Box(
            modifier = Modifier
                .width(82.dp)
                .height(38.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 9.dp)
                    .size(20.dp),
                painter = painterResource(id = R.drawable.match_case),
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
                painter = painterResource(id = R.drawable.language_japanese_kana),
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
