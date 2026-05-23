package dev.aaa1115910.bv.screen.main.home

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BadgedBox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.wjzfocus.WjzFocusHost
import dev.aaa1115910.bv.wjzfocus.WjzFocusLayer
import dev.aaa1115910.bv.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.wjzfocus.LocalWjzFocusCoordinator
import dev.aaa1115910.bv.wjzfocus.wjzFocusable
import dev.aaa1115910.bv.activities.user.LoginActivity
import dev.aaa1115910.bv.activities.user.UserLockSettingsActivity
import dev.aaa1115910.bv.entity.db.UserDB
import dev.aaa1115910.bv.repository.UserRepository
import dev.aaa1115910.bv.screen.main.home.lock.UnlockSwitchUserContent
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.C
import dev.aaa1115910.bv.tv.component.TvAlertDialog
import dev.aaa1115910.bv.util.rememberTvImageRequest
import dev.aaa1115910.bv.viewmodel.UserViewModel
import dev.aaa1115910.bv.viewmodel.user.UserSwitchViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private val UserSwitchManageButtonNodeId = WjzFocusNodeId("main/user-switch/manage")
private val UserSwitchAddUserNodeId = WjzFocusNodeId("main/user-switch/add")
private val UserSwitchMenuShowTokenNodeId = WjzFocusNodeId("main/user-switch/menu/show-token")
private val UserSwitchMenuLockNodeId = WjzFocusNodeId("main/user-switch/menu/lock")
private val UserSwitchMenuDeleteNodeId = WjzFocusNodeId("main/user-switch/menu/delete")
private val UserSwitchDeleteConfirmNodeId = WjzFocusNodeId("main/user-switch/delete/confirm")
private val UserSwitchDeleteDismissNodeId = WjzFocusNodeId("main/user-switch/delete/dismiss")
private val UserSwitchRootScopeId = WjzFocusScopeId("main/user-switch/root")
private val UserSwitchMenuDialogScopeId = WjzFocusScopeId("main/user-switch/menu")
private val UserSwitchMenuContainerNodeId = WjzFocusNodeId("main/user-switch/menu/container")
private val UserSwitchAuthDialogScopeId = WjzFocusScopeId("main/user-switch/auth")
private val UserSwitchAuthContainerNodeId = WjzFocusNodeId("main/user-switch/auth/container")
private val UserSwitchDeleteDialogScopeId = WjzFocusScopeId("main/user-switch/delete")
private val UserSwitchDeleteContainerNodeId = WjzFocusNodeId("main/user-switch/delete/container")

