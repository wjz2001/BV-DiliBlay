package dev.aaa1115910.bv.screen.main.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridState

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.user.DynamicVideo
import dev.aaa1115910.bv.activities.video.SeasonInfoActivity
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.wjzfocus.WjzFocusItemKey
import dev.aaa1115910.bv.component.LoadingTip
import dev.aaa1115910.bv.component.videocard.SmallVideoCardGridHost
import dev.aaa1115910.bv.component.videocard.SmallVideoCard
import dev.aaa1115910.bv.component.rememberTvGridFocusTarget
import dev.aaa1115910.bv.entity.VideoSource
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.screen.main.runtime.ContentRuntimeState
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.formatHourMinSec
import dev.aaa1115910.bv.util.toWanString
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.home.DynamicViewModel
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.yield
import org.koin.androidx.compose.koinViewModel

@Composable
fun DynamicsScreen(
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    active: Boolean = true,
    activationSerial: Long = 0L,
    refreshSerial: Long = 0L,
    longPressSerial: Long = 0L,
    contentEntryFocusRequester: FocusRequester? = null,
    tabFocusRequester: FocusRequester? = null,
    onContentEntryReady: () -> Unit = {}
) {
    val dynamicViewModel: DynamicViewModel = koinViewModel()
    val toViewViewModel: ToViewViewModel = koinViewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val dynamicList by dynamicViewModel.dynamicList.collectAsStateWithLifecycle()

    val onClickVideo: (DynamicVideo) -> Unit = { dynamic ->
        val proxyArea = ProxyArea.checkProxyArea(dynamic.title)
        val targetEpId = dynamic.epid?.takeIf { it > 0 }
        val targetSeasonId = dynamic.seasonId?.takeIf { it > 0 }
        val hasSeasonHint = targetSeasonId != null || targetEpId != null

        if (hasSeasonHint) {
            SeasonInfoActivity.actionStart(
                context = context,
                epId = targetEpId,
                seasonId = targetSeasonId,
                proxyArea = proxyArea
            )
        } else {
            VideoInfoActivity.actionStart(
                context = context,
                aid = dynamic.aid,
                epid = dynamic.epid,
                source = if (dynamic.epid != null) VideoSource.Pgc else VideoSource.Ugc,
                proxyArea = proxyArea
            )
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            toViewViewModel.uiEvent.collect { event ->
                when (event) {
                    is UiEffect.ShowToast -> {
                        event.message.toast(context)
                    }
                }
            }
        }
    }

    LaunchedEffect(active, activationSerial) {
        dynamicViewModel.updateRuntimeState(
            if (active && activationSerial > 0L) ContentRuntimeState.Active else ContentRuntimeState.Frozen
        )
    }

    LaunchedEffect(active, refreshSerial) {
        if (!active) return@LaunchedEffect
        if (refreshSerial == 0L) return@LaunchedEffect
        dynamicViewModel.reloadAll()
    }

    LaunchedEffect(active, longPressSerial) {
        if (!active) return@LaunchedEffect
        if (longPressSerial == 0L) return@LaunchedEffect
        dynamicViewModel.requestScrollToTop()
    }

    var lastHandledScrollToken by remember { mutableStateOf(dynamicViewModel.scrollToTopToken) }

    LaunchedEffect(dynamicViewModel.scrollToTopToken, gridState, active) {
        if (!active) return@LaunchedEffect
        val token = dynamicViewModel.scrollToTopToken
        if (token <= lastHandledScrollToken) return@LaunchedEffect

        lastHandledScrollToken = token

        // 最多重试 3 次，避免重组/焦点导致首次滚动被抵消
        repeat(3) {
            gridState.scrollToItem(0)
            if (gridState.firstVisibleItemIndex == 0) return@LaunchedEffect
            yield()
        }
    }

    // 监听可见区最后一个 item 的 index，距离尾部 20 个就翻页
    LaunchedEffect(gridState, active) {
        if (!active) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .filter { index ->
                index != null && index >= dynamicList.size - 20
            }
            .collect {
                dynamicViewModel.loadMore()
            }
    }

    if (dynamicViewModel.isLogin) {
        val focusableWrapIndexMap = buildMap<Long, Int> {
            dynamicList.forEach { video ->
                if (video.aid != DynamicViewModel.REFRESH_PLACEHOLDER_AID) {
                    put(video.aid, size)
                }
            }
        }

        SmallVideoCardGridHost(
            modifier = modifier,
            state = gridState,
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(24.dp),
            nodeIdPrefix = "dynamics/videos",
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            focusItemCount = focusableWrapIndexMap.size,
            focusItemKeys = focusableWrapIndexMap.keys.map { WjzFocusItemKey("Long:$it") },
            entryFocusRequester = contentEntryFocusRequester,
            upFocusRequester = tabFocusRequester,
            onEntryFocusReady = onContentEntryReady
        ) { cardUiStateFor ->
            itemsIndexed(
                items = dynamicList,
                key = { _, item -> item.aid }
            ) { _, item ->
                val isRefreshPlaceholder = item.aid == DynamicViewModel.REFRESH_PLACEHOLDER_AID

                SmallVideoCard(
                    focusTarget = focusableWrapIndexMap[item.aid]
                        ?.let { rememberTvGridFocusTarget(it) },
                    uiState = cardUiStateFor(item.aid),
                    data = remember(item, isRefreshPlaceholder) {
                        VideoCardData(
                            avid = item.aid,
                            title = item.title,
                            cover = item.cover,
                            upMid = item.authorMid,
                            playString = if (isRefreshPlaceholder) "" else item.play.takeIf { it != -1 }.toWanString(),
                            danmakuString = if (isRefreshPlaceholder) "" else item.danmaku.takeIf { it != -1 }.toWanString(),
                            upName = item.author,
                            timeString = if (isRefreshPlaceholder) "" else (item.duration * 1000L).formatHourMinSec(),
                            pubTime = if (isRefreshPlaceholder) null else item.pubTime
                        )
                    },
                    onClick = {
                        if (!isRefreshPlaceholder) onClickVideo(item)
                    },
                    onAddWatchLater = if (isRefreshPlaceholder) {
                        null
                    } else {
                        { toViewViewModel.addToView(item.aid) }
                    },
                    onGoToUpPage = if (isRefreshPlaceholder) {
                        null
                    } else {
                        { UpInfoActivity.actionStart(context, item.authorMid, item.author) }
                    },
                    interactive = !isRefreshPlaceholder
                )
            }

            if (dynamicViewModel.loading)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingTip()
                    }
                }

            if (!dynamicViewModel.hasMore)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        text = "没有更多了捏",
                        color = C.onSurface
                    )
                }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "请先登录")
        }
    }
}
