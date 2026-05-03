package dev.aaa1115910.bv.screen.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.Typeface
import androidx.activity.ComponentActivity
import androidx.annotation.FontRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.ui.theme.ThemeMode
import dev.aaa1115910.bv.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@Composable
private fun SettingsPanel(
    modifier: Modifier = Modifier,
    color: Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            // panel 自己内容裁剪，避免内容越界
            .clipToBounds()
            .drawWithCache {
                onDrawBehind {
                    drawRect(color)
                }
            }
    ) {
        content()
    }
}

@Composable
internal fun SettingsMotionColumnsLayout(
    modifier: Modifier = Modifier,
    motion: SettingsMotionController,
    contentColor: Color,
    contentActivated: Boolean,
    onContentActivated: () -> Unit,
    categoryColumn: @Composable (Modifier) -> Unit,
    itemColumn: @Composable (Modifier) -> Unit,
    detailColumn: @Composable (Modifier) -> Unit
){
    var categoryWidthPx by remember { mutableIntStateOf(0) }
    var itemWidthPx by remember { mutableIntStateOf(0) }
    val dividerWidthPx = with(LocalDensity.current) { 1.dp.toPx() } // 标准 1dp 分割线宽
    val panelColor = C.background

    CompositionLocalProvider(LocalSettingsContentColor provides contentColor) {
        Row(
            modifier = modifier
                .background(Color.Transparent)
                .onPreviewKeyEvent {
                    if (!contentActivated && it.type == KeyEventType.KeyDown && it.key.issettingsActivationKey()) {
                        onContentActivated()
                    }
                    false
                }
        ) {
            // ===== Column 1：Category =====
            SettingsPanel(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .fillMaxHeight()
                    .zIndex(1f), // 层级递增，后面的列盖在前面的上面
                color = panelColor
            ) {
                categoryColumn(
                    Modifier
                        .fillMaxHeight()
                        .onSizeChanged { categoryWidthPx = it.width }
                )
            }

            // ===== Column 2：Item =====
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .zIndex(2f)
                    .graphicsLayer {
                        // 覆盖在 Column 1 之上
                        val dx12 = categoryWidthPx + dividerWidthPx
                        translationX = -dx12 * motion.fold2.value
                    }
            ) {
                SettingsDivider(alpha = motion.divider12Alpha)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onSizeChanged { itemWidthPx = it.width }
                ) {
                    val shadowW = 12.dp
                    SeamShadowStrip(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(shadowW)
                            .graphicsLayer { translationX = -shadowW.toPx() },
                        foldAnimatable = motion.fold2
                    )

                    SettingsPanel(modifier = Modifier.fillMaxSize(), color = panelColor) {
                        itemColumn(Modifier.fillMaxSize())
                    }
                }
            }

            // ===== Column 3：Detail =====
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .zIndex(3f)
                    .graphicsLayer {
                        // Column 3 必须在布局上跟随 Column 2 移动，还要叠加自身的向左合并动画
                        val dx12 = categoryWidthPx + dividerWidthPx
                        val dx23 = itemWidthPx + dividerWidthPx
                        translationX = -dx12 * motion.fold2.value - dx23 * motion.fold3.value
                    }
            ) {
                SettingsDivider(alpha = motion.divider23Alpha)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val shadowW = 12.dp
                    SeamShadowStrip(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(shadowW)
                            .graphicsLayer { translationX = -shadowW.toPx() },
                        foldAnimatable = motion.fold3
                    )

                    SettingsPanel(modifier = Modifier.fillMaxSize(), color = panelColor) {
                        detailColumn(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

private fun Key.issettingsActivationKey(): Boolean {
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
private fun rememberIsDarkFromPrefs(): Boolean {
    val themeModeOrdinal by Prefs.themeModeFlow.collectAsState(Prefs.themeMode.ordinal)
    val themeMode = remember(themeModeOrdinal) { ThemeMode.fromOrdinal(themeModeOrdinal) }
    return themeMode == ThemeMode.DARK
}

@Composable
internal fun SpotlightRevealScrim(
    menuPullProgress: Float, // 菜单扯走进度：0=没拉开, 1=完全拉开
    darkness: Float,         // 黑暗程度（关联 bgScrim）：1=纯黑, 0=全亮无遮挡
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return@Canvas

        val p = menuPullProgress.coerceIn(0f, 1f)
        val d = darkness.coerceIn(0f, 1f)

        // 如果黑暗度已降为 0，则不需要绘制任何遮罩（完全露出底图）
        if (d <= 0.001f) return@Canvas

        // 光源中心：绑定在菜单的右边缘。菜单往左扯走，边缘坐标为 w * (1 - p)
        // 将光源稍微偏向垂直中心，使其看起来像一个手电筒或环境点光源
        val lightCenterX = w * (1f - p)
        val lightCenterY = h * 0.5f

        // 光圈半径：随着拉开进度非线性放大
        // p.pow(1.5f) 能让光圈在前期聚焦于边缘，后期产生“爆发扩散”包围全屏的视觉感受
        val maxRadius = hypot(w, h) * 1.5f
        val radius = max(1f, maxRadius * p.pow(1.5f))

        // 3. 径向渐变（点光源，非平行光）
        // 中心完全透明（露出原图），外围逐渐过渡到纯黑
        val spotlightBrush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,                   // 光源绝对核心：透出原图
                0.3f to Color.Black.copy(alpha = d * 0.2f),  // 光晕过渡：微微发暗
                0.8f to Color.Black.copy(alpha = d * 0.8f),  // 光源边缘：较暗
                1.0f to Color.Black.copy(alpha = d)          // 未被光照到的地方：纯黑
            ),
            center = Offset(lightCenterX, lightCenterY),
            radius = radius
        )

        drawRect(
            brush = spotlightBrush,
            topLeft = Offset.Zero,
            size = Size(w, h)
        )
    }
}

@Composable
private fun SeamShadowStrip(
    modifier: Modifier = Modifier,
    foldAnimatable: Animatable<Float, *>
) {
    val isDark = rememberIsDarkFromPrefs()

    val shadowAlpha = if (isDark) 0.55f else 0.12f
    // 贴面板边缘的 1px 硬边线
    val edgeWidthPx = if (isDark) 2f else 1f
    val edgeColor = if (isDark) {
        Color.White.copy(alpha = 0.24f)
    } else {
        Color.Black.copy(alpha = 0.16f)
    }

    val shadowBrush = remember(isDark, shadowAlpha) {
        Brush.horizontalGradient(
            0f to Color.Transparent,
            1f to Color.Black.copy(alpha = shadowAlpha)
        )
    }

    Canvas(
        modifier = modifier.graphicsLayer {
            val fold = foldAnimatable.value
            alpha = (fold * 8f).coerceIn(0f, 1f)
        }
    ) {
        drawRect(brush = shadowBrush, topLeft = Offset.Zero, size = size)

        // 贴边：主硬线
        drawRect(
            color = edgeColor,
            topLeft = Offset(size.width - edgeWidthPx, 0f),
            size = Size(edgeWidthPx, size.height)
        )
        // 往左一像素：辅助线（增加“切割感”）
        drawRect(
            color = if (isDark) Color.Black.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.25f),
            topLeft = Offset(size.width - 2f * edgeWidthPx, 0f),
            size = Size(edgeWidthPx, size.height)
        )
    }
}