@Composable
fun UserSwitchScreen(
    modifier: Modifier = Modifier,
    userViewModel: UserViewModel = koinViewModel(),
    userSwitchViewModel: UserSwitchViewModel = koinViewModel(),
    userRepository: UserRepository = koinInject()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val userList by userSwitchViewModel.userDbList.collectAsStateWithLifecycle()

    var showUnlock by remember { mutableStateOf(false) }
    var unlockUser: UserDB? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        userViewModel.updateUserInfo()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    userViewModel.updateUserInfo()
                    userSwitchViewModel.updateData()
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = LocalDensity.current.density * 1.25f,
            fontScale = LocalDensity.current.fontScale * 1.25f
        )
    ) {
        WjzFocusHost(
            modifier = modifier,
            layer = WjzFocusLayer.Content,
            scopeId = UserSwitchRootScopeId
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp)
            ) {
                Box {
                    UserSwitchContent(
                        userList = userList,
                        currentUid = userRepository.uid,
                        currentUserLevel = userViewModel.responseData?.level,
                        loadingUserList = userSwitchViewModel.loading,
                        onAddUser = {
                            context.startActivity(Intent(context, LoginActivity::class.java))
                        },
                        onDeleteUser = { user ->
                            userSwitchViewModel.deleteUser(user) {
                                if (userList.isEmpty()) (context as Activity).finish()
                            }
                        },
                        onSwitchUser = { user ->
                            if (user.uid != userRepository.uid && user.lock.isNotBlank()) {
                                unlockUser = user
                                showUnlock = true
                            } else {
                                userSwitchViewModel.switchUser(user) {
                                    (context as Activity).finish()
                                }
                            }
                        },
                        onShowUserLockSettings = { uid ->
                            UserLockSettingsActivity.actionStart(context, uid)
                        }
                    )
                    if (showUnlock) {
                        UnlockSwitchUserContent(
                            userList = userList,
                            unlockUser = unlockUser!!,
                            onUnlockSuccess = { user ->
                                userSwitchViewModel.switchUser(user) {
                                    (context as Activity).finish()
                                }
                            },
                            onCancel = {
                                showUnlock = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserSwitchContent(
    modifier: Modifier = Modifier,
    userList: ImmutableList<UserDB> = persistentListOf(),
    currentUid: Long,
    currentUserLevel: Int?,
    loadingUserList: Boolean,
    onSwitchUser: (UserDB) -> Unit,
    onDeleteUser: (UserDB) -> Unit,
    onAddUser: () -> Unit,
    onShowUserLockSettings: (Long) -> Unit
) {
    var choosedUser by remember {
        mutableStateOf(
            UserDB(
                uid = -1,
                username = "None",
                avatar = "https://i0.hdslb.com/bfs/article/b6b843d84b84a3ba5526b09ebf538cd4b4c8c3f3.jpg",
                auth = ""
            )
        )
    }

    var isInManagerMode by remember { mutableStateOf(false) }
    var showUserMenuDialog by remember { mutableStateOf(false) }
    var showAuthDataDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val focusCoordinator = LocalWjzFocusCoordinator.current
    var focusedUserUid by remember { mutableStateOf<Long?>(null) }
    var previousUserUids by remember { mutableStateOf<List<Long>>(emptyList()) }

    LaunchedEffect(userList, isInManagerMode) {
        val currentUserUids = userList.map { it.uid }
        val removedFocusedUid = focusedUserUid?.takeIf { uid ->
            uid in previousUserUids && uid !in currentUserUids
        }
        if (removedFocusedUid != null) {
            val removedIndex = previousUserUids.indexOf(removedFocusedUid)
            val targetUid = currentUserUids.getOrNull(
                removedIndex.coerceAtMost(currentUserUids.lastIndex)
            )
            val targetNodeId = when {
                targetUid != null -> WjzFocusNodeId("main/user-switch/item/$targetUid")
                isInManagerMode -> UserSwitchManageButtonNodeId
                else -> UserSwitchAddUserNodeId
            }
            focusCoordinator?.enqueueRequestFocus(
                nodeId = targetNodeId,
                layer = WjzFocusLayer.Content,
                scopeId = UserSwitchRootScopeId
            )
        }
        previousUserUids = currentUserUids
    }

    Surface(
        modifier = modifier,
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
                    text = stringResource(R.string.user_switch_title),
                    style = MaterialTheme.typography.displaySmall
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                itemsIndexed(
                    items = userList,
                    key = { _, user -> user.uid }
                ) { index, user ->
                    UserItem(
                        modifier = Modifier.wjzFocusable(
                            nodeId = WjzFocusNodeId("main/user-switch/item/${user.uid}"),
                            layer = WjzFocusLayer.Content,
                            fallback = !isInManagerMode && index == 0,
                            onFocusChanged = { hasFocus ->
                                if (hasFocus) focusedUserUid = user.uid
                            }
                        ),
                        avatar = user.avatar,
                        username = user.username,
                        level = if (user.uid == currentUid) currentUserLevel else null,
                        lockEnabled = user.lock.isNotBlank(),
                        onClick = {
                            if (isInManagerMode) {
                                choosedUser = user
                                showUserMenuDialog = true
                            } else {
                                onSwitchUser(user)
                            }
                        }
                    )
                }
                if (!isInManagerMode) {
                    item {
                        AddUserItem(
                            modifier = Modifier.wjzFocusable(
                                nodeId = UserSwitchAddUserNodeId,
                                layer = WjzFocusLayer.Content,
                                fallback = userList.isEmpty()
                            ),
                            onClick = onAddUser
                        )
                    }
                }
            }

            Button(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .wjzFocusable(
                        nodeId = UserSwitchManageButtonNodeId,
                        layer = WjzFocusLayer.Content,
                        fallback = isInManagerMode && userList.isEmpty()
                    ),
                onClick = { isInManagerMode = !isInManagerMode }
            ) {
                if (isInManagerMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null
                        )
                        Text(stringResource(R.string.user_switch_button_exit_manage_account))
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null
                        )
                        Text(stringResource(R.string.user_switch_button_manage_account))
                    }
                }
            }
        }
    }

    UserMenuDialog(
        show = showUserMenuDialog,
        onHideDialog = { showUserMenuDialog = false },
        username = choosedUser.username,
        uid = choosedUser.uid,
        showTokenButton = choosedUser.uid == currentUid || choosedUser.lock.isBlank(),
        onShowUserAuthData = { showAuthDataDialog = true },
        onDeleteUser = { showDeleteConfirmDialog = true },
        onShowUserLockSettings = { uid ->
            isInManagerMode = false
            onShowUserLockSettings(uid)
        }
    )

    UserAuthDataDialog(
        show = showAuthDataDialog,
        onHideDialog = { showAuthDataDialog = false },
        userDB = choosedUser
    )

    DeleteConfirmDialog(
        show = showDeleteConfirmDialog,
        onHideDialog = { showDeleteConfirmDialog = false },
        userDB = choosedUser,
        onConfirm = {
            onDeleteUser(choosedUser)
            showDeleteConfirmDialog = false
        }
    )
}

