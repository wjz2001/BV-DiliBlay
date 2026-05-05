package dev.aaa1115910.bv.screen.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import coil.compose.AsyncImage
import dev.aaa1115910.bv.component.MainChromeDefaults
import dev.aaa1115910.bv.ui.theme.AppWhite
import dev.aaa1115910.bv.ui.theme.C

@Composable
fun LeftNaviUserButton(
    modifier: Modifier = Modifier,
    isLogin: Boolean,
    avatar: String,
    focusRequester: FocusRequester,
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onPreviewKeyEvent: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { false },
    onClick: () -> Unit
) {
    val userIconColor by animateColorAsState(
        targetValue = if (isFocused) AppWhite else C.onSurface,
        label = "userIconColor"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) C.primary else Color.Transparent,
        label = "userBackgroundColor"
    )

    Surface(
        modifier = modifier
            .size(MainChromeDefaults.Size)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChanged(it.hasFocus) }
            .onPreviewKeyEvent(onPreviewKeyEvent)
            .focusable()
            .background(backgroundColor)
            .clickable(onClick = onClick),
        shape = RectangleShape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLogin) {
                AsyncImage(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    model = avatar,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds
                )
            } else {
                Icon(
                    imageVector = LeftNaviItem.User.displayIcon,
                    contentDescription = null,
                    tint = userIconColor
                )
            }
        }
    }
}
