package dev.aaa1115910.bv.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.ui.theme.BVTheme

@Composable
fun UpIcon(
    modifier: Modifier = Modifier,
    color: Color,
    upgroup: Boolean = false
) {
    Icon(
        modifier = if (upgroup) modifier.size(22.dp) else modifier,
        painter = painterResource(
            id = if (upgroup) R.drawable.group_24px else R.drawable.ic_up
        ),
        contentDescription = null,
        tint = color
    )
}

@Preview
@Composable
fun UpIconPreview() {
    BVTheme {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            UpIcon(color = MaterialTheme.colorScheme.onSurface)
            Text(text = "bishi")
        }
    }
}
