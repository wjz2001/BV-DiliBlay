package dev.aaa1115910.bv.screen.settings

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemColors
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.http.BiliHttpProxyApi
import dev.aaa1115910.biliapi.repositories.ChannelRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.settings.LogsActivity
import dev.aaa1115910.bv.activities.settings.SpeedTestActivity
import dev.aaa1115910.bv.component.HomeTopNavItem
import dev.aaa1115910.bv.component.RadioMenuSelectListContent
import dev.aaa1115910.bv.component.controllers.playermenu.PlaySpeedItem
import dev.aaa1115910.bv.component.settings.CookiesDialog
import dev.aaa1115910.bv.component.settings.SettingCycleListItem
import dev.aaa1115910.bv.component.settings.SettingSwitchListItem
import dev.aaa1115910.bv.entity.Audio
import dev.aaa1115910.bv.entity.Resolution
import dev.aaa1115910.bv.entity.VideoCodec
import dev.aaa1115910.bv.screen.settings.content.ActionAfterPlayItems
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.ui.theme.ThemeMode
import dev.aaa1115910.bv.util.CodecMedia
import dev.aaa1115910.bv.util.CodecMode
import dev.aaa1115910.bv.util.CodecType
import dev.aaa1115910.bv.util.CodecUtil
import dev.aaa1115910.bv.util.LogCatcherUtil
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.requestFocus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File
import java.text.DecimalFormat
import kotlin.math.pow

private enum class NewSettingsColumn {
    Category,
    Item,
    Detail
}

private data class NewSettingsEntry(
    val id: String,
    val title: String,
    val supportText: String,
    val canFocusDetail: Boolean = true,
    val showSupportTextInItem: Boolean = true,
    val detailKeyEvent: ((KeyEvent) -> Boolean)? = null,
    val itemContent: @Composable (
        modifier: Modifier,
        colors: ListItemColors,
        onFocus: () -> Unit
    ) -> Unit = { modifier, colors, onFocus ->
        NewSettingsListItem(
            modifier = modifier,
            title = title,
            description = if (showSupportTextInItem) supportText else "",
            colors = colors,
            onFocus = onFocus
        )
    },
    val detailContent: @Composable (focused: Boolean) -> Unit
)

@Composable
fun NewSettingsScreen(
    modifier: Modifier = Modifier
) {
    var currentCategory by remember { mutableStateOf(SettingsMenuNavItem.AudioVideo) }
    val currentItems = newSettingsEntries(currentCategory)
    var currentItemId by remember(currentCategory) {
        mutableStateOf(currentItems.firstOrNull()?.id.orEmpty())
    }
    var contentActivated by remember { mutableStateOf(Prefs.newSettingsContentActivated) }
    val currentItem = if (contentActivated) {
        currentItems.firstOrNull { it.id == currentItemId }
            ?: currentItems.firstOrNull()
    } else {
        null
    }
    var focusColumn by remember { mutableStateOf(NewSettingsColumn.Category) }

    LaunchedEffect(currentCategory, currentItems.size) {
        if (currentItem == null || currentItems.none { it.id == currentItemId }) {
            currentItemId = currentItems.firstOrNull()?.id.orEmpty()
        }
    }

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = LocalDensity.current.density * 1.5f,
            fontScale = LocalDensity.current.fontScale * 1.5f
        )
    ) {
        Scaffold(
            modifier = modifier,
            containerColor = C.background,
        ) { innerPadding ->
            NewSettingsColumns(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                currentCategory = currentCategory,
                currentItems = currentItems,
                currentItem = currentItem,
                focusColumn = focusColumn,
                contentActivated = contentActivated,
                onContentActivated = {
                    contentActivated = true
                    Prefs.newSettingsContentActivated = true
                },
                onCategoryFocused = { category ->
                    currentCategory = category
                    currentItemId = ""
                    focusColumn = NewSettingsColumn.Category
                },
                onItemFocused = { item ->
                    currentItemId = item.id
                    focusColumn = NewSettingsColumn.Item
                },
                onDetailFocused = {
                    focusColumn = NewSettingsColumn.Detail
                }
            )
        }
    }
}

@Composable
private fun NewSettingsColumns(
    modifier: Modifier = Modifier,
    currentCategory: SettingsMenuNavItem,
    currentItems: List<NewSettingsEntry>,
    currentItem: NewSettingsEntry?,
    focusColumn: NewSettingsColumn,
    contentActivated: Boolean,
    onContentActivated: () -> Unit,
    onCategoryFocused: (SettingsMenuNavItem) -> Unit,
    onItemFocused: (NewSettingsEntry) -> Unit,
    onDetailFocused: () -> Unit
) {
    val categoryFocusRequester = remember { FocusRequester() }
    val itemFocusRequester = remember { FocusRequester() }
    val detailFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        categoryFocusRequester.requestFocus(scope)
    }

    Row(
        modifier = modifier
            .background(C.background)
            .onPreviewKeyEvent {
                if (
                    !contentActivated
                    && it.type == KeyEventType.KeyDown
                    && it.key.isNewSettingsActivationKey()
                ) {
                    onContentActivated()
                }
                false
            }
    ) {
        NewSettingsCategoryColumn(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .fillMaxHeight(),
            focusRequester = categoryFocusRequester,
            selectedCategory = currentCategory,
            focused = focusColumn == NewSettingsColumn.Category,
            onCategoryFocused = onCategoryFocused,
            onRight = { itemFocusRequester.requestFocus(scope) }
        )

        NewSettingsDivider()

        NewSettingsItemColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            focusRequester = itemFocusRequester,
            items = currentItems,
            selectedItem = currentItem,
            focused = focusColumn == NewSettingsColumn.Item,
            onItemFocused = onItemFocused,
            onLeft = { categoryFocusRequester.requestFocus(scope) },
            onRight = {
                if (currentItem?.canFocusDetail == true) {
                    onDetailFocused()
                    detailFocusRequester.requestFocus(scope)
                }
            }
        )

        NewSettingsDivider()

        if (currentItem != null) {
            NewSettingsDetailColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(C.background),
                focusRequester = detailFocusRequester,
                item = currentItem,
                focused = focusColumn == NewSettingsColumn.Detail,
                onFocused = onDetailFocused,
                onLeft = { itemFocusRequester.requestFocus(scope) }
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(C.background)
            ) {}
        }
    }
}

