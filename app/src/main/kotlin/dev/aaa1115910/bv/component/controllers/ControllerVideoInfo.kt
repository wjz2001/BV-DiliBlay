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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.video.VideoShot
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntrySurface
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryId
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.resolve
import dev.aaa1115910.bv.wjzfocus.target
import dev.aaa1115910.bv.wjzfocus.down
import dev.aaa1115910.bv.wjzfocus.left
import dev.aaa1115910.bv.wjzfocus.right
import dev.aaa1115910.bv.wjzfocus.up
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.wjzfocus.wjzFocusLocalId
import dev.aaa1115910.bv.entity.VideoSource
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

private const val PlayerControlsFocusComponentId = "playerControls"
private val PlayerControllerFirstActionFocusLocalId = playerControllerActionFocusLocalId("danmaku")

private fun playerControllerActionFocusLocalId(id: String): WjzFocusLocalId =
    wjzFocusLocalId("actions", id)

private fun playerControlsEntryId(id: String): WjzFocusEntryId {
    return WjzFocusEntryId.parse("$PlayerControlsFocusComponentId/$id")
}

@Composable
fun ControllerVideoInfo(
    modifier: Modifier = Modifier,
    show: Boolean,
    isSeeking: Boolean,
    goTime: Long,
    seekerState: SeekerState,
    title: String,
    secondTitle: String,
    clock: Pair<Int, Int>,
    currentPlaySpeed: Float,
    videoShot: VideoShot?,
    videoShotCache: VideoShotImageCache,
    videoRotation: VideoRotation?,
    videoFlip: VideoFlip?,
    source: VideoSource,
    danmakuEnabled: Boolean,
    isLooping: Boolean,
    onDirectionLeft: () -> Unit,
    onDirectionRight: () -> Unit,
    onSeekGoTime: () -> Unit,
    onPlayPause: () -> Unit,
    onDanmakuSwitchChange: () -> Unit,
    onShowSettings: () -> Unit,
    onShowRelatedVideos: () -> Unit,
    onGoToVideoInfo: () -> Unit,
    onToggleLoop: () -> Unit,
    onGoToUpPage: () -> Unit,
    onShowTimeJump: () -> Unit,
    onShowComments: () -> Unit,
    showVideoInfoEntry: Boolean = false,
    hasMultipleCoAuthors: Boolean = false,
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
            ControllerVideoInfoTop(
                modifier = Modifier.align(Alignment.TopCenter),
                title = title,
                clock = clock,
                currentTime = seekerState.currentTime,
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
            ControllerVideoInfoBottom(
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
                source = source,
                danmakuEnabled = danmakuEnabled,
                isLooping = isLooping,
                onDirectionLeft = onDirectionLeft,
                onDirectionRight = onDirectionRight,
                onSeekGoTime = onSeekGoTime,
                onPlayPause = onPlayPause,
                onDanmakuSwitchChange = onDanmakuSwitchChange,
                onShowSettings = onShowSettings,
                onShowRelatedVideos = onShowRelatedVideos,
                onGoToVideoInfo = onGoToVideoInfo,
                onToggleLoop = onToggleLoop,
                onGoToUpPage = onGoToUpPage,
                onShowTimeJump = onShowTimeJump,
                onShowComments = onShowComments,
                showVideoInfoEntry = showVideoInfoEntry,
                hasMultipleCoAuthors = hasMultipleCoAuthors,
                focusButtonsOnShow = focusButtonsOnShow,
                onConsumeFocusButtonsOnShow = onConsumeFocusButtonsOnShow
            )
        }
    }
}

@Composable
fun ControllerVideoInfoTop(
    modifier: Modifier = Modifier,
    title: String,
    isSeeking: Boolean,
    goTime: Long,
    seekerState: SeekerState,
    clock: Pair<Int, Int>,
    currentTime: Long = 0,
    totalDuration: Long = 0,
    // 3. 接收播放速度
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
                hour = clock.first,
                minute = clock.second,
                second = 0,
                // 4. 将计算所需的所有数据都传给 Clock 组件
                // currentTime = currentTime,
                currentTime = if (isSeeking) goTime else seekerState.currentTime,//结束时间随着进度条拖动变化
                totalDuration = totalDuration,
                currentPlaySpeed = currentPlaySpeed
            )
        }
    }
}

