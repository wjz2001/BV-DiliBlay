package dev.aaa1115910.bv.screen.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import coil.compose.AsyncImage
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.isDpadDown
import dev.aaa1115910.bv.util.isDpadRight
import dev.aaa1115910.bv.util.isDpadUp
import dev.aaa1115910.bv.util.isKeyDown
import dev.aaa1115910.bv.util.isKeyUp
import dev.aaa1115910.bv.screen.main.common.MainContentFocusTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LeftNaviContent(
    modifier: Modifier = Modifier,
    isLogin: Boolean = false,
    avatar: String = "",
    selectedItem: LeftNaviItem,
    searchFocusRequester: FocusRequester,
    homeFocusRequester: FocusRequester,
    personalFocusRequester: FocusRequester,
    ugcFocusRequester: FocusRequester,
    pgcFocusRequester: FocusRequester,
    onLeftNaviItemChanged: (LeftNaviItem) -> Unit,
    onLeftNaviItemPreload: (LeftNaviItem) -> Unit = {},
    onOpenSettings: () -> Unit,
    onShowUserPanel: () -> Unit,
    onFocusToContent: (MainContentFocusTarget) -> Unit,
    onLogin: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val userFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }

    var userArmedEntryTarget by remember { mutableStateOf<MainContentFocusTarget?>(null) }
    var settingsArmedEntryTarget by remember { mutableStateOf<MainContentFocusTarget?>(null) }

    val contentItems = listOf(
        LeftNaviItem.Search,
        LeftNaviItem.Home,
        LeftNaviItem.Personal,
        LeftNaviItem.UGC,
        LeftNaviItem.PGC
    )

    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        containerColor = Color.White.copy(alpha = 0.05f),
    ) {
        // 顶部用户按钮
        var userIsFocused by remember { mutableStateOf(false) }
        NavigationRailItem(
            modifier = Modifier
                .focusRequester(userFocusRequester)
                .onFocusChanged {
                    userIsFocused = it.hasFocus
                    if (!it.hasFocus) {
                        userArmedEntryTarget = null
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    when {
                        keyEvent.isDpadUp() -> {
                            if (keyEvent.isKeyDown()) {
                                settingsFocusRequester.requestFocus()
                                return@onPreviewKeyEvent true
                            }
                            true
                        }

                        keyEvent.key == Key.DirectionRight -> {
                            when {
                                keyEvent.isKeyDown() -> {
                                    userArmedEntryTarget = MainContentFocusTarget.LeftEntry
                                    true
                                }

                                keyEvent.isKeyUp() -> {
                                    val armed = userArmedEntryTarget
                                    userArmedEntryTarget = null
                                    if (armed == MainContentFocusTarget.LeftEntry) {
                                        onFocusToContent(MainContentFocusTarget.LeftEntry)
                                    }
                                    true
                                }

                                else -> false
                            }
                        }

                        keyEvent.key == Key.DirectionLeft -> {
                            when {
                                keyEvent.isKeyDown() -> {
                                    userArmedEntryTarget = MainContentFocusTarget.RightEntry
                                    true
                                }

                                keyEvent.isKeyUp() -> {
                                    val armed = userArmedEntryTarget
                                    userArmedEntryTarget = null
                                    if (armed == MainContentFocusTarget.RightEntry) {
                                        onFocusToContent(MainContentFocusTarget.RightEntry)
                                    }
                                    true
                                }

                                else -> false
                            }
                        }

                        else -> false
                    }
                },
            onClick = {
                if (isLogin) onShowUserPanel() else onLogin()
            },
            selected = userIsFocused,
            icon = {
                if (isLogin) {
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        colors = SurfaceDefaults.colors(
                            containerColor = Color.Gray
                        )
                    ) {
                        AsyncImage(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            model = avatar,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds
                        )
                    }
                } else {
                    Icon(
                        imageVector = LeftNaviItem.User.displayIcon,
                        contentDescription = null
                    )
                }
            }
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            contentItems.forEach { item ->
                val itemFocusRequester = when (item) {
                    LeftNaviItem.Search -> searchFocusRequester
                    LeftNaviItem.Home -> homeFocusRequester
                    LeftNaviItem.Personal -> personalFocusRequester
                    LeftNaviItem.UGC -> ugcFocusRequester
                    LeftNaviItem.PGC -> pgcFocusRequester
                    else -> error("Unexpected item: $item")
                }

                var isFocused by remember(item) { mutableStateOf(false) }
                var preloadJob by remember(item) { mutableStateOf<Job?>(null) }
                var armedEntryTarget by remember(item) { mutableStateOf<MainContentFocusTarget?>(null) }

                val indicatorColor by animateColorAsState(
                    targetValue = if (item == selectedItem) {
                        MaterialTheme.colorScheme.border
                    } else {
                        Color.Transparent
                    },
                    label = "selectionIndicatorColor"
                )

                NavigationRailItem(
                    modifier = Modifier
                        .focusRequester(itemFocusRequester)
                        .onFocusChanged { focusState ->
                            isFocused = focusState.hasFocus

                            if (!focusState.hasFocus) {
                                armedEntryTarget = null
                            }

                            preloadJob?.cancel()
                            preloadJob = if (focusState.hasFocus) {
                                scope.launch {
                                    delay(200)
                                    if (isFocused) {
                                        onLeftNaviItemPreload(item)
                                    }
                                }
                            } else {
                                null
                            }
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            when (keyEvent.key) {
                                Key.DirectionRight -> {
                                    when {
                                        keyEvent.isKeyDown() -> {
                                            armedEntryTarget = MainContentFocusTarget.LeftEntry
                                            true
                                        }

                                        keyEvent.isKeyUp() -> {
                                            val armed = armedEntryTarget
                                            armedEntryTarget = null
                                            if (armed == MainContentFocusTarget.LeftEntry) {
                                                onFocusToContent(MainContentFocusTarget.LeftEntry)
                                            }
                                            true
                                        }

                                        else -> false
                                    }
                                }

                                Key.DirectionLeft -> {
                                    when {
                                        keyEvent.isKeyDown() -> {
                                            armedEntryTarget = MainContentFocusTarget.RightEntry
                                            true
                                        }

                                        keyEvent.isKeyUp() -> {
                                            val armed = armedEntryTarget
                                            armedEntryTarget = null
                                            if (armed == MainContentFocusTarget.RightEntry) {
                                                onFocusToContent(MainContentFocusTarget.RightEntry)
                                            }
                                            true
                                        }

                                        else -> false
                                    }
                                }

                                else -> false
                            }
                        }
                        .selectionIndicator(indicatorColor),
                    onClick = {
                        onLeftNaviItemChanged(item)
                    },
                    selected = isFocused,
                    icon = {
                        Icon(
                            imageVector = item.displayIcon,
                            contentDescription = null
                        )
                    }
                )
            }
        }

        // 底部设置按钮
        var settingsIsFocused by remember { mutableStateOf(false) }
        NavigationRailItem(
            modifier = Modifier
                .focusRequester(settingsFocusRequester)
                .onFocusChanged {
                    settingsIsFocused = it.hasFocus
                    if (!it.hasFocus) {
                        settingsArmedEntryTarget = null
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    when {
                        keyEvent.isDpadDown() -> {
                            if (keyEvent.isKeyDown()) {
                                userFocusRequester.requestFocus()
                                return@onPreviewKeyEvent true
                            }
                            true
                        }

                        keyEvent.key == Key.DirectionRight -> {
                            when {
                                keyEvent.isKeyDown() -> {
                                    settingsArmedEntryTarget = MainContentFocusTarget.LeftEntry
                                    true
                                }

                                keyEvent.isKeyUp() -> {
                                    val armed = settingsArmedEntryTarget
                                    settingsArmedEntryTarget = null
                                    if (armed == MainContentFocusTarget.LeftEntry) {
                                        onFocusToContent(MainContentFocusTarget.LeftEntry)
                                    }
                                    true
                                }

                                else -> false
                            }
                        }

                        keyEvent.key == Key.DirectionLeft -> {
                            when {
                                keyEvent.isKeyDown() -> {
                                    settingsArmedEntryTarget = MainContentFocusTarget.RightEntry
                                    true
                                }

                                keyEvent.isKeyUp() -> {
                                    val armed = settingsArmedEntryTarget
                                    settingsArmedEntryTarget = null
                                    if (armed == MainContentFocusTarget.RightEntry) {
                                        onFocusToContent(MainContentFocusTarget.RightEntry)
                                    }
                                    true
                                }

                                else -> false
                            }
                        }

                        else -> false
                    }
                },
            onClick = onOpenSettings,
            selected = settingsIsFocused,
            icon = {
                Icon(
                    imageVector = LeftNaviItem.Settings.displayIcon,
                    contentDescription = null
                )
            }
        )
    }
}

