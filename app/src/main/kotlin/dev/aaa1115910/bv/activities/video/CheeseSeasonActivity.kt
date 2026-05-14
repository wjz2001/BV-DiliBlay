package dev.aaa1115910.bv.activities.video

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.aaa1115910.bv.activities.setThemedContentWhenStartupReady
import dev.aaa1115910.bv.screen.main.home.CheeseSeasonScreen
import dev.aaa1115910.bv.viewmodel.video.CheeseSeasonViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class CheeseSeasonActivity : ComponentActivity() {
    companion object {
        fun actionStart(
            context: Context,
            seasonId: Long
        ) {
            context.startActivity(
                Intent(context, CheeseSeasonActivity::class.java).apply {
                    putExtra("season_id", seasonId)
                }
            )
        }
    }

    private val cheeseSeasonViewModel: CheeseSeasonViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setThemedContentWhenStartupReady(
            onReady = { cheeseSeasonViewModel.init(intent.getLongExtra("season_id", 0L)) }
        ) {
            CheeseSeasonScreen()
        }
    }
}
