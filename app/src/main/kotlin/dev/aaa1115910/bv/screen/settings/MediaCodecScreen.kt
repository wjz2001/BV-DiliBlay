package dev.aaa1115910.bv.screen.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.wjzfocus.WjzFocusHost
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.resolve
import dev.aaa1115910.bv.wjzfocus.wjzFocusGroupRestorerComponent
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.wjzfocus.wjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.wjzObserveFocusChanged
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.CodecInfoData
import dev.aaa1115910.bv.util.CodecMedia
import dev.aaa1115910.bv.util.CodecMode
import dev.aaa1115910.bv.util.CodecType
import dev.aaa1115910.bv.util.CodecUtil
import dev.aaa1115910.bv.util.BvKeyDirection
import dev.aaa1115910.bv.util.bvKeyDirection
import dev.aaa1115910.bv.util.swapList
import java.util.Locale

private val MediaCodecScopeId = WjzFocusScopeId("settings/media-codec")
private val MediaCodecEmptyLocalId = wjzFocusLocalId("empty")

private fun mediaCodecListLocalId(codecInfoData: CodecInfoData): WjzFocusLocalId {
    return wjzFocusLocalId("list", codecInfoData.name, codecInfoData.mimeType)
}

private object MediaCodecFocus {
    private val listRestorer = wjzFocusGroupRestorerComponent(
        componentId = "settings/media-codec/list",
        layer = WjzFocusLayer.Content,
        scopeId = MediaCodecScopeId
    )

    fun emptyTarget() = listTarget(MediaCodecEmptyLocalId)

    fun listItemTarget(codecInfoData: CodecInfoData) = listTarget(
        localId = mediaCodecListLocalId(codecInfoData)
    )

    @Composable
    fun Modifier.listRestorerHost(): Modifier {
        return with(listRestorer) { restorerHost() }
    }

    private fun listTarget(localId: WjzFocusLocalId) = listRestorer.target(
        nodeId = MediaCodecScopeId.resolve(localId)
    )
}

@Composable
fun MediaCodecScreen(
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        val list = CodecUtil.parseCodecs()
        println(list)
    }

    var currentCodecInfoData by remember { mutableStateOf<CodecInfoData?>(null) }
    var focusInNav by remember { mutableStateOf(false) }

    val decoderList = remember { mutableStateListOf<CodecInfoData>() }

    LaunchedEffect(Unit) {
        val list = CodecUtil.parseCodecs().filter { it.type == CodecType.Decoder }
        decoderList.swapList(list)
        currentCodecInfoData = list.firstOrNull()
    }

    WjzFocusHost(
        modifier = modifier,
        layer = WjzFocusLayer.Content,
        scopeId = MediaCodecScopeId
    ) {
        val focusCoordinator = LocalWjzFocusCoordinator.current

        LaunchedEffect(currentCodecInfoData, decoderList.size) {
            when {
                decoderList.isEmpty() -> {
                    focusCoordinator?.let(MediaCodecFocus.emptyTarget()::restoreFocus)
                }
                currentCodecInfoData != null -> {
                    val current = currentCodecInfoData ?: return@LaunchedEffect
                    focusCoordinator?.let(MediaCodecFocus.listItemTarget(current)::restoreFocus)
                }
                else -> {
                    val first = decoderList.firstOrNull() ?: return@LaunchedEffect
                    focusCoordinator?.let(MediaCodecFocus.listItemTarget(first)::restoreFocus)
                }
            }
        }

        Scaffold(
            topBar = {
                Box(
                    modifier = Modifier.padding(
                        start = 48.dp,
                        top = 24.dp,
                        bottom = 8.dp,
                        end = 48.dp
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.title_activity_media_codec),
                            fontSize = 24.sp
                        )
                        Text(
                            text = "",
                            color = C.onSurfaceVariant
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier.padding(innerPadding)
            ) {
                MediaCodecListItems(
                    modifier = with(MediaCodecFocus) {
                        Modifier
                            .wjzObserveFocusChanged { focusInNav = it }
                            .listRestorerHost()
                            .weight(3f)
                            .fillMaxHeight()
                    },
                    codecInfoDataList = decoderList,
                    currentCodecInfoData = currentCodecInfoData,
                    onCodecInfoDataChanged = { currentCodecInfoData = it },
                    isFocusing = focusInNav
                )
                MediaCodecDetails(
                    modifier = Modifier
                        .weight(5f)
                        .fillMaxSize(),
                    onBackNav = { focusInNav = true },
                    currentCodecInfoData = currentCodecInfoData
                )
            }
        }
    }
}

