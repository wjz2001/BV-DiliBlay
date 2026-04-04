package dev.aaa1115910.bv.component.buttons

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon

@Composable
fun CommentButton(
    modifier: Modifier = Modifier,
    countText: String = "",
    onClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(percent = 50)

    Button(
        modifier = modifier.height(44.dp),
        contentPadding = PaddingValues(0.dp),
        scale = ButtonDefaults.scale(
            focusedScale = 1f,
            pressedScale = 1f
        ),
        shape = ButtonDefaults.shape(
            shape = pillShape,
            focusedShape = pillShape,
            pressedShape = pillShape,
            disabledShape = pillShape,
            focusedDisabledShape = pillShape
        ),
        onClick = onClick
    ) {
        CapsuleStatButtonContent(
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Comment,
                    contentDescription = null
                )
            },
            text = countText
        )
    }
}