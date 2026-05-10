package dev.aaa1115910.bv.activities.pgc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.aaa1115910.biliapi.entity.pgc.PgcType
import dev.aaa1115910.bv.activities.setThemedContentWhenStartupReady
import dev.aaa1115910.bv.screen.main.pgc.PgcIndexScreen

class PgcIndexActivity : ComponentActivity() {
    companion object {
        fun actionStart(
            context: Context,
            pgcType: PgcType
        ) {
            context.startActivity(
                Intent(context, PgcIndexActivity::class.java).apply {
                    putExtra("pgcType", pgcType.ordinal)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setThemedContentWhenStartupReady {
            PgcIndexScreen()
        }
    }
}
