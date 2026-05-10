package dev.aaa1115910.bv.activities.video

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.aaa1115910.bv.activities.setThemedContentWhenStartupReady
import dev.aaa1115910.bv.screen.user.UpSpaceScreen

class UpInfoActivity : ComponentActivity() {
    companion object {
        fun actionStart(context: Context, mid: Long, name: String) {
            context.startActivity(
                Intent(context, UpInfoActivity::class.java).apply {
                    putExtra("mid", mid)
                    putExtra("name", name)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setThemedContentWhenStartupReady {
            UpSpaceScreen()
        }
    }
}
