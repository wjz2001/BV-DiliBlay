package dev.aaa1115910.bv.activities.video

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.aaa1115910.bv.activities.setThemedContentWhenStartupReady
import dev.aaa1115910.bv.entity.VideoSource
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.screen.VideoInfoScreen
import dev.aaa1115910.bv.viewmodel.video.VideoDetailViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class VideoInfoActivity : ComponentActivity() {
    companion object {
        fun actionStart(
            context: Context,
            aid: Long = 0,
            source: VideoSource = VideoSource.Ugc,
            epid: Int? = null,
            seasonId: Long? = null,
            fromController: Boolean = false,
            proxyArea: ProxyArea = ProxyArea.MainLand
        ) {
            when (source) {
                VideoSource.Ugc -> {
                    context.startActivity(
                        Intent(context, VideoInfoActivity::class.java).apply {
                            putExtra("aid", aid)
                            putExtra("fromController", fromController)
                            putExtra("proxy_area", proxyArea.ordinal)
                        }
                    )
                }

                VideoSource.Pgc -> {
                    SeasonInfoActivity.actionStart(
                        context = context,
                        epId = epid,
                        seasonId = seasonId?.toInt(),
                        proxyArea = proxyArea
                    )
                }

                VideoSource.Cheese -> {
                    val targetSeasonId = requireNotNull(seasonId) { "Cheese detail requires seasonId" }
                    CheeseSeasonActivity.actionStart(
                        context = context,
                        seasonId = targetSeasonId
                    )
                }
            }
        }
    }

    private val videoDetailViewModel: VideoDetailViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setThemedContentWhenStartupReady(
            onReady = { getParamsFromIntent() }
        ) {
            VideoInfoScreen()
        }
    }

    private fun getParamsFromIntent() {
        if (intent.hasExtra("aid")) {
            val aid = intent.getLongExtra("aid", 170001)
            val fromController = intent.getBooleanExtra("fromController", false)
            val proxyArea = ProxyArea.entries[intent.getIntExtra("proxy_area", 0)]

            videoDetailViewModel.init(
                aid = aid,
                fromController = fromController,
                proxyArea = proxyArea,
            )
        }
    }
}
