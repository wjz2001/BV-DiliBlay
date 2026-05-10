package dev.aaa1115910.bv.activities.video

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.aaa1115910.bv.activities.setThemedContentWhenStartupReady
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.screen.SeasonInfoScreen

class SeasonInfoActivity : ComponentActivity() {
    companion object {
        fun actionStart(
            context: Context,
            epId: Int? = null,
            seasonId: Int? = null,
            proxyArea: ProxyArea = ProxyArea.MainLand
        ) {
            context.startActivity(
                Intent(context, SeasonInfoActivity::class.java).apply {
                    epId?.let { putExtra("epid", epId) }
                    seasonId?.let { putExtra("seasonid", seasonId) }
                    putExtra("proxy_area", proxyArea.ordinal)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setThemedContentWhenStartupReady {
            SeasonInfoScreen()
        }
    }
}
