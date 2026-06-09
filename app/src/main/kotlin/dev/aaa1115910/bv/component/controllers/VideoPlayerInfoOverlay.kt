package dev.aaa1115910.bv.component.controllers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.video.VideoShot
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntrySurface
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryId
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusSubmitIntent
import dev.aaa1115910.bv.wjzfocus.resolve
import dev.aaa1115910.bv.wjzfocus.target
import dev.aaa1115910.bv.wjzfocus.down
import dev.aaa1115910.bv.wjzfocus.left
import dev.aaa1115910.bv.wjzfocus.right
import dev.aaa1115910.bv.wjzfocus.up
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.ui.state.SeekerState
import dev.aaa1115910.bv.ui.theme.AppBlack
import dev.aaa1115910.bv.ui.theme.AppWhite
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.ThemeMode
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.VideoShotImageCache
import dev.aaa1115910.bv.util.formatHourMinSec
import dev.aaa1115910.bv.entity.VideoFlip
import dev.aaa1115910.bv.entity.VideoRotation

import kotlinx.coroutines.delay
import java.util.Calendar

private fun playerControlsEntryId(id: String): WjzFocusEntryId {
    return WjzFocusEntryId.parse("$PlayerControlsFocusComponentId/$id")
}

@Composable
internal fun VideoPlayerInfoOverlay(
    modifier: Modifier = Modifier,
    show: Boolean,
    isSeeking: Boolean,
    goTime: Long,
    seekerState: SeekerState,
    title: String,
    secondTitle: String,
    currentPlaySpeed: Float,
    videoShot: VideoShot?,
    videoShotCache: VideoShotImageCache,
    videoRotation: VideoRotation?,
    videoFlip: VideoFlip?,
    actions: List<VideoPlayerOverlayAction>,
    focusButtonsOnShow: Boolean = false,
    onConsumeFocusButtonsOnShow: () -> Unit = {}
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.TopCenter),
            visible = show,
            enter = expandVertically(),
            exit = shrinkVertically(),
            label = "ControllerTopVideoInfo"
        ) {
            VideoPlayerInfoOverlayTop(
                modifier = Modifier.align(Alignment.TopCenter),
                title = title,
                totalDuration = seekerState.totalDuration,
                isSeeking = isSeeking,
                goTime = goTime,
                seekerState = seekerState,
                currentPlaySpeed = currentPlaySpeed
            )
        }
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.BottomCenter),
            visible = show,
            enter = expandVertically(),
            exit = shrinkVertically(),
            label = "ControllerBottomVideoInfo"
        ) {
            VideoPlayerInfoOverlayBottom(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                show = show,
                isSeeking = isSeeking,
                goTime = goTime,
                seekerState = seekerState,
                secondTitle = secondTitle,
                videoShot = videoShot,
                videoShotCache = videoShotCache,
                videoRotation = videoRotation,
                videoFlip = videoFlip,
                actions = actions,
                focusButtonsOnShow = focusButtonsOnShow,
                onConsumeFocusButtonsOnShow = onConsumeFocusButtonsOnShow
            )
        }
    }
}

@Composable
internal fun VideoPlayerInfoOverlayTop(
    modifier: Modifier = Modifier,
    title: String,
    isSeeking: Boolean,
    goTime: Long,
    seekerState: SeekerState,
    totalDuration: Long = 0,
    currentPlaySpeed: Float
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(C.scrim)
            .padding(horizontal = 32.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    shadow = Shadow(
                        color = AppBlack,
                        blurRadius = 1f
                    ),
                ),
                color = AppWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Clock(
                currentTime = if (isSeeking) goTime else seekerState.currentTime,
                totalDuration = totalDuration,
                currentPlaySpeed = currentPlaySpeed
            )
        }
    }
}

