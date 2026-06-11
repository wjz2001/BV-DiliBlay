package dev.aaa1115910.bv.screen.search

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.search.Hotword
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusComponentId
import dev.aaa1115910.bv.wjzfocus.WjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.WjzFocusLocalEntryId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusHost
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.WjzFocusTopologyRegionRef
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryId
import dev.aaa1115910.bv.wjzfocus.activateLayer
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.wjzfocus.rememberWjzFocusCoordinator
import dev.aaa1115910.bv.component.search.SearchKeyword
import dev.aaa1115910.bv.component.search.SoftKeyboard
import dev.aaa1115910.bv.component.search.SoftKeyboardEntryId
import dev.aaa1115910.bv.component.search.SoftKeyboardFirstKeyLocalId
import dev.aaa1115910.bv.component.search.SoftKeyboardScopeId
import dev.aaa1115910.bv.component.search.SoftKeyboardType
import dev.aaa1115910.bv.entity.db.SearchHistoryDB
import dev.aaa1115910.bv.component.TvAlertDialog
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.viewmodel.search.SearchInputViewModel
import dev.aaa1115910.bv.screen.main.common.MainTopNavDefaultEntryId
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntrySurface
import dev.aaa1115910.bv.wjzfocus.defaultEntry
import dev.aaa1115910.bv.wjzfocus.down
import dev.aaa1115910.bv.wjzfocus.entry
import dev.aaa1115910.bv.wjzfocus.horizontal
import dev.aaa1115910.bv.wjzfocus.localTarget
import dev.aaa1115910.bv.wjzfocus.resolve
import dev.aaa1115910.bv.wjzfocus.submitExternalEntryFocus
import dev.aaa1115910.bv.wjzfocus.keepLayer
import dev.aaa1115910.bv.wjzfocus.up
import dev.aaa1115910.bv.wjzfocus.wjzFocusAllowOnlyEntries
import dev.aaa1115910.bv.wjzfocus.wjzTextFieldFocus
import dev.aaa1115910.bv.wjzfocus.wjzFocusLocalId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.koin.androidx.compose.koinViewModel

private val SearchInputRootScopeId = WjzFocusScopeId("search/input/root")
private val SearchInputDeleteAllDialogScopeId = WjzFocusScopeId("search/input/delete-all")
private val SearchInputDeleteAllDialogContainerLocalId = wjzFocusLocalId("container")
private val SearchInputKeywordLocalId = wjzFocusLocalId("keyword")
private const val SearchInputComponentId = "searchInput"
private val SearchInputKeywordEntryLocalId = WjzFocusLocalEntryId("keyword")
internal val SearchInputDefaultEntryId = WjzFocusComponentId(SearchInputComponentId).defaultEntry()
private val SearchInputKeywordEntryId =
    WjzFocusComponentId(SearchInputComponentId).entry(SearchInputKeywordEntryLocalId)
private val SearchInputKeyboardHorizontalPadding = 28.dp
private val SearchInputTextFieldHorizontalPadding = 12.dp
private val SearchInputTextFieldVerticalPadding = 8.dp

private fun SoftKeyboardType.keyboardContentWidth(): Dp {
    return 434.dp
}

private fun SearchRightEntryToken.toFocusLocalId(): WjzFocusLocalId {
    val slot = slot.name.lowercase()
    val key = firstItemIdentity.replace("/", "_")
    return wjzFocusLocalId("right-entry", slot, key)
}

private fun Modifier.blockSearchInputDirection(direction: Key): Modifier {
    return onPreviewKeyEvent { event ->
        event.type == KeyEventType.KeyDown && event.key == direction
    }
}

@Composable
private fun searchActionIconButtonColors() = IconButtonDefaults.colors(
    containerColor = C.surface,
    contentColor = C.onSurface,
    focusedContainerColor = C.surfaceVariant,
    focusedContentColor = C.onSurfaceVariant,
    pressedContainerColor = C.surfaceVariant,
    pressedContentColor = C.onSurfaceVariant
)

