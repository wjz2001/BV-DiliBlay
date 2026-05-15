package dev.aaa1115910.bv.component.buttons

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon

@Composable
fun CoinButton(
    modifier: Modifier = Modifier,
    isCoined: Boolean,
    countText: String = "",
    onClick: () -> Unit
) {
    Button(
        modifier = modifier.height(40.dp),
        contentPadding = PaddingValues(0.dp),
        scale = ButtonDefaults.scale(
            focusedScale = 1f,
            pressedScale = 0.9f
        ),
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
