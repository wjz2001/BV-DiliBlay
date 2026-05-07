package dev.aaa1115910.bv.activities.user

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.aaa1115910.bv.activities.setContentWhenStartupReady
import dev.aaa1115910.bv.screen.user.lock.UserLockSettingsScreen
import dev.aaa1115910.bv.ui.theme.BVTheme

class UserLockSettingsActivity : ComponentActivity() {

    companion object {
        fun actionStart(
            context: Context,
            uid: Long
        ) {
            context.startActivity(
                Intent(context, UserLockSettingsActivity::class.java).apply {
                    putExtra("uid", uid)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWhenStartupReady {
            BVTheme {
                UserLockSettingsScreen()
            }
        }
    }
}