@Composable
private fun NewSettingsCategoryColumn(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
    selectedCategory: SettingsMenuNavItem,
    focused: Boolean,
    onCategoryFocused: (SettingsMenuNavItem) -> Unit,
    onRight: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories = remember {
        SettingsMenuNavItem.entries.filterNot { it == SettingsMenuNavItem.Info }
    }
    val itemFocusRequesters = remember(categories) {
        categories.associateWith { FocusRequester() }
    }

    Column(
        modifier = modifier
            .padding(start = 32.dp, top = 32.dp, end = 24.dp, bottom = 32.dp)
            .onPreviewKeyEvent {
                when (it.key) {
                    Key.DirectionLeft -> true

                    Key.DirectionRight -> {
                        if (it.type == KeyEventType.KeyDown) onRight()
                        true
                    }

                    else -> false
                }
        },
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        categories
            .forEachIndexed { index, category ->
                val selected = category == selectedCategory
                val previousFocusRequester =
                    if (categories.size == 1) {
                        focusRequester
                    } else {
                        itemFocusRequesters[categories[(index - 1 + categories.size) % categories.size]]
                    }
                val nextFocusRequester =
                    if (categories.size == 1) {
                        focusRequester
                    } else {
                        itemFocusRequesters[categories[(index + 1) % categories.size]]
                    }
                val itemModifier = Modifier
                    .then(
                        if (selected) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier.focusRequester(itemFocusRequesters.getValue(category))
                        }
                    )
                    .onPreviewKeyEvent {
                        when (it.key) {
                            Key.DirectionLeft -> true

                            Key.DirectionRight -> {
                                if (it.type == KeyEventType.KeyDown) onRight()
                                true
                            }

                            Key.DirectionUp -> {
                                if (it.type == KeyEventType.KeyDown) {
                                    previousFocusRequester?.requestFocus(scope)
                                }
                                true
                            }

                            Key.DirectionDown -> {
                                if (it.type == KeyEventType.KeyDown) {
                                    nextFocusRequester?.requestFocus(scope)
                                }
                                true
                            }

                            else -> false
                        }
                    }
                    .newSettingsBottomIndicator(
                        animatedSelected = selected && focused,
                        fixedSelected = selected && !focused,
                        color = C.onBackground
                    )

                NewSettingsListItem(
                    modifier = itemModifier,
                    title = category.getDisplayName(context),
                    colors = newSettingsTransparentListItemColors(),
                    onFocus = { onCategoryFocused(category) }
                )
            }
    }
}

@Composable
private fun NewSettingsItemColumn(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
    items: List<NewSettingsEntry>,
    selectedItem: NewSettingsEntry?,
    focused: Boolean,
    onItemFocused: (NewSettingsEntry) -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    val indicatorColor = C.onBackground
    val itemColors = newSettingsTransparentListItemColors()
    val scope = rememberCoroutineScope()
    val itemIds = items.map { it.id }
    val itemFocusRequesters = remember(itemIds) {
        itemIds.associateWith { FocusRequester() }
    }

    LazyColumn(
        modifier = modifier
            .padding(24.dp)
            .onPreviewKeyEvent {
                when (it.key) {
                    Key.DirectionLeft -> {
                        if (it.type == KeyEventType.KeyDown) onLeft()
                        true
                    }

                    Key.DirectionRight -> {
                        if (it.type == KeyEventType.KeyDown && selectedItem?.canFocusDetail == true) {
                            onRight()
                        }
                        selectedItem != null
                    }

                    else -> false
                }
        },
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEachIndexed { index, item ->
            val selected = item.id == selectedItem?.id
            val previousFocusRequester =
                if (items.size == 1) {
                    focusRequester
                } else {
                    itemFocusRequesters[items[(index - 1 + items.size) % items.size].id]
                }
            val nextFocusRequester =
                if (items.size == 1) {
                    focusRequester
                } else {
                    itemFocusRequesters[items[(index + 1) % items.size].id]
                }
            val itemModifier = Modifier
                .fillMaxWidth()
                .then(
                    if (selected) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier.focusRequester(itemFocusRequesters.getValue(item.id))
                    }
                )
                .onPreviewKeyEvent {
                    when (it.key) {
                        Key.DirectionLeft -> {
                            if (it.type == KeyEventType.KeyDown) onLeft()
                            true
                        }

                        Key.DirectionRight -> {
                            if (it.type == KeyEventType.KeyDown && item.canFocusDetail) {
                                onRight()
                            }
                            true
                        }

                        Key.DirectionUp -> {
                            if (it.type == KeyEventType.KeyDown) {
                                previousFocusRequester?.requestFocus(scope)
                            }
                            true
                        }

                        Key.DirectionDown -> {
                            if (it.type == KeyEventType.KeyDown) {
                                nextFocusRequester?.requestFocus(scope)
                            }
                            true
                        }

                        else -> false
                    }
                }
                .newSettingsBottomIndicator(
                    animatedSelected = selected && focused,
                    fixedSelected = selected && !focused,
                    color = indicatorColor
                )

            item(key = item.id) {
                item.itemContent(
                    itemModifier,
                    itemColors
                ) { onItemFocused(item) }
            }
        }
    }
}

