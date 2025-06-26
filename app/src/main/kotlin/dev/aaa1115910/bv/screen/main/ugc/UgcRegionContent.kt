package dev.aaa1115910.bv.screen.main.ugc

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun UgcRegionContent(
    modifier: Modifier = Modifier,
    state: UgcScaffoldState
) {
    UgcRegionScaffold(
        modifier = modifier,
        state = state,
    )
}
