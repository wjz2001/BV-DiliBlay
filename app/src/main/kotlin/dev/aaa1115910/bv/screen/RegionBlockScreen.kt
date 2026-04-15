package dev.aaa1115910.bv.screen

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.ui.theme.AppWhite
import dev.aaa1115910.bv.ui.theme.BVTheme
import qrcode.QRCode
import qrcode.color.Colors
import kotlin.system.exitProcess

@Composable
fun RegionBlockScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var qrImage by remember {
        mutableStateOf(ImageBitmap(1, 1, ImageBitmapConfig.Argb8888))
    }

    val qrContent = stringResource(R.string.region_block_qr_content)

    var finishNumberTarget by remember { mutableIntStateOf(0) }
    val finishNumber by animateIntAsState(
        targetValue = finishNumberTarget,
        animationSpec = keyframes {
            durationMillis = 12 * 1000
            0 at 0
            10 at 1 * 1000
            60 at 3 * 1000
            90 at 7 * 1000
            91 at 10 * 1000
            100 at 12 * 1000
        },
        label = "finish percent animation"
    )

    // 用 qrContent 做 key：语言/配置变化导致字符串变化时，会重新生成二维码
    LaunchedEffect(qrContent) {
        finishNumberTarget = 100

        val pngBytes = QRCode.ofSquares()
            .withColor(Colors.BLACK)
            .withBackgroundColor(Colors.WHITE)
            .build(qrContent)
            .renderToBytes()

        BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)?.let { bmp ->
            qrImage = bmp.asImageBitmap()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            (context as Activity).finish()
            exitProcess(0)
        }
    }

    Surface(
        modifier = modifier
            .focusable()
            .onKeyEvent {
                (context as Activity).finish()
                exitProcess(0)
            },
        shape = RoundedCornerShape(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(84.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(
                    text = stringResource(R.string.region_block_character_painting),
                    fontSize = 100.sp
                )

                Column {
                    Text(
                        text = stringResource(R.string.region_block_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = stringResource(R.string.region_block_subtitle),
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                Text(
                    text = "$finishNumber% 完成",
                    style = MaterialTheme.typography.titleLarge
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(AppWhite),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            modifier = Modifier.size(64.dp),
                            bitmap = qrImage,
                            contentDescription = null
                        )
                    }

                    Column {
                        Text(text = stringResource(R.string.region_block_solution_title))
                        Text(text = stringResource(R.string.region_block_solution_text))
                    }
                }
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun RegionBlockScreenPreview() {
    BVTheme {
        RegionBlockScreen()
    }
}
