package dev.aaa1115910.bv.component.videocard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CurrencyYen
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.compose.ui.unit.Dp
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
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.component.TvGridBringIntoViewMode
import dev.aaa1115910.bv.component.TvLazyVerticalGrid
import dev.aaa1115910.bv.component.UpIcon
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.repository.StartupCoverRepository
import dev.aaa1115910.bv.ui.theme.AppWhite
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.ThemeMode
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.ImageSize
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.requestFocus
import dev.aaa1115910.bv.util.resizedImageUrl
import dev.aaa1115910.bv.util.rememberTvImageRequest
import dev.aaa1115910.bv.util.toWanString
import dev.aaa1115910.bv.viewmodel.SmallVideoCardItemUiState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.Int
import kotlin.math.min

private val CoverStatIconSize = 24.dp
private val ActionButtonSize = 60.dp
private val ActionIconSize = 40.dp
private val smallVideoCardLogger = KotlinLogging.logger {}

private const val SmallVideoCardAnimationDurationMillis = 180

private val smallVideoCardIsPrivateFlavor: Boolean
    get() = BuildConfig.IS_PRIVATE

/**
 * 取景器四角边框：只画四个角的“L”形线段（8 条线）
 */
private fun Modifier.viewfinderCorners(
    color: Color,
    strokeWidthDp: Dp = 3.dp,
    // 角线段长度（会按控件尺寸自适应并 clamp）
    cornerRatio: Float = 0.18f,
    minCornerDp: Dp = 14.dp,
    maxCornerDp: Dp = 28.dp
): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            // 动画时间
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        )
    }
    this.drawWithCache {
        val sw = strokeWidthDp.toPx()
        onDrawWithContent {
            drawContent()

            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@onDrawWithContent
            val minSide = min(w, h)
            // 计算出角线段的最终目标长度
            val targetCornerLen = (minSide * cornerRatio)
                .coerceIn(minCornerDp.toPx(), maxCornerDp.toPx())
                .coerceAtMost(minSide / 2f)

            // 2. 将目标长度乘以动画进度 progress.value，实现从点到线的生长动画
            val cornerLen = targetCornerLen * progress.value

            val half = sw / 2f

            fun line(start: Offset, end: Offset) {
                drawLine(
                    color = color,
                    start = start,
                    end = end,
                    strokeWidth = sw,
                    cap = StrokeCap.Square
                )
            }

            // Top-Left
            line(Offset(half, half), Offset(half + cornerLen, half))
            line(Offset(half, half), Offset(half, half + cornerLen))

            // Top-Right
            line(Offset(w - half, half), Offset(w - half - cornerLen, half))
            line(Offset(w - half, half), Offset(w - half, half + cornerLen))

            // Bottom-Left
            line(Offset(half, h - half), Offset(half + cornerLen, h - half))
            line(Offset(half, h - half), Offset(half, h - half - cornerLen))

            // Bottom-Right
            line(Offset(w - half, h - half), Offset(w - half - cornerLen, h - half))
            line(Offset(w - half, h - half), Offset(w - half, h - half - cornerLen))
        }
    }
}

/**
 * 说明：
 * onGoToUpPage 在非 Host 模式下作为 legacy fallback；
 * 在 Host 模式下，UP 跳转统一走 SmallVideoCardGridViewModel + SmallVideoCardGridHost。
 */
