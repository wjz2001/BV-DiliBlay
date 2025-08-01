package dev.aaa1115910.bv.screen.settings.content

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.settings.LogsActivity
import dev.aaa1115910.bv.component.settings.CookiesDialog
import dev.aaa1115910.bv.component.settings.SettingListItem
import dev.aaa1115910.bv.component.settings.SettingSwitchListItem
import dev.aaa1115910.bv.entity.Audio
import dev.aaa1115910.bv.screen.settings.SettingsMenuNavItem
import dev.aaa1115910.bv.util.FirebaseUtil
import dev.aaa1115910.bv.util.Prefs

@Composable
fun OtherSetting(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showCookiesDialog by remember { mutableStateOf(false) }
    var showPreferedApiDialog by remember { mutableStateOf(false) }

    var showFps by remember { mutableStateOf(Prefs.showFps) }
    var useOldPlayer by remember { mutableStateOf(Prefs.useOldPlayer) }
    var updateAlpha by remember { mutableStateOf(Prefs.updateAlpha) }
    var enableFfmpegAudioRenderer by remember { mutableStateOf(Prefs.enableFfmpegAudioRenderer) }
    var selectedApi by remember { mutableStateOf(Prefs.apiType.name) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = SettingsMenuNavItem.Other.getDisplayName(context),
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(modifier = Modifier.height(12.dp))

        SettingListItem(
            title = "接口选择",
            supportText = "当前：$selectedApi",
            onClick = { showPreferedApiDialog = true }
        )
        SettingSwitchListItem(
            title = stringResource(R.string.settings_other_firebase_title),
            supportText = stringResource(R.string.settings_other_firebase_text),
            checked = Prefs.enableFirebaseCollection,
            onCheckedChange = {
                Prefs.enableFirebaseCollection = it
                FirebaseUtil.setCrashlyticsCollectionEnabled(it)
            }
        )


        SettingListItem(
            title = stringResource(R.string.settings_other_cookies_title),
            supportText = stringResource(R.string.settings_other_cookies_text),
            onClick = { showCookiesDialog = true }
        )

        SettingSwitchListItem(
            title = stringResource(R.string.settings_other_fps_title),
            supportText = stringResource(R.string.settings_other_fps_text),
            checked = showFps,
            onCheckedChange = {
                showFps = it
                Prefs.showFps = it
            }
        )

        SettingSwitchListItem(
            title = stringResource(R.string.settings_other_old_player_title),
            supportText = stringResource(R.string.settings_other_old_player_text),
            checked = useOldPlayer,
            onCheckedChange = {
                useOldPlayer = it
                Prefs.useOldPlayer = it
            }
        )

//            item {
//                SettingSwitchListItem(
//                    title = stringResource(R.string.settings_other_alpha_title),
//                    supportText = stringResource(R.string.settings_other_alpha_text),
//                    checked = updateAlpha,
//                    onCheckedChange = {
//                        updateAlpha = it
//                        Prefs.updateAlpha = it
//                    }
//                )
//            }

        SettingListItem(
            title = stringResource(R.string.settings_create_logs_title),
            supportText = stringResource(R.string.settings_create_logs_text),
            onClick = {
                context.startActivity(Intent(context, LogsActivity::class.java))
            }
        )

        if (BuildConfig.DEBUG) {
            SettingListItem(
                title = stringResource(R.string.settings_crash_test_title),
                supportText = stringResource(R.string.settings_crash_test_text),
                onClick = {
                    throw Exception("Boom!")
                }
            )

        }

        SettingSwitchListItem(
            title = stringResource(R.string.settings_other_ffmpeg_audio_renderer_title),
            supportText = stringResource(R.string.settings_other_ffmpeg_audio_renderer_text),
            checked = enableFfmpegAudioRenderer,
            onCheckedChange = {
                enableFfmpegAudioRenderer = it
                Prefs.enableFfmpegAudioRenderer = it
            }
        )
    }
    CookiesDialog(
        show = showCookiesDialog,
        onHideDialog = { showCookiesDialog = false }
    )

    if (showPreferedApiDialog) {
        OptionDialog(
            options = ApiType.entries.toTypedArray(),
            selectedOption = Prefs.apiType,
            onDismiss = { showPreferedApiDialog = false },
            onSelect = {
                Prefs.apiType = it
                selectedApi = it.name
            },
            getDisplayName = { it.name }
        )
    }
}