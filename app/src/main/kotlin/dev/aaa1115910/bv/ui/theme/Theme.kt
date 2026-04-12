package dev.aaa1115910.bv.ui.theme

import android.app.Activity
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Typography
import dev.aaa1115910.bv.component.FpsMonitor
import dev.aaa1115910.bv.util.Prefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BVTheme(
    themeMode: ThemeMode? = null,
    content: @Composable () -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    val view = LocalView.current
    val resolvedThemeMode = when {
        themeMode != null -> themeMode
        view.isInEditMode -> ThemeMode.DARK
        else -> ThemeMode.fromOrdinal(
            Prefs.themeModeFlow.collectAsState(Prefs.themeMode.ordinal).value
        )
    }
    val isDarkTheme = resolvedThemeMode == ThemeMode.DARK
    val themeTokens = resolvedThemeMode.themeTokens

    val colorSchemeTv = themeTokens.toTvColorScheme()
    val colorSchemeCommon = themeTokens.toCommonColorScheme()
    val colorExtras = themeTokens.extras

    val typographyTv =
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) android6AndBelowTypographyTv else Typography()
    val typographyCommon =
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            android6AndBelowTypographyCommon
        } else {
            androidx.compose.material3.Typography()
        }

    if (!view.isInEditMode) {
        LaunchedEffect(resolvedThemeMode) {
            AppCompatDelegate.setDefaultNightMode(resolvedThemeMode.toNightMode())
        }
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorSchemeCommon.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkTheme
        }
    }

    val containerSize = LocalWindowInfo.current.containerSize
    val screenWidthPx = containerSize.width.toFloat()

    val density = if (view.isInEditMode) {
        LocalDensity.current.density
    } else {
        val defaultWidth = if (screenWidthPx > 0f) screenWidthPx else 1920f
        Prefs.densityFlow.collectAsState(defaultWidth / 960f).value
    }

    val showFps by remember { mutableStateOf(if (!view.isInEditMode) Prefs.showFps else false) }

    MaterialTheme(
        colorScheme = colorSchemeTv,
        typography = typographyTv
    ) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = colorSchemeCommon,
            typography = typographyCommon
        ) {
            CompositionLocalProvider(
                LocalBvThemeTokens provides themeTokens,
                LocalRippleConfiguration provides null,
                LocalDensity provides Density(density = density, fontScale = fontScale)
            ) {
                androidx.compose.material3.Surface(color = colorSchemeCommon.background) {
                    Surface(shape = RoundedCornerShape(0.dp)) {
                        if (showFps) {
                            FpsMonitor {
                                content()
                            }
                        } else {
                            content()
                        }
                    }
                }
            }
        }
    }
}