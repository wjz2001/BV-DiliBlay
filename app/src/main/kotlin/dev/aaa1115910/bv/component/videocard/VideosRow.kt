package dev.aaa1115910.bv.component.videocard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.aaa1115910.bv.component.ifElse
import dev.aaa1115910.bv.entity.carddata.VideoCardData

@Composable
fun VideosRow(
    modifier: Modifier = Modifier,
    header: String,
    videos: List<VideoCardData>,
    onVideoClicked: (VideoCardData) -> Unit,
    onAddWatchLater: ((Long) -> Unit)? = null,
    onGoToDetailPage: ((Long) -> Unit)? = null,
    onGoToUpPage: ((Long, String) -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    var hasFocus by remember { mutableStateOf(false) }
    val titleColor = if (hasFocus) Color.White else Color.White.copy(alpha = 0.6f)
    val titleFontSize by animateFloatAsState(
        targetValue = if (hasFocus) 30f else 14f,
        label = "title font size"
    )
    var rowHeight by remember { mutableStateOf(0.dp) }

    Column(
        modifier = modifier.onFocusChanged { hasFocus = it.hasFocus }
    ) {
        Text(
            modifier = Modifier.padding(start = 50.dp),
            text = header,
            fontSize = titleFontSize.sp,
            color = titleColor
        )
        LazyRow(
            modifier = Modifier
                .padding(top = 15.dp)
                .focusRestorer(focusRequester)
                .onGloballyPositioned {
                    rowHeight = with(density) {
                        it.size.height.toDp()
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Top,
            contentPadding = PaddingValues(horizontal = 62.dp)
        ) {
            itemsIndexed(items = videos) { index, videoData ->
                SmallVideoCard(
                    modifier = Modifier
                        .width(200.dp)
                        .ifElse(index == 0, Modifier.focusRequester(focusRequester)),
                    data = videoData,
                    onClick = { onVideoClicked(videoData) },
                    onAddWatchLater = onAddWatchLater?.let { { it(videoData.avid) } },
                    onGoToDetailPage = onGoToDetailPage?.let { { it(videoData.avid) } },
                    onGoToUpPage = onGoToUpPage?.let { f ->
                        videoData.upMid?.let { mid ->
                            { f(mid, videoData.upName) }
                        }
                    }
                )
            }
        }
    }
}
