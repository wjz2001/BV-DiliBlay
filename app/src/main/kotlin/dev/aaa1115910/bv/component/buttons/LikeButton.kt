package dev.aaa1115910.bv.component.buttons

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme

@Composable
fun LikeButton(
    modifier: Modifier = Modifier,
    isLiked: Boolean,
    countText: String = "",
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Button(
        modifier = modifier.height(40.dp),
        contentPadding = PaddingValues(0.dp),
        scale = ButtonDefaults.scale(
            focusedScale = 1f,
            pressedScale = 1f
        ),
        colors = ButtonDefaults.colors(
            pressedContainerColor = MaterialTheme.colorScheme.primary
        ),
        onClick = onClick,
        onLongClick = {}
    ) {
        CapsuleStatButtonContent(
            icon = {
                Icon(
                    imageVector = if (isLiked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = null
                )
            },
            text = countText
        )
    }
}

@Preview
@Composable
fun LikeButtonEnablePreview() {
    LikeButton(
        isLiked = false,
        countText = "6666666",
        onClick = {},
        onLongClick = {},
    )
}
