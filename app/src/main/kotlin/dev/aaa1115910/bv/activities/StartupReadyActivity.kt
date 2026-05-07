package dev.aaa1115910.bv.activities

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.ui.theme.BVTheme

private const val PREFS_READY_TIMEOUT_MILLIS = 2_000L
private const val STARTUP_WORK_READY_TIMEOUT_MILLIS = 10_000L

private enum class StartupReadyState { Loading, Ready, Error }

enum class StartupGate { Prefs, StartupWork }

fun ComponentActivity.setContentWhenStartupReady(
    gate: StartupGate = StartupGate.Prefs,
    onReady: () -> Unit = {},
    content: @Composable () -> Unit
) {
    setContent {
        var state by remember { mutableStateOf(StartupReadyState.Loading) }
        var retryNonce by remember { mutableIntStateOf(0) }

        LaunchedEffect(retryNonce) {
            state = StartupReadyState.Loading
            val ready = runCatching {
                val app = BVApp.instance
                app?.startDeferredStartupWork(forceRestart = retryNonce > 0)
                when (gate) {
                    StartupGate.Prefs -> app?.awaitPrefsReady(PREFS_READY_TIMEOUT_MILLIS) == true
                    StartupGate.StartupWork -> app?.awaitStartupWorkReady(STARTUP_WORK_READY_TIMEOUT_MILLIS) == true
                }
            }.getOrDefault(false)

            if (ready) {
                runCatching { onReady() }
                    .onSuccess { state = StartupReadyState.Ready }
                    .onFailure { state = StartupReadyState.Error }
            } else {
                state = StartupReadyState.Error
            }
        }

        BVTheme {
            when (state) {
                StartupReadyState.Loading -> StartupReadyShell()
                StartupReadyState.Error -> StartupReadyError(
                    onRetry = { retryNonce++ }
                )

                StartupReadyState.Ready -> content()
            }
        }
    }
}

@Composable
private fun StartupReadyShell() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    )
}

@Composable
private fun StartupReadyError(
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(text = "启动失败")
            Button(onClick = onRetry) {
                Text(text = "重试")
            }
        }
    }
}
