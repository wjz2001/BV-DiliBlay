package dev.aaa1115910.bv.component.videocard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.aaa1115910.biliapi.entity.FavoriteFolderMetadata
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.component.CoAuthorsDialogHost
import dev.aaa1115910.bv.component.TvLazyVerticalGrid
import dev.aaa1115910.bv.component.FavoriteDialog
import dev.aaa1115910.bv.component.handleUpHomeClick
import dev.aaa1115910.bv.component.rememberCoAuthorsDialogState
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.SmallVideoCardGridEvent
import dev.aaa1115910.bv.viewmodel.SmallVideoCardGridUiState
import dev.aaa1115910.bv.viewmodel.SmallVideoCardGridViewModel
import dev.aaa1115910.bv.viewmodel.SmallVideoCardItemUiState
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 通过 CompositionLocal 向 SmallVideoCard 提供通用的页面级 ViewModel。
 *
 * Host 模式下：
 * - SmallVideoCard 不再自己持有 dialog / repository / cache / jobs
 * - 统一从这里拿到页面级 SmallVideoCardGridViewModel
 */
val LocalSmallVideoCardGridViewModel =
    compositionLocalOf<SmallVideoCardGridViewModel?> { null }

val LocalSmallVideoCardGridUiState =
    compositionLocalOf { SmallVideoCardGridUiState() }

private class GridRowWrapController(
    private val columnCount: Int
) {
    private val requesters = mutableMapOf<Int, FocusRequester>()

    var enabled: Boolean = true
    var itemCount: Int = 0
    var entryFocusRequester: FocusRequester? = null
    var upFocusRequester: FocusRequester? = null
    var enableHorizontalLinks: Boolean = true
    var onEntryFocusReady: (() -> Unit)? = null
    var gridState: LazyGridState? = null
    var scope: CoroutineScope? = null

    private fun requesterFor(index: Int): FocusRequester {
        if (index == 0) {
            entryFocusRequester?.let { return it }
        }
        return requesters.getOrPut(index) { FocusRequester() }
    }

    private fun sameColumnTarget(index: Int, rowOffset: Int): Int? {
        val target = index + columnCount * rowOffset
        return target.takeIf { it in 0 until itemCount }
    }

    private fun moveFocusTo(index: Int) {
        val state = gridState ?: return
        val coroutineScope = scope ?: return
        coroutineScope.launch {
            state.scrollToItem(index)
            requesterFor(index).requestFocus()
        }
    }

    fun Modifier.modifierFor(index: Int): Modifier {
        if (!enabled || itemCount <= 0 || index !in 0 until itemCount) return this

        val rowStart = (index / columnCount) * columnCount
        val rowEnd = minOf(rowStart + columnCount - 1, itemCount - 1)
        val upTarget = sameColumnTarget(index, -1)
        val downTarget = sameColumnTarget(index, 1)

        var modifier = this
            .focusRequester(requesterFor(index))
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (event.key) {
                    Key.DirectionUp -> {
                        val target = upTarget ?: return@onPreviewKeyEvent false
                        moveFocusTo(target)
                        true
                    }

                    Key.DirectionDown -> {
                        val target = downTarget ?: return@onPreviewKeyEvent false
                        moveFocusTo(target)
                        true
                    }

                    else -> false
                }
            }
            .focusProperties {
                if (enableHorizontalLinks && rowStart != rowEnd) {
                    left = if (index == rowStart) {
                        requesterFor(rowEnd)
                    } else {
                        requesterFor(index - 1)
                    }

                    right = if (index == rowEnd) {
                        requesterFor(rowStart)
                    } else {
                        requesterFor(index + 1)
                    }
                }
                if (rowStart == 0) {
                    up = upFocusRequester ?: FocusRequester.Default
                } else {
                    upTarget?.let { up = requesterFor(it) }
                }
                downTarget?.let { down = requesterFor(it) }
            }
        if (index == 0) {
            modifier = modifier.onGloballyPositioned {
                onEntryFocusReady?.invoke()
            }
        }
        return modifier
    }
}

