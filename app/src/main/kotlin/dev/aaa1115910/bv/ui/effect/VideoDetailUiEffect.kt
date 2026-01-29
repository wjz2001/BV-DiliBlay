package dev.aaa1115910.bv.ui.effect

import dev.aaa1115910.bv.entity.proxy.ProxyArea

sealed class VideoDetailUiEffect {
    data class ShowToast(val message: String) : VideoDetailUiEffect()
    data class LaunchPlayerActivity(val cid: Long?) : VideoDetailUiEffect()
    data class DirectlyPlay(val cid: Long?) : VideoDetailUiEffect()
    data class LaunchSeasonInfoActivity(val seasonId: Int?, val proxyArea: ProxyArea) :
        VideoDetailUiEffect()
}