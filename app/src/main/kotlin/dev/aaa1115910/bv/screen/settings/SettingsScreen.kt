package dev.aaa1115910.bv.screen.settings

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.graphics.toColorInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import dev.aaa1115910.bv.component.BvTabOrderListContent
import dev.aaa1115910.bv.component.HomeTopNavItem
import dev.aaa1115910.bv.component.HomeTopNavRefreshSelectDialog
import dev.aaa1115910.bv.component.RadioMenuSelectDialog
import dev.aaa1115910.bv.component.RadioMenuSelectListContent
import dev.aaa1115910.bv.component.SettingsBottomIndicator
import dev.aaa1115910.bv.component.settings.SettingCycleListItem
import dev.aaa1115910.bv.component.settings.SettingListItem
import dev.aaa1115910.bv.component.settings.SettingSwitchListItem
import dev.aaa1115910.bv.component.settings.SettingsNavigationListItem
import dev.aaa1115910.bv.component.settings.actionEntry
import dev.aaa1115910.bv.entity.Audio
import dev.aaa1115910.bv.entity.Resolution
import dev.aaa1115910.bv.entity.VideoCodec
import dev.aaa1115910.bv.network.HttpServer
import dev.aaa1115910.bv.screen.settings.content.ActionAfterPlayItems
import dev.aaa1115910.bv.screen.settings.content.BlockSetting
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.ui.theme.ThemeMode
import dev.aaa1115910.bv.util.CodecMedia
import dev.aaa1115910.bv.util.CodecMode
import dev.aaa1115910.bv.util.CodecType
import dev.aaa1115910.bv.util.CodecUtil
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.requestFocus
import dev.aaa1115910.bv.viewmodel.settings.LogsViewModel
import dev.aaa1115910.bv.viewmodel.settings.SettingsStorageViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.DecimalFormat
import kotlin.math.pow
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class SettingsColumn {
    Category,
    Item,
    Detail
}

private enum class SettingsMenuNavItem(private val strRes: Int) {
    AudioVideo(R.string.settings_item_audio_video_settings),
    UI(R.string.settings_item_ui),
    Other(R.string.settings_item_other),
    Block(R.string.settings_item_block),
    Network(R.string.settings_item_network),
    Info(R.string.settings_item_info),
    About(R.string.settings_item_about);

    fun getDisplayName(context: Context) = context.getString(strRes)
}

private const val AppVersionEntryId = "app_version"

internal data class SettingsEntry(
    val id: String,
    val title: String,
    val supportText: String,
    val canFocusDetail: Boolean = true,
    val autoScrollableDetail: Boolean = true,
    val showSupportTextInItem: Boolean = true,
    val detailKeyEvent: ((KeyEvent) -> Boolean)? = null,
    val itemContent: @Composable (
        modifier: Modifier,
        colors: ListItemColors,
        contentColor: Color?,
        onFocus: () -> Unit
    ) -> Unit = { modifier, colors, contentColor, onFocus ->
        SettingsNavigationListItem(
            modifier = modifier,
            title = title,
            description = if (showSupportTextInItem) supportText else "",
            colors = colors,
            contentColor = contentColor,
            onFocus = onFocus
        )
    },
    val detailContent: @Composable (focused: Boolean) -> Unit
)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = LocalDensity.current.density * 1.5f,
            fontScale = LocalDensity.current.fontScale * 1.5f
        )
    ) {
        Scaffold(
            modifier = modifier,
            containerColor = Color.Transparent,
        ) { innerPadding ->
            SettingsMotionHost(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            )
        }
    }
}

