package dev.aaa1115910.bv.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.aaa1115910.bv.tv.component.TvAlertDialog
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.LibVLCDownloaderViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun LibVLCDownloaderDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onHideDialog: () -> Unit,
    libVLCDownloaderViewModel: LibVLCDownloaderViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val processing = libVLCDownloaderViewModel.processing
    val text = libVLCDownloaderViewModel.text

    val startInstall: () -> Unit = {
        libVLCDownloaderViewModel.startInstall(
            cacheDir = context.cacheDir,
            filesDir = context.filesDir,
            onSuccess = {
                onHideDialog()
                "LibVLC 安装成功".toast(context)
            },
            onFailure = {
                "LibVLC 安装失败: ${it.message}".toast(context)
            }
        )
    }

    if (show) {
        TvAlertDialog(
            modifier = modifier,
            title = { Text(text = "LibVLC 下载器") },
            text = { Text(text = text) },
            onDismissRequest = { if (!processing) onHideDialog() },
            confirmButton = {
                Button(
                    onClick = { startInstall() },
                    enabled = !processing
                ) {
                    Text(text = "下载")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { onHideDialog() },
                    enabled = !processing
                ) {
                    Text(text = "取消")
                }
            }
        )
    }
}
