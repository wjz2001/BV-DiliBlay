package dev.aaa1115910.bv.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.FavoriteFolderMetadata
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.block.BlockPage
import dev.aaa1115910.bv.ui.theme.C
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.Border

private const val DialogAutoFocusRetryCount = 20
private const val DialogAutoFocusRetryDelayMillis = 50L

/**
 * 简单多选弹框的“提交时机”：
 *
 * OnEachClick:
 * - 每次点击 chip 后立刻回调 onSubmit
 * - 适合“收藏夹多选框”这种交互
 *
 * OnDismiss:
 * - 点击时只改弹框内部临时状态
 * - 等用户关闭弹框时，再统一回调 onSubmit
 * - 适合“黑名单生效页多选框”“关注分组多选框”
 */
private enum class SubmitMode {
    OnEachClick,
    OnDismiss
}

/**
 * 当 submitMode = OnDismiss 时，提交与关闭的先后顺序：
 *
 * SubmitThenHide:
 * - 先提交数据，再关闭面板
 *
 * HideThenSubmit:
 * - 先关闭面板，再提交数据
 * - 关注分组轻量模式里原本就是这种需求
 */
private enum class DismissOrder {
    SubmitThenHide,
    HideThenSubmit
}

/**
 * 用新的内容替换 SnapshotStateList。
 *
 * 作用：
 * - 比如 dialog 打开时，用最新的初始选中项覆盖旧状态
 * - 比直接 clear + addAll 更语义化一点
 */
private fun <T> SnapshotStateList<T>.replaceWith(items: Collection<T>) {
    clear()
    addAll(items)
}

/**
 * 最基础的“多选 Chip 配色”。
 *
 * 这个函数只负责公共 UI：
 * - AlertDialog
 * - title
 * - FlowRow
 * - 最大高度
 * - 滚动
 * - 间距与 padding
 *
 * 所有 Dialog/Chip 都走这里，未来新增 Dialog 只要复用 SimpleMultiSelectDialog，
 * 就天然统一视觉与交互状态（focused/pressed/disabled/selected）。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun multiSelectChipColors() = FilterChipDefaults.colors(
    // 默认（未聚焦/未按压/未选中）
    containerColor = Color.Transparent,
    contentColor = Color.Transparent,

    // focused = TV 上的“激活/高亮”
    focusedContainerColor = C.primary,
    focusedContentColor = C.onPrimary,

    // pressed
    pressedContainerColor = C.primaryContainer,
    pressedContentColor = C.onPrimaryContainer,

    // selected（未聚焦）
    selectedContainerColor = C.secondary,
    selectedContentColor = C.onSecondary,

    // disabled
    disabledContainerColor = C.surfaceVariant,
    disabledContentColor = C.disabled,

    // focused + selected
    focusedSelectedContainerColor = C.primary,
    focusedSelectedContentColor = C.onPrimary,

    // pressed + selected
    pressedSelectedContainerColor = C.primaryContainer,
    pressedSelectedContentColor = C.onPrimaryContainer
)

/**
 * 最基础的“多选弹框壳”（统一风格：直接用 C）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BaseMultiSelectDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    if (!show) return

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        confirmButton = {},
        containerColor = C.surface,
        title = {
            M3Text(
                text = title,
                color = C.onSurface
            )
        },
        text = {
            FlowRow(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    )
}

/**
 * 最基础的“多选 Chip 壳”。
 *
 * 这个函数只负责公共 UI：
 * - FilterChip
 * - 是否选中
 * - 点击回调
 * - 左侧 Done 图标
 *
 * 业务方只需要关心：
 * - 当前是不是选中
 * - 点击时怎么改状态
 * - Chip 内部文字怎么展示
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BaseMultiSelectChip(
    modifier: Modifier = Modifier,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    FilterChip(
        modifier = modifier,
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        shape = FilterChipDefaults.shape(shape = RectangleShape),
        border = filterChipBorder(
            normalColor = C.inverseSurface,
            focusedColor = Color.Transparent
        ),
        colors = multiSelectChipColors(),
        leadingIcon = {
            Row {
                AnimatedVisibility(visible = selected) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Rounded.Done,
                        contentDescription = null
                    )
                }
            }
        }
    ) {
        content()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun filterChipBorder(
    normalColor: Color,
    focusedColor: Color,
    width: Dp = 1.dp
) = run {
    val shape = RectangleShape
    val normalBorder = remember(normalColor, width) {
        Border(
            border = BorderStroke(width, normalColor),
            inset = 0.dp,
            shape = shape
        )
    }

    val focusedBorder = remember(focusedColor) {
        Border(
            border = BorderStroke(0.dp, focusedColor),
            inset = 0.dp,
            shape = shape
        )
    }

    FilterChipDefaults.border(
        border = normalBorder,
        focusedBorder = focusedBorder,
        selectedBorder = normalBorder,
        disabledBorder = normalBorder,
        focusedSelectedBorder = focusedBorder,
        focusedDisabledBorder = focusedBorder,
        pressedSelectedBorder = focusedBorder,
        selectedDisabledBorder = normalBorder,
        focusedSelectedDisabledBorder = focusedBorder
    )
}


/**
 * 通用“简单多选弹框”。
 *
 * 适合那些逻辑比较轻的场景：
 * - 收藏夹多选框
 * - 黑名单生效页多选框
 * - 关注分组多选框
 *
 * 这些场景共同特点：
 * - 没有网络拉取
 * - 没有队列
 * - 没有 loading
 * - 没有 stale cache
 * - 只是在一个列表里做选中/取消选中
 *
 * 为了适配不同业务，这里提供了几个可配置点：
 * - itemId: 如何取每个 item 的唯一 id
 * - submitMode: 点击即提交 / dismiss 时提交
 * - dismissOrder: 关闭与提交的顺序
 * - onToggle: 点击某个 item 后怎么修改 selectedIds
 *   默认是普通多选；如果业务有特殊规则，可自定义
 */
