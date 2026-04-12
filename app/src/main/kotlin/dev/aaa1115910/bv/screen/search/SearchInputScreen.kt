package dev.aaa1115910.bv.screen.search

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.search.Hotword
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.search.SearchResultActivity
import dev.aaa1115910.bv.component.search.SearchKeyword
import dev.aaa1115910.bv.component.search.SoftKeyboard
import dev.aaa1115910.bv.entity.db.SearchHistoryDB
import dev.aaa1115910.bv.tv.component.TvAlertDialog
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.viewmodel.search.SearchInputViewModel
import dev.aaa1115910.bv.screen.main.common.mainContentLeftExit
import dev.aaa1115910.bv.screen.main.common.mainContentRightExit
import org.koin.androidx.compose.koinViewModel

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

    return null
}

@Composable
fun SearchInputScreen(
    modifier: Modifier = Modifier,
    defaultFocusRequester: FocusRequester,
    onDefaultFocusReady: (() -> Unit)? = null,
    searchInputViewModel: SearchInputViewModel = koinViewModel()
) {
    SearchInputRoute(
        modifier = modifier,
        defaultFocusRequester = defaultFocusRequester,
        onDefaultFocusReady = onDefaultFocusReady,
        searchInputViewModel = searchInputViewModel
    )
}

@Composable
fun MainDrawerSearchInputScreen(
    modifier: Modifier = Modifier,
    defaultFocusRequester: FocusRequester,
    drawerFocusRequester: FocusRequester,
    rightEntryFocusRequester: FocusRequester,
    onDefaultFocusReady: (() -> Unit)? = null,
    onCurrentRightEntryTokenChanged: ((SearchRightEntryToken?) -> Unit)? = null,
    onRightEntryFocusReady: ((SearchRightEntryToken) -> Unit)? = null,
    searchInputViewModel: SearchInputViewModel = koinViewModel()
) {
    SearchInputRoute(
        modifier = modifier,
        defaultFocusRequester = defaultFocusRequester,
        drawerFocusRequester = drawerFocusRequester,
        rightEntryFocusRequester = rightEntryFocusRequester,
        onDefaultFocusReady = onDefaultFocusReady,
        onCurrentRightEntryTokenChanged = onCurrentRightEntryTokenChanged,
        onRightEntryFocusReady = onRightEntryFocusReady,
        searchInputViewModel = searchInputViewModel
    )
}