@Composable
private fun NewSettingsDetailColumn(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
    item: NewSettingsEntry,
    focused: Boolean,
    onFocused: () -> Unit,
    onLeft: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(24.dp)
            .then(
                if (item.canFocusDetail) {
                    Modifier
                        .focusProperties {
                            onExit = {
                                when (requestedFocusDirection) {
                                    FocusDirection.Left -> {
                                        onLeft()
                                        cancelFocusChange()
                                    }

                                    FocusDirection.Up,
                                    FocusDirection.Down,
                                    FocusDirection.Right -> cancelFocusChange()

                                    else -> Unit
                                }
                            }
                        }
                        .focusRequester(focusRequester)
                        .focusable()
                        .onFocusChanged {
                            if (it.hasFocus) onFocused()
                        }
                        .onPreviewKeyEvent {
                            item.detailKeyEvent?.invoke(it) == true
                        }
                } else {
                    Modifier
                }
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item.detailContent(focused)
    }
}

@Composable
private fun NewSettingsListItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String = "",
    colors: ListItemColors,
    onFocus: () -> Unit
) {
    ListItem(
        modifier = modifier
            .onFocusChanged {
                if (it.hasFocus) onFocus()
        },
        selected = false,
        onClick = {},
        colors = colors,
        headlineContent = {
            Text(
                text = title,
                color = C.onBackground
            )
        },
        supportingContent = if (description.isBlank()) {
            null
        } else {
            {
                Text(
                    text = description,
                    color = C.onBackground
                )
            }
        }
    )
}

@Composable
private fun newSettingsTransparentListItemColors() = ListItemDefaults.colors(
    containerColor = Color.Transparent,
    focusedContainerColor = Color.Transparent,
    pressedContainerColor = Color.Transparent,
    selectedContainerColor = Color.Transparent,
    focusedSelectedContainerColor = Color.Transparent,
    pressedSelectedContainerColor = Color.Transparent
)

private fun Modifier.newSettingsBottomIndicator(
    animatedSelected: Boolean,
    fixedSelected: Boolean,
    color: Color
): Modifier = composed {
    val progress = remember { Animatable(if (animatedSelected || fixedSelected) 1f else 0f) }

    LaunchedEffect(animatedSelected, fixedSelected) {
        when {
            fixedSelected -> progress.snapTo(1f)
            animatedSelected -> progress.animateTo(1f, animationSpec = tween(240))
            else -> progress.animateTo(0f, animationSpec = tween(160))
        }
    }

    drawWithContent {
        drawContent()
        if (progress.value <= 0f) return@drawWithContent

        val strokeHeight = 1.dp.toPx()
        val width = size.width * progress.value
        drawRect(
            color = color,
            topLeft = Offset(
                x = (size.width - width) / 2f,
                y = size.height - strokeHeight
            ),
            size = Size(width = width, height = strokeHeight)
        )
    }
}

private fun Key.isNewSettingsActivationKey(): Boolean {
    return when (this) {
        Key.DirectionUp,
        Key.DirectionDown,
        Key.DirectionLeft,
        Key.DirectionRight,
        Key.MediaRewind,
        Key.MediaFastForward -> true

        else -> false
    }
}

@Composable
private fun onePixel(): Dp = with(LocalDensity.current) { 1.toDp() }

@Composable
private fun NewSettingsDivider() {
    VerticalDivider(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 32.dp),
        thickness = onePixel(),
        color = C.onBackground
    )
}
@Composable
private fun newSettingsEntries(category: SettingsMenuNavItem): List<NewSettingsEntry> {
    return when (category) {
        SettingsMenuNavItem.AudioVideo -> audioVideoSettingsEntries()
        SettingsMenuNavItem.UI -> uiSettingsEntries()
        SettingsMenuNavItem.Other -> otherSettingsEntries()
        SettingsMenuNavItem.Network -> networkSettingsEntries()
        SettingsMenuNavItem.Storage -> storageSettingsEntries()
        SettingsMenuNavItem.Info -> infoSettingsEntries()
        SettingsMenuNavItem.About -> aboutSettingsEntries()
        SettingsMenuNavItem.Block -> blockSettingsEntries()
    }
}

@Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@Composable
private fun audioVideoSettingsEntries(): List<NewSettingsEntry> {
    val context = LocalContext.current
    var selectedResolution by remember { mutableStateOf(Prefs.defaultQuality) }
    var selectedVideoCodec by remember { mutableStateOf(Prefs.defaultVideoCodec) }
    var selectedAudioCodec by remember { mutableStateOf(Prefs.defaultAudio) }
    var selectedPlaySpeed by remember { mutableStateOf(Prefs.defaultPlaySpeed) }
    var selectedActionAfterPlay by remember { mutableStateOf(Prefs.actionAfterPlay) }
    var enableSoftwareVideoRenderer by remember { mutableStateOf(Prefs.enableSoftwareVideoDecoder) }
    var enableFfmpegAudioRenderer by remember { mutableStateOf(Prefs.enableFfmpegAudioRenderer) }

    return listOf(
        radioEntry(
            id = "default_quality",
            title = "默认分辨率",
            supportText = "当前：${selectedResolution.getDisplayName(context)}",
            items = Resolution.entries.toList(),
            selected = selectedResolution,
            onSelected = {
                selectedResolution = it
                Prefs.defaultQuality = it
            },
            text = { it.getDisplayName(context) },
            itemKey = { it.name }
        ),
        radioEntry(
            id = "default_video_codec",
            title = "默认视频编码",
            supportText = "当前：${selectedVideoCodec.getDisplayName(context)}",
            items = VideoCodec.entries.toList(),
            selected = selectedVideoCodec,
            onSelected = {
                selectedVideoCodec = it
                Prefs.defaultVideoCodec = it
            },
            text = { it.getDisplayName(context) },
            itemKey = { it.name }
        ),
        radioEntry(
            id = "default_audio",
            title = "默认音频编码",
            supportText = "当前：${selectedAudioCodec.getDisplayName(context)}",
            items = Audio.entries.toList(),
            selected = selectedAudioCodec,
            onSelected = {
                selectedAudioCodec = it
                Prefs.defaultAudio = it
            },
            text = { it.getDisplayName(context) },
            itemKey = { it.name }
        ),
        radioEntry(
            id = "default_play_speed",
            title = "默认播放速度",
            supportText = "当前：${selectedPlaySpeed.getDisplayName(context)}",
            items = PlaySpeedItem.entries.toList(),
            selected = selectedPlaySpeed,
            onSelected = {
                selectedPlaySpeed = it
                Prefs.defaultPlaySpeed = it
            },
            text = { it.getDisplayName(context) },
            itemKey = { it.name }
        ),
        radioEntry(
            id = "action_after_play",
            title = "播放结束动作",
            supportText = "当前：${selectedActionAfterPlay.getDisplayName(context)}",
            items = ActionAfterPlayItems.entries.toList(),
            selected = selectedActionAfterPlay,
            onSelected = {
                selectedActionAfterPlay = it
                Prefs.actionAfterPlay = it
            },
            text = { it.getDisplayName(context) },
            itemKey = { it.name }
        ),
        switchEntry(
            id = "software_video_renderer",
            title = stringResource(R.string.settings_media_software_video_renderer_title),
            supportText = stringResource(R.string.settings_media_software_video_renderer_text),
            checked = enableSoftwareVideoRenderer,
            onCheckedChange = {
                enableSoftwareVideoRenderer = it
                Prefs.enableSoftwareVideoDecoder = it
            }
        ),
        switchEntry(
            id = "ffmpeg_audio_renderer",
            title = stringResource(R.string.settings_media_ffmpeg_audio_renderer_title),
            supportText = stringResource(R.string.settings_media_ffmpeg_audio_renderer_text),
            checked = enableFfmpegAudioRenderer,
            onCheckedChange = {
                enableFfmpegAudioRenderer = it
                Prefs.enableFfmpegAudioRenderer = it
            }
        )
    )
}

@Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@Composable
private fun uiSettingsEntries(): List<NewSettingsEntry> {
    val context = LocalContext.current
    val themeModeOrdinal by Prefs.themeModeFlow.collectAsState(Prefs.themeMode.ordinal)
    var selectedThemeMode by remember(themeModeOrdinal) {
        mutableStateOf(ThemeMode.fromOrdinal(themeModeOrdinal))
    }
    var selectedFirstHomeTopNavItem by remember { mutableStateOf(Prefs.firstHomeTopNavItem) }
    var showVideoInfo by remember { mutableStateOf(Prefs.showVideoInfo) }
    var showPersistentSeek by remember { mutableStateOf(Prefs.showPersistentSeek) }
    var focusAlwaysCenter by remember { mutableStateOf(Prefs.focusAlwaysCenter) }

    return listOf(
        radioEntry(
            id = "first_home_top_nav_item",
            title = stringResource(R.string.settings_ui_homepage_title),
            supportText = selectedFirstHomeTopNavItem.getDisplayName(context),
            items = HomeTopNavItem.entries.toList(),
            selected = selectedFirstHomeTopNavItem,
            onSelected = {
                selectedFirstHomeTopNavItem = it
                Prefs.firstHomeTopNavItem = it
            },
            text = { it.getDisplayName(context) },
            itemKey = { it.name }
        ),
        switchEntry(
            id = "show_video_info",
            title = stringResource(R.string.settings_ui_show_video_info_title),
            supportText = stringResource(R.string.settings_ui_show_video_info_text),
            checked = showVideoInfo,
            onCheckedChange = {
                showVideoInfo = it
                Prefs.showVideoInfo = it
            }
        ),
        switchEntry(
            id = "show_persistent_seek",
            title = stringResource(R.string.settings_ui_show_persistent_seek_title),
            supportText = stringResource(R.string.settings_ui_show_persistent_seek_text),
            checked = showPersistentSeek,
            onCheckedChange = {
                showPersistentSeek = it
                Prefs.showPersistentSeek = it
            }
        ),
        cycleEntry(
            id = "focus_always_center",
            title = stringResource(R.string.settings_ui_focus_always_center_title),
            supportText = if (focusAlwaysCenter) {
                "选中的内容始终保持在屏幕中间"
            } else {
                "只有移动到边缘时才滚动（更流畅）"
            },
            items = listOf(false, true),
            selected = focusAlwaysCenter,
            onSelected = {
                focusAlwaysCenter = it
                Prefs.focusAlwaysCenter = it
            },
            trailingText = {
                if (it) "Pivot" else "KeepVisible"
            },
        ),
        cycleEntry(
            id = "theme_mode",
            title = stringResource(R.string.settings_ui_theme_title),
            supportText = stringResource(R.string.settings_ui_theme_text),
            items = ThemeMode.entries.toList(),
            selected = selectedThemeMode,
            onSelected = {
                selectedThemeMode = it
                Prefs.themeMode = it
            },
            trailingText = { it.getDisplayName(context) },
        )
    )
}