@Composable
fun SmallVideoCard(
    modifier: Modifier = Modifier,
    frameModifier: Modifier = Modifier,
    data: VideoCardData,
    titleMaxLines: Int = 3,
    onClick: () -> Unit,
    onAddWatchLater: (() -> Unit)? = null,
    onGoToUpPage: (() -> Unit)? = null,
    pendingRemoval: Boolean = false,
    onPendingRemovalFocusLost: (() -> Unit)? = null,
    uiState: SmallVideoCardItemUiState? = null,
    interactive: Boolean = true,
    classroomDirectUpNavigation: Boolean = false,
    upButtonOnly: Boolean = false,
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
        pendingRemoval = pendingRemoval,
        onPendingRemovalFocusLost = onPendingRemovalFocusLost,
        uiState = uiState,
        interactive = interactive,
        classroomDirectUpNavigation = classroomDirectUpNavigation,
        upButtonOnly = upButtonOnly,
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
    pendingRemoval: Boolean = false,
    onPendingRemovalFocusLost: (() -> Unit)? = null,
    uiState: SmallVideoCardItemUiState? = null,
    interactive: Boolean = true,
    classroomDirectUpNavigation: Boolean = false,
    upButtonOnly: Boolean = false,
    coverDensityMultiplier: Float = 1.5f,
    coverFontScaleMultiplier: Float = 1.5f,
    infoDensityMultiplier: Float = 1.35f,
    infoFontScaleMultiplier: Float = 1.35f,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hostVm = LocalSmallVideoCardGridViewModel.current
    val hostUiState = LocalSmallVideoCardGridUiState.current
    val itemUiState = uiState ?: SmallVideoCardItemUiState()

    var showActions by remember(data.avid) { mutableStateOf(false) }
    var releaseLongPress by remember(data.avid) { mutableStateOf(false) }
    var restoreFocusToCardAfterActionsClose by remember(data.avid) { mutableStateOf(false) }

    val cardFocusRequester = remember(data.avid) { FocusRequester() }

    val historyButtonRequester = remember(data.avid) { FocusRequester() }
    val favoriteButtonRequester = remember(data.avid) { FocusRequester() }
    val upButtonRequester = remember(data.avid) { FocusRequester() }
    val watchLaterButtonRequester = remember(data.avid) { FocusRequester() }

    val isHostMode = hostVm != null

    LaunchedEffect(data.avid, data.cid, hostVm) {
        hostVm?.ensureMetricsLoaded(data)
    }

    val canWatchLater = onAddWatchLater != null
    val canFavorite = hostUiState.capabilities.canFavorite
    val canHistory = hostUiState.capabilities.canHistory
    val isPrivateFlavor = smallVideoCardIsPrivateFlavor
    val canOpenVideoInfo = !isPrivateFlavor
    val canUseHistory = canHistory && !upButtonOnly
    val canUseFavorite = canFavorite && !upButtonOnly
    val canUseWatchLater = canWatchLater && !upButtonOnly

    val isFavorite = itemUiState.isFavorite
    val hasMultipleCoAuthors = itemUiState.hasMultipleCoAuthors
    val metricsSnapshot = itemUiState.metrics?.snapshot
    val playString = metricsSnapshot?.view.toWanString().ifEmpty { data.playString }
    val danmakuString = metricsSnapshot?.danmaku.toWanString().ifEmpty { data.danmakuString }
    val showVipBadge = Prefs.showVipVideoArgueTip && metricsSnapshot?.isVipVideo == true
    val showPaidBadge =
        Prefs.showPaidVideoArgueTip && !showVipBadge && metricsSnapshot?.isPaidVideo == true
    val showVerticalBadge =
        Prefs.showVerticalVideoArgueTip && metricsSnapshot?.isVerticalVideo == true

    LaunchedEffect(
        data.avid,
        metricsSnapshot?.isVipVideo,
        metricsSnapshot?.isPaidVideo,
        metricsSnapshot?.isVerticalVideo,
        itemUiState.metrics?.runtime?.degraded,
        itemUiState.metrics?.runtime?.failureCode
    ) {
        smallVideoCardLogger.info {
            "SmallVideoCard badge metrics: aid=${data.avid}, " +
                    "vip=${metricsSnapshot?.isVipVideo}, " +
                    "paid=${metricsSnapshot?.isPaidVideo}, " +
                    "vertical=${metricsSnapshot?.isVerticalVideo}, " +
                    "showVip=$showVipBadge, showPaid=$showPaidBadge, showVertical=$showVerticalBadge, " +
                    "source=${itemUiState.metrics?.runtime?.sourceId}, " +
                    "degraded=${itemUiState.metrics?.runtime?.degraded}, " +
                    "failureCode=${itemUiState.metrics?.runtime?.failureCode}"
        }
    }

    val canGoToUpPage = if (isHostMode) {
        data.upMid != null
    } else {
        data.upMid != null || legacyOnGoToUpPage != null
    }

    val allowDismissActionsOnFocusLoss =
        !pendingRemoval &&
                hostUiState.favoriteDialog.aid != data.avid &&
                hostUiState.coAuthorsDialog.ownerAid != data.avid

    val canFinalizePendingRemovalOnFocusLoss =
        hostUiState.favoriteDialog.aid != data.avid &&
                hostUiState.coAuthorsDialog.ownerAid != data.avid

    val actionLayerVisible = showActions || pendingRemoval

    fun navigateToUp(mid: Long, name: String) {
        UpInfoActivity.actionStart(context, mid = mid, name = name)
    }

    fun navigateToVideoInfo() {
        VideoInfoActivity.actionStart(context, data.avid)
    }

    fun navigateToUpFallback() {
        if (legacyOnGoToUpPage != null) {
            legacyOnGoToUpPage()
            return
        }
        val mid = data.upMid ?: return
        navigateToUp(mid = mid, name = data.upName)
    }

    fun requestDefaultActionFocus() {
        val target = when {
            canUseHistory -> historyButtonRequester
            canUseFavorite -> favoriteButtonRequester
            canGoToUpPage -> upButtonRequester
            canUseWatchLater -> watchLaterButtonRequester
            else -> null
        }
        target?.requestFocus(scope)
    }

    LaunchedEffect(
        actionLayerVisible,
        canHistory,
        canFavorite,
        canGoToUpPage,
        canWatchLater,
        hostVm
    ) {
        if (actionLayerVisible) {
            requestDefaultActionFocus()

            hostVm?.onActionsShown(
                aid = data.avid,
                canGoToUpPage = canGoToUpPage && !classroomDirectUpNavigation
            )
        } else {
            releaseLongPress = false
            hostVm?.onActionsClosed(data.avid)
        }
    }

    LaunchedEffect(hostUiState.lastDismissedDialogAid, actionLayerVisible) {
        val dismissedAid = hostUiState.lastDismissedDialogAid
        if (actionLayerVisible && dismissedAid == data.avid) {
            requestDefaultActionFocus()
            hostVm?.consumeLastDismissedDialogAid(data.avid)
        }
    }

    LaunchedEffect(showActions, pendingRemoval, restoreFocusToCardAfterActionsClose) {
        if (!pendingRemoval && !showActions && restoreFocusToCardAfterActionsClose) {
            cardFocusRequester.requestFocus(scope)
            restoreFocusToCardAfterActionsClose = false
        }
    }

    val onClickWithStartupCover = remember(data.avid, data.cover, onClick) {
        {
            StartupCoverRepository.put(data.avid, data.cover)
            onClick()
        }
    }

    // 把 hasFocus 提升到 Core，让“分离动画”和“底部文字动画”共享同一进度
    var cardHasFocus by remember(data.avid) { mutableStateOf(false) }

    // focused 且不显示 actions 层 -> 1；长按 actions 层出现时 -> 0（倒放）
    val separationTarget = if (cardHasFocus && !actionLayerVisible) 1f else 0f
    val separationProgress by animateFloatAsState(
        targetValue = separationTarget,
        animationSpec = tween(
            durationMillis = SmallVideoCardAnimationDurationMillis,
            easing = FastOutSlowInEasing
        ),
        label = "bv_small_video_card_separation"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        BvSmallVideoCardFrame(
            modifier = frameModifier,
            interactive = interactive,
            showActions = actionLayerVisible,
            allowDismissActionsOnFocusLoss = allowDismissActionsOnFocusLoss,
            cardFocusRequester = cardFocusRequester,
            separationProgress = separationProgress,
            onCardHasFocusChanged = { cardHasFocus = it },
            onClick = onClickWithStartupCover,
            onLongClick = {
                if (!pendingRemoval) {
                    showActions = true
                }
            },
            onDismissActions = {
                if (!pendingRemoval) {
                    showActions = false
                }
            },
            onPendingRemovalFocusLost = onPendingRemovalFocusLost,
            pendingRemoval = pendingRemoval,
            canFinalizePendingRemovalOnFocusLoss = canFinalizePendingRemovalOnFocusLoss
        ) {
            if (actionLayerVisible) {
                BvSmallVideoCardActions(
                    historyButtonRequester = historyButtonRequester,
                    favoriteButtonRequester = favoriteButtonRequester,
                    upButtonRequester = upButtonRequester,
                    watchLaterButtonRequester = watchLaterButtonRequester,
                    isPrivateFlavor = isPrivateFlavor,
                    canHistory = canUseHistory,
                    canFavorite = canUseFavorite,
                    canGoToUpPage = canGoToUpPage,
                    canWatchLater = canUseWatchLater && !pendingRemoval,
                    canOpenVideoInfo = canOpenVideoInfo && !upButtonOnly,
                    isFavorite = isFavorite,
                    hasMultipleCoAuthors = hasMultipleCoAuthors,
                    onBack = {
                        if (!pendingRemoval) {
                            // 关闭浮层后，恢复焦点到卡片（动画会按 focused 正常正放）
                            restoreFocusToCardAfterActionsClose = true
                            showActions = false
                        }
                    },
                    onHistoryClick = {
                        if (!releaseLongPress) {
                            releaseLongPress = true
                            return@BvSmallVideoCardActions
                        }
                        if (!canUseHistory) return@BvSmallVideoCardActions
                        hostVm?.reportHistory(data.avid)
                    },
                    onInfoClick = {
                        if (!releaseLongPress) {
                            releaseLongPress = true
                            return@BvSmallVideoCardActions
                        }
                        navigateToVideoInfo()
                    },
                    onFavoriteClick = {
                        if (!releaseLongPress) {
                            releaseLongPress = true
                            return@BvSmallVideoCardActions
                        }
                        if (!canUseFavorite) return@BvSmallVideoCardActions
                        hostVm?.openFavoriteDialog(data.avid)
                    },
                    onUpClick = {
                        if (!releaseLongPress) {
                            releaseLongPress = true
                            return@BvSmallVideoCardActions
                        }
                        if (!canGoToUpPage) return@BvSmallVideoCardActions

                        if (isHostMode && !classroomDirectUpNavigation) {
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

                        val add = onAddWatchLater ?: return@BvSmallVideoCardActions
                        add()
                    }
                )
            } else {
                CardCover(
                    cover = data.cover,
                    play = playString,
                    danmaku = danmakuString,
                    time = data.timeString,
                    interactive = interactive,
                    coverDensityMultiplier = coverDensityMultiplier,
                    coverFontScaleMultiplier = coverFontScaleMultiplier,
                    isFocused = cardHasFocus,
                    showPaidBadge = showPaidBadge,
                    showVipBadge = showVipBadge,
                    showVerticalBadge = showVerticalBadge
                )
            }
        }

        if (!pendingRemoval) {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
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
    }
}

@Composable
private fun BvSmallVideoCardFrame(
    modifier: Modifier = Modifier,
    interactive: Boolean,
    showActions: Boolean,
    allowDismissActionsOnFocusLoss: Boolean,
    cardFocusRequester: FocusRequester,
    separationProgress: Float,
    onCardHasFocusChanged: (Boolean) -> Unit,
    pendingRemoval: Boolean,
    canFinalizePendingRemovalOnFocusLoss: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissActions: () -> Unit,
    onPendingRemovalFocusLost: (() -> Unit)?,
    content: @Composable BoxScope.() -> Unit
) {
    var cardHasFocusLocal by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 按压动画 (平常1f，按下0.9f)
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        label = "press_scale"
    )

    // 分离倒放系数 (平常1f，按下0f，使分离状态在按下时平滑倒放)
    val pressSeparationFraction by animateFloatAsState(
        targetValue = if (isPressed) 0f else 1f,
        label = "press_separation_fraction"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .zIndex(if (cardHasFocusLocal) 1f else 0f)
    ) {
        val density = LocalDensity.current

        // 固定的安全分离位移
        val separationDp = 8.dp
        val offsetPxTarget = with(density) { separationDp.toPx() }
        val selectedBorder = C.primary

        // 背后垫片层
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val ambientPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb((0.039f * 255).toInt(), 0, 0, 0)
                    }
                    val spotPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb((0.19f * 255).toInt(), 0, 0, 0)
                    }

                    onDrawBehind {
                        val p = (separationProgress * pressSeparationFraction).coerceIn(0f, 1f)
                        if (p <= 0.0001f) return@onDrawBehind

                        val offset = offsetPxTarget * p
                        val c = selectedBorder.copy(alpha = p)

                        // 画垫片（底座本身）
                        drawRect(
                            color = c,
                            topLeft = Offset(offset, offset),
                            size = androidx.compose.ui.geometry.Size(
                                width = size.width,
                                height = size.height
                            )
                        )

                        // 计算 MD2 阴影参数，仅在与垫片重合处绘制
                        val elevationPx = 8.dp.toPx() * p
                        if (elevationPx > 0.5f) {

                            val ambientBlur = elevationPx.coerceAtLeast(1f)
                            ambientPaint.maskFilter =
                                android.graphics.BlurMaskFilter(ambientBlur, android.graphics.BlurMaskFilter.Blur.NORMAL)

                            val spotBlur = (elevationPx * 0.75f).coerceAtLeast(1f)
                            val spotOffsetY = elevationPx * 0.5f
                            spotPaint.maskFilter =
                                android.graphics.BlurMaskFilter(spotBlur, android.graphics.BlurMaskFilter.Blur.NORMAL)

                            clipRect(
                                left = offset,
                                top = offset,
                                right = size.width + offset,
                                bottom = size.height + offset
                            ) {
                                drawContext.canvas.nativeCanvas.apply {
                                    // 绘制环境阴影
                                    drawRect(
                                        -offset,
                                        -offset,
                                        size.width - offset,
                                        size.height - offset,
                                        ambientPaint
                                    )

                                    // 绘制直射阴影
                                    drawRect(
                                        -offset,
                                        -offset + spotOffsetY,
                                        size.width - offset,
                                        size.height - offset + spotOffsetY,
                                        spotPaint
                                    )
                                }
                            }
                        }
                    }
                }
        )

        Card(
            onClick = { if (interactive && !showActions) onClick() },
            onLongClick = { if (interactive) onLongClick() },
            interactionSource = interactionSource,
            modifier = modifier
                .then(
                    if (!interactive) Modifier.focusProperties { canFocus = false }
                    else Modifier
                )
                .focusRequester(cardFocusRequester)
                .fillMaxSize()
                .graphicsLayer {
                    val p = (separationProgress * pressSeparationFraction).coerceIn(0f, 1f)
                    val offset = offsetPxTarget * p

                    translationX = -offset
                    translationY = -offset

                    scaleX = pressScale
                    scaleY = pressScale

                    shape = RectangleShape
                    clip = false
                }
                .onFocusChanged { focusState ->
                    val hasFocus = focusState.hasFocus
                    cardHasFocusLocal = hasFocus
                    onCardHasFocusChanged(hasFocus)

                    if (pendingRemoval && canFinalizePendingRemovalOnFocusLoss && !focusState.hasFocus) {
                        onPendingRemovalFocusLost?.invoke()
                    }
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
            border = CardDefaults.border(Border.None, Border.None)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                content()
            }
        }
    }
}

