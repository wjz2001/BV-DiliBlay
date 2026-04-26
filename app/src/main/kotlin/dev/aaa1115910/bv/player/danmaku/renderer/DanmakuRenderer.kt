package dev.aaa1115910.bv.player.danmaku.renderer

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ImageCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import dev.aaa1115910.bv.player.danmaku.config.DanmakuConfig
import dev.aaa1115910.bv.player.danmaku.model.DanmakuItem
import dev.aaa1115910.bv.player.danmaku.model.DanmakuItemRef
import dev.aaa1115910.bv.player.danmaku.model.DanmakuRenderFrame
import dev.aaa1115910.bv.player.danmaku.model.DanmakuRenderSnapshot
import dev.aaa1115910.bv.player.danmaku.model.DanmakuTrackType
import dev.aaa1115910.bv.player.danmaku.model.MaskRegionSet
import dev.aaa1115910.bv.util.danmakuMask
import kotlin.math.min

interface DanmakuRenderer {
    val state: DanmakuRendererState
    val renderTick: Long
    val maskTick: Long

    fun render(input: DanmakuRendererInput): DanmakuRendererState

    fun render(
        snapshot: DanmakuRenderSnapshot,
        maskRegionSet: MaskRegionSet? = null,
    ): DanmakuRendererState

    fun render(
        frame: DanmakuRenderFrame,
        maskRegionSet: MaskRegionSet? = null,
    ): DanmakuRendererState

    fun clear(reason: String = "clear"): DanmakuRendererState
}

data class DanmakuRendererInput(
    val frame: DanmakuRenderFrame,
    val maskRegionSet: MaskRegionSet? = null,
) {
    companion object {
        fun from(
            snapshot: DanmakuRenderSnapshot,
            playbackPositionMs: Long,
            playbackSpeed: Float,
            reason: String,
            frameTimeNanos: Long? = null,
            hasMask: Boolean = false,
            maskRegionSet: MaskRegionSet? = null,
        ): DanmakuRendererInput {
            return DanmakuRendererInput(
                frame = DanmakuRenderFrame(
                    snapshot = snapshot,
                    playbackPositionMs = playbackPositionMs,
                    playbackSpeed = playbackSpeed,
                    reason = reason,
                    frameTimeNanos = frameTimeNanos,
                    hasMaskOverride = hasMask,
                ),
                maskRegionSet = maskRegionSet,
            )
        }
    }
}

data class DanmakuRendererStats(
    val renderedFrameCount: Long = 0L,
    val repeatedSnapshotCount: Long = 0L,
    val droppedFrameCount: Long = 0L,
    val lastSnapshotAgeMs: Double = 0.0,
)

data class DanmakuRendererState(
    val input: DanmakuRendererInput? = null,
    val frame: DanmakuRenderFrame? = input?.frame,
    val snapshot: DanmakuRenderSnapshot? = frame?.snapshot,
    val maskRegionSet: MaskRegionSet? = input?.maskRegionSet,
    val lastSnapshotFrameId: Long = snapshot?.frameId ?: 0L,
    val activeItemCount: Int = snapshot?.count ?: 0,
    val maskEnabled: Boolean = frame?.hasMask ?: false,
    val renderSerial: Long = 0L,
    val clearSerial: Long = 0L,
    val snapshotBufferSerial: Long = 0L,
    val snapshotBufferIndex: Int = 0,
    val snapshotDirty: Boolean = false,
    val renderStats: DanmakuRendererStats = DanmakuRendererStats(),
    val reason: String = frame?.reason ?: "init",
)

class ComposeDanmakuRenderer : DanmakuRenderer {
    var input: DanmakuRendererInput? = null
        private set

    override var state: DanmakuRendererState = DanmakuRendererState()
        private set

    override var renderTick by mutableLongStateOf(0L)
        private set

    override var maskTick by mutableLongStateOf(0L)
        private set

    private val snapshotBuffers = arrayOfNulls<DanmakuRenderSnapshot>(SNAPSHOT_BUFFER_COUNT)
    private var publishedBufferIndex: Int = 0
    private var writableBufferIndex: Int = 1
    private var snapshotBufferSerial: Long = 0L
    private var snapshotDirty: Boolean = false
    private var lastSnapshotFrameId: Long = 0L
    private var lastFrameTimeNanos: Long? = null