@Composable
private fun SettingsMotionHost(
    modifier: Modifier = Modifier
) {
    var currentCategory by remember { mutableStateOf(SettingsMenuNavItem.AudioVideo) }
    var appVersionGalleryActive by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val motion = remember(scope) { SettingsMotionController(scope) }
    val displayCategory = currentCategory
    val currentItems = settingsEntries(displayCategory)
    var currentItemId by remember(displayCategory) {
        mutableStateOf(currentItems.firstOrNull()?.id.orEmpty())
    }
    val activity = LocalContext.current.findActivity()
    var contentActivated by remember { mutableStateOf(Prefs.settingsContentActivated) }
    val currentItem = if (contentActivated) {
        currentItems.firstOrNull { it.id == currentItemId }
            ?: currentItems.firstOrNull()
    } else {
        null
    }
    var lastDetailItemId by remember(displayCategory) {
        mutableStateOf(currentItems.firstOrNull()?.id.orEmpty())
    }
    val currentDetailItem = if (
        currentCategory == SettingsMenuNavItem.About &&
        currentItemId == AppVersionEntryId
    ) {
        currentItems.firstOrNull { it.id == lastDetailItemId }
            ?: currentItems.firstOrNull()
    } else {
        currentItem
    }
    var focusColumn by remember { mutableStateOf(SettingsColumn.Category) }
    var galleryImageRes by remember { mutableIntStateOf(R.drawable.versionbadge) }

    LaunchedEffect(currentCategory, currentItems.size) {
        if (currentItem == null || currentItems.none { it.id == currentItemId }) {
            currentItemId = currentItems.firstOrNull()?.id.orEmpty()
        }
    }

    LaunchedEffect(appVersionGalleryActive) {
        if (appVersionGalleryActive) {
            // 稍作延迟等待UI稳定后触发进场
            delay(100)
            motion.enterGallery()
        }
    }

    LaunchedEffect(motion.inGallery) {
        galleryImageRes = R.drawable.versionbadge
        if (motion.inGallery) {
            //  15s 切换
            delay(15000)
            galleryImageRes = R.drawable.sleepingcharacter
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (motion.locked) return@onPreviewKeyEvent true
                if (event.key == Key.Back && motion.inGallery) {
                    motion.exitGallery(activity)
                    appVersionGalleryActive = false
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }

        // 只在 About/过渡期间显示底图层，避免平时浪费绘制
        val showGalleryLayer =
            appVersionGalleryActive || motion.inGallery || motion.locked

        // ======= 底图层：始终“在底下”，不滑入，只负责承接“露出来” =======
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(C.background)
                .graphicsLayer {
                    alpha = if (showGalleryLayer) 1f else 0f
                }
        ) {
            // 你的图片切换逻辑保持不变
            Crossfade(
                targetState = galleryImageRes,
                animationSpec = tween(durationMillis = 650),
                label = "galleryImageCrossfade"
            ) { resId ->
                val painter = painterResource(resId)
                Box(Modifier.fillMaxSize()) {
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop
                    )

                    if (resId == R.drawable.versionbadge) {
                        val resources = LocalResources.current
                        val iwF = painter.intrinsicSize.width
                        val ihF = painter.intrinsicSize.height
                        val (iw, ih) = remember(iwF, ihF) {
                            if (iwF.isFinite() && ihF.isFinite() && iwF > 0f && ihF > 0f) {
                                iwF.toInt() to ihF.toInt()
                            } else {
                                readBitmapBoundsNoDecode(resources, R.drawable.versionbadge)
                            }
                        }
                        VersionBadgeOverlay(
                            modifier = Modifier.matchParentSize(),
                            srcImageWidthPx = iw,
                            srcImageHeightPx = ih,
                            fontResId = R.font.brushuplife,
                            textColor = "#C2AEA5".toColorInt(),
                            oversample = 2.0f
                        )
                        // 聚光灯遮罩
                        SpotlightRevealScrim(
                            menuPullProgress = motion.menuPull.value,
                            darkness = motion.bgScrim.value,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            }
        }

        // ======= 菜单主视图：三列先对齐堆叠，再整块被扯走露出底图 =======
        if (motion.showMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pullProgress = motion.menuPull.value
                        val tension = motion.menuTension.value

                        val tensionX = 12.dp.toPx() * tension
                        val tensionScaleX = 1f + 0.02f * tension

                        // ✅ 菜单整块被扯走（露出底下图片）
                        translationX = -widthPx * pullProgress + tensionX
                        scaleX = tensionScaleX
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
            ) {
                SettingsColumns(
                    modifier = Modifier.fillMaxSize(),
                    currentCategory = currentCategory,
                    currentItems = currentItems,
                    currentItem = currentItem,
                    currentDetailItem = currentDetailItem,
                    focusColumn = focusColumn,
                    contentActivated = contentActivated,
                    onContentActivated = {
                        contentActivated = true
                        Prefs.settingsContentActivated = true
                    },
                    onCategoryFocused = { category ->
                        currentCategory = category
                        appVersionGalleryActive = false
                        currentItemId = ""
                        focusColumn = SettingsColumn.Category
                    },
                    onItemFocused = { item ->
                        if (item.id != AppVersionEntryId) {
                            lastDetailItemId = item.id
                        }
                        currentItemId = item.id
                        appVersionGalleryActive =
                            currentCategory == SettingsMenuNavItem.About && item.id == AppVersionEntryId
                        focusColumn = SettingsColumn.Item
                    },
                    onDetailFocused = {
                        focusColumn = SettingsColumn.Detail
                    },
                    motion = motion,
                    contentColor = C.onBackground
                )
            }
        }
    }
}

@Composable
private fun SettingsColumns(
    modifier: Modifier = Modifier,
    currentCategory: SettingsMenuNavItem,
    currentItems: List<SettingsEntry>,
    currentItem: SettingsEntry?,
    currentDetailItem: SettingsEntry?,
    focusColumn: SettingsColumn,
    contentActivated: Boolean,
    onContentActivated: () -> Unit,
    onCategoryFocused: (SettingsMenuNavItem) -> Unit,
    onItemFocused: (SettingsEntry) -> Unit,
    onDetailFocused: () -> Unit,
    motion: SettingsMotionController,
    contentColor: Color
) {
    val categoryFocusRequester = remember { FocusRequester() }
    val itemFocusRequester = remember { FocusRequester() }
    val detailFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        categoryFocusRequester.requestFocus(scope)
    }

    SettingsMotionColumnsLayout(
        modifier = modifier,
        motion = motion,
        contentColor = contentColor,
        contentActivated = contentActivated,
        onContentActivated = onContentActivated,
        categoryColumn = { columnModifier ->
            SettingsCategoryColumn(
                modifier = columnModifier,
                focusRequester = categoryFocusRequester,
                selectedCategory = currentCategory,
                focused = focusColumn == SettingsColumn.Category,
                onCategoryFocused = onCategoryFocused,
                onRight = { itemFocusRequester.requestFocus(scope) }
            )
        },
        itemColumn = { columnModifier ->
            SettingsItemColumn(
                modifier = columnModifier,
                focusRequester = itemFocusRequester,
                items = currentItems,
                selectedItem = currentItem,
                focused = focusColumn == SettingsColumn.Item,
                activePathEnabled = focusColumn == SettingsColumn.Detail,
                onItemFocused = onItemFocused,
                onLeft = { categoryFocusRequester.requestFocus(scope) },
                onRight = {
                    if (currentItem?.canRequestDetailFocus() == true) {
                        onDetailFocused()
                        detailFocusRequester.requestFocus(scope)
                    }
                }
            )
        },
        detailColumn = { columnModifier ->
            if (currentDetailItem != null) {
                SettingsDetailColumn(
                    modifier = columnModifier,
                    focusRequester = detailFocusRequester,
                    item = currentDetailItem,
                    focused = focusColumn == SettingsColumn.Detail,
                    onFocused = onDetailFocused,
                    onLeft = { itemFocusRequester.requestFocus(scope) }
                )
            }
        }
    )
}

