package dev.aaa1115910.bv.screen.settings.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.component.controllers2.playermenu.PlaySpeedItem
import dev.aaa1115910.bv.component.settings.SettingListItem
import dev.aaa1115910.bv.component.settings.SettingsMenuSelectItem
import dev.aaa1115910.bv.entity.Audio
import dev.aaa1115910.bv.entity.Resolution
import dev.aaa1115910.bv.entity.VideoCodec
import dev.aaa1115910.bv.screen.settings.SettingsMenuNavItem
import dev.aaa1115910.bv.util.Prefs

@Composable
fun AudioVideoSetting(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var showResolutionDialog by remember { mutableStateOf(false) }
    var showAudioCodecDialog by remember { mutableStateOf(false) }
    var showVideoCodecDialog by remember { mutableStateOf(false) }
    var showPlaySpeedDialog by remember { mutableStateOf(false) }

    var selectedResolution by remember { mutableStateOf(Prefs.defaultQuality) }
    var selectedVideoCodec by remember { mutableStateOf(Prefs.defaultVideoCodec) }
    var selectedAudioCodec by remember { mutableStateOf(Prefs.defaultAudio) }
    var selectedPlaySpeed by remember { mutableStateOf(Prefs.defaultPlaySpeed) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = SettingsMenuNavItem.AudioVideo.getDisplayName(context),
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingListItem(
            title = "默认分辨率",
            supportText = "当前：${selectedResolution.getDisplayName(context)}",
            onClick = { showResolutionDialog = true }
        )
        SettingListItem(
            title = "默认视频编码",
            supportText = "当前：${selectedVideoCodec.getDisplayName(context)}",
            onClick = { showVideoCodecDialog = true }
        )
        SettingListItem(
            title = "默认音频编码",
            supportText = "当前：${selectedAudioCodec.getDisplayName(context)}",
            onClick = { showAudioCodecDialog = true }
        )
        SettingListItem(
            title = "默认播放速度",
            supportText = "当前：${selectedPlaySpeed.getDisplayName(context)}",
            onClick = { showPlaySpeedDialog = true }
        )
    }
    // 弹窗复用组件
    if (showResolutionDialog) {
        OptionDialog(
            options = Resolution.entries.toTypedArray(),
            selectedOption = selectedResolution,
            onDismiss = { showResolutionDialog = false },
            onSelect = {
                Prefs.defaultQuality = it
                selectedResolution = it
            },
            getDisplayName = { it.getDisplayName(context) }
        )
    }

    if (showVideoCodecDialog) {
        OptionDialog(
            options = VideoCodec.entries.toTypedArray(),
            selectedOption = selectedVideoCodec,
            onDismiss = { showVideoCodecDialog = false },
            onSelect = {
                Prefs.defaultVideoCodec = it
                selectedVideoCodec = it
            },
            getDisplayName = { it.getDisplayName(context) }
        )
    }

    if (showAudioCodecDialog) {
        OptionDialog(
            options = Audio.entries.toTypedArray(),
            selectedOption = selectedAudioCodec,
            onDismiss = { showAudioCodecDialog = false },
            onSelect = {
                Prefs.defaultAudio = it
                selectedAudioCodec = it
            },
            getDisplayName = { it.getDisplayName(context) }
        )
    }

    if (showPlaySpeedDialog) {
        OptionDialog(
            options = PlaySpeedItem.entries.toTypedArray(),
            selectedOption = selectedPlaySpeed,
            onDismiss = { showPlaySpeedDialog = false },
            onSelect = {
                Prefs.defaultPlaySpeed = it
                selectedPlaySpeed = it
            },
            getDisplayName = { it.getDisplayName(context) }
        )
    }
}



