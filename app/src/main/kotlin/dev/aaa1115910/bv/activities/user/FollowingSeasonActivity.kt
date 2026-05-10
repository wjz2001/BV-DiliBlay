package dev.aaa1115910.bv.activities.user

import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.aaa1115910.bv.activities.setThemedContentWhenStartupReady
import dev.aaa1115910.bv.screen.user.FollowingSeasonScreen

class FollowingSeasonActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setThemedContentWhenStartupReady {
            FollowingSeasonScreen()
        }
    }
}