@Composable
fun UserMenuDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onHideDialog: () -> Unit,
    username: String,
    uid: Long,
    showTokenButton: Boolean,
    onShowUserAuthData: () -> Unit,
    onDeleteUser: () -> Unit,
    onShowUserLockSettings: (Long) -> Unit
) {
    if (show) {
        TvAlertDialog(
            modifier = modifier,
            onDismissRequest = onHideDialog,
            sourceScopeId = UserSwitchRootScopeId,
            dialogScopeId = UserSwitchMenuDialogScopeId,
            containerNodeId = UserSwitchMenuContainerNodeId,
            title = { Text(text = username) },
            text = {
                LazyColumn(
                    modifier = Modifier.width(240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    if (showTokenButton) {
                        item {
                            UserMenuButton(
                                modifier = Modifier.wjzFocusable(
                                    nodeId = UserSwitchMenuShowTokenNodeId,
                                    layer = WjzFocusLayer.Dialog,
                                    fallback = true
                                ),
                                text = stringResource(R.string.user_switch_menu_show_token),
                                onClick = {
                                    onHideDialog()
                                    onShowUserAuthData()
                                }
                            )
                        }
                    }

                    item {
                        UserMenuButton(
                            modifier = Modifier.wjzFocusable(
                                nodeId = UserSwitchMenuLockNodeId,
                                layer = WjzFocusLayer.Dialog,
                                fallback = !showTokenButton
                            ),
                            text = stringResource(R.string.user_switch_menu_user_lock),
                            onClick = {
                                onHideDialog()
                                onShowUserLockSettings(uid)
                            }
                        )
                    }

                    item {
                        UserMenuButton(
                            modifier = Modifier.wjzFocusable(
                                nodeId = UserSwitchMenuDeleteNodeId,
                                layer = WjzFocusLayer.Dialog
                            ),
                            text = stringResource(R.string.user_switch_menu_delete_account),
                            onClick = {
                                onHideDialog()
                                onDeleteUser()
                            },
                            color = MaterialTheme.colorScheme.errorContainer
                        )
                    }
                }
            },
            dismissButton = {},
            confirmButton = {}
        )
    }
}

@Composable
fun UserAuthDataDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onHideDialog: () -> Unit,
    userDB: UserDB,
    userSwitchViewModel: UserSwitchViewModel = koinViewModel()
) {
    var qrImage by remember {
        mutableStateOf(
            ImageBitmap(
                1,
                1,
                ImageBitmapConfig.Argb8888
            )
        )
    }

    LaunchedEffect(show) {
        if (show) {
            userSwitchViewModel.generateAuthQrImage(userDB.auth) {
                qrImage = it
            }
        }
    }

    if (show) {
        TvAlertDialog(
            modifier = modifier,
            onDismissRequest = onHideDialog,
            sourceScopeId = UserSwitchRootScopeId,
            dialogScopeId = UserSwitchAuthDialogScopeId,
            containerNodeId = UserSwitchAuthContainerNodeId,
            title = { Text(text = userDB.username) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(C.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            modifier = Modifier.size(120.dp),
                            bitmap = qrImage,
                            contentDescription = null
                        )
                    }
                    Text(text = userDB.auth)
                }
            },
            dismissButton = {},
            confirmButton = {}
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onHideDialog: () -> Unit,
    userDB: UserDB,
    onConfirm: () -> Unit
) {
    if (show) {
        TvAlertDialog(
            modifier = modifier,
            onDismissRequest = { onHideDialog() },
            sourceScopeId = UserSwitchRootScopeId,
            dialogScopeId = UserSwitchDeleteDialogScopeId,
            containerNodeId = UserSwitchDeleteContainerNodeId,
            title = { Text(text = stringResource(R.string.delete_account_confirm_dialog_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.delete_account_confirm_dialog_text,
                        userDB.username,
                        userDB.uid
                    )
                )
            },
            confirmButton = {
                Button(
                    modifier = Modifier.wjzFocusable(
                        nodeId = UserSwitchDeleteConfirmNodeId,
                        layer = WjzFocusLayer.Dialog,
                        fallback = true
                    ),
                    onClick = { onConfirm() }
                ) {
                    Text(text = stringResource(R.string.delete_account_confirm_dialog_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(
                    modifier = Modifier.wjzFocusable(
                        nodeId = UserSwitchDeleteDismissNodeId,
                        layer = WjzFocusLayer.Dialog
                    ),
                    onClick = { onHideDialog() }
                ) {
                    Text(text = stringResource(R.string.delete_account_confirm_dialog_dismiss))
                }
            }
        )
    }
}

@Composable
fun UserItem(
    modifier: Modifier = Modifier,
    avatar: String,
    username: String,
    level: Int? = null,
    lockEnabled: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val avatarRequest = rememberTvImageRequest(
        url = avatar,
        widthDp = 80.dp,
        heightDp = 80.dp
    )

    Column(
        modifier = modifier.width(120.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (onClick != null) {
            BadgedBox(
                modifier = Modifier.padding(18.dp),
                badge = {
                    if (lockEnabled) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null
                        )
                    }
                }
            ) {
                Surface(
                    modifier = Modifier
                        .size(80.dp),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = ClickableSurfaceDefaults.shape(
                        shape = CircleShape
                    ),
                    glow = ClickableSurfaceDefaults.glow(
                        focusedGlow = Glow(
                            elevationColor = MaterialTheme.colorScheme.inverseSurface,
                            elevation = 16.dp
                        )
                    ),
                    onClick = onClick
                ) {
                    AsyncImage(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                        model = avatarRequest,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .padding(18.dp)
                    .size(80.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = CircleShape
            ) {
                AsyncImage(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    model = avatarRequest,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds
                )
            }
        }
        Box(
            modifier = Modifier.height(26.dp),
            contentAlignment = Alignment.Center
        ) {
            if (level == null) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(),
                    text = username,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee(),
                        text = username,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Lv.$level",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun AddUserItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.width(120.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .padding(18.dp)
                .size(80.dp),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = ClickableSurfaceDefaults.shape(
                shape = CircleShape
            ),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(
                    elevationColor = MaterialTheme.colorScheme.inverseSurface,
                    elevation = 16.dp
                )
            ),
            onClick = onClick
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(40.dp),
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
            }
        }
        Box(
            modifier = Modifier.height(26.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
                text = stringResource(R.string.user_switch_add_user),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview
@Composable
fun UserItemPreview() {
    BVTheme {
        UserItem(
            avatar = "",
            username = "This is a user name",
            level = 6,
            onClick = {},
            lockEnabled = true
        )
    }
}

@Preview
@Composable
fun AddUserItemPreview() {
    BVTheme {
        AddUserItem(
            onClick = {}
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
fun UserSwitchContentPreview() {
    BVTheme {
        UserSwitchContent(
            userList = persistentListOf(
                UserDB(
                    uid = 0,
                    username = "大楚兴 陈胜王 大楚兴 陈胜王",
                    avatar = "0https://i0.hdslb.com/bfs/article/b6b843d84b84a3ba5526b09ebf538cd4b4c8c3f3.jpg",
                    auth = "{xxx1}"
                ),
                UserDB(
                    uid = 1,
                    username = "This is a long username",
                    avatar = "0https://i0.hdslb.com/bfs/article/b6b843d84b84a3ba5526b09ebf538cd4b4c8c3f3.jpg",
                    auth = "{xxx2}",
                    lock = "rdrd"
                ),
                UserDB(
                    uid = 2,
                    username = "\uD835\uDD4F",
                    avatar = "0https://i0.hdslb.com/bfs/article/b6b843d84b84a3ba5526b09ebf538cd4b4c8c3f3.jpg",
                    auth = "{xxx3}"
                )
            ),
            currentUid = 0L,
            currentUserLevel = 6,
            loadingUserList = false,
            onSwitchUser = {},
            onDeleteUser = {},
            onAddUser = {},
            onShowUserLockSettings = {}
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
fun UserMenuDialogPreview() {
    BVTheme {
        UserMenuDialog(
            show = true,
            onHideDialog = {},
            username = "This is a user name",
            uid = 0,
            showTokenButton = true,
            onShowUserAuthData = {},
            onDeleteUser = {},
            onShowUserLockSettings = {}
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
fun UserAuthDataDialogPreview() {
    BVTheme {
        UserAuthDataDialog(
            show = true,
            onHideDialog = {},
            userDB = UserDB(
                uid = 0,
                username = "Android Studio Official",
                avatar = "0https://i0.hdslb.com/bfs/article/b6b843d84b84a3ba5526b09ebf538cd4b4c8c3f3.jpg",
                auth = ""
            ),
        )
    }
}

@Composable
private fun UserMenuButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    color: Color? = null
) {
    Button(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = ButtonDefaults.shape(shape = MaterialTheme.shapes.medium),
        colors = if (color != null) ButtonDefaults.colors(containerColor = color) else ButtonDefaults.colors(),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
