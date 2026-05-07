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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.aaa1115910.bv.screen.user.lock.UnlockUserScreen
import dev.aaa1115910.bv.ui.theme.AppBlack
import dev.aaa1115910.bv.ui.theme.AppWhite
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.ThemeMode
import dev.aaa1115910.bv.util.Prefs
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val userRepository: UserRepository by inject()
    private val logger = KotlinLogging.logger {}

    enum class MainStartupPhase { Shell, RealUi }
    enum class LockState { Unknown, Locked, Unlocked }
    enum class AppScreen { Shell, Error, Main, Unlock }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().apply {
            setKeepOnScreenCondition { false }
        }
        super.onCreate(savedInstanceState)

        setContent {
            var phase by remember { mutableStateOf(MainStartupPhase.Shell) }
            var lockState by remember { mutableStateOf(LockState.Unknown) }
            var startupError by remember { mutableStateOf(false) }
            var startupErrorMessage by remember { mutableStateOf("启动失败") }
            var retryNonce by remember { mutableIntStateOf(0) }

            LaunchedEffect(retryNonce) {
                phase = MainStartupPhase.Shell
                lockState = LockState.Unknown
                startupError = false
                startupErrorMessage = "启动失败"

                runCatching {
                    withFrameNanos {}

                    val app = BVApp.instance
                    app?.startDeferredStartupWork()
                    val prefsReady = app?.awaitPrefsReady(timeoutMillis = 1500) == true
                    if (!prefsReady) error("Prefs not ready")

                    val user = withContext(Dispatchers.IO) {
                        userRepository.findUserByUid(Prefs.uid)
                    }
                    logger.info { "default user: ${user?.username}" }

                    lockState = if (user?.lock?.isNotBlank() == true) {
                        LockState.Locked
                    } else {
                        LockState.Unlocked
                    }
                    phase = MainStartupPhase.RealUi
                }.onFailure {
                    logger.error(it) { "Main startup failed" }
                    startupError = true
                }
            }

            var hasTriggeredAutoLogin by remember { mutableStateOf(false) }
            LaunchedEffect(phase, lockState) {
                if (phase != MainStartupPhase.RealUi) return@LaunchedEffect
                if (lockState != LockState.Unlocked || hasTriggeredAutoLogin) return@LaunchedEffect
                if (!Prefs.autoOpenLoginOnFirstLaunch) return@LaunchedEffect

                if (Prefs.autoOpenLoginOnFirstLaunch) {
                    hasTriggeredAutoLogin = true
                    Prefs.autoOpenLoginOnFirstLaunch = false
                    if (!userRepository.isLogin) {
                        startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    }
                }
            }

            val currentScreen = remember(startupError, phase, lockState) {
                when {
                    startupError -> AppScreen.Error
                    phase == MainStartupPhase.Shell -> AppScreen.Shell
                    phase == MainStartupPhase.RealUi -> {
                        when (lockState) {
                            LockState.Unlocked -> AppScreen.Main
                            LockState.Locked -> AppScreen.Unlock
                            LockState.Unknown -> AppScreen.Shell
                        }
                    }

                    else -> AppScreen.Shell
                }
            }

            BVTheme {
                // 采用 Box 堆叠模式
                Box(modifier = Modifier.fillMaxSize()) {

                    // 底层：实际的应用界面
                    // 一旦状态变为 RealUi，这里就会立刻挂载并开始加载主页内部的请求
                    when (currentScreen) {
                        AppScreen.Main -> MainScreen()
                        AppScreen.Unlock -> UnlockUserScreen(
                            onUnlockSuccess = { user ->
                                logger.info { "unlock user lock for user ${user.uid}" }
                                lockState = LockState.Unlocked
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
                    // 当 currentScreen 不是 Shell 时，它会触发 fadeOut 渐渐消失
                    AnimatedVisibility(
                        visible = currentScreen == AppScreen.Shell,
                        enter = fadeIn(),
                        exit = fadeOut(
                            // 淡出时间
                            animationSpec = tween(durationMillis = 500)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (Prefs.themeMode == ThemeMode.DARK) AppBlack else AppWhite),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_banner),
                contentDescription = "Banner",
                modifier = Modifier.fillMaxSize(),
                alignment = Alignment.Center,
                contentScale = ContentScale.Crop
            )
        }
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
