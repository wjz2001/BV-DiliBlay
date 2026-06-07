package dev.aaa1115910.bv.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.platform.LocalDensity
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntrySurface
import dev.aaa1115910.bv.wjzfocus.WjzFocusComponentId
import dev.aaa1115910.bv.wjzfocus.WjzFocusExitsBuilder
import dev.aaa1115910.bv.wjzfocus.WjzFocusEntryId
import dev.aaa1115910.bv.wjzfocus.WjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusRequestResult
import dev.aaa1115910.bv.wjzfocus.WjzFocusRestoreStrategy
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusTargetEntry
import dev.aaa1115910.bv.wjzfocus.WjzFocusTopologyRegionRef
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.activateLayer
import dev.aaa1115910.bv.wjzfocus.defaultEntry
import dev.aaa1115910.bv.wjzfocus.horizontal
import dev.aaa1115910.bv.wjzfocus.keepLayer
import dev.aaa1115910.bv.wjzfocus.resolve
import dev.aaa1115910.bv.wjzfocus.submitExternalNodeFocus
import dev.aaa1115910.bv.wjzfocus.submitInitialEntryFocus
import dev.aaa1115910.bv.wjzfocus.wjzFocusEncodeItemEntryId
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import dev.aaa1115910.bv.wjzfocus.wjzFocusGroupRestorerComponent
import dev.aaa1115910.bv.wjzfocus.wjzFocusGroup
import dev.aaa1115910.bv.wjzfocus.wjzFocusItemNodeId
import dev.aaa1115910.bv.wjzfocus.wjzFocusLocalId
import dev.aaa1115910.bv.wjzfocus.wjzFocusRememberTopologyRegion
import dev.aaa1115910.bv.wjzfocus.wjzFocusRouter
import dev.aaa1115910.bv.wjzfocus.wjzLinearFocusResolver
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.isConfirmKey
import io.github.oshai.kotlinlogging.KotlinLogging

enum class BvTabIndicator {
    Pill,
    Underline
}

enum class BvTabIconMode {
    /** 一直显示“图标 + 文字”，图标在左，文字在右 */
    IconText,

    /** 一直显示“文字 + 图标”，文字在左，图标在右 */
    TextIcon,

    /** 平时只显示文字，获得焦点时在文字左边显示图标 */
    FocusedIconText,

    /** 平时只显示文字，获得焦点时在文字右边显示图标 */
    TextFocusedIcon,

    /** 平时只显示图标，获得焦点时在图标左边显示文字 */
    FocusedTextIcon,

    /** 平时只显示图标，获得焦点时在图标右边显示文字 */
    IconFocusedText,

    /** 只显示图标，不显示文字 */
    IconOnly,

    /** 只显示文字，不显示图标 */
    TextOnly
}

enum class BvTabEntryFocusTarget {
    DefaultEntry,
    LeftEntry,
    RightEntry
}

data class BvTabEntryFocusReady<T>(
    val target: BvTabEntryFocusTarget,
    val item: T,
    val itemKey: Any,
    val nodeId: WjzFocusNodeId
)

data class BvTabFocusRequest<T>(
    val id: Long,
    val item: T? = null,
    val target: BvTabEntryFocusTarget = BvTabEntryFocusTarget.DefaultEntry,
    val activateLayer: Boolean = false
)

data class BvTabEntryFocusConsumed<T>(
    val target: BvTabEntryFocusTarget,
    val item: T,
    val itemKey: Any,
    val nodeId: WjzFocusNodeId
)

sealed interface BvTabEntryFocusResolution<out T> {
    data class Ready<T>(
        val target: BvTabEntryFocusTarget,
        val item: T,
        val itemKey: Any,
        val nodeId: WjzFocusNodeId
    ) : BvTabEntryFocusResolution<T>

    data class Pending(
        val target: BvTabEntryFocusTarget
    ) : BvTabEntryFocusResolution<Nothing>

    data class Reject(
        val target: BvTabEntryFocusTarget
    ) : BvTabEntryFocusResolution<Nothing>
}

fun <T> resolveBvTabEntryFocus(
    items: List<T>,
    selectedItem: T?,
    entryFocusItem: T?,
    entryFocusTarget: BvTabEntryFocusTarget = BvTabEntryFocusTarget.DefaultEntry,
    itemKey: (T) -> Any,
    focusNodeId: WjzFocusNodeId? = null,
    focusScopeId: WjzFocusScopeId? = null,
    focusTargetNodeId: ((Any) -> WjzFocusNodeId?)? = null
): BvTabEntryFocusResolution<T> {
    if (items.isEmpty()) {
        return BvTabEntryFocusResolution.Pending(entryFocusTarget)
    }

    val itemKeys = items.map(itemKey)
    val targetTabIndex = when (entryFocusTarget) {
        BvTabEntryFocusTarget.LeftEntry -> 0
        BvTabEntryFocusTarget.RightEntry -> items.lastIndex
        BvTabEntryFocusTarget.DefaultEntry -> if (entryFocusItem == null) {
            val selectedItemKey = selectedItem?.let(itemKey)
            itemKeys.indexOf(selectedItemKey).takeIf { it >= 0 } ?: 0
        } else {
            val entryFocusItemKey = itemKey(entryFocusItem)
            itemKeys.indexOf(entryFocusItemKey)
                .takeIf { it >= 0 }
                ?: return BvTabEntryFocusResolution.Reject(entryFocusTarget)
        }
    }
    val resolvedItem = items[targetTabIndex]
    val resolvedKey = itemKeys[targetTabIndex]
    val resolvedNodeId = focusTargetNodeId?.invoke(resolvedKey)
        ?: focusScopeId?.resolve(wjzFocusLocalId("tab", resolvedKey))
        ?: return BvTabEntryFocusResolution.Pending(entryFocusTarget)
    return BvTabEntryFocusResolution.Ready(
        target = entryFocusTarget,
        item = resolvedItem,
        itemKey = resolvedKey,
        nodeId = resolvedNodeId
    )
}