data class SearchRightEntryToken(
    val slot: Slot,
    val keyword: String,
    val historyCount: Int,
    val hotwordCount: Int,
    val suggestCount: Int,
    val showHotword: Boolean,
    val firstItemIdentity: String
) {
    enum class Slot {
        History,
        Hotword,
        Suggest
    }
}

@Composable
fun SearchInputScreen(
    modifier: Modifier = Modifier,
    onDefaultFocusReady: (() -> Unit)? = null,
    onSearchSubmit: ((String, Boolean) -> Unit)? = null,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    topNavEntryId: WjzFocusEntryId = MainTopNavDefaultEntryId,
    searchInputViewModel: SearchInputViewModel = koinViewModel()
) {
    SearchInputRoute(
        modifier = modifier,
        onDefaultFocusReady = onDefaultFocusReady,
        onSearchSubmit = onSearchSubmit,
        topologyRegion = topologyRegion,
        topNavEntryId = topNavEntryId,
        searchInputViewModel = searchInputViewModel
    )
}

@Composable
fun MainDrawerSearchInputScreen(
    modifier: Modifier = Modifier,
    onDefaultFocusReady: (() -> Unit)? = null,
    onCurrentRightEntryTokenChanged: ((SearchRightEntryToken?) -> Unit)? = null,
    onRightEntryFocusReady: ((SearchRightEntryToken) -> Unit)? = null,
    onSearchSubmit: ((String, Boolean) -> Unit)? = null,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    topNavEntryId: WjzFocusEntryId = MainTopNavDefaultEntryId,
    searchInputViewModel: SearchInputViewModel = koinViewModel()
) {
    SearchInputRoute(
        modifier = modifier,
        onDefaultFocusReady = onDefaultFocusReady,
        onCurrentRightEntryTokenChanged = onCurrentRightEntryTokenChanged,
        onRightEntryFocusReady = onRightEntryFocusReady,
        onSearchSubmit = onSearchSubmit,
        topologyRegion = topologyRegion,
        topNavEntryId = topNavEntryId,
        searchInputViewModel = searchInputViewModel
    )
}

@Composable
private fun SearchInputRoute(
    modifier: Modifier = Modifier,
    onDefaultFocusReady: (() -> Unit)? = null,
    onCurrentRightEntryTokenChanged: ((SearchRightEntryToken?) -> Unit)? = null,
    onRightEntryFocusReady: ((SearchRightEntryToken) -> Unit)? = null,
    onSearchSubmit: ((String, Boolean) -> Unit)? = null,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    topNavEntryId: WjzFocusEntryId = MainTopNavDefaultEntryId,
    searchInputViewModel: SearchInputViewModel = koinViewModel()
) {
    val searchKeyword = searchInputViewModel.keyword
    val hotwords by searchInputViewModel.hotwords.collectAsStateWithLifecycle()
    val searchHistories by searchInputViewModel.searchHistories.collectAsStateWithLifecycle()
    val suggests by searchInputViewModel.suggests.collectAsStateWithLifecycle()
    val parentFocusCoordinator = LocalWjzFocusCoordinator.current
    val ownFocusCoordinator = rememberWjzFocusCoordinator()
    val focusCoordinator = parentFocusCoordinator ?: ownFocusCoordinator
    var keywordEntryArmed by remember { mutableStateOf(false) }

    val onSearch: (String) -> Unit = onSearch@{ keyword ->
        if (keyword.isBlank()) return@onSearch
        focusCoordinator.activateLayer(WjzFocusLayer.Content)
        onSearchSubmit?.invoke(keyword, searchInputViewModel.enableProxy)
        searchInputViewModel.keyword = keyword
        searchInputViewModel.addSearchHistory(keyword)
    }

    LaunchedEffect(searchInputViewModel) {
        searchInputViewModel.ensureInitialized(showHotwordErrorToast = true)
    }

    LaunchedEffect(searchKeyword) {
        searchInputViewModel.updateSuggests()
    }

    WjzFocusHost(
        modifier = modifier.onKeyEvent { event ->
            if (event.key != Key.Back || event.type != KeyEventType.KeyDown) {
                return@onKeyEvent false
            }

            focusCoordinator.submitExternalEntryFocus(
                entryId = topNavEntryId,
                layerActivation = activateLayer,
                dedupeKey = "search-input-back-to-top-nav"
            )
            true
        },
        coordinator = focusCoordinator,
        layer = WjzFocusLayer.Content,
        scopeId = SearchInputRootScopeId,
    ) {
        WjzFocusEntrySurface(
            componentId = SearchInputComponentId,
            default = {
                defaultEntry(
                    nodeId = SoftKeyboardScopeId.resolve(SoftKeyboardFirstKeyLocalId),
                    layer = WjzFocusLayer.Content,
                    scopeId = SoftKeyboardScopeId
                )
            },
            entries = {
                entry(SearchInputKeywordEntryLocalId) {
                    keywordEntryArmed = true
                    SearchInputRootScopeId.localTarget(SearchInputKeywordLocalId)
                }
            }
        )
        SearchInputScreenContent(
            onDefaultFocusReady = onDefaultFocusReady,
            onCurrentRightEntryTokenChanged = onCurrentRightEntryTokenChanged,
            onRightEntryFocusReady = onRightEntryFocusReady,
            searchKeyword = searchKeyword,
            onSearchKeywordChange = { searchInputViewModel.keyword = it },
            onSearch = onSearch,
            topologyRegion = topologyRegion,
            topNavEntryId = topNavEntryId,
            keywordEntryArmed = keywordEntryArmed,
            onKeywordEntryConsumed = { keywordEntryArmed = false },
            showProxyOptions = Prefs.enableProxy,
            enableProxy = searchInputViewModel.enableProxy,
            onEnableProxyChange = { searchInputViewModel.enableProxy = it },
            hotwords = hotwords,
            suggests = suggests,
            histories = searchHistories,
            onDeleteHistory = { searchInputViewModel.deleteSearchHistory(it) },
            onDeleteAllHistories = { searchInputViewModel.deleteAllSearchHistories() }
        )
    }
}

