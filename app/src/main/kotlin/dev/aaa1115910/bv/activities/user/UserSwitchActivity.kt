package dev.aaa1115910.bv.activities.user

import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.aaa1115910.bv.activities.setContentWhenStartupReady
import dev.aaa1115910.bv.screen.user.UserSwitchScreen
import dev.aaa1115910.bv.ui.theme.BVTheme

class UserSwitchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWhenStartupReady {
            BVTheme {
                UserSwitchScreen()
            }
        }
    }
}