@Composable
private fun otherSettingsEntries(): List<NewSettingsEntry> {
    val context = LocalContext.current
    var showCookiesDialog by remember { mutableStateOf(false) }
    var showFps by remember { mutableStateOf(Prefs.showFps) }
    var inIncognitoMode by remember { mutableStateOf(Prefs.incognitoMode) }
    var selectedApi by remember { mutableStateOf(Prefs.apiType) }

    CookiesDialog(
        show = showCookiesDialog,
        onHideDialog = { showCookiesDialog = false }
    )

    return listOf(
        cycleEntry(
            id = "api_type",
            title = "接口选择",
            supportText = "",
            items = listOf(ApiType.App, ApiType.Web),
            selected = selectedApi,
            onSelected = {
                selectedApi = it
                Prefs.apiType = it
            },
            trailingText = { it.displayName() },
        ),
        actionEntry(
            id = "cookies",
            title = stringResource(R.string.settings_other_cookies_title),
            supportText = stringResource(R.string.settings_other_cookies_text),
            actionText = "打开 Cookies",
            onClick = { showCookiesDialog = true }
        ),
        switchEntry(
            id = "incognito_mode",
            title = stringResource(R.string.user_info_Incognito_mode_title),
            supportText = if (inIncognitoMode) {
                stringResource(R.string.user_info_Incognito_mode_on)
            } else {
                stringResource(R.string.user_info_Incognito_mode_off)
            },
            checked = inIncognitoMode,
            onCheckedChange = {
                inIncognitoMode = it
                Prefs.incognitoMode = it
            }
        ),
        switchEntry(
            id = "show_fps",
            title = stringResource(R.string.settings_other_fps_title),
            supportText = stringResource(R.string.settings_other_fps_text),
            checked = showFps,
            onCheckedChange = {
                showFps = it
                Prefs.showFps = it
            }
        ),
        actionEntry(
            id = "create_logs",
            title = stringResource(R.string.settings_create_logs_title),
            supportText = stringResource(R.string.settings_create_logs_text),
            actionText = "打开日志",
            onClick = {
                context.startActivity(Intent(context, LogsActivity::class.java))
            }
        )
    ) + if (BuildConfig.DEBUG) {
        listOf(
            actionEntry(
                id = "crash_test",
                title = stringResource(R.string.settings_crash_test_title),
                supportText = stringResource(R.string.settings_crash_test_text),
                actionText = "触发崩溃",
                onClick = { throw Exception("Boom!") }
            )
        )
    } else {
        emptyList()
    } + listOf(deviceInfoEntry())
}

@Composable
private fun deviceInfoEntry(): NewSettingsEntry {
    val context = LocalContext.current
    val memoryInfo = rememberDeviceMemoryInfo(context)
    val storageInfo = rememberDeviceStorageInfo()
    val screenInfo = rememberDeviceScreenInfo(context)
    val codecInfosResult = remember { runCatching { CodecUtil.parseCodecs() } }
    val codecInfos = codecInfosResult.getOrDefault(emptyList())
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val scrollStep = with(LocalDensity.current) { 72.dp.toPx() }
    var canFocusDetail by remember { mutableStateOf(false) }
    val codecStats = remember(codecInfos) {
        val totalCount = codecInfos.size
        val decoderCount = codecInfos.count { it.type == CodecType.Decoder }
        val encoderCount = codecInfos.count { it.type == CodecType.Encoder }
        val audioCount = codecInfos.count { it.media == CodecMedia.Audio }
        val videoCount = codecInfos.count { it.media == CodecMedia.Video }
        val hardwareCount = codecInfos.count { it.mode == CodecMode.Hardware }
        val softwareCount = codecInfos.count { it.mode == CodecMode.Software }

        listOf(
            "总数" to totalCount.toString(),
            "解码器" to decoderCount.toString(),
            "编码器" to encoderCount.toString(),
            "音频" to audioCount.toString(),
            "视频" to videoCount.toString(),
            "硬件" to hardwareCount.toString(),
            "软件" to softwareCount.toString()
        )
    }
    val hasOverflow by remember {
        derivedStateOf { scrollState.maxValue > 0 }
    }

    LaunchedEffect(hasOverflow) {
        canFocusDetail = hasOverflow
    }

    return NewSettingsEntry(
        id = "device_info",
        title = "设备信息",
        supportText = "设备与解码器摘要",
        canFocusDetail = canFocusDetail,
        detailKeyEvent = { event ->
            if (!canFocusDetail || event.type != KeyEventType.KeyDown) {
                false
            } else {
                when (event.key) {
                    Key.DirectionUp -> {
                        coroutineScope.launch { scrollState.animateScrollBy(-scrollStep) }
                        true
                    }

                    Key.DirectionDown -> {
                        coroutineScope.launch { scrollState.animateScrollBy(scrollStep) }
                        true
                    }

                    else -> false
                }
            }
        },
        detailContent = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NewSettingsDeviceInfoLine("制造商", Build.MANUFACTURER)
                NewSettingsDeviceInfoLine("型号", "${Build.MODEL} (${Build.PRODUCT})")
                NewSettingsDeviceInfoLine("系统版本", Build.VERSION.RELEASE)
                NewSettingsDeviceInfoLine(
                    "屏幕",
                    "${screenInfo.first}x${screenInfo.second} @ ${screenInfo.third}"
                )
                NewSettingsDeviceInfoLine("内存", "${memoryInfo.first}/${memoryInfo.second}")
                NewSettingsDeviceInfoLine("存储", "${storageInfo.first}/${storageInfo.second}")
                if (Build.VERSION.SDK_INT >= 31) {
                    NewSettingsDeviceInfoLine("SoC", "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
                }
                NewSettingsDeviceInfoLine("当前版本", BuildConfig.VERSION_NAME)
                if (codecInfos.isNotEmpty()) {
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        text = "解码器摘要",
                        color = C.onBackground,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    codecStats.forEach { (label, value) ->
                        NewSettingsDeviceInfoLine(label, value)
                    }
                }
            }
        }
    )
}

