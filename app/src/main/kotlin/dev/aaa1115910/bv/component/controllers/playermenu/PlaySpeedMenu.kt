package dev.aaa1115910.bv.component.controllers.playermenu

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.entity.LocalVideoPlayerControllerData
import dev.aaa1115910.bv.component.controllers.MenuFocusState
import dev.aaa1115910.bv.component.controllers.playermenu.component.MenuListItem
import dev.aaa1115910.bv.component.controllers.playermenu.component.StepLessMenuItem
import dev.aaa1115910.bv.component.ifElse
import dev.aaa1115910.bv.util.Prefs
import kotlin.math.roundToInt

@Composable
fun PlaySpeedMenuList(
    modifier: Modifier = Modifier,
    onSelectedPlaySpeedItemChange: (PlaySpeedItem) -> Unit,
    onPlaySpeedChange: (Float) -> Unit,
    onFocusStateChange: (MenuFocusState) -> Unit
) {
    // val context = LocalContext.current
    val data = LocalVideoPlayerControllerData.current
    // val focusRequester = remember { FocusRequester() }

    Row(
        modifier = modifier
            .fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepLessMenuItem(
            modifier = Modifier
                .width(216.dp)
                .padding(horizontal = 8.dp),
            value = data.currentVideoSpeed,
            step = 0.25f,
            range = 0.25f..5f,
            text = "${(data.currentVideoSpeed * 100).roundToInt() / 100f}倍",
            onValueChange = { speed ->
                onPlaySpeedChange(speed)
                val speedItem = PlaySpeedItem.fromSpeed(speed)
                onSelectedPlaySpeedItemChange(speedItem)
                Prefs.defaultPlaySpeed = speedItem
            },
            onFocusBackToParent = { onFocusStateChange(MenuFocusState.MenuNav) }
        )
        /*
        LazyColumn(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyUp) {
                        if (listOf(Key.Enter, Key.DirectionCenter).contains(it.key)) {
                            return@onPreviewKeyEvent false
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (it.key == Key.DirectionRight)
                        onFocusStateChange(MenuFocusState.MenuNav)
                    false
                }
                .focusRestorer(focusRequester),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp),
        ) {
            itemsIndexed(PlaySpeedItem.entries.toMutableList()) { index, item ->
                MenuListItem(
                    modifier = Modifier
                        .ifElse(
                            index == data.currentSelectedPlaySpeedItem.ordinal,
                            Modifier.focusRequester(focusRequester)
                        ),
                    text = item.getDisplayName(context),
                    selected = data.currentSelectedPlaySpeedItem == item,
                    onClick = {
                        onPlaySpeedChange(item.speed)
                        onSelectedPlaySpeedItemChange(item)
                    },
                )
            }
        }
        */
    }


}

enum class PlaySpeedItem(val code: Int, private val strRes: Int, val speed: Float) {
    x0_25(0, R.string.play_speed_x0_25, 0.25f),
    x0_5(1, R.string.play_speed_x0_5, 0.5f),
    x0_75(2, R.string.play_speed_x0_75, 0.75f),
    x1(3, R.string.play_speed_x1, 1.0f),
    x1_25(4, R.string.play_speed_x1_25, 1.25f),
    x1_5(5, R.string.play_speed_x1_5, 1.5f),
    x1_75(6, R.string.play_speed_x1_75, 1.75f),
    x2(7, R.string.play_speed_x2, 2.0f),
    x2_25(8, R.string.play_speed_x2_25, 2.25f),
    x2_5(9, R.string.play_speed_x2_5, 2.5f),
    x2_75(10, R.string.play_speed_x2_75, 2.75f),
    x3(11, R.string.play_speed_x3, 3.0f),
    x3_25(12, R.string.play_speed_x3_25, 3.25f),
    x3_5(13, R.string.play_speed_x3_5, 3.5f),
    x3_75(14, R.string.play_speed_x3_75, 3.75f),
    x4(15, R.string.play_speed_x4, 4.0f),
    x4_25(16, R.string.play_speed_x4_25, 4.25f),
    x4_5(17, R.string.play_speed_x4_5, 4.5f),
    x4_75(18, R.string.play_speed_x4_75, 4.75f),
    x5(19, R.string.play_speed_x5, 5.0f);

    companion object {
        fun fromCode(code: Int): PlaySpeedItem {
            return entries.find { it.code == code } ?: x1
        }

        fun fromSpeed(speed: Float): PlaySpeedItem {
            // 因为 step 是 0.25f，所以这里的值理论上总能找到精确匹配。
            // find 会返回第一个满足条件的元素。
            // 使用 ?: x1 作为安全兜底，以防万一出现意外情况。
            return entries.find { it.speed == speed } ?: x1
        }
    }

    fun getDisplayName(context: Context) = context.getString(strRes)
}