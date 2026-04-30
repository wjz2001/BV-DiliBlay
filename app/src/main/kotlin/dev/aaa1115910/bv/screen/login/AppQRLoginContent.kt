package dev.aaa1115910.bv.screen.login

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.KeyEvent
import android.widget.Toast.LENGTH_LONG
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.login.QrLoginState
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.ui.theme.AppBlack
import dev.aaa1115910.bv.ui.theme.AppRed
import dev.aaa1115910.bv.ui.theme.AppWhite
import dev.aaa1115910.bv.util.ApiTestLoginExportPayload
import dev.aaa1115910.bv.util.ApiTestLoginExportUtil
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.login.AppQrLoginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

@Composable
fun AppQRLoginContent(
    modifier: Modifier = Modifier,
    appQrLoginViewModel: AppQrLoginViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var exportResultText by remember { mutableStateOf<String?>(null) }
    var permissionPendingPayload by remember { mutableStateOf<ApiTestLoginExportPayload?>(null) }
    val loginSuccessText = stringResource(R.string.login_success)
    val exportPermissionDeniedText = stringResource(R.string.login_export_permission_denied)
    val exportSuccessFormat = stringResource(R.string.login_export_success, "%s")
    val exportFailedFormat = stringResource(R.string.login_export_failed, "%s")

    suspend fun exportPayload(payload: ApiTestLoginExportPayload) {
        val result = withContext(Dispatchers.IO) {
            ApiTestLoginExportUtil.exportToDownloads(context, payload)
        }
        val message = result.fold(
            onSuccess = { exportSuccessFormat.format(it) },
            onFailure = { exportFailedFormat.format(it.message ?: it.javaClass.simpleName) }
        )
        exportResultText = message
        message.toast(context, LENGTH_LONG)
        appQrLoginViewModel.clearPendingApiTestExport()
    }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val payload = permissionPendingPayload
        permissionPendingPayload = null
        if (granted && payload != null) {
            coroutineScope.launch {
                exportPayload(payload)
            }
        } else {
            exportResultText = exportPermissionDeniedText
            exportPermissionDeniedText.toast(context, LENGTH_LONG)
            appQrLoginViewModel.clearPendingApiTestExport()
        }
    }

    fun requestQRCode() {
        exportResultText = null
        permissionPendingPayload = null
        appQrLoginViewModel.requestQRCode()
    }

    LaunchedEffect(Unit) {
        requestQRCode()
    }

    LaunchedEffect(appQrLoginViewModel.pendingApiTestExport) {
        val payload = appQrLoginViewModel.pendingApiTestExport ?: return@LaunchedEffect
        if (ApiTestLoginExportUtil.requiresLegacyWritePermission() &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionPendingPayload = payload
            legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            exportPayload(payload)
        }
    }

    // 用“事件组”去重：避免 Ready/RequestingQRCode 状态切换时重复弹同一句
    val lastToastEventId = remember { mutableIntStateOf(-1) }

    LaunchedEffect(appQrLoginViewModel.state) {
        val eventId = when (appQrLoginViewModel.state) {
            QrLoginState.Ready, QrLoginState.RequestingQRCode -> 1
            QrLoginState.Expired -> 2
            QrLoginState.Error, QrLoginState.Unknown -> 3
            else -> -1
        }

        if (eventId != -1 && eventId == lastToastEventId.intValue) return@LaunchedEffect
        if (eventId != -1) lastToastEventId.intValue = eventId

        when (appQrLoginViewModel.state) {
            QrLoginState.Ready,
            QrLoginState.RequestingQRCode -> {
                R.string.login_requesting.toast(context, LENGTH_LONG)
            }

            QrLoginState.Expired -> {
                R.string.login_expired.toast(context, LENGTH_LONG)
                delay(3500L) // “紧接着”弹下一条：避免被立刻覆盖
                R.string.login_retry.toast(context, LENGTH_LONG)
            }

            QrLoginState.Error,
            QrLoginState.Unknown -> {
                R.string.login_error.toast(context, LENGTH_LONG)
                delay(3500L)
                R.string.login_retry.toast(context, LENGTH_LONG)
            }

            QrLoginState.Success -> {
                if (BuildConfig.ENABLE_API_TEST_LOGIN_DUMP) {
                    loginSuccessText.toast(context, LENGTH_LONG)
                } else {
                    (context as? Activity)?.finish()
                }
            }

            else -> Unit // WaitingForScan / WaitingForConfirm 等：不弹
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            appQrLoginViewModel.cancelCheckLoginResultTimer()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBlack),
    ) {
        Box(
            modifier = modifier
                .focusable()
                .fillMaxSize()
                .onKeyEvent {
                    if (it.key.nativeKeyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                        if (listOf(QrLoginState.Expired, QrLoginState.Error)
                                .contains(appQrLoginViewModel.state)
                        ) {
                            requestQRCode()
                        }
                        return@onKeyEvent true
                    }
                    false
                },
            contentAlignment = Alignment.Center
        ) {
            val qrStateReady = appQrLoginViewModel.state in listOf(
                QrLoginState.WaitingForScan,
                QrLoginState.WaitingForConfirm
            )
            val qrBitmapReady = appQrLoginViewModel.qrImage.width > 1 && appQrLoginViewModel.qrImage.height > 1
            val showLoginGroup = qrStateReady && qrBitmapReady
            AnimatedVisibility(visible = showLoginGroup) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SideTwoCells(
                        topText = "登",
                        topColor = Color.Cyan,
                        bottomText = "录",
                        bottomColor = Color.Yellow,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f)
                                .background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White)
                                    .then(Modifier)
                                    .padding(56.dp),
                                bitmap = appQrLoginViewModel.qrImage,
                                contentDescription = null
                            )
                        }

                    SideTwoCells(
                        topText = "扫",
                        topColor = AppRed,
                        bottomText = "码",
                        bottomColor = Color.Green,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
            AnimatedVisibility(
                visible = BuildConfig.ENABLE_API_TEST_LOGIN_DUMP &&
                    appQrLoginViewModel.state == QrLoginState.Success,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = loginSuccessText,
                        style = MaterialTheme.typography.displaySmall,
                        color = AppWhite
                    )
                    Text(
                        modifier = Modifier.padding(top = 16.dp),
                        text = exportResultText.orEmpty(),
                        style = MaterialTheme.typography.displaySmall,
                        color = AppWhite,
                        fontSize = 26.sp
                    )
                }
            }
        }
    }
}

private val QrLoginFontFamily = FontFamily(
    Font(R.font.qr_login, weight = FontWeight.Normal)
)
@Composable
private fun SideTwoCells(
    topText: String,
    bottomText: String,
    topColor: Color,
    bottomColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .weight(1f)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = topText,
                color = topColor,
                fontSize = 108.sp,
                style = MaterialTheme.typography.displayLarge.copy(fontFamily = QrLoginFontFamily)
            )
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .weight(1f)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = bottomText,
                color = bottomColor,
                fontSize = 108.sp,
                style = MaterialTheme.typography.displayLarge.copy(fontFamily = QrLoginFontFamily)
            )
        }
    }
}