@Composable
internal fun VideoPlayerInfoOverlayBottom(
    modifier: Modifier = Modifier,
    secondTitle: String,
    show: Boolean,
    isSeeking: Boolean,
    goTime: Long,
    seekerState: SeekerState,
    videoShot: VideoShot?,
    videoShotCache: VideoShotImageCache,
    videoRotation: VideoRotation?,
    videoFlip: VideoFlip?,
    actions: List<VideoPlayerOverlayAction>,
    focusButtonsOnShow: Boolean = false,
    onConsumeFocusButtonsOnShow: () -> Unit = {}
) {
    val focusCoordinator = LocalWjzFocusCoordinator.current
    val focusScopeId = LocalWjzFocusScopeId.current

    LaunchedEffect(show, focusButtonsOnShow, focusCoordinator, focusScopeId) {
        if (show) {
            delay(50)
            if (focusButtonsOnShow) {
                focusCoordinator?.submitEntryFocusIntent(
                    entryId = WjzFocusEntryId.parse(PlayerControlsFocusComponentId),
                    intent = WjzFocusSubmitIntent.ExternalEntry(
                        dedupeKey = PlayerControlsFocusComponentId
                    )
                )
                onConsumeFocusButtonsOnShow()
            }
        }
    }
    Box(
        modifier = modifier
            .background(C.scrim)
            .padding(top = 5.dp)
    ) {
        if (isSeeking && videoShot != null) {
            Box(
                modifier = Modifier.matchParentSize()
            ) {
                VideoShot(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 48.dp)
                        .offset(y = (-108).dp),
                    videoShot = videoShot,
                    imageCache = videoShotCache,
                    position = goTime,
                    duration = seekerState.totalDuration,
                    coercedOffset = (-24).dp,
                    videoRotation = videoRotation,
                    videoFlip = videoFlip
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.Bottom
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier
                        .padding(horizontal = 32.dp),
                    text = "${if (isSeeking) goTime.formatHourMinSec() else seekerState.currentTime.formatHourMinSec()} / ${seekerState.totalDuration.formatHourMinSec()}",
                    color = AppWhite,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(
                        shadow = Shadow(color = AppBlack, blurRadius = 1f),
                    ),
                )
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 32.dp),
                    textAlign = TextAlign.Center,
                    text = secondTitle,
                    color = AppWhite,
                    style = TextStyle(
                        shadow = Shadow(color = AppBlack, blurRadius = 1f),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    modifier = Modifier
                        .padding(horizontal = 32.dp),
                    text = "${if (isSeeking) goTime.formatHourMinSec() else seekerState.currentTime.formatHourMinSec()} / ${seekerState.totalDuration.formatHourMinSec()}",
                    color = AppWhite,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(
                        shadow = Shadow(color = AppBlack, blurRadius = 1f),
                    ),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                VideoProgressSeek(
                    modifier = Modifier.fillMaxWidth(),
                    duration = seekerState.totalDuration,
                    position = if (isSeeking) goTime else seekerState.currentTime,
                    bufferedPercentage = seekerState.bufferedPercentage,
                    isPersistentSeek = false
                )
            }

            WjzFocusEntrySurface(
                componentId = PlayerControlsFocusComponentId,
                default = {
                    val scopeId = requireNotNull(focusScopeId) {
                        "PlayerControls entry requires WjzFocus scope"
                    }
                    scopeId.target(
                        actions.firstOrNull()?.focusLocalId ?: PlayerControlsFirstActionFocusLocalId
                    ).copy(layer = WjzFocusLayer.Player)
                },
                entries = {
                    actions.forEach { action ->
                        val scopeId = requireNotNull(focusScopeId) {
                            "PlayerControls entry requires WjzFocus scope"
                        }
                        entry(action.id) move scopeId.target(action.focusLocalId)
                            .copy(layer = WjzFocusLayer.Player)
                    }
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start)
            ) {
                actions.forEachIndexed { index, action ->
                    val previousEntryId = playerControlsEntryId(
                        actions.getOrNull(index - 1)?.id ?: actions.last().id
                    )
                    val nextEntryId = playerControlsEntryId(
                        actions.getOrNull(index + 1)?.id ?: actions.first().id
                    )
                    Surface(
                        modifier = Modifier
                            .then(
                                if (focusScopeId != null) {
                                    Modifier.wjzFocusExits(
                                        nodeId = focusScopeId.resolve(action.focusLocalId),
                                        scopeId = focusScopeId,
                                        layer = WjzFocusLayer.Player,
                                        enabled = show,
                                        exits = {
                                            left move previousEntryId
                                            right move nextEntryId
                                            cancel(up)
                                            cancel(down)
                                        }
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        onClick = action.onClick,
                        shape = ClickableSurfaceDefaults.shape(
                            shape = MaterialTheme.shapes.extraSmall.copy(all = CornerSize(0.dp)),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = AppBlack,
                            contentColor = AppWhite,
                            focusedContainerColor = AppWhite,
                            focusedContentColor = AppBlack,
                            pressedContainerColor = AppWhite,
                            pressedContentColor = AppBlack
                        )
                    ) {
                        when (action) {
                            is VideoPlayerOverlayAction.Resource -> {
                                Icon(
                                    painter = painterResource(id = action.iconRes),
                                    contentDescription = action.description,
                                    modifier = Modifier.padding(5.dp)
                                )
                            }

                            is VideoPlayerOverlayAction.Vector -> {
                                Icon(
                                    imageVector = action.imageVector,
                                    contentDescription = action.description,
                                    modifier = Modifier.padding(5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Clock(
    modifier: Modifier = Modifier,
    currentTime: Long,
    totalDuration: Long,
    currentPlaySpeed: Float
) {
    var clockText by remember { mutableStateOf("") }
    var finishTimeText by remember { mutableStateOf("") }

    LaunchedEffect(currentTime, totalDuration, currentPlaySpeed) {
        if (totalDuration > 0 && currentTime < totalDuration && currentPlaySpeed > 0) {
            val remainingMillis = totalDuration - currentTime
            val actualRemainingMillis = (remainingMillis / currentPlaySpeed).toLong()

            val finishTime = Calendar.getInstance().apply {
                add(Calendar.MILLISECOND, actualRemainingMillis.toInt())
            }

            val finishHour = finishTime.get(Calendar.HOUR_OF_DAY)
            val finishMinute = finishTime.get(Calendar.MINUTE)
            val finishSecond = finishTime.get(Calendar.SECOND)

            finishTimeText =
                "${finishHour.toString().padStart(2, '0')}:${finishMinute.toString().padStart(2, '0')}:${finishSecond.toString().padStart(2, '0')} 结束"
        } else {
            finishTimeText = ""
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val second = calendar.get(Calendar.SECOND)
            clockText = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 32.sp)) {
                    append(hour.toString().padStart(2, '0'))
                    append(":")
                    append(minute.toString().padStart(2, '0'))
                    append(":")
                    append(second.toString().padStart(2, '0'))
                }
            }.toString()
            delay(1000)
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            color = AppWhite,
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                shadow = Shadow(color = AppBlack, blurRadius = 1f),
                fontSize = 32.sp
            ),
            text = clockText
        )
        if (finishTimeText.isNotEmpty()) {
            Text(
                modifier = Modifier.padding(top = 2.dp),
                color = AppWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                style = TextStyle(
                    shadow = Shadow(color = AppBlack, blurRadius = 1f),
                ),
                text = finishTimeText
            )
        }
    }
}

@Preview
@Composable
private fun ClockPreview() {
    BVTheme {
        Clock(
            currentTime = 1000 * 60 * 15,
            totalDuration = 1000 * 60 * 45,
            currentPlaySpeed = 1.0f
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun VideoPlayerInfoOverlayPreview() {
    var show by remember { mutableStateOf(true) }

    BVTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppWhite),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = { show = !show }) {
                Text(text = "Switch")
            }
        }
        VideoPlayerInfoOverlay(
            modifier = Modifier.fillMaxSize(),
            show = show,
            isSeeking = false,
            goTime = 0,
            seekerState = SeekerState(0, 0, 0, ""),
            title = "【A320】民航史上最佳逆袭！A320的前世今生！民航史上最佳逆袭！A320的前世今生！",
            secondTitle = "哈哈哈",
            currentPlaySpeed = 1.0f,
            videoShot = null,
            videoShotCache = VideoShotImageCache(),
            videoRotation = null,
            videoFlip = null,
            actions = emptyList(),
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun VideoPlayerInfoOverlayLightPreview() {
    var show by remember { mutableStateOf(true) }

    BVTheme(themeMode = ThemeMode.LIGHT) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppWhite),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = { show = !show }) {
                Text(text = "Switch")
            }
        }
        VideoPlayerInfoOverlay(
            modifier = Modifier.fillMaxSize(),
            show = show,
            isSeeking = false,
            goTime = 0,
            seekerState = SeekerState(0, 0, 0, ""),
            title = "【A320】民航史上最佳逆袭！A320的前世今生！民航史上最佳逆袭！A320的前世今生！",
            secondTitle = "哈哈哈",
            currentPlaySpeed = 1.0f,
            videoShot = null,
            videoShotCache = VideoShotImageCache(),
            videoRotation = null,
            videoFlip = null,
            actions = emptyList(),
        )
    }
}
