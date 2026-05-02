package dev.aaa1115910.bv.screen.settings.content

import android.content.Context

enum class ActionAfterPlayItems (val code: Int, private val displayName: String){
    Pause(0, "暂停"),
    PlayNext(1, "播放下一集"),
    PlayRelated(3, "播放首个相关视频"),
    Exit(2, "退出播放器");


    companion object{
        fun fromCode(code: Int): ActionAfterPlayItems {
            return ActionAfterPlayItems.entries.find { it.code == code } ?: Exit
        }
    }

    fun getDisplayName(context: Context): String {
        return displayName
    }
}


