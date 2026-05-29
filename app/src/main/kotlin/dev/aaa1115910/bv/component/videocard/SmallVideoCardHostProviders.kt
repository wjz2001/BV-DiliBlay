package dev.aaa1115910.bv.component.videocard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.aaa1115910.biliapi.entity.FavoriteFolderMetadata
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.component.CoAuthorsDialogHost
import dev.aaa1115910.bv.component.FavoriteDialog
import dev.aaa1115910.bv.component.handleUpHomeClick
import dev.aaa1115910.bv.component.rememberCoAuthorsDialogState
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.SmallVideoCardGridEvent
import dev.aaa1115910.bv.viewmodel.SmallVideoCardGridViewModel
import dev.aaa1115910.bv.viewmodel.SmallVideoCardItemUiState
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun SmallVideoCardHostProviders(
    viewModelKey: String? = null,
    onNavigateUp: ((Long, String) -> Unit)? = null,
    content: @Composable (
        cardUiStateFor: (Long) -> SmallVideoCardItemUiState?
    ) -> Unit
) {
    val viewModel: SmallVideoCardGridViewModel = if (viewModelKey != null) {
        koinViewModel(key = viewModelKey)
    } else {
        koinViewModel()
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cardUiMap by viewModel.cardUiMap.collectAsStateWithLifecycle()
    val coAuthorsDialogState = rememberCoAuthorsDialogState()

    val navigateUp = remember(context, onNavigateUp) {
        onNavigateUp ?: { mid: Long, name: String ->
            UpInfoActivity.actionStart(context, mid = mid, name = name)
        }
    }

    val favoriteFolders = remember { mutableStateListOf<FavoriteFolderMetadata>() }
    val selectedFolderIds = remember { mutableStateListOf<Long>() }

    val cardUiStateFor = remember(cardUiMap) {
        { aid: Long -> cardUiMap[aid] }
    }

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

    CompositionLocalProvider(
        LocalSmallVideoCardGridViewModel provides viewModel,
        LocalSmallVideoCardGridUiState provides uiState
    ) {
        content(cardUiStateFor)
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
