package dev.aaa1115910.bv.activities.video

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.aaa1115910.bv.activities.setContentWhenStartupReady
import dev.aaa1115910.bv.screen.CheeseSeasonScreen
import dev.aaa1115910.bv.ui.theme.BVTheme
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
        setContentWhenStartupReady(
            onReady = { cheeseSeasonViewModel.init(intent.getLongExtra("season_id", 0L)) }
        ) {
            BVTheme {
                CheeseSeasonScreen()
            }
        }
    }
}