internal val LocalSettingsContentColor = compositionLocalOf { Color.Unspecified }

internal tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private object FixedMotionDurationScale : MotionDurationScale {
    override val key = MotionDurationScale.Key
    //值越大动画越慢
    override val scaleFactor: Float = 6f
}

private fun CoroutineScope.launchFixedMotion(
    block: suspend CoroutineScope.() -> Unit
) = launch(context = FixedMotionDurationScale, block = block)

internal class SettingsMotionController(
    private val scope: CoroutineScope
) {
    val fold2 = Animatable(0f)
    val fold3 = Animatable(0f)

    // 菜单整体被扯走（0=原位，1=完全扯走出屏）
    val menuPull = Animatable(0f)

    // 底图遮罩（1=最暗，0=无遮罩，完全亮）
    val bgScrim = Animatable(1f)

    val menuTension = Animatable(0f)

    var divider12Alpha by mutableFloatStateOf(1f)
        private set
    var divider23Alpha by mutableFloatStateOf(1f)
        private set

    var showMenu by mutableStateOf(true)
        private set
    var locked by mutableStateOf(false)
        private set
    var inGallery by mutableStateOf(false)
        private set

    private var job: Job? = null

    fun enterGallery() {
        job?.cancel()
        job = scope.launchFixedMotion {
            locked = true
            inGallery = false
            showMenu = true

            divider12Alpha = 1f
            divider23Alpha = 1f

            fold2.snapTo(0f)
            fold3.snapTo(0f)
            menuPull.snapTo(0f)
            bgScrim.snapTo(1f)     // 先暗着，等露出后再变亮
            menuTension.snapTo(0f)

            // 三列折叠对齐（堆叠）
            val j1 = launch {
                fold2.animateTo(1f, animationSpec = tween(300, easing = FastOutSlowInEasing))
                divider12Alpha = 0f
            }
            val j2 = launch {
                delay(80)
                fold3.animateTo(1f, animationSpec = tween(300, easing = FastOutSlowInEasing))
                divider23Alpha = 0f
            }
            j1.join()
            j2.join()

            // 拉紧一下（纸张张力）
            menuTension.animateTo(1f, animationSpec = tween(50, easing = LinearOutSlowInEasing))
            menuTension.animateTo(0f, animationSpec = tween(30, easing = FastOutLinearInEasing))

            // 菜单整块扯走；同时底图遮罩淡出（逐渐变亮）
            val p1 = launch {
                menuPull.animateTo(1f, animationSpec = tween(450, easing = FastOutSlowInEasing))
            }
            val p2 = launch {
                bgScrim.animateTo(0f, animationSpec = tween(450, easing = FastOutSlowInEasing))
            }
            p1.join()
            p2.join()

            // 完成：菜单隐藏，只剩底图
            showMenu = false
            inGallery = true
            locked = false
        }
    }

    fun exitGallery(activity: Activity? = null) {
        job?.cancel()
        job = scope.launchFixedMotion {
            locked = true
            inGallery = false

            // 退出时：菜单重新出现（此时仍是堆叠态，且在左侧出屏）
            showMenu = true

            // 从“已扯走、已变亮”的状态开始倒放
            fold2.snapTo(1f)
            fold3.snapTo(1f)
            menuPull.snapTo(1f)
            bgScrim.snapTo(0f)
            menuTension.snapTo(0f)

            // 1) 菜单拉回；底图遮罩恢复（逐渐变暗）
            val backJob = launch {
                menuPull.animateTo(0f, animationSpec = tween(300, easing = FastOutSlowInEasing))
            }
            val scrimJob = launch {
                bgScrim.animateTo(1f, animationSpec = tween(300, easing = FastOutSlowInEasing))
            }
            backJob.join()
            scrimJob.join()

            // 2) 堆叠展开回三列
            val unstackJob1 = launch {
                divider23Alpha = 1f
                fold3.animateTo(0f, animationSpec = tween(300, easing = FastOutSlowInEasing))
            }
            val unstackJob2 = launch {
                delay(80)
                divider12Alpha = 1f
                fold2.animateTo(0f, animationSpec = tween(300, easing = FastOutSlowInEasing))
            }
            unstackJob1.join()
            unstackJob2.join()

            locked = false
            (activity as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
                ?: activity?.finish()
        }
    }
}

internal fun readBitmapBoundsNoDecode(res: Resources, resId: Int): Pair<Int, Int> {
    val opts = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        inScaled = false
    }
    BitmapFactory.decodeResource(res, resId, opts)
    val w = opts.outWidth.coerceAtLeast(1)
    val h = opts.outHeight.coerceAtLeast(1)
    return w to h
}