private val bvTabRowFocusLogger = KotlinLogging.logger("BvTabRowFocus")

private data class PendingBvTabEntryFocusConsumption<T>(
    val requestId: Long,
    val target: BvTabEntryFocusTarget,
    val item: T,
    val itemKey: Any,
    val nodeId: WjzFocusNodeId
)

/**
 * 项目统一的 TV TabRow 封装
 *
 * 新增 TV 横向 tab 时优先使用本组件，而不是直接使用 [TabRow]。如果样式固定，调用处优先使用
 * [BvUnderlineTabRow] 或 [BvPillTabRow] 等于 [BvTabRow]，避免在调用处直接传 [BvTabIndicator]。
 *
 * 该组件统一处理：
 * - TV 版 [TabRow]/[Tab] 的使用；
 * - 组内焦点恢复，让焦点离开后回到 tab 组时优先恢复到组内焦点；
 * - 业务 item 到 TabRow 下标的映射；
 * - 默认焦点入口、默认焦点 ready 通知、公开 default/selected entry、点击和长按；
 * - Pill/Underline 两种 indicator，以及 tab 间 separator
 *
 * 参数说明：
 * @param modifier 作用在 [TabRow] 外层的 modifier。通常用于 padding、宽高或父级布局约束
 * @param items 要展示的 tab item。为空时组件直接不渲染
 * @param selectedItem 当前业务选中的 item。组件会用 [itemKey] 把它映射成 `selectedTabIndex`
 * 如果 [selectedItem] 不在 [items] 中，会回退到第一个可见 tab
 * @param entryFocusItem 焦点进入该 TabRow 时希望落到的 item。组件会用 [itemKey] 把它映射成默认焦点目标
 * 未指定 [entryFocusItem] 时会回退到 [selectedItem] 对应 tab；如果仍找不到，再回退到第一个可见 tab。
 * 如果 [BvTabEntryFocusTarget.DefaultEntry] 指定的 [entryFocusItem] 不在 [items] 中，新入口 ready 协议会
 * 返回 Reject，不会把回退目标当成真实 ready。
 * 该参数用于“从侧栏/上层区域进入 tab 时落到指定项”，不等同于当前选中的内容
 * @param entryFocusTarget 组件内部的入口落点目标。[BvTabEntryFocusTarget.DefaultEntry]
 * 只有在未指定 [entryFocusItem] 时才按 [selectedItem] -> first 兜底。
 * @param itemKey item 的稳定身份。设置页重排/隐藏 tab 时必须使用业务 ID、code、enum name 等稳定值，
 * 不要使用列表下标。该 key 同时用于 selected/entry 映射、默认焦点通知去重、Compose key 稳定身份
 * @param itemText 默认 tab 内容使用的文字。仅当 [tabContent] 为 null 时生效
 * @param itemIcon 默认 tab 内容使用的图标。仅当 [tabContent] 为 null 时生效
 * @param iconMode 默认 tab 内容中文字和图标的显示策略。仅当 [tabContent] 为 null 时生效
 * @param indicator indicator 样式。传 [BvTabIndicator.Pill] 使用胶囊，传 [BvTabIndicator.Underline] 使用下划线。
 * 调用处通常不需要直接传该参数，优先使用 [BvPillTabRow] 或 [BvUnderlineTabRow]。
 * @param retainIndicatorWhenFocusBelow 当焦点从 TabRow 按下方向键进入内容区时，离焦后是否保留 indicator
 * @param separatorWidth 默认 tab 间距宽度。仅当 [separator] 为 null 时生效，默认 12.dp。
 * @param separator 自定义 tab 间 separator，优先级高于 [separatorWidth]
 * @param onDefaultFocusReady 默认焦点目标完成布局后回调。每个目标 key 只通知一次。
 * @param onEntryFocusReady legacy ready 回调，只在入口可请求且目标完成布局后触发。
 * @param onEntryFocusResolution 语义入口解析状态回调，会通知 Ready/Pending/Reject；Ready 只在入口可请求且目标完成布局后触发。
 * @param onEntryFocusConsumed 语义入口消费回调，只在目标 tab 实际获得焦点后触发。
 * @param onSelectedChanged tab 获得焦点时触发。本组件由调用方 ViewModel 中的
 * `DebouncedActivationController` 负责默认 900ms debounce。
 * @param onClick tab 被点击、确认键激活时触发，立即回调，不经过 debounce。
 * @param onLongClick 确认键长按时触发。返回 true 表示已消费这次长按事件。
 * @param onLeftExit 历史边界参数；组件不再在按键预览里承担跨区域移动，边界由 WjzFocus exits 或上游入口处理。
 * @param onRightExit 历史边界参数；组件不再在按键预览里承担跨区域移动，边界由 WjzFocus exits 或上游入口处理。
 * @param onUp 历史边界参数；组件不再在按键预览里承担上方向出口处理。
 * @param onDown 历史边界参数；组件不再在按键预览里承担下方向出口或内容层恢复处理。
 * @param tabContent 自定义 tab 内容。传入后 [itemText]、[itemIcon]、[iconMode] 不再生效；
 * 外层 [Tab]、焦点、按键、selected 状态仍由 [BvTabRow] 管理。
 *
 * - 焦点目标优先级为 [entryFocusItem] -> [selectedItem] -> 第一个可见 item。
 */