@Composable
private fun <T, ID> SimpleMultiSelectDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    title: String,
    items: List<T>,
    initialSelectedIds: List<ID>,
    itemId: (T) -> ID,
    onHideDialog: () -> Unit,
    onSubmit: (List<ID>) -> Unit,
    submitMode: SubmitMode,
    dismissOrder: DismissOrder = DismissOrder.SubmitThenHide,
    itemEnabled: (T) -> Boolean = { true },
    onToggle: SnapshotStateList<ID>.(id: ID, selected: Boolean, item: T) -> Unit = { id, selected, _ ->
        if (selected) remove(id) else add(id)
    },
    itemContent: @Composable (T) -> Unit
) {
    // 弹框内部维护一份“临时选中状态”
    // 注意：这份状态只存在于 dialog 内部
    val selectedIds = remember { mutableStateListOf<ID>() }

    // 默认给第一个 item 聚焦，适合 TV 场景
    val defaultFocusRequester = remember { FocusRequester() }
    val defaultFocusItemKey = items.firstOrNull { itemEnabled(it) }?.let(itemId)
    val didDefaultItemReceiveFocus = remember { mutableStateOf(false) }

    LaunchedEffect(show, defaultFocusItemKey) {
        didDefaultItemReceiveFocus.value = false
    }

    DialogDefaultItemAutoFocus(
        show = show,
        defaultFocusItemKey = defaultFocusItemKey,
        didDefaultItemReceiveFocus = didDefaultItemReceiveFocus.value,
        focusRequester = defaultFocusRequester
    )

    LaunchedEffect(show, submitMode) {
        if (show && submitMode == SubmitMode.OnDismiss) {
            selectedIds.replaceWith(initialSelectedIds)
        }
    }

    LaunchedEffect(show, submitMode, items, initialSelectedIds) {
        if (show && submitMode == SubmitMode.OnEachClick) {
            selectedIds.replaceWith(initialSelectedIds)
        }
    }

    BaseMultiSelectDialog(
        modifier = modifier,
        show = show,
        title = title,
        onDismissRequest = {
            when (submitMode) {
                SubmitMode.OnEachClick -> onHideDialog()
                SubmitMode.OnDismiss -> {
                    when (dismissOrder) {
                        DismissOrder.SubmitThenHide -> {
                            onSubmit(selectedIds.toList())
                            onHideDialog()
                        }

                        DismissOrder.HideThenSubmit -> {
                            onHideDialog()
                            onSubmit(selectedIds.toList())
                        }
                    }
                }
            }
        }
    ) {
        items.forEach { item ->
            val id = itemId(item)
            val selected = selectedIds.contains(id)
            val enabled = itemEnabled(item)
            val isDefaultFocusItem = id == defaultFocusItemKey

            val itemModifier = if (isDefaultFocusItem) {
                Modifier
                    .focusRequester(defaultFocusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.hasFocus) {
                            didDefaultItemReceiveFocus.value = true
                        }
                    }
            } else {
                Modifier
            }

            BaseMultiSelectChip(
                modifier = itemModifier,
                selected = selected,
                enabled = enabled,
                onClick = {
                    if (!enabled) return@BaseMultiSelectChip

                    selectedIds.onToggle(id, selected, item)

                    // 如果是“点击即提交”，则每次点击都立刻把结果回调出去
                    if (submitMode == SubmitMode.OnEachClick) {
                        onSubmit(selectedIds.toList())
                    }
                }
            ) {
                itemContent(item)
            }
        }
    }
}

/**
 * 弹窗默认焦点兜底：
 *
 * 1. 焦点目标不是“第一个 item”，而是“第一个 enabled item”
 * 2. 打开弹窗后会持续重试 requestFocus
 * 3. 只有目标 item 真的拿到焦点，才停止重试
 *
 * 这样能覆盖：
 * - 先显示弹窗、后到数据
 * - Dialog 刚创建时节点还没挂稳
 * - 底层已有焦点持有者时，首次 requestFocus 被吞掉
 */