enum class LeftNaviItem(
    val displayIcon: ImageVector
) {
    User(displayIcon = Icons.Default.AccountCircle),
    Search(displayIcon = Icons.Default.Search),
    Personal(displayIcon = Icons.Default.Person),
    Home(displayIcon = Icons.Default.Home),
    UGC(displayIcon = Icons.Default.OndemandVideo),
    PGC(displayIcon = Icons.Default.Movie),
    Settings(displayIcon = Icons.Default.Settings),
}

fun Modifier.selectionIndicator(color: Color): Modifier {
    return this.drawBehind {
        val strokeWidth = 4.dp.toPx()
        drawRect(
            color = color,
            topLeft = Offset.Zero,
            size = Size(width = strokeWidth, height = size.height)
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun LeftNaviContentPreview() {
    BVTheme {
        LeftNaviContent(
            selectedItem = LeftNaviItem.Home,
            searchFocusRequester = remember { FocusRequester() },
            homeFocusRequester = remember { FocusRequester() },
            personalFocusRequester = remember { FocusRequester() },
            ugcFocusRequester = remember { FocusRequester() },
            pgcFocusRequester = remember { FocusRequester() },
            onLeftNaviItemChanged = {},
            onLeftNaviItemPreload = {},
            onOpenSettings = {},
            onShowUserPanel = {},
            onFocusToContent = {},
            onLogin = {}
        )
    }
}