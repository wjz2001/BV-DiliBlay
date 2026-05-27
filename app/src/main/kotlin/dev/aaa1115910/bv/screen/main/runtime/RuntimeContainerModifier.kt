package dev.aaa1115910.bv.screen.main.runtime

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.aaa1115910.bv.wjzfocus.wjzDisabledFocus

fun Modifier.runtimeContainerInputEnabled(enabled: Boolean): Modifier {
    return if (enabled) {
        this
    } else {
        this
            .wjzDisabledFocus()
            .onPreviewKeyEvent { true }
            .clearAndSetSemantics { }
    }
}
