package dev.aaa1115910.bv.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import dev.aaa1115910.bv.R

enum class ThemeMode {
    LIGHT,
    DARK;

    fun getDisplayName(context: Context): String {
        return when (this) {
            LIGHT -> context.getString(R.string.theme_mode_light)
            DARK -> context.getString(R.string.theme_mode_dark)
        }
    }

    fun toNightMode(): Int {
        return when (this) {
            LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
    }

    companion object {
        fun fromOrdinal(ordinal: Int): ThemeMode {
            return entries.getOrElse(ordinal) { DARK }
        }
    }
}