/**
 * 透视一致的 inset：用 Matrix 把单位正方形映射到 quad，再把 (m,m)... 映射回像素坐标。
 * m: 0.08~0.15 常用。越大越“离边远”。
 */
private fun insetQuadXY(q: VersionBadgeQuad): VersionBadgeQuad {
    val unit = floatArrayOf(
        0f, 0f,
        1f, 0f,
        1f, 1f,
        0f, 1f
    )
    val dst = floatArrayOf(
        q.tl.x, q.tl.y,
        q.tr.x, q.tr.y,
        q.br.x, q.br.y,
        q.bl.x, q.bl.y
    )

    val map = Matrix()
    check(map.setPolyToPoly(unit, 0, dst, 0, 4))

    val mx = 0.02f
    val my = 0.05f

    val inner = floatArrayOf(
        mx, my,
        1f - mx, my,
        1f - mx, 1f - my,
        mx, 1f - my
    )
    map.mapPoints(inner)

    return VersionBadgeQuad(
        tl = PointF(inner[0], inner[1]),
        tr = PointF(inner[2], inner[3]),
        br = PointF(inner[4], inner[5]),
        bl = PointF(inner[6], inner[7])
    )
}

private fun makeTextPatch(
    context: Context,
    text: String,
    @FontRes fontResId: Int,
    width: Int,
    height: Int,
    textColor: Int
): Bitmap {
    val bmp = createBitmap(width, height)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(AColor.TRANSPARENT, PorterDuff.Mode.CLEAR)

    val typeface = ResourcesCompat.getFont(context, fontResId) ?: Typeface.DEFAULT
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        color = textColor
        textAlign = Paint.Align.LEFT
    }

    // 字号：先按高度估，再按宽度缩到不溢出
    val textSize = height * 0.62f
    paint.textSize = textSize

    val maxW = width * 0.92f
    val measured = paint.measureText(text).coerceAtLeast(1f)
    val scale = min(1f, maxW / measured)
    paint.textSize = textSize * scale

    // baseline 居中
    val fm = paint.fontMetrics
    val textW = paint.measureText(text)
    val textH = (fm.descent - fm.ascent)
    val x = (width - textW) / 2f
    val y = (height - textH) / 2f - fm.ascent

    canvas.drawText(text, x, y, paint)
    return bmp
}