@Composable
fun MediaCodecListItems(
    modifier: Modifier = Modifier,
    codecInfoDataList: List<CodecInfoData>,
    currentCodecInfoData: CodecInfoData?,
    onCodecInfoDataChanged: (CodecInfoData) -> Unit,
    isFocusing: Boolean
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = codecInfoDataList) { codecInfoData ->
            MediaCodecListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .wjzFocusExits(
                        localId = mediaCodecListLocalId(codecInfoData),
                        layer = WjzFocusLayer.Content,
                        fallback = currentCodecInfoData == codecInfoData
                    ),
                codecInfoData = codecInfoData,
                onFocus = { onCodecInfoDataChanged(codecInfoData) },
                selected = currentCodecInfoData == codecInfoData
            )
        }
    }
}

@Composable
fun MediaCodecListItem(
    modifier: Modifier = Modifier,
    codecInfoData: CodecInfoData,
    onFocus: () -> Unit,
    onLoseFocus: () -> Unit = {},
    onClick: () -> Unit = {},
    selected: Boolean
) {
    ListItem(
        modifier = modifier
            .wjzObserveFocusChanged { if (it) onFocus() else onLoseFocus() },
        selected = selected,
        onClick = onClick,
        headlineContent = {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = codecInfoData.name,
                //style = MaterialTheme.typography.titleLarge
            )
        },
        overlineContent = {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp),
                    text = codecInfoData.mimeType,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = when (codecInfoData.media) {
                        CodecMedia.Audio -> Icons.Default.Audiotrack
                        CodecMedia.Video -> Icons.Default.Videocam
                    }, contentDescription = null
                )
            }
        }
    )
}

