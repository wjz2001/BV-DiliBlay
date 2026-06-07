package dev.aaa1115910.bv.screen.main.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.key.type
import dev.aaa1115910.bv.util.isKeyDown
import dev.aaa1115910.bv.util.isMenuKey
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.season.FollowingSeasonStatus
import dev.aaa1115910.biliapi.entity.season.FollowingSeasonType
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.video.SeasonInfoActivity
import dev.aaa1115910.bv.wjzfocus.WjzFocusItemKey
import dev.aaa1115910.bv.wjzfocus.WjzFocusTopologyRegionRef
import dev.aaa1115910.bv.wjzfocus.enabledIf
import dev.aaa1115910.bv.component.videocard.SmallVideoCardGridHost
import dev.aaa1115910.bv.component.videocard.SeasonCard
import dev.aaa1115910.bv.component.rememberTvGridFocusModifier
import dev.aaa1115910.bv.entity.carddata.SeasonCardData
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.util.ImageSize
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.resizedImageUrl
import dev.aaa1115910.bv.viewmodel.user.FollowingSeasonViewModel
import dev.aaa1115910.bv.ui.theme.C
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.androidx.compose.koinViewModel

@Composable
fun FollowingSeasonScreen(
    modifier: Modifier = Modifier,
    lazyGridState: LazyGridState = rememberLazyGridState(),
    active: Boolean = true,
    activationSerial: Long = 0L,
    refreshSerial: Long = 0L,
    onContentEntryReady: () -> Unit = {},
    followingSeasonViewModel: FollowingSeasonViewModel = koinViewModel(),
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone
) {
    val context = LocalContext.current
    val logger = KotlinLogging.logger { }

    var currentIndex by remember { mutableIntStateOf(0) }
    var showFilter by remember { mutableStateOf(false) }

    val followingSeasons by followingSeasonViewModel.followingSeasons.collectAsStateWithLifecycle()
    var followingSeasonType by remember { mutableStateOf(followingSeasonViewModel.followingSeasonType) }
    var followingSeasonStatus by remember { mutableStateOf(followingSeasonViewModel.followingSeasonStatus) }
    val noMore = followingSeasonViewModel.noMore
    var filterEffectInitialized by remember { mutableStateOf(false) }

    val updateType: (FollowingSeasonType) -> Unit = {
        followingSeasonType = it
        followingSeasonViewModel.followingSeasonType = it
    }

    val updateStatus: (FollowingSeasonStatus) -> Unit = {
        followingSeasonStatus = it
        followingSeasonViewModel.followingSeasonStatus = it
    }

    DisposableEffect(active) {
        onDispose {
            followingSeasonViewModel.cancelOngoingLoads()
        }
    }

    LaunchedEffect(active, followingSeasonType, followingSeasonStatus) {
        if (!filterEffectInitialized) {
            filterEffectInitialized = true
            return@LaunchedEffect
        }
        if (!active) return@LaunchedEffect
        logger.fInfo { "Start update search result because filter updated" }
        followingSeasonViewModel.clearData()
        withFrameNanos { }
        followingSeasonViewModel.ensureLoaded()
    }

    LaunchedEffect(active, activationSerial) {
        if (!active) return@LaunchedEffect
        if (activationSerial == 0L) return@LaunchedEffect
        withFrameNanos { }
        followingSeasonViewModel.ensureLoaded()
    }

    LaunchedEffect(active, refreshSerial) {
        if (!active) return@LaunchedEffect
        if (refreshSerial == 0L) return@LaunchedEffect
        lazyGridState.scrollToItem(0)
        followingSeasonViewModel.reloadAll()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent {
                if (it.isMenuKey()) {
                    if (it.isKeyDown()) return@onKeyEvent true
                    showFilter = true
                    return@onKeyEvent true
                }
                false
            },
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            text = stringResource(R.string.filter_dialog_open_tip),
            color = C.onSurfaceVariant
        )
        SmallVideoCardGridHost(
            modifier = Modifier,
            state = lazyGridState,
            columns = GridCells.Fixed(6),
            contentPadding = PaddingValues(24.dp),
            nodeIdPrefix = "following-season/seasons",
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            focusItemCount = followingSeasons.size,
            focusItemKeys = followingSeasons.map { WjzFocusItemKey("Long:${it.seasonId}") },
            focusColumnCount = 6,
            onEntryFocusReady = onContentEntryReady,
            topologyRegion = topologyRegion.enabledIf(active)
        ) { cardUiStateFor ->
            if (followingSeasons.isNotEmpty()) {
                itemsIndexed(
                    items = followingSeasons,
                    key = { _, followingSeason -> followingSeason.seasonId }
                ) { index, followingSeason ->
                    SeasonCard(
                        modifier = rememberTvGridFocusModifier(index),
                        data = SeasonCardData(
                            seasonId = followingSeason.seasonId,
                            title = followingSeason.title,
                            cover = followingSeason.cover.resizedImageUrl(ImageSize.SeasonCoverThumbnail),
                            rating = null
                        ),
                        onFocus = {
                            currentIndex = index
                            if (active && index + 30 > followingSeasons.size) {
                                println("load more by focus")
                                followingSeasonViewModel.loadMore()
                            }
                        },
                        onClick = {
                            SeasonInfoActivity.actionStart(
                                context = context,
                                seasonId = followingSeason.seasonId,
                                proxyArea = ProxyArea.checkProxyArea(followingSeason.title)
                            )
                        }
                    )
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyTip()
                }
            }

            if (followingSeasons.isEmpty() && noMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedButton(onClick = { showFilter = true }) {
                            Text(text = stringResource(R.string.filter_dialog_open_tip_click))
                        }
                    }
                }
            }
        }
    }

    FollowingSeasonFilter(
        show = showFilter,
        onHideFilter = { showFilter = false },
        selectedType = followingSeasonType,
        selectedStatus = followingSeasonStatus,
        onSelectedTypeChange = updateType,
        onSelectedStatusChange = updateStatus
    )
}
