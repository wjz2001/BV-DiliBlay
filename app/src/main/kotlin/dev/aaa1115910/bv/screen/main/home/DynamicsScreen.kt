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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.user.DynamicVideo
import dev.aaa1115910.bv.activities.video.SeasonInfoActivity
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.component.LoadingTip
import dev.aaa1115910.bv.component.videocard.SmallVideoCardGridHost
import dev.aaa1115910.bv.component.videocard.SmallVideoCard
import dev.aaa1115910.bv.component.videocard.rememberGridRowWrapModifier
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.formatHourMinSec
import dev.aaa1115910.bv.util.toWanString
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.home.DynamicViewModel
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.koin.androidx.compose.koinViewModel

@Composable
fun DynamicsScreen(
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    dynamicViewModel: DynamicViewModel = koinViewModel(),
    toViewViewModel: ToViewViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
            proxyArea = proxyArea
            )
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

    var lastHandledScrollToken by remember { mutableStateOf(dynamicViewModel.scrollToTopToken) }

    LaunchedEffect(dynamicViewModel.scrollToTopToken, gridState) {
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
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .filter { index ->
                index != null && index >= dynamicViewModel.dynamicList.size - 20
            }
            .collect {
                scope.launch(Dispatchers.IO) {
                    dynamicViewModel.loadMore()
                }
            }
    }

    if (dynamicViewModel.isLogin) {
        val focusableWrapIndexMap = buildMap<Long, Int> {
            dynamicViewModel.dynamicList.forEach { video ->
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
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalWrapItemCount = focusableWrapIndexMap.size
        ) {
            itemsIndexed(
                items = dynamicViewModel.dynamicList,
                key = { _, item -> item.aid }
            ) { _, item ->
                val isRefreshPlaceholder = item.aid == DynamicViewModel.REFRESH_PLACEHOLDER_AID

                SmallVideoCard(
                    frameModifier = focusableWrapIndexMap[item.aid]
                        ?.let { rememberGridRowWrapModifier(it) }
                        ?: Modifier,
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
                    onGoToDetailPage = if (isRefreshPlaceholder) {
                        null
                    } else {
                        {
                            VideoInfoActivity.actionStart(
                                context = context,
                                fromController = true,
                                aid = item.aid,
                                epid = item.epid,
                            )
                        }
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