@Composable
private fun SearchInputRoute(
    modifier: Modifier = Modifier,
    defaultFocusRequester: FocusRequester,
    drawerFocusRequester: FocusRequester = FocusRequester.Default,
    rightEntryFocusRequester: FocusRequester? = null,
    onDefaultFocusReady: (() -> Unit)? = null,
    onCurrentRightEntryTokenChanged: ((SearchRightEntryToken?) -> Unit)? = null,
    onRightEntryFocusReady: ((SearchRightEntryToken) -> Unit)? = null,
    searchInputViewModel: SearchInputViewModel = koinViewModel()
) {
    val context = LocalContext.current

    val searchKeyword = searchInputViewModel.keyword
    val hotwords = searchInputViewModel.hotwords
    val searchHistories = searchInputViewModel.searchHistories
    val suggests = searchInputViewModel.suggests

    val onSearch: (String) -> Unit = { keyword ->
        SearchResultActivity.actionStart(context, keyword, searchInputViewModel.enableProxy)
        searchInputViewModel.keyword = keyword
        searchInputViewModel.addSearchHistory(keyword)
    }

    LaunchedEffect(searchInputViewModel) {
        searchInputViewModel.ensureInitialized(showHotwordErrorToast = true)
    }

    LaunchedEffect(searchKeyword) {
        searchInputViewModel.updateSuggests()
    }

    SearchInputScreenContent(
        modifier = modifier,
        defaultFocusRequester = defaultFocusRequester,
        drawerFocusRequester = drawerFocusRequester,
        rightEntryFocusRequester = rightEntryFocusRequester,
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

@Composable
private fun SearchInputScreenContent(
    modifier: Modifier = Modifier,
    defaultFocusRequester: FocusRequester,
    drawerFocusRequester: FocusRequester = FocusRequester.Default,
    rightEntryFocusRequester: FocusRequester? = null,
    onDefaultFocusReady: (() -> Unit)? = null,
    onCurrentRightEntryTokenChanged: ((SearchRightEntryToken?) -> Unit)? = null,
    onRightEntryFocusReady: ((SearchRightEntryToken) -> Unit)? = null,
    searchKeyword: String,
    onSearchKeywordChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    showProxyOptions: Boolean,
    enableProxy: Boolean,
    onEnableProxyChange: (Boolean) -> Unit,
    hotwords: List<Hotword>,
    suggests: List<String>,
    histories: List<SearchHistoryDB>,
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
                Box(
                    modifier = Modifier.mainContentLeftExit(drawerFocusRequester)
                ) {
                    SearchInput(
                        firstButtonFocusRequester = defaultFocusRequester,
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
                        firstItemFocusRequester = rightEntryFocusRequester,
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
                        firstItemFocusRequester = rightEntryFocusRequester,
                        onFirstItemPlaced = onRightEntryFocusReady,
                        onSearch = onSearch
                    )
                }

                SearchHistory(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    drawerFocusRequester = drawerFocusRequester,
                    histories = histories,
                    firstItemReadyToken = currentRightEntryToken?.takeIf {
                        it.slot == SearchRightEntryToken.Slot.History
                    },
                    firstItemFocusRequester = rightEntryFocusRequester,
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
    firstButtonFocusRequester: FocusRequester,
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

    // 外部（SoftKeyboard）修改了 searchKeyword 时，同步回输入框文本
    // 只在未聚焦时同步，避免覆盖用户在输入框内移动的光标
    LaunchedEffect(searchKeyword) {
        if (!textFieldHasFocus && fieldValue.text != searchKeyword) {
            fieldValue = fieldValue.copy(text = searchKeyword)
        }
    }

    Box(
        modifier = modifier
            .width(280.dp)
            .fillMaxHeight()
            .focusGroup(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .width(258.dp)
                    .onFocusChanged { focusState ->
                        val nowFocused = focusState.isFocused || focusState.hasFocus

                        // 只在“未聚焦 -> 聚焦”的瞬间把光标设置到末尾
                        if (!textFieldHasFocus && nowFocused) {
                            fieldValue = fieldValue.copy(
                                selection = TextRange(fieldValue.text.length)
                            )
                        }

                        textFieldHasFocus = nowFocused
                    },
                value = fieldValue,
                onValueChange = {
                    fieldValue = it
                    onSearchKeywordChange(it.text)
                },
                maxLines = 1,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(searchKeyword) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.inverseSurface,
                    cursorColor = MaterialTheme.colorScheme.inverseSurface
                )
            )
            SoftKeyboard(
                firstButtonFocusRequester = firstButtonFocusRequester,
                showSearchWithProxy = showProxyOptions,
                enableSearchWithProxy = enableProxy,
                onClick = { onSearchKeywordChange(searchKeyword + it) },
                onClear = { onSearchKeywordChange("") },
                onDelete = {
                    if (searchKeyword.isNotEmpty()) {
                        onSearchKeywordChange(searchKeyword.dropLast(1))
                    }
                },
                onSearch = { onSearch(searchKeyword) },
                onEnableSearchWithProxyChange = onEnableProxyChange,
                onFirstButtonPlaced = onDefaultFocusReady
            )
        }
    }
}

@Composable
private fun SearchHotwords(
    modifier: Modifier = Modifier,
    hotwords: List<Hotword>,
    showHotword: Boolean,
    onToggleShowHotword: () -> Unit,
    firstItemReadyToken: SearchRightEntryToken? = null,
    firstItemFocusRequester: FocusRequester? = null,
    onFirstItemPlaced: ((SearchRightEntryToken) -> Unit)? = null,
    onSearch: (String) -> Unit
) {
    Column(
        modifier = modifier
            .width(250.dp)
            .fillMaxHeight()
            .focusGroup(),
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
                itemsIndexed(hotwords) { index, hotword ->
                    val itemModifier =
                        if (index == 0 &&
                            firstItemReadyToken != null &&
                            firstItemFocusRequester != null
                        ) {
                            Modifier
                                .focusRequester(firstItemFocusRequester)
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
    suggests: List<String>,
    firstItemReadyToken: SearchRightEntryToken? = null,
    firstItemFocusRequester: FocusRequester? = null,
    onFirstItemPlaced: ((SearchRightEntryToken) -> Unit)? = null,
    onSearch: (String) -> Unit
) {
    Column(
        modifier = modifier
            .width(250.dp)
            .fillMaxHeight()
            .focusGroup(),
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
            itemsIndexed(suggests) { index, suggest ->
                val itemModifier =
                    if (index == 0 &&
                        firstItemReadyToken != null &&
                        firstItemFocusRequester != null
                    ) {
                        Modifier
                            .focusRequester(firstItemFocusRequester)
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
    drawerFocusRequester: FocusRequester = FocusRequester.Default,
    histories: List<SearchHistoryDB>,
    firstItemReadyToken: SearchRightEntryToken? = null,
    firstItemFocusRequester: FocusRequester? = null,
    onFirstItemPlaced: ((SearchRightEntryToken) -> Unit)? = null,
    onSearch: (String) -> Unit,
    onDelete: (SearchHistoryDB) -> Unit,
    onDeleteAll: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    var deleteMode by remember { mutableStateOf(false) }
    var showDeleteAllConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .mainContentRightExit(drawerFocusRequester)
            .width(250.dp)
            .fillMaxHeight()
            .focusGroup(),
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
            itemsIndexed(histories) { index, searchHistory ->
                val itemModifier =
                    if (index == 0 &&
                        firstItemReadyToken != null &&
                        firstItemFocusRequester != null
                    ) {
                        Modifier
                            .focusRequester(firstItemFocusRequester)
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
                            if (index == histories.lastIndex) {
                                focusManager.moveFocus(FocusDirection.Up)
                            }
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
                defaultFocusRequester = FocusRequester.Default,
                searchKeyword = "",
                onSearchKeywordChange = {},
                onSearch = {},
                showProxyOptions = true,
                enableProxy = false,
                onEnableProxyChange = {},
                hotwords = listOf(
                    Hotword("热搜1", "热搜1", null),
                    Hotword("热搜2", "热搜2", null)
                ),
                suggests = listOf("建议1", "建议2"),
                histories = listOf(
                    SearchHistoryDB(keyword = "历史1"),
                    SearchHistoryDB(keyword = "历史2")
                ),
                onDeleteHistory = {},
                onDeleteAllHistories = {}
            )
        }
    }
}