@Composable
private fun NewSettingsDeviceInfoLine(
    title: String,
    text: String
) {
    Text(
        modifier = Modifier.padding(horizontal = 12.dp),
        text = "$title：$text",
        color = C.onBackground,
        style = MaterialTheme.typography.bodyLarge
    )
}

@Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@Composable
private fun networkSettingsEntries(
    channelRepository: ChannelRepository = koinInject()
): List<NewSettingsEntry> {
    val context = LocalContext.current
    var enableProxy by remember { mutableStateOf(Prefs.enableProxy) }
    var proxyHttpServer by remember { mutableStateOf(Prefs.proxyHttpServer) }
    var proxyGRPCServer by remember { mutableStateOf(Prefs.proxyGRPCServer) }
    var preferOfficialCdn by remember { mutableStateOf(Prefs.preferOfficialCdn) }
    var showProxyHttpServerEditDialog by remember { mutableStateOf(false) }
    var showProxyGRPCServerEditDialog by remember { mutableStateOf(false) }

    NewProxyServerEditDialog(
        show = showProxyHttpServerEditDialog,
        onHideDialog = { showProxyHttpServerEditDialog = false },
        title = stringResource(R.string.settings_network_proxy_http_server_title),
        proxyServer = proxyHttpServer,
        onProxyServerChange = {
            proxyHttpServer = it
            Prefs.proxyHttpServer = it
            BiliHttpProxyApi.createClient(it)
        }
    )
    NewProxyServerEditDialog(
        show = showProxyGRPCServerEditDialog,
        onHideDialog = { showProxyGRPCServerEditDialog = false },
        title = stringResource(R.string.settings_network_proxy_grpc_server_title),
        proxyServer = proxyGRPCServer,
        onProxyServerChange = {
            proxyGRPCServer = it
            Prefs.proxyGRPCServer = it
            runCatching {
                channelRepository.initProxyChannel(
                    accessKey = Prefs.accessToken,
                    buvid = Prefs.buvid,
                    proxyServer = it
                )
            }
        }
    )

    return listOf(
        switchEntry(
            id = "enable_proxy",
            title = stringResource(R.string.settings_network_enable_proxy_title),
            supportText = stringResource(R.string.settings_network_enable_proxy_text),
            checked = enableProxy,
            onCheckedChange = {
                enableProxy = it
                Prefs.enableProxy = it
                if (it) BVApp.instance?.initProxy()
            }
        ),
        actionEntry(
            id = "proxy_http_server",
            title = stringResource(R.string.settings_network_proxy_http_server_title),
            supportText = proxyHttpServer.ifBlank {
                stringResource(R.string.settings_network_proxy_server_content_empty)
            },
            actionText = "编辑",
            onClick = { showProxyHttpServerEditDialog = true }
        ),
        actionEntry(
            id = "proxy_grpc_server",
            title = stringResource(R.string.settings_network_proxy_grpc_server_title),
            supportText = proxyGRPCServer.ifBlank {
                stringResource(R.string.settings_network_proxy_server_content_empty)
            },
            actionText = "编辑",
            onClick = { showProxyGRPCServerEditDialog = true }
        ),
        switchEntry(
            id = "prefer_official_cdn",
            title = stringResource(R.string.settings_network_prefer_official_cdn_title),
            supportText = stringResource(R.string.settings_network_prefer_official_cdn_text),
            checked = preferOfficialCdn,
            onCheckedChange = {
                preferOfficialCdn = it
                Prefs.preferOfficialCdn = it
            }
        ),
        actionEntry(
            id = "speed_test",
            title = stringResource(R.string.settings_network_test_title),
            supportText = stringResource(R.string.settings_network_test_text),
            actionText = "开始测速",
            onClick = {
                context.startActivity(Intent(context, SpeedTestActivity::class.java))
            }
        )
    )
}

@Composable
private fun storageSettingsEntries(): List<NewSettingsEntry> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logger = KotlinLogging.logger { }
    var loading by remember { mutableStateOf(false) }
    var imageCacheSize by remember { mutableLongStateOf(0L) }
    var updateCacheSize by remember { mutableLongStateOf(0L) }
    var crashLogsSize by remember { mutableLongStateOf(0L) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var clearFun: (() -> Unit)? by remember { mutableStateOf(null) }
    var content by remember { mutableStateOf("") }
    var size by remember { mutableLongStateOf(0L) }

    val titleImageCache = stringResource(R.string.settings_storage_image_cache)
    val titleOthersCache = stringResource(R.string.settings_storage_others_cache)
    val titleCrashLogs = stringResource(R.string.settings_storage_crash_logs)

    val calSize = {
        imageCacheSize = getFolderSize(File(context.cacheDir, "image_cache"))
        updateCacheSize = getFolderSize(File(context.cacheDir, "update_downloader"))
        crashLogsSize = getFolderSize(File(context.filesDir, LogCatcherUtil.LOG_DIR))
    }
    val showClearDialog: (String, Long, () -> Unit) -> Unit = { title, cacheSize, clear ->
        clearFun = clear
        content = title
        size = cacheSize
        showConfirmDialog = true
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            loading = true
            calSize()
            loading = false
        }
    }

    ConfirmDeleteDialog(
        show = showConfirmDialog,
        onHideDialog = { showConfirmDialog = false },
        content = content,
        size = size,
        clearFiles = {
            clearFun?.invoke()
            calSize()
        }
    )

    return listOf(
        actionEntry(
            id = "image_cache",
            title = titleImageCache,
            supportText = cacheSizeText(loading, imageCacheSize),
            actionText = "清除",
            onClick = {
                showClearDialog(titleImageCache, imageCacheSize) {
                    logger.fInfo { "clearImageCaches" }
                    File(context.cacheDir, "image_cache").deleteRecursively()
                }
            }
        ),
        actionEntry(
            id = "others_cache",
            title = titleOthersCache,
            supportText = cacheSizeText(loading, updateCacheSize),
            actionText = "清除",
            onClick = {
                showClearDialog(titleOthersCache, updateCacheSize) {
                    logger.fInfo { "clearOthersCaches" }
                    File(context.cacheDir, "update_downloader").deleteRecursively()
                }
            }
        ),
        actionEntry(
            id = "crash_logs",
            title = titleCrashLogs,
            supportText = cacheSizeText(loading, crashLogsSize),
            actionText = "清除",
            onClick = {
                showClearDialog(titleCrashLogs, crashLogsSize) {
                    logger.fInfo { "clearCrashLogs" }
                    File(context.filesDir, LogCatcherUtil.LOG_DIR).deleteRecursively()
                }
            }
        )
    )
}