    override fun render(input: DanmakuRendererInput): DanmakuRendererState {
        this.input = input
        val frame = input.frame
        val stats = nextStats(frame)
        val publishedSnapshot = publishRenderSnapshotIfDirty(frame.snapshot)
        val previousMaskFrame = state.maskRegionSet?.frame
        lastSnapshotFrameId = publishedSnapshot.frameId
        lastFrameTimeNanos = frame.frameTimeNanos
        state = DanmakuRendererState(
            input = input,
            snapshot = publishedSnapshot,
            maskRegionSet = input.maskRegionSet,
            lastSnapshotFrameId = publishedSnapshot.frameId,
            activeItemCount = publishedSnapshot.count,
            maskEnabled = frame.hasMask,
            renderSerial = state.renderSerial + 1,
            clearSerial = state.clearSerial,
            snapshotBufferSerial = snapshotBufferSerial,
            snapshotBufferIndex = publishedBufferIndex,
            snapshotDirty = snapshotDirty,
            renderStats = stats,
            reason = frame.reason,
        )
        if (previousMaskFrame !== input.maskRegionSet?.frame) {
            maskTick += 1
        }
        renderTick += 1
        return state
    }

    override fun render(
        snapshot: DanmakuRenderSnapshot,
        maskRegionSet: MaskRegionSet?,
    ): DanmakuRendererState {
        return render(
            DanmakuRendererInput.from(
                snapshot = snapshot,
                playbackPositionMs = snapshot.positionMs.toLong(),
                playbackSpeed = 1f,
                reason = "render",
                frameTimeNanos = System.nanoTime(),
                hasMask = maskRegionSet?.frame != null,
                maskRegionSet = maskRegionSet,
            )
        )
    }

    override fun render(
        frame: DanmakuRenderFrame,
        maskRegionSet: MaskRegionSet?,
    ): DanmakuRendererState {
        return render(
            DanmakuRendererInput(
                frame = frame,
                maskRegionSet = maskRegionSet,
            )
        )
    }

    override fun clear(reason: String): DanmakuRendererState {
        input = null
        val hadMaskFrame = state.maskRegionSet?.frame != null
        snapshotBuffers[0] = null
        snapshotBuffers[1] = null
        publishedBufferIndex = 0
        writableBufferIndex = 1
        snapshotBufferSerial += 1
        snapshotDirty = false
        lastSnapshotFrameId = 0L
        lastFrameTimeNanos = null
        state = DanmakuRendererState(
            renderSerial = state.renderSerial,
            clearSerial = state.clearSerial + 1,
            snapshotBufferSerial = snapshotBufferSerial,
            snapshotBufferIndex = publishedBufferIndex,
            snapshotDirty = snapshotDirty,
            renderStats = state.renderStats,
            reason = reason,
        )
        if (hadMaskFrame) {
            maskTick += 1
        }
        renderTick += 1
        return state
    }

    private fun publishRenderSnapshotIfDirty(snapshot: DanmakuRenderSnapshot): DanmakuRenderSnapshot {
        val current = snapshotBuffers[publishedBufferIndex]
        if (current?.frameId == snapshot.frameId) {
            snapshotDirty = false
            return current
        }
        snapshotBuffers[writableBufferIndex] = snapshot
        snapshotDirty = true
        val nextPublished = writableBufferIndex
        writableBufferIndex = publishedBufferIndex
        publishedBufferIndex = nextPublished
        snapshotBufferSerial += 1
        snapshotDirty = false
        return snapshotBuffers[publishedBufferIndex] ?: snapshot
    }

    private fun nextStats(frame: DanmakuRenderFrame): DanmakuRendererStats {
        val previous = state.renderStats
        val repeated = if (frame.snapshot.frameId == lastSnapshotFrameId && lastSnapshotFrameId != 0L) {
            previous.repeatedSnapshotCount + 1
        } else {
            previous.repeatedSnapshotCount
        }
        val dropped = if (isDroppedFrame(frame.frameTimeNanos)) {
            previous.droppedFrameCount + 1
        } else {
            previous.droppedFrameCount
        }
        val snapshotAgeMs = (frame.playbackPositionMs - frame.snapshot.positionMs).coerceAtLeast(0.0)
        return previous.copy(
            renderedFrameCount = previous.renderedFrameCount + 1,
            repeatedSnapshotCount = repeated,
            droppedFrameCount = dropped,
            lastSnapshotAgeMs = snapshotAgeMs,
        )
    }