@Composable
fun <T> BvTabRow(
    modifier: Modifier = Modifier,
    items: List<T>,
    selectedItem: T?,
    entryFocusItem: T? = selectedItem,
    entryFocusTarget: BvTabEntryFocusTarget = BvTabEntryFocusTarget.DefaultEntry,
    itemKey: (T) -> Any,
    itemText: (T) -> String = { it.toString() },
    itemIcon: (@Composable (T, Dp) -> Unit)? = null,
    itemHasIcon: (T) -> Boolean = { itemIcon != null },
    iconMode: BvTabIconMode = BvTabIconMode.IconText,
    indicator: BvTabIndicator = BvTabIndicator.Pill,
    retainIndicatorWhenFocusBelow: Boolean = true,
    separatorWidth: Dp = 12.dp,
    separator: (@Composable () -> Unit)? = null,
    onDefaultFocusReady: ((Any) -> Unit)? = null,
    onEntryFocusReady: ((BvTabEntryFocusReady<T>) -> Unit)? = null,
    onEntryFocusResolution: ((BvTabEntryFocusResolution<T>) -> Unit)? = null,
    onEntryFocusConsumed: ((BvTabEntryFocusConsumed<T>) -> Unit)? = null,
    onSelectedChanged: (T) -> Unit = {},
    onClick: (T) -> Unit = {},
    onConfirm: (T) -> Unit = onClick,
    onLongClick: ((T) -> Boolean)? = null,
    onLeftExit: (() -> Unit)? = null,
    onRightExit: (() -> Unit)? = null,
    onUp: ((T) -> Boolean)? = null,
    onDown: ((T) -> Boolean)? = null,
    contentFocusEnabled: Boolean = false,
    contentFocusReadyKey: Any? = null,
    onContentFocusRequested: (T) -> Unit = {},
    tabExits: WjzFocusExitsBuilder.() -> Unit = {},
    tabItemExits: (index: Int, item: T) -> (WjzFocusExitsBuilder.() -> Unit) = { _, _ -> {} },
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    wrap: Boolean = true,
    initialFocusEnabled: Boolean = false,
    entryFocusRequest: BvTabFocusRequest<T>? = null,
    autoRequestEntryFocus: Boolean = false,
    focusNodeId: WjzFocusNodeId? = null,
    focusScopeId: WjzFocusScopeId? = null,
    focusComponentId: WjzFocusComponentId? = null,
    focusLayer: WjzFocusLayer = WjzFocusLayer.TopNav,
    backFocusEnabled: Boolean = true,
    tabContent: (@Composable RowScope.(item: T, selected: Boolean, focused: Boolean) -> Unit)? = null
) {
    val currentOnEntryFocusResolution by rememberUpdatedState(onEntryFocusResolution)
    val currentOnEntryFocusConsumed by rememberUpdatedState(onEntryFocusConsumed)
    if (items.isEmpty()) {
        LaunchedEffect(entryFocusTarget) {
            currentOnEntryFocusResolution?.invoke(BvTabEntryFocusResolution.Pending(entryFocusTarget))
        }
        return
    }

    val targetEntryFocusItem = entryFocusItem
    val itemKeys = remember(items) { items.map(itemKey) }
    val selectedItemKey = selectedItem?.let(itemKey)
    val entryFocusItemKey = targetEntryFocusItem?.let(itemKey)
    val focusCoordinator = LocalWjzFocusCoordinator.current
    val topology = wjzFocusRememberTopologyRegion(topologyRegion)
    val ambientFocusScopeId = LocalWjzFocusScopeId.current
    val resolvedFocusScopeId = focusScopeId ?: ambientFocusScopeId
    val focusRootLocalId = remember { wjzFocusLocalId("root") }
    val resolvedFocusNodeId = remember(focusNodeId, resolvedFocusScopeId, focusRootLocalId) {
        focusNodeId ?: resolvedFocusScopeId?.resolve(focusRootLocalId)
    }
    val selectedTabIndex = itemKeys.indexOf(selectedItemKey).takeIf { it >= 0 } ?: 0
    val focusTargetIndex = itemKeys.indexOf(entryFocusItemKey).takeIf { it >= 0 } ?: selectedTabIndex
    val tabEntryComponentId = remember(focusComponentId, resolvedFocusNodeId) {
        focusComponentId?.value ?: resolvedFocusNodeId?.value?.let { "bv_tab_row_${it.hashCode()}" }
    }
    val tabFocusListId = remember(tabEntryComponentId, itemKeys) {
        tabEntryComponentId ?: "bv_tab_row_${itemKeys.hashCode()}"
    }
    val tabFocusTargets = remember(itemKeys, tabFocusListId, resolvedFocusScopeId, focusLayer) {
        itemKeys.map { key ->
            val entryId = wjzFocusEncodeItemEntryId(key.toString())
            val localId = WjzFocusLocalId(
                wjzFocusItemNodeId(
                    tabFocusListId,
                    entryId
                )
            )
            val nodeId = resolvedFocusScopeId?.resolve(localId) ?: WjzFocusNodeId(localId.value)
            WjzFocusTargetEntry(
                id = entryId,
                nodeId = nodeId,
                layer = focusLayer,
                scopeId = resolvedFocusScopeId
            )
        }
    }
    val tabFocusResolver = remember(tabFocusTargets, wrap) {
        wjzLinearFocusResolver(
            entries = tabFocusTargets,
            direction = horizontal,
            wrap = wrap
        )
    }
    val focusTargetNodeId = tabFocusTargets.getOrNull(focusTargetIndex)?.nodeId
    val entryFocusResolution = resolveBvTabEntryFocus(
        items = items,
        selectedItem = selectedItem,
        entryFocusItem = entryFocusItem,
        entryFocusTarget = entryFocusTarget,
        itemKey = itemKey,
        focusNodeId = resolvedFocusNodeId,
        focusScopeId = resolvedFocusScopeId,
        focusTargetNodeId = if (resolvedFocusScopeId == null) {
            null
        } else {
            { key ->
                val index = itemKeys.indexOf(key)
                tabFocusTargets.getOrNull(index)?.nodeId
            }
        }
    )

    var focusedTabIndex by remember(itemKeys) { mutableIntStateOf(focusTargetIndex) }
    var isFocusBelowTabRow by remember(itemKeys) { mutableStateOf(false) }
    var lastDefaultFocusPositionedState by remember(itemKeys) { mutableStateOf<String?>(null) }
    var pendingEntryFocusConsumption by remember {
        mutableStateOf<PendingBvTabEntryFocusConsumption<T>?>(null)
    }

    val canRequestInitialFocus = (initialFocusEnabled || autoRequestEntryFocus) && backFocusEnabled
    LaunchedEffect(
        canRequestInitialFocus,
        tabEntryComponentId,
        focusCoordinator,
        resolvedFocusScopeId,
        focusLayer,
        items.isNotEmpty()
    ) {
        if (!canRequestInitialFocus) {
            bvTabRowFocusLogger.fInfo { "initial entry skip: canRequestInitialFocus=false" }
            return@LaunchedEffect
        }
        val coordinator = focusCoordinator
        if (coordinator == null) {
            bvTabRowFocusLogger.fInfo { "initial entry skip: focusCoordinator=null" }
            return@LaunchedEffect
        }
        val targetNodeId = focusTargetNodeId
        if (targetNodeId == null) {
            bvTabRowFocusLogger.fInfo { "initial entry skip: focusTargetNodeId=null" }
            return@LaunchedEffect
        }
        val dedupeKey = tabEntryComponentId ?: resolvedFocusNodeId?.value ?: tabFocusListId

        bvTabRowFocusLogger.fInfo {
            "initial entry request: targetNodeId=${targetNodeId.value}, " +
                    "layer=$focusLayer, scopeId=${resolvedFocusScopeId?.value}, " +
                    "dedupeKey=$dedupeKey"
        }
        val entryComponentId = tabEntryComponentId ?: return@LaunchedEffect
        val result = coordinator.submitInitialEntryFocus(
            entryId = WjzFocusEntryId("$entryComponentId/default"),
            dedupeKey = dedupeKey
        )
        bvTabRowFocusLogger.fInfo { "initial entry result: $result" }
    }

    LaunchedEffect(
        entryFocusRequest?.id,
        focusCoordinator,
        resolvedFocusScopeId,
        focusLayer,
        itemKeys
    ) {
        val request = entryFocusRequest ?: return@LaunchedEffect
        val coordinator = focusCoordinator
        if (coordinator == null || !backFocusEnabled) {
            pendingEntryFocusConsumption = null
            return@LaunchedEffect
        }
        val requestItemKey = request.item?.let(itemKey)
        val targetIndex = requestItemKey
            ?.let { key -> itemKeys.indexOf(key).takeIf { it >= 0 } }
            ?: when (request.target) {
                BvTabEntryFocusTarget.LeftEntry -> 0
                BvTabEntryFocusTarget.RightEntry -> items.lastIndex
                BvTabEntryFocusTarget.DefaultEntry -> focusTargetIndex
            }
        val targetNodeId = tabFocusTargets.getOrNull(targetIndex)?.nodeId
        if (targetNodeId == null) {
            pendingEntryFocusConsumption = null
            return@LaunchedEffect
        }

        pendingEntryFocusConsumption = PendingBvTabEntryFocusConsumption(
            requestId = request.id,
            target = request.target,
            item = items[targetIndex],
            itemKey = itemKeys[targetIndex],
            nodeId = targetNodeId
        )
        val result = coordinator.submitExternalNodeFocus(
            nodeId = targetNodeId,
            layer = focusLayer,
            scopeId = resolvedFocusScopeId,
            layerActivation = if (request.activateLayer) activateLayer else keepLayer,
            dedupeKey = request.id
        )
        if (result != WjzFocusRequestResult.Focused && result != WjzFocusRequestResult.Enqueued) {
            pendingEntryFocusConsumption = null
        }
        bvTabRowFocusLogger.fInfo { "external entry result: requestId=${request.id}, result=$result" }
    }

    LaunchedEffect(entryFocusResolution) {
        if (entryFocusResolution !is BvTabEntryFocusResolution.Ready) {
            currentOnEntryFocusResolution?.invoke(entryFocusResolution)
        }
    }

    val tabRowModifier = if (resolvedFocusNodeId == null) {
        modifier
    } else {
        modifier.wjzFocusExits(
            nodeId = resolvedFocusNodeId,
            layer = focusLayer,
            strategy = WjzFocusRestoreStrategy.Container,
            enabled = backFocusEnabled
        )
    }

    val tabRowContent: @Composable () -> Unit = {
        TabRow(
            modifier = Modifier.wjzFocusGroup(),
            selectedTabIndex = selectedTabIndex,
            separator = separator ?: { Spacer(modifier = Modifier.width(separatorWidth)) },
            indicator = bvTabIndicator(
                indicator = indicator,
                selectedTabIndex = selectedTabIndex,
                retainIndicatorWhenFocusBelow = retainIndicatorWhenFocusBelow,
                isFocusBelowTabRow = isFocusBelowTabRow
            )
        ) {
            items.forEachIndexed { index, item ->
                val key = itemKeys[index]
                val tabFocusTarget = tabFocusTargets[index]
                var confirmLongPressTriggered by remember(key) { mutableStateOf(false) }
                val focused = focusedTabIndex == index
                val selected = selectedTabIndex == index
                var tabModifier = Modifier
                    .wjzFocusRouter(
                        currentEntryId = tabFocusTarget.id,
                        resolver = tabFocusResolver
                    )
                    .onPreviewKeyEvent { event ->
                        val isConfirmKey = event.isConfirmKey()
                        if (!isConfirmKey) return@onPreviewKeyEvent false

                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                if (event.nativeKeyEvent.isLongPress) {
                                    if (onLongClick == null) {
                                        return@onPreviewKeyEvent false
                                    }
                                    if (!confirmLongPressTriggered) {
                                        confirmLongPressTriggered = onLongClick(item)
                                    }
                                    return@onPreviewKeyEvent confirmLongPressTriggered
                                }
                                false
                            }

                            KeyEventType.KeyUp -> {
                                if (confirmLongPressTriggered) {
                                    confirmLongPressTriggered = false
                                    true
                                } else {
                                    onConfirm(item)
                                    true
                                }
                            }

                            else -> false
                        }
                    }

                if (resolvedFocusScopeId != null) {
                    tabModifier = tabModifier.wjzFocusExits(
                        nodeId = tabFocusTarget.nodeId,
                        scopeId = tabFocusTarget.scopeId,
                        layer = tabFocusTarget.layer,
                        enabled = backFocusEnabled,
                        attachFocusTarget = false,
                        exits = {
                            if (topology.isStandalone) {
                                tabExits()
                                tabItemExits(index, item).invoke(this)
                            } else {
                                val localExits = WjzFocusExitsBuilder().apply {
                                    tabExits()
                                    tabItemExits(index, item).invoke(this)
                                }.exits
                                addAll(localExits)
                                addAll(
                                    topology.nodeExits.filterNot { topologyExit ->
                                        localExits.any { localExit ->
                                            localExit.direction == topologyExit.direction
                                        }
                                    }
                                )
                            }
                        }
                    )
                }

                if (index == focusTargetIndex) {
                    tabModifier = tabModifier.onGloballyPositioned {
                        val state = buildString {
                            append("key=").append(key)
                            append(", index=").append(index)
                            append(", selected=").append(selected)
                            append(", focused=").append(focused)
                            append(", focusTargetIndex=").append(focusTargetIndex)
                        }
                        if (lastDefaultFocusPositionedState != state) {
                            lastDefaultFocusPositionedState = state
                            bvTabRowFocusLogger.fInfo {
                                "default focus target positioned: $state"
                            }
                        }
                        onDefaultFocusReady?.invoke(key)
                        val ready = entryFocusResolution as? BvTabEntryFocusResolution.Ready
                        if (ready != null && ready.itemKey == key) {
                            val readyState = BvTabEntryFocusReady(
                                target = ready.target,
                                item = ready.item,
                                itemKey = ready.itemKey,
                                nodeId = ready.nodeId
                            )
                            onEntryFocusReady?.invoke(readyState)
                            currentOnEntryFocusResolution?.invoke(ready)
                        }
                    }
                }

                Tab(
                    colors = when (indicator) {
                        BvTabIndicator.Pill -> mainTopTabColors()
                        BvTabIndicator.Underline -> TabDefaults.underlinedIndicatorTabColors()
                    },
                    modifier = tabModifier,
                    selected = selected,
                    onFocus = {
                        isFocusBelowTabRow = false
                        confirmLongPressTriggered = false
                        focusedTabIndex = index
                        val pendingConsumption = pendingEntryFocusConsumption
                        if (pendingConsumption != null &&
                            pendingConsumption.requestId == entryFocusRequest?.id &&
                            pendingConsumption.itemKey == key
                        ) {
                            pendingEntryFocusConsumption = null
                            currentOnEntryFocusConsumed?.invoke(
                                BvTabEntryFocusConsumed(
                                    target = pendingConsumption.target,
                                    item = pendingConsumption.item,
                                    itemKey = pendingConsumption.itemKey,
                                    nodeId = pendingConsumption.nodeId
                                )
                            )
                        }
                        onSelectedChanged(item)
                    },
                    onClick = { onConfirm(item) }
                ) {
                    if (tabContent != null) {
                        tabContent(item, selected, focused)
                    } else {
                        BvTabDefaultContent(
                            item = item,
                            text = itemText,
                            icon = itemIcon,
                            hasIcon = itemHasIcon,
                            iconMode = iconMode,
                            focused = focused
                        )
                    }
                }
            }
        }
    }

    if (resolvedFocusNodeId == null) {
        tabRowContent()
    } else {
        val tabRestorer = remember(resolvedFocusNodeId, resolvedFocusScopeId, focusLayer) {
            wjzFocusGroupRestorerComponent(
                componentId = resolvedFocusNodeId.value,
                layer = focusLayer,
                scopeId = resolvedFocusScopeId
            )
        }
        Box(
            modifier = with(tabRestorer) {
                tabRowModifier.restorerHost(
                    enabled = backFocusEnabled,
                    fallbackNodeId = focusTargetNodeId
                )
            }.wjzFocusGroup()
        ) {
            if (backFocusEnabled) {
                val entryComponentId = tabEntryComponentId ?: return@Box
                WjzFocusEntrySurface(
                    componentId = entryComponentId,
                    default = {
                        topology.resolveInitialTarget(
                            componentId = entryComponentId,
                            targets = tabFocusTargets
                        ) {
                            tabFocusTargets.getOrNull(focusTargetIndex)
                                ?: defaultEntry(
                                nodeId = resolvedFocusNodeId,
                                layer = focusLayer,
                                scopeId = resolvedFocusScopeId
                            )
                        }
                    },
                    entries = {
                        val selectedTabTarget = tabFocusTargets.getOrNull(selectedTabIndex)
                        if (selectedTabTarget != null) {
                            entry("selected") {
                                selectedTabTarget
                            }
                        }
                        val leftTabTarget = tabFocusTargets.firstOrNull()
                        if (leftTabTarget != null) {
                            entry("left") {
                                leftTabTarget
                            }
                        }
                        val rightTabTarget = tabFocusTargets.lastOrNull()
                        if (rightTabTarget != null) {
                            entry("right") {
                                rightTabTarget
                            }
                        }
                    }
                )
            }
            tabRowContent()
        }
    }
}

