package dev.aaa1115910.bv.screen.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.component.videocard.SmallVideoCardGridHost
import dev.aaa1115910.bv.component.videocard.SmallVideoCard
import dev.aaa1115910.bv.component.videocard.rememberGridRowWrapModifier
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.tv.component.TvAlertDialog
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.user.HistoryViewModel
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.Text

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    historyViewModel: HistoryViewModel = koinViewModel(),
    toViewViewModel: ToViewViewModel = koinViewModel()
) {
    val context = LocalContext.current
    var searchFieldHasFocus by remember { mutableStateOf(false) }

    val visibleHistories by remember {
        derivedStateOf {
            val q = historyViewModel.debouncedQuery.trim()
            if (q.isBlank()) {
                historyViewModel.histories
            } else {
                historyViewModel.histories.filter { it.title.contains(q, ignoreCase = true) }
            }
        }
    }

    // 监听可见区最后一个 item 的 index，距离尾部 20 个就翻页
    LaunchedEffect(historyViewModel.debouncedQuery) {
        val q = historyViewModel.debouncedQuery.trim()
        if (q.isBlank()) {
            historyViewModel.stopAutoLoad()
        } else {
            historyViewModel.startAutoLoad()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            historyViewModel.stopAutoLoad()
        }
    }

// 空关键词：保留触底翻页
    LaunchedEffect(gridState, historyViewModel.debouncedQuery) {
        if (historyViewModel.debouncedQuery.trim().isNotBlank()) return@LaunchedEffect

        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .filter { index ->
                index != null && index >= historyViewModel.histories.size - 20
            }
            .collect {
                historyViewModel.update()
            }
    }

    LaunchedEffect(Unit) {
        toViewViewModel.uiEvent.collect { event ->
            when (event) {
                is UiEffect.ShowToast -> {
                    event.message.toast(context)
                }
            }
        }
    }
    val searchFocusedLineColor = C.primary
    val searchUnfocusedLineColor = C.onSurfaceVariant

    Box(
        modifier = modifier.onPreviewKeyEvent {
            if (historyViewModel.showSearchDialog && it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                historyViewModel.closeSearchDialog(apply = true)
                return@onPreviewKeyEvent true
            }
            false
        }
    ) {
        SmallVideoCardGridHost(
        modifier = modifier,
        state = gridState,
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        horizontalWrapItemCount = visibleHistories.size
    ) {
            if (visibleHistories.isNotEmpty()) {
                itemsIndexed(visibleHistories) { index, history ->
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    SmallVideoCard(
                        frameModifier = rememberGridRowWrapModifier(index),
                        data = history,
                        onClick = {
                            VideoInfoActivity.actionStart(
                                context = context,
                                aid = history.avid,
                                epid = history.epId,
                                proxyArea = ProxyArea.checkProxyArea(history.title)
                            )
                        },
                        onAddWatchLater = {
                            toViewViewModel.addToView(history.avid)
                        },
                        onGoToDetailPage = {
                            VideoInfoActivity.actionStart(
                                context = context,
                                fromController = true,
                                aid = history.avid,
                                epid = history.epId
                            )
                        },
                        onGoToUpPage = history.upMid?.let {
                            { UpInfoActivity.actionStart(context, it, history.upName) }
                        }
                    )
                }
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyTip()
            }
        }

        }

        if (historyViewModel.showSearchDialog) {
            TvAlertDialog(
                onDismissRequest = {
                    historyViewModel.closeSearchDialog(apply = true)
                },
                title = {
                    Text(text = "在历史记录中搜索")
                },
                text = {
                    TextField(
                        modifier = Modifier
                            .width(600.dp)
                            .onFocusChanged { searchFieldHasFocus = it.hasFocus }
                            .drawWithContent {
                                drawContent()
                                val stroke = 3.dp.toPx()
                                val y = size.height - stroke / 2f
                                drawLine(
                                    color = if (searchFieldHasFocus) {
                                        searchFocusedLineColor
                                    } else {
                                        searchUnfocusedLineColor
                                    },
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = stroke
                                )
                            },
                        value = historyViewModel.rawQuery,
                        onValueChange = historyViewModel::onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 26.sp, lineHeight = 30.sp),
                        shape = RectangleShape,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { historyViewModel.onSearchAction() }
                        )
                    )
                },
                confirmButton = {},
                properties = DialogProperties(usePlatformDefaultWidth = false)
            )
        }
    }
}
