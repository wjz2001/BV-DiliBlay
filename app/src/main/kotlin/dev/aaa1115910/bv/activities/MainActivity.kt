package dev.aaa1115910.bv.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.user.LoginActivity
import dev.aaa1115910.bv.repository.UserRepository
import dev.aaa1115910.bv.screen.MainScreen
import dev.aaa1115910.bv.ui.theme.AppBlack
import dev.aaa1115910.bv.ui.theme.AppWhite
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.ThemeMode
import dev.aaa1115910.bv.util.Prefs
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.android.ext.android.inject
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {

    private val userRepository: UserRepository by inject()
    private val logger = KotlinLogging.logger {}

    //  控制系统 Splash 什么时候撤退
    private val composeReady = AtomicBoolean(false)

    enum class MainStartupPhase { Shell, RealUi }
    enum class AppScreen { Shell, Error, Main }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().apply {
            // 只要 Compose 还没画好，就先用纯色屏幕
            setKeepOnScreenCondition { !composeReady.get() }
        }
        super.onCreate(savedInstanceState)

        setContent {
            var phase by remember { mutableStateOf(MainStartupPhase.Shell) }
            var showStartupShell by remember { mutableStateOf(true) }
            var startupError by remember { mutableStateOf(false) }
            var startupErrorMessage by remember { mutableStateOf("启动失败") }
            var retryNonce by remember { mutableIntStateOf(0) }

            LaunchedEffect(retryNonce) {
                phase = MainStartupPhase.Shell
                showStartupShell = true
                startupError = false
                startupErrorMessage = "启动失败"

                runCatching {
                    val app = BVApp.instance
                    app?.startDeferredStartupWork(forceRestart = retryNonce > 0)
                    val startupReady = app?.awaitStartupWorkReady(timeoutMillis = 10_000) == true
                    if (!startupReady) error("Startup not ready")

                    // 启动时不再读取 user.lock
                    phase = MainStartupPhase.RealUi
                }.onFailure {
                    logger.error(it) { "Main startup failed" }
                    startupError = true
                    showStartupShell = false
                }
            }

            var hasTriggeredAutoLogin by remember { mutableStateOf(false) }
            LaunchedEffect(phase) {
                if (phase != MainStartupPhase.RealUi) return@LaunchedEffect
                if (hasTriggeredAutoLogin) return@LaunchedEffect
                if (!Prefs.autoOpenLoginOnFirstLaunch) return@LaunchedEffect

                if (Prefs.autoOpenLoginOnFirstLaunch) {
                    hasTriggeredAutoLogin = true
                    Prefs.autoOpenLoginOnFirstLaunch = false
                    if (!userRepository.isLogin) {
                        startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    }
                }
            }

            val currentScreen = remember(startupError, phase) {
                when {
                    startupError -> AppScreen.Error
                    phase == MainStartupPhase.RealUi -> AppScreen.Main
                    else -> AppScreen.Shell
                }
            }

            BVTheme {
                // 采用 Box 堆叠模式
                Box(modifier = Modifier.fillMaxSize()) {

                    // 底层：实际的应用界面
                    // 启动链完成后挂载主页，避免 Prefs 未就绪时读到默认值
                    when (currentScreen) {
                        AppScreen.Main -> MainScreenReady(
                            onReady = {
                                showStartupShell = false
                            }
                        )
                        AppScreen.Error -> MainStartupError(
                            message = startupErrorMessage,
                            onRetry = { retryNonce++ }
                        )

                        else -> {
                            // Shell 状态下，底层是空的，什么也不做
                        }
                    }

                    // 顶层：开屏 Banner (遮罩层)
                    // 启动检查完成后触发 fadeOut 渐渐消失
                    AnimatedVisibility(
                        visible = showStartupShell,
                        enter = fadeIn(),
                        exit = fadeOut(
                            // 淡出时间
                            animationSpec = tween(durationMillis = 800)
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        MainStartupShell()
                    }
                }
            }
        }
    }

    @Composable
    private fun MainStartupShell() {
        SideEffect {
            composeReady.set(true)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // 把背景改成和系统 Splash一样，可以无缝的转换
                .background(Color(0xFF0092E4)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_banner),
                contentDescription = "Logo",
                modifier = Modifier.fillMaxSize(),
                alignment = Alignment.Center,
                contentScale = ContentScale.Crop
            )
        }
    }

    @Composable
    private fun MainScreenReady(
        onReady: () -> Unit
    ) {
        SideEffect {
            onReady()
        }
        MainScreen()
    }
}

@Composable
private fun MainStartupError(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (Prefs.themeMode == ThemeMode.DARK) AppBlack else AppWhite),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(text = message)
            Button(onClick = onRetry) {
                Text(text = "重试")
            }
        }
    }
}
