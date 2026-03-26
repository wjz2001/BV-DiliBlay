package dev.aaa1115910.bv.screen.main.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.aaa1115910.bv.component.UgcTopNavItem
import dev.aaa1115910.bv.viewmodel.pgc.PgcAnimeViewModel
import dev.aaa1115910.bv.viewmodel.pgc.PgcWarmUpOptions
import dev.aaa1115910.bv.viewmodel.search.SearchInputViewModel
import dev.aaa1115910.bv.viewmodel.ugc.UgcViewModel
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainDrawerPreloadHost(
    preloadSearch: Boolean,
    preloadPersonal: Boolean,
    preloadUgc: Boolean,
    preloadPgc: Boolean
) {
    if (preloadSearch) {
        SearchDrawerPreloader()
    }
    if (preloadPersonal) {
        PersonalDrawerPreloader()
    }
    if (preloadUgc) {
        UgcDrawerPreloader()
    }
    if (preloadPgc) {
        PgcDrawerPreloader()
    }
}

@Composable
private fun SearchDrawerPreloader(
    searchInputViewModel: SearchInputViewModel = koinViewModel()
) {
    LaunchedEffect(searchInputViewModel) {
        searchInputViewModel.warmUp(showHotwordErrorToast = false)
    }
}

@Composable
private fun PersonalDrawerPreloader(
    toViewViewModel: ToViewViewModel = koinViewModel()
) {
    LaunchedEffect(toViewViewModel) {
        toViewViewModel.warmUp()
    }
}

@Composable
private fun UgcDrawerPreloader(
    ugcViewModel: UgcViewModel = koinViewModel()
) {
    LaunchedEffect(ugcViewModel) {
        ugcViewModel.warmUp(UgcTopNavItem.Douga)
    }
}

@Composable
private fun PgcDrawerPreloader(
    pgcAnimeViewModel: PgcAnimeViewModel = koinViewModel()
) {
    LaunchedEffect(pgcAnimeViewModel) {
        pgcAnimeViewModel.warmUp(
            PgcWarmUpOptions(
                showCarouselErrorToast = false
            )
        )
    }
}