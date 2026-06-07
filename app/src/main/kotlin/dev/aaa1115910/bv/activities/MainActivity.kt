package dev.aaa1115910.bv.activities

import android.Manifest
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.repository.UserRepository
import dev.aaa1115910.bv.screen.LoginScreen
import dev.aaa1115910.bv.screen.MainScreen
import dev.aaa1115910.bv.ui.theme.AppBlack
import dev.aaa1115910.bv.ui.theme.AppWhite
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.ThemeMode
import dev.aaa1115910.bv.component.TvAlertDialog
import dev.aaa1115910.bv.ui.theme.AppRed
import dev.aaa1115910.bv.ui.theme.AppRedDark
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.util.ApiTestLoginExportUtil
import dev.aaa1115910.bv.util.LogCatcherUtil
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusSavedState
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.all
import dev.aaa1115910.bv.wjzfocus.wjzFocusExits
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.concurrent.atomic.AtomicBoolean

private val StoragePermissionConfirmNodeId = WjzFocusNodeId("storage-permission-confirm")
private val StoragePermissionDialogScopeId = WjzFocusScopeId("main/storage-permission")
private val StoragePermissionDialogContainerNodeId =
    WjzFocusNodeId("main/storage-permission/container")
private const val MainWjzFocusSavedStateKey = "main_wjz_focus_saved_state"

class MainActivity : ComponentActivity() {

    private val userRepository: UserRepository by inject()
    private val logger = KotlinLogging.logger {}
    private var mainWjzFocusSavedState: WjzFocusSavedState? = null
    private var mainWjzFocusStateProvider: (() -> WjzFocusSavedState)? = null

