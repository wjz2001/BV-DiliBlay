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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.search.Hotword
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryResolution
import dev.aaa1115910.bv.wjzfocus.WjzFocusHostExit
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusHost
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.wjzFocus
import dev.aaa1115910.bv.wjzfocus.rememberWjzFocusCoordinator
import dev.aaa1115910.bv.component.search.SearchKeyword
import dev.aaa1115910.bv.component.search.SoftKeyboard
import dev.aaa1115910.bv.component.search.SoftKeyboardType
import dev.aaa1115910.bv.entity.db.SearchHistoryDB
import dev.aaa1115910.bv.component.TvAlertDialog
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.viewmodel.search.SearchInputViewModel
import dev.aaa1115910.bv.screen.main.common.MainContentNavigationExitEntry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.koin.androidx.compose.koinViewModel

private val SearchInputRootScopeId = WjzFocusScopeId("search/input/root")
private val SearchInputDeleteAllDialogScopeId = WjzFocusScopeId("search/input/delete-all")
private val SearchInputDeleteAllDialogContainerNodeId = WjzFocusNodeId("search/input/delete-all/container")
private val SearchSubmitFocusNodeId = WjzFocusNodeId("main/content/current")
private val SearchSubmitFocusScopeId = WjzFocusScopeId("main")

private fun searchInputHostExits(): List<WjzFocusHostExit> {
    return listOf(
        WjzFocusHostExit(FocusDirection.Left, MainContentNavigationExitEntry.DrawerCurrentItem.entryId),
        WjzFocusHostExit(FocusDirection.Right, MainContentNavigationExitEntry.TopNavUser.entryId)
    )
}

private fun SearchRightEntryToken.toFocusLocalId(): String {
    val slot = slot.name.lowercase()
    val key = firstItemIdentity.replace("/", "_")
    return "right-entry/$slot/$key"
}