@Composable
private fun infoSettingsEntries(): List<NewSettingsEntry> {
    val context = LocalContext.current
    val memoryInfo = rememberDeviceMemoryInfo(context)
    val storageInfo = rememberDeviceStorageInfo()
    val screenInfo = rememberDeviceScreenInfo(context)

    return listOf(
        textEntry(
            id = "manufacturer",
            title = "制造商",
            supportText = Build.MANUFACTURER,
            text = stringResource(R.string.settings_info_manufacturer, Build.MANUFACTURER)
        ),
        textEntry(
            id = "model",
            title = "型号",
            supportText = Build.MODEL,
            text = stringResource(R.string.settings_info_model, Build.MODEL, Build.PRODUCT)
        ),
        textEntry(
            id = "system",
            title = "系统版本",
            supportText = Build.VERSION.RELEASE,
            text = stringResource(R.string.settings_info_system, Build.VERSION.RELEASE)
        ),
        textEntry(
            id = "screen",
            title = "屏幕",
            supportText = "${screenInfo.first}x${screenInfo.second}",
            text = stringResource(
                R.string.settings_info_screen,
                screenInfo.first,
                screenInfo.second,
                screenInfo.third
            )
        ),
        textEntry(
            id = "memory",
            title = "内存",
            supportText = "${memoryInfo.first}/${memoryInfo.second}",
            text = stringResource(R.string.settings_info_memory, *memoryInfo.toList().toTypedArray())
        ),
        textEntry(
            id = "storage",
            title = "存储",
            supportText = "${storageInfo.first}/${storageInfo.second}",
            text = stringResource(R.string.settings_info_storage, *storageInfo.toList().toTypedArray())
        )
    ) + if (Build.VERSION.SDK_INT >= 31) {
        listOf(
            textEntry(
                id = "soc",
                title = "SoC",
                supportText = Build.SOC_MODEL,
                text = stringResource(R.string.settings_info_soc, Build.SOC_MANUFACTURER, Build.SOC_MODEL)
            )
        )
    } else {
        emptyList()
    }
}

@Suppress("UNUSED_VARIABLE")
@Composable
private fun aboutSettingsEntries(): List<NewSettingsEntry> {
    val currentVersionName = BuildConfig.VERSION_NAME
    return emptyList()
}

@Composable
private fun blockSettingsEntries(): List<NewSettingsEntry> {
    return listOf(
        customEntry(
            id = "block_settings",
            title = stringResource(R.string.settings_item_block),
            supportText = "屏蔽分组、页面与更新",
        ) {
            dev.aaa1115910.bv.screen.settings.content.BlockSetting(
                modifier = Modifier.fillMaxSize(),
                contentActive = it
            )
        }
    )
}

private fun <T> radioEntry(
    id: String,
    title: String,
    supportText: String,
    items: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    text: (T) -> String,
    itemKey: (T) -> Any
) = NewSettingsEntry(
    id = id,
    title = title,
    supportText = supportText,
    detailContent = { focused ->
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                text = title,
                color = C.onBackground,
                style = MaterialTheme.typography.headlineSmall
            )
            RadioMenuSelectListContent(
                modifier = Modifier.fillMaxWidth(),
                items = items,
                selected = { it == selected },
                onClick = onSelected,
                text = text,
                itemKey = itemKey,
                defaultFocusKey = itemKey(selected),
                requestDefaultFocus = focused,
            )
        }
    }
)

private fun switchEntry(
    id: String,
    title: String,
    supportText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) = NewSettingsEntry(
    id = id,
    title = title,
    supportText = supportText,
    canFocusDetail = false,
    showSupportTextInItem = false,
    itemContent = { modifier, colors, onFocus ->
        SettingSwitchListItem(
            modifier = modifier.onFocusChanged {
                if (it.hasFocus) onFocus()
            },
            title = title,
            supportText = "",
            checked = checked,
            colors = colors,
            onCheckedChange = onCheckedChange
        )
    },
    detailContent = {
        NewSettingsSupportTextDetail(supportText)
    }
)

private fun <T> cycleEntry(
    id: String,
    title: String,
    supportText: String,
    items: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    trailingText: (T) -> String
) = NewSettingsEntry(
    id = id,
    title = title,
    supportText = supportText,
    canFocusDetail = false,
    showSupportTextInItem = false,
    itemContent = { modifier, colors, onFocus ->
        SettingCycleListItem(
            modifier = modifier.onFocusChanged {
                if (it.hasFocus) onFocus()
            },
            title = title,
            options = items,
            checked = selected,
            colors = colors,
            supportText = { "" },
            trailingText = trailingText,
            onCheckedChange = onSelected
        )
    },
    detailContent = {
        NewSettingsSupportTextDetail(supportText)
    }
)

