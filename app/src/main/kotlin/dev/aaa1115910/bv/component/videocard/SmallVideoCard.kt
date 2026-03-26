package dev.aaa1115910.bv.component.videocard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.component.UpIcon
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.util.ImageSize
import dev.aaa1115910.bv.util.resizedImageUrl

private const val SmallVideoCardFocusedScale = 1.1f
private val CoverStatIconSize = 16.dp
private val ActionButtonSize = 56.dp
private val ActionIconSize = 28.dp

@Composable
fun SmallVideoCard(
    modifier: Modifier = Modifier,
    data: VideoCardData,
    delToView: Boolean = false,
    onClick: () -> Unit,
    onAddWatchLater: (() -> Unit)? = null,
    onGoToDetailPage: (() -> Unit)? = null,
    onGoToUpPage: (() -> Unit)? = null,
) {
    var showActions by remember { mutableStateOf(false) }
    var releaseLongPress by remember { mutableStateOf(false) }
    val firstButtonRequester = remember { FocusRequester() }

    val hasAnyAction = onAddWatchLater != null || onGoToDetailPage != null || onGoToUpPage != null

    LaunchedEffect(showActions, hasAnyAction) {
        if (showActions && hasAnyAction) {
            firstButtonRequester.requestFocus()
        } else if (!showActions) {
            releaseLongPress = false
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SmallVideoCardFrame(
            showActions = showActions,
            onClick = onClick,
            onLongClick = {
                if (hasAnyAction) showActions = true
            },
            onFocusExit = {
                showActions = false
            }
        ) {
            if (showActions) {
                SmallVideoCardActions(
                    delToView = delToView,
                    releaseLongPress = releaseLongPress,
                    onReleaseLongPressHandled = { releaseLongPress = true },
                    firstButtonRequester = firstButtonRequester,
                    onAddWatchLater = onAddWatchLater,
                    onGoToDetailPage = onGoToDetailPage,
                    onGoToUpPage = onGoToUpPage
                )
            } else {
                CardCover(
                    cover = data.cover,
                    play = data.playString,
                    danmaku = data.danmakuString,
                    time = data.timeString
                )
            }
        }

        CardInfo(
            modifier = Modifier.fillMaxWidth(),
            title = data.title,
            upName = data.upName,
            pubTime = data.pubTime
        )
    }
}

@Composable
private fun SmallVideoCardFrame(
    showActions: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocusExit: () -> Unit,
    content: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) SmallVideoCardFocusedScale else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "small_video_card_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                transformOrigin = TransformOrigin.Center
            }
    ) {
        Card(
            onClick = { if (!showActions) onClick() },
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (!focusState.hasFocus) {
                        onFocusExit()
                    }
                },
            shape = CardDefaults.shape(MaterialTheme.shapes.large),

            // 关掉 TV Material 默认缩放
            scale = CardDefaults.scale(
                scale = 1f,
                focusedScale = 1f,
                pressedScale = 1f
            ),

            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(3.dp, MaterialTheme.colorScheme.border),
                    shape = MaterialTheme.shapes.large
                )
            )
        ) {
            content()
        }
    }
}

@Composable
private fun SmallVideoCardActions(
    delToView: Boolean,
    releaseLongPress: Boolean,
    onReleaseLongPressHandled: () -> Unit,
    firstButtonRequester: FocusRequester,
    onAddWatchLater: (() -> Unit)?,
    onGoToDetailPage: (() -> Unit)?,
    onGoToUpPage: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        onAddWatchLater?.let {
            SmallVideoCardActionButton(
                modifier = Modifier.focusRequester(firstButtonRequester),
                iconRes = if (delToView) R.drawable.remove_from_list else R.drawable.add_to_list,
                contentDescription = "Add to/Remove from watch later",
                onClick = {
                    if (!releaseLongPress) {
                        onReleaseLongPressHandled()
                        return@SmallVideoCardActionButton
                    }
                    it()
                }
            )
        }

        onGoToDetailPage?.let {
            SmallVideoCardActionButton(
                iconRes = R.drawable.info_24px,
                contentDescription = "Video Detail",
                onClick = it
            )
        }

        onGoToUpPage?.let {
            SmallVideoCardActionButton(
                iconRes = R.drawable.contact_page_24px,
                contentDescription = "Up Page",
                onClick = it
            )
        }
    }
}

@Composable
private fun SmallVideoCardActionButton(
    modifier: Modifier = Modifier,
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        modifier = modifier.size(ActionButtonSize),

        // 如果你当前版本 IconButton 支持 scale 参数，也建议关掉默认缩放
        // scale = IconButtonDefaults.scale(
        //     scale = 1f,
        //     focusedScale = 1f,
        //     pressedScale = 1f,
        //     disabledScale = 1f,
        //     focusedDisabledScale = 1f
        // ),

        onClick = onClick
    ) {
        Icon(
            modifier = Modifier.size(ActionIconSize),
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription
        )
    }
}

@Composable
fun CardCover(
    modifier: Modifier = Modifier,
    cover: String,
    play: String,
    danmaku: String,
    time: String
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large),
        contentAlignment = Alignment.BottomCenter
    ) {
        AsyncImage(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.large),
            model = cover.resizedImageUrl(ImageSize.SmallVideoCardCover),
            contentDescription = null,
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f)
                        )
                    )
                )
        )

        CardCoverStatsRow(
            play = play,
            danmaku = danmaku,
            time = time
        )
    }
}

@Composable
private fun CardCoverStatsRow(
    play: String,
    danmaku: String,
    time: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (play.isNotBlank()) {
            Icon(
                modifier = Modifier.size(CoverStatIconSize),
                painter = painterResource(id = R.drawable.ic_play_count),
                contentDescription = null,
                tint = Color.White
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = play,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
            Spacer(Modifier.width(8.dp))
        }

        if (danmaku.isNotBlank()) {
            Icon(
                modifier = Modifier.size(CoverStatIconSize),
                painter = painterResource(id = R.drawable.ic_danmaku_count),
                contentDescription = null,
                tint = Color.White
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = danmaku,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = time,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            maxLines = 1
        )
    }
}

@Composable
fun CardInfo(
    modifier: Modifier = Modifier,
    title: String,
    upName: String,
    pubTime: String?
) {
    Column(
        modifier = modifier.padding(vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            UpIcon()
            Text(
                modifier = Modifier.weight(1f),
                text = upName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pubTime ?: "",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}