@Composable
fun ControllerVideoInfoBottom(
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
    source: VideoSource,
    danmakuEnabled: Boolean,
    isLooping: Boolean,
    onDirectionLeft: () -> Unit,
    onDirectionRight: () -> Unit,
    onSeekGoTime: () -> Unit,
    onPlayPause: () -> Unit,
    onDanmakuSwitchChange: () -> Unit,
    onShowSettings: () -> Unit,
    onShowRelatedVideos: () -> Unit,
    onGoToVideoInfo: () -> Unit,
    onToggleLoop: () -> Unit,
    onGoToUpPage: () -> Unit,
    onShowTimeJump: () -> Unit,
    onShowComments: () -> Unit,
    showVideoInfoEntry: Boolean = false,
    hasMultipleCoAuthors: Boolean = false,
    focusButtonsOnShow: Boolean = false,
    onConsumeFocusButtonsOnShow: () -> Unit = {}
) {
    val focusCoordinator = LocalWjzFocusCoordinator.current
    val focusScopeId = LocalWjzFocusScopeId.current

    LaunchedEffect(show, focusButtonsOnShow, focusCoordinator, focusScopeId) {
        if (show) {
            delay(50)
            if (focusButtonsOnShow) {
                focusCoordinator?.requestEntryFocus(WjzFocusEntryId.parse(PlayerControlsFocusComponentId))
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

            val icons = listOfNotNull(
                ControllerActionIcon.Resource(
                    id = "danmaku",
                    iconRes = if (danmakuEnabled) R.drawable.danmaku_on_24px else R.drawable.danmaku_off_24px,
                    description = "弹幕开关",
                    onClick = onDanmakuSwitchChange
                ),
                ControllerActionIcon.Resource(
                    id = "comments",
                    iconRes = R.drawable.comment_24px,
                    description = "评论",
                    onClick = onShowComments
                ),
                ControllerActionIcon.Resource(
                    id = "time-jump",
                    iconRes = R.drawable.manage_history_24px,
                    description = "时间跳转",
                    onClick = onShowTimeJump
                ),
                if (showVideoInfoEntry) {
                    ControllerActionIcon.Vector(
                        id = "video-info",
                        imageVector = Icons.Rounded.Info,
                        description = "详情页",
                        onClick = onGoToVideoInfo
                    )
                } else {
                    null
                },
                if (source.isUgc) {
                    ControllerActionIcon.Resource(
                        id = "up-page",
                        iconRes = if (hasMultipleCoAuthors) R.drawable.group_24px else R.drawable.contact_page_24px,
                        description = "up主页",
                        onClick = onGoToUpPage
                    )
                } else {
                    null
                },
                if (source.isUgc) {
                    ControllerActionIcon.Resource(
                        id = "related-videos",
                        iconRes = R.drawable.related_videos_24px,
                        description = "相关视频",
                        onClick = onShowRelatedVideos
                    )
                } else {
                    null
                },
                ControllerActionIcon.Resource(
                    id = "loop",
                    iconRes = if (isLooping) R.drawable.repeat_one_on_24px else R.drawable.repeat_one_24px,
                    description = "循环播放",
                    onClick = onToggleLoop
                ),
            )

            WjzFocusEntrySurface(
                componentId = PlayerControlsFocusComponentId,
                default = {
                    val scopeId = requireNotNull(focusScopeId) {
                        "PlayerControls entry requires WjzFocus scope"
                    }
                    scopeId.target(
                        icons.firstOrNull()?.focusLocalId ?: PlayerControllerFirstActionFocusLocalId
                    ).copy(layer = WjzFocusLayer.Player)
                },
                entries = {
                    icons.forEach { icon ->
                        val scopeId = requireNotNull(focusScopeId) {
                            "PlayerControls entry requires WjzFocus scope"
                        }
                        entry(icon.id) move scopeId.target(icon.focusLocalId)
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
                icons.forEachIndexed { index, icon ->
                    val previousEntryId = playerControlsEntryId(
                        icons.getOrNull(index - 1)?.id ?: icons.last().id
                    )
                    val nextEntryId = playerControlsEntryId(
                        icons.getOrNull(index + 1)?.id ?: icons.first().id
                    )
                    Surface(
                        modifier = Modifier
                            .then(
                                if (focusScopeId != null) {
                                    Modifier.wjzFocusExits(
                                        nodeId = focusScopeId.resolve(icon.focusLocalId),
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
                        onClick = icon.onClick,
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
                        when (icon) {
                            is ControllerActionIcon.Resource -> {
                                Icon(
                                    painter = painterResource(id = icon.iconRes),
                                    contentDescription = icon.description,
                                    modifier = Modifier.padding(5.dp)
                                )
                            }

                            is ControllerActionIcon.Vector -> {
                                Icon(
                                    imageVector = icon.imageVector,
                                    contentDescription = icon.description,
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

private sealed interface ControllerActionIcon {
    val id: String
    val description: String
    val onClick: () -> Unit
    val focusLocalId: WjzFocusLocalId
        get() = playerControllerActionFocusLocalId(id)

    data class Resource(
        override val id: String,
        val iconRes: Int,
        override val description: String,
        override val onClick: () -> Unit
    ) : ControllerActionIcon

    data class Vector(
        override val id: String,
        val imageVector: androidx.compose.ui.graphics.vector.ImageVector,
        override val description: String,
        override val onClick: () -> Unit
    ) : ControllerActionIcon
}

@Composable
private fun Clock(
    modifier: Modifier = Modifier,
    hour: Int,
    minute: Int,
    second: Int,
    // 5. 修改 Clock 组件的参数，接收计算所需的数据
    currentTime: Long,
    totalDuration: Long,
    currentPlaySpeed: Float
) {
    // 1. 创建两个状态，一个用于显示时钟，一个用于显示结束时间
    var clockText by remember { mutableStateOf("") }
    var finishTimeText by remember { mutableStateOf("") }

    // 2. 使用一个 LaunchedEffect，但它的 key 包含了所有外部依赖项
    // 这样，无论是时间流逝（内部 delay 驱动）还是外部播放进度变化（key 变化驱动），
    // 都会重新执行计算，保证了数据的即时性。
    LaunchedEffect(currentTime, totalDuration, currentPlaySpeed) {
        // 视频结束时间的计算逻辑
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
                "${String.format("%02d:%02d:%02d", finishHour, finishMinute, finishSecond)} 结束"
        } else {
            finishTimeText = ""
        }
    }

    // 3. 这个 LaunchedEffect 只负责驱动系统时钟的更新，每秒一次
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
            }.toString() // 将 AnnotatedString 转换为普通 String
            delay(1000)
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            // modifier = modifier,
            color = AppWhite,
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                shadow = Shadow(color = AppBlack, blurRadius = 1f),
                fontSize = 32.sp
            ),
            text = clockText
        )
        // 视频结束时间
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
    val clock = Triple(12, 30, 30)
    BVTheme {
        Clock(
            hour = clock.first,
            minute = clock.second,
            second = clock.third,
            currentTime = 1000 * 60 * 15, // 15分钟
            totalDuration = 1000 * 60 * 45, // 45分钟
            currentPlaySpeed = 1.0f
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun ControllerVideoInfoPreview() {
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
        ControllerVideoInfo(
            modifier = Modifier.fillMaxSize(),
            show = show,
            isSeeking = false,
            goTime = 0,
            seekerState = SeekerState(0, 0, 0, ""),
            title = "【A320】民航史上最佳逆袭！A320的前世今生！民航史上最佳逆袭！A320的前世今生！",
            secondTitle = "哈哈哈",
            clock = Pair(12, 30),
            currentPlaySpeed = 1.0f,
            videoShot = null,
            videoShotCache = VideoShotImageCache(),
            videoRotation = null,
            videoFlip = null,
            source = VideoSource.Ugc,
            danmakuEnabled = false,
            isLooping = false,
            onDirectionRight = {},
            onDirectionLeft = {},
            onSeekGoTime = {},
            onPlayPause = {},
            onDanmakuSwitchChange = {},
            onShowSettings = {},
            onShowRelatedVideos = {},
            onGoToVideoInfo = {},
            onToggleLoop = {},
            onGoToUpPage = {},
            onShowTimeJump = {},
            onShowComments = {},
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun ControllerVideoInfoLightPreview() {
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
        ControllerVideoInfo(
            modifier = Modifier.fillMaxSize(),
            show = show,
            isSeeking = false,
            goTime = 0,
            seekerState = SeekerState(0, 0, 0, ""),
            title = "【A320】民航史上最佳逆袭！A320的前世今生！民航史上最佳逆袭！A320的前世今生！",
            secondTitle = "哈哈哈",
            clock = Pair(12, 30),
            currentPlaySpeed = 1.0f,
            videoShot = null,
            videoShotCache = VideoShotImageCache(),
            videoRotation = null,
            videoFlip = null,
            source = VideoSource.Ugc,
            danmakuEnabled = false,
            isLooping = false,
            onDirectionRight = {},
            onDirectionLeft = {},
            onSeekGoTime = {},
            onPlayPause = {},
            onDanmakuSwitchChange = {},
            onShowSettings = {},
            onShowRelatedVideos = {},
            onGoToVideoInfo = {},
            onToggleLoop = {},
            onGoToUpPage = {},
            onShowTimeJump = {},
            onShowComments = {},
        )
    }
}
