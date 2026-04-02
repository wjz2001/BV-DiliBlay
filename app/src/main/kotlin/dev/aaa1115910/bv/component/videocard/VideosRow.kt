package dev.aaa1115910.bv.component.videocard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.FavoriteFolderMetadata
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.component.CoAuthorsDialogHost
import dev.aaa1115910.bv.component.buttons.FavoriteDialog
import dev.aaa1115910.bv.component.handleUpHomeClick
import dev.aaa1115910.bv.component.rememberCoAuthorsDialogState
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.SmallVideoCardGridEvent
import dev.aaa1115910.bv.viewmodel.SmallVideoCardGridViewModel
import org.koin.androidx.compose.koinViewModel

private class RowWrapController {
    private val requesters = mutableMapOf<Int, FocusRequester>()

    var enabled: Boolean = true
    var itemCount: Int = 0

    fun requesterFor(index: Int): FocusRequester {
        return requesters.getOrPut(index) { FocusRequester() }
    }

    fun Modifier.modifierFor(index: Int): Modifier {
        if (index !in 0 until itemCount) return this

        // 无论是否启用循环，都先给每个 item 挂上自己的 requester，
        // 这样 focusRestorer 可以稳定回到第一个 item。
        val base = this.focusRequester(requesterFor(index))

        if (!enabled || itemCount <= 1) return base

        val lastIndex = itemCount - 1
        return base.focusProperties {
            left = requesterFor(if (index == 0) lastIndex else index - 1)
            right = requesterFor(if (index == lastIndex) 0 else index + 1)
        }
    }
}

@Composable
fun VideosRow(
    modifier: Modifier = Modifier,
    header: String,
    videos: List<VideoCardData>,
    onVideoClicked: (VideoCardData) -> Unit,
    onAddWatchLater: ((Long) -> Unit)? = null,
    onGoToDetailPage: ((Long) -> Unit)? = null,
    onGoToUpPage: ((Long, String) -> Unit)? = null,
    enableHorizontalWrap: Boolean = true,
) {
    val viewModel: SmallVideoCardGridViewModel = koinViewModel()
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val coAuthorsDialogState = rememberCoAuthorsDialogState()

    var hasFocus by remember { mutableStateOf(false) }
    val titleColor = if (hasFocus) Color.White else Color.White.copy(alpha = 0.6f)
    val titleFontSize by animateFloatAsState(
        targetValue = 14f,
        label = "title font size"
    )

    val navigateUp = remember(context, onGoToUpPage) {
        onGoToUpPage ?: { mid: Long, name: String ->
            UpInfoActivity.actionStart(context, mid = mid, name = name)
        }
    }

    val favoriteFolders = remember { mutableStateListOf<FavoriteFolderMetadata>() }
    val selectedFolderIds = remember { mutableStateListOf<Long>() }

    val rowWrapController = remember { RowWrapController() }.apply {
        enabled = enableHorizontalWrap
        itemCount = videos.size
    }

    val fallbackFocusRequester = remember { FocusRequester() }

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

    CompositionLocalProvider(LocalSmallVideoCardGridViewModel provides viewModel) {
        Column(
            modifier = modifier.onFocusChanged { hasFocus = it.hasFocus }
        ) {
            Text(
                modifier = Modifier.padding(start = 50.dp),
                text = header,
                fontSize = titleFontSize.sp,
                color = titleColor
            )

            LazyRow(
                modifier = Modifier
                    .padding(top = 15.dp)
                    .focusRestorer(
                        if (videos.isNotEmpty()) {
                            rowWrapController.requesterFor(0)
                        } else {
                            fallbackFocusRequester
                        }
                    ),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top,
                contentPadding = PaddingValues(horizontal = 62.dp)
            ) {
                itemsIndexed(
                    items = videos,
                    key = { _, item -> item.avid }
                ) { index, videoData ->
                    SmallVideoCard(
                        modifier = with(rowWrapController) {
                            Modifier
                                .width(200.dp)
                                .modifierFor(index)
                        },
                        data = videoData,
                        onClick = { onVideoClicked(videoData) },
                        coverDensityMultiplier = 1f,
                        coverFontScaleMultiplier = 1f,
                        infoDensityMultiplier = 1f,
                        infoFontScaleMultiplier = 1f,
                        onAddWatchLater = onAddWatchLater?.let { callback ->
                            { callback(videoData.avid) }
                        },
                        onGoToDetailPage = onGoToDetailPage?.let { callback ->
                            { callback(videoData.avid) }
                        },
                        onGoToUpPage = onGoToUpPage?.let { callback ->
                            videoData.upMid?.let { mid ->
                                { callback(mid, videoData.upName) }
                            }
                        }
                    )
                }
            }
        }
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