/**
 * 胶囊样式的 [BvTabRow]。
 *
 * 固定使用 [BvTabIndicator.Pill]，调用处不需要传 `indicator`。
 * 其他参数和 [BvTabRow] 保持一致。
 */
@Composable
fun <T> BvPillTabRow(
    modifier: Modifier = Modifier,
    items: List<T>,
    selectedItem: T?,
    entryFocusItem: T? = selectedItem,
    entryFocusTarget: BvTabEntryFocusTarget = BvTabEntryFocusTarget.DefaultEntry,
    itemKey: (T) -> Any,
    itemText: (T) -> String = { it.toString() },
    itemIcon: (@Composable (T, Dp) -> Unit)? = null,
    itemHasIcon: (T) -> Boolean = { itemIcon != null },
    iconMode: BvTabIconMode = BvTabIconMode.IconText,
    retainIndicatorWhenFocusBelow: Boolean = true,
    separatorWidth: Dp = 12.dp,
    separator: (@Composable () -> Unit)? = null,
    onDefaultFocusReady: ((Any) -> Unit)? = null,
    onEntryFocusReady: ((BvTabEntryFocusReady<T>) -> Unit)? = null,
    onEntryFocusResolution: ((BvTabEntryFocusResolution<T>) -> Unit)? = null,
    onEntryFocusConsumed: ((BvTabEntryFocusConsumed<T>) -> Unit)? = null,
    onSelectedChanged: (T) -> Unit = {},
    onClick: (T) -> Unit = {},
    onConfirm: (T) -> Unit = onClick,
    onLongClick: ((T) -> Boolean)? = null,
    onLeftExit: (() -> Unit)? = null,
    onRightExit: (() -> Unit)? = null,
    onUp: ((T) -> Boolean)? = null,
    onDown: ((T) -> Boolean)? = null,
    contentFocusEnabled: Boolean = false,
    contentFocusReadyKey: Any? = null,
    onContentFocusRequested: (T) -> Unit = {},
    tabExits: WjzFocusExitsBuilder.() -> Unit = {},
    tabItemExits: (index: Int, item: T) -> (WjzFocusExitsBuilder.() -> Unit) = { _, _ -> {} },
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    wrap: Boolean = true,
    initialFocusEnabled: Boolean = false,
    entryFocusRequest: BvTabFocusRequest<T>? = null,
    autoRequestEntryFocus: Boolean = false,
    focusNodeId: WjzFocusNodeId? = null,
    focusScopeId: WjzFocusScopeId? = null,
    focusComponentId: WjzFocusComponentId? = null,
    focusLayer: WjzFocusLayer = WjzFocusLayer.TopNav,
    backFocusEnabled: Boolean = true,
    tabContent: (@Composable RowScope.(item: T, selected: Boolean, focused: Boolean) -> Unit)? = null
) {
    BvTabRow(
        modifier = modifier,
        items = items,
        selectedItem = selectedItem,
        entryFocusItem = entryFocusItem,
        entryFocusTarget = entryFocusTarget,
        itemKey = itemKey,
        itemText = itemText,
        itemIcon = itemIcon,
        itemHasIcon = itemHasIcon,
        iconMode = iconMode,
        indicator = BvTabIndicator.Pill,
        retainIndicatorWhenFocusBelow = retainIndicatorWhenFocusBelow,
        separatorWidth = separatorWidth,
        separator = separator,
        onDefaultFocusReady = onDefaultFocusReady,
        onEntryFocusReady = onEntryFocusReady,
        onEntryFocusResolution = onEntryFocusResolution,
        onEntryFocusConsumed = onEntryFocusConsumed,
        onSelectedChanged = onSelectedChanged,
        onClick = onClick,
        onConfirm = onConfirm,
        onLongClick = onLongClick,
        onLeftExit = onLeftExit,
        onRightExit = onRightExit,
        onUp = onUp,
        onDown = onDown,
        contentFocusEnabled = contentFocusEnabled,
        contentFocusReadyKey = contentFocusReadyKey,
        onContentFocusRequested = onContentFocusRequested,
        tabExits = tabExits,
        tabItemExits = tabItemExits,
        topologyRegion = topologyRegion,
        wrap = wrap,
        initialFocusEnabled = initialFocusEnabled,
        entryFocusRequest = entryFocusRequest,
        autoRequestEntryFocus = autoRequestEntryFocus,
        focusNodeId = focusNodeId,
        focusScopeId = focusScopeId,
        focusComponentId = focusComponentId,
        focusLayer = focusLayer,
        backFocusEnabled = backFocusEnabled,
        tabContent = tabContent
    )
}

