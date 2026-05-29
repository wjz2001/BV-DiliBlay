package dev.aaa1115910.bv.component.videocard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.wjzfocus.WjzFocusItemKey
import dev.aaa1115910.bv.component.TvGridFocusHost
import dev.aaa1115910.bv.viewmodel.SmallVideoCardGridUiState
import dev.aaa1115910.bv.viewmodel.SmallVideoCardGridViewModel
import dev.aaa1115910.bv.viewmodel.SmallVideoCardItemUiState

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
    nodeIdPrefix: String,

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
    onEntryFocusReady: (() -> Unit)? = null,
    focusItemCount: Int = 0,
    focusItemKeys: List<WjzFocusItemKey>,
    focusColumnCount: Int = 4,
    content: LazyGridScope.((Long) -> SmallVideoCardItemUiState?) -> Unit
) {
    /**
     * 真正的 grid 内容。
     * 所有 SmallVideoCard 都可以通过 CompositionLocal 拿到同一个 VM。
     */
    SmallVideoCardHostProviders(
        onNavigateUp = onNavigateUp
    ) { cardUiStateFor ->
        TvGridFocusHost(
            columns = columns,
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            nodeIdPrefix = nodeIdPrefix,
            enableRowHorizontalWrap = enableRowHorizontalWrap,
            onEntryFocusReady = onEntryFocusReady,
            focusItemCount = focusItemCount,
            itemKeys = focusItemKeys,
            focusColumnCount = focusColumnCount
        ) {
            content(cardUiStateFor)
        }
    }
}
