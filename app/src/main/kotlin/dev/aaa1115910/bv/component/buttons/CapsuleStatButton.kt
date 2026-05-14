package dev.aaa1115910.bv.component.buttons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * 胶囊按钮内容：左侧“正方形”区域（宽=高）居中放 icon，右侧放一行数字。
 *
 * 注意：
 * - 这里只负责排版，不负责点击/按键逻辑（由各 Button 自己处理）
 * - 左侧 40.dp 正方形区域的中心点即 40.dp 胶囊左半圆圆心
 */
@Composable
internal fun CapsuleStatButtonContent(
    icon: @Composable () -> Unit,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .defaultMinSize(minWidth = 40.dp)
                .padding(end = 14.dp)
        ) {
            Text(
                text = text,
                fontSize = 24.sp,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}
