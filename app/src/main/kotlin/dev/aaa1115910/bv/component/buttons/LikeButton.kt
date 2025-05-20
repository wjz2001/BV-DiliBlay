package dev.aaa1115910.bv.component.buttons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButtonDefaults

@Composable
fun LikeButton(
    modifier: Modifier = Modifier,
    isLiked: Boolean,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier,
        onClick = onClick,
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
            contentDescription = null
        )
    }
}

@Preview
@Composable
fun LikeButtonEnablePreview() {
    LikeButton(
        isLiked = false,
        onClick = {}
    )
}