private val LocalGridRowWrapController =
    compositionLocalOf<GridRowWrapController?> { null }

@Composable
fun rememberGridRowWrapModifier(index: Int): Modifier {
    val controller = LocalGridRowWrapController.current
    return if (controller != null) {
        with(controller) { Modifier.modifierFor(index) }
    } else {
        Modifier
    }
}

/**
 * SmallVideoCard 的页面级宿主（Host）。
 *
 * 职责：
 * 1. 内部通过 Koin 持有一个通用 SmallVideoCardGridViewModel
 * 2. 统一渲染 FavoriteDialog / CoAuthorsDialogHost（页面级一个）
 * 3. 通过 CompositionLocal 把 ViewModel 提供给所有 SmallVideoCard
 * 4. 内部继续包裹 TvLazyVerticalGrid，统一焦点滚动策略
 *
 * 使用方式：
 * 原来页面层写：
 *
 * TvLazyVerticalGrid(...) {
 *     items(...) {
 *         SmallVideoCard(...)
 *     }
 * }
 *
 * 现在改成：
 *
 * SmallVideoCardGridHost(...) {
 *     items(...) {
 *         SmallVideoCard(...)
 *     }
 * }
 *
 * 这样页面层基本不用关心：
 * - FavoriteDialog
 * - CoAuthorsDialogHost
 * - history / favorite / coAuthors 的通用状态管理
 *
 * 说明：
 * - mode 默认使用 KeepVisible，更适合大卡、弱设备、非标准 TV 系统
 * - 如果你更想保留 TV 定轴感，可以切成 Pivot
 * - onNavigateUp 是页面级统一 UP 跳转入口；不传则默认启动 UpInfoActivity
 */