@Composable
private fun SettingsCategoryColumn(
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
                val activePath = selected && !focused
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
                    .settingsActivePathAnchor(
                        active = activePath,
                        anchorColor = C.primary
                    )

                SettingsNavigationListItem(
                    modifier = itemModifier,
                    title = category.getDisplayName(context),
                    colors = settingsTransparentListItemColors(),
                    contentColor = if (activePath) C.onSurfaceVariant else null,
                    onFocus = { onCategoryFocused(category) }
                )
            }
    }
}

@Composable
private fun SettingsItemColumn(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
    items: List<SettingsEntry>,
    selectedItem: SettingsEntry?,
    focused: Boolean,
    activePathEnabled: Boolean,
    onItemFocused: (SettingsEntry) -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    val activePathAnchorColor = C.secondary
    val activePathContentColor = C.onSurfaceVariant
    val itemColors = settingsTransparentListItemColors()
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
                        if (it.type == KeyEventType.KeyDown && selectedItem?.canRequestDetailFocus() == true) {
                            onRight()
                        }
                        selectedItem != null
                    }

                    else -> false
                }
            },
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { index, item ->
            val selected = item.id == selectedItem?.id
            val useMainFocusRequester = selected || (selectedItem == null && index == 0)
            val activePath = selected && activePathEnabled
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
                    if (useMainFocusRequester) {
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
                            if (it.type == KeyEventType.KeyDown && item.canRequestDetailFocus()) {
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
                .settingsActivePathAnchor(
                    active = activePath,
                    anchorColor = activePathAnchorColor
                )

            item(key = item.id) {
                item.itemContent(
                    itemModifier,
                    itemColors,
                    if (activePath) activePathContentColor else null
                ) { onItemFocused(item) }
            }
        }
    }
}