    private fun isDroppedFrame(frameTimeNanos: Long?): Boolean {
        val previousFrameTimeNanos = lastFrameTimeNanos ?: return false
        val currentFrameTimeNanos = frameTimeNanos ?: return false
        return currentFrameTimeNanos - previousFrameTimeNanos > FRAME_DEADLINE_NANOS * 2
    }

    private companion object {
        const val SNAPSHOT_BUFFER_COUNT = 2
        const val FRAME_DEADLINE_NANOS = 16_666_667L
    }
}

@Composable
fun DanmakuRendererHost(
    renderer: DanmakuRenderer,
    modifier: Modifier = Modifier,
    config: DanmakuConfig = DanmakuConfig(),
    videoAspectRatio: Float? = null,
    measureCache: DanmakuTextMeasureCache = rememberDanmakuTextMeasureCache(),
    renderCache: DanmakuRenderCache = rememberDanmakuRenderCache(),
    bitmapCache: DanmakuBitmapCache = rememberDanmakuBitmapCache(),
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val maskTick = renderer.maskTick
    val rendererState = renderer.state
    val maskRegionSet = rendererState.maskRegionSet
    val maskFrame = maskRegionSet?.frame
    val maskAspectRatio = videoAspectRatio
        ?: maskRegionSet?.let { regionSet ->
            val width = regionSet.viewportWidth
            val height = regionSet.viewportHeight
            if (width > 0 && height > 0) width.toFloat() / height.toFloat() else null
        }
    @Suppress("UNUSED_VARIABLE")
    val maskInvalidation = maskTick
    val rendererModifier = if (maskFrame != null && maskAspectRatio != null) {
        modifier.danmakuMask(maskFrame, maskAspectRatio)
    } else {
        modifier
    }

    Canvas(modifier = rendererModifier.fillMaxSize()) {
        val renderTick = renderer.renderTick
        @Suppress("UNUSED_VARIABLE")
        val drawInvalidation = renderTick
        val snapshot = renderer.state.snapshot
        if (snapshot == null || config.enabled.not() || snapshot.items.isEmpty()) {
            renderCache.clearBitmaps()
            return@Canvas
        }

        bitmapCache.beginFrame()
        renderCache.beginFrame()
        val opacity = config.opacity.coerceIn(0f, 1f)
        val areaHeight = size.height * config.area.coerceIn(0f, 1f)
        val bottomAreaTop = size.height - areaHeight
        val roughHeightPx = estimateDanmakuTextHeightPx(config, density)
        snapshot.items.forEach { itemRef ->
            val roughWidthPx = itemRef.item.textWidthPx + config.textPaddingPx.coerceAtLeast(0)
            if (itemRef.x + roughWidthPx < 0f || itemRef.x > size.width) return@forEach
            if (itemRef.y + roughHeightPx < 0f || itemRef.y > size.height) return@forEach

            val renderItem = renderCache.getOrBuild(
                itemRef = itemRef,
                config = config,
                density = density,
                textMeasurer = textMeasurer,
                measureCache = measureCache,
                bitmapCache = bitmapCache,
            )
            val bitmapTopLeft = Offset(itemRef.x, itemRef.y)
            val textTopLeft = Offset(itemRef.x + renderItem.textOffsetPx, itemRef.y + renderItem.textOffsetPx)
            val right = bitmapTopLeft.x + renderItem.widthPx
            val bottom = bitmapTopLeft.y + renderItem.heightPx
            if (right < 0f || bitmapTopLeft.x > size.width) return@forEach
            if (bottom < 0f || bitmapTopLeft.y > size.height) return@forEach

            when (itemRef.item.trackType) {
                DanmakuTrackType.Bottom -> clipRect(top = bottomAreaTop, bottom = size.height) {
                    drawDanmakuItem(renderItem, bitmapTopLeft, textTopLeft, opacity)
                }

                DanmakuTrackType.Scroll,
                DanmakuTrackType.Top,
                -> clipRect(top = 0f, bottom = areaHeight) {
                    drawDanmakuItem(renderItem, bitmapTopLeft, textTopLeft, opacity)
                }
            }
        }
        renderCache.endFrame()
    }
}

