package dev.aaa1115910.bv.ui.common

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
}