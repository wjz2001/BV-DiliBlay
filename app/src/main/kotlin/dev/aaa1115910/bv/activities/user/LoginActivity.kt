package dev.aaa1115910.bv.activities.user

import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.aaa1115910.bv.activities.setContentWhenStartupReady
import dev.aaa1115910.bv.screen.login.LoginScreen
import dev.aaa1115910.bv.ui.theme.BVTheme

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWhenStartupReady {
            BVTheme {
                LoginScreen()
            }
        }
    }
}