@Composable
fun SmallVideoCardGridHost(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),

    /**
     * 页面级统一 UP 跳转。
     *
     * Host 模式下：
     * - SmallVideoCard 不再直接主依赖 onGoToUpPage
     * - 单作者/多作者最终都通过 Host 统一走这里
     *
     * 不传时默认：
     * UpInfoActivity.actionStart(context, mid, name)
    */
    onNavigateUp: ((Long, String) -> Unit)? = null,
    enableRowHorizontalWrap: Boolean = true,
    entryFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onEntryFocusReady: (() -> Unit)? = null,
    horizontalWrapItemCount: Int = 0,
    horizontalWrapColumnCount: Int = 4,
    content: LazyGridScope.((Long) -> SmallVideoCardItemUiState?) -> Unit
) {
    val viewModel: SmallVideoCardGridViewModel = koinViewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cardUiMap by viewModel.cardUiMap.collectAsStateWithLifecycle()
    val coAuthorsDialogState = rememberCoAuthorsDialogState()
    val scope = rememberCoroutineScope()

    /**
     * 默认导航实现：
     * 如果页面没有传自己的 onNavigateUp，就直接打开 UpInfoActivity
     */
    val navigateUp = remember(context, onNavigateUp) {
        onNavigateUp ?: { mid: Long, name: String ->
            UpInfoActivity.actionStart(context, mid = mid, name = name)
        }
    }

    /**
     * FavoriteDialog 当前 API 需要可变列表，这里做一层桥接。
     * ViewModel 内部仍然保持不可变 UI state。
     */
    val favoriteFolders = remember { mutableStateListOf<FavoriteFolderMetadata>() }
    val selectedFolderIds = remember { mutableStateListOf<Long>() }

    val rowWrapController = remember(horizontalWrapColumnCount) {
        horizontalWrapColumnCount.let(::GridRowWrapController)
    }.apply {
        enabled = horizontalWrapItemCount > 0 &&
                horizontalWrapColumnCount > 0 &&
                (enableRowHorizontalWrap || entryFocusRequester != null)
        itemCount = horizontalWrapItemCount
        this.entryFocusRequester = entryFocusRequester
        this.upFocusRequester = upFocusRequester
        this.enableHorizontalLinks = enableRowHorizontalWrap
        this.onEntryFocusReady = onEntryFocusReady
        this.gridState = state
        this.scope = scope
    }
    val cardUiStateFor = remember(cardUiMap) {
        { aid: Long -> cardUiMap[aid] }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshCapabilities()
    }

    /**
     * 把 VM 中的 favorite dialog 数据同步到 FavoriteDialog 需要的列表容器。
     */
    LaunchedEffect(uiState.favoriteDialog.folders, uiState.favoriteDialog.selectedFolderIds) {
        favoriteFolders.clear()
        favoriteFolders.addAll(uiState.favoriteDialog.folders)

        selectedFolderIds.clear()
        selectedFolderIds.addAll(uiState.favoriteDialog.selectedFolderIds)
    }

    /**
     * 当 VM 要求显示 coAuthors dialog 时，由 Host 真正弹出。
     *
     * 这里继续复用项目现有的 handleUpHomeClick：
     * - 单作者：直接走 onNavigateSingle
     * - 多作者：交给 CoAuthorsDialogHost
     */
    LaunchedEffect(uiState.coAuthorsDialog.show, uiState.coAuthorsDialog.authors) {
        if (uiState.coAuthorsDialog.show) {
            handleUpHomeClick(
                authors = uiState.coAuthorsDialog.authors,
                state = coAuthorsDialogState,
                onNavigateSingle = { mid, name ->
                    navigateUp(mid, name)
                    viewModel.dismissCoAuthorsDialog()
                }
            )
        }
    }

    /**
     * 如果 dialog 被外部关闭（比如返回键），同步通知 VM 清理状态。
     */
    LaunchedEffect(coAuthorsDialogState.visible, uiState.coAuthorsDialog.show) {
        if (!coAuthorsDialogState.visible && uiState.coAuthorsDialog.show) {
            viewModel.dismissCoAuthorsDialog()
        }
    }

    /**
     * 收集一次性事件：
     * - Toast
     * - NavigateUp
     */
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is SmallVideoCardGridEvent.Toast -> {
                        event.message.toast(context)
                    }

                    is SmallVideoCardGridEvent.NavigateUp -> {
                        navigateUp(event.mid, event.name)
                    }
                }
            }
        }
    }

    /**
     * 真正的 grid 内容。
     * 所有 SmallVideoCard 都可以通过 CompositionLocal 拿到同一个 VM。
     */
    CompositionLocalProvider(
        LocalSmallVideoCardGridViewModel provides viewModel,
        LocalSmallVideoCardGridUiState provides uiState,
        LocalGridRowWrapController provides rowWrapController
    ) {
        val gridModifier = if (horizontalWrapItemCount <= 0 && entryFocusRequester != null) {
            modifier
                .focusRequester(entryFocusRequester)
                .focusProperties {
                    up = upFocusRequester ?: FocusRequester.Default
                }
                .focusable()
                .onGloballyPositioned {
                    onEntryFocusReady?.invoke()
                }
        } else {
            modifier
        }

        TvLazyVerticalGrid(
            columns = columns,
            modifier = gridModifier,
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            content = { content(cardUiStateFor) }
        )
    }

    /**
     * 页面级唯一 FavoriteDialog。
     */
    FavoriteDialog(
        show = uiState.favoriteDialog.show,
        onHideDialog = viewModel::dismissFavoriteDialog,
        userFavoriteFolders = favoriteFolders,
        favoriteFolderIds = selectedFolderIds,
        onUpdateFavoriteFolders = viewModel::updateFavoriteFolders
    )

    /**
     * 页面级唯一 CoAuthorsDialogHost。
     */
    CoAuthorsDialogHost(
        state = coAuthorsDialogState,
        onClickAuthor = { mid, name ->
            viewModel.onCoAuthorClicked(mid, name)
        }
    )
}
