package dev.aaa1115910.bv.component.videocard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.FavoriteFolderMetadata
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.component.CoAuthorsDialogHost
import dev.aaa1115910.bv.component.TvLazyVerticalGrid
import dev.aaa1115910.bv.component.buttons.FavoriteDialog
import dev.aaa1115910.bv.component.handleUpHomeClick
import dev.aaa1115910.bv.component.rememberCoAuthorsDialogState
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.SmallVideoCardGridEvent
import dev.aaa1115910.bv.viewmodel.SmallVideoCardGridViewModel
import org.koin.androidx.compose.koinViewModel

val LocalSmallVideoCardGridViewModel =
    compositionLocalOf<SmallVideoCardGridViewModel?> { null }

/**
 * 页面级 Host：
 * - 内部持有通用 SmallVideoCardGridViewModel（通过 Koin）
 * - 统一渲染 FavoriteDialog / CoAuthorsDialogHost
 * - 内部继续使用 TvLazyVerticalGrid
 *
 * 你的页面层只需要把外层：
 * TvLazyVerticalGrid(...)
 * 改成：
 * SmallVideoCardGridHost(...)
 */
@Composable
fun SmallVideoCardGridHost(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),

    // 针对“一屏两行多卡片”的默认值
    pivotFraction: Float = 0.45f,
    topSafeFraction: Float = 0.12f,
    bottomSafeFraction: Float = 0.9f,
    hysteresis: Dp = 36.dp,

    // 页面如有自定义 UP 跳转，可以统一在这里传；不传则默认起 UpInfoActivity
    onNavigateUp: ((Long, String) -> Unit)? = null,

    content: LazyGridScope.() -> Unit
) {
    val viewModel: SmallVideoCardGridViewModel = koinViewModel()
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val coAuthorsDialogState = rememberCoAuthorsDialogState()

    val navigateUp = remember(context, onNavigateUp) {
        onNavigateUp ?: { mid: Long, name: String ->
            UpInfoActivity.actionStart(context, mid = mid, name = name)
        }
    }

    val favoriteFolders = remember { mutableStateListOf<FavoriteFolderMetadata>() }
    val selectedFolderIds = remember { mutableStateListOf<Long>() }

    LaunchedEffect(Unit) {
        viewModel.refreshCapabilities()
    }

    LaunchedEffect(uiState.favoriteDialog.folders, uiState.favoriteDialog.selectedFolderIds) {
        favoriteFolders.clear()
        favoriteFolders.addAll(uiState.favoriteDialog.folders)

        selectedFolderIds.clear()
        selectedFolderIds.addAll(uiState.favoriteDialog.selectedFolderIds)
    }

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

    LaunchedEffect(coAuthorsDialogState.visible, uiState.coAuthorsDialog.show) {
        if (!coAuthorsDialogState.visible && uiState.coAuthorsDialog.show) {
            viewModel.dismissCoAuthorsDialog()
        }
    }

    LaunchedEffect(viewModel) {
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

    CompositionLocalProvider(
        LocalSmallVideoCardGridViewModel provides viewModel
    ) {
        TvLazyVerticalGrid(
            columns = columns,
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            pivotFraction = pivotFraction,
            topSafeFraction = topSafeFraction,
            bottomSafeFraction = bottomSafeFraction,
            hysteresis = hysteresis,
            content = content
        )
    }

    FavoriteDialog(
        show = uiState.favoriteDialog.show,
        onHideDialog = viewModel::dismissFavoriteDialog,
        userFavoriteFolders = favoriteFolders,
        favoriteFolderIds = selectedFolderIds,
        onUpdateFavoriteFolders = viewModel::updateFavoriteFolders
    )

    CoAuthorsDialogHost(
        state = coAuthorsDialogState,
        onClickAuthor = { mid, name ->
            viewModel.onCoAuthorClicked(mid, name)
        }
    )
}