/**
 * 下划线样式的 [BvTabRow]。
 *
 * 固定使用 [BvTabIndicator.Underline]，调用处不需要传 `indicator`。
 * 其他参数和 [BvTabRow] 保持一致。
 */
@Composable
fun <T> BvUnderlineTabRow(
    modifier: Modifier = Modifier,
    items: List<T>,
    selectedItem: T?,
    entryFocusItem: T? = selectedItem,
    entryFocusTarget: BvTabEntryFocusTarget = BvTabEntryFocusTarget.DefaultEntry,
    itemKey: (T) -> Any,
    itemText: (T) -> String = { it.toString() },
    itemIcon: (@Composable (T, Dp) -> Unit)? = null,
    itemHasIcon: (T) -> Boolean = { itemIcon != null },
    iconMode: BvTabIconMode = BvTabIconMode.IconText,
    retainIndicatorWhenFocusBelow: Boolean = true,
    separatorWidth: Dp = 12.dp,
    separator: (@Composable () -> Unit)? = null,
    onDefaultFocusReady: ((Any) -> Unit)? = null,
    onEntryFocusReady: ((BvTabEntryFocusReady<T>) -> Unit)? = null,
    onEntryFocusResolution: ((BvTabEntryFocusResolution<T>) -> Unit)? = null,
    onEntryFocusConsumed: ((BvTabEntryFocusConsumed<T>) -> Unit)? = null,
    onSelectedChanged: (T) -> Unit = {},
    onClick: (T) -> Unit = {},
    onConfirm: (T) -> Unit = onClick,
    onLongClick: ((T) -> Boolean)? = null,
    onLeftExit: (() -> Unit)? = null,
    onRightExit: (() -> Unit)? = null,
    onUp: ((T) -> Boolean)? = null,
    onDown: ((T) -> Boolean)? = null,
    contentFocusEnabled: Boolean = false,
    contentFocusReadyKey: Any? = null,
    onContentFocusRequested: (T) -> Unit = {},
    tabExits: WjzFocusExitsBuilder.() -> Unit = {},
    tabItemExits: (index: Int, item: T) -> (WjzFocusExitsBuilder.() -> Unit) = { _, _ -> {} },
    topologyRegion: WjzFocusTopologyRegionRef = WjzFocusTopologyRegionRef.Standalone,
    wrap: Boolean = true,
    initialFocusEnabled: Boolean = false,
    entryFocusRequest: BvTabFocusRequest<T>? = null,
    autoRequestEntryFocus: Boolean = false,
    focusNodeId: WjzFocusNodeId? = null,
    focusScopeId: WjzFocusScopeId? = null,
    focusComponentId: WjzFocusComponentId? = null,
    focusLayer: WjzFocusLayer = WjzFocusLayer.TopNav,
    backFocusEnabled: Boolean = true,
    tabContent: (@Composable RowScope.(item: T, selected: Boolean, focused: Boolean) -> Unit)? = null
) {
    BvTabRow(
        modifier = modifier,
        items = items,
        selectedItem = selectedItem,
        entryFocusItem = entryFocusItem,
        entryFocusTarget = entryFocusTarget,
        itemKey = itemKey,
        itemText = itemText,
        itemIcon = itemIcon,
        itemHasIcon = itemHasIcon,
        iconMode = iconMode,
        indicator = BvTabIndicator.Underline,
        retainIndicatorWhenFocusBelow = retainIndicatorWhenFocusBelow,
        separatorWidth = separatorWidth,
        separator = separator,
        onDefaultFocusReady = onDefaultFocusReady,
        onEntryFocusReady = onEntryFocusReady,
        onEntryFocusResolution = onEntryFocusResolution,
        onEntryFocusConsumed = onEntryFocusConsumed,
        onSelectedChanged = onSelectedChanged,
        onClick = onClick,
        onConfirm = onConfirm,
        onLongClick = onLongClick,
        onLeftExit = onLeftExit,
        onRightExit = onRightExit,
        onUp = onUp,
        onDown = onDown,
        contentFocusEnabled = contentFocusEnabled,
        contentFocusReadyKey = contentFocusReadyKey,
        onContentFocusRequested = onContentFocusRequested,
        tabExits = tabExits,
        tabItemExits = tabItemExits,
        topologyRegion = topologyRegion,
        wrap = wrap,
        initialFocusEnabled = initialFocusEnabled,
        entryFocusRequest = entryFocusRequest,
        autoRequestEntryFocus = autoRequestEntryFocus,
        focusNodeId = focusNodeId,
        focusScopeId = focusScopeId,
        focusComponentId = focusComponentId,
        focusLayer = focusLayer,
        backFocusEnabled = backFocusEnabled,
        tabContent = tabContent
    )
}