private fun DrawScope.drawDanmakuItem(
    renderItem: DanmakuRenderItem,
    bitmapTopLeft: Offset,
    textTopLeft: Offset,
    opacity: Float,
) {
    renderItem.bitmap?.let { bitmap ->
        if (bitmap.fillMask != null) {
            bitmap.strokeMask?.let { strokeMask ->
                drawImage(
                    image = strokeMask,
                    topLeft = bitmapTopLeft,
                    colorFilter = ColorFilter.tint(renderItem.strokeColor.copy(alpha = opacity)),
                )
            }
            drawImage(
                image = bitmap.fillMask,
                topLeft = bitmapTopLeft,
                colorFilter = ColorFilter.tint(renderItem.color.copy(alpha = opacity)),
            )
        } else {
            bitmap.imageBitmap?.let { imageBitmap ->
                drawImage(
                    image = imageBitmap,
                    topLeft = bitmapTopLeft,
                    alpha = opacity,
                )
            }
        }
        return
    }
    drawDanmakuText(
        renderItem = renderItem,
        topLeft = textTopLeft,
        opacity = opacity,
    )
}

private fun DrawScope.drawDanmakuText(
    renderItem: DanmakuRenderItem,
    topLeft: Offset,
    opacity: Float,
) {
    drawText(
        textLayoutResult = renderItem.measureResult.layoutResult,
        color = renderItem.strokeColor.copy(alpha = opacity),
        topLeft = topLeft,
        drawStyle = renderItem.strokeDrawStyle,
    )
    drawText(
        textLayoutResult = renderItem.measureResult.layoutResult,
        color = renderItem.color.copy(alpha = opacity),
        topLeft = topLeft,
        drawStyle = Fill,
    )
}

data class DanmakuRenderCacheKey(
    val itemId: Long,
    val itemColor: Int,
    val itemTextSize: Int,
    val textSizeSpBits: Int,
    val textSizeScale: Int,
    val colorful: Boolean,
    val textPaddingPx: Int,
    val densityBits: Int,
    val fontScaleBits: Int,
)

class DanmakuRenderItem(
    val measureResult: DanmakuTextMeasureResult,
    val color: Color,
    val strokeColor: Color,
    val textOffsetPx: Float,
    var bitmap: DanmakuBitmap?,
    val bitmapKey: DanmakuBitmapCacheKey,
    val strokeDrawStyle: Stroke,
    private val density: Density,
) {
    val widthPx: Int = measureResult.widthPx
    val heightPx: Int = measureResult.heightPx
    var lastSeenFrame: Long = 0L

    fun tryAttachBitmap(bitmapCache: DanmakuBitmapCache?) {
        if (bitmap != null || bitmapCache == null) return
        bitmap = bitmapCache.getOrBuild(
            key = bitmapKey,
            renderItem = this,
            density = density,
        )
    }
}

data class DanmakuBitmapCacheKey(
    val text: String,
    val color: Color,
    val strokeColor: Color,
    val textSizeSpBits: Int,
    val textSizeScale: Int,
    val textPaddingPx: Int,
    val strokeWidthPxBits: Int,
    val densityBits: Int,
    val fontScaleBits: Int,
)

data class DanmakuBitmap(
    val imageBitmap: ImageBitmap? = null,
    val fillMask: ImageBitmap? = null,
    val strokeMask: ImageBitmap? = null,
    val allocationByteCount: Int,
)

