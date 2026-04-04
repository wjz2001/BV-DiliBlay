package dev.aaa1115910.bv.component.videocard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.component.TvGridBringIntoViewMode
import dev.aaa1115910.bv.component.TvLazyVerticalGrid
import dev.aaa1115910.bv.component.UpIcon
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.ImageSize
import dev.aaa1115910.bv.util.requestFocus
import dev.aaa1115910.bv.util.resizedImageUrl
import dev.aaa1115910.bv.viewmodel.SmallVideoCardGridUiState
import dev.aaa1115910.bv.viewmodel.SmallVideoCardItemUiState
import kotlin.Int

private val CoverStatIconSize = 24.dp
private val ActionButtonSize = 60.dp
private val ActionIconSize = 40.dp

private const val SmallVideoCardAnimationDurationMillis = 90
private val SmallVideoCardTransformOrigin = TransformOrigin(0.5f, 0.45f)

/**
 * 兼容旧调用的公开 API。
 *
 * 说明：
 * 1. delToView 当前实现已不再使用，仅为兼容旧调用保留。
 * 2. onGoToDetailPage 当前实现已不再使用，仅为兼容旧调用保留。
 * 3. onGoToUpPage 在非 Host 模式下作为 legacy fallback；
 *    在 Host 模式下，UP 跳转统一走 SmallVideoCardGridViewModel + SmallVideoCardGridHost。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SmallVideoCard(
    modifier: Modifier = Modifier,
    frameModifier: Modifier = Modifier,
    data: VideoCardData,
    titleMaxLines: Int = 3,
    delToView: Boolean = false,
    onClick: () -> Unit,
    onAddWatchLater: (() -> Unit)? = null,
    onGoToDetailPage: (() -> Unit)? = null,
    onGoToUpPage: (() -> Unit)? = null,
    interactive: Boolean = true,
    focusedScale: Float = 1.1f,
    coverDensityMultiplier: Float = 1.5f,
    coverFontScaleMultiplier: Float = 1.5f,
    infoDensityMultiplier: Float = 1.35f,
    infoFontScaleMultiplier: Float = 1.35f
) {
    SmallVideoCardCore(
        modifier = modifier,
        frameModifier = frameModifier,
        data = data,
        titleMaxLines = titleMaxLines,
        onClick = onClick,
        onAddWatchLater = onAddWatchLater,
        legacyOnGoToUpPage = onGoToUpPage,
        interactive = interactive,
        focusedScale = focusedScale,
        coverDensityMultiplier = coverDensityMultiplier,
        coverFontScaleMultiplier = coverFontScaleMultiplier,
        infoDensityMultiplier = infoDensityMultiplier,
        infoFontScaleMultiplier = infoFontScaleMultiplier
    )
}

@Composable
private fun SmallVideoCardCore(
    modifier: Modifier = Modifier,
    frameModifier: Modifier = Modifier,
    data: VideoCardData,
    titleMaxLines: Int,
    onClick: () -> Unit,
    onAddWatchLater: (() -> Unit)? = null,
    legacyOnGoToUpPage: (() -> Unit)? = null,
    interactive: Boolean = true,
    focusedScale: Float = 1.1f,
    coverDensityMultiplier: Float = 1.5f,
    coverFontScaleMultiplier: Float = 1.5f,
    infoDensityMultiplier: Float = 1.35f,
    infoFontScaleMultiplier: Float = 1.35f
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hostVm = LocalSmallVideoCardGridViewModel.current

    val hostUiState by hostVm?.uiState?.collectAsState()
        ?: remember { mutableStateOf(SmallVideoCardGridUiState()) }

    val itemUiState by hostVm?.cardUiFlow(data.avid)?.collectAsState()
        ?: remember(data.avid) { mutableStateOf(SmallVideoCardItemUiState()) }

    var showActions by remember(data.avid) { mutableStateOf(false) }
    var releaseLongPress by remember(data.avid) { mutableStateOf(false) }

    val historyButtonRequester = remember(data.avid) { FocusRequester() }

    val isHostMode = hostVm != null

    val canWatchLater = onAddWatchLater != null
    val canFavorite = hostUiState.capabilities.canFavorite
    val canHistory = hostUiState.capabilities.canHistory

    val isFavorite = itemUiState.isFavorite
    val hasMultipleCoAuthors = itemUiState.hasMultipleCoAuthors

    val canGoToUpPage = if (isHostMode) {
        data.upMid != null
    } else {
        data.upMid != null || legacyOnGoToUpPage != null
    }

    val allowDismissActionsOnFocusLoss =
        hostUiState.favoriteDialog.aid != data.avid &&
                hostUiState.coAuthorsDialog.ownerAid != data.avid

    fun navigateToUp(mid: Long, name: String) {
        UpInfoActivity.actionStart(context, mid = mid, name = name)
    }

    fun navigateToUpFallback() {
        if (legacyOnGoToUpPage != null) {
            legacyOnGoToUpPage()
            return
        }
        val mid = data.upMid ?: return
        navigateToUp(mid = mid, name = data.upName)
    }

    LaunchedEffect(showActions, canGoToUpPage, hostVm) {
        if (showActions) {
            historyButtonRequester.requestFocus(scope)
            hostVm?.onActionsShown(
                aid = data.avid,
                canGoToUpPage = canGoToUpPage
            )
        } else {
            releaseLongPress = false
            hostVm?.onActionsClosed(data.avid)
        }
    }

    LaunchedEffect(hostUiState.lastDismissedDialogAid, showActions) {
        val dismissedAid = hostUiState.lastDismissedDialogAid
        if (showActions && dismissedAid == data.avid) {
            historyButtonRequester.requestFocus(scope)
            hostVm?.consumeLastDismissedDialogAid(data.avid)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        BvSmallVideoCardFrame(
            modifier = frameModifier,
            interactive = interactive,
            showActions = showActions,
            allowDismissActionsOnFocusLoss = allowDismissActionsOnFocusLoss,
            focusedScale = focusedScale,
            onClick = onClick,
            onLongClick = { showActions = true },
            onDismissActions = { showActions = false }
        ) {
            if (showActions) {
                BvSmallVideoCardActions(
                    historyButtonRequester = historyButtonRequester,
                    canHistory = canHistory,
                    canFavorite = canFavorite,
                    canGoToUpPage = canGoToUpPage,
                    canWatchLater = canWatchLater,
                    isFavorite = isFavorite,
                    hasMultipleCoAuthors = hasMultipleCoAuthors,
                    onHistoryClick = {
                        if (!releaseLongPress) {
                            releaseLongPress = true
                            return@BvSmallVideoCardActions
                        }
                        if (!canHistory) return@BvSmallVideoCardActions
                        hostVm?.reportHistory(data.avid)
                    },
                    onFavoriteClick = {
                        if (!releaseLongPress) {
                            releaseLongPress = true
                            return@BvSmallVideoCardActions
                        }
                        if (!canFavorite) return@BvSmallVideoCardActions
                        hostVm?.openFavoriteDialog(data.avid)
                    },
                    onUpClick = {
                        if (!releaseLongPress) {
                            releaseLongPress = true
                            return@BvSmallVideoCardActions
                        }
                        if (!canGoToUpPage) return@BvSmallVideoCardActions

                        if (isHostMode) {
                            val fallbackMid = data.upMid
                            val fallbackName = data.upName

                            if (fallbackMid != null) {
                                hostVm.openCoAuthorsOrNavigate(
                                    aid = data.avid,
                                    fallbackMid = fallbackMid,
                                    fallbackName = fallbackName
                                )
                            }
                        } else {
                            navigateToUpFallback()
                        }
                    },
                    onWatchLaterClick = {
                        if (!releaseLongPress) {
                            releaseLongPress = true
                            return@BvSmallVideoCardActions
                        }
                        if (!canWatchLater) return@BvSmallVideoCardActions
                        onAddWatchLater()
                    }
                )
            } else {
                CardCover(
                    cover = data.cover,
                    play = data.playString,
                    danmaku = data.danmakuString,
                    time = data.timeString,
                    interactive = interactive,
                    coverDensityMultiplier = coverDensityMultiplier,
                    coverFontScaleMultiplier = coverFontScaleMultiplier
                )
            }
        }

        CardInfo(
            modifier = Modifier.fillMaxWidth(),
            title = data.title,
            titleMaxLines = titleMaxLines,
            upName = data.upName,
            pubTime = data.pubTime,
            hasMultipleCoAuthors = hasMultipleCoAuthors,
            infoDensityMultiplier = infoDensityMultiplier,
            infoFontScaleMultiplier = infoFontScaleMultiplier
        )
    }
}

@Composable
private fun BvSmallVideoCardFrame(
    modifier: Modifier = Modifier,
    interactive: Boolean,
    showActions: Boolean,
    allowDismissActionsOnFocusLoss: Boolean,
    focusedScale: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissActions: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    var cardIsFocused by remember { mutableStateOf(false) }
    var cardHasFocus by remember { mutableStateOf(false) }

    val targetScale = if (cardHasFocus) focusedScale else 1f
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = SmallVideoCardAnimationDurationMillis),
        label = "bv_small_video_card_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .zIndex(if (cardHasFocus) 1f else 0f)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                transformOrigin = SmallVideoCardTransformOrigin
            }
    ) {
        Card(
            onClick = { if (interactive && !showActions) onClick() },
            onLongClick = { if (interactive) onLongClick() },
            modifier = modifier
                .then(
                    if (!interactive) {
                        Modifier.focusProperties { canFocus = false }
                    } else {
                        Modifier
                    }
                )
                .fillMaxSize()
                .onFocusChanged { focusState ->
                    cardIsFocused = focusState.isFocused
                    cardHasFocus = focusState.hasFocus
                    if (!focusState.hasFocus && allowDismissActionsOnFocusLoss) {
                        onDismissActions()
                    }
                },
            shape = CardDefaults.shape(RectangleShape),
            scale = CardDefaults.scale(
                scale = 1f,
                focusedScale = 1f,
                pressedScale = 1f
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(3.dp, MaterialTheme.colorScheme.border),
                    shape = RectangleShape
                )
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                content()

                if (showActions && !cardIsFocused) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.border,
                                shape = RectangleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun BvSmallVideoCardActions(
    historyButtonRequester: FocusRequester,
    canHistory: Boolean,
    canFavorite: Boolean,
    canGoToUpPage: Boolean,
    canWatchLater: Boolean,
    isFavorite: Boolean,
    hasMultipleCoAuthors: Boolean,
    onHistoryClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onUpClick: () -> Unit,
    onWatchLaterClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    BvActionIconButton(
                        modifier = Modifier.focusRequester(historyButtonRequester),
                        canClick = canHistory,
                        onClick = onHistoryClick
                    ) { tint ->
                        Icon(
                            modifier = Modifier.size(ActionIconSize),
                            painter = painterResource(id = R.drawable.add_to_list),
                            contentDescription = "History",
                            tint = tint
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    BvActionIconButton(
                        canClick = canFavorite,
                        onClick = onFavoriteClick
                    ) { tint ->
                        Icon(
                            modifier = Modifier.size(ActionIconSize),
                            imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Favorite",
                            tint = tint
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    BvActionIconButton(
                        canClick = canGoToUpPage,
                        onClick = onUpClick
                    ) { tint ->
                        if (hasMultipleCoAuthors) {
                            Icon(
                                modifier = Modifier.size(ActionIconSize),
                                imageVector = Icons.Rounded.Group,
                                contentDescription = "CoAuthors",
                                tint = tint
                            )
                        } else {
                            Icon(
                                modifier = Modifier.size(ActionIconSize),
                                painter = painterResource(id = R.drawable.contact_page_24px),
                                contentDescription = "Up Page",
                                tint = tint
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    BvActionIconButton(
                        canClick = canWatchLater,
                        onClick = onWatchLaterClick
                    ) { tint ->
                        Icon(
                            modifier = Modifier.size(ActionIconSize),
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = "Watch later",
                            tint = tint
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BvActionIconButton(
    modifier: Modifier = Modifier,
    canClick: Boolean,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    IconButton(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .size(ActionButtonSize)
            .aspectRatio(1f),
        shape = ButtonDefaults.shape(shape = CircleShape),
        scale = IconButtonDefaults.scale(
            scale = 1f,
            focusedScale = 1f,
            pressedScale = 1f,
            disabledScale = 1f,
            focusedDisabledScale = 1f
        ),
        onClick = onClick
    ) {
        val tint = when {
            isFocused -> Color.Black
            canClick -> Color.White
            else -> Color.White.copy(alpha = 0.4f)
        }
        icon(tint)
    }
}

@Composable
fun CardCover(
    modifier: Modifier = Modifier,
    cover: String,
    play: String,
    danmaku: String,
    time: String,
    interactive: Boolean,
    coverDensityMultiplier: Float,
    coverFontScaleMultiplier: Float
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RectangleShape),
        contentAlignment = Alignment.BottomCenter
    ) {
        AsyncImage(
            modifier = Modifier
                .fillMaxSize()
                .clip(RectangleShape),
            model = cover.resizedImageUrl(ImageSize.SmallVideoCardCover),
            contentDescription = null,
            contentScale = ContentScale.Crop
        )

        CoverStatsBar(
            play = play,
            danmaku = danmaku,
            time = time,
            interactive = interactive,
            coverDensityMultiplier = coverDensityMultiplier,
            coverFontScaleMultiplier = coverFontScaleMultiplier
        )
    }
}

@Composable
private fun CoverStatsBar(
    play: String,
    danmaku: String,
    time: String,
    interactive: Boolean,
    coverDensityMultiplier: Float,
    coverFontScaleMultiplier: Float
) {
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = LocalDensity.current.density * coverDensityMultiplier,
            fontScale = LocalDensity.current.fontScale * coverFontScaleMultiplier
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (interactive) {
                        Modifier.background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.15f to Color.Transparent,
                                    0.16f to Color.Black.copy(alpha = 0.7f),
                                    1.0f to Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val style = MaterialTheme.typography.bodySmall
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current

            val iconWidthPx = with(density) { CoverStatIconSize.roundToPx() }
            val gap2Px = with(density) { 2.dp.roundToPx() }
            val gap8Px = with(density) { 8.dp.roundToPx() }

            val displayTexts = remember(
                play,
                danmaku,
                time,
                constraints.maxWidth,
                style,
                iconWidthPx,
                gap2Px,
                gap8Px
            ) {
                val timeWidthPx = textMeasurer.measure(
                    text = time,
                    style = style,
                    maxLines = 1,
                    softWrap = false
                ).size.width

                val leftMaxWidthPx = (constraints.maxWidth - timeWidthPx - gap8Px).coerceAtLeast(0)

                pickCompactPairThatFits(
                    playRaw = play,
                    danmakuRaw = danmaku,
                    leftMaxWidthPx = leftMaxWidthPx,
                    textMeasurer = textMeasurer,
                    style = style,
                    iconWidthPx = iconWidthPx,
                    gap2Px = gap2Px,
                    gap8Px = gap8Px
                )
            }

            val playShow = displayTexts.first
            val danmakuShow = displayTexts.second

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds()
                        .offset(y = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (playShow.isNotBlank()) {
                        Icon(
                            modifier = Modifier.size(CoverStatIconSize),
                            painter = painterResource(id = R.drawable.ic_play_count),
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = playShow,
                            style = style,
                            color = Color.White,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(8.dp))
                    }

                    if (danmakuShow.isNotBlank()) {
                        Icon(
                            modifier = Modifier.size(CoverStatIconSize),
                            painter = painterResource(id = R.drawable.ic_danmaku_count),
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = danmakuShow,
                            style = style,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    modifier = Modifier.offset(y = 3.dp),
                    text = time,
                    style = style,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun CardInfo(
    modifier: Modifier = Modifier,
    title: String,
    titleMaxLines: Int,
    upName: String,
    pubTime: String?,
    hasMultipleCoAuthors: Boolean = false,
    infoDensityMultiplier: Float,
    infoFontScaleMultiplier: Float
) {
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = LocalDensity.current.density * infoDensityMultiplier,
            fontScale = LocalDensity.current.fontScale * infoFontScaleMultiplier
        )
    ) {
        Column(
            modifier = modifier.padding(vertical = 6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                UpIcon(upgroup = hasMultipleCoAuthors)
                Text(
                    modifier = Modifier.weight(1f),
                    text = upName,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pubTime ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private enum class CompactLevel { Normal, DropDecimalWanYi, Thousand, Hundred }

private fun dropDecimalIfWanYi(src: String): String {
    val s = src.trim()
    return s.replace(Regex("""^(\d+)\.\d+(万亿)$"""), "$1$2")
}

private fun compactToThousandOrHundredIfPureNumber(src: String, level: CompactLevel): String {
    val s = src.trim()
    if (s.isBlank()) return s

    if (s.contains("万") || s.contains("亿")) {
        return if (level == CompactLevel.DropDecimalWanYi) dropDecimalIfWanYi(s) else s
    }

    val n = s.toLongOrNull() ?: return s

    return when (level) {
        CompactLevel.Normal,
        CompactLevel.DropDecimalWanYi -> s
        CompactLevel.Thousand -> if (n < 1000) s else "${n / 1000}千"
        CompactLevel.Hundred -> if (n < 100) s else "${n / 100}百"
    }
}

private fun measureLeftWidthPx(
    playText: String,
    danmakuText: String,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    iconWidthPx: Int,
    gap2Px: Int,
    gap8Px: Int
): Int {
    var w = 0
    val hasPlay = playText.isNotBlank()
    val hasDanmaku = danmakuText.isNotBlank()

    if (hasPlay) {
        val playW = textMeasurer.measure(
            text = playText,
            style = style,
            maxLines = 1,
            softWrap = false
        ).size.width
        w += iconWidthPx + gap2Px + playW + gap8Px
    }

    if (hasDanmaku) {
        val danW = textMeasurer.measure(
            text = danmakuText,
            style = style,
            maxLines = 1,
            softWrap = false
        ).size.width
        w += iconWidthPx + gap2Px + danW
    }

    return w
}

private fun pickCompactPairThatFits(
    playRaw: String,
    danmakuRaw: String,
    leftMaxWidthPx: Int,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    iconWidthPx: Int,
    gap2Px: Int,
    gap8Px: Int
): Pair<String, String> {
    val candidates = listOf(
        CompactLevel.Normal,
        CompactLevel.DropDecimalWanYi,
        CompactLevel.Thousand,
        CompactLevel.Hundred
    ).map { level ->
        val p = compactToThousandOrHundredIfPureNumber(playRaw, level)
        val d = compactToThousandOrHundredIfPureNumber(danmakuRaw, level)
        p to d
    } + listOf(
        compactToThousandOrHundredIfPureNumber(playRaw, CompactLevel.Hundred) to ""
    )

    return candidates.firstOrNull { (p, d) ->
        measureLeftWidthPx(
            playText = p,
            danmakuText = d,
            textMeasurer = textMeasurer,
            style = style,
            iconWidthPx = iconWidthPx,
            gap2Px = gap2Px,
            gap8Px = gap8Px
        ) <= leftMaxWidthPx
    } ?: (playRaw to danmakuRaw)
}

@Preview
@Composable
private fun SmallVideoCardPreview() {
    val data = VideoCardData(
        avid = 0,
        cid = 0,
        title = "震惊！太震惊了！真的是太震惊了！我的天呐！真TMD震惊！",
        cover = "http://i2.hdslb.com/bfs/archive/af17fc07b8f735e822563cc45b7b5607a491dfff.jpg",
        upName = "bishi",
        playString = "2333",
        danmakuString = "666",
        timeString = "23:33",
        pubTime = "1小时前"
    )
    BVTheme {
        Surface(
            modifier = Modifier.width(300.dp)
        ) {
            SmallVideoCard(
                modifier = Modifier.padding(20.dp),
                onClick = {},
                data = data,
                titleMaxLines = 3,
            )
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SmallVideoCardsPreview() {
    val data = VideoCardData(
        avid = 0,
        cid = 0,
        title = "震惊！太震惊了！真的是太震惊了！我的天呐！真TMD震惊！",
        cover = "",
        upName = "bishi",
        playString = "2333",
        danmakuString = "666",
        timeString = "23:33",
        pubTime = "1小时前"
    )
    BVTheme {
        TvLazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            mode = TvGridBringIntoViewMode.KeepVisible
        ) {
            repeat(20) {
                item(span = { GridItemSpan(1) }) {
                    SmallVideoCard(
                        onClick = {},
                        data = data,
                        titleMaxLines = 3
                    )
                }
            }
        }
    }
}