/**
 * 用于设置页配置 BvTabRow 的可见项和显示顺序。
 *
 * 内部复用 [OrderedMultiSelectListContent]，适合放在设置页详情列里使用。
 *
 * 数据流：
 * 1. allItems 是完整 tab 列表。
 * 2. enabledOrderedIds 是当前启用且已经排好序的 tab ID 列表。
 * 3. 用户调整选择后，onSubmit 返回新的启用有序 ID 列表。
 * 4. 页面保存这个 ID 列表，并按它过滤、排序 allItems。
 * 5. 页面把过滤排序后的 items 传给 BvTabRow。
 *
 * itemId 必须和 BvTabRow 的 `itemKey` 表达同一个稳定身份，例如业务 code、枚举名或服务端 ID。
 * 不要使用列表下标作为 ID。下标会随着过滤、插入、删除或排序变化而改变，无法稳定还原
 */
@Composable
fun <T, ID : Any> BvTabOrderListContent(
    modifier: Modifier = Modifier,
    allItems: List<T>,
    enabledOrderedIds: List<ID>,
    itemId: (T) -> ID,
    onSubmit: (List<ID>) -> Unit,
    text: (T) -> String,
    itemKey: ((T) -> Any)? = null,
    defaultFocusKey: Any? = enabledOrderedIds.firstOrNull(),
    defaultFocusIndex: Int? = null,
    requestDefaultFocus: Boolean = true,
    requiredSelectedIds: Collection<ID> = emptyList(),
    requiredSelectionToastText: String = "初始页面不能隐藏",
    onBlockedExit: (() -> Unit)? = null
) {
    var selectedOrders by remember(allItems, enabledOrderedIds) {
        mutableStateOf(
            enabledOrderedIds
                .filter { id -> allItems.any { item -> itemId(item) == id } }
                .distinct()
                .mapIndexed { index, id -> id to index + 1 }
                .toMap()
        )
    }
    val startupItemId = remember(allItems, itemKey, defaultFocusKey) {
        if (defaultFocusKey == null) {
            null
        } else {
            allItems.firstOrNull { item ->
                (itemKey?.invoke(item) ?: itemId(item)) == defaultFocusKey
            }?.let(itemId)
        }
    }
    val resolvedRequiredSelectedIds = remember(requiredSelectedIds, startupItemId) {
        if (startupItemId == null) {
            requiredSelectedIds
        } else {
            requiredSelectedIds + startupItemId
        }
    }

    OrderedMultiSelectListContent(
        modifier = modifier,
        items = allItems,
        selectedOrders = selectedOrders,
        itemId = itemId,
        onSelectedOrdersChange = { orders ->
            selectedOrders = orders
            onSubmit(
                orders.entries
                    .sortedBy { it.value }
                    .map { it.key }
            )
        },
        text = text,
        itemKey = itemKey ?: { itemId(it) },
        defaultFocusKey = defaultFocusKey,
        defaultFocusIndex = defaultFocusIndex,
        requestDefaultFocus = requestDefaultFocus,
        requiredSelectedIds = resolvedRequiredSelectedIds,
        requiredSelectionToastText = requiredSelectionToastText,
        onBlockedExit = onBlockedExit
    )
}

