package dev.aaa1115910.bv.screen.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.aaa1115910.biliapi.entity.cheese.PurchasedCourse
import dev.aaa1115910.bv.activities.video.CheeseSeasonActivity
import dev.aaa1115910.bv.component.videocard.SmallVideoCard
import dev.aaa1115910.bv.component.videocard.SmallVideoCardGridHost
import dev.aaa1115910.bv.component.videocard.rememberGridRowWrapModifier
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.viewmodel.user.MyClassroomViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.koin.androidx.compose.koinViewModel

@Composable
fun MyClassroomScreen(
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    myClassroomViewModel: MyClassroomViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val courses = myClassroomViewModel.courses

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
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        horizontalWrapItemCount = courses.size,
        horizontalWrapColumnCount = 4
    ) {
        if (courses.isNotEmpty()) {
            itemsIndexed(courses, key = { _, course -> course.seasonId }) { index, course ->
                SmallVideoCard(
                    frameModifier = rememberGridRowWrapModifier(index),
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
