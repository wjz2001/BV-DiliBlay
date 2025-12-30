package dev.aaa1115910.bv.component.controllers

import android.os.CountDownTimer
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.video.VideoShot
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.entity.VideoPlayerInfoData
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.countDownTimer
import dev.aaa1115910.bv.util.formatHourMinSec
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun ControllerVideoInfo(
    modifier: Modifier = Modifier,
    show: Boolean,
    isSeeking: Boolean,
    goTime: Long,
    infoData: VideoPlayerInfoData,
    title: String,
    subtitle: String,
    clock: Triple<Int, Int, Int>,
    currentPlaySpeed: Float,
    videoShot: VideoShot?,
    fromSeason: Boolean,
    danmakuEnabled: Boolean,
    isLooping: Boolean,
    onHideInfo: () -> Unit,
    onDirectionLeft: () -> Unit,
    onDirectionRight: () -> Unit,
    onSeekGoTime: () -> Unit,
    onPlayPause: () -> Unit,
    onDanmakuSwitchChange: () -> Unit,
    onShowSettings: () -> Unit,
    onGoToVideoInfo: () -> Unit,
    onToggleLoop: () -> Unit,
    onGoToUpPage: () -> Unit
) {
    var hideVideoInfoTimer: CountDownTimer? by remember { mutableStateOf(null) }
    val setHideVideoInfoTimer = {
        hideVideoInfoTimer?.cancel()
        hideVideoInfoTimer = countDownTimer(5000, 1000, "hideVideoInfoTimer") {
            onHideInfo()
        }
    }
    LaunchedEffect(show) {
        if (show) {
            setHideVideoInfoTimer()
        } else {
            hideVideoInfoTimer?.cancel()
            hideVideoInfoTimer = null
        }
    }

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
                    currentTime = infoData.currentTime,
                    totalDuration = infoData.totalDuration,
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
                        .align(Alignment.BottomCenter)
                        .onPreviewKeyEvent {
                            hideVideoInfoTimer?.cancel()
                            setHideVideoInfoTimer()
                            return@onPreviewKeyEvent false
                        },
                    subtitle = subtitle,
                    show = show,
                    isSeeking = isSeeking,
                    goTime = goTime,
                    infoData = infoData,
                    videoShot = videoShot,
                    fromSeason = fromSeason,
                    danmakuEnabled = danmakuEnabled,
                    isLooping = isLooping,
                    onDirectionLeft = onDirectionLeft,
                    onDirectionRight = onDirectionRight,
                    onSeekGoTime = onSeekGoTime,
                    onPlayPause = onPlayPause,
                    onDanmakuSwitchChange = onDanmakuSwitchChange,
                    onShowSettings = onShowSettings,
                    onGoToVideoInfo = onGoToVideoInfo,
                    onToggleLoop = onToggleLoop,
                    onGoToUpPage = onGoToUpPage
                )
            }
        }
}

@Composable
fun ControllerVideoInfoTop(
    modifier: Modifier = Modifier,
    title: String,
    clock: Triple<Int, Int, Int>,
    currentTime: Long = 0,
    totalDuration: Long = 0,
    // 3. 接收播放速度
    currentPlaySpeed: Float
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            /*
            .clip(
                MaterialTheme.shapes.large.copy(
                    topStart = CornerSize(0.dp),
                    topEnd = CornerSize(0.dp)
                )
            )
             */
            .background(
                Color.Black.copy(alpha = 0.5f)
                /*
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.5f), // 上部颜色较深
                        Color.Black.copy(alpha = 0f)  // 下部颜色较浅
                    )
                )
                 */
            )
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
                        color = Color.Black,
                        blurRadius = 1f
                    ),
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Clock(
                hour = clock.first,
                minute = clock.second,
                second = clock.third,
                // 4. 将计算所需的所有数据都传给 Clock 组件
                currentTime = currentTime,
                totalDuration = totalDuration,
                currentPlaySpeed = currentPlaySpeed
            )
        }
    }
}