/**
 * 按设置页保存的启用 ID 列表过滤并排序 tab。
 *
 * 该函数和 [BvTabOrderListContent] 配套使用：设置页保存 [enabledOrderedIds]，业务页把返回值传给
 * [BvTabRow] / [BvPillTabRow] / [BvUnderlineTabRow]。
 */
fun <T, ID : Any> filterOrderedBvTabItems(
    allItems: List<T>,
    enabledOrderedIds: List<ID>,
    itemId: (T) -> ID
): List<T> {
    val itemById = allItems.associateBy(itemId)
    return enabledOrderedIds.mapNotNull { id -> itemById[id] }
}

@Composable
private fun bvTabIndicator(
    indicator: BvTabIndicator,
    selectedTabIndex: Int,
    retainIndicatorWhenFocusBelow: Boolean,
    isFocusBelowTabRow: Boolean
): @Composable (List<DpRect>, Boolean) -> Unit {
    return { tabPositions, doesTabRowHaveFocus ->
        tabPositions.getOrNull(selectedTabIndex)?.let { currentTabPosition ->
            val shouldShowIndicator =
                !retainIndicatorWhenFocusBelow || doesTabRowHaveFocus || isFocusBelowTabRow
            when (indicator) {
                BvTabIndicator.Pill -> {
                    if (shouldShowIndicator) {
                        TabRowDefaults.PillIndicator(
                            currentTabPosition = currentTabPosition,
                            doesTabRowHaveFocus = doesTabRowHaveFocus,
                            activeColor = MaterialTheme.colorScheme.primary,
                            inactiveColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                }

                BvTabIndicator.Underline -> {
                    if (shouldShowIndicator) {
                        TabRowDefaults.UnderlinedIndicator(
                            currentTabPosition = currentTabPosition,
                            doesTabRowHaveFocus = doesTabRowHaveFocus
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BvTabLabel(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable (Dp) -> Unit)? = null,
    showIcon: Boolean = icon != null,
    iconMode: BvTabIconMode = BvTabIconMode.IconText,
    focused: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge.copy(
        fontSize = MaterialTheme.typography.labelLarge.fontSize * 1.8f
    )
) {
    val density = LocalDensity.current
    val iconSize = with(density) {
        if (textStyle.fontSize.isUnspecified) 16.dp else textStyle.fontSize.toDp()
    }
    val textContent: @Composable () -> Unit = {
        Text(
            text = text,
            color = LocalContentColor.current,
            style = textStyle
        )
    }
    val iconContent: @Composable () -> Unit = { icon?.invoke(iconSize) }
    val gap: @Composable () -> Unit = {
        if (showIcon && icon != null) Spacer(modifier = Modifier.width(4.dp))
    }

    Box(
        modifier = modifier
            .height(MainTopTabDefaults.TabContentHeight)
            .padding(MainTopTabDefaults.TabContentPadding)
            .wrapContentWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (iconMode) {
                BvTabIconMode.IconText -> {
                    if (showIcon) {
                        iconContent()
                        gap()
                    }
                    textContent()
                }

                BvTabIconMode.TextIcon -> {
                    textContent()
                    if (showIcon) {
                        gap()
                        iconContent()
                    }
                }

                BvTabIconMode.FocusedIconText -> {
                    if (focused && showIcon) {
                        iconContent()
                        gap()
                    }
                    textContent()
                }

                BvTabIconMode.TextFocusedIcon -> {
                    textContent()
                    if (focused && showIcon) {
                        gap()
                        iconContent()
                    }
                }

                BvTabIconMode.IconOnly -> {
                    if (showIcon) {
                        iconContent()
                    }
                }

                BvTabIconMode.FocusedTextIcon -> {
                    if (focused) {
                        textContent()
                        if (showIcon) {
                            gap()
                        }
                    }
                    if (showIcon) {
                        iconContent()
                    }
                }

                BvTabIconMode.IconFocusedText -> {
                    if (showIcon) {
                        iconContent()
                    }
                    if (focused) {
                        if (showIcon) {
                            gap()
                        }
                        textContent()
                    }
                }

                BvTabIconMode.TextOnly -> {
                    textContent()
                }
            }
        }
    }
}

@Composable
private fun <T> RowScope.BvTabDefaultContent(
    item: T,
    text: (T) -> String,
    icon: (@Composable (T, Dp) -> Unit)?,
    hasIcon: (T) -> Boolean,
    iconMode: BvTabIconMode,
    focused: Boolean
) {
    BvTabLabel(
        text = text(item),
        icon = icon?.let { itemIcon -> { iconSize -> itemIcon(item, iconSize) } },
        showIcon = hasIcon(item),
        iconMode = iconMode,
        focused = focused
    )
}
