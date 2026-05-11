package dev.aaa1115910.bv.component

import androidx.compose.foundation.focusGroup
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.requestFocus
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

private val bvTabRowFocusLogger = KotlinLogging.logger("BvTabRowFocus")

/**
 * 项目统一的 TV TabRow 封装
 *
 * 新增 TV 横向 tab 时优先使用本组件，而不是直接使用 [TabRow]。如果样式固定，调用处优先使用
 * [BvUnderlineTabRow] 或 [BvPillTabRow] 等于 [BvTabRow]，避免在调用处直接传 [BvTabIndicator]。
 *
 * 该组件统一处理：
 * - TV 版 [TabRow]/[Tab] 的使用；
 * - [focusRestorer] 和 [focusGroup]，让焦点离开后回到 tab 组时优先恢复到组内焦点；
 * - 业务 item 到 TabRow 下标的映射；
 * - 默认焦点入口、默认焦点 ready 通知、上下跨区域跳转、左右边界退出、点击和长按；
 * - Pill/Underline 两种 indicator，以及 tab 间 separator
 *
 * 参数说明：
 * @param modifier 作用在 [TabRow] 外层的 modifier。通常用于 padding、宽高或父级布局约束
 * @param items 要展示的 tab item。为空时组件直接不渲染
 * @param selectedItem 当前业务选中的 item。组件会用 [itemKey] 把它映射成 `selectedTabIndex`
 * 如果 [selectedItem] 不在 [items] 中，会回退到第一个可见 tab
 * @param entryFocusItem 焦点进入该 TabRow 时希望落到的 item。组件会用 [itemKey] 把它映射成默认焦点目标
 * 如果 [entryFocusItem] 不在 [items] 中，会回退到 [selectedItem] 对应 tab；如果仍找不到，再回退到第一个可见 tab
 * 该参数用于“从侧栏/上层区域进入 tab 时落到指定项”，不等同于当前选中的内容
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
 * @param defaultFocusRequester 默认焦点入口。组件会把它挂到 [entryFocusItem] 解析出的目标 tab 上，
 * 如果不传，组件内部创建一个 [FocusRequester] 供 [focusRestorer] fallback 使用。
 * @param onDefaultFocusReady 默认焦点目标完成布局后回调。适合外部在收到该回调后调用 [defaultFocusRequester] 的
 * `requestFocus()`，避免目标节点尚未挂载时请求焦点失败。每个目标 key 只通知一次。
 * @param onSelectedChanged tab 获得焦点时触发。本组件由调用方 ViewModel 中的
 * `DebouncedActivationController` 负责默认 900ms debounce。
 * @param onClick tab 被点击、确认键激活时触发，立即回调，不经过 debounce。
 * @param onLongClick 确认键长按时触发。返回 true 表示已消费这次长按事件。
 * @param onLeftExit 第一个 tab 收到左方向键时触发。普通左右移动不拦截，交给 TV [TabRow] 自己处理。
 * 触发后组件固定消费该事件。
 * @param onRightExit 最后一个 tab 收到右方向键时触发。普通左右移动不拦截，交给 TV [TabRow] 自己处理。
 * 触发后组件固定消费该事件。
 * @param onUp 收到上方向键时触发。返回 true 表示已处理并消费；返回 false 或 null 时继续按 [blockUp] 判断
 * @param blockUp 当 [onUp] 未消费事件时，是否封锁上方向出口。顶层 [TopNav] 应使用 true
 * - [onUp] 高于 [blockUp]；只有 [onUp] 未消费时才检查 [blockUp]
 * @param onDown 收到下方向键时触发。返回 true 表示已处理并消费；返回 false 或 null 时继续按 [blockDown] 判断
 * @param blockDown 当 [onDown] 未消费事件时，是否封锁下方向出口
 * - [onDown] 高于 [blockDown]；只有 [onDown] 未消费时才检查 [blockDown]
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
    itemKey: (T) -> Any,
    itemText: (T) -> String = { it.toString() },
    itemIcon: (@Composable (T, Dp) -> Unit)? = null,
    itemHasIcon: (T) -> Boolean = { itemIcon != null },
    iconMode: BvTabIconMode = BvTabIconMode.IconText,
    indicator: BvTabIndicator = BvTabIndicator.Pill,
    retainIndicatorWhenFocusBelow: Boolean = true,
    separatorWidth: Dp = 12.dp,
    separator: (@Composable () -> Unit)? = null,
    defaultFocusRequester: FocusRequester? = null,
    onDefaultFocusReady: ((Any) -> Unit)? = null,
    onSelectedChanged: (T) -> Unit = {},
    onClick: (T) -> Unit = {},
    onConfirm: (T) -> Unit = onClick,
    onLongClick: ((T) -> Boolean)? = null,
    onLeftExit: (() -> Unit)? = null,
    onRightExit: (() -> Unit)? = null,
    onUp: ((T) -> Boolean)? = null,
    onDown: ((T) -> Boolean)? = null,
    contentFocusRequester: FocusRequester? = null,
    contentFocusReadyKey: Any? = null,
    onContentFocusRequested: (T) -> Unit = {},
    blockUp: Boolean = false,
    blockDown: Boolean = false,
    autoRequestEntryFocus: Boolean = true,
    tabContent: (@Composable RowScope.(item: T, selected: Boolean, focused: Boolean) -> Unit)? = null
) {
    if (items.isEmpty()) return

    val internalFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val entryFocusRequester = defaultFocusRequester ?: internalFocusRequester
    val itemKeys = remember(items) { items.map(itemKey) }
    val selectedItemKey = selectedItem?.let(itemKey)
    val entryFocusItemKey = entryFocusItem?.let(itemKey)

    val selectedTabIndex = itemKeys.indexOf(selectedItemKey).takeIf { it >= 0 } ?: 0
    val focusTargetIndex = itemKeys.indexOf(entryFocusItemKey).takeIf { it >= 0 } ?: selectedTabIndex

    var focusedTabIndex by remember(itemKeys) { mutableIntStateOf(focusTargetIndex) }
    var isFocusBelowTabRow by remember(itemKeys) { mutableStateOf(false) }
    var pendingContentFocusKey by remember(itemKeys) { mutableStateOf<Any?>(null) }
    var lastDefaultFocusPositionedState by remember(itemKeys) { mutableStateOf<String?>(null) }

    LaunchedEffect(autoRequestEntryFocus, entryFocusItemKey, focusTargetIndex) {
        if (!autoRequestEntryFocus) return@LaunchedEffect
        if (entryFocusItem == null) return@LaunchedEffect
        repeat(3) {
            withFrameNanos { }
            runCatching { entryFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(pendingContentFocusKey, contentFocusReadyKey, contentFocusRequester) {
        val pendingKey = pendingContentFocusKey ?: return@LaunchedEffect
        val requester = contentFocusRequester ?: run {
            pendingContentFocusKey = null
            return@LaunchedEffect
        }
        if (contentFocusReadyKey == null || contentFocusReadyKey == pendingKey) {
            requester.requestFocus(scope)
            pendingContentFocusKey = null
        }
    }

        TabRow(
            modifier = modifier
                .focusRestorer(entryFocusRequester)
                .focusGroup(),
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
                var confirmLongPressTriggered by remember(key) { mutableStateOf(false) }
                val focused = focusedTabIndex == index
                val selected = selectedTabIndex == index
                var tabModifier = Modifier
                    .focusProperties {
                        if (blockUp) up = FocusRequester.Cancel
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionLeft -> {
                                    if (index == 0 && onLeftExit != null) {
                                        onLeftExit()
                                        return@onPreviewKeyEvent true
                                    }
                                }

                                Key.DirectionRight -> {
                                    if (index == items.lastIndex && onRightExit != null) {
                                        onRightExit()
                                        return@onPreviewKeyEvent true
                                    }
                                }

                                Key.DirectionUp -> {
                                    if (onUp?.invoke(item) == true) return@onPreviewKeyEvent true
                                    if (blockUp) return@onPreviewKeyEvent true
                                }

                                Key.DirectionDown -> {
                                    if (onDown?.invoke(item) == true) {
                                        isFocusBelowTabRow = true
                                        return@onPreviewKeyEvent true
                                    }
                                    if (contentFocusRequester != null) {
                                        isFocusBelowTabRow = true
                                        pendingContentFocusKey = key
                                        onContentFocusRequested(item)
                                        return@onPreviewKeyEvent true
                                    }
                                    if (blockDown) return@onPreviewKeyEvent true
                                }

                                else -> Unit
                            }
                        }

                        val isConfirmKey =
                            event.key == Key.DirectionCenter ||
                                    event.key == Key.Enter ||
                                    event.key == Key.Spacebar
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

                if (index == focusTargetIndex) {
                    tabModifier = tabModifier
                        .focusRequester(entryFocusRequester)
                        .onGloballyPositioned {
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
    itemKey: (T) -> Any,
    itemText: (T) -> String = { it.toString() },
    itemIcon: (@Composable (T, Dp) -> Unit)? = null,
    itemHasIcon: (T) -> Boolean = { itemIcon != null },
    iconMode: BvTabIconMode = BvTabIconMode.IconText,
    retainIndicatorWhenFocusBelow: Boolean = true,
    separatorWidth: Dp = 12.dp,
    separator: (@Composable () -> Unit)? = null,
    defaultFocusRequester: FocusRequester? = null,
    onDefaultFocusReady: ((Any) -> Unit)? = null,
    onSelectedChanged: (T) -> Unit = {},
    onClick: (T) -> Unit = {},
    onConfirm: (T) -> Unit = onClick,
    onLongClick: ((T) -> Boolean)? = null,
    onLeftExit: (() -> Unit)? = null,
    onRightExit: (() -> Unit)? = null,
    onUp: ((T) -> Boolean)? = null,
    onDown: ((T) -> Boolean)? = null,
    contentFocusRequester: FocusRequester? = null,
    contentFocusReadyKey: Any? = null,
    onContentFocusRequested: (T) -> Unit = {},
    blockUp: Boolean = false,
    blockDown: Boolean = false,
    autoRequestEntryFocus: Boolean = true,
    tabContent: (@Composable RowScope.(item: T, selected: Boolean, focused: Boolean) -> Unit)? = null
) {
    BvTabRow(
        modifier = modifier,
        items = items,
        selectedItem = selectedItem,
        entryFocusItem = entryFocusItem,
        itemKey = itemKey,
        itemText = itemText,
        itemIcon = itemIcon,
        itemHasIcon = itemHasIcon,
        iconMode = iconMode,
        indicator = BvTabIndicator.Pill,
        retainIndicatorWhenFocusBelow = retainIndicatorWhenFocusBelow,
        separatorWidth = separatorWidth,
        separator = separator,
        defaultFocusRequester = defaultFocusRequester,
        onDefaultFocusReady = onDefaultFocusReady,
        onSelectedChanged = onSelectedChanged,
        onClick = onClick,
        onConfirm = onConfirm,
        onLongClick = onLongClick,
        onLeftExit = onLeftExit,
        onRightExit = onRightExit,
        onUp = onUp,
        onDown = onDown,
        contentFocusRequester = contentFocusRequester,
        contentFocusReadyKey = contentFocusReadyKey,
        onContentFocusRequested = onContentFocusRequested,
        blockUp = blockUp,
        blockDown = blockDown,
        autoRequestEntryFocus = autoRequestEntryFocus,
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
    itemKey: (T) -> Any,
    itemText: (T) -> String = { it.toString() },
    itemIcon: (@Composable (T, Dp) -> Unit)? = null,
    itemHasIcon: (T) -> Boolean = { itemIcon != null },
    iconMode: BvTabIconMode = BvTabIconMode.IconText,
    retainIndicatorWhenFocusBelow: Boolean = true,
    separatorWidth: Dp = 12.dp,
    separator: (@Composable () -> Unit)? = null,
    defaultFocusRequester: FocusRequester? = null,
    onDefaultFocusReady: ((Any) -> Unit)? = null,
    onSelectedChanged: (T) -> Unit = {},
    onClick: (T) -> Unit = {},
    onConfirm: (T) -> Unit = onClick,
    onLongClick: ((T) -> Boolean)? = null,
    onLeftExit: (() -> Unit)? = null,
    onRightExit: (() -> Unit)? = null,
    onUp: ((T) -> Boolean)? = null,
    onDown: ((T) -> Boolean)? = null,
    contentFocusRequester: FocusRequester? = null,
    contentFocusReadyKey: Any? = null,
    onContentFocusRequested: (T) -> Unit = {},
    blockUp: Boolean = false,
    blockDown: Boolean = false,
    autoRequestEntryFocus: Boolean = true,
    tabContent: (@Composable RowScope.(item: T, selected: Boolean, focused: Boolean) -> Unit)? = null
) {
    BvTabRow(
        modifier = modifier,
        items = items,
        selectedItem = selectedItem,
        entryFocusItem = entryFocusItem,
        itemKey = itemKey,
        itemText = itemText,
        itemIcon = itemIcon,
        itemHasIcon = itemHasIcon,
        iconMode = iconMode,
        indicator = BvTabIndicator.Underline,
        retainIndicatorWhenFocusBelow = retainIndicatorWhenFocusBelow,
        separatorWidth = separatorWidth,
        separator = separator,
        defaultFocusRequester = defaultFocusRequester,
        onDefaultFocusReady = onDefaultFocusReady,
        onSelectedChanged = onSelectedChanged,
        onClick = onClick,
        onConfirm = onConfirm,
        onLongClick = onLongClick,
        onLeftExit = onLeftExit,
        onRightExit = onRightExit,
        onUp = onUp,
        onDown = onDown,
        contentFocusRequester = contentFocusRequester,
        contentFocusReadyKey = contentFocusReadyKey,
        onContentFocusRequested = onContentFocusRequested,
        blockUp = blockUp,
        blockDown = blockDown,
        autoRequestEntryFocus = autoRequestEntryFocus,
        tabContent = tabContent
    )
}

/**
 * 用于设置页配置 BvTabRow 的可见项和显示顺序。
 *
 * 该 Dialog 内部复用 OrderedMultiSelectDialog，关闭时输出已经按用户选择顺序排序后的 ID 列表。
 *
 * 数据流：
 * 1. allItems 是完整 tab 列表。
 * 2. enabledOrderedIds 是当前启用且已经排好序的 tab ID 列表。
 * 3. 用户提交后，onSubmit 返回新的启用有序 ID 列表。
 * 4. 页面保存这个 ID 列表，并按它过滤、排序 allItems。
 * 5. 页面把过滤排序后的 items 传给 BvTabRow。
 *
 * itemId 必须和 BvTabRow 的 `itemKey` 表达同一个稳定身份，例如业务 code、枚举名或服务端 ID。
 * 不要使用列表下标作为 ID。下标会随着过滤、插入、删除或排序变化而改变，无法稳定还原
 */
@Composable
fun <T, ID : Any> BvTabOrderDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    allItems: List<T>,
    enabledOrderedIds: List<ID>,
    itemId: (T) -> ID,
    onSubmit: (List<ID>) -> Unit,
    text: (T) -> String,
    itemKey: ((T) -> Any)? = null,
    defaultFocusKey: Any? = enabledOrderedIds.firstOrNull(),
    defaultFocusIndex: Int? = null
) {
    OrderedMultiSelectDialog(
        visible = visible,
        onDismissRequest = onDismissRequest,
        title = title,
        items = allItems,
        initialSelectedIds = enabledOrderedIds,
        itemId = itemId,
        onSubmit = onSubmit,
        text = text,
        itemKey = itemKey ?: { itemId(it) },
        defaultFocusKey = defaultFocusKey,
        defaultFocusIndex = defaultFocusIndex
    )
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
