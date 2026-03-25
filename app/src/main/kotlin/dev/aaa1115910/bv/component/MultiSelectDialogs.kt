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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.FavoriteFolderMetadata
import dev.aaa1115910.biliapi.http.BiliHttpApi
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.block.BlockPage
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
 * 最基础的“多选弹框壳”。
 *
 * 这个函数只负责公共 UI：
 * - AlertDialog
 * - title
 * - FlowRow
 * - 最大高度
 * - 滚动
 * - 间距与 padding
 *
 * 它不关心业务：
 * - 不关心选中了什么
 * - 不关心点击后怎么处理
 * - 不关心 dismiss 时提交什么
 *
 * 所有业务逻辑都由调用方自己实现。
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
        title = { Text(text = title) },
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
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    FilterChip(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
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

/**
 * 通用“简单多选弹框”。
 *
 * 它适合那些逻辑比较轻的场景：
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

    /**
     * 当 dialog 打开时，用外部传进来的初始选中项重置内部状态。
     *
     * 为什么用 LaunchedEffect(show, items, initialSelectedIds)：
     * - show 从 false -> true 时，需要重置
     * - items / initialSelectedIds 变化时，也希望同步最新状态
     */
    LaunchedEffect(show, items, initialSelectedIds) {
        if (show) {
            selectedIds.replaceWith(initialSelectedIds)
            if (items.isNotEmpty()) {
                defaultFocusRequester.requestFocus()
            }
        }
    }

    BaseMultiSelectDialog(
        modifier = modifier,
        show = show,
        title = title,
        onDismissRequest = {
            when (submitMode) {
                // 点击时已经提交过了，关闭时只负责隐藏
                SubmitMode.OnEachClick -> {
                    onHideDialog()
                }

                // 关闭时再统一提交
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
        items.forEachIndexed { index, item ->
            val id = itemId(item)
            val selected = selectedIds.contains(id)

            // 第一个 item 默认拿焦点
            val itemModifier =
                if (index == 0) Modifier.focusRequester(defaultFocusRequester) else Modifier

            BaseMultiSelectChip(
                modifier = itemModifier,
                selected = selected,
                onClick = {
                    // 点击后的选中逻辑由 onToggle 决定
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
 * 收藏夹多选框：
 * - 点击一个收藏夹后，立刻把当前选中收藏夹列表回调给外部
 * - 关闭 dialog 时不再额外提交
 *
 * 这是最典型的“简单多选 + 点击即提交”场景。
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
 *
 * 这是一个独立的轻量业务，和黑名单分组选择已经彻底分开。
 *
 * 规则：
 * 1. 只做本地选中/取消选中
 * 2. 不拉取成员，不跑队列，不读写成员缓存
 * 3. 0（默认/未分组）和其它分组互斥
 * 4. dismiss 时：先关闭弹框，再把结果提交给外部
 *
 * 为什么也用 SimpleMultiSelectDialog：
 * - 因为它本质仍然只是“简单多选”
 * - 唯一特殊点只是 onToggle 规则不同
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
 * 黑名单分组多选框（重逻辑版）：
 *
 * 这是四个弹框里最复杂的一个，因此没有走 SimpleMultiSelectDialog，
 * 而是只复用了最底层的 BaseMultiSelectDialog / BaseMultiSelectChip。
 *
 * 主要逻辑：
 * 1. 若本地已有旧 mids：
 *    - 直接选中
 *    - 如果 count 不一致，显示“需更新”
 *
 * 2. 若本地没有 mids：
 *    - 点击后进入队列
 *    - 串行拉取成员
 *    - 完成后才真正选中
 *
 * 3. dialog 关闭时：
 *    - 立即停止拉取
 *    - 丢弃未完成数据
 *    - 只提交最终 selectedTagIds
 *    - 只提交本次 dialog 成功拉取完成的 upserts
 *
 * 4. 防止“等同全量分组”：
 *    - 当 tags.size >= 2 且已选数 == 总数 - 1 时
 *    - 剩余最后一个未选分组禁止获得焦点，也禁止被选中
 */
@Composable
internal fun BlockGroupSelectDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    title: String,
    tags: List<BlockTagItem>,
    initialSelectedTagIds: List<Int>,
    initialMembersCache: Map<Int, CachedMembers>,
    onHideDialog: () -> Unit,
    onSubmit: (selectedTagIds: List<Int>, newOrUpdatedCache: Map<Int, CachedMembers>) -> Unit
) {
    if (!show) return

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    /**
     * 只有用户发生过真实点击后，才允许弹 toast 提示。
     * 否则会出现“刚打开 dialog 什么都没做就提示”的干扰。
     */
    var userInteracted by remember { mutableStateOf(false) }

    /**
     * 当前 dialog 内部真正“已选中”的 tagId 列表。
     *
     * 对于黑名单重逻辑来说：
     * - 已经 Selected / SelectedStale 的项，才算 selected
     * - Queued / Loading 不算真正选中
     */
    val selected = remember { mutableStateListOf<Int>() }

    /**
     * 记录“用户意图”：
     * - true 代表用户希望该分组最终是选中状态
     * - false 代表用户希望它不选中
     *
     * 之所以需要它，是因为队列/拉取过程中可能发生状态变化，
     * 需要区分“当前 UI 状态”和“用户最终意图”。
     */
    val desired = remember { mutableStateMapOf<Int, Boolean>() }

    /**
     * 当已选数达到 total - 1 时，提示用户不能再全选。
     *
     * 注意这里只负责 toast 提示，
     * 真正的“禁止聚焦/禁止点击”逻辑在后面。
     */
    LaunchedEffect(userInteracted, selected.size, tags.size) {
        if (!userInteracted) return@LaunchedEffect
        if (tags.size >= 2 && selected.size == tags.size - 1) {
            "禁止全选关注分组".toast(context)
        }
    }

    /**
     * 每个 tag 当前的 UI 状态：
     * - Idle
     * - Queued
     * - Loading
     * - Selected
     * - SelectedStale
     */
    val uiState = remember { mutableStateMapOf<Int, TagUiState>() }

    /**
     * 待拉取分组队列。
     * 只有没有可用缓存、且用户点击选中时，才会入队。
     */
    val queue = remember { ArrayDeque<Int>() }

    /**
     * 串行 worker job。
     * 保证同一时刻只有一个分组在拉取，避免并发请求太多。
     */
    var workerJob by remember { mutableStateOf<Job?>(null) }

    /**
     * 本次 dialog 内成功拉取完成的缓存。
     * 只记录“完整成功”的数据。
     * 未完成/取消/失败的数据都不会进入这里。
     */
    val fetchedCache = remember { mutableStateMapOf<Int, CachedMembers>() }

    /**
     * 每个 tag 一个 FocusRequester。
     * 用来在“最后一个未选中项禁止聚焦”时，主动把焦点挪走。
     */
    val focusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    fun requesterFor(tagId: Int): FocusRequester =
        focusRequesters.getOrPut(tagId) { FocusRequester() }

    /**
     * 当前焦点落在哪个 tag 上。
     * 用于配合“最后一个未选项禁止聚焦”的逻辑。
     */
    var focusedTagId by remember { mutableStateOf<Int?>(null) }

    /**
     * 是否应该阻止最后一个未选中项获取焦点。
     *
     * 条件：
     * - tags 至少 2 个
     * - 当前已选数 == 总数 - 1
     */
    fun shouldBlockLastUnselectedFocus(): Boolean =
        tags.size >= 2 && selected.size == tags.size - 1

    /**
     * 找出“当前唯一剩下的未选中 tagId”。
     * 仅在 shouldBlockLastUnselectedFocus() 为 true 时才有意义。
     */
    fun remainingUnselectedTagId(): Int? {
        if (!shouldBlockLastUnselectedFocus()) return null
        return tags.firstOrNull { !selected.contains(it.tagid) }?.tagid
    }

    /**
     * 把焦点移动到任意一个可聚焦项上。
     *
     * 场景：
     * - 当前焦点正好落在“最后一个已被禁止的未选项”上
     * - 这时要手动把焦点挪到别的可聚焦项
     */
    fun requestFocusToAnyFocusable() {
        if (tags.isEmpty()) return
        val blocked = remainingUnselectedTagId()
        val targetId = tags.firstOrNull { it.tagid != blocked }?.tagid ?: return
        requesterFor(targetId).requestFocus()
    }

    /**
     * dialog 打开时初始化内部状态。
     *
     * 规则：
     * - initialSelectedTagIds 中的项必须保留为“已选”
     * - 是否是 Selected 还是 SelectedStale，取决于缓存是否可用、是否新鲜
     */
    LaunchedEffect(show) {
        selected.clear()
        desired.clear()
        uiState.clear()
        queue.clear()
        workerJob?.cancel()
        workerJob = null
        fetchedCache.clear()

        tags.forEach { tag ->
            val tagId = tag.tagid
            val isInitiallySelected = initialSelectedTagIds.contains(tagId)
            val cached = initialMembersCache[tagId]
            val cacheUsable = cached != null && cached.mids.isNotEmpty()
            val cacheFresh = cacheUsable && cached!!.count == tag.count

            if (isInitiallySelected) {
                selected.add(tagId)
                desired[tagId] = true
                uiState[tagId] = if (cacheFresh) TagUiState.Selected else TagUiState.SelectedStale
            } else {
                desired[tagId] = false
                uiState[tagId] = TagUiState.Idle
            }
        }

        requestFocusToAnyFocusable()
    }

    /**
     * 若当前焦点正好落在“最后一个不允许聚焦的未选项”上，
     * 则在选中数量变化后，主动把焦点挪走。
     */
    LaunchedEffect(selected.size) {
        val blocked = remainingUnselectedTagId()
        if (blocked != null && focusedTagId == blocked) {
            requestFocusToAnyFocusable()
        }
    }

    /**
     * 若当前没有 worker 在跑，则启动一个。
     *
     * worker 的核心职责：
     * - 从 queue 里依次取出 tagId
     * - 串行请求成员列表
     * - 成功后写入 fetchedCache
     * - 然后把该项变成 Selected
     */
    fun startWorkerIfNeeded() {
        if (workerJob?.isActive == true) return

        workerJob = scope.launch {
            while (isActive && queue.isNotEmpty()) {
                val tagId = queue.removeFirst()
                val tag = tags.firstOrNull { it.tagid == tagId } ?: continue

                // 如果用户在排队期间已经取消了意图，则直接丢弃
                if (desired[tagId] != true) {
                    uiState[tagId] = TagUiState.Idle
                    continue
                }

                uiState[tagId] = TagUiState.Loading(fetched = 0)

                val mids = mutableListOf<Long>()
                val ps = 50
                var pn = 1
                var ok = true

                while (isActive) {
                    val pageMembers = try {
                        BiliHttpApi.getRelationTagMembers(
                            tagId = tagId,
                            pageNumber = pn,
                            pageSize = ps,
                            orderType = null,
                            accessKey = Prefs.accessToken.takeIf { it.isNotBlank() },
                            sessData = Prefs.sessData.takeIf { it.isNotBlank() }
                        ).getResponseData()
                    } catch (e: CancellationException) {
                        // 协程取消要继续向外抛，不能吞掉
                        throw e
                    } catch (_: Throwable) {
                        // 其它异常认为本次拉取失败
                        ok = false
                        break
                    }

                    if (pageMembers.isEmpty()) break

                    mids.addAll(pageMembers.map { it.mid })
                    uiState[tagId] = TagUiState.Loading(fetched = mids.size)

                    // 最后一页：返回数量小于 pageSize
                    if (pageMembers.size < ps) break

                    pn++
                    delay(700) // 固定延迟，避免风控/限流
                }

                // 拉取失败或 dialog 关闭导致 isActive=false，则丢弃本次结果
                if (!ok || !isActive) {
                    uiState[tagId] = TagUiState.Idle
                    desired[tagId] = false
                    continue
                }

                // 拉取完整成功，先写入本次 dialog 的缓存
                fetchedCache[tagId] = CachedMembers(
                    count = tag.count,
                    mids = mids
                )

                // 双保险：如果此时已经到了“不能再选最后一个”的状态，
                // 则不要自动把它选上
                if (shouldBlockLastUnselectedFocus() && !selected.contains(tagId)) {
                    uiState[tagId] = TagUiState.Idle
                    desired[tagId] = false
                    continue
                }

                if (!selected.contains(tagId)) {
                    selected.add(tagId)
                }
                desired[tagId] = true
                uiState[tagId] = TagUiState.Selected
            }
        }
    }

    /**
     * 将某个 tag 放入队列。
     * 若已经在队列中，则不重复入队。
     */
    fun enqueue(tagId: Int) {
        if (queue.contains(tagId)) return
        queue.addLast(tagId)
        startWorkerIfNeeded()
    }

    /**
     * dismiss 时统一提交：
     *
     * 1. 立即停止拉取
     * 2. 丢弃所有未完成项（Queued / Loading）
     * 3. 只提交最终 selectedTagIds
     * 4. 只提交本次 dialog 拉取成功、且最终仍被选中的 upserts
     */
    fun dismissAndSubmit() {
        workerJob?.cancel()
        workerJob = null

        tags.forEach { tag ->
            when (uiState[tag.tagid]) {
                is TagUiState.Queued,
                is TagUiState.Loading -> {
                    uiState[tag.tagid] = TagUiState.Idle
                    desired[tag.tagid] = false
                }

                else -> Unit
            }
        }
        queue.clear()

        val finalSelected = selected.toList().sorted()
        val upserts = fetchedCache
            .filter { (tagId, _) -> finalSelected.contains(tagId) }
            .toMap()

        onSubmit(finalSelected, upserts)
        onHideDialog()
    }

    BaseMultiSelectDialog(
        modifier = modifier,
        show = show,
        title = title,
        onDismissRequest = { dismissAndSubmit() }
    ) {
        tags.forEach { tag ->
            val state = uiState[tag.tagid] ?: TagUiState.Idle
            val isSelected = state == TagUiState.Selected || state == TagUiState.SelectedStale

            // 右侧显示文字：
            // - Idle / Selected: count
            // - SelectedStale: count + 需更新
            // - Queued / Loading: 进度
            val trailingText = when (state) {
                TagUiState.Idle -> "${tag.count}"
                TagUiState.Selected -> "${tag.count}"
                TagUiState.SelectedStale -> "${tag.count} 需更新"
                TagUiState.Queued -> "0/${tag.count}"
                is TagUiState.Loading -> "${state.fetched.coerceAtMost(tag.count)}/${tag.count}"
            }

            // 若当前正处于“最后一个未选项禁止聚焦”状态，
            // 则唯一未选中的那个 item 不允许获取焦点
            val blockFocus = shouldBlockLastUnselectedFocus() && !isSelected

            BaseMultiSelectChip(
                modifier = Modifier
                    .focusRequester(requesterFor(tag.tagid))
                    .focusProperties { canFocus = !blockFocus }
                    .onFocusChanged {
                        if (it.hasFocus) focusedTagId = tag.tagid
                    },
                selected = isSelected,
                onClick = {
                    userInteracted = true

                    val isSelectedState =
                        state == TagUiState.Selected || state == TagUiState.SelectedStale

                    // 若已经达到 total - 1，再点最后一个未选项会变成“等同全量”，因此禁止
                    if (tags.size >= 2 && selected.size >= tags.size - 1 && !isSelectedState) {
                        "禁止全选关注分组".toast(context)
                        return@BaseMultiSelectChip
                    }

                    // 双保险：即使某些场景下点击事件仍然到了这里，也直接拦住
                    if (shouldBlockLastUnselectedFocus() && !isSelected) {
                        return@BaseMultiSelectChip
                    }

                    // 缓存优先级：
                    // 本次 dialog 成功拉取的 fetchedCache > 进入 dialog 时的初始缓存
                    val cached = fetchedCache[tag.tagid] ?: initialMembersCache[tag.tagid]
                    val cacheUsable = cached != null && cached.mids.isNotEmpty()
                    val cacheFresh = cacheUsable && cached!!.count == tag.count

                    when (state) {
                        // 已选：允许取消
                        TagUiState.Selected,
                        TagUiState.SelectedStale -> {
                            selected.remove(tag.tagid)
                            desired[tag.tagid] = false
                            uiState[tag.tagid] = TagUiState.Idle
                        }

                        // 排队中 / 拉取中：当前最小实现是不响应二次点击
                        TagUiState.Queued,
                        is TagUiState.Loading -> Unit

                        // 未选：
                        // - 有可用缓存则秒选中
                        // - 否则进入队列等待串行拉取
                        TagUiState.Idle -> {
                            desired[tag.tagid] = true
                            if (cacheUsable) {
                                if (!selected.contains(tag.tagid)) {
                                    selected.add(tag.tagid)
                                }
                                uiState[tag.tagid] =
                                    if (cacheFresh) TagUiState.Selected else TagUiState.SelectedStale
                            } else {
                                uiState[tag.tagid] = TagUiState.Queued
                                enqueue(tag.tagid)
                            }
                        }
                    }
                }
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = tag.name)
                    Text(text = trailingText)
                }
            }
        }
    }
}

/**
 * 黑名单/关注分组的基础数据。
 *
 * tagid:
 * - 分组 id
 * name:
 * - 分组名称
 * count:
 * - 该分组成员数
 */
data class BlockTagItem(
    val tagid: Int,
    val name: String,
    val count: Int
)

/**
 * 本地缓存的成员数据。
 *
 * count:
 * - 拉取缓存时的成员数快照
 * mids:
 * - 当时拉下来的成员 mid 列表
 *
 * 用途：
 * - 如果当前 tag.count 与缓存 count 一致，则缓存可视为“新鲜”
 * - 若不一致，则说明缓存可能过期，UI 上显示“需更新”
 */
data class CachedMembers(
    val count: Int,
    val mids: List<Long>
)

/**
 * 黑名单分组弹框内部的 UI 状态。
 *
 * Idle:
 * - 未选中，也未排队
 *
 * Queued:
 * - 已加入拉取队列，但尚未开始拉取
 *
 * Loading:
 * - 正在拉取成员，fetched 表示当前已拉取多少
 *
 * Selected:
 * - 已选中，且缓存是新鲜的
 *
 * SelectedStale:
 * - 已选中，但缓存可能过期，需要更新
 */
private sealed interface TagUiState {
    data object Idle : TagUiState
    data object Queued : TagUiState
    data class Loading(val fetched: Int) : TagUiState
    data object Selected : TagUiState
    data object SelectedStale : TagUiState
}