@Composable
private fun SettingsDetailColumn(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
    item: SettingsEntry,
    focused: Boolean,
    onFocused: () -> Unit,
    onLeft: () -> Unit
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val scrollStep = with(LocalDensity.current) { 72.dp.toPx() }
    val useAutoScroll = item.autoScrollableDetail && item.detailKeyEvent == null
    val hasAutoScrollOverflow by remember(useAutoScroll) {
        derivedStateOf { useAutoScroll && scrollState.maxValue > 0 }
    }
    val canFocusDetail = item.canFocusDetail || useAutoScroll

    Column(
        modifier = modifier
            .settingsDetailSaturation(focused)
            .padding(24.dp)
            .then(
                if (canFocusDetail) {
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
                            when {
                                item.detailKeyEvent?.invoke(it) == true -> true
                                useAutoScroll && hasAutoScrollOverflow && it.type == KeyEventType.KeyDown -> {
                                    when (it.key) {
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

                                else -> false
                            }
                        }
                } else {
                    Modifier
                }
            )
            .then(
                if (useAutoScroll) {
                    Modifier.verticalScroll(scrollState)
                } else {
                    Modifier
                }
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item.detailContent(focused)
    }
}

private fun SettingsEntry.canRequestDetailFocus() =
    canFocusDetail || (autoScrollableDetail && detailKeyEvent == null)

@Composable
internal fun settingsTransparentListItemColors() = ListItemDefaults.colors(
    containerColor = Color.Transparent,
    focusedContainerColor = Color.Transparent,
    pressedContainerColor = Color.Transparent,
    selectedContainerColor = Color.Transparent,
    focusedSelectedContainerColor = Color.Transparent,
    pressedSelectedContainerColor = Color.Transparent
)

private fun Modifier.settingsActivePathAnchor(
    active: Boolean,
    anchorColor: Color
): Modifier = composed {
    val progress = remember { Animatable(if (active) 1f else 0f) }
    val anchorSize = 12.dp
    val anchorStartPadding = 12.dp
    val anchorTextGap = 5.dp
    val anchorSpace = anchorStartPadding + anchorSize + anchorTextGap
    val reservedWidth = if (active) anchorSpace else 0.dp

    LaunchedEffect(active) {
        if (active) {
            progress.animateTo(1f, animationSpec = tween(180))
        } else {
            progress.animateTo(0f, animationSpec = tween(120))
        }
    }

    drawWithContent {
        val value = progress.value
        if (value <= 0f || !active) {
            drawContent()
            return@drawWithContent
        }

        val squareSize = anchorSize.toPx() * value
        val anchorStartPaddingPx = anchorStartPadding.toPx()

        drawRect(
            color = anchorColor,
            topLeft = Offset(
                x = anchorStartPaddingPx,
                y = size.height / 2f - squareSize / 2f
            ),
            size = Size(squareSize, squareSize)
        )

        drawContent()
    }.padding(start = reservedWidth)
}

private fun Modifier.settingsDetailSaturation(
    focused: Boolean
): Modifier = drawWithContent {
    if (focused) {
        drawContent()
        return@drawWithContent
    }

    val paint = Paint().apply {
        colorFilter = ColorFilter.colorMatrix(
            ColorMatrix().apply { setToSaturation(0f) }
        )
    }
    drawIntoCanvas { canvas ->
        canvas.saveLayer(size.toRect(), paint)
        this@drawWithContent.drawContent()
        canvas.restore()
    }
}



@Composable
private fun onePixel(): Dp = with(LocalDensity.current) { 1.toDp() }

@Composable
internal fun SettingsDivider(alpha: Float) {
    VerticalDivider(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 32.dp),
        thickness = onePixel(),
        color = C.onBackground.copy(alpha = alpha)
    )
}
@Composable
private fun settingsEntries(category: SettingsMenuNavItem): List<SettingsEntry> {
    return when (category) {
        SettingsMenuNavItem.AudioVideo -> audioVideoSettingsEntries()
        SettingsMenuNavItem.UI -> uiSettingsEntries()
        SettingsMenuNavItem.Other -> otherSettingsEntries()
        SettingsMenuNavItem.Network -> networkSettingsEntries()
        SettingsMenuNavItem.Info -> infoSettingsEntries()
        SettingsMenuNavItem.About -> aboutSettingsEntries()
        SettingsMenuNavItem.Block -> blockSettingsEntries()
    }
}

@Composable
private fun aboutSettingsEntries(): List<SettingsEntry> {
    return listOf(
        textEntry(
            id = "thanks",
            title = "鸣谢",
            supportText = "项目与贡献者",
            text = settingsThanksText()
        ),
        SettingsEntry(
            id = AppVersionEntryId,
            title = stringResource(R.string.settings_app_version),
            supportText = "",
            canFocusDetail = false,
            autoScrollableDetail = false,
            showSupportTextInItem = false,
            detailContent = {}
        )
    )
}
@Composable
private fun settingsThanksText(): String {
    return """
        鸣谢：
        哔哩哔哩电视版 1.66 及其后续开发者
        https://github.com/aaa1115910/bv
        https://github.com/Frost819/bv
        https://github.com/fantasytyx/bv
        https://github.com/bggRGjQaUbCoE/PiliPlus
        https://github.com/open-ani/animeko
        https://github.com/Nemo2011/bilibili-api
    """.trimIndent()
}

@Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@Composable
private fun audioVideoSettingsEntries(): List<SettingsEntry> {
    val context = LocalContext.current
    var selectedResolution by remember { mutableStateOf(Prefs.defaultQuality) }
    var selectedVideoCodec by remember { mutableStateOf(Prefs.defaultVideoCodec) }
    var selectedAudioCodec by remember { mutableStateOf(Prefs.defaultAudio) }
    var selectedActionAfterPlay by remember { mutableStateOf(Prefs.actionAfterPlay) }
    var enableSoftwareVideoRenderer by remember { mutableStateOf(Prefs.enableSoftwareVideoDecoder) }
    var enableFfmpegAudioRenderer by remember { mutableStateOf(Prefs.enableFfmpegAudioRenderer) }
    var inIncognitoMode by remember { mutableStateOf(Prefs.incognitoMode) }
    var showFps by remember { mutableStateOf(Prefs.showFps) }

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
        )
    )
}

@Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@Composable
private fun uiSettingsEntries(): List<SettingsEntry> {
    val context = LocalContext.current
    val themeModeOrdinal by Prefs.themeModeFlow.collectAsStateWithLifecycle(
        initialValue = Prefs.themeMode.ordinal
    )
    var selectedThemeMode by remember(themeModeOrdinal) {
        mutableStateOf(ThemeMode.fromOrdinal(themeModeOrdinal))
    }
    var selectedFirstHomeTopNavItem by remember { mutableStateOf(Prefs.firstHomeTopNavItem) }
    var selectedHomeTopNavItems by remember {
        mutableStateOf(Prefs.homeTopNavItems.ensureVisibleHomeTabs(Prefs.firstHomeTopNavItem))
    }
    var selectedHomeAutoRefreshTopNavItems by remember {
        mutableStateOf(Prefs.homeAutoRefreshTopNavItems)
    }
    var showHomeAutoRefreshTopNavDialog by remember { mutableStateOf(false) }
    var showVideoInfo by remember { mutableStateOf(Prefs.showVideoInfo) }
    var showVerticalVideoArgueTip by remember { mutableStateOf(Prefs.showVerticalVideoArgueTip) }
    var showPaidVideoArgueTip by remember { mutableStateOf(Prefs.showPaidVideoArgueTip) }
    var showVipVideoArgueTip by remember { mutableStateOf(Prefs.showVipVideoArgueTip) }
    var showPersistentSeek by remember { mutableStateOf(Prefs.showPersistentSeek) }
    var focusAlwaysCenter by remember { mutableStateOf(Prefs.focusAlwaysCenter) }

    HomeTopNavRefreshSelectDialog(
        show = showHomeAutoRefreshTopNavDialog,
        title = "主页页面切入自动刷新",
        initialSelectedItems = selectedHomeAutoRefreshTopNavItems,
        onHideDialog = { showHomeAutoRefreshTopNavDialog = false },
        onSubmit = {
            selectedHomeAutoRefreshTopNavItems = it
            Prefs.homeAutoRefreshTopNavItems = it
        }
    )

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
                selectedHomeTopNavItems = selectedHomeTopNavItems.ensureVisibleHomeTabs(it)
                Prefs.homeTopNavItems = selectedHomeTopNavItems
            },
            text = { it.getDisplayName(context) },
            itemKey = { it.name }
        ),
        customEntry(
            id = "home_top_nav_items",
            title = "主页面调整",
            supportText = selectedHomeTopNavItems.joinToString("、") {
                it.getDisplayName(context)
            }
        ) { focused ->
            BvTabOrderListContent(
                modifier = Modifier.fillMaxWidth(),
                allItems = HomeTopNavItem.entries.toList(),
                enabledOrderedIds = selectedHomeTopNavItems.map { it.code },
                itemId = { it.code },
                onSubmit = { ids ->
                    val nextItems = ids
                        .map { HomeTopNavItem.fromCode(it) }
                        .distinct()

                    selectedHomeTopNavItems = nextItems
                    if (selectedFirstHomeTopNavItem in nextItems) {
                        Prefs.homeTopNavItems = nextItems
                    }
                },
                text = { it.getDisplayName(context) },
                defaultFocusKey = selectedFirstHomeTopNavItem.code,
                requestDefaultFocus = focused
            )
        },
        actionEntry(
            id = "home_auto_refresh_top_nav_items",
            title = "主页页面切入自动刷新",
            supportText = if (selectedHomeAutoRefreshTopNavItems.isEmpty()) {
                "未启用"
            } else {
                selectedHomeAutoRefreshTopNavItems.joinToString("、") {
                    it.getDisplayName(context)
                }
            },
            actionText = "选择需要自动刷新的顶部导航项",
            onClick = { showHomeAutoRefreshTopNavDialog = true }
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
            id = "show_vertical_video_argue_tip",
            title = "显示竖屏警告",
            supportText = "在视频详情页顶部显示竖屏视频横幅提示",
            checked = showVerticalVideoArgueTip,
            onCheckedChange = {
                showVerticalVideoArgueTip = it
                Prefs.showVerticalVideoArgueTip = it
            }
        ),
        switchEntry(
            id = "show_paid_video_argue_tip",
            title = "显示付费警告",
            supportText = "在视频详情页顶部显示付费视频横幅提示",
            checked = showPaidVideoArgueTip,
            onCheckedChange = {
                showPaidVideoArgueTip = it
                Prefs.showPaidVideoArgueTip = it
            }
        ),
        switchEntry(
            id = "show_vip_video_argue_tip",
            title = "显示大会员视频提示",
            supportText = "在视频卡片上显示大会员视频角标",
            checked = showVipVideoArgueTip,
            onCheckedChange = {
                showVipVideoArgueTip = it
                Prefs.showVipVideoArgueTip = it
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
private fun otherSettingsEntries(): List<SettingsEntry> {
    val context = LocalContext.current
    val logsViewModel: LogsViewModel = koinViewModel()
    var host by remember { mutableStateOf("x.x.x.x") }
    var port by remember { mutableIntStateOf(0) }
    val logServerAddress = if (host.isNotBlank() && host != "x.x.x.x" && port != 0) {
        "http://$host:$port/"
    } else {
        "正在获取端口……"
    }
    val logServerSupportText = "请输入 $logServerAddress，或扫描右侧二维码进入日志管理界面"

    LaunchedEffect(Unit) {
        HttpServer.startServer()
        host = resolveWifiIpAddress()
        port = HttpServer.server?.engine?.resolvedConnectors()?.firstOrNull()?.port ?: 0
        logsViewModel.waitPortAndGenerateServerQr(host)
    }

    LaunchedEffect(logsViewModel.resolvedPort) {
        if (logsViewModel.resolvedPort != 0) {
            port = logsViewModel.resolvedPort
            if (host.isBlank() || host == "x.x.x.x") {
                host = resolveWifiIpAddress()
            }
        }
    }

    return listOf(
        customEntry(
            id = "storage_management",
            title = stringResource(R.string.settings_item_storage),
            supportText = "清理缓存与日志",
        ) {
            StorageManagementDetail(focused = it)
        },
        logEntry(
            id = "create_logs",
            title = stringResource(R.string.settings_create_logs_title),
            supportText = logServerSupportText,
            actionText = "打开日志",
            serverQrImage = logsViewModel.serverQrImage,
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
private fun deviceInfoEntry(): SettingsEntry {
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

    return SettingsEntry(
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
                SettingsDeviceInfoLine("制造商", Build.MANUFACTURER)
                SettingsDeviceInfoLine("型号", "${Build.MODEL} (${Build.PRODUCT})")
                SettingsDeviceInfoLine("系统版本", Build.VERSION.RELEASE)
                SettingsDeviceInfoLine(
                    "屏幕",
                    "${screenInfo.first}x${screenInfo.second} @ ${screenInfo.third}"
                )
                SettingsDeviceInfoLine("内存", "${memoryInfo.first}/${memoryInfo.second}")
                SettingsDeviceInfoLine("存储", "${storageInfo.first}/${storageInfo.second}")
                if (Build.VERSION.SDK_INT >= 31) {
                    SettingsDeviceInfoLine("SoC", "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
                }
                SettingsDeviceInfoLine("当前版本", BuildConfig.VERSION_NAME)
                if (codecInfos.isNotEmpty()) {
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        text = "解码器摘要",
                        color = LocalSettingsContentColor.current,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    codecStats.forEach { (label, value) ->
                        SettingsDeviceInfoLine(label, value)
                    }
                }
            }
        }
    )
}

@Composable
private fun SettingsDeviceInfoLine(
    title: String,
    text: String
) {
    Text(
        modifier = Modifier.padding(horizontal = 12.dp),
        text = "$title：$text",
        color = LocalSettingsContentColor.current,
        style = MaterialTheme.typography.bodyLarge
    )
}

@Suppress("UNUSED_VARIABLE", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@Composable
private fun networkSettingsEntries(
    channelRepository: ChannelRepository = koinInject()
): List<SettingsEntry> {
    val context = LocalContext.current
    var enableProxy by remember { mutableStateOf(Prefs.enableProxy) }
    var proxyHttpServer by remember { mutableStateOf(Prefs.proxyHttpServer) }
    var proxyGRPCServer by remember { mutableStateOf(Prefs.proxyGRPCServer) }
    var preferOfficialCdn by remember { mutableStateOf(Prefs.preferOfficialCdn) }
    var selectedApi by remember { mutableStateOf(Prefs.apiType) }
    var showProxyHttpServerEditDialog by remember { mutableStateOf(false) }
    var showProxyGRPCServerEditDialog by remember { mutableStateOf(false) }

    ProxyServerEditDialog(
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
    ProxyServerEditDialog(
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
private fun StorageManagementDetail(focused: Boolean) {
    val context = LocalContext.current
    val storageViewModel: SettingsStorageViewModel = koinViewModel()
    val scope = rememberCoroutineScope()
    val itemFocusRequester = remember { FocusRequester() }
    val confirmItems = remember { listOf("是", "否") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var clearFun: (() -> Unit)? by remember { mutableStateOf(null) }
    var content by remember { mutableStateOf("") }

    val titleImageCache = stringResource(R.string.settings_storage_image_cache)
    val titleOthersCache = stringResource(R.string.settings_storage_others_cache)
    val titleCrashLogs = stringResource(R.string.settings_storage_crash_logs)

    val showClearDialog: (String, () -> Unit) -> Unit = { title, clear ->
        clearFun = clear
        content = title
        showConfirmDialog = true
    }

    LaunchedEffect(Unit) {
        storageViewModel.refresh(context.cacheDir, context.filesDir)
    }

    LaunchedEffect(focused) {
        if (focused) {
            delay(50L)
            itemFocusRequester.requestFocus(scope)
        }
    }

    RadioMenuSelectDialog(
        visible = showConfirmDialog,
        onDismissRequest = { showConfirmDialog = false },
        title = "是否删除$content",
        items = confirmItems,
        selected = { it == "否" },
        onSelect = {
            if (it == "是") clearFun?.invoke()
            showConfirmDialog = false
        },
        text = { it },
        itemKey = { it },
        defaultFocusKey = "否"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingListItem(
            modifier = Modifier.focusRequester(itemFocusRequester),
            title = titleImageCache,
            supportText = cacheSizeText(storageViewModel.loading, storageViewModel.imageCacheSize),
            onClick = {
                showClearDialog(titleImageCache) {
                    storageViewModel.clearImageCaches(context.cacheDir, context.filesDir)
                }
            }
        )
        SettingListItem(
            title = titleOthersCache,
            supportText = cacheSizeText(storageViewModel.loading, storageViewModel.updateCacheSize),
            onClick = {
                showClearDialog(titleOthersCache) {
                    storageViewModel.clearOthersCaches(context.cacheDir, context.filesDir)
                }
            }
        )
        SettingListItem(
            title = titleCrashLogs,
            supportText = cacheSizeText(storageViewModel.loading, storageViewModel.crashLogsSize),
            onClick = {
                showClearDialog(titleCrashLogs) {
                    storageViewModel.clearCrashLogs(context.cacheDir, context.filesDir)
                }
            }
        )
    }
}

@Composable
private fun infoSettingsEntries(): List<SettingsEntry> {
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

@Composable
private fun blockSettingsEntries(): List<SettingsEntry> {
    return listOf(
        customEntry(
            id = "block_settings",
            title = "黑名单",
            supportText = "屏蔽分组、页面与更新",
        ) {
            BlockSetting(
                modifier = Modifier.fillMaxSize(),
                contentActive = it
            )
        }
    )
}

private fun logEntry(
    id: String,
    title: String,
    supportText: String,
    actionText: String,
    serverQrImage: ImageBitmap?,
    onClick: () -> Unit
) = SettingsEntry(
    id = id,
    title = title,
    supportText = supportText,
    autoScrollableDetail = false,
    detailContent = { focused ->
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            ListItem(
                modifier = SettingsBottomIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    animatedSelected = focused,
                    fixedSelected = false,
                    color = C.primary
                ),
                headlineContent = { Text(text = actionText) },
                supportingContent = { Text(text = supportText) },
                onClick = onClick,
                selected = focused,
                colors = settingsTransparentListItemColors()
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                val qrSize = if (maxWidth < maxHeight) maxWidth else maxHeight
                Box(
                    modifier = Modifier
                        .size(qrSize)
                        .clip(MaterialTheme.shapes.large)
                        .background(C.surface),
                    contentAlignment = Alignment.Center
                ) {
                    if (serverQrImage != null) {
                        Image(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            bitmap = serverQrImage,
                            contentDescription = null
                        )
                    } else {
                        Text(text = "正在获取端口……")
                    }
                }
            }
        }
    }
)

private fun <T> radioEntry(
    id: String,
    title: String,
    supportText: String,
    items: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    text: (T) -> String,
    itemKey: (T) -> Any
) = SettingsEntry(
    id = id,
    title = title,
    supportText = supportText,
    autoScrollableDetail = false,
    detailContent = { focused ->
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                text = title,
                color = LocalSettingsContentColor.current,
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
) = SettingsEntry(
    id = id,
    title = title,
    supportText = supportText,
    canFocusDetail = false,
    showSupportTextInItem = false,
    itemContent = { modifier, colors, contentColor, onFocus ->
        SettingSwitchListItem(
            modifier = modifier.onFocusChanged {
                if (it.hasFocus) onFocus()
            },
            title = title,
            supportText = "",
            checked = checked,
            colors = colors,
            contentColor = contentColor,
            onCheckedChange = onCheckedChange
        )
    },
    detailContent = {
        SettingsSupportTextDetail(supportText)
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
) = SettingsEntry(
    id = id,
    title = title,
    supportText = supportText,
    canFocusDetail = false,
    showSupportTextInItem = false,
    itemContent = { modifier, colors, contentColor, onFocus ->
        SettingCycleListItem(
            modifier = modifier.onFocusChanged {
                if (it.hasFocus) onFocus()
            },
            title = title,
            options = items,
            checked = selected,
            colors = colors,
            contentColor = contentColor,
            supportText = { "" },
            trailingText = trailingText,
            onCheckedChange = onSelected
        )
    },
    detailContent = {
        SettingsSupportTextDetail(supportText)
    }
)

private fun textEntry(
    id: String,
    title: String,
    supportText: String,
    text: String
) = SettingsEntry(
    id = id,
    title = title,
    supportText = supportText,
    canFocusDetail = false,
    detailContent = {
        SettingsTextDetail(text)
    }
)

@Suppress("SAME_PARAMETER_VALUE")
private fun customEntry(
    id: String,
    title: String,
    supportText: String,
    detailContent: @Composable (focused: Boolean) -> Unit
) = SettingsEntry(
    id = id,
    title = title,
    supportText = supportText,
    autoScrollableDetail = false,
    detailContent = detailContent
)

@Composable
private fun SettingsSupportTextDetail(
    supportText: String
) {
    if (supportText.isBlank()) return

    Column(
        modifier = Modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "说明：",
            color = LocalSettingsContentColor.current,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = supportText,
            color = LocalSettingsContentColor.current,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp)
        )
    }
}

@Composable
private fun SettingsTextDetail(
    text: String
) {
    Text(
        modifier = Modifier.padding(horizontal = 12.dp),
        text = text,
        color = LocalSettingsContentColor.current,
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun ProxyServerEditDialog(
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

private fun List<HomeTopNavItem>.ensureVisibleHomeTabs(
    firstTab: HomeTopNavItem
): List<HomeTopNavItem> {
    val validItems = HomeTopNavItem.entries.toSet()
    val orderedItems = filter { it in validItems }.distinct()
    val visibleItems = if (firstTab in orderedItems) {
        orderedItems
    } else {
        listOf(firstTab) + orderedItems
    }
    return visibleItems.ifEmpty { HomeTopNavItem.entries.toList() }
}

private fun resolveWifiIpAddress(): String {
    return runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (intf in interfaces) {
            if (intf.name.equals("wlan0", ignoreCase = true)) {
                val addresses = intf.inetAddresses
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return@runCatching addr.hostAddress ?: ""
                    }
                }
            }
        }
        ""
    }.getOrDefault("")
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
private fun SettingsScreenPreview() {
    BVTheme {
        SettingsScreen()
    }
}
