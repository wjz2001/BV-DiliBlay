package dev.aaa1115910.bv.activities.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.aaa1115910.bv.activities.setThemedContentWhenStartupReady
import dev.aaa1115910.bv.screen.settings.SettingsScreen
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.wjzfocus.WjzFocusDebugConfig
import dev.aaa1115910.bv.wjzfocus.WjzFocusDebugOverlayRegistry
import dev.aaa1115910.bv.wjzfocus.WjzFocusLogLevel

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installWjzFocusR8TestDebugOverlay()
        WjzFocusDebugOverlayRegistry.installDefault(Prefs.wjzFocusDebugOverlay)
        WjzFocusDebugConfig.logLevel = WjzFocusLogLevel.entries
            .getOrElse(Prefs.wjzFocusLogLevel) { WjzFocusLogLevel.Off }
        setThemedContentWhenStartupReady {
            SettingsScreen()
        }
    }

    override fun onDestroy() {
        WjzFocusDebugOverlayRegistry.clear()
        super.onDestroy()
    }

    private fun installWjzFocusR8TestDebugOverlay() {
        runCatching {
            Class.forName("dev.aaa1115910.bv.r8test.WjzFocusR8TestDebugOverlayInstaller")
                .getMethod("install")
                .invoke(null)
        }
    }
}