private fun actionEntry(
    id: String,
    title: String,
    supportText: String,
    actionText: String,
    onClick: () -> Unit
) = NewSettingsEntry(
    id = id,
    title = title,
    supportText = supportText,
    detailContent = {
        NewSettingsActionDetail(
            title = actionText,
            supportText = supportText,
            focused = it,
            onClick = onClick
        )
    }
)

private fun textEntry(
    id: String,
    title: String,
    supportText: String,
    text: String
) = NewSettingsEntry(
    id = id,
    title = title,
    supportText = supportText,
    canFocusDetail = false,
    detailContent = {
        NewSettingsTextDetail(text)
    }
)

@Suppress("SAME_PARAMETER_VALUE")
private fun customEntry(
    id: String,
    title: String,
    supportText: String,
    detailContent: @Composable (focused: Boolean) -> Unit
) = NewSettingsEntry(
    id = id,
    title = title,
    supportText = supportText,
    detailContent = detailContent
)

@Composable
private fun NewSettingsActionDetail(
    title: String,
    supportText: String,
    focused: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = supportText) },
        onClick = onClick,
        selected = focused,
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = C.onSurface,

            selectedContainerColor = C.primaryContainer,
            selectedContentColor = C.onPrimary,

            pressedContainerColor = C.secondaryContainer,
            pressedContentColor = C.onBackground,

            disabledContainerColor = Color.Transparent,
            disabledContentColor = C.disabled,

            pressedSelectedContainerColor = C.primaryContainer,
            pressedSelectedContentColor = C.onPrimary
        )
    )
}

@Composable
private fun NewSettingsSupportTextDetail(
    supportText: String
) {
    if (supportText.isBlank()) return

    Column(
        modifier = Modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "说明：",
            color = C.onBackground,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = supportText,
            color = C.onBackground,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp)
        )
    }
}

@Composable
private fun NewSettingsTextDetail(
    text: String
) {
    Text(
        modifier = Modifier.padding(horizontal = 12.dp),
        text = text,
        color = C.onBackground,
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun NewProxyServerEditDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onHideDialog: () -> Unit,
    title: String,
    proxyServer: String,
    onProxyServerChange: (String) -> Unit
) {
    var proxyServerString by remember(show) { mutableStateOf(proxyServer) }

    if (show) {
        AlertDialog(
            modifier = modifier,
            title = { Text(text = title) },
            text = {
                OutlinedTextField(
                    value = proxyServerString,
                    onValueChange = { proxyServerString = it },
                    singleLine = true,
                    maxLines = 1,
                    shape = MaterialTheme.shapes.medium,
                    placeholder = {
                        Text(text = stringResource(R.string.proxy_server_edit_dialog_input_field_label))
                    }
                )
            },
            onDismissRequest = onHideDialog,
            confirmButton = {
                Button(onClick = {
                    onProxyServerChange(
                        proxyServerString
                            .replace("\n", "")
                            .replace("https://", "")
                            .replace("http://", "")
                    )
                    onHideDialog()
                }) {
                    Text(text = stringResource(id = R.string.common_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onHideDialog) {
                    Text(text = stringResource(id = R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun ConfirmDeleteDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onHideDialog: () -> Unit,
    content: String,
    size: Long,
    clearFiles: () -> Unit
) {
    if (show) {
        AlertDialog(
            modifier = modifier,
            onDismissRequest = onHideDialog,
            title = { Text(text = "清除$content") },
            text = { Text(text = "${size / 1024 / 1024} MB") },
            confirmButton = {
                Button(onClick = {
                    clearFiles()
                    onHideDialog()
                }) {
                    Text(text = "确定")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onHideDialog) {
                    Text(text = "取消")
                }
            }
        )
    }
}

private fun getFolderSize(f: File): Long {
    if (!f.exists()) return 0L
    if (!f.isDirectory) return f.length()
    return f.listFiles()?.sumOf { getFolderSize(it) } ?: 0L
}

@Composable
private fun cacheSizeText(loading: Boolean, size: Long): String {
    return if (loading) {
        stringResource(R.string.settings_storage_calculating)
    } else {
        "${size / 1024 / 1024} MB"
    }
}

private fun ApiType.displayName(): String {
    return when (this) {
        ApiType.App -> "App 接口"
        ApiType.Web -> "Web 接口"
    }
}

@Composable
private fun rememberDeviceMemoryInfo(context: Context): Pair<String, String> {
    return remember {
        runCatching {
            val memoryInfo = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .getMemoryInfo(memoryInfo)
            val df = DecimalFormat("###.##")
            Pair(
                "${df.format(memoryInfo.availMem / 1024.0.pow(3))} GB",
                "${df.format(memoryInfo.totalMem / 1024.0.pow(3))} GB"
            )
        }.getOrDefault(Pair("Unknown", "Unknown"))
    }
}

@Composable
private fun rememberDeviceStorageInfo(): Pair<String, String> {
    return remember {
        runCatching {
            val statFs = StatFs(Environment.getExternalStorageDirectory().absolutePath)
            val df = DecimalFormat("###.##")
            Pair(
                "${df.format(statFs.availableBytes / 1024.0.pow(3))} GB",
                "${df.format(statFs.totalBytes / 1024.0.pow(3))} GB"
            )
        }.getOrDefault(Pair("Unknown", "Unknown"))
    }
}

@Suppress("DEPRECATION")
@Composable
private fun rememberDeviceScreenInfo(context: Context): Triple<Int, Int, Float> {
    return remember {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            (context as Activity).windowManager.defaultDisplay
        }

        val mode = display.mode
        Triple(mode.physicalWidth, mode.physicalHeight, mode.refreshRate)
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun NewSettingsScreenPreview() {
    BVTheme {
        NewSettingsScreen()
    }
}