private data class CropMap(val scale: Float, val offsetX: Float, val offsetY: Float)

private fun computeCropMap(srcW: Float, srcH: Float, dstW: Float, dstH: Float): CropMap {
    val scale = max(dstW / srcW, dstH / srcH) // ContentScale.Crop
    val drawW = srcW * scale
    val drawH = srcH * scale
    val offsetX = (dstW - drawW) / 2f
    val offsetY = (dstH - drawH) / 2f
    return CropMap(scale, offsetX, offsetY)
}

/**
 * 叠加层：
 * 1) 生成透明文字贴片（小 bitmap）
 * 2) 按透视矩阵贴到 quad（映射到屏幕坐标）
 * 3) 永久画出 quad 四条边 + 角点（你看完就删这几行 drawLine/drawCircle）
 */
@Composable
internal fun VersionBadgeOverlay(
    modifier: Modifier,
    srcImageWidthPx: Int,
    srcImageHeightPx: Int,
    versionText: String = BuildConfig.VERSION_NAME,
    @FontRes fontResId: Int,
    textColor: Int,
    oversample: Float = 2.0f
) {
    val context = LocalContext.current

    // base quad
    val baseQuadPx = remember(srcImageWidthPx, srcImageHeightPx) {
        VersionBadgeQuadFixedN.toPxQuad(srcImageWidthPx, srcImageHeightPx)
    }

    // inner quad（用于贴字的安全内缩）
    val innerQuadPx = remember(srcImageWidthPx, srcImageHeightPx) {
        insetQuadXY(baseQuadPx)
    }

    // 贴片尺寸按 inner quad 平均宽高估算（超采样更清晰）
    val (patchW, patchH) = remember(innerQuadPx, oversample) {
        fun dist(a: PointF, b: PointF): Float = hypot(a.x - b.x, a.y - b.y)
        val avgW = (dist(innerQuadPx.tl, innerQuadPx.tr) + dist(innerQuadPx.bl, innerQuadPx.br)) / 2f
        val avgH = (dist(innerQuadPx.tl, innerQuadPx.bl) + dist(innerQuadPx.tr, innerQuadPx.br)) / 2f
        max(64, (avgW * oversample).toInt()) to max(32, (avgH * oversample).toInt())
    }

    val patchBitmap = remember(versionText, fontResId, textColor, patchW, patchH) {
        makeTextPatch(context, versionText, fontResId, patchW, patchH, textColor)
    }

    val bitmapPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true } }

    Canvas(modifier) {
        // 与底图 Image 的 ContentScale.Crop 保持一致，保证对齐
        val crop = computeCropMap(
            srcW = srcImageWidthPx.toFloat(),
            srcH = srcImageHeightPx.toFloat(),
            dstW = size.width,
            dstH = size.height
        )

        fun mapToScreen(p: PointF): PointF =
            PointF(crop.offsetX + p.x * crop.scale, crop.offsetY + p.y * crop.scale)
        // inner quad 映射到屏幕（用于贴字）
        val tl = mapToScreen(innerQuadPx.tl)
        val tr = mapToScreen(innerQuadPx.tr)
        val br = mapToScreen(innerQuadPx.br)
        val bl = mapToScreen(innerQuadPx.bl)

        // patch 四角 -> inner quad 四角（屏幕坐标）
        val src = floatArrayOf(
            0f, 0f,
            patchBitmap.width.toFloat(), 0f,
            patchBitmap.width.toFloat(), patchBitmap.height.toFloat(),
            0f, patchBitmap.height.toFloat()
        )
        val dst = floatArrayOf(
            tl.x, tl.y,
            tr.x, tr.y,
            br.x, br.y,
            bl.x, bl.y
        )

        val m = Matrix()
        val ok = m.setPolyToPoly(src, 0, dst, 0, 4)
        if (ok) {
            drawIntoCanvas { c ->
                c.nativeCanvas.drawBitmap(patchBitmap, m, bitmapPaint)
            }
        }
    }
}