@Composable
private fun SearchInputScreenContent(
    modifier: Modifier = Modifier,
    onDefaultFocusReady: (() -> Unit)? = null,
    onCurrentRightEntryTokenChanged: ((SearchRightEntryToken?) -> Unit)? = null,
    onRightEntryFocusReady: ((SearchRightEntryToken) -> Unit)? = null,
    searchKeyword: String,
    onSearchKeywordChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    topNavEntryId: WjzFocusEntryId = MainTopNavDefaultEntryId,
    keywordEntryArmed: Boolean,
    onKeywordEntryConsumed: () -> Unit,
    showProxyOptions: Boolean,
    enableProxy: Boolean,
    onEnableProxyChange: (Boolean) -> Unit,
    hotwords: ImmutableList<Hotword>,
    suggests: ImmutableList<String>,
    histories: ImmutableList<SearchHistoryDB>,
    onDeleteHistory: (SearchHistoryDB) -> Unit,
    onDeleteAllHistories: () -> Unit
) {
    var showHotword by remember { mutableStateOf(Prefs.showHotword) }
    val hotwordEntryToken = remember(
        searchKeyword,
        histories,
        hotwords,
        suggests,
        showHotword
    ) {
        if (searchKeyword.isEmpty() && showHotword) {
            hotwords.firstOrNull()?.let { hotword ->
                SearchRightEntryToken(
                    slot = SearchRightEntryToken.Slot.Hotword,
                    keyword = searchKeyword,
                    historyCount = histories.size,
                    hotwordCount = hotwords.size,
                    suggestCount = suggests.size,
                    showHotword = showHotword,
                    firstItemIdentity = hotword.showName
                )
            }
        } else {
            null
        }
    }
    val currentRightEntryToken = remember(
        searchKeyword,
        histories,
        hotwords,
        suggests,
        showHotword
    ) {
        if (searchKeyword.isEmpty()) {
            histories.firstOrNull()?.let { history ->
                SearchRightEntryToken(
                    slot = SearchRightEntryToken.Slot.History,
                    keyword = searchKeyword,
                    historyCount = histories.size,
                    hotwordCount = hotwords.size,
                    suggestCount = suggests.size,
                    showHotword = showHotword,
                    firstItemIdentity = history.keyword
                )
            }
        } else {
            suggests.firstOrNull()?.let { suggest ->
                SearchRightEntryToken(
                    slot = SearchRightEntryToken.Slot.Suggest,
                    keyword = searchKeyword,
                    historyCount = histories.size,
                    hotwordCount = hotwords.size,
                    suggestCount = suggests.size,
                    showHotword = showHotword,
                    firstItemIdentity = suggest
                )
            }
        }
    }

    LaunchedEffect(currentRightEntryToken) {
        onCurrentRightEntryTokenChanged?.invoke(currentRightEntryToken)
    }

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = LocalDensity.current.density * 1.5f,
            fontScale = LocalDensity.current.fontScale * 1.5f
        )
    ) {
        Scaffold(
            modifier = modifier
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(vertical = 8.dp)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                SearchHotwords(
                    modifier = Modifier.weight(1f),
                    hotwords = hotwords,
                    showHotword = showHotword,
                    onToggleShowHotword = {
                        showHotword = !showHotword
                        Prefs.showHotword = showHotword
                    },
                    topNavEntryId = topNavEntryId,
                    firstItemReadyToken = hotwordEntryToken,
                    onFirstItemPlaced = onRightEntryFocusReady,
                    onSearch = onSearch
                )

                SearchInput(
                    onDefaultFocusReady = onDefaultFocusReady,
                    searchKeyword = searchKeyword,
                    onSearchKeywordChange = onSearchKeywordChange,
                    onSearch = { onSearch(searchKeyword) },
                    topNavEntryId = topNavEntryId,
                    keywordEntryArmed = keywordEntryArmed,
                    onKeywordEntryConsumed = onKeywordEntryConsumed,
                    showProxyOptions = showProxyOptions,
                    enableProxy = enableProxy,
                    onEnableProxyChange = onEnableProxyChange
                )

                if (searchKeyword.isNotEmpty()) {
                    SearchSuggestion(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                        suggests = suggests,
                        topNavEntryId = topNavEntryId,
                        firstItemReadyToken = currentRightEntryToken,
                        onFirstItemPlaced = onRightEntryFocusReady,
                        onSearch = onSearch
                    )
                } else {
                    SearchHistory(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                        histories = histories,
                        topNavEntryId = topNavEntryId,
                        firstItemReadyToken = currentRightEntryToken,
                        onFirstItemPlaced = onRightEntryFocusReady,
                        onSearch = onSearch,
                        onDelete = onDeleteHistory,
                        onDeleteAll = onDeleteAllHistories
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchInput(
    modifier: Modifier = Modifier,
    onDefaultFocusReady: (() -> Unit)? = null,
    searchKeyword: String,
    onSearchKeywordChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    topNavEntryId: WjzFocusEntryId = MainTopNavDefaultEntryId,
    keywordEntryArmed: Boolean,
    onKeywordEntryConsumed: () -> Unit,
    showProxyOptions: Boolean,
    enableProxy: Boolean,
    onEnableProxyChange: (Boolean) -> Unit
) {
    // 只在“从外部进入焦点”的那一刻，把光标挪到末尾
    var textFieldHasFocus by remember { mutableStateOf(false) }

    // 用 TextFieldValue 承载光标位置（selection）
    var fieldValue by remember { mutableStateOf(TextFieldValue(searchKeyword)) }
    var keyboardType by remember { mutableStateOf(SoftKeyboardType.English) }
    var symbolKeyboardSourceType by remember { mutableStateOf(SoftKeyboardType.English) }
    val focusCoordinator = LocalWjzFocusCoordinator.current

    // 外部（SoftKeyboard）修改了 searchKeyword 时，同步回输入框文本
    // 只在未聚焦时同步，避免覆盖用户在输入框内移动的光标
    LaunchedEffect(searchKeyword) {
        if (!textFieldHasFocus && fieldValue.text != searchKeyword) {
            fieldValue = fieldValue.copy(text = searchKeyword)
        }
    }

    fun updateFieldValue(value: TextFieldValue) {
        fieldValue = value
        onSearchKeywordChange(value.text)
    }

    fun insertText(text: String) {
        val cursorPosition = fieldValue.selection.start.coerceIn(0, fieldValue.text.length)
        val newText = fieldValue.text.substring(0, cursorPosition) +
                text +
                fieldValue.text.substring(cursorPosition)
        updateFieldValue(
            fieldValue.copy(
                text = newText,
                selection = TextRange(cursorPosition + text.length)
            )
        )
    }

    fun deleteBackward() {
        val cursorPosition = fieldValue.selection.start.coerceIn(0, fieldValue.text.length)
        if (cursorPosition == 0) return

        val newText = fieldValue.text.removeRange(cursorPosition - 1, cursorPosition)
        updateFieldValue(
            fieldValue.copy(
                text = newText,
                selection = TextRange(cursorPosition - 1)
            )
        )
    }

    fun moveCursor(offset: Int) {
        val cursorPosition = fieldValue.selection.start.coerceIn(0, fieldValue.text.length)
        fieldValue = fieldValue.copy(
            selection = TextRange((cursorPosition + offset).coerceIn(0, fieldValue.text.length))
        )
    }

    fun submitSearch() {
        onSearch(fieldValue.text)
    }

    Box(
        modifier = modifier
            .width(keyboardType.keyboardContentWidth() + SearchInputKeyboardHorizontalPadding * 2)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.padding(horizontal = SearchInputKeyboardHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val underlineColor = C.inverseSurface
            BasicTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .wjzTextFieldFocus(
                        localId = SearchInputKeywordLocalId,
                        layer = WjzFocusLayer.Content,
                        requestPolicy = wjzFocusAllowOnlyEntries(SearchInputKeywordEntryId),
                        backEntryId = topNavEntryId,
                        exits = {
                            up move topNavEntryId
                            down move SoftKeyboardEntryId
                            cancel(horizontal)
                        },
                        onFocused = {
                            if (!textFieldHasFocus) {
                                fieldValue = fieldValue.copy(
                                    selection = TextRange(fieldValue.text.length)
                                )
                            }
                        },
                        onFocusChanged = { focused ->
                            textFieldHasFocus = focused
                            if (focused) {
                                if (keywordEntryArmed) {
                                    onKeywordEntryConsumed()
                                } else {
                                    focusCoordinator?.submitExternalEntryFocus(
                                        entryId = SoftKeyboardEntryId,
                                        layerActivation = keepLayer,
                                        dedupeKey = "search-input-keyword-reject-native-focus"
                                    )
                                }
                            }
                        }
                    )
                    .drawBehind {
                        drawLine(
                            color = underlineColor,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    },
                value = fieldValue,
                onValueChange = {
                    fieldValue = it
                    onSearchKeywordChange(it.text)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = C.onSurface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { submitSearch() },
                    onNext = { submitSearch() },
                    onDone = { submitSearch() }
                ),
                cursorBrush = SolidColor(underlineColor),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                horizontal = SearchInputTextFieldHorizontalPadding,
                                vertical = SearchInputTextFieldVerticalPadding
                            ),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        innerTextField()
                    }
                }
            )
            SoftKeyboard(
                keyboardType = keyboardType,
                showSearchWithProxy = showProxyOptions,
                enableSearchWithProxy = enableProxy,
                onClick = { insertText(it) },
                onClear = { updateFieldValue(TextFieldValue("")) },
                onDelete = { deleteBackward() },
                onMoveCursorLeft = { moveCursor(-1) },
                onMoveCursorRight = { moveCursor(1) },
                onSearch = { submitSearch() },
                onOpenSymbolKeyboard = {
                    if (keyboardType != SoftKeyboardType.Symbol) {
                        symbolKeyboardSourceType = keyboardType
                    }
                    keyboardType = SoftKeyboardType.Symbol
                },
                onKeyboardTypeChange = {
                    keyboardType = if (keyboardType == SoftKeyboardType.Symbol && it == SoftKeyboardType.English) {
                        symbolKeyboardSourceType
                    } else {
                        it
                    }
                },
                onEnableSearchWithProxyChange = onEnableProxyChange,
                backEntryId = topNavEntryId,
                upEntryId = SearchInputKeywordEntryId,
                onFirstButtonPlaced = onDefaultFocusReady
            )
        }
    }
}

@Composable
private fun SearchHotwords(
    modifier: Modifier = Modifier,
    hotwords: ImmutableList<Hotword>,
    showHotword: Boolean,
    onToggleShowHotword: () -> Unit,
    topNavEntryId: WjzFocusEntryId = MainTopNavDefaultEntryId,
    firstItemReadyToken: SearchRightEntryToken? = null,
    onFirstItemPlaced: ((SearchRightEntryToken) -> Unit)? = null,
    onSearch: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .blockSearchInputDirection(Key.DirectionRight),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                text = stringResource(R.string.search_input_hotword),
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(
                onClick = onToggleShowHotword,
                colors = searchActionIconButtonColors()
            ) {
                if (showHotword) {
                    Icon(
                        painter = painterResource(id = R.drawable.expand_circle_up_24px),
                        contentDescription = null,
                        tint = C.onSurface
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.expand_circle_down_24px),
                        contentDescription = null,
                        tint = C.onSurface
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = showHotword,
            enter = expandVertically(
                expandFrom = Alignment.Top
            ) + fadeIn(),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top
            ) + fadeOut()
        ) {
            LazyColumn(
                modifier = Modifier,
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(
                    items = hotwords,
                    key = { _, hotword -> hotword.showName }
                ) { index, hotword ->
                    val itemModifier =
                        if (index == 0 && firstItemReadyToken != null) {
                            Modifier
                                .wjzFocusExits(
                                    localId = firstItemReadyToken.toFocusLocalId(),
                                    layer = WjzFocusLayer.Content,
                                    exits = {
                                        up move topNavEntryId
                                    }
                                )
                                .onGloballyPositioned {
                                    onFirstItemPlaced?.invoke(firstItemReadyToken)
                                }
                        } else {
                            Modifier
                        }

                    SearchKeyword(
                        modifier = itemModifier,
                        keyword = hotword.showName,
                        leadingIcon = hotword.icon ?: "",
                        onClick = { onSearch(hotword.showName) }
                    )
                }
            }
        }
    }
}


