package dev.aaa1115910.bv.activities.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.aaa1115910.bv.activities.setThemedContentWhenStartupReady
import dev.aaa1115910.bv.screen.settings.SettingsScreen

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setThemedContentWhenStartupReady {
            SettingsScreen()
        }
    }
}