@Composable
fun MediaCodecDetails(
    modifier: Modifier = Modifier,
    onBackNav: () -> Unit,
    currentCodecInfoData: CodecInfoData?
) {
    val context = LocalContext.current

    if (currentCodecInfoData != null) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .onPreviewKeyEvent {
                    val result = it.bvKeyDirection() == BvKeyDirection.Left
                    if (result) onBackNav()
                    result
                },
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                horizontal = 48.dp,
                vertical = 24.dp
            )
        ) {
            item {
                MediaCodecDetailItem(
                    title = stringResource(R.string.codec_detail_hs_title),
                    text = when (currentCodecInfoData.mode) {
                        CodecMode.Hardware -> stringResource(R.string.codec_detail_hs_hardware)
                        CodecMode.Software -> stringResource(R.string.codec_detail_hs_software)
                    }
                )
            }
            item {
                MediaCodecDetailItem(
                    title = stringResource(R.string.codec_detail_max_supported_instances_title),
                    text = currentCodecInfoData.maxSupportedInstances.toString()
                )
            }
            if (currentCodecInfoData.media == CodecMedia.Video) {
                item {
                    MediaCodecDetailItem(
                        title = stringResource(R.string.codec_detail_color_formats_title),
                        text = currentCodecInfoData.colorFormats.joinToString()
                    )
                }
            }
            if (currentCodecInfoData.media == CodecMedia.Audio) {
                item {
                    MediaCodecDetailItem(
                        title = stringResource(R.string.codec_detail_audio_bitrate_range_title),
                        text = "${currentCodecInfoData.audioBitrateRange?.first?.toBps()} - ${currentCodecInfoData.audioBitrateRange?.last?.toBps()}"
                    )
                }
            }
            if (currentCodecInfoData.media == CodecMedia.Video) {
                item {
                    MediaCodecDetailItem(
                        title = stringResource(R.string.codec_detail_video_max_bitrate_title),
                        text = currentCodecInfoData.videoBitrateRange?.last?.toBps() ?: "Unknown"
                    )
                }
            }
            if (currentCodecInfoData.media == CodecMedia.Video) {
                item {
                    MediaCodecDetailItem(
                        title = stringResource(R.string.codec_detail_video_frame_range_title),
                        text = "${currentCodecInfoData.videoFrame?.first}fps - ${currentCodecInfoData.videoFrame?.last}fps"
                    )
                }
            }
            if (currentCodecInfoData.media == CodecMedia.Video) {
                item {
                    MediaCodecDetailItem(
                        title = stringResource(R.string.codec_detail_video_frame_supported_title),
                        text = currentCodecInfoData.supportedFrameRates.joinToString("\n") { supportedFrameRate ->
                            when (supportedFrameRate.resolution.second) {
                                360 -> context.getString(R.string.codec_detail_video_resolution_360p)
                                480 -> context.getString(R.string.codec_detail_video_resolution_480p)
                                720 -> context.getString(R.string.codec_detail_video_resolution_720p)
                                1080 -> context.getString(R.string.codec_detail_video_resolution_1080p)
                                1440 -> context.getString(R.string.codec_detail_video_resolution_1440p)
                                2160 -> context.getString(R.string.codec_detail_video_resolution_2160p)
                                4320 -> context.getString(R.string.codec_detail_video_resolution_4320p)
                                else -> context.getString(R.string.codec_detail_video_resolution_unknown)
                            } + ": " +
                                    ("${
                                        String.format(
                                            Locale.getDefault(),
                                            "%.1f",
                                            supportedFrameRate.frameRate.upper
                                        )
                                    }fps"
                                        .takeUnless { supportedFrameRate.unsupported }
                                        ?: context.getString(R.string.codec_detail_video_frame_unsupported))
                        }
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && currentCodecInfoData.media == CodecMedia.Video) {
                item {
                    MediaCodecDetailItem(
                        title = stringResource(R.string.codec_detail_video_frame_achievable_title),
                        text = currentCodecInfoData.achievableFrameRates.joinToString("\n") { achievableFrameRates ->
                            when (achievableFrameRates.resolution.second) {
                                360 -> context.getString(R.string.codec_detail_video_resolution_360p)
                                480 -> context.getString(R.string.codec_detail_video_resolution_480p)
                                720 -> context.getString(R.string.codec_detail_video_resolution_720p)
                                1080 -> context.getString(R.string.codec_detail_video_resolution_1080p)
                                1440 -> context.getString(R.string.codec_detail_video_resolution_1440p)
                                2160 -> context.getString(R.string.codec_detail_video_resolution_2160p)
                                4320 -> context.getString(R.string.codec_detail_video_resolution_4320p)
                                else -> context.getString(R.string.codec_detail_video_resolution_unknown)
                            } + ": " +
                                    ("${
                                        String.format(
                                            Locale.getDefault(),
                                            "%.1f",
                                            achievableFrameRates.frameRate.upper
                                        )
                                    }fps"
                                        .takeUnless { achievableFrameRates.unsupported }
                                        ?: context.getString(R.string.codec_detail_video_frame_unsupported))
                        }
                    )
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .wjzFocusExits(
                    localId = MediaCodecEmptyLocalId,
                    layer = WjzFocusLayer.Content,
                    fallback = true
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("Empty")
        }
    }
}

@Composable
fun MediaCodecDetailItem(
    modifier: Modifier = Modifier,
    title: String,
    text: String
) {
    var hasFocus by remember { mutableStateOf(false) }

    ListItem(
        modifier = modifier
            .wjzObserveFocusChanged { hasFocus = it },
        selected = hasFocus,
        onClick = {},
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = text) }
    )
}

private val previewCodecInfoData = CodecInfoData(
    name = "c2.android.avc.decoder",
    type = CodecType.Decoder,
    mode = CodecMode.Hardware,
    media = CodecMedia.Video,
    mimeType = "video/avc",
    maxSupportedInstances = 1,
    colorFormats = listOf(21, 19, 20),
    audioBitrateRange = 0..0,
    videoBitrateRange = 0..0,
    videoFrame = 0..0,
    supportedFrameRates = emptyList(),
    achievableFrameRates = emptyList()
)

@Preview(device = "id:tv_1080p")
@Composable
private fun MediaCodecListItemPreview() {
    BVTheme {
        MediaCodecListItem(
            codecInfoData = previewCodecInfoData,
            onFocus = {},
            selected = false
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun MediaCodecDetailsPreview() {
    BVTheme {
        MediaCodecDetails(
            currentCodecInfoData = previewCodecInfoData,
            onBackNav = {}
        )
    }
}

private fun Int.toBps(): String {
    return when {
        this >= 1000000 -> "${this / 1000000} Mbps"
        this >= 1000 -> "${this / 1000} Kbps"
        else -> "$this bps"
    }
}