private data class VersionBadgeQuad(
    val tl: PointF,
    val tr: PointF,
    val br: PointF,
    val bl: PointF
)

/**
 * 版本徽章文字区域的四角点。常量基于 3072x1728 原图归一化保存，
 * 使用时按实际解码出来的 bitmap 宽高还原为像素坐标。
 */
private object VersionBadgeQuadFixedN {
    // 基于 3072x1728 的原图像素点：
    private const val TLX = 806 / 3072.0
    private const val TLY = 557 / 1728.0
    private const val TRX = 1957 / 3072.0
    private const val TRY = 751 / 1728.0
    private const val BRX = 1922 / 3072.0
    private const val BRY = 944 / 1728.0
    private const val BLX = 772 / 3072.0
    private const val BLY = 727 / 1728.0

    fun toPxQuad(widthPx: Int, heightPx: Int): VersionBadgeQuad {
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()
        return VersionBadgeQuad(
            tl = PointF((TLX * w).toFloat(), (TLY * h).toFloat()),
            tr = PointF((TRX * w).toFloat(), (TRY * h).toFloat()),
            br = PointF((BRX * w).toFloat(), (BRY * h).toFloat()),
            bl = PointF((BLX * w).toFloat(), (BLY * h).toFloat())
        )
    }
}