@Composable
private fun SearchSuggestion(
    modifier: Modifier = Modifier,
    suggests: ImmutableList<String>,
    topNavEntryId: WjzFocusEntryId = MainTopNavDefaultEntryId,
    firstItemReadyToken: SearchRightEntryToken? = null,
    onFirstItemPlaced: ((SearchRightEntryToken) -> Unit)? = null,
    onSearch: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .blockSearchInputDirection(Key.DirectionLeft),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            text = stringResource(R.string.search_input_suggest),
            style = MaterialTheme.typography.titleLarge
        )
        LazyColumn(
            modifier = Modifier,
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(
                items = suggests,
                key = { _, suggest -> suggest }
            ) { index, suggest ->
                val itemModifier =
                    if (index == 0 && firstItemReadyToken != null) {
                        Modifier
                            .wjzFocusExits(
                                localId = firstItemReadyToken.toFocusLocalId(),
                                layer = WjzFocusLayer.Content,
                                exits = {
                                    up move topNavEntryId
                                }
                            )
                            .onGloballyPositioned {
                                onFirstItemPlaced?.invoke(firstItemReadyToken)
                            }
                    } else {
                        Modifier
                    }

                SearchKeyword(
                    modifier = itemModifier,
                    keyword = suggest,
                    leadingIcon = "",
                    onClick = { onSearch(suggest) }
                )
            }
        }
    }
}

