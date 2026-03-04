package dev.aaa1115910.bv.activities.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.aaa1115910.bv.network.HttpServer
import dev.aaa1115910.bv.screen.settings.LogsScreen
import dev.aaa1115910.bv.ui.theme.BVTheme

class LogsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        HttpServer.startServer()
        setContent {
            BVTheme {
                LogsScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        HttpServer.stopServer()
    }
}