@Composable
private fun searchActionIconButtonColors() = IconButtonDefaults.colors(
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
    focusedContainerColor = MaterialTheme.colorScheme.onSurface,
    focusedContentColor = MaterialTheme.colorScheme.surface,
    pressedContainerColor = MaterialTheme.colorScheme.onSurface,
    pressedContentColor = MaterialTheme.colorScheme.surface
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

private fun resolveSearchRightEntryToken(
    keyword: String,
    histories: List<SearchHistoryDB>,
    hotwords: List<Hotword>,
    suggests: List<String>,
    showHotword: Boolean
): SearchRightEntryToken? {
    if (keyword.isEmpty() && showHotword) {
        hotwords.firstOrNull()?.let { hotword ->
            return SearchRightEntryToken(
                slot = SearchRightEntryToken.Slot.Hotword,
                keyword = keyword,
                historyCount = histories.size,
                hotwordCount = hotwords.size,
                suggestCount = suggests.size,
                showHotword = showHotword,
                firstItemIdentity = hotword.showName
            )
        }
    }

    if (keyword.isNotEmpty()) {
        suggests.firstOrNull()?.let { suggest ->
            return SearchRightEntryToken(
                slot = SearchRightEntryToken.Slot.Suggest,
                keyword = keyword,
                historyCount = histories.size,
                hotwordCount = hotwords.size,
                suggestCount = suggests.size,
                showHotword = showHotword,
                firstItemIdentity = suggest
            )
        }
    }

    histories.firstOrNull()?.let { history ->
        return SearchRightEntryToken(
            slot = SearchRightEntryToken.Slot.History,
            keyword = keyword,
            historyCount = histories.size,
            hotwordCount = hotwords.size,
            suggestCount = suggests.size,
            showHotword = showHotword,
            firstItemIdentity = history.keyword
        )
    }

    return null
}

@Composable
fun SearchInputScreen(
    modifier: Modifier = Modifier,
    onDefaultFocusReady: (() -> Unit)? = null,
    onSearchSubmit: ((String, Boolean) -> Unit)? = null,
    searchInputViewModel: SearchInputViewModel = koinViewModel()
) {
    SearchInputRoute(
        modifier = modifier,
        onDefaultFocusReady = onDefaultFocusReady,
        onSearchSubmit = onSearchSubmit,
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
    searchInputViewModel: SearchInputViewModel = koinViewModel()
) {
    SearchInputRoute(
        modifier = modifier,
        onDefaultFocusReady = onDefaultFocusReady,
        onCurrentRightEntryTokenChanged = onCurrentRightEntryTokenChanged,
        onRightEntryFocusReady = onRightEntryFocusReady,
        onSearchSubmit = onSearchSubmit,
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
    searchInputViewModel: SearchInputViewModel = koinViewModel()
) {
    val searchKeyword = searchInputViewModel.keyword
    val hotwords by searchInputViewModel.hotwords.collectAsStateWithLifecycle()
    val searchHistories by searchInputViewModel.searchHistories.collectAsStateWithLifecycle()
    val suggests by searchInputViewModel.suggests.collectAsStateWithLifecycle()
    val parentFocusCoordinator = LocalWjzFocusCoordinator.current
    val ownFocusCoordinator = rememberWjzFocusCoordinator()
    val focusCoordinator = parentFocusCoordinator ?: ownFocusCoordinator

    val onSearch: (String) -> Unit = onSearch@{ keyword ->
        if (keyword.isBlank()) return@onSearch
        focusCoordinator.activateLayer(WjzFocusLayer.Content)
        focusCoordinator.enqueueRequestFocus(
            nodeId = SearchSubmitFocusNodeId,
            layer = WjzFocusLayer.Content,
            scopeId = SearchSubmitFocusScopeId
        )
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
        modifier = modifier,
        coordinator = focusCoordinator,
        layer = WjzFocusLayer.Content,
        scopeId = SearchInputRootScopeId,
        exits = searchInputHostExits(),
        onHostExit = { WjzFocusEntryResolution.Reject }
    ) {
        SearchInputScreenContent(
            onDefaultFocusReady = onDefaultFocusReady,
            onCurrentRightEntryTokenChanged = onCurrentRightEntryTokenChanged,
            onRightEntryFocusReady = onRightEntryFocusReady,
            searchKeyword = searchKeyword,
            onSearchKeywordChange = { searchInputViewModel.keyword = it },
            onSearch = onSearch,
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
    val currentRightEntryToken = remember(
        searchKeyword,
        histories,
        hotwords,
        suggests,
        showHotword
    ) {
        resolveSearchRightEntryToken(
            keyword = searchKeyword,
            histories = histories,
            hotwords = hotwords,
            suggests = suggests,
            showHotword = showHotword
        )
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
                    .padding(start = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box {
                    SearchInput(
                        onDefaultFocusReady = onDefaultFocusReady,
                        searchKeyword = searchKeyword,
                        onSearchKeywordChange = onSearchKeywordChange,
                        onSearch = { onSearch(searchKeyword) },
                        showProxyOptions = showProxyOptions,
                        enableProxy = enableProxy,
                        onEnableProxyChange = onEnableProxyChange
                    )
                }

                if (searchKeyword.isEmpty()) {
                    SearchHotwords(
                        modifier = Modifier.weight(1f),
                        hotwords = hotwords,
                        showHotword = showHotword,
                        onToggleShowHotword = {
                            showHotword = !showHotword
                            Prefs.showHotword = showHotword
                        },
                        firstItemReadyToken = currentRightEntryToken?.takeIf {
                            it.slot == SearchRightEntryToken.Slot.Hotword
                        },
                        onFirstItemPlaced = onRightEntryFocusReady,
                        onSearch = onSearch
                    )
                } else {
                    SearchSuggestion(
                        modifier = Modifier.weight(1f),
                        suggests = suggests,
                        firstItemReadyToken = currentRightEntryToken?.takeIf {
                            it.slot == SearchRightEntryToken.Slot.Suggest
                        },
                        onFirstItemPlaced = onRightEntryFocusReady,
                        onSearch = onSearch
                    )
                }

                SearchHistory(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    histories = histories,
                    firstItemReadyToken = currentRightEntryToken?.takeIf {
                        it.slot == SearchRightEntryToken.Slot.History
                    },
                    onFirstItemPlaced = onRightEntryFocusReady,
                    onSearch = onSearch,
                    onDelete = onDeleteHistory,
                    onDeleteAll = onDeleteAllHistories
                )
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
    showProxyOptions: Boolean,
    enableProxy: Boolean,
    onEnableProxyChange: (Boolean) -> Unit
) {
    // 只在“从外部进入焦点”的那一刻，把光标挪到末尾
    var textFieldHasFocus by remember { mutableStateOf(false) }

    // 用 TextFieldValue 承载光标位置（selection）
    var fieldValue by remember { mutableStateOf(TextFieldValue(searchKeyword)) }
    val textFieldInteractionSource = remember { MutableInteractionSource() }
    var keyboardType by remember { mutableStateOf(SoftKeyboardType.English) }
    var symbolKeyboardSourceType by remember { mutableStateOf(SoftKeyboardType.English) }

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

    LaunchedEffect(textFieldInteractionSource) {
        textFieldInteractionSource.interactions.collect { interaction ->
            when (interaction) {
                is FocusInteraction.Focus -> {
                    if (!textFieldHasFocus) {
                        fieldValue = fieldValue.copy(
                            selection = TextRange(fieldValue.text.length)
                        )
                    }
                    textFieldHasFocus = true
                }

                is FocusInteraction.Unfocus -> {
                    textFieldHasFocus = false
                }
            }
        }
    }

    Box(
        modifier = modifier
            .width(
                when (keyboardType) {
                    SoftKeyboardType.English -> 280.dp
                    SoftKeyboardType.Japanese -> 456.dp
                    SoftKeyboardType.Symbol -> 412.dp
                }
            )
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .width(258.dp),
                value = fieldValue,
                onValueChange = {
                    fieldValue = it
                    onSearchKeywordChange(it.text)
                },
                maxLines = 1,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { submitSearch() },
                    onNext = { submitSearch() },
                    onDone = { submitSearch() }
                ),
                interactionSource = textFieldInteractionSource,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.inverseSurface,
                    cursorColor = MaterialTheme.colorScheme.inverseSurface
                )
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
    firstItemReadyToken: SearchRightEntryToken? = null,
    onFirstItemPlaced: ((SearchRightEntryToken) -> Unit)? = null,
    onSearch: (String) -> Unit
) {
    Column(
        modifier = modifier
            .width(250.dp)
            .fillMaxHeight(),
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
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.expand_circle_down_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
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
                                .wjzFocus(
                                    id = firstItemReadyToken.toFocusLocalId(),
                                    layer = WjzFocusLayer.Content,
                                    fallback = true
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
    firstItemReadyToken: SearchRightEntryToken? = null,
    onFirstItemPlaced: ((SearchRightEntryToken) -> Unit)? = null,
    onSearch: (String) -> Unit
) {
    Column(
        modifier = modifier
            .width(250.dp)
            .fillMaxHeight(),
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
                            .wjzFocus(
                                id = firstItemReadyToken.toFocusLocalId(),
                                layer = WjzFocusLayer.Content,
                                fallback = true
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
            .width(250.dp)
            .fillMaxHeight(),
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
                            .wjzFocus(
                                id = firstItemReadyToken.toFocusLocalId(),
                                layer = WjzFocusLayer.Content,
                                fallback = true
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
            containerNodeId = SearchInputDeleteAllDialogContainerNodeId,
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
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            SearchInputScreenContent(
                modifier = Modifier,
                searchKeyword = "",
                onSearchKeywordChange = {},
                onSearch = {},
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
