package dev.aaa1115910.bv.entity

import android.content.Context
import dev.aaa1115910.bv.R

enum class DanmakuSpeedFactor(val strRes: Int, val factor: Float) {
    S1(R.string.danmaku_speed_factor_x1_5, 1.5f),
    S2(R.string.danmaku_speed_factor_x1_25, 1.25f),
    S3(R.string.danmaku_speed_factor_x1, 1f),
    S4(R.string.danmaku_speed_factor_x0_75, 0.75f),
    S5(R.string.danmaku_speed_factor_x0_5, 0.5f);

    fun getDisplayName(context: Context) = context.getString(strRes)

    companion object {
        fun getIndexByFactor(targetFactor: Float): Int {
            return entries.find { it.factor == targetFactor }?.ordinal ?: S3.ordinal
        }
    }
}