package dev.aaa1115910.bv.screen.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.component.videocard.SmallVideoCardGridHost
import dev.aaa1115910.bv.component.videocard.SmallVideoCard
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel



@Composable
fun ToViewScreen(
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    toViewViewModel: ToViewViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRemovalAid by remember { mutableStateOf<Long?>(null) }

    val groupedHistories by remember {
        derivedStateOf {
            val unwatched = ArrayList<VideoCardData>(toViewViewModel.histories.size)
            val watched = ArrayList<VideoCardData>()

            toViewViewModel.histories.forEach { item ->
                if (item.timeString == "已看完") {
                    watched.add(item)
                } else {
                    unwatched.add(item)
                }
            }

            unwatched to watched
        }
    }
    val unwatched = groupedHistories.first
    val watched = groupedHistories.second

    LaunchedEffect(Unit) {
        toViewViewModel.uiEvent.collect { event ->
            when (event) {
                is UiEffect.ShowToast -> {
                    event.message.toast(context)
                }
            }
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            Triple(
                gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
                toViewViewModel.histories.size,
                toViewViewModel.noMore
            )
        }
            .distinctUntilChanged()
            .filter { (index, size, noMore) ->
                index != null &&
                        !noMore &&
                        index >= size - 20
            }
            .collect {
                toViewViewModel.update()
            }
    }

    SmallVideoCardGridHost(
        modifier = modifier,
        state = gridState,
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 未看完标题
        item(
            key = "to_view_header_unwatched",
            span = { GridItemSpan(maxLineSpan) }
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "未看完",
                color = C.onSurfaceVariant
            )
        }
        if (unwatched.isNotEmpty()) {
            items(
                items = unwatched,
                key = { it.avid },
                contentType = { "to_view_unwatched" }
            ) { item ->
                Box(contentAlignment = Alignment.Center) {
                    SmallVideoCard(
                        data = item,
                        delToView = true,
                        pendingRemoval = pendingRemovalAid == item.avid,
                        onPendingRemovalFocusLost = {
                            if (pendingRemovalAid == item.avid) {
                                pendingRemovalAid = null
                                toViewViewModel.removeFromLocalList(item.avid)
                            }
                        },
                        onClick = {
                            VideoInfoActivity.actionStart(
                                context = context,
                                aid = item.avid,
                                epid = item.epId,
                                proxyArea = ProxyArea.checkProxyArea(item.title)
                            )
                        },
                        onAddWatchLater = {
                            scope.launch {
                                if (toViewViewModel.deleteToViewRemote(item.avid)) {
                                    pendingRemovalAid = item.avid
                                }
                            }
                        },
                        onGoToDetailPage = {
                            VideoInfoActivity.actionStart(
                                context = context,
                                fromController = true,
                                aid = item.avid,
                                epid = item.epId
                            )
                        },
                        onGoToUpPage = item.upMid?.let {
                            { UpInfoActivity.actionStart(context, it, item.upName) }
                        }
                    )
                }
            }
        } else {
            item(
                key = "to_view_empty_unwatched",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                EmptyTip()
            }
        }

        item(
            key = "to_view_header_watched",
            span = { GridItemSpan(maxLineSpan) }
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "已看完",
                color = C.onSurfaceVariant
            )
        }
        if (watched.isNotEmpty()) {
            items(
                items = watched,
                key = { it.avid },
                contentType = { "to_view_watched" }
            ) { item ->
                Box(contentAlignment = Alignment.Center) {
                    SmallVideoCard(
                        data = item,
                        delToView = true,
                        pendingRemoval = pendingRemovalAid == item.avid,
                        onPendingRemovalFocusLost = {
                            if (pendingRemovalAid == item.avid) {
                                pendingRemovalAid = null
                                toViewViewModel.removeFromLocalList(item.avid)
                            }
                        },
                        onClick = {
                            VideoInfoActivity.actionStart(
                                context = context,
                                aid = item.avid,
                                epid = item.epId,
                                proxyArea = ProxyArea.checkProxyArea(item.title)
                            )
                        },
                        onAddWatchLater = {
                            scope.launch {
                                if (toViewViewModel.deleteToViewRemote(item.avid)) {
                                    pendingRemovalAid = item.avid
                                }
                            }
                        },
                        onGoToDetailPage = {
                            VideoInfoActivity.actionStart(
                                context = context,
                                fromController = true,
                                aid = item.avid,
                                epid = item.epId
                            )
                        },
                        onGoToUpPage = item.upMid?.let {
                            { UpInfoActivity.actionStart(context, it, item.upName) }
                        }
                    )
                }
            }
        } else {
            item(
                key = "to_view_empty_watched",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                EmptyTip()
            }
        }
    }
}