class DanmakuRenderCache(
    private val maxSize: Int = DEFAULT_MAX_SIZE,
) {
    private var frameSerial = 0L
    private val cache = object : LinkedHashMap<DanmakuRenderCacheKey, DanmakuRenderItem>(
        maxSize,
        LOAD_FACTOR,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<DanmakuRenderCacheKey, DanmakuRenderItem>?,
        ): Boolean {
            return size > maxSize.coerceAtLeast(1)
        }
    }

    fun beginFrame() {
        frameSerial += 1
    }

    fun endFrame() {
        cache.values.forEach { renderItem ->
            if (renderItem.lastSeenFrame != frameSerial) {
                renderItem.bitmap = null
            }
        }
    }

    fun clearBitmaps() {
        cache.values.forEach { renderItem ->
            renderItem.bitmap = null
        }
    }

    fun getOrBuild(
        itemRef: DanmakuItemRef,
        config: DanmakuConfig,
        density: Density,
        textMeasurer: TextMeasurer,
        measureCache: DanmakuTextMeasureCache,
        bitmapCache: DanmakuBitmapCache?,
    ): DanmakuRenderItem {
        val key = DanmakuRenderCacheKey(
            itemId = itemRef.item.id,
            itemColor = itemRef.item.color,
            itemTextSize = itemRef.item.textSize,
            textSizeSpBits = config.textSizeSp.toBits(),
            textSizeScale = config.textSizeScale,
            colorful = config.colorful,
            textPaddingPx = config.textPaddingPx,
            densityBits = density.density.toBits(),
            fontScaleBits = density.fontScale.toBits(),
        )
        cache[key]?.let { renderItem ->
            renderItem.lastSeenFrame = frameSerial
            renderItem.tryAttachBitmap(bitmapCache)
            return renderItem
        }

        val measureResult = measureCache.getOrMeasure(
            item = itemRef.item,
            config = config,
            density = density,
            textMeasurer = textMeasurer,
        )
        val textColor = if (config.colorful) itemRef.item.toComposeColor() else Color.White
        val strokeWidthPx = with(density) { DanmakuStrokeWidthDp.toPx() }
        val bitmapKey = DanmakuBitmapCacheKey(
            text = itemRef.item.text,
            color = textColor,
            strokeColor = Color.Black,
            textSizeSpBits = config.textSizeSp.toBits(),
            textSizeScale = config.textSizeScale,
            textPaddingPx = config.textPaddingPx,
            strokeWidthPxBits = strokeWidthPx.toBits(),
            densityBits = density.density.toBits(),
            fontScaleBits = density.fontScale.toBits(),
        )
        return DanmakuRenderItem(
            measureResult = measureResult,
            color = textColor,
            strokeColor = Color.Black,
            textOffsetPx = config.textPaddingPx.coerceAtLeast(0) / 2f,
            bitmap = null,
            bitmapKey = bitmapKey,
            strokeDrawStyle = Stroke(
                width = strokeWidthPx,
                join = StrokeJoin.Round,
            ),
            density = density,
        ).also { renderItem ->
            renderItem.lastSeenFrame = frameSerial
            renderItem.tryAttachBitmap(bitmapCache)
            cache[key] = renderItem
        }
    }

    fun clear() {
        cache.clear()
        frameSerial = 0L
    }

    fun size(): Int = cache.size

    companion object {
        const val DEFAULT_MAX_SIZE = 4_096
        private const val LOAD_FACTOR = 0.75f
    }
}

@Composable
fun rememberDanmakuRenderCache(
    maxSize: Int = DanmakuRenderCache.DEFAULT_MAX_SIZE,
): DanmakuRenderCache {
    return remember(maxSize) {
        DanmakuRenderCache(maxSize = maxSize)
    }
}

private fun DanmakuItem.toComposeColor(): Color {
    return Color(0xff000000L or (color.toLong() and 0x00ffffffL))
}

