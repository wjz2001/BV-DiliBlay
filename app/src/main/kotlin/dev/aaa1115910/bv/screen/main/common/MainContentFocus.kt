package dev.aaa1115910.bv.screen.main.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties

enum class MainContentFocusTarget {
    LeftEntry,
    RightEntry
}

data class MainContentEntryRequest(
    val id: Long,
    val target: MainContentFocusTarget
)

fun Modifier.mainContentHorizontalExit(
    drawerFocusRequester: FocusRequester
): Modifier {
    return this.focusProperties {
        onExit = {
            when (requestedFocusDirection) {
                FocusDirection.Left,
                FocusDirection.Right -> drawerFocusRequester.requestFocus()
            }
        }
    }
}

fun Modifier.mainContentLeftExit(
    drawerFocusRequester: FocusRequester
): Modifier {
    return this.focusProperties {
        onExit = {
            if (requestedFocusDirection == FocusDirection.Left) {
                drawerFocusRequester.requestFocus()
            }
        }
    }
}

fun Modifier.mainContentRightExit(
    drawerFocusRequester: FocusRequester
): Modifier {
    return this.focusProperties {
        onExit = {
            if (requestedFocusDirection == FocusDirection.Right) {
                drawerFocusRequester.requestFocus()
            }
        }
    }
}