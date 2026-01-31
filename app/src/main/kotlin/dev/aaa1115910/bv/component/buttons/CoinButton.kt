package dev.aaa1115910.bv.component.buttons

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Button
import androidx.tv.material3.Icon

@Composable
fun CoinButton(
    modifier: Modifier = Modifier,
    isCoined: Boolean,
    countText: String = "",
    onClick: () -> Unit
) {

    val pillShape = RoundedCornerShape(percent = 50)

    Button(
        modifier = modifier,
        onClick = onClick,
    ) {
        /*
        Icon(
            imageVector = if (isCoined) Icons.Rounded.Paid else Icons.Outlined.Paid,
            contentDescription = null
        )
         */
        CapsuleStatButtonContent(
            icon = {
                Icon(
                    imageVector = if (isCoined) Icons.Rounded.Paid else Icons.Outlined.Paid,
                    contentDescription = null
                )
            },
            text = countText
        )
    }
}

@Preview
@Composable
fun CoinButtonPreview() {
    CoinButton(
        isCoined = false,
        onClick = {}
    )
}