@Composable
private fun <K> DialogDefaultItemAutoFocus(
    show: Boolean,
    defaultFocusItemKey: K?,
    didDefaultItemReceiveFocus: Boolean,
    focusRequester: FocusRequester
) {
    LaunchedEffect(show, defaultFocusItemKey, didDefaultItemReceiveFocus) {
        if (!show || defaultFocusItemKey == null || didDefaultItemReceiveFocus) return@LaunchedEffect

        repeat(DialogAutoFocusRetryCount) {
            runCatching { focusRequester.requestFocus() }
            delay(DialogAutoFocusRetryDelayMillis)
        }
    }
}

/**
 * 收藏夹多选框：点击即提交。
 */
@Composable
internal fun FavoriteDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onHideDialog: () -> Unit,
    userFavoriteFolders: List<FavoriteFolderMetadata> = emptyList(),
    favoriteFolderIds: List<Long> = emptyList(),
    onUpdateFavoriteFolders: (List<Long>) -> Unit
) {
    SimpleMultiSelectDialog(
        modifier = modifier,
        show = show,
        title = stringResource(R.string.favorite_dialog_title),
        items = userFavoriteFolders,
        initialSelectedIds = favoriteFolderIds,
        itemId = { it.id },
        onHideDialog = onHideDialog,
        onSubmit = onUpdateFavoriteFolders,
        submitMode = SubmitMode.OnEachClick
    ) { folder ->
        Text(text = folder.title)
    }
}

/**
 * 黑名单生效页多选框：
 * - 点击 chip 时只改 dialog 内部临时状态
 * - 真正提交发生在 dismiss 时
 *
 * 这是“简单多选 + dismiss 时提交”场景。
 */
@Composable
internal fun BlockPageSelectDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    title: String,
    allPages: List<BlockPage> = BlockPage.entries,
    initialSelectedPages: List<BlockPage> = emptyList(),
    onHideDialog: () -> Unit,
    onSubmit: (List<BlockPage>) -> Unit
) {
    SimpleMultiSelectDialog(
        modifier = modifier,
        show = show,
        title = title,
        items = allPages,
        initialSelectedIds = initialSelectedPages,
        itemId = { it },
        onHideDialog = onHideDialog,
        onSubmit = onSubmit,
        submitMode = SubmitMode.OnDismiss,
        dismissOrder = DismissOrder.SubmitThenHide
    ) { page ->
        Text(text = page.displayName)
    }
}

/**
 * 关注分组多选框：
 * - 0（默认/未分组）和其它分组互斥
 * - dismiss 时：先关闭弹框，再提交结果
 */
@Composable
internal fun FollowGroupSelectDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    title: String,
    tags: List<BlockTagItem>,
    initialSelectedTagIds: List<Int>,
    onHideDialog: () -> Unit,
    onSubmit: (List<Int>) -> Unit
) {
    // 进入 dialog 时做一次标准化：
    // 如果初始数据里同时含有 0 和其它项，只保留 0
    val normalizedInitialSelectedTagIds =
        if (initialSelectedTagIds.contains(0)) listOf(0) else initialSelectedTagIds

    SimpleMultiSelectDialog(
        modifier = modifier,
        show = show,
        title = title,
        items = tags,
        initialSelectedIds = normalizedInitialSelectedTagIds,
        itemId = { it.tagid },
        onHideDialog = onHideDialog,
        onSubmit = { ids -> onSubmit(ids.sorted()) },
        submitMode = SubmitMode.OnDismiss,
        dismissOrder = DismissOrder.HideThenSubmit,
        onToggle = { id, selected, _ ->
            if (selected) {
                // 已选中：点击则取消
                remove(id)
            } else {
                // 未选中：根据 0 与其它互斥规则处理
                if (id == 0) {
                    clear()
                    add(0)
                } else {
                    remove(0)
                    if (!contains(id)) add(id)
                }
            }
        }
    ) { tag ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = tag.name)
            Text(text = "${tag.count}")
        }
    }
}

/**
 * 黑名单分组多选框：dismiss 时统一提交。
 */
@Composable
internal fun BlockGroupSelectDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    title: String,
    tags: List<BlockTagItem>,
    initialSelectedTagIds: List<Int>,
    onHideDialog: () -> Unit,
    onSubmit: (selectedTagIds: List<Int>) -> Unit
) {
    SimpleMultiSelectDialog(
        modifier = modifier,
        show = show,
        title = title,
        items = tags,
        initialSelectedIds = initialSelectedTagIds.distinct(),
        itemId = { it.tagid },
        onHideDialog = onHideDialog,
        onSubmit = { ids -> onSubmit(ids.distinct().sorted()) },
        submitMode = SubmitMode.OnDismiss,
        dismissOrder = DismissOrder.SubmitThenHide
    ) { tag ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = tag.name)
            Text(text = "${tag.count}")
        }
    }
}

/**
 * 黑名单/关注分组的基础数据。
 */
data class BlockTagItem(
    val tagid: Int,
    val name: String,
    val count: Int
)