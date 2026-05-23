package dev.aaa1115910.bv.screen.main.home.lock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.wjzfocus.WjzFocusHost
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.wjzFocusable
import dev.aaa1115910.bv.component.ifElse
import dev.aaa1115910.bv.entity.db.UserDB
import dev.aaa1115910.bv.screen.main.home.UserItem
import dev.aaa1115910.bv.util.BvKeyDirection
import dev.aaa1115910.bv.util.bvKeyDirection
import dev.aaa1115910.bv.util.isBvConfirmKey
import dev.aaa1115910.bv.util.isNativeActionDown
import dev.aaa1115910.bv.util.toast
import kotlinx.collections.immutable.ImmutableList

@Composable
fun UnlockSwitchUserContent(
    modifier: Modifier = Modifier,
    userList: ImmutableList<UserDB>,
    unlockUser: UserDB?,
    onUnlockSuccess: (UserDB) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    var inputPassword by remember { mutableStateOf("") }
    val inputShow by remember {
        derivedStateOf {
            inputPassword
                .replace("u", "*")
                .replace("d", "*")
                .replace("l", "*")
                .replace("r", "*")
        }
    }
    val unselectedUserAlpha by remember { mutableFloatStateOf(0.4f) }

    BackHandler(true) {
    }

    WjzFocusHost {
        Surface(
            modifier = modifier
                .clickable {}
                .wjzFocusable(
                    nodeId = WjzFocusNodeId("unlock-switch-user/input"),
                    layer = WjzFocusLayer.Content,
                    fallback = true
                )
                .onPreviewKeyEvent {
                    if (it.isNativeActionDown()) return@onPreviewKeyEvent true
                    when (it.bvKeyDirection()) {
                        BvKeyDirection.Up -> inputPassword += "u"
                        BvKeyDirection.Down -> inputPassword += "d"
                        BvKeyDirection.Left -> inputPassword += "l"
                        BvKeyDirection.Right -> inputPassword += "r"
                        null -> Unit
                    }
                    when {
                        it.isBvConfirmKey() -> {
                            if (unlockUser?.lock == inputPassword) {
                                onUnlockSuccess(unlockUser)
                            } else {
                                R.string.user_lock_toast_password_error.toast(context)
                                inputPassword = ""
                            }
                        }

                        it.key == Key.Back -> {
                            if (inputPassword.isNotBlank()) {
                                inputPassword = inputPassword.drop(1)
                            } else {
                                onCancel()
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                },
            shape = RoundedCornerShape(0.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.user_lock_title_input_password),
                        style = MaterialTheme.typography.displaySmall
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    items(items = userList) { user ->
                        UserItem(
                            modifier = Modifier
                                .ifElse({ user != unlockUser }, Modifier.alpha(unselectedUserAlpha)),
                            avatar = user.avatar,
                            username = user.username,
                            lockEnabled = user.lock.isNotBlank(),
                        )
                    }
                }

                Text(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp),
                    text = inputShow,
                    style = MaterialTheme.typography.displayLarge
                )

                Text(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp),
                    text = stringResource(R.string.user_lock_input_tip),
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                )
            }
        }
    }
}