class DanmakuBitmapCache(
    maxBytes: Int,
    private val maxBuildsPerFrame: Int = DEFAULT_MAX_BUILDS_PER_FRAME,
) {
    private val maxEntryBytes = (maxBytes / MAX_ENTRY_FRACTION).coerceAtLeast(MIN_ENTRY_BYTES)
    private var buildsThisFrame = 0
    private val occurrenceCounts = object : LinkedHashMap<DanmakuBitmapCacheKey, Int>(
        OCCURRENCE_CACHE_MAX_SIZE,
        LOAD_FACTOR,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<DanmakuBitmapCacheKey, Int>?,
        ): Boolean {
            return size > OCCURRENCE_CACHE_MAX_SIZE
        }
    }

    private val cache = object : LruCache<DanmakuBitmapCacheKey, DanmakuBitmap>(maxBytes.coerceAtLeast(1)) {
        override fun sizeOf(key: DanmakuBitmapCacheKey, value: DanmakuBitmap): Int {
            return value.allocationByteCount
        }
    }

    fun beginFrame() {
        buildsThisFrame = 0
    }

    fun getOrBuild(
        key: DanmakuBitmapCacheKey,
        renderItem: DanmakuRenderItem,
        density: Density,
    ): DanmakuBitmap? {
        val occurrenceCount = recordOccurrence(key)
        cache.get(key)?.let { return it }
        val width = renderItem.widthPx.coerceAtLeast(1)
        val height = renderItem.heightPx.coerceAtLeast(1)
        val estimatedBytes = estimateBitmapBytes(width, height, ALPHA_8_BYTES_PER_PIXEL * ALPHA_MASK_COUNT)
        if (estimatedBytes > maxEntryBytes) return null
        if (buildsThisFrame >= maxBuildsPerFrame.coerceAtLeast(0)) return null
        buildsThisFrame += 1

        val bitmap = buildDanmakuAlphaMaskBitmap(renderItem, width, height, density)
            ?: buildDanmakuArgbBitmap(renderItem, width, height, density)
            ?: return null
        if (bitmap.allocationByteCount > maxEntryBytes) return null
        if (shouldKeepInReusableCache(key, occurrenceCount, bitmap.allocationByteCount)) {
            cache.put(key, bitmap)
        }
        return bitmap
    }

    fun clear() {
        cache.evictAll()
        occurrenceCounts.clear()
    }

    fun sizeBytes(): Int = cache.size()

    fun maxSizeBytes(): Int = cache.maxSize()

    private fun buildDanmakuAlphaMaskBitmap(
        renderItem: DanmakuRenderItem,
        width: Int,
        height: Int,
        density: Density,
    ): DanmakuBitmap? {
        return runCatching {
            val strokeBitmap = createBitmap(width, height, Bitmap.Config.ALPHA_8)
            val fillBitmap = createBitmap(width, height, Bitmap.Config.ALPHA_8)
            drawTextToImageBitmap(
                target = strokeBitmap.asImageBitmap(),
                width = width,
                height = height,
                density = density,
            ) {
                drawDanmakuStrokeMask(renderItem, Offset(renderItem.textOffsetPx, renderItem.textOffsetPx))
            }
            drawTextToImageBitmap(
                target = fillBitmap.asImageBitmap(),
                width = width,
                height = height,
                density = density,
            ) {
                drawDanmakuFillMask(renderItem, Offset(renderItem.textOffsetPx, renderItem.textOffsetPx))
            }
            DanmakuBitmap(
                fillMask = fillBitmap.asImageBitmap(),
                strokeMask = strokeBitmap.asImageBitmap(),
                allocationByteCount = fillBitmap.allocationByteCount + strokeBitmap.allocationByteCount,
            )
        }.getOrNull()
    }

    private fun buildDanmakuArgbBitmap(
        renderItem: DanmakuRenderItem,
        width: Int,
        height: Int,
        density: Density,
    ): DanmakuBitmap? {
        return runCatching {
            val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val imageBitmap = bitmap.asImageBitmap()
            val canvas = ImageCanvas(imageBitmap)
            val drawScope = CanvasDrawScope()
            drawScope.draw(
                density = density,
                layoutDirection = LayoutDirection.Ltr,
                canvas = canvas,
                size = Size(width.toFloat(), height.toFloat()),
            ) {
                drawDanmakuText(
                    renderItem = renderItem,
                    topLeft = Offset(renderItem.textOffsetPx, renderItem.textOffsetPx),
                    opacity = 1f,
                )
            }
            DanmakuBitmap(
                imageBitmap = imageBitmap,
                allocationByteCount = bitmap.allocationByteCount,
            )
        }.getOrNull()
    }

    private fun drawTextToImageBitmap(
        target: ImageBitmap,
        width: Int,
        height: Int,
        density: Density,
        block: DrawScope.() -> Unit,
    ) {
        val canvas = ImageCanvas(target)
        val drawScope = CanvasDrawScope()
        drawScope.draw(
            density = density,
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas,
            size = Size(width.toFloat(), height.toFloat()),
            block = block,
        )
    }

    private fun DrawScope.drawDanmakuStrokeMask(
        renderItem: DanmakuRenderItem,
        topLeft: Offset,
    ) {
        drawText(
            textLayoutResult = renderItem.measureResult.layoutResult,
            color = Color.White,
            topLeft = topLeft,
            drawStyle = renderItem.strokeDrawStyle,
        )
    }

    private fun DrawScope.drawDanmakuFillMask(
        renderItem: DanmakuRenderItem,
        topLeft: Offset,
    ) {
        drawText(
            textLayoutResult = renderItem.measureResult.layoutResult,
            color = Color.White,
            topLeft = topLeft,
            drawStyle = Fill,
        )
    }

    private fun estimateBitmapBytes(width: Int, height: Int, bytesPerPixel: Int): Int {
        val pixels = width.toLong() * height.toLong()
        return (pixels * bytesPerPixel.toLong())
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun recordOccurrence(key: DanmakuBitmapCacheKey): Int {
        val count = (occurrenceCounts[key] ?: 0) + 1
        occurrenceCounts[key] = count
        return count
    }

    private fun shouldKeepInReusableCache(
        key: DanmakuBitmapCacheKey,
        occurrenceCount: Int,
        allocationByteCount: Int,
    ): Boolean {
        if (allocationByteCount > maxEntryBytes) return false
        if (key.text.length <= SHORT_TEXT_CACHE_LENGTH) return true
        return occurrenceCount >= MIN_REUSABLE_CACHE_OCCURRENCES
    }

    companion object {
        const val DEFAULT_MAX_BUILDS_PER_FRAME = 8
        private const val ALPHA_8_BYTES_PER_PIXEL = 1
        private const val ALPHA_MASK_COUNT = 2
        private const val MAX_ENTRY_FRACTION = 8
        private const val MIN_ENTRY_BYTES = 256 * 1024
        private const val SHORT_TEXT_CACHE_LENGTH = 12
        private const val MIN_REUSABLE_CACHE_OCCURRENCES = 2
        private const val OCCURRENCE_CACHE_MAX_SIZE = 8_192
        private const val LOAD_FACTOR = 0.75f
    }
}

