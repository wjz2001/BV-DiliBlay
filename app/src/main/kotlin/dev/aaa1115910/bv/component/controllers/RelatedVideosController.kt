package dev.aaa1115910.bv.component.controllers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.component.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.component.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.component.wjzfocus.WjzFocusSourceToken
import dev.aaa1115910.bv.component.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.component.wjzfocus.wjzFocusNode
import dev.aaa1115910.bv.component.videocard.VideosRow
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.ui.theme.C
import kotlinx.collections.immutable.toPersistentList

private val RelatedVideosRootNodeId = WjzFocusNodeId("player/related-videos/root")

@Composable
fun RelatedVideosController(
    modifier: Modifier = Modifier,
    show: Boolean,
    relatedVideos: List<VideoCardData>,
    onVideoClicked: (VideoCardData) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusCoordinator = LocalWjzFocusCoordinator.current
    val rowVideos = remember(relatedVideos) { relatedVideos.toPersistentList() }
    var overlaySourceToken by remember { mutableStateOf<WjzFocusSourceToken?>(null) }
    val currentOverlaySourceToken by rememberUpdatedState(overlaySourceToken)

/*
    val backgroundBrush = remember {
        Brush.verticalGradient(
            // 使用 Pair 来指定位置和颜色
            0.0f to Color.Transparent,
            0.15f to Color.Black.copy(alpha = 0.5f),
            0.85f to Color.Black.copy(alpha = 0.5f),
            1.0f to Color.Transparent
        )
    }

 */

    LaunchedEffect(show) {
        if(show){
            overlaySourceToken?.let { token ->
                focusCoordinator?.restoreSourceLayer(
                    expectedActiveLayer = WjzFocusLayer.Overlay,
                    token = token
                )
                overlaySourceToken = null
            }
            overlaySourceToken = focusCoordinator?.activateLayer(
                layer = WjzFocusLayer.Overlay,
                recordSource = true
            )
            focusCoordinator?.requestFocus(
                nodeId = RelatedVideosRootNodeId,
                layer = WjzFocusLayer.Overlay
            )
        } else if (overlaySourceToken != null) {
            focusCoordinator?.restoreSourceLayer(
                expectedActiveLayer = WjzFocusLayer.Overlay,
                token = overlaySourceToken
            )
            overlaySourceToken = null
        }
    }

    DisposableEffect(focusCoordinator) {
        onDispose {
            currentOverlaySourceToken?.let { token ->
                focusCoordinator?.restoreSourceLayer(
                    expectedActiveLayer = WjzFocusLayer.Overlay,
                    token = token
                )
                overlaySourceToken = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .wjzFocusNode(
                nodeId = RelatedVideosRootNodeId,
                requester = focusRequester,
                layer = WjzFocusLayer.Overlay,
                fallback = true,
                enabled = show
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = show,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            // 使用 Box 包裹 VideosRow 并应用背景
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(C.scrim)
                    .padding(vertical = 12.dp)
            ) {
                VideosRow(
                    modifier = Modifier,
                    header = stringResource(R.string.video_info_related_video_title),
                    focusedHeaderColor = C.onScrim,
                    unfocusedHeaderColor = C.onScrim.copy(alpha = 0.6f),
                    videos = rowVideos,
                    onVideoClicked = onVideoClicked
                )
            }
        }
    }
}

@Preview
@Composable
fun RelatedVideosControllerPreview() {
    RelatedVideosController(
        show = true,
        relatedVideos = listOf(
            VideoCardData(
                avid = 0,
                cid = 0,
                title = "标题",
                cover = "",
                upName = "aaa",
            )
        ),
        onVideoClicked = {  }
    )
}