@Composable
private fun SearchHistory(
    modifier: Modifier = Modifier,
    histories: ImmutableList<SearchHistoryDB>,
    topNavEntryId: WjzFocusEntryId = MainTopNavDefaultEntryId,
    firstItemReadyToken: SearchRightEntryToken? = null,
    onFirstItemPlaced: ((SearchRightEntryToken) -> Unit)? = null,
    onSearch: (String) -> Unit,
    onDelete: (SearchHistoryDB) -> Unit,
    onDeleteAll: () -> Unit
) {
    var deleteMode by remember { mutableStateOf(false) }
    var showDeleteAllConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .blockSearchInputDirection(Key.DirectionLeft),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                text = stringResource(R.string.search_input_history),
                style = MaterialTheme.typography.titleLarge
            )
            Row {
                if (deleteMode) {
                    IconButton(
                        onClick = { showDeleteAllConfirmDialog = true },
                        colors = searchActionIconButtonColors()
                    ) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null)
                    }
                }
                IconButton(
                    onClick = { deleteMode = !deleteMode },
                    colors = searchActionIconButtonColors()
                ) {
                    if (deleteMode) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                    } else {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier,
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(
                items = histories,
                key = { _, searchHistory -> searchHistory.id ?: searchHistory.keyword }
            ) { index, searchHistory ->
                val itemModifier =
                    if (index == 0 && firstItemReadyToken != null) {
                        Modifier
                            .wjzFocusExits(
                                localId = firstItemReadyToken.toFocusLocalId(),
                                layer = WjzFocusLayer.Content,
                                exits = {
                                    up move topNavEntryId
                                }
                            )
                            .onGloballyPositioned {
                                onFirstItemPlaced?.invoke(firstItemReadyToken)
                            }
                    } else {
                        Modifier
                    }

                SearchKeyword(
                    modifier = itemModifier,
                    keyword = searchHistory.keyword,
                    leadingIcon = "",
                    onClick = {
                        if (deleteMode) {
                            onDelete(searchHistory)
                        } else {
                            onSearch(searchHistory.keyword)
                        }
                    },
                    trailingIcon = (@Composable {
                        Icon(
                            modifier = Modifier.size(16.dp),
                            imageVector = Icons.Default.Delete,
                            contentDescription = null
                        )
                    }).takeIf { deleteMode }
                )
            }
        }
    }

    if (showDeleteAllConfirmDialog) {
        TvAlertDialog(
            onDismissRequest = { showDeleteAllConfirmDialog = false },
            sourceScopeId = SearchInputRootScopeId,
            dialogScopeId = SearchInputDeleteAllDialogScopeId,
            containerNodeId = SearchInputDeleteAllDialogScopeId.resolve(
                SearchInputDeleteAllDialogContainerLocalId
            ),
            title = {
                Text(text = stringResource(R.string.search_input_history_delete_all_confirm_dialog_title))
            },
            text = {
                Text(text = stringResource(R.string.search_input_history_delete_all_confirm_dialog_text))
            },
            confirmButton = {
                Button(onClick = {
                    onDeleteAll()
                    showDeleteAllConfirmDialog = false
                    deleteMode = false
                }) {
                    Text(text = stringResource(R.string.search_input_history_delete_all_confirm_dialog_confirm_button))
                }
            },
            dismissButton = {
                Button(onClick = {
                    showDeleteAllConfirmDialog = false
                }) {
                    Text(text = stringResource(R.string.search_input_history_delete_all_confirm_dialog_cancel_button))
                }
            }
        )
    }
}

@Preview(device = "id:tv_1080p")
@Preview(device = "id:tv_1080p", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchInputScreenContentPreview() {
    BVTheme {
        Row {
            Spacer(
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxHeight()
                    .background(C.surfaceVariant)
            )
            SearchInputScreenContent(
                modifier = Modifier,
                searchKeyword = "",
                onSearchKeywordChange = {},
                onSearch = {},
                keywordEntryArmed = false,
                onKeywordEntryConsumed = {},
                showProxyOptions = true,
                enableProxy = false,
                onEnableProxyChange = {},
                hotwords = persistentListOf(
                    Hotword("热搜1", "热搜1", null),
                    Hotword("热搜2", "热搜2", null)
                ),
                suggests = persistentListOf("建议1", "建议2"),
                histories = persistentListOf(
                    SearchHistoryDB(keyword = "历史1"),
                    SearchHistoryDB(keyword = "历史2")
                ),
                onDeleteHistory = {},
                onDeleteAllHistories = {}
            )
        }
    }
}
