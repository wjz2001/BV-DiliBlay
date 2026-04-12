package dev.aaa1115910.bv.screen.settings.content

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.component.HomeTopNavItem
import dev.aaa1115910.bv.component.settings.SettingCycleListItem
import dev.aaa1115910.bv.component.settings.SettingListItem
import dev.aaa1115910.bv.component.settings.SettingSwitchListItem
import dev.aaa1115910.bv.screen.settings.SettingsMenuNavItem
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.ThemeMode
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.requestFocus
import kotlin.math.roundToInt

@Composable
fun UISetting(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var showDensityDialog by remember { mutableStateOf(false) }
    var showHomepageDialog by remember { mutableStateOf(false) }

    var showVideoInfo by remember { mutableStateOf(Prefs.showVideoInfo) }
    var showPersistentSeek by remember { mutableStateOf(Prefs.showPersistentSeek) }
    var focusAlwaysCenter by remember { mutableStateOf(Prefs.focusAlwaysCenter) }
    val themeModeOrdinal by Prefs.themeModeFlow.collectAsState(Prefs.themeMode.ordinal)
    var selectedThemeMode by remember(themeModeOrdinal) { mutableStateOf(ThemeMode.fromOrdinal(themeModeOrdinal)) }

    val density by Prefs.densityFlow.collectAsState(LocalWindowInfo.current.containerSize.width / 960f)
    var selectedFirstHomeTopNavItem by remember { mutableStateOf(Prefs.firstHomeTopNavItem) }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = SettingsMenuNavItem.UI.getDisplayName(context),
                style = MaterialTheme.typography.displaySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    SettingListItem(
                        title = stringResource(R.string.settings_ui_homepage_title),
                        supportText = stringResource(R.string.settings_ui_homepage_text),
                        onClick = { showHomepageDialog = true }
                    )
                }
                item {
                    SettingSwitchListItem(
                        title = stringResource(R.string.settings_ui_show_video_info_title),
                        supportText = stringResource(R.string.settings_ui_show_video_info_text),
                        checked = showVideoInfo,
                        onCheckedChange = {
                            showVideoInfo = it
                            Prefs.showVideoInfo = it
                        }
                    )
                }
                item {
                    SettingSwitchListItem(
                        title = stringResource(R.string.settings_ui_show_persistent_seek_title),
                        supportText = stringResource(R.string.settings_ui_show_persistent_seek_text),
                        checked = showPersistentSeek,
                        onCheckedChange = {
                            showPersistentSeek = it
                            Prefs.showPersistentSeek = it
                        }
                    )
                }
                item {
                    SettingCycleListItem(
                        title = stringResource(R.string.settings_ui_focus_always_center_title),
                        options = listOf(false, true),
                        checked = focusAlwaysCenter,
                        supportText = {
                            if (it) {
                                "选中的内容始终保持在屏幕中间"
                            } else {
                                "只有移动到边缘时才滚动（更流畅）"
                            }
                        },
                        trailingText = {
                            if (it) {
                                "Pivot"
                            } else {
                                "KeepVisible"
                            }
                        },
                        onCheckedChange = {
                            focusAlwaysCenter = it
                            Prefs.focusAlwaysCenter = it
                        }
                    )
                }
                item {
                    SettingCycleListItem(
                        title = stringResource(R.string.settings_ui_theme_title),
                        options = ThemeMode.entries.toList(),
                        checked = selectedThemeMode,
                        supportText = { context.getString(R.string.settings_ui_theme_text) },
                        trailingText = { it.getDisplayName(context) },
                        onCheckedChange = {
                            selectedThemeMode = it
                            Prefs.themeMode = it
                        }
                    )
                }
                /*
                item {
                    SettingListItem(
                        title = stringResource(R.string.settings_ui_density_title),
                        supportText = stringResource(R.string.settings_ui_density_text),
                        onClick = { showDensityDialog = true }
                    )
                }
                 */
            }
        }
    }

    UIDensityDialog(
        show = showDensityDialog,
        onHideDialog = { showDensityDialog = false },
        density = density,
        onDensityChange = { Prefs.density = it }
    )

    if (showHomepageDialog) {
        OptionDialog(
            options = HomeTopNavItem.entries.toTypedArray(),
            selectedOption = selectedFirstHomeTopNavItem,
            onDismiss = { showHomepageDialog = false },
            onSelect = {
                Prefs.firstHomeTopNavItem = it
                selectedFirstHomeTopNavItem = it
            },
            getDisplayName = { it.getDisplayName(context) }
        )
    }
}

@Composable
private fun UIDensityDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onHideDialog: () -> Unit,
    density: Float,
    onDensityChange: (Float) -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val screenWidth = LocalWindowInfo.current.containerSize.width
    val defaultDensity by remember { mutableFloatStateOf(screenWidth / 960f) }

    LaunchedEffect(show) {
        if (show) focusRequester.requestFocus(scope)
    }

    // 这里得采用固定的 Density，否则会导致更改 Density 时，对话框反复重新加载
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = defaultDensity,
            fontScale = LocalDensity.current.fontScale
        )
    ) {
        if (show) {
            AlertDialog(
                modifier = modifier,
                onDismissRequest = { onHideDialog() },
                title = { Text(text = stringResource(R.string.settings_ui_density_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .focusable()
                            .fillMaxWidth()
                            .onPreviewKeyEvent {
                                if (it.key == Key.DirectionUp || it.key == Key.DirectionDown) {
                                    if (it.type == KeyEventType.KeyDown) {
                                        var newDensity = if (it.key == Key.DirectionUp)
                                            density + 0.1f else density - 0.1f
                                        newDensity = (newDensity * 10).roundToInt() / 10f
                                        if (newDensity < 0.5f) newDensity = 0.5f
                                        if (newDensity > 5f) newDensity = 5f
                                        onDensityChange(newDensity)
                                    }
                                }
                                false
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Rounded.ArrowDropUp, contentDescription = null)
                        Text(text = "$density")
                        Icon(imageVector = Icons.Rounded.ArrowDropDown, contentDescription = null)
                    }
                },
                confirmButton = {}
            )
        }
    }
}

@Preview
@Composable
fun UIDensityDialogPreview() {
    val show by remember { mutableStateOf(true) }
    var density by remember { mutableFloatStateOf(1.0f) }

    BVTheme(themeMode = ThemeMode.DARK) {
        UIDensityDialog(
            show = show,
            onHideDialog = {},
            density = density,
            onDensityChange = { density = it }
        )
    }
}

@Preview
@Composable
fun UIDensityDialogLightPreview() {
    val show by remember { mutableStateOf(true) }
    var density by remember { mutableFloatStateOf(1.0f) }

    BVTheme(themeMode = ThemeMode.LIGHT) {
        UIDensityDialog(
            show = show,
            onHideDialog = {},
            density = density,
            onDensityChange = { density = it }
        )
    }
}