@Composable
fun rememberDanmakuBitmapCache(): DanmakuBitmapCache {
    val context = LocalContext.current
    val maxBytes = remember(context) {
        calculateDanmakuBitmapCacheMaxBytes(context)
    }
    return remember(maxBytes) {
        DanmakuBitmapCache(maxBytes = maxBytes)
    }
}

private fun calculateDanmakuBitmapCacheMaxBytes(context: Context): Int {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memoryClassBytes = (activityManager?.memoryClass ?: DEFAULT_MEMORY_CLASS_MB).toLong() * 1024L * 1024L
    val byMemoryClass = (memoryClassBytes / 8L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val hardCap = if (activityManager?.isLowRamDevice == true) {
        LOW_RAM_BITMAP_CACHE_BYTES
    } else {
        DEFAULT_BITMAP_CACHE_BYTES
    }
    return min(byMemoryClass, hardCap).coerceAtLeast(MIN_BITMAP_CACHE_BYTES)
}

private val DanmakuStrokeWidthDp = 2.dp
private fun estimateDanmakuTextHeightPx(config: DanmakuConfig, density: Density): Float {
    val scale = config.textSizeScale.coerceIn(25, 400) / 100f
    val textSizePx = config.textSizeSp.coerceAtLeast(MIN_TEXT_SIZE_SP) * density.fontScale * density.density * scale
    return (textSizePx * TEXT_HEIGHT_FACTOR).coerceAtLeast(16f) + config.textPaddingPx.coerceAtLeast(0)
}

private const val DEFAULT_MEMORY_CLASS_MB = 256
private const val MIN_BITMAP_CACHE_BYTES = 4 * 1024 * 1024
private const val LOW_RAM_BITMAP_CACHE_BYTES = 8 * 1024 * 1024
private const val DEFAULT_BITMAP_CACHE_BYTES = 32 * 1024 * 1024
private const val MIN_TEXT_SIZE_SP = 1f
private const val TEXT_HEIGHT_FACTOR = 1.08f
