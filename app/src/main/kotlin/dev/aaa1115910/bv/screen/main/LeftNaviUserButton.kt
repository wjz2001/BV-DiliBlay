package dev.aaa1115910.bv.screen.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.aaa1115910.bv.component.MainChromeDefaults
import dev.aaa1115910.bv.ui.theme.AppWhite
import dev.aaa1115910.bv.ui.theme.C

@Composable
fun LeftNaviUserButton(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    colorAnimationEnabled: Boolean,
    isLogin: Boolean,
    avatar: String,
    username: String,
    focusRequester: FocusRequester,
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onPreviewKeyEvent: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { false },
    onClick: () -> Unit
) {
    val animatedUserIconColor by animateColorAsState(
        targetValue = if (colorAnimationEnabled && isFocused) AppWhite else C.onSurface,
        label = "userIconColor"
    )
    val userIconColor = if (colorAnimationEnabled) animatedUserIconColor else C.onSurface
    val backgroundColor = Color.Transparent
    val interactionSource = remember { MutableInteractionSource() }
    var isPressed by remember { mutableStateOf(false) }
    val focusProgress = animateFloatAsState(
        targetValue = if (!expanded && isFocused) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "userButtonFocusProgress"
    )
    val pressProgress = animateFloatAsState(
        targetValue = if (!expanded && isPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "userButtonPressProgress"
    )

    val buttonWidth = if (expanded) 360.dp else MainChromeDefaults.Size
    val buttonHeight = if (expanded) 280.dp else MainChromeDefaults.Size
    val avatarSize = if (expanded) 136.dp else MainChromeDefaults.Size - 4.dp
    val avatarModel = remember(avatar) { avatar.asHighResolutionBiliAvatar() }

    Surface(
        modifier = modifier
            .width(buttonWidth)
            .height(buttonHeight)
            .graphicsLayer {
                val focused = focusProgress.value
                val pressed = pressProgress.value
                val focusOffset = 6.dp.toPx()
                translationX = focused * focusOffset
                translationY = focused * focusOffset
                transformOrigin = TransformOrigin(0f, 0f)
                val scale = 1f + focused * 0.08f - pressed * 0.08f
                scaleX = scale
                scaleY = scale
            }
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (!it.hasFocus) isPressed = false
                onFocusChanged(it.hasFocus)
            }
            .onPreviewKeyEvent { keyEvent ->
                val isConfirmKey = keyEvent.key == Key.DirectionCenter ||
                        keyEvent.key == Key.Enter ||
                        keyEvent.key == Key.Spacebar
                if (isConfirmKey && !expanded) {
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> isPressed = true
                        KeyEventType.KeyUp -> isPressed = false
                    }
                }
                onPreviewKeyEvent(keyEvent)
            }
            .focusable()
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RectangleShape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (expanded) 32.dp else 2.dp, vertical = if (expanded) 20.dp else 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isLogin) {
                    AsyncImage(
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape),
                        model = avatarModel,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds
                    )
                } else {
                    Icon(
                        modifier = Modifier
                            .size(avatarSize),
                        imageVector = LeftNaviItem.User.displayIcon,
                        contentDescription = null,
                        tint = userIconColor
                    )
                }

                if (expanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .height(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isLogin) username.ifBlank { "未登录" } else "未登录",
                            color = userIconColor,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun String.asHighResolutionBiliAvatar(): String {
    if (isBlank() || contains("@") || !contains("hdslb.com")) return this
    val queryStart = indexOf('?')
    return if (queryStart == -1) {
        "$this@480w_480h_1c.webp"
    } else {
        "${substring(0, queryStart)}@480w_480h_1c.webp${substring(queryStart)}"
    }
}
