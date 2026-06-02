package dev.aaa1115910.bv.component.controllers.playermenu

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.component.controllers.MenuFocusState
import dev.aaa1115910.bv.component.controllers.playermenu.component.StepLessMenuItem
import kotlin.math.roundToInt

@Composable
fun PlaySpeedMenuList(
    modifier: Modifier = Modifier,
    currentSelectedPlaySpeedItem: PlaySpeedItem,
    onPlaySpeedChange: (Float) -> Unit,
    onFocusStateChange: (MenuFocusState) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepLessMenuItem(
            modifier = Modifier
                .width(216.dp)
                .padding(horizontal = 8.dp),
            localFocusId = playerMenuLocalFocusId(PlayerMenuPlaySpeedFocusIdPrefix, "step"),
            value = currentSelectedPlaySpeedItem.speed,
            step = 0.25f,
            range = 0.25f..5f,
            text = "${(currentSelectedPlaySpeedItem.speed * 100).roundToInt() / 100f}倍",
            onValueChange = { speed ->
                val speedItem = PlaySpeedItem.fromSpeedNearest(speed)
                onPlaySpeedChange(speedItem.speed)
            },
            onFocusBackToParent = { onFocusStateChange(MenuFocusState.MenuNav) }
        )
    }
}

enum class PlaySpeedItem(val code: Int, private val strRes: Int, val speed: Float) {
    X0_25(0, R.string.play_speed_x0_25, 0.25f),
    X0_5(1, R.string.play_speed_x0_5, 0.5f),
    X0_75(2, R.string.play_speed_x0_75, 0.75f),
    X1(3, R.string.play_speed_x1, 1.0f),
    X1_25(4, R.string.play_speed_x1_25, 1.25f),
    X1_5(5, R.string.play_speed_x1_5, 1.5f),
    X1_75(6, R.string.play_speed_x1_75, 1.75f),
    X2(7, R.string.play_speed_x2, 2.0f),
    X2_25(8, R.string.play_speed_x2_25, 2.25f),
    X2_5(9, R.string.play_speed_x2_5, 2.5f),
    X2_75(10, R.string.play_speed_x2_75, 2.75f),
    X3(11, R.string.play_speed_x3, 3.0f),
    X3_25(12, R.string.play_speed_x3_25, 3.25f),
    X3_5(13, R.string.play_speed_x3_5, 3.5f),
    X3_75(14, R.string.play_speed_x3_75, 3.75f),
    X4(15, R.string.play_speed_x4, 4.0f),
    X4_25(16, R.string.play_speed_x4_25, 4.25f),
    X4_5(17, R.string.play_speed_x4_5, 4.5f),
    X4_75(18, R.string.play_speed_x4_75, 4.75f),
    X5(19, R.string.play_speed_x5, 5.0f);

    companion object {
        fun fromCode(code: Int): PlaySpeedItem {
            return entries.find { it.code == code } ?: X1
        }

        fun fromSpeed(speed: Float): PlaySpeedItem {
            return entries.find { it.speed == speed } ?: X1
        }

        fun fromSpeedNearest(speed: Float): PlaySpeedItem {
            // 使用 minByOrNull 并在找不到时返回默认的 1.0 倍速 (X1)
            return entries.minByOrNull { kotlin.math.abs(it.speed - speed) } ?: X1
        }
    }

    fun getDisplayName(context: Context) = context.getString(strRes)
}