@Composable
private fun BvSmallVideoCardActions(
    historyButtonRequester: FocusRequester,
    favoriteButtonRequester: FocusRequester,
    upButtonRequester: FocusRequester,
    watchLaterButtonRequester: FocusRequester,
    isPrivateFlavor: Boolean,
    canHistory: Boolean,
    canFavorite: Boolean,
    canGoToUpPage: Boolean,
    canWatchLater: Boolean,
    canOpenVideoInfo: Boolean,
    isFavorite: Boolean,
    hasMultipleCoAuthors: Boolean,
    onBack: () -> Unit,
    onHistoryClick: () -> Unit,
    onInfoClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onUpClick: () -> Unit,
    onWatchLaterClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .viewfinderCorners(
                color = C.selectedBorder,
                strokeWidthDp = 3.dp
            )
            .onPreviewKeyEvent {
                if (it.key == Key.Back) {
                    if (it.type == KeyEventType.KeyUp) onBack()
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 长按浮层背景色
                .background(C.surface)
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
                        canClick = if (isPrivateFlavor) canHistory else canOpenVideoInfo,
                        onClick = if (isPrivateFlavor) onHistoryClick else onInfoClick
                    ) {
                        Icon(
                            modifier = Modifier.size(ActionIconSize),
                            painter = painterResource(
                                id = if (isPrivateFlavor) {
                                    R.drawable.add_to_list
                                } else {
                                    R.drawable.info_24px
                                }
                            ),
                            contentDescription = if (isPrivateFlavor) "History" else "Video info"
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
                        modifier = Modifier.focusRequester(favoriteButtonRequester),
                        canClick = canFavorite,
                        onClick = onFavoriteClick
                    ) {
                        Icon(
                            modifier = Modifier.size(ActionIconSize),
                            imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Favorite"
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
                        modifier = Modifier.focusRequester(upButtonRequester),
                        canClick = canGoToUpPage,
                        onClick = onUpClick
                    ) {
                        if (hasMultipleCoAuthors) {
                            Icon(
                                modifier = Modifier.size(ActionIconSize),
                                imageVector = Icons.Rounded.Group,
                                contentDescription = "CoAuthors"
                            )
                        } else {
                            Icon(
                                modifier = Modifier.size(ActionIconSize),
                                painter = painterResource(id = R.drawable.contact_page_24px),
                                contentDescription = "Up Page"
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
                        modifier = Modifier.focusRequester(watchLaterButtonRequester),
                        canClick = canWatchLater,
                        onClick = onWatchLaterClick
                    ) {
                        Icon(
                            modifier = Modifier.size(ActionIconSize),
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = "Watch later"
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
    icon: @Composable () -> Unit
) {
    IconButton(
        modifier = modifier
            .focusProperties { canFocus = canClick }
            .size(ActionButtonSize)
            .aspectRatio(1f),
        enabled = canClick,
        colors = IconButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = C.onSurface,

            focusedContainerColor = C.primary,
            focusedContentColor = C.surface,

            pressedContainerColor = C.primaryContainer,
            pressedContentColor = C.surface,

            disabledContainerColor = Color.Transparent,
            disabledContentColor = C.disabled
        ),
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
        icon()
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
    coverFontScaleMultiplier: Float,
    isFocused: Boolean,
    showPaidBadge: Boolean = false,
    showVipBadge: Boolean = false,
    showVerticalBadge: Boolean = false
) {
    val desaturateFilter = remember {
        // 调节饱和度
        // 0.0f = 完全黑白 (灰度图)
        // 1.0f = 原始色彩
        // > 1.0f = 增加鲜艳度 (比如 1.5f 会非常刺眼)
        // 这里设为 0.8f，意思是让没被选中的卡片颜色稍微发灰一点
        val matrix = ColorMatrix().apply { setToSaturation(0.8f) }

        // 调节亮度/透明度
        // 这里的 0.5f 就是倍数。乘以 0.5 就相当于把 RGB 原有的亮度砍掉一半，实现“压暗”效果。
        // 如果你想让它更暗，可以改成 0.3f；如果不想那么暗，改成 0.7f。
        // 最后一行的 1f 代表 Alpha (透明度) 保持 100% 不变。
        /*
         * 如何给未选中的卡片加一层“蓝色蒙版/滤镜”？
         * 你可以修改蓝色通道的“偏移量”（第5列数字，范围一般是 0~255）。
         * 比如把蓝色那行改成：0.0f, 0.0f, 0.5f, 0.0f, 50.0f
         * 这样暗下去的同时，图片会泛着一层神秘的幽蓝色。
         * 对角线上的数值（比如 0.5f, 0, 0...）只是最基础的“亮度调节”；
         * 而对角线以外的其他数值（比如红色通道里的 G、B），则是用来做“通道混合/调色”
         */
        val darkenMatrix = ColorMatrix(
            floatArrayOf(
                // R    G    B    A   偏移量
                0.75f, 0.0f, 0.0f, 0.0f, 0.35f,  // 红色通道：保留原红色 50%，不加额外偏色
                0.0f, 0.75f, 0.0f, 0.0f, 0.0f,  // 绿色通道：保留原绿色 50%，不加额外偏色
                0.0f, 0.0f, 0.75f, 0.0f, 0.0f,  // 蓝色通道：保留原蓝色 50%，不加额外偏色
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f   // 透明通道：保留原透明度 100%，不让图片变半透明
            )
        )

        // 把“降低饱和度”和“降低亮度”两个效果乘在一起合并
        matrix.timesAssign(darkenMatrix)

        // 转化为 Compose 可用的 ColorFilter
        ColorFilter.colorMatrix(matrix)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RectangleShape),
        contentAlignment = Alignment.BottomCenter
    ) {
        val coverUrl = cover.resizedImageUrl(ImageSize.SmallVideoCardCover)
        val coverRequest = rememberTvImageRequest(
            url = coverUrl,
            widthDp = maxWidth,
            heightDp = maxHeight
        )

        AsyncImage(
            modifier = Modifier
                .fillMaxSize()
                .clip(RectangleShape),
            model = coverRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = if (isFocused) null else desaturateFilter
        )

        val badgeSize = minOf(maxWidth, maxHeight) * 0.22f
        val badgeIconSize = badgeSize * 0.45f

        if (showVipBadge) {
            VipVideoBadge(
                modifier = Modifier.align(Alignment.TopStart),
                size = badgeSize,
                iconSize = badgeIconSize
            )
        } else if (showPaidBadge) {
            PaidVideoBadge(
                modifier = Modifier.align(Alignment.TopStart),
                size = badgeSize,
                iconSize = badgeIconSize
            )
        }

        if (showVerticalBadge) {
            VerticalVideoBadge(
                modifier = Modifier.align(Alignment.TopEnd),
                size = badgeSize,
                iconSize = badgeIconSize
            )
        }

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

private enum class CoverCornerBadgePosition {
    TopStart,
    TopEnd
}

@Composable
private fun CoverCornerBadge(
    modifier: Modifier = Modifier,
    position: CoverCornerBadgePosition,
    size: Dp,
    iconSize: Dp,
    icon: @Composable (Modifier) -> Unit
) {
    val iconOffsetX = when (position) {
        CoverCornerBadgePosition.TopStart -> size / 3f - iconSize / 2f
        CoverCornerBadgePosition.TopEnd -> size * 2f / 3f - iconSize / 2f
    }
    val iconOffsetY = size / 3f - iconSize / 2f
    val badgeColor = C.bilibili

    Box(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val canvasSize = this.size
                val badgePath = Path().apply {
                    when (position) {
                        CoverCornerBadgePosition.TopStart -> {
                            moveTo(0f, 0f)
                            lineTo(canvasSize.width, 0f)
                            lineTo(0f, canvasSize.height)
                        }

                        CoverCornerBadgePosition.TopEnd -> {
                            moveTo(canvasSize.width, 0f)
                            lineTo(0f, 0f)
                            lineTo(canvasSize.width, canvasSize.height)
                        }
                    }
                    close()
                }

                onDrawBehind {
                    drawPath(path = badgePath, color = badgeColor)
                }
            }
    ) {
        icon(
            Modifier
                .offset(x = iconOffsetX, y = iconOffsetY)
                .size(iconSize)
        )
    }
}

@Composable
private fun PaidVideoBadge(
    modifier: Modifier = Modifier,
    size: Dp,
    iconSize: Dp
) {
    CoverCornerBadge(
        modifier = modifier,
        position = CoverCornerBadgePosition.TopStart,
        size = size,
        iconSize = iconSize
    ) { iconModifier ->
        Icon(
            modifier = iconModifier,
            imageVector = Icons.Rounded.CurrencyYen,
            contentDescription = "Paid video",
            tint = AppWhite
        )
    }
}

@Composable
private fun VipVideoBadge(
    modifier: Modifier = Modifier,
    size: Dp,
    iconSize: Dp
) {
    CoverCornerBadge(
        modifier = modifier,
        position = CoverCornerBadgePosition.TopStart,
        size = size,
        iconSize = iconSize
    ) { iconModifier ->
        Icon(
            modifier = iconModifier,
            painter = painterResource(id = R.drawable.vip),
            contentDescription = "VIP video",
            tint = AppWhite
        )
    }
}

@Composable
private fun VerticalVideoBadge(
    modifier: Modifier = Modifier,
    size: Dp,
    iconSize: Dp
) {
    CoverCornerBadge(
        modifier = modifier,
        position = CoverCornerBadgePosition.TopEnd,
        size = size,
        iconSize = iconSize
    ) { iconModifier ->
        Icon(
            modifier = iconModifier,
            imageVector = Icons.Rounded.PhoneAndroid,
            contentDescription = "Vertical video",
            tint = AppWhite
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
                        Modifier.background(Color.Transparent)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
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
                        .clipToBounds(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (playShow.isNotBlank()) {
                        Icon(
                            modifier = Modifier.size(CoverStatIconSize),
                            painter = painterResource(id = R.drawable.ic_play_count),
                            contentDescription = null,
                            tint = AppWhite
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = playShow,
                            style = style,
                            color = AppWhite,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(8.dp))
                    }

                    if (danmakuShow.isNotBlank()) {
                        Icon(
                            modifier = Modifier.size(CoverStatIconSize),
                            painter = painterResource(id = R.drawable.ic_danmaku_count),
                            contentDescription = null,
                            tint = AppWhite
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = danmakuShow,
                            style = style,
                            color = AppWhite,
                            maxLines = 1
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    modifier = Modifier,
                    text = time,
                    style = style,
                    color = AppWhite,
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
                UpIcon(
                    color = C.onSurface,
                    upgroup = hasMultipleCoAuthors
                )
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
    BVTheme(themeMode = ThemeMode.DARK) {
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

@Preview
@Composable
private fun SmallVideoCardLightPreview() {
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
    BVTheme(themeMode = ThemeMode.LIGHT) {
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
