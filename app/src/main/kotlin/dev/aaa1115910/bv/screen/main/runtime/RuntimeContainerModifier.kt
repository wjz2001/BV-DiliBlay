package dev.aaa1115910.bv.screen.main.runtime

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.clearAndSetSemantics

fun Modifier.runtimeContainerInputEnabled(enabled: Boolean): Modifier {
    return if (enabled) {
        this
    } else {
        this
            .focusProperties { canFocus = false }
            .onPreviewKeyEvent { true }
            .clearAndSetSemantics { }
    }
}
