package dev.aaa1115910.bv.activities.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.aaa1115910.bv.activities.setThemedContentWhenStartupReady
import dev.aaa1115910.bv.network.HttpServer
import dev.aaa1115910.bv.screen.settings.LogsScreen

class LogsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setThemedContentWhenStartupReady(
            onReady = { HttpServer.startServer() }
        ) {
            LogsScreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        HttpServer.stopServer()
    }
}
