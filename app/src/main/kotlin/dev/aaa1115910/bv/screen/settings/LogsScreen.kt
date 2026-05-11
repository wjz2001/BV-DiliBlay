package dev.aaa1115910.bv.screen.settings

import android.Manifest
import android.os.FileObserver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.network.HttpServer
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.ApiTestLoginExportUtil
import dev.aaa1115910.bv.util.LogCatcherUtil
import dev.aaa1115910.bv.util.swapList
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.settings.LogsViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

@Composable
fun LogsScreen(
    modifier: Modifier = Modifier,
    logsViewModel: LogsViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf("x.x.x.x") }
    var port by remember { mutableIntStateOf(0) }

    val logs = remember { mutableStateListOf<File>() }
    var currentSelectFile by remember { mutableStateOf<File?>(null) }

    var isCreateFocused by remember { mutableStateOf(true) }
    var refreshLogsJob: Job? by remember { mutableStateOf(null) }

    val generateFileQRCode = {
        logsViewModel.generateFileQr(host, port, currentSelectFile?.name)
    }

    val getIpAddress: () -> String = let@{
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                if (intf.name.equals("wlan0", ignoreCase = true)) {
                    val addresses = intf.inetAddresses
                    for (addr in addresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return@let addr.hostAddress ?: ""
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        ""
    }

    val updateLogs = {
        LogCatcherUtil.updateLogFiles()
        val newLogs = (LogCatcherUtil.manualFiles + LogCatcherUtil.crashFiles)
            .sortedByDescending { it.lastModified() }
        logs.swapList(newLogs)
    }

    val createManualLog = {
        LogCatcherUtil.logLogcat(manual = true)
        "Log created".toast(context)
        updateLogs()
    }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            createManualLog()
        } else {
            LogCatcherUtil.logLogcat(manual = true)
            "日志已保存到应用内，未保存到下载目录".toast(context)
            updateLogs()
        }
    }

    // 让 FileObserver 始终调用“最新的 updateLogs lambda”，避免重组后回调持有旧引用
    val updateLogsLatest by rememberUpdatedState(newValue = updateLogs)

     // 监听日志目录：当网页端/崩溃写入新文件后，TV 端列表立刻刷新
    val logDir = remember(context) { File(context.filesDir, LogCatcherUtil.LOG_DIR) }

    DisposableEffect(logDir.absolutePath) {
        // 目录不存在时先创建；否则某些机型上 startWatching 可能无效
        if (!logDir.exists()) {
            logDir.mkdirs()
        }

        // 关注“写入完成/移动进来/创建/删除”等事件即可
        val mask =
            FileObserver.CLOSE_WRITE or
                    FileObserver.MOVED_TO or
                    FileObserver.CREATE or
                    FileObserver.DELETE

        @Suppress("DEPRECATION")
        val observer = object : FileObserver(logDir.absolutePath, mask) {
            override fun onEvent(event: Int, path: String?) {
                // 回调线程不保证是主线程；切回 Compose scope（主线程）刷新
                scope.launch {
                    refreshLogsJob?.cancel()
                    refreshLogsJob = launch {
                        delay(150)
                        updateLogsLatest()
                    }
                }
            }
        }

        observer.startWatching()

        onDispose {
            observer.stopWatching()
            refreshLogsJob?.cancel()
            refreshLogsJob = null
        }
    }

    LaunchedEffect(Unit) {
        host = getIpAddress()
        port = HttpServer.server?.engine?.resolvedConnectors()?.first()?.port ?: 0

        updateLogs()
    }

    LaunchedEffect(logsViewModel.resolvedPort) {
        if (logsViewModel.resolvedPort != 0) {
            port = logsViewModel.resolvedPort
        }
    }

    LogsScreenContent(
        modifier = modifier,
        isCreateFocused = isCreateFocused,
        fileQrImage = logsViewModel.fileQrImage,
        logs = logs,
        onFocusCreate = {
            isCreateFocused = true
            logsViewModel.clearFileQr()
        },
        onFocusLogFile = { file ->
            isCreateFocused = false
            logsViewModel.cancelServerQr()
            currentSelectFile = file
            generateFileQRCode()
        },
        onClickCreateLog = {
            if (ApiTestLoginExportUtil.requiresLegacyWritePermission() &&
                !ApiTestLoginExportUtil.canWriteDownloadsWithoutRequest(context)
            ) {
                legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                createManualLog()
            }
        }
    )
}

@Composable
fun LogsScreenContent(
    modifier: Modifier = Modifier,
    isCreateFocused: Boolean,
    fileQrImage: ImageBitmap?,
    logs: List<File>,
    onFocusCreate: () -> Unit,
    onFocusLogFile: (File) -> Unit,
    onClickCreateLog: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Box(
                modifier = Modifier.padding(start = 48.dp, top = 24.dp, bottom = 8.dp, end = 48.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(id = R.string.title_activity_logs),
                        fontSize = 48.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier.weight(1f)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = 36.dp,
                        vertical = 12.dp
                    )
                ) {
                    item {
                        CreateLogItem(
                            modifier = Modifier.focusRequester(focusRequester),
                            onFocus = onFocusCreate,
                            onClick = onClickCreateLog
                        )
                    }
                    items(items = logs) { logFile ->
                        LogItem(
                            filename = logFile.name,
                            size = logFile.length(),
                            onFocus = { onFocusLogFile(logFile) }
                        )
                    }
                    if (logs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = stringResource(R.string.log_list_empty))
                            }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                if (!isCreateFocused) {
                    if (fileQrImage != null) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val qrSize = if (maxWidth < maxHeight) maxWidth else maxHeight
                            Box(
                                modifier = Modifier
                                    .size(qrSize)
                                    .clip(MaterialTheme.shapes.large)
                                    .background(C.surface),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp),
                                    bitmap = fileQrImage,
                                    contentDescription = null
                                )
                            }
                        }
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
fun LogItem(
    modifier: Modifier = Modifier,
    filename: String,
    size: Long,
    onFocus: () -> Unit
) {
    ListItem(
        modifier = modifier
            .onFocusChanged {
                if (it.hasFocus) onFocus()
            },
        selected = false,
        onClick = { /*TODO*/ },
        headlineContent = {
            Text(text = filename)
        },
        supportingContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (filename.startsWith("logs_manual"))
                        stringResource(R.string.log_type_manual)
                    else
                        stringResource(R.string.log_type_crash)
                )
                Text(
                    text = "${size / 1024} KB"
                )
            }
        }
    )
}

@Composable
fun CreateLogItem(
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = modifier
            .onFocusChanged {
                if (it.hasFocus) onFocus()
            },
        selected = false,
        onClick = onClick,
        headlineContent = {
            Text(text = stringResource(R.string.log_save_now_button))
        }
    )
}

@Preview
@Composable
fun LogItemPreview() {
    BVTheme {
        LogItem(
            filename = "logs_manual_3202-11-11_08:16:23.log",
            size = 2145,
            onFocus = {}
        )
    }
}
