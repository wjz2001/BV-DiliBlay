package dev.aaa1115910.bv.ui.effect

sealed class UiEffect {
    data class ShowToast(val message: String) : UiEffect()
}