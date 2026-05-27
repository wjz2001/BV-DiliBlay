package dev.aaa1115910.bv.screen.main.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.aaa1115910.biliapi.entity.cheese.PurchasedCourse
import dev.aaa1115910.bv.activities.video.CheeseSeasonActivity
import dev.aaa1115910.bv.wjzfocus.WjzFocusItemKey
import dev.aaa1115910.bv.component.videocard.SmallVideoCard
import dev.aaa1115910.bv.component.videocard.SmallVideoCardGridHost
import dev.aaa1115910.bv.component.rememberTvGridFocusTarget
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.viewmodel.user.MyClassroomViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.koin.androidx.compose.koinViewModel

@Composable
fun MyClassroomScreen(
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    activationSerial: Long = 0L,
    refreshSerial: Long = 0L,
    onContentEntryReady: () -> Unit = {},
    myClassroomViewModel: MyClassroomViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val courses by myClassroomViewModel.courses.collectAsStateWithLifecycle()

    LaunchedEffect(activationSerial) {
        if (activationSerial == 0L) return@LaunchedEffect
        withFrameNanos { }
        myClassroomViewModel.ensureLoaded()
    }

    LaunchedEffect(refreshSerial) {
        if (refreshSerial == 0L) return@LaunchedEffect
        gridState.scrollToItem(0)
        myClassroomViewModel.reloadAll()
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .filter { index -> index != null && index >= courses.size - 12 }
            .collect { myClassroomViewModel.loadMore() }
    }

    SmallVideoCardGridHost(
        modifier = modifier,
        state = gridState,
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(24.dp),
        nodeIdPrefix = "my-classroom/courses",
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        focusItemCount = courses.size,
        focusItemKeys = courses.map { WjzFocusItemKey("Long:${it.seasonId}") },
        focusColumnCount = 4,
        onEntryFocusReady = onContentEntryReady
    ) { cardUiStateFor ->
        if (courses.isNotEmpty()) {
            itemsIndexed(courses, key = { _, course -> course.seasonId }) { index, course ->
                SmallVideoCard(
                    focusTarget = rememberTvGridFocusTarget(index),
                    uiState = cardUiStateFor(-course.seasonId),
                    data = course.toVideoCardData(),
                    titleMaxLines = 3,
                    classroomDirectUpNavigation = true,
                    upButtonOnly = true,
                    onClick = {
                        CheeseSeasonActivity.actionStart(
                            context = context,
                            seasonId = course.seasonId
                        )
                    },
                    onGoToUpPage = null,
                    onAddWatchLater = null
                )
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyTip()
            }
        }
    }
}

private fun PurchasedCourse.toVideoCardData(): VideoCardData {
    return VideoCardData(
        avid = -seasonId,
        title = title,
        cover = cover,
        upName = upName,
        upMid = upMid,
        playString = "",
        danmakuString = "",
        timeString = epCountText,
        pubTime = null
    )
}
