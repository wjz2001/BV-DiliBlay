package dev.aaa1115910.bv.activities.video

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import dev.aaa1115910.biliapi.entity.user.Author
import dev.aaa1115910.bv.activities.StartupGate
import dev.aaa1115910.bv.activities.setThemedContentWhenStartupReady
import dev.aaa1115910.bv.entity.VideoSource
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.screen.VideoPlayerV3Screen
import dev.aaa1115910.bv.util.BvLog
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.viewmodel.player.VideoPlayerV3ViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class VideoPlayerV3Activity : ComponentActivity() {
    private val playerViewModel: VideoPlayerV3ViewModel by viewModel()

    private var initialLoadDispatched = false
    private var startupReady = false

    companion object {
        private val logger = KotlinLogging.logger { }
        private var currentInstance: VideoPlayerV3Activity? = null

        fun actionStart(
            context: Context,
            avid: Long,
            cid: Long,
            title: String,
            partTitle: String,
            played: Long,
            source: VideoSource = VideoSource.Ugc,
            subType: Int? = null,
            epid: Int? = null,
            seasonId: Int? = null,
            proxyArea: ProxyArea = ProxyArea.MainLand,
            author: Author? = null,
            enableUgcDetailBackStack: Boolean = false
        ) {
            currentInstance?.finish()
            context.startActivity(
                Intent(context, VideoPlayerV3Activity::class.java).apply {
                    putExtra("avid", avid)
                    putExtra("cid", cid)
                    putExtra("title", title)
                    putExtra("partTitle", partTitle)
                    putExtra("played", played)
                    putExtra("video_source", source.name)
                    putExtra("subType", subType)
                    putExtra("epid", epid)
                    putExtra("seasonId", seasonId)
                    putExtra("proxy_area", proxyArea.ordinal)
                    putExtra("author_mid", author?.mid)
                    putExtra("author_name", author?.name)
                    putExtra("enable_ugc_detail_back_stack", enableUgcDetailBackStack)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentInstance = this
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setThemedContentWhenStartupReady(
            gate = StartupGate.Prefs,
            onReady = {
                initViewModelFromIntent()
                playerViewModel.initVideoPlayer(applicationContext)
                startupReady = true
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    playerViewModel.onHostStartFastResumeOrRecreate()
                }
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    dispatchInitialLoadIfNeeded()
                }
            }
        ) {
            VideoPlayerV3Screen()
        }
    }

    override fun onStart() {
        // 先进入抑制期，再走 super，覆盖 Lifecycle.ON_START 期间可能发生的 surface 相关竞态
        playerViewModel.setSuppressPlayerErrors(true)
        BvLog.i("BugDebug", "VideoPlayerV3Activity onStart: suppressPlayerErrors=true (resuming)")

        super.onStart()

        if (!startupReady) return

        // 快恢复（不重建）或按需重建
        playerViewModel.onHostStartFastResumeOrRecreate()

        lifecycleScope.launch {
            delay(500)
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                playerViewModel.setSuppressPlayerErrors(false)
                BvLog.i("BugDebug", "VideoPlayerV3Activity onStart: suppressPlayerErrors=false")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!startupReady) return

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        dispatchInitialLoadIfNeeded()
    }

    override fun onPause() {
        // 先进入抑制期，再走 super，覆盖 Lifecycle.ON_PAUSE 期间的 surface detach 竞态
        playerViewModel.setSuppressPlayerErrors(true)
        BvLog.i("BugDebug", "VideoPlayerV3Activity onPause: suppressPlayerErrors=true")

        super.onPause()

        // 恢复状态栏
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())

        playerViewModel.videoPlayer?.pause()
    }

    override fun onStop() {
        // 尽量早做，避免 stop 阶段 surfaceDestroyed 触发后再处理
        BvLog.i("BugDebug", "VideoPlayerV3Activity onStop: isFinishing=$isFinishing")

        if (!isFinishing) {
            playerViewModel.onHostStopFastResume()
        } else {
            // 退出 Activity 时也抑制错误，避免“退出前闪抽风”
            playerViewModel.setSuppressPlayerErrors(true)
        }

        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentInstance === this) {
            currentInstance = null
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (isFinishing) {
            playerViewModel.detachPlayer()
        }
    }

    private fun initViewModelFromIntent() {
        if (intent.hasExtra("avid")) {
            val aid = intent.getLongExtra("avid", 170001)
            val cid = intent.getLongExtra("cid", 170001)
            val title = intent.getStringExtra("title") ?: "Unknown Title"
            val partTitle = intent.getStringExtra("partTitle").orEmpty()
            val played = intent.getLongExtra("played", 0)
            val source = intent.getStringExtra("video_source")
                ?.let { value -> VideoSource.entries.firstOrNull { it.name == value } }
                ?: VideoSource.Ugc
            val subType = intent.getIntExtra("subType", 0)
            val epid = intent.getIntExtra("epid", 0)
            val seasonId = intent.getIntExtra("seasonId", 0)
            val proxyArea = ProxyArea.entries[intent.getIntExtra("proxy_area", 0)]
            val authorMid = intent.getLongExtra("author_mid", 0)
            val authorName = intent.getStringExtra("author_name")
            val enableUgcDetailBackStack =
                intent.getBooleanExtra("enable_ugc_detail_back_stack", false)

            logger.fInfo {
                "Launch parameter: [aid=$aid, cid=$cid, enableUgcDetailBackStack=$enableUgcDetailBackStack]"
            }

            playerViewModel.init(
                aid = aid,
                cid = cid,
                epid = epid.takeIf { it != 0 },
                title = title,
                partTitle = partTitle,
                lastPlayed = played,
                source = source,
                subType = subType,
                seasonId = seasonId,
                proxyArea = proxyArea,
                authorMid = authorMid,
                authorName = authorName ?: "",
                enableUgcDetailBackStack = enableUgcDetailBackStack
            )
        } else {
            logger.fInfo { "Null launch parameter" }
        }
    }

    private fun dispatchInitialLoadIfNeeded() {
        if (initialLoadDispatched) return
        initialLoadDispatched = true
        BvLog.i("BugDebug", "VideoPlayerV3Activity: schedule initial load")

        window.decorView.post {
            window.decorView.post {
                if (!isFinishing && !isDestroyed) {
                    BvLog.i("BugDebug", "VideoPlayerV3Activity: initial loadVideoWithResources()")
                    playerViewModel.loadVideoWithResources()
                }
            }
        }
    }
}
