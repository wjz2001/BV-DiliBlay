package dev.aaa1115910.bv.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.component.PgcTopNavItem
import dev.aaa1115910.bv.component.TopNav
import dev.aaa1115910.bv.component.PersistLazyListViewportEffect
import dev.aaa1115910.bv.component.rememberRestoredLazyListState
import dev.aaa1115910.bv.screen.main.pgc.AnimeContent
import dev.aaa1115910.bv.screen.main.pgc.DocumentaryContent
import dev.aaa1115910.bv.screen.main.pgc.GuoChuangContent
import dev.aaa1115910.bv.screen.main.pgc.MovieContent
import dev.aaa1115910.bv.screen.main.pgc.TvContent
import dev.aaa1115910.bv.screen.main.pgc.VarietyContent
import dev.aaa1115910.bv.viewmodel.main.PgcContentViewModel
import dev.aaa1115910.bv.viewmodel.pgc.PgcAnimeViewModel
import dev.aaa1115910.bv.viewmodel.pgc.PgcDocumentaryViewModel
import dev.aaa1115910.bv.viewmodel.pgc.PgcGuoChuangViewModel
import dev.aaa1115910.bv.viewmodel.pgc.PgcMovieViewModel
import dev.aaa1115910.bv.viewmodel.pgc.PgcTvViewModel
import dev.aaa1115910.bv.viewmodel.pgc.PgcVarietyViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun PgcContent(
    navFocusRequester: FocusRequester,
    onDefaultFocusReady: (() -> Unit)? = null,
    pgcContentViewModel: PgcContentViewModel = koinViewModel(),
    pgcAnimeViewModel: PgcAnimeViewModel = koinViewModel(),
    pgcGuoChuangViewModel: PgcGuoChuangViewModel = koinViewModel(),
    pgcMovieViewModel: PgcMovieViewModel = koinViewModel(),
    pgcDocumentaryViewModel: PgcDocumentaryViewModel = koinViewModel(),
    pgcTvViewModel: PgcTvViewModel = koinViewModel(),
    pgcVarietyViewModel: PgcVarietyViewModel = koinViewModel()
) {
    val animeState = rememberRestoredLazyListState(
        pgcContentViewModel.viewportOf(PgcTopNavItem.Anime)
    )
    val guoChuangState = rememberRestoredLazyListState(
        pgcContentViewModel.viewportOf(PgcTopNavItem.GuoChuang)
    )
    val movieState = rememberRestoredLazyListState(
        pgcContentViewModel.viewportOf(PgcTopNavItem.Movie)
    )
    val documentaryState = rememberRestoredLazyListState(
        pgcContentViewModel.viewportOf(PgcTopNavItem.Documentary)
    )
    val tvState = rememberRestoredLazyListState(
        pgcContentViewModel.viewportOf(PgcTopNavItem.Tv)
    )
    val varietyState = rememberRestoredLazyListState(
        pgcContentViewModel.viewportOf(PgcTopNavItem.Variety)
    )

    PersistLazyListViewportEffect(animeState) { index, offset ->
        pgcContentViewModel.updateViewport(PgcTopNavItem.Anime, index, offset)
    }
    PersistLazyListViewportEffect(guoChuangState) { index, offset ->
        pgcContentViewModel.updateViewport(PgcTopNavItem.GuoChuang, index, offset)
    }
    PersistLazyListViewportEffect(movieState) { index, offset ->
        pgcContentViewModel.updateViewport(PgcTopNavItem.Movie, index, offset)
    }
    PersistLazyListViewportEffect(documentaryState) { index, offset ->
        pgcContentViewModel.updateViewport(PgcTopNavItem.Documentary, index, offset)
    }
    PersistLazyListViewportEffect(tvState) { index, offset ->
        pgcContentViewModel.updateViewport(PgcTopNavItem.Tv, index, offset)
    }
    PersistLazyListViewportEffect(varietyState) { index, offset ->
        pgcContentViewModel.updateViewport(PgcTopNavItem.Variety, index, offset)
    }

    val selectedTab = pgcContentViewModel.selectedTab
    var focusOnContent by remember { mutableStateOf(false) }
    val currentListOnTop by remember {
        derivedStateOf {
            with(
                when (selectedTab) {
                    PgcTopNavItem.Anime -> animeState
                    PgcTopNavItem.GuoChuang -> guoChuangState
                    PgcTopNavItem.Movie -> movieState
                    PgcTopNavItem.Documentary -> documentaryState
                    PgcTopNavItem.Tv -> tvState
                    PgcTopNavItem.Variety -> varietyState
                }
            ) {
                firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
            }
        }
    }

    Scaffold(
        modifier = Modifier,
        topBar = {
            TopNav(
                modifier = Modifier.padding(end = 80.dp),
                items = PgcTopNavItem.entries,
                isLargePadding = !focusOnContent && currentListOnTop,
                selectedItem = selectedTab,
                defaultFocusRequester = navFocusRequester,
                onDefaultFocusReady = onDefaultFocusReady,
                onSelectedChanged = { nav ->
                    pgcContentViewModel.selectedTab = nav as PgcTopNavItem
                },
                onClick = { nav ->
                    when (nav) {
                        PgcTopNavItem.Anime -> pgcAnimeViewModel.reloadAll()
                        PgcTopNavItem.GuoChuang -> pgcGuoChuangViewModel.reloadAll()
                        PgcTopNavItem.Movie -> pgcMovieViewModel.reloadAll()
                        PgcTopNavItem.Documentary -> pgcDocumentaryViewModel.reloadAll()
                        PgcTopNavItem.Tv -> pgcTvViewModel.reloadAll()
                        PgcTopNavItem.Variety -> pgcVarietyViewModel.reloadAll()
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .onFocusChanged { focusOnContent = it.hasFocus }
                .onPreviewKeyEvent {
                    if (it.key == Key.Menu) {
                        if (it.type == KeyEventType.KeyDown) return@onPreviewKeyEvent true
                        when (selectedTab) {
                            PgcTopNavItem.Anime -> pgcAnimeViewModel.reloadAll()
                            PgcTopNavItem.GuoChuang -> pgcGuoChuangViewModel.reloadAll()
                            PgcTopNavItem.Movie -> pgcMovieViewModel.reloadAll()
                            PgcTopNavItem.Documentary -> pgcDocumentaryViewModel.reloadAll()
                            PgcTopNavItem.Tv -> pgcTvViewModel.reloadAll()
                            PgcTopNavItem.Variety -> pgcVarietyViewModel.reloadAll()
                        }
                        navFocusRequester.requestFocus()
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
        ) {

                when (selectedTab) {
                    PgcTopNavItem.Anime -> AnimeContent(lazyListState = animeState)
                    PgcTopNavItem.GuoChuang -> GuoChuangContent(lazyListState = guoChuangState)
                    PgcTopNavItem.Movie -> MovieContent(lazyListState = movieState)
                    PgcTopNavItem.Documentary -> DocumentaryContent(lazyListState = documentaryState)
                    PgcTopNavItem.Tv -> TvContent(lazyListState = tvState)
                    PgcTopNavItem.Variety -> VarietyContent(lazyListState = varietyState)
                }
        }
    }
}