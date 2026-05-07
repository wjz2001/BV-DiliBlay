package dev.aaa1115910.bv.component.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.Text
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest

@Composable
fun SearchKeyword(
    modifier: Modifier = Modifier,
    keyword: String,
    leadingIcon: String,
    trailingIcon: @Composable() (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(context)
            .data(data = leadingIcon)
            .build(),
        imageLoader = context.imageLoader,
        contentScale = ContentScale.FillHeight
    )

    if (leadingIcon != "" && painter.state is AsyncImagePainter.State.Success) {
        DenseListItem(
            modifier = modifier,
            selected = false,
            onClick = onClick,
            headlineContent = {
                Text(
                    text = keyword,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                Image(
                    modifier = Modifier.height(16.dp),
                    painter = painter,
                    contentDescription = null,
                )
            },
            trailingContent = trailingIcon
        )
    } else {
        DenseListItem(
            modifier = modifier,
            selected = false,
            onClick = onClick,
            headlineContent = {
                Text(
                    text = keyword,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = trailingIcon
        )
    }
}
