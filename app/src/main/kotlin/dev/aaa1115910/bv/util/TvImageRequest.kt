package dev.aaa1115910.bv.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil.request.ImageRequest

@Composable
fun rememberTvImageRequest(url: String?, widthDp: Dp, heightDp: Dp): ImageRequest {
    val density = LocalDensity.current
    val context = LocalContext.current
    return remember(url, widthDp, heightDp) {
        ImageRequest.Builder(context)
            .data(url)
            .size(
                with(density) { widthDp.toPx().toInt() },
                with(density) { heightDp.toPx().toInt() }
            )
            .crossfade(false)
            .build()
    }
}
