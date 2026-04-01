package dev.aaa1115910.bv.entity

import android.content.Context
import dev.aaa1115910.bv.R

enum class VideoTransformNormal(
    private val strRes: Int
) {
    Normal(R.string.video_rotation_normal);

    fun getDisplayName(context: Context) = context.getString(strRes)
}

enum class VideoRotation(
    private val strRes: Int,
    val effectDegreesCounterClockwise: Float
) {
    Left90(R.string.video_rotation_left_90, 90f),
    Right90(R.string.video_rotation_right_90, 270f),
    Rotate180(R.string.video_rotation_180, 180f);

    val isQuarterTurn: Boolean
        get() = this == Left90 || this == Right90

    fun getDisplayName(context: Context) = context.getString(strRes)
}

enum class VideoFlip(
    private val strRes: Int,
    val scaleX: Float,
    val scaleY: Float
) {
    Horizontal(R.string.video_flip_horizontal, -1f, 1f),
    Vertical(R.string.video_flip_vertical, 1f, -1f);

    fun getDisplayName(context: Context) = context.getString(strRes)
}