    //  控制系统 Splash 什么时候撤退
    private val composeReady = AtomicBoolean(false)
    private var pendingStartMainContent: (() -> Unit)? = null
    private val requestStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val onReady = pendingStartMainContent
            pendingStartMainContent = null
            onReady?.invoke()
            if (granted) LogCatcherUtil.syncLogsToDownloads()
        }

    enum class MainStartupPhase { Shell, RealUi }
    enum class AppScreen { Shell, Error, Main }

    override fun onCreate(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        mainWjzFocusSavedState =
            savedInstanceState?.getSerializable(MainWjzFocusSavedStateKey) as? WjzFocusSavedState

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
            var showStoragePermissionDialog by remember { mutableStateOf(false) }
            var retryNonce by remember { mutableIntStateOf(0) }
            var hasTriggeredAutoLogin by remember { mutableStateOf(false) }
            var allowFirstLaunchMainDialog by remember { mutableStateOf(false) }
            var showInitialLoginOverlay by remember { mutableStateOf(false) }
            var initialLoginOverlayClosing by remember { mutableStateOf(false) }

            LaunchedEffect(retryNonce) {
                phase = MainStartupPhase.Shell
                showStartupShell = true
                startupError = false
                startupErrorMessage = "启动失败"
                allowFirstLaunchMainDialog = false
                showInitialLoginOverlay = false
                initialLoginOverlayClosing = false

                runCatching {
                    val app = BVApp.instance
                    app?.startDeferredStartupWork(forceRestart = retryNonce > 0)
                    val startupReady = app?.awaitStartupWorkReady(timeoutMillis = 10_000) == true
                    if (!startupReady) error("Startup not ready")

                    // 启动时不再读取 user.lock
                    requestStoragePermissionBeforeStartMain(
                        onReady = {
                            phase = MainStartupPhase.RealUi
                        },
                        onPermissionDialogRequired = {
                            showStoragePermissionDialog = true
                        }
                    )
                }.onFailure {
                    logger.error(it) { "Main startup failed" }
                    startupError = true
                    showStartupShell = false
                }
            }

            LaunchedEffect(phase) {
                if (phase != MainStartupPhase.RealUi) return@LaunchedEffect
                if (hasTriggeredAutoLogin) {
                    if (!showInitialLoginOverlay && !initialLoginOverlayClosing) {
                        allowFirstLaunchMainDialog = true
                    }
                    return@LaunchedEffect
                }
                if (!Prefs.autoOpenLoginOnFirstLaunch) {
                    allowFirstLaunchMainDialog = true
                    return@LaunchedEffect
                }

                if (Prefs.autoOpenLoginOnFirstLaunch) {
                    hasTriggeredAutoLogin = true
                    Prefs.autoOpenLoginOnFirstLaunch = false
                    if (!userRepository.isLogin) {
                        allowFirstLaunchMainDialog = false
                        showInitialLoginOverlay = true
                        initialLoginOverlayClosing = false
                    } else {
                        allowFirstLaunchMainDialog = true
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
                            allowFirstLaunchMainDialog = allowFirstLaunchMainDialog,
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

                    if (showStoragePermissionDialog) {
                        StoragePermissionDialog(
                            onDismiss = {
                                showStoragePermissionDialog = false
                                continueStartMainContent()
                            },
                            onConfirm = {
                                showStoragePermissionDialog = false
                                requestStoragePermissionLauncher.launch(
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                            }
                        )
                    }

                    if (showInitialLoginOverlay) {
                        InitialLoginOverlay(
                            closing = initialLoginOverlayClosing,
                            onClose = {
                                initialLoginOverlayClosing = true
                            },
                            onClosed = {
                                showInitialLoginOverlay = false
                                initialLoginOverlayClosing = false
                                allowFirstLaunchMainDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mainWjzFocusSavedState = mainWjzFocusStateProvider?.invoke() ?: mainWjzFocusSavedState
        mainWjzFocusSavedState?.let { outState.putSerializable(MainWjzFocusSavedStateKey, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        mainWjzFocusSavedState = mainWjzFocusStateProvider?.invoke() ?: mainWjzFocusSavedState
        super.onStop()
    }

    private fun requestStoragePermissionBeforeStartMain(
        onReady: () -> Unit,
        onPermissionDialogRequired: () -> Unit
    ) {
        if (ApiTestLoginExportUtil.canWriteDownloadsWithoutRequest(this)) {
            onReady()
            return
        }
        if (!ApiTestLoginExportUtil.requiresLegacyWritePermission()) {
            onReady()
            return
        }
        if (!Prefs.showLogStoragePermissionDialog) {
            onReady()
            return
        }

        pendingStartMainContent = onReady
        Prefs.showLogStoragePermissionDialog = false
        onPermissionDialogRequired()
    }

    private fun continueStartMainContent() {
        val onReady = pendingStartMainContent
        pendingStartMainContent = null
        onReady?.invoke()
    }

    @Composable
    private fun InitialLoginOverlay(
        closing: Boolean,
        onClose: () -> Unit,
        onClosed: () -> Unit
    ) {
        val scale = remember { Animatable(1f) }
        val alpha = remember { Animatable(1f) }

        LaunchedEffect(closing) {
            if (!closing) return@LaunchedEffect
            coroutineScope {
                launch {
                    scale.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = 420,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
                launch {
                    alpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = 420,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
            onClosed()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(
                        pivotFractionX = 0.04f,
                        pivotFractionY = 0.06f
                    )
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                }
        ) {
            LoginScreen(
                modifier = Modifier.fillMaxSize(),
                onClose = onClose
            )
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
    private fun StoragePermissionDialog(
        onDismiss: () -> Unit,
        onConfirm: () -> Unit
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = LocalDensity.current.density * 1.5f,
                fontScale = LocalDensity.current.fontScale * 1.5f
            )
        ) {
            TvAlertDialog(
                // 禁用系统默认的弹窗宽度限制
                properties = DialogProperties(usePlatformDefaultWidth = false),
                onDismissRequest = onDismiss,
                dialogScopeId = StoragePermissionDialogScopeId,
                containerNodeId = StoragePermissionDialogContainerNodeId,
                title = { Text(text = "日志管理") },
                titleContentColor = C.onSurface,
                text = {
                    Text(text = "本应用需要获取存储权限才能把日志导出到用户目录下的 Download 文件夹内，这在特殊情况下很有用。如果你选择不给存储权限，可以直接按返回键，不会影响正常使用。")
                },
                textContentColor = C.onSurface,
                confirmButton = {
                    var hasFocus by remember { mutableStateOf(false) }

                    Button(
                        modifier = Modifier.wjzFocusExits(
                            nodeId = StoragePermissionConfirmNodeId,
                            layer = WjzFocusLayer.Dialog,
                            fallback = true,
                            exits = {
                                cancel(all)
                            },
                            onFocusChanged = { hasFocus = it }
                        ),
                        onClick = onConfirm,
                        colors = ButtonDefaults.colors(
                            containerColor = AppRed,
                            contentColor = AppWhite,
                            focusedContainerColor = AppRed,
                            focusedContentColor = AppWhite,
                            pressedContainerColor = AppRedDark,
                            pressedContentColor = AppWhite
                        )
                    ) {
                        Text(text = "同意")
                    }
                }
            )
        }
        }

    @Composable
    private fun MainScreenReady(
        allowFirstLaunchMainDialog: Boolean,
        onReady: () -> Unit
    ) {
        SideEffect {
            onReady()
        }
        MainScreen(
            allowFirstLaunchMainDialog = allowFirstLaunchMainDialog,
            initialWjzFocusSavedState = mainWjzFocusSavedState,
            onWjzFocusStateSaved = { state ->
                mainWjzFocusSavedState = state
            },
            onWjzFocusStateProviderChanged = { provider ->
                mainWjzFocusStateProvider = provider
            }
        )
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
