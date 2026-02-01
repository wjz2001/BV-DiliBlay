package dev.aaa1115910.bv.component.buttons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * 胶囊按钮内容：左侧“正方形”区域（宽=高）居中放 icon，右侧放一行数字。
 *
 * 注意：
 * - 这里只负责排版，不负责点击/按键逻辑（由各 Button 自己处理）
 * - 通过 leftIconBox 的 aspectRatio(1f) 让 icon 处于左半圆圆心
 */
@Composable
internal fun CapsuleStatButtonContent(
    icon: @Composable () -> Unit,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),//高度由内容决定，不依赖父布局
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp)
        ) {
            // 【支架】隐形的 18.sp 文字
            // 它的作用是告诉 Row："我的高度应该是这么大"
            Text(
                text = text,
                fontSize = 18.sp, // 这里写死你觉得合适的高度基准
                color = androidx.compose.ui.graphics.Color.Transparent, // 设为透明
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                // 注意：这里不需要 TextOverflow，让它完整撑开宽度
            )

            // 【实体】真正想显示的大字号文字
            Text(
                text = text,
                fontSize = 24.sp, // 不会撑大按钮高度
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    // 自定义 Layout 测量逻辑
                    // 作用：测量真实大小，但在向父容器汇报时，撒谎说自己高度为 0。
                    // 结果：Box 计算大小时会忽略它，只看上面那个 18.sp 的隐形文字。
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val visualCorrection = (-3).dp.roundToPx()
                        // 汇报宽度照旧（保证横向不重叠），汇报高度为 0
                        layout(placeable.width, 0) {
                            // 因为汇报高度为 0，对齐中心点变成了 0 的位置。
                            // 为了让文字垂直居中，向上偏移文字真实高度的一半。
                            placeable.placeRelative(
                                x = 0,
                                y = -placeable.height / 2 + visualCorrection
                            )
                        }
                    }
            )
        }

        //Spacer(Modifier.width(6.dp))
    }
}