@Composable
fun ControllerVideoInfoBottom(
    modifier: Modifier = Modifier,
    subtitle: String,
    show: Boolean,
    isSeeking: Boolean,
    goTime: Long,
    infoData: VideoPlayerInfoData,
    videoShot: VideoShot?,
    fromSeason: Boolean,
    danmakuEnabled: Boolean,
    isLooping: Boolean,
    onDirectionLeft: () -> Unit,
    onDirectionRight: () -> Unit,
    onSeekGoTime: () -> Unit,
    onPlayPause: () -> Unit,
    onDanmakuSwitchChange: () -> Unit,
    onShowSettings: () -> Unit,
    onGoToVideoInfo: () -> Unit,
    onToggleLoop: () -> Unit,
    onGoToUpPage: () -> Unit
) {
    val seekFocusRequester = remember { FocusRequester() }
    val buttonsFocusRequester = remember { FocusRequester() }

    var isSeekFocused by remember { mutableStateOf(false) }

    LaunchedEffect(show) {
        if (show) {
            delay(50)
            try {
                seekFocusRequester.requestFocus()
            } catch (e: IllegalStateException) {
                Log.d("ControllerVideoInfo", "requestFocus failed")
            }
        }
    }
    Column(
        modifier = modifier
            /*
            .clip(
                MaterialTheme.shapes.large

            )
             */
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(top = 5.dp),

        verticalArrangement = Arrangement.Bottom
    ) {
        if (isSeeking && videoShot != null) {
            VideoShot(
                modifier = Modifier
                    .padding(horizontal = 48.dp),
                videoShot = videoShot,
                position = goTime,
                duration = infoData.totalDuration,
                coercedOffset = (-24).dp
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier
                    .padding(horizontal = 24.dp),
                text = "${if (isSeeking) goTime.formatHourMinSec() else infoData.currentTime.formatHourMinSec()} / ${infoData.totalDuration.formatHourMinSec()}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = TextStyle(
                    shadow = Shadow(color = Color.Black, blurRadius = 1f),
                ),
            )
            Text(
                modifier = Modifier
                    .weight(1f),
                textAlign = TextAlign.Center,
                text = subtitle,
                color = Color.White,
                style = TextStyle(
                    shadow = Shadow(color = Color.Black, blurRadius = 1f),
                ),
                maxLines = 1,
                // 如果文本超出1行，则以省略号(...)结尾
                overflow = TextOverflow.Ellipsis
            )
            Text(
                modifier = Modifier
                    .padding(horizontal = 24.dp),
                text = "${if (isSeeking) goTime.formatHourMinSec() else infoData.currentTime.formatHourMinSec()} / ${infoData.totalDuration.formatHourMinSec()}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = TextStyle(
                    shadow = Shadow(color = Color.Black, blurRadius = 1f),
                ),
            )
        }
        /*
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                modifier = Modifier.padding(bottom = 2.dp, start = 24.dp),
                text = "${if (isSeeking) goTime.formatHourMinSec() else infoData.currentTime.formatHourMinSec()} / ${infoData.totalDuration.formatHourMinSec()}",
                color = Color.White,
                style = TextStyle(
                    shadow = Shadow(color = Color.Black, blurRadius = 1f),
                ),
            )
        }
         */
        Row(
            modifier = Modifier
                // .padding(horizontal = 24.dp)
                /*
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = if (isSeekFocused) 1f else 0f),
                    shape = RoundedCornerShape(8.dp)
                )
                */
                .focusable()
                .focusRequester(seekFocusRequester)
                .onKeyEvent {
                    when (it.key) {
                        Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                            if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                            if (isSeeking) {
                                onSeekGoTime()
                            } else {
                                onPlayPause()
                            }
                            return@onKeyEvent true
                        }

                        Key.DirectionLeft, Key.MediaRewind -> {
                            if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                            onDirectionLeft()
                            return@onKeyEvent true
                        }

                        Key.DirectionRight, Key.MediaFastForward -> {
                            if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                            onDirectionRight()
                            return@onKeyEvent true
                        }

                        Key.DirectionDown -> {
                            if (it.type == KeyEventType.KeyDown) return@onKeyEvent true
                            buttonsFocusRequester.requestFocus()
                            return@onKeyEvent true
                        }
                    }
                    return@onKeyEvent false
                }
                .onFocusChanged {
                    isSeekFocused = it.isFocused
                },
        ) {
            VideoProgressSeek(
                modifier = Modifier
                    .focusable()
                    .fillMaxWidth(),
                duration = infoData.totalDuration,
                position = if (isSeeking) goTime else infoData.currentTime,
                bufferedPercentage = infoData.bufferedPercentage,
                isPersistentSeek = false
            )
        }

        val icons = listOfNotNull(
            // (R.drawable.play_pause_24px to "播放/暂停") to onPlayPause,
            ((if (danmakuEnabled) (R.drawable.danmaku_on_24px) else (R.drawable.danmaku_off_24px)) to "弹幕开关") to onDanmakuSwitchChange,
            // (R.drawable.settings_24px to "打开设置") to onShowSettings,
            // if (!fromSeason) (R.drawable.info_24px to "视频信息") to onGoToVideoInfo else null,
            if (!fromSeason) (R.drawable.contact_page_24px to "up主页") to onGoToUpPage else null,
            ((if (isLooping) (R.drawable.repeat_one_on_24px) else (R.drawable.repeat_one_24px)) to "循环播放") to onToggleLoop,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(buttonsFocusRequester)
                .onKeyEvent {
                    if (it.key == Key.DirectionUp) {
                        if (it.type == KeyEventType.KeyDown) return@onKeyEvent true
                        seekFocusRequester.requestFocus()
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start)
        ) {
            icons.forEach { (icon, function) ->
                Surface(
                    onClick = function,
                    shape = ClickableSurfaceDefaults.shape(
                        shape = MaterialTheme.shapes.small,
                    ),
                ) {
                    Icon(
                        painter = painterResource(id = icon.first),
                        contentDescription = icon.second,
                        modifier = Modifier.padding(5.dp)
                    )
                }
            }
        }
    }
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
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                shadow = Shadow(color = Color.Black, blurRadius = 1f),
                fontSize = 32.sp
            ),
            text = clockText
            /*
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 32.sp)) {
                    append("$hour".padStart(2, '0'))
                    append(":")
                    append("$minute".padStart(2, '0'))
                    append(":")
                    append("$second".padStart(2, '0'))
                }
            }
             */
        )
        // 视频结束时间
        if (finishTimeText.isNotEmpty()) {
            Text(
                modifier = Modifier.padding(top = 2.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                style = TextStyle(
                    shadow = Shadow(color = Color.Black, blurRadius = 1f),
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

    BVTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
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
            infoData = VideoPlayerInfoData(
                totalDuration = 1000 * 60 * 45, // 45分钟
                currentTime = 1000 * 60 * 10,   // 10分钟
                bufferedPercentage = 66,
                resolutionWidth = 0,
                resolutionHeight = 0,
                codec = ""
            ),
            title = "【A320】民航史上最佳逆袭！A320的前世今生！民航史上最佳逆袭！A320的前世今生！",
            subtitle = "哈哈哈",
            clock = Triple(12, 30, 30),
            currentPlaySpeed = 1.0f,
            videoShot = null,
            fromSeason = false,
            danmakuEnabled = false,
            isLooping = false,
            onHideInfo = {},
            onDirectionRight = {},
            onDirectionLeft = {},
            onSeekGoTime = {},
            onPlayPause = {},
            onDanmakuSwitchChange = {},
            onShowSettings = {},
            onGoToVideoInfo = {},
            onToggleLoop = {},
            onGoToUpPage